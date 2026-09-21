"""读取当前文件或经过 Git 对象和摘要验证的历史文件；不写缓存或恢复文件。"""

from __future__ import annotations

import hashlib
import json
from pathlib import Path, PurePosixPath
import re
import subprocess


class GovernanceError(RuntimeError):
    """控制面事实不足或相互矛盾时失败关闭。"""


class HistoryStore:
    """历史索引只提供定位，正文和完成依据必须从原提交核验。"""

    def __init__(self, root: Path):
        self.root = root.resolve()
        self.entries: dict[str, dict] = {}
        self._blobs: dict[str, bytes] | None = None
        self.source = ""
        path = root / "docs/tasks/history-index.json"
        if not path.is_file():
            return
        try:
            data = json.loads(path.read_text(encoding="utf-8"))
            if set(data) != {"schemaVersion", "sourceCommit", "files"} or data["schemaVersion"] != 1:
                raise GovernanceError("历史索引版本或字段无效")
            self.source = data["sourceCommit"]
            if not isinstance(self.source, str) or not re.fullmatch(r"[0-9a-f]{40}", self.source):
                raise GovernanceError("历史提交必须是完整 SHA")
            for entry in data["files"]:
                name = self.safe_path(entry["path"])
                if name in self.entries:
                    raise GovernanceError(f"历史路径重复：{name}")
                if entry["action"] not in {"keep", "move", "merge", "delete", "update"}:
                    raise GovernanceError(f"历史处置无效：{name}")
                if not entry["reason"] or not re.fullmatch(r"[0-9a-f]{64}", entry["sha256"]):
                    raise GovernanceError(f"历史原因或摘要无效：{name}")
                if "destination" in entry:
                    self.safe_path(entry["destination"])
                self.entries[name] = entry
        except (ValueError, KeyError, TypeError) as error:
            raise GovernanceError(f"历史索引格式无效：{error}") from error

    @staticmethod
    def safe_path(name: str) -> str:
        """路径只允许仓库内的规范相对路径，不能借历史查询读取外部文件。"""
        if (not isinstance(name, str) or not name or "\\" in name or ":" in name
                or "\n" in name or "\0" in name or PurePosixPath(name).is_absolute()
                or any(part in {"", ".", ".."} for part in name.split("/"))):
            raise GovernanceError(f"无效仓库路径：{name!r}")
        return name

    def _git(self, *args: str, input: bytes | None = None) -> bytes:
        result = subprocess.run(["git", *args], cwd=self.root, input=input,
                                capture_output=True, check=False)
        if result.returncode:
            raise GovernanceError("历史提交不可访问或不是当前 HEAD 的祖先；需要完整 Git 历史")
        return result.stdout

    def _load_blobs(self) -> None:
        if self._blobs is not None:
            return
        self._git("merge-base", "--is-ancestor", self.source, "HEAD")
        tree: dict[str, str] = {}
        for row in self._git("ls-tree", "-rz", self.source).split(b"\0"):
            if row:
                meta, name = row.split(b"\t", 1)
                mode, kind, oid = meta.split()
                if kind == b"blob" and mode != b"120000":
                    tree[name.decode()] = oid.decode()
        missing = self.entries.keys() - tree.keys()
        if missing:
            raise GovernanceError(f"历史文件不在原提交中：{sorted(missing)[0]}")
        expected = {name for name in tree if name.startswith(("docs/", "project/"))
                    or name in {"README.md", "AGENTS.md"}}
        omitted = expected - self.entries.keys()
        if omitted:
            raise GovernanceError(f"历史处置清单遗漏原文件：{sorted(omitted)[0]}")
        # 单次批量读取避免每份证据启动 Git，校验仍覆盖全部索引文件。
        names = list(self.entries)
        request = "".join(tree[name] + "\n" for name in names).encode()
        output = self._git("cat-file", "--batch", input=request) if names else b""
        blobs, offset = {}, 0
        for name in names:
            end = output.index(b"\n", offset)
            _, kind, size = output[offset:end].split()
            length = int(size)
            content = output[end + 1:end + 1 + length]
            if kind != b"blob" or hashlib.sha256(content).hexdigest() != self.entries[name]["sha256"]:
                raise GovernanceError(f"历史文件摘要不匹配：{name}")
            blobs[name] = content
            offset = end + length + 2
        self._blobs = blobs

    def historical(self, name: str) -> bytes:
        name = self.safe_path(name)
        if name not in self.entries:
            raise GovernanceError(f"不存在的历史文件：{name}")
        self._load_blobs()
        assert self._blobs is not None
        return self._blobs[name]

    def read(self, name: str) -> bytes:
        name = self.safe_path(name.split("#", 1)[0])
        path = self.root / name
        if not path.resolve().is_relative_to(self.root):
            raise GovernanceError(f"路径越出仓库：{name}")
        if path.is_file():
            return path.read_bytes()
        return self.historical(name)

    def verify(self) -> None:
        if self.source:
            self._load_blobs()
        for name, entry in self.entries.items():
            action = entry["action"]
            destination = entry.get("destination", name)
            if action in {"keep", "update", "move", "merge"}:
                path = self.root / destination
                if not path.resolve().is_relative_to(self.root):
                    raise GovernanceError(f"处置目标路径越出仓库：{destination}")
                if not path.is_file():
                    raise GovernanceError(f"处置目标不存在：{destination}")
                if action in {"keep", "move"} and hashlib.sha256(path.read_bytes()).hexdigest() != entry["sha256"]:
                    raise GovernanceError(f"保留历史文件正文被改写：{destination}")
            if action in {"move", "merge", "delete"} and (self.root / name).exists():
                raise GovernanceError(f"已退休路径仍存在：{name}")
