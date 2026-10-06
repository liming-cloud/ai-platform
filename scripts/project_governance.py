#!/usr/bin/env python3
"""验证并展示由仓库承载的长期项目控制面。"""

from __future__ import annotations

import re
import json
from urllib.parse import unquote, urlsplit
import subprocess
import sys
from dataclasses import dataclass
from pathlib import Path

# 即使调用方没有传 -B，状态和历史查询也不在仓库生成 Python 缓存。
sys.dont_write_bytecode = True

if __package__:
    from .project_history import GovernanceError, HistoryStore
else:
    sys.path.insert(0, str(Path(__file__).resolve().parent))
    from project_history import GovernanceError, HistoryStore


REQUIRED_STATE_KEYS = {
    "schemaVersion", "productVersion", "currentMilestone", "productFrontier",
    "currentTask", "updatedAt", "updatedBy",
}
REQUIRED_DESIGN_FILES = {
    "docs/foundation/charter.md", "docs/foundation/roadmap.md",
    "docs/standards/document-control.md", "docs/architecture/repository.md",
    "docs/architecture/backend.md", "docs/design/product-and-ui.md",
    "docs/design/domain-and-contracts.md",
}
REQUIRED_REPOSITORY_LAYOUT_SECTIONS = {
    "根目录", "后端和运行边界", "能力与所有权", "前端依赖", "验证与变更",
}
M1_REQUIRED_CONTEXT_MODULES = {
    "organization-access": "organizationaccess",
    "knowledge-data": "knowledgedata",
    "model-runtime": "modelruntime",
    "assistant-delivery": "assistantdelivery",
    "trusted-query": "trustedquery",
    "evaluation-operations": "evaluationoperations",
}
M0_FORBIDDEN_TRACKED_PREFIXES = (
    "backend/",
    "frontend/",
    "deploy/",
    "docs/decisions/",
    "docs/engineering/",
)
M0_FORBIDDEN_TRACKED_FILES = {
    "docs/PROJECT_DELIVERY.md",
    "docs/architecture/target-architecture.md",
    "docs/design/global-ui-design.md",
    "docs/design/product-and-ux-design.md",
    "scripts/quality/test_delivery_cleanup.py",
    "scripts/quality/test_validate_backend_context_modules.py",
    "scripts/quality/validate_backend_context_modules.py",
}
LIST_STATE_KEYS = set()
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
WORK_PACKAGE_ID = re.compile(r"^(?:WP|GOV)-\d{4}$")
ADR_ID = re.compile(r"^ADR-\d{4}$")
COMMIT_SHA = re.compile(r"^[0-9a-f]{7,40}$")
STALE_ACTIVE_STATEMENT = re.compile(r"(?:本|该)?工作包(?:仍|继续)?保持进行中")
MAX_LATEST_EVIDENCE = 8
MAX_FRONTIER_ADRS = 12
MAX_RESUME_CONTEXT_BYTES = 12 * 1024


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
    metadata: dict[str, str]


@dataclass(frozen=True)
class AdrDecision:
    identifier: str
    status: str
    title: str
    path: Path


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
    started = False
    for line in text.splitlines():
        if started and not line.startswith("|"):
            break
        if line.startswith("| 字段 |"):
            started = True
        if not started:
            continue
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


def parse_work_package(path: Path, text: str | None = None) -> WorkPackage:
    text = text if text is not None else path.read_text(encoding="utf-8")
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
    if (path.parent.name if path.name == "README.md" else path.stem) != identifier:
        raise GovernanceError(f"{path}：文件名必须与工作包编号一致")
    adrs = tuple(item.strip() for item in metadata["ADRs"].split(",") if item.strip())
    status = STATUS_VALUES.get(metadata["Status"], metadata["Status"].lower())
    if status == "done" and STALE_ACTIVE_STATEMENT.search(text):
        raise GovernanceError(f"{path}：已完成但正文仍声明进行中")
    return WorkPackage(
        identifier=identifier,
        status=status,
        owner=metadata["Owner"],
        branch=metadata["Branch"],
        milestone=metadata["Milestone"],
        adrs=adrs,
        evidence=metadata["Evidence"],
        commit=metadata["Commit"],
        path=path,
        sections=sections,
        metadata=metadata,
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
    return sorted((root / "docs" / "architecture" / "decisions").glob(f"{identifier}-*.md"))


def parse_adr(path: Path) -> AdrDecision:
    text = path.read_text(encoding="utf-8")
    heading = re.search(
        r"^#\s+(ADR-\d{4})[：:]\s*(.+?)\s*$",
        text,
        flags=re.MULTILINE,
    )
    if heading is None:
        raise GovernanceError(f"{path}：缺少规范 ADR 标题")
    identifier, title = heading.groups()
    if path.name.split("-", 2)[:2] != identifier.split("-"):
        raise GovernanceError(f"{path}：文件名与 ADR 标题编号不一致")

    status_heading = re.search(
        r"^##\s+状态(?:和日期)?\s*$",
        text,
        flags=re.MULTILINE,
    )
    if status_heading is None:
        raise GovernanceError(f"{path}：缺少状态章节")
    status_section = text[status_heading.end():].split("\n## ", 1)[0]
    labeled_status = re.search(
        r"^-\s*状态[：:]\s*(.+?)\s*$",
        status_section,
        flags=re.MULTILINE,
    )
    status = (
        labeled_status.group(1).strip().rstrip("。.")
        if labeled_status
        else next(
            (
                line.strip().rstrip("。.")
                for line in status_section.splitlines()
                if line.strip() and not line.startswith("#")
            ),
            "",
        )
    )
    if not status:
        raise GovernanceError(f"{path}：状态不能为空")
    return AdrDecision(identifier, status, title, path)


def adr_decisions(root: Path) -> tuple[AdrDecision, ...]:
    decisions = tuple(
        parse_adr(path)
        for path in sorted((root / "docs" / "architecture" / "decisions").glob("ADR-*.md"))
    )
    identifiers = [decision.identifier for decision in decisions]
    if len(identifiers) != len(set(identifiers)):
        raise GovernanceError("ADR 编号重复")
    return decisions


def is_confirmed_decision(status: str) -> bool:
    return status.startswith("已接受") or status == "部分被替代"


def render_decision_index(root: Path, current_text: str = "") -> str:
    lines = [
        "# 决策索引",
        "",
        "> 本表由 `python3 scripts/project_governance.py sync-decisions` 从 ADR 元数据生成。",
        "",
        "原状态是决策时点记录；被后续 ADR 取代的条款不是现行规范。当前范围从 docs/README.md 的主题入口读取。",
        "",
        "| 编号 | 原状态 | 决策 | ADR |",
        "| --- | --- | --- | --- |",
    ]
    for decision in adr_decisions(root):
        title = decision.title.replace("|", "\\|")
        status = decision.status.replace("|", "\\|")
        relative_path = decision.path.relative_to(root)
        lines.append(
            f"| {decision.identifier} | {status} | {title} | `{relative_path}` |"
        )
    return "\n".join(lines) + "\n"


def tracked_files(root: Path) -> set[str]:
    result = subprocess.run(
        ["git", "ls-files"],
        cwd=root,
        capture_output=True,
        text=True,
        check=False,
    )
    if result.returncode != 0:
        raise GovernanceError("无法读取 Git 受控文件清单")
    return {line for line in result.stdout.splitlines() if line}


def verify_screenshot_policy(root: Path) -> None:
    """截图只留在临时目录；同时检查索引及被忽略的证据文件，避免仅靠 .gitignore 放行。"""
    evidence_roots = ("docs", "project/evidence", "artifacts", "screenshots")
    raster_suffixes = {".png", ".jpg", ".jpeg", ".webp", ".gif", ".avif"}
    candidates = tracked_files(root) if (root / ".git").exists() else set()
    for directory in evidence_roots:
        candidates.update(
            str(path.relative_to(root))
            for path in (root / directory).rglob("*")
            if path.is_file() and path.suffix.lower() in raster_suffixes
        )
    screenshots = sorted(
        name for name in candidates
        if Path(name).suffix.lower() in raster_suffixes
        and (
            any(name.startswith(directory + "/") for directory in evidence_roots)
            or "screenshot" in name.lower()
            or "截图" in name
        )
    )
    if screenshots:
        raise GovernanceError(
            "截图不得保存在仓库；请保存到 /private/tmp，并登记 OpenDesign 链接："
            + ", ".join(screenshots)
        )


def markdown_level_two_sections(path: Path) -> set[str]:
    return {
        match.group(1).strip()
        for match in re.finditer(
            r"^##\s+(?:\d+\.\s+)?(.+?)\s*$",
            path.read_text(encoding="utf-8"),
            flags=re.MULTILINE,
        )
    }


def verify_m1_context_modules(root: Path) -> None:
    contexts_pom = root / "backend" / "contexts" / "pom.xml"
    pom_text = contexts_pom.read_text(encoding="utf-8") if contexts_pom.is_file() else ""
    missing: list[str] = []
    for module, namespace in M1_REQUIRED_CONTEXT_MODULES.items():
        module_root = root / "backend" / "contexts" / module
        package_info = (
            module_root
            / "src"
            / "main"
            / "java"
            / "com"
            / "aiplatform"
            / "foundation"
            / namespace
            / "package-info.java"
        )
        if (
            not (module_root / "pom.xml").is_file()
            or not package_info.is_file()
            or f"<module>{module}</module>" not in pom_text
        ):
            missing.append(module)
    if missing:
        raise GovernanceError(
            "M1 缺少限界上下文模块：" + ", ".join(sorted(missing))
        )


def task_paths(root: Path) -> list[Path]:
    return sorted(path for path in (root / "docs/tasks").glob("*/README.md")
                  if WORK_PACKAGE_ID.fullmatch(path.parent.name)) + sorted(
                      (root / "docs/tasks/backlog").glob("WP-*.md"))


def load_packages(root: Path, history: HistoryStore) -> dict[str, WorkPackage]:
    """当前任务优先；退休任务只从校验过的原文恢复，不接受索引伪造状态。"""
    packages = {}
    for path in task_paths(root):
        package = parse_work_package(path)
        if package.identifier in packages:
            raise GovernanceError(f"工作包编号重复：{package.identifier}")
        packages[package.identifier] = package
    for name, entry in history.entries.items():
        if name.startswith("project/work-packages/") and entry["action"] == "delete":
            package = parse_work_package(root / name, history.historical(name).decode())
            if package.identifier in packages:
                raise GovernanceError(f"退休工作包重复激活：{package.identifier}")
            if package.status != "done":
                raise GovernanceError(f"未完成工作包不能仅存历史：{package.identifier}")
            packages[package.identifier] = package
    return packages


def section(text: str, name: str) -> str:
    match = re.search(r"^##\s+" + re.escape(name) + r"\s*$", text, re.MULTILINE)
    return text[match.end():].split("\n## ", 1)[0].strip() if match else ""


def recent_evidence(package: WorkPackage) -> list[str]:
    if not package.path.is_file():
        return []
    text = section(package.path.read_text(), "证据")
    return list(dict.fromkeys(re.findall(r"(?:docs|project)/(?:[\w./-]+)\.(?:md|json)", text)))


def evidence_bindings(text: str) -> set[tuple[str, str, str]]:
    bindings, package = set(), None
    for line in text.splitlines():
        if line.startswith("## "):
            heading = re.fullmatch(r"## (WP-\d{4})\s*", line)
            package = heading.group(1) if heading else None
        cells = line.strip().strip("|").split("|") if line.startswith("|") else []
        if package and len(cells) == 4:
            references = re.findall(r"`((?:project|docs)/[^`]+)`", cells[1])
            commits = re.findall(r"`([0-9a-f]{40})`", cells[2])
            bindings.update((package, reference, commit) for reference in references for commit in commits)
    return bindings


def historical_evidence_bindings(history: HistoryStore) -> set[tuple[str, str, str]]:
    if "project/evidence/INDEX.md" not in history.entries:
        return set()
    bindings = evidence_bindings(history.historical("project/evidence/INDEX.md").decode())
    aliases = set(bindings)
    for package, name, commit in bindings:
        entry = history.entries.get(name.split("#", 1)[0], {})
        if entry.get("action") == "move":
            aliases.add((package, entry["destination"], commit))
    return aliases


def current_evidence_bindings(root: Path) -> set[tuple[str, str, str]]:
    """新证据由自身元数据绑定，不再要求维护平行的证据总表。"""
    bindings = set()
    for path in (root / "docs/tasks").rglob("*.md"):
        metadata = parse_metadata_table(path.read_text(), path)
        owner = metadata.get("责任任务", "")
        commit = metadata.get("验证提交", "")
        if (WORK_PACKAGE_ID.fullmatch(owner) and re.fullmatch(r"[0-9a-f]{40}", commit)
                and metadata.get("验证范围")):
            bindings.add((owner, path.relative_to(root).as_posix(), commit))
    return bindings


def verify_milestone_transition(previous: str, current: str, packages: dict[str, WorkPackage]) -> None:
    """变更全局指针不能绕过上一阶段的工作包结果与人工门禁。"""
    if not re.fullmatch(r"M\d+", previous) or not re.fullmatch(r"M\d+", current):
        raise GovernanceError("里程碑编号无效")
    before, after = int(previous[1:]), int(current[1:])
    if after < before:
        raise GovernanceError("不能通过回退里程碑改写历史事实")
    for number in range(before, after):
        products = [p for p in packages.values() if p.milestone == f"M{number}" and p.identifier.startswith("WP-")]
        gates = [p for p in products if p.metadata.get("任务类型") == "里程碑"]
        if not gates or any(p.status != "done" or p.metadata.get("人工验收") != "已接受" for p in gates):
            raise GovernanceError(f"M{number} 人工门禁未通过")
        if any(p.status != "done" for p in products):
            raise GovernanceError(f"M{number} 仍有未完成产品工作包")


# ADR-0092 只调整 M6 内顺序；平台概览仍须在全量复验和人工评审前闭合。
M6_DELIVERY_SEQUENCE = tuple(f"WP-{number:04d}" for number in range(17, 26)) + ("WP-0027", "WP-0026")


def verify_m6_delivery_sequence(packages: dict[str, WorkPackage]) -> None:
    """进行中、受阻或已完成的产品包都不能跳过已接受顺序中的前置结果。"""
    for index, identifier in enumerate(M6_DELIVERY_SEQUENCE):
        package = packages.get(identifier)
        if package is None or package.milestone != "M6" or package.status == "pending":
            continue
        missing = [previous for previous in M6_DELIVERY_SEQUENCE[:index]
                   if previous not in packages or packages[previous].milestone != "M6"
                   or packages[previous].status != "done"]
        if missing:
            raise GovernanceError(f"顺排前置未完成：{identifier} 依赖 {', '.join(missing)}")


def render_task_index(root: Path, state: dict, packages: dict[str, WorkPackage]) -> str:
    lines = ["# 任务索引", "", "> 由 make decisions-sync 生成；状态只编辑任务 README。", "",
             f"产品前沿：{state['productFrontier']}；当前执行：{state['currentTask']}；里程碑：{state['currentMilestone']}。",
             "", "| 任务 | 状态 | 负责人 | 恢复入口 |", "| --- | --- | --- | --- |"]
    order = {identifier: index + 17 for index, identifier in enumerate(M6_DELIVERY_SEQUENCE)}
    for identifier, package in sorted(packages.items(), key=lambda item: (
            item[0].split("-")[0], order.get(item[0], int(item[0].split("-")[1])))):
        name = package.path.relative_to(root).as_posix()
        target = f"[{identifier}]({package.path.relative_to(root / 'docs/tasks').as_posix()})" if package.path.is_file() else f"Git 历史：`{name}`"
        lines.append(f"| {identifier} | {package.status} | {package.owner} | {target} |")
    lines += ["", "M6 按 ADR-0092 顺排，其他边界沿用 ADR-0079；WP-0027 平台概览在 WP-0025 后、WP-0026 全量复验前闭合，最终回 WP-0014 人工复评。治理任务不推进产品。",
              "历史恢复：python3 -B scripts/project_governance.py history <原路径>。"]
    return "\n".join(lines) + "\n"


def verify_current_links(root: Path, history: HistoryStore) -> None:
    """冻结原文按来源解析；新规范和活动入口不得引用已退休的工作树路径。"""
    frozen = {e.get("destination", p) for p, e in history.entries.items()
              if e["action"] in {"keep", "move"}}
    paths = [root / "README.md", root / "AGENTS.md", *(root / "docs").rglob("*.md")]
    for path in paths:
        if not path.is_file() or path.relative_to(root).as_posix() in frozen:
            continue
        text = re.sub(r"```.*?```", "", path.read_text(), flags=re.DOTALL)
        for target in re.findall(r"(?<!!)\[[^]\n]+\]\(([^)]+)\)", text):
            parts = urlsplit(target.strip("<>"))
            if parts.scheme or not parts.path:
                continue
            resolved = (path.parent / unquote(parts.path)).resolve()
            if not resolved.is_relative_to(root.resolve()) or not resolved.is_file():
                raise GovernanceError(f"当前链接失效：{path.relative_to(root)} -> {target}")


def verify(root: Path) -> tuple[dict, dict[str, WorkPackage]]:
    root = root.resolve()
    if (root / "project/STATE.yaml").exists():
        raise GovernanceError("旧 STATE 仍存在，不能维护两个状态源")
    missing = [name for name in REQUIRED_DESIGN_FILES if not (root / name).is_file()]
    if missing:
        raise GovernanceError("缺少基座设计文件：" + ", ".join(sorted(missing)))
    state = parse_state(root / "docs/tasks/STATE.yaml")
    if state["schemaVersion"] != "2":
        raise GovernanceError("STATE 必须使用版本 2")
    verify_screenshot_policy(root)
    history = HistoryStore(root)
    history.verify()
    packages = load_packages(root, history)
    verify_m6_delivery_sequence(packages)
    if "project/STATE.yaml" in history.entries:
        original = history.historical("project/STATE.yaml").decode()
        match = re.search(r"^currentMilestone:\s*(M\d+)\s*$", original, re.MULTILINE)
        if match is None:
            raise GovernanceError("历史里程碑缺失")
        verify_milestone_transition(match[1], str(state["currentMilestone"]), packages)
    if not str(state["productFrontier"]).startswith("WP-"):
        raise GovernanceError("产品前沿必须是 WP 工作包")
    for key in ("productFrontier", "currentTask"):
        identifier = str(state[key])
        if identifier not in packages or not packages[identifier].path.is_file():
            raise GovernanceError(f"{key} 引用了不存在或无效的工作包")
        package = packages[identifier]
        if package.milestone != state["currentMilestone"]:
            raise GovernanceError("当前任务与里程碑不一致，不能绕过人工门禁")
        if key == "productFrontier" and package.status not in {"active", "blocked"}:
            raise GovernanceError("产品前沿必须指向进行中或受阻工作包")
        if len(package.adrs) > MAX_FRONTIER_ADRS:
            raise GovernanceError("当前前沿最多引用 12 个 ADR")
        for field in ("输入提交", "基线提交"):
            commit = package.metadata.get(field, "")
            if not re.fullmatch(r"[0-9a-f]{40}", commit):
                raise GovernanceError(f"{identifier} 缺少完整{field}")
            if (root / ".git").exists() and (not git_commit_exists(root, commit) or not is_ancestor(root, commit)):
                raise GovernanceError(f"{identifier} {field}不存在或不是 HEAD 祖先")
        evidence = recent_evidence(package)
        if len(evidence) > MAX_LATEST_EVIDENCE:
            raise GovernanceError("latestEvidence 最多保留 8 条")
        for name in evidence:
            history.read(name)
    decisions = adr_decisions(root)
    accepted = {d.identifier for d in decisions if is_confirmed_decision(d.status)}
    active_owners = {}
    for package in packages.values():
        if package.status not in {"active", "blocked", "pending", "done"}:
            raise GovernanceError(f"未知任务状态：{package.identifier}")
        for adr in package.adrs:
            if adr not in accepted:
                raise GovernanceError(f"{package.identifier} 引用不存在或未接受的 ADR：{adr}")
        if package.status == "active":
            if package.owner in active_owners:
                raise GovernanceError(f"负责人存在多个进行中的工作包：{package.owner}")
            active_owners[package.owner] = package.identifier
        if package.status == "done":
            if package.evidence.lower() in {"", "pending", "none"}:
                raise GovernanceError(f"{package.identifier} 已完成但没有证据")
            commit_pattern = r"[0-9a-f]{40}" if package.path.is_file() else r"[0-9a-f]{7,40}"
            if not re.fullmatch(commit_pattern, package.commit):
                raise GovernanceError(f"{package.identifier} 已完成但没有完整提交")
            if (root / ".git").exists() and (not git_commit_exists(root, package.commit) or not is_ancestor(root, package.commit)):
                raise GovernanceError(f"{package.identifier} 完成提交不存在或不是 HEAD 祖先")
            content = history.read(package.evidence).decode()
            if package.path.is_file():
                metadata = parse_metadata_table(content, root / package.evidence)
                if not metadata.get("验证范围"):
                    raise GovernanceError(f"{package.identifier} 证据缺少验证范围")
                if metadata.get("责任任务") != package.identifier or metadata.get("验证提交") != package.commit:
                    raise GovernanceError(f"{package.identifier} 证据未绑定任务与验证提交")
                if package.metadata.get("人工验收") != "不适用":
                    if package.metadata.get("人工验收") != "已接受" or not package.metadata.get("人工依据"):
                        raise GovernanceError(f"{package.identifier} 缺少人工接受依据")
                    history.read(package.metadata["人工依据"])
            # 退休包的绑定直接来自已校验的原任务元数据；不把旧索引短 SHA 改写为新验收。
        if package.metadata.get("任务类型") == "里程碑" and package.status == "done" and package.metadata.get("人工验收") != "已接受":
            raise GovernanceError("里程碑完成缺少人工接受")
    layout = root / "docs/architecture/repository.md"
    if REQUIRED_REPOSITORY_LAYOUT_SECTIONS - markdown_level_two_sections(layout):
        raise GovernanceError("详细目录规划缺少章节")
    if state["currentMilestone"] == "M0" and (root / ".git").exists():
        if any(p in M0_FORBIDDEN_TRACKED_FILES or p.startswith(M0_FORBIDDEN_TRACKED_PREFIXES) for p in tracked_files(root)):
            raise GovernanceError("M0 设计分支仍包含旧实现文件")
    if state["currentMilestone"] != "M0":
        verify_m1_context_modules(root)
    if (root / ".git").exists() and not (root / "docs/tasks/history-index.json").is_file():
        raise GovernanceError("Git 工作树缺少历史索引，不能跳过已退休证据校验")
    for name, expected in (
        ("docs/architecture/decisions/README.md", render_decision_index(root)),
        ("docs/tasks/README.md", render_task_index(root, state, packages)),
    ):
        path = root / name
        if not path.is_file() or path.read_text() != expected:
            raise GovernanceError(f"生成索引与事实不一致：{name}；请运行 make decisions-sync")
    verify_current_links(root, history)
    if len(build_resume_context(root, state, packages).encode()) > MAX_RESUME_CONTEXT_BYTES:
        raise GovernanceError("恢复上下文超过 12 KiB")
    return state, packages


def build_resume_context(root: Path, state: dict, packages: dict[str, WorkPackage]) -> str:
    task = packages[str(state["currentTask"])]
    product = packages[str(state["productFrontier"])]
    paths = [find_adr(root, adr)[0].relative_to(root).as_posix() for adr in task.adrs]
    lines = ["# 项目恢复上下文", "", f"里程碑：{state['currentMilestone']}；产品前沿：{product.identifier}；当前执行：{task.identifier}。",
             "", "## 当前任务 ADR", *["- " + path for path in paths], "",
             task.path.read_text().strip()]
    if task.identifier != product.identifier:
        lines += ["", "## 产品前沿保持", f"{product.identifier}：{product.status}；入口 {product.path.relative_to(root)}。",
                  section(product.path.read_text(), "下一前沿")]
    return "\n".join(lines) + "\n"


def render_status(root: Path, resume: bool) -> str:
    state, packages = verify(root)
    if resume:
        return build_resume_context(root, state, packages)
    return "\n".join([
        f"产品版本：{state['productVersion']}", f"当前里程碑：{state['currentMilestone']}",
        f"产品前沿：{state['productFrontier']}", f"当前执行任务：{state['currentTask']}",
        "进行中：" + ", ".join(p.identifier for p in packages.values() if p.status == "active"),
        "源码、部署和原型验证对象分别记录于任务证据，不使用全系统最后验证 SHA。",
    ])


def main() -> int:
    root = Path(__file__).resolve().parents[1]
    command = sys.argv[1] if len(sys.argv) > 1 else "verify"
    try:
        if command == "verify":
            state, packages = verify(root)
            print(f"项目治理验证通过：{len(packages)} 个任务；产品前沿 {state['productFrontier']}；当前执行 {state['currentTask']}")
        elif command in {"status", "resume"}:
            print(render_status(root, resume=command == "resume"))
        elif command == "sync-decisions":
            state = parse_state(root / "docs/tasks/STATE.yaml")
            packages = load_packages(root, HistoryStore(root))
            (root / "docs/architecture/decisions/README.md").write_text(render_decision_index(root))
            (root / "docs/tasks/README.md").write_text(render_task_index(root, state, packages))
            print("决策与任务索引已从唯一事实源同步")
        elif command == "history" and len(sys.argv) == 3:
            print(HistoryStore(root).historical(sys.argv[2]).decode(), end="")
        else:
            raise GovernanceError(f"未知命令或参数：{command}")
    except (GovernanceError, OSError, ValueError) as error:
        print(f"项目治理验证失败：{error}", file=sys.stderr)
        return 1
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
