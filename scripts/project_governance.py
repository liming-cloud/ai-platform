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
REQUIRED_DESIGN_FILES = {
    "docs/product/foundation-product-design.md",
    "docs/design/foundation-frontend-design.md",
    "docs/architecture/foundation-domain-design.md",
    "docs/architecture/foundation-backend-design.md",
    "docs/architecture/foundation-backend-development-structure.md",
    "docs/architecture/foundation-api-and-data-contracts.md",
    "docs/architecture/foundation-repository-layout.md",
    "docs/planning/foundation-delivery-plan.md",
}
REQUIRED_REPOSITORY_LAYOUT_SECTIONS = {
    "规划后的仓库根目录",
    "后端 Maven 反应堆",
    "限界上下文标准模板",
    "扩展模块",
    "三个可执行启动模块",
    "前端工作区",
    "公共契约目录",
    "数据库与存储所有权",
    "Docker 与部署目录",
    "跨系统验证目录",
    "M1 最小落地目录",
    "自动化门禁",
    "目录设计验收",
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
STALE_ACTIVE_STATEMENT = re.compile(r"(?:本|该)?工作包(?:仍|继续)?保持进行中")
MAX_LATEST_EVIDENCE = 8
MAX_FRONTIER_ADRS = 12
MAX_RESUME_CONTEXT_BYTES = 12 * 1024


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
        for path in sorted((root / "docs" / "adr").glob("ADR-*.md"))
    )
    identifiers = [decision.identifier for decision in decisions]
    if len(identifiers) != len(set(identifiers)):
        raise GovernanceError("ADR 编号重复")
    return decisions


def is_confirmed_decision(status: str) -> bool:
    return status.startswith("已接受") or status == "部分被替代"


def human_decision_section(text: str) -> str:
    heading = re.search(r"^##\s+已确认人工决策\s*$", text, flags=re.MULTILINE)
    return text[heading.start():].strip() if heading else ""


def render_decision_index(root: Path, current_text: str = "") -> str:
    lines = [
        "# 决策索引",
        "",
        "> 本表由 `python3 scripts/project_governance.py sync-decisions` 从 ADR 元数据生成。",
        "",
        "| 编号 | 状态 | 决策 | ADR |",
        "| --- | --- | --- | --- |",
    ]
    for decision in adr_decisions(root):
        title = decision.title.replace("|", "\\|")
        status = decision.status.replace("|", "\\|")
        relative_path = decision.path.relative_to(root)
        lines.append(
            f"| {decision.identifier} | {status} | {title} | `{relative_path}` |"
        )
    human_section = human_decision_section(current_text)
    if human_section:
        lines.extend(["", human_section])
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
    decision_index_path = root / "project" / "DECISIONS.md"
    current_decision_index = decision_index_path.read_text(encoding="utf-8")
    expected_decision_index = render_decision_index(root, current_decision_index)
    if current_decision_index != expected_decision_index:
        raise GovernanceError(
            "决策索引与 ADR 元数据不一致；请运行 make decisions-sync"
        )
    missing_design_files = [
        path for path in sorted(REQUIRED_DESIGN_FILES) if not (root / path).is_file()
    ]
    if missing_design_files:
        raise GovernanceError(f"缺少基座设计文件：{', '.join(missing_design_files)}")
    repository_layout_path = root / "docs" / "architecture" / "foundation-repository-layout.md"
    missing_layout_sections = (
        REQUIRED_REPOSITORY_LAYOUT_SECTIONS
        - markdown_level_two_sections(repository_layout_path)
    )
    if missing_layout_sections:
        raise GovernanceError(
            "详细目录规划缺少章节：" + ", ".join(sorted(missing_layout_sections))
        )
    if state["currentMilestone"] != "M0":
        verify_m1_context_modules(root)
    if state["currentMilestone"] == "M0" and (root / ".git").exists():
        current_tracked_files = tracked_files(root)
        forbidden_files = sorted(
            path
            for path in current_tracked_files
            if path in M0_FORBIDDEN_TRACKED_FILES
            or path.startswith(M0_FORBIDDEN_TRACKED_PREFIXES)
        )
        if forbidden_files:
            raise GovernanceError(
                "M0 设计分支仍包含旧实现文件：" + ", ".join(forbidden_files[:10])
            )
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
    if len(packages[frontier].adrs) > MAX_FRONTIER_ADRS:
        raise GovernanceError(
            f"当前前沿最多引用 {MAX_FRONTIER_ADRS} 个 ADR，"
            f"{frontier} 实际引用 {len(packages[frontier].adrs)} 个"
        )

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
    accepted_decisions = {
        decision.identifier
        for decision in adr_decisions(root)
        if is_confirmed_decision(decision.status)
    }
    confirmed_decisions = set(decisions)
    if confirmed_decisions != accepted_decisions:
        missing = sorted(accepted_decisions - confirmed_decisions)
        unexpected = sorted(confirmed_decisions - accepted_decisions)
        details = []
        if missing:
            details.append("缺少 " + ", ".join(missing))
        if unexpected:
            details.append("多余 " + ", ".join(unexpected))
        raise GovernanceError(
            "confirmedDecisions 与 ADR 状态不一致：" + "；".join(details)
        )
    for decision in decisions:
        if not ADR_ID.fullmatch(decision) or len(find_adr(root, decision)) != 1:
            raise GovernanceError(f"状态引用了不存在或不唯一的决策：{decision}")

    evidence = state["latestEvidence"]
    assert isinstance(evidence, list)
    if len(evidence) > MAX_LATEST_EVIDENCE:
        raise GovernanceError(
            f"latestEvidence 最多保留 {MAX_LATEST_EVIDENCE} 条，"
            "历史证据应只保留在 project/evidence/INDEX.md"
        )
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
    resume_context = build_resume_context(root, state, packages)
    if len(resume_context.encode("utf-8")) > MAX_RESUME_CONTEXT_BYTES:
        raise GovernanceError(
            f"恢复上下文超过 {MAX_RESUME_CONTEXT_BYTES // 1024} KiB，"
            "请压缩当前工作包或活动证据"
        )
    return state, packages


def build_resume_context(
    root: Path,
    state: dict[str, str | list[str]],
    packages: dict[str, WorkPackage],
) -> str:
    frontier = str(state["currentFrontier"])
    package = packages[frontier]
    evidence = state["latestEvidence"]
    active = state["activeWorkPackages"]
    blocked = state["blockedWorkPackages"]
    open_decisions = state["openDecisions"]
    assert isinstance(evidence, list)
    assert isinstance(active, list)
    assert isinstance(blocked, list)
    assert isinstance(open_decisions, list)

    adr_paths = [
        find_adr(root, identifier)[0].relative_to(root)
        for identifier in package.adrs
    ]
    lines = [
        "# 项目恢复上下文",
        "",
        f"- 当前里程碑：{state['currentMilestone']}",
        f"- 当前前沿：{frontier}",
        f"- 最后验证提交：{state['lastVerifiedCommit']}",
        f"- 进行中：{', '.join(active) if active else '无'}",
        f"- 受阻：{', '.join(blocked) if blocked else '无'}",
        f"- 待决策：{', '.join(open_decisions) if open_decisions else '无'}",
        "",
        "## 最近证据",
        "",
        *(f"- {item}" for item in evidence),
        "",
        "## 活动 ADR",
        "",
        *(f"- {item}" for item in adr_paths),
        "",
        "## 当前工作包",
        "",
        package.path.read_text(encoding="utf-8").strip(),
    ]
    return "\n".join(lines) + "\n"


def render_status(root: Path, resume: bool) -> str:
    state, packages = verify(root)
    if resume:
        return build_resume_context(root, state, packages)
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
        elif command == "sync-decisions":
            path = root / "project" / "DECISIONS.md"
            current_text = path.read_text(encoding="utf-8") if path.is_file() else ""
            path.write_text(
                render_decision_index(root, current_text),
                encoding="utf-8",
            )
            print("决策索引已从 ADR 元数据同步")
        else:
            raise GovernanceError(f"未知命令“{command}”")
    except GovernanceError as error:
        print(f"项目治理验证失败：{error}", file=sys.stderr)
        return 1
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
