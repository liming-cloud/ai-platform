#!/usr/bin/env python3
"""验证并展示由仓库承载的长期项目控制面。"""

from __future__ import annotations

import re
import subprocess
import sys
from dataclasses import dataclass
from pathlib import Path


REQUIRED_STATE_KEYS = {
    "schemaVersion",
    "productVersion",
    "currentMilestone",
    "currentFrontier",
    "lastVerifiedCommit",
    "activeWorkPackages",
    "blockedWorkPackages",
    "confirmedDecisions",
    "openDecisions",
    "latestEvidence",
    "nextReviewAt",
    "updatedAt",
    "updatedBy",
}
LIST_STATE_KEYS = {
    "activeWorkPackages",
    "blockedWorkPackages",
    "confirmedDecisions",
    "openDecisions",
    "latestEvidence",
}
REQUIRED_WORK_PACKAGE_SECTIONS = {
    "问题",
    "范围内",
    "范围外",
    "公共接口与数据",
    "约束",
    "验收标准",
    "验证",
    "证据",
    "下一前沿",
}
WORK_PACKAGE_FIELDS = {
    "ID": "ID",
    "状态": "Status",
    "负责人": "Owner",
    "分支": "Branch",
    "里程碑": "Milestone",
    "ADR": "ADRs",
    "证据": "Evidence",
    "提交": "Commit",
}
STATUS_VALUES = {
    "进行中": "active",
    "受阻": "blocked",
    "待开始": "pending",
    "已完成": "done",
}
WORK_PACKAGE_ID = re.compile(r"^WP-\d{4}$")
ADR_ID = re.compile(r"^ADR-\d{4}$")
COMMIT_SHA = re.compile(r"^[0-9a-f]{7,40}$")


class GovernanceError(RuntimeError):
    pass


@dataclass(frozen=True)
class WorkPackage:
    identifier: str
    status: str
    owner: str
    branch: str
    milestone: str
    adrs: tuple[str, ...]
    evidence: str
    commit: str
    path: Path
    sections: frozenset[str]


def parse_scalar(value: str) -> str:
    normalized = value.strip()
    if len(normalized) >= 2 and normalized[0] == normalized[-1] and normalized[0] in {'"', "'"}:
        return normalized[1:-1]
    return normalized


def parse_state(path: Path) -> dict[str, str | list[str]]:
    if not path.is_file():
        raise GovernanceError(f"缺少状态文件：{path}")
    state: dict[str, str | list[str]] = {}
    active_list: str | None = None
    for line_number, raw_line in enumerate(path.read_text(encoding="utf-8").splitlines(), start=1):
        line = raw_line.rstrip()
        stripped = line.strip()
        if not stripped or stripped.startswith("#"):
            continue
        if line.startswith("  - "):
            if active_list is None:
                raise GovernanceError(f"{path}:{line_number}：列表项缺少列表字段")
            value = parse_scalar(line[4:])
            if not value:
                raise GovernanceError(f"{path}:{line_number}：列表项不能为空")
            cast_list = state[active_list]
            assert isinstance(cast_list, list)
            cast_list.append(value)
            continue
        if line.startswith((" ", "\t")):
            raise GovernanceError(f"{path}:{line_number}：不支持嵌套映射")
        if ":" not in line:
            raise GovernanceError(f"{path}:{line_number}：应为“字段: 值”")
        key, raw_value = line.split(":", 1)
        key = key.strip()
        if not key or key in state:
            raise GovernanceError(f"{path}:{line_number}：字段为空或重复“{key}”")
        if key in LIST_STATE_KEYS:
            if raw_value.strip() not in {"", "[]"}:
                raise GovernanceError(f"{path}:{line_number}：列表“{key}”必须使用缩进列表项")
            state[key] = []
            active_list = key
        else:
            value = parse_scalar(raw_value)
            if not value:
                raise GovernanceError(f"{path}:{line_number}：字段“{key}”不能为空")
            state[key] = value
            active_list = None
    missing = REQUIRED_STATE_KEYS - state.keys()
    unknown = state.keys() - REQUIRED_STATE_KEYS
    if missing:
        raise GovernanceError(f"状态缺少字段：{', '.join(sorted(missing))}")
    if unknown:
        raise GovernanceError(f"状态包含未知字段：{', '.join(sorted(unknown))}")
    return state


def parse_metadata_table(text: str, path: Path) -> dict[str, str]:
    metadata: dict[str, str] = {}
    for line in text.splitlines():
        match = re.match(r"^\|\s*([^|]+?)\s*\|\s*([^|]*?)\s*\|$", line)
        if not match:
            continue
        key, value = match.group(1).strip(), match.group(2).strip()
        if key in {"字段", "---"}:
            continue
        key = WORK_PACKAGE_FIELDS.get(key, key)
        if key in metadata:
            raise GovernanceError(f"{path}：元数据字段重复“{key}”")
        metadata[key] = value
    return metadata


def parse_work_package(path: Path) -> WorkPackage:
    text = path.read_text(encoding="utf-8")
    metadata = parse_metadata_table(text, path)
    required = {"ID", "Status", "Owner", "Branch", "Milestone", "ADRs", "Evidence", "Commit"}
    missing = required - metadata.keys()
    if missing:
        raise GovernanceError(f"{path}：缺少元数据：{', '.join(sorted(missing))}")
    sections = frozenset(
        match.group(1).strip()
        for match in re.finditer(r"^##\s+(.+?)\s*$", text, flags=re.MULTILINE)
    )
    missing_sections = REQUIRED_WORK_PACKAGE_SECTIONS - sections
    if missing_sections:
        raise GovernanceError(f"{path}：缺少章节：{', '.join(sorted(missing_sections))}")
    identifier = metadata["ID"]
    if not WORK_PACKAGE_ID.fullmatch(identifier):
        raise GovernanceError(f"{path}：工作包编号无效“{identifier}”")
    if path.stem != identifier:
        raise GovernanceError(f"{path}：文件名必须与工作包编号一致")
    adrs = tuple(item.strip() for item in metadata["ADRs"].split(",") if item.strip())
    return WorkPackage(
        identifier=identifier,
        status=STATUS_VALUES.get(metadata["Status"], metadata["Status"].lower()),
        owner=metadata["Owner"],
        branch=metadata["Branch"],
        milestone=metadata["Milestone"],
        adrs=adrs,
        evidence=metadata["Evidence"],
        commit=metadata["Commit"],
        path=path,
        sections=sections,
    )


def git_commit_exists(root: Path, commit: str) -> bool:
    result = subprocess.run(
        ["git", "cat-file", "-e", f"{commit}^{{commit}}"],
        cwd=root,
        stdout=subprocess.DEVNULL,
        stderr=subprocess.DEVNULL,
        check=False,
    )
    return result.returncode == 0


def is_ancestor(root: Path, ancestor: str) -> bool:
    result = subprocess.run(
        ["git", "merge-base", "--is-ancestor", ancestor, "HEAD"],
        cwd=root,
        stdout=subprocess.DEVNULL,
        stderr=subprocess.DEVNULL,
        check=False,
    )
    return result.returncode == 0


def find_adr(root: Path, identifier: str) -> list[Path]:
    return sorted((root / "docs" / "adr").glob(f"{identifier}-*.md"))


def verify(root: Path) -> tuple[dict[str, str | list[str]], dict[str, WorkPackage]]:
    required_files = {
        root / "project" / "CHARTER.md",
        root / "project" / "ROADMAP.md",
        root / "project" / "STATE.yaml",
        root / "project" / "DECISIONS.md",
        root / "project" / "TRACEABILITY.md",
        root / "project" / "RISKS.md",
        root / "project" / "evidence" / "INDEX.md",
    }
    missing_files = [str(path.relative_to(root)) for path in required_files if not path.is_file()]
    if missing_files:
        raise GovernanceError(f"缺少控制面文件：{', '.join(sorted(missing_files))}")

    state = parse_state(root / "project" / "STATE.yaml")
    packages: dict[str, WorkPackage] = {}
    for path in sorted((root / "project" / "work-packages").glob("WP-*.md")):
        package = parse_work_package(path)
        if package.identifier in packages:
            raise GovernanceError(f"工作包编号重复：{package.identifier}")
        packages[package.identifier] = package
    if not packages:
        raise GovernanceError("没有找到工作包")

    active_ids = state["activeWorkPackages"]
    blocked_ids = state["blockedWorkPackages"]
    assert isinstance(active_ids, list)
    assert isinstance(blocked_ids, list)
    referenced_ids = active_ids + blocked_ids
    if len(referenced_ids) != len(set(referenced_ids)):
        raise GovernanceError("进行中和受阻工作包不能重复")
    for identifier in referenced_ids:
        if not WORK_PACKAGE_ID.fullmatch(identifier) or identifier not in packages:
            raise GovernanceError(f"状态引用了不存在或无效的工作包：{identifier}")
    frontier = state["currentFrontier"]
    assert isinstance(frontier, str)
    if frontier not in referenced_ids:
        raise GovernanceError("currentFrontier 必须是进行中或受阻工作包")

    active_owners: dict[str, str] = {}
    for identifier in active_ids:
        package = packages[identifier]
        if package.status != "active":
            raise GovernanceError(f"{identifier} 在状态中为进行中，但工作包状态为“{package.status}”")
        if package.owner in active_owners:
            raise GovernanceError(
                f"负责人“{package.owner}”存在多个进行中的工作包："
                f"{active_owners[package.owner]}, {identifier}"
            )
        active_owners[package.owner] = identifier
    for identifier in blocked_ids:
        if packages[identifier].status != "blocked":
            raise GovernanceError(f"{identifier} 在状态中受阻，但工作包状态不是受阻")

    for package in packages.values():
        for adr in package.adrs:
            if not ADR_ID.fullmatch(adr) or len(find_adr(root, adr)) != 1:
                raise GovernanceError(f"{package.identifier} 引用了不存在或不唯一的 ADR：{adr}")
        if package.status == "done":
            if package.evidence.lower() in {"", "pending", "none"}:
                raise GovernanceError(f"{package.identifier} 已完成但没有证据")
            if not COMMIT_SHA.fullmatch(package.commit):
                raise GovernanceError(f"{package.identifier} 已完成但没有有效提交")
            if not (root / package.evidence.split("#", 1)[0]).is_file():
                raise GovernanceError(f"{package.identifier} 的证据路径不存在")

    decisions = state["confirmedDecisions"]
    assert isinstance(decisions, list)
    for decision in decisions:
        if not ADR_ID.fullmatch(decision) or len(find_adr(root, decision)) != 1:
            raise GovernanceError(f"状态引用了不存在或不唯一的决策：{decision}")

    evidence = state["latestEvidence"]
    assert isinstance(evidence, list)
    for evidence_path in evidence:
        if not (root / evidence_path.split("#", 1)[0]).is_file():
            raise GovernanceError(f"状态引用了不存在的证据：{evidence_path}")

    last_verified = state["lastVerifiedCommit"]
    assert isinstance(last_verified, str)
    if not COMMIT_SHA.fullmatch(last_verified):
        raise GovernanceError("lastVerifiedCommit 必须是 Git 提交 SHA")
    if (root / ".git").exists():
        if not git_commit_exists(root, last_verified):
            raise GovernanceError("lastVerifiedCommit 不存在")
        if not is_ancestor(root, last_verified):
            raise GovernanceError("lastVerifiedCommit 不是当前 HEAD 的祖先")
    return state, packages


def render_status(root: Path, resume: bool) -> str:
    state, packages = verify(root)
    active = state["activeWorkPackages"]
    blocked = state["blockedWorkPackages"]
    evidence = state["latestEvidence"]
    assert isinstance(active, list)
    assert isinstance(blocked, list)
    assert isinstance(evidence, list)
    lines = [
        f"产品版本：{state['productVersion']}",
        f"当前里程碑：{state['currentMilestone']}",
        f"当前前沿：{state['currentFrontier']}",
        f"最后验证提交：{state['lastVerifiedCommit']}",
        f"进行中：{', '.join(active) if active else '无'}",
        f"受阻：{', '.join(blocked) if blocked else '无'}",
        f"待决策：{', '.join(state['openDecisions']) if state['openDecisions'] else '无'}",
        f"最新证据：{', '.join(evidence) if evidence else '无'}",
        f"下次评审：{state['nextReviewAt']}",
    ]
    if resume:
        frontier = str(state["currentFrontier"])
        lines.extend(
            [
                "",
                "事实来源文件：",
                "- project/CHARTER.md",
                "- project/STATE.yaml",
                f"- project/work-packages/{frontier}.md",
                "",
                (root / "project" / "work-packages" / f"{frontier}.md").read_text(encoding="utf-8"),
            ]
        )
    return "\n".join(lines)


def main() -> int:
    root = Path(__file__).resolve().parents[1]
    command = sys.argv[1] if len(sys.argv) > 1 else "verify"
    try:
        if command == "verify":
            state, packages = verify(root)
            print(
                f"项目治理验证通过：{len(packages)} 个工作包，"
                f"当前前沿为 {state['currentFrontier']}"
            )
        elif command == "status":
            print(render_status(root, resume=False))
        elif command == "resume":
            print(render_status(root, resume=True))
        else:
            raise GovernanceError(f"未知命令“{command}”")
    except GovernanceError as error:
        print(f"项目治理验证失败：{error}", file=sys.stderr)
        return 1
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
