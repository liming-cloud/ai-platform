"""验证已验收原型的全量承接关系，不把原型成功状态当作生产完成。"""

import copy
import hashlib
import json
import re
import subprocess
import unittest
from collections import Counter
from pathlib import Path

from scripts import project_governance as governance


ROOT = Path(__file__).resolve().parents[2]
MAP_PATH = "docs/tasks/milestones/M6/implementation-map.json"
INVENTORY_PATH = "docs/prototypes/wp-0016/page-inventory.json"
LEDGER_PATH = "docs/prototypes/wp-0016/ACTION-LEDGER.json"

# 页面责任包与操作责任包分开校验，防止跨包任务被入口页面提前结项。
PACKAGE_SCREENS = {
    "WP-0017": "console-login tenant-login platform-tenants platform-tenant-create platform-tenant-detail platform-tenant-admins platform-tenant-quotas platform-tenant-features platform-tenant-security platform-accounts platform-roles".split(),
    "WP-0018": "tenant-overview tenant-members tenant-member-create tenant-member-account tenant-member-features tenant-member-assistants tenant-roles access-policies access-policy-detail".split(),
    "WP-0019": "platform-model-connections platform-model-connection-detail platform-model-deployments platform-model-deployment-detail model-providers model-provider-detail model-configurations model-configuration-detail model-deployments model-deployment-detail routing-policies".split(),
    "WP-0020": "knowledge-directory knowledge-overview knowledge-sources source-add source-detail processing-quality sync-result dataset-schema object-storage-scope quick-publication".split(),
    "WP-0021": "evaluation-datasets evaluation-dataset-detail evaluation-run-create evaluation-run-detail evaluation-comparison evaluation-failure-analysis".split(),
    "WP-0022": "index-versions index-detail index-rebuild".split(),
    "WP-0023": "assistants-directory assistant-create assistant-overview assistant-draft assistant-test assistant-evaluation assistant-release".split(),
    "WP-0024": "login assistants conversation".split(),
    "WP-0025": "governance-query usage-quotas audit-records audit-record-detail query-diagnostic".split(),
    "WP-0027": ["platform-overview"],
}
PAGE_OWNERS = {
    screen: package for package, screens in PACKAGE_SCREENS.items() for screen in screens
}
USER_DEFERRED = {"channel-binding", "device-credentials"}
PROTOTYPE_OPERATIONS = {"console-login:1", "tenant-login:2", "login:1"}
CROSS_PACKAGE_OPERATIONS = {
    "source-detail:4": ("WP-0022", ["WP-0020"]),
    "source-detail:5": ("WP-0022", ["WP-0020"]),
    "sync-result:0": ("WP-0022", ["WP-0020"]),
    "quick-publication:0": ("WP-0023", ["WP-0020", "WP-0022"]),
}


def evidence_index_bindings(text):
    """只接受工作包标题下证据表格中的明确文件和完整提交，不从叙述推断绑定。"""
    return governance.evidence_bindings(text)



def repository_facts(root, mapping=None):
    """只核验矩阵引用的提交；工作包完成合法性仍由项目治理门禁核验。"""
    if mapping is None:
        mapping = json.loads((root / MAP_PATH).read_text(encoding="utf-8"))
    referenced_commits = {
        operation["verifiedCommit"]
        for page in mapping.get("pages", []) for operation in page.get("operations", [])
        if isinstance(operation.get("verifiedCommit"), str)
    }
    verified_commits = set()
    for commit in sorted(referenced_commits):
        if re.fullmatch(r"[0-9a-f]{40}", commit) is None:
            continue
        object_type = subprocess.run(
            ["git", "cat-file", "-t", commit], cwd=root,
            capture_output=True, text=True, check=False, timeout=10,
        )
        if object_type.returncode != 0 or object_type.stdout.strip() != "commit":
            continue
        ancestor = subprocess.run(
            ["git", "merge-base", "--is-ancestor", commit, "HEAD"], cwd=root,
            capture_output=True, text=True, check=False, timeout=10,
        )
        if ancestor.returncode == 0:
            verified_commits.add(commit)
    history = governance.HistoryStore(root)
    history.verify()
    packages = governance.load_packages(root, history)
    return {
        "verified_commits": verified_commits,
        "evidence_bindings": governance.historical_evidence_bindings(history) | governance.current_evidence_bindings(root),
        "accepted_adrs": {
            adr.identifier for adr in governance.adr_decisions(root)
            if governance.is_confirmed_decision(adr.status)
        },
        "existing_evidence": {
            path.relative_to(root).as_posix()
            for path in (root / "docs/tasks").rglob("*") if path.is_file()
        } | {name for name in history.entries if name.startswith("project/evidence/")},
        "completed_packages": {p.identifier for p in packages.values() if p.status == "done"},
    }



def validate_implementation_map(mapping, inventory, ledger, *, accepted_adrs=(),
                                existing_evidence=(), completed_packages=(), verified_commits=(),
                                evidence_bindings=()):
    """按调用方提供的已接受 ADR、现存证据和完成工作包事实校验承接进度。"""
    errors = []

    def require(condition, message):
        if not condition:
            errors.append(message)

    def text_list(value):
        return isinstance(value, list) and bool(value) and all(
            isinstance(item, str) and item.strip() for item in value)

    def validate_progress(operation, classification, dependencies):
        op_id, status = operation.get("id"), operation.get("status")
        require(classification != "production" or operation.get("workPackage") not in completed_packages
                or status == "verified", f"{op_id} 已完成责任包仍有未验证生产操作")
        require(not any(key in operation for key in ("api", "endpoint", "productionEvidence")),
                f"{op_id} 未冻结契约不得用零散接口或证据字段替代结构化事实")
        if classification != "production" or status == "pending-contract":
            require(status == ("excluded" if classification != "production" else "pending-contract"),
                    f"{op_id} 不得编造生产完成事实")
            require(not any(key in operation for key in ("contract", "verification", "verifiedCommit")),
                    f"{op_id} 未冻结契约或排除项不得提前登记实施证据")
            return
        require(status in {"contracted", "implemented", "verified"}, f"{op_id} 不得编造生产完成事实")
        contract = operation.get("contract")
        if not isinstance(contract, dict):
            require(False, f"{op_id} 缺少完整生产契约")
            return
        target = contract.get("target")
        require(isinstance(target, dict) and target.get("kind") in {"api", "navigation"}
                and isinstance(target.get("value"), str) and bool(target["value"].strip())
                and isinstance(contract.get("saveBoundary"), str) and bool(contract["saveBoundary"].strip())
                and text_list(contract.get("authorizationTests")), f"{op_id} 缺少完整生产契约")
        require(contract.get("adr") in accepted_adrs, f"{op_id} 引用了未接受的 ADR")
        if status == "contracted":
            require(not any(key in operation for key in ("verification", "verifiedCommit")),
                    f"{op_id} 仅冻结契约时不得登记实施证据")
        if status in {"implemented", "verified"}:
            verification = operation.get("verification")
            if not isinstance(verification, dict):
                require(False, f"{op_id} 缺少验证命令与证据")
            else:
                require(text_list(verification.get("commands")) and text_list(verification.get("evidence")),
                        f"{op_id} 缺少验证命令与证据")
                if text_list(verification.get("evidence")):
                    require(all(ref in existing_evidence for ref in verification["evidence"]),
                            f"{op_id} 验证证据不存在")
        if status == "verified":
            commit = operation.get("verifiedCommit")
            require(isinstance(commit, str) and re.fullmatch(r"[0-9a-f]{40}", commit) is not None,
                    f"{op_id} 缺少完整提交标识")
            require(isinstance(commit, str) and commit in verified_commits,
                    f"{op_id} 提交不存在、不是 commit 对象或不属于当前 HEAD 历史")
            verification = operation.get("verification")
            evidence = verification.get("evidence") if isinstance(verification, dict) else None
            require(isinstance(commit, str) and text_list(evidence) and any(
                (operation.get("workPackage"), reference, commit) in evidence_bindings
                and re.search(r"(?:^|/)WP-0016(?:[-/.]|$)", reference) is None
                for reference in evidence), f"{op_id} 证据未按责任工作包绑定提交，原型证据不能证明生产完成")
            require(set(dependencies).issubset(completed_packages), f"{op_id} 依赖工作包尚未完成")
        else:
            require("verifiedCommit" not in operation, f"{op_id} 尚未验证不得登记完成提交")

    inventory_pages = {
        page["screen"]: (group["phase"], f"/groups/{group_index}/pages/{page_index}")
        for group_index, group in enumerate(inventory["groups"])
        for page_index, page in enumerate(group["pages"])
    }
    ledger_pages = {
        page["screen"]: (index, page)
        for index, page in enumerate(ledger["pages"])
    }
    pages = mapping.get("pages", [])
    require(mapping.get("schemaVersion") == 1, "承接矩阵版本不支持")
    require(set(inventory_pages) == set(ledger_pages), "原型页面清单与操作台账不一致")
    require(Counter(page.get("screen") for page in pages) == Counter(inventory_pages.keys()),
            "页面存在遗漏、重复或未知入口")
    expected_operation_ids = {
        f"{screen}:{index}"
        for screen, (_, page) in ledger_pages.items()
        for index in range(len(page["controls"]))
    }
    actual_operation_ids = []
    for page in pages:
        screen = page.get("screen")
        if screen not in inventory_pages or screen not in ledger_pages:
            continue
        phase, inventory_pointer = inventory_pages[screen]
        ledger_index, source = ledger_pages[screen]
        page_class = "deferred" if screen in USER_DEFERRED else (
            "production" if phase == "M6" else "later-milestone")
        require(page.get("inventoryPointer") == inventory_pointer, f"{screen} 页面来源引用错误")
        require(page.get("ledgerPointer") == f"/pages/{ledger_index}", f"{screen} 操作页引用错误")
        require(page.get("phase") == phase, f"{screen} 原型阶段不一致")
        require(page.get("classification") == page_class, f"{screen} 页面范围分类错误")
        require(page.get("workPackage") == PAGE_OWNERS.get(screen), f"{screen} 页面责任包错误")
        controls = page.get("operations", [])
        production_controls = [operation for operation in controls if operation.get("classification") == "production"]
        page_status = "excluded"
        if page_class == "production":
            states = {operation.get("status") for operation in production_controls}
            page_status = "verified" if states == {"verified"} else (
                "pending-contract" if states == {"pending-contract"} else "in-progress")
        require(page.get("status") == page_status, f"{screen} 页面状态必须汇总全部生产操作，不得提前完成")
        require(Counter(op.get("sourceControlIndex") for op in controls) == Counter(range(len(source["controls"]))),
                f"{screen} 操作存在遗漏、重复或未知索引")
        for operation in controls:
            op_id = operation.get("id")
            actual_operation_ids.append(op_id)
            index = operation.get("sourceControlIndex")
            if not isinstance(index, int) or index < 0 or index >= len(source["controls"]):
                continue
            source_control = source["controls"][index]
            expected_id = f"{screen}:{index}"
            require(op_id == expected_id, f"{screen} 操作稳定标识与索引不一致")
            require(operation.get("label") == source_control["label"], f"{expected_id} 原型操作文本发生漂移")
            if source_control["status"] == "forbidden":
                classification, reason = "forbidden", "role-boundary"
            elif source_control.get("acceptanceExcluded"):
                classification, reason = "deferred", "user-deferred"
            elif expected_id in PROTOTYPE_OPERATIONS:
                classification, reason = "prototype-only", "demo-login-repair"
            elif phase != "M6":
                classification, reason = "deferred", "later-milestone"
            else:
                classification, reason = "production", "m6-approved-prototype"
            require(operation.get("classification") == classification, f"{expected_id} 操作范围分类错误")
            require(operation.get("reason") == reason, f"{expected_id} 操作范围依据错误")
            expected_owner, dependencies = CROSS_PACKAGE_OPERATIONS.get(expected_id, (PAGE_OWNERS.get(screen), []))
            if classification != "production":
                expected_owner, dependencies = None, []
            require(operation.get("workPackage") == expected_owner, f"{expected_id} 操作责任包错误")
            require(operation.get("dependsOn") == dependencies, f"{expected_id} 跨包交接依赖错误")
            validate_progress(operation, classification, dependencies)
    require(Counter(actual_operation_ids) == Counter(expected_operation_ids), "操作稳定标识存在遗漏或重复")
    return errors


class M6ImplementationMapTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.mapping = json.loads((ROOT / MAP_PATH).read_text(encoding="utf-8"))
        cls.inventory = json.loads((ROOT / INVENTORY_PATH).read_text(encoding="utf-8"))
        cls.ledger = json.loads((ROOT / LEDGER_PATH).read_text(encoding="utf-8"))
        cls.facts = repository_facts(ROOT)

    def assert_invalid(self, change, expected):
        mapping = copy.deepcopy(self.mapping)
        change(mapping)
        errors = validate_implementation_map(mapping, self.inventory, self.ledger, **self.facts)
        self.assertTrue(any(expected in error for error in errors), errors)

    def test_complete_map_matches_approved_prototype(self):
        self.assertEqual([], validate_implementation_map(self.mapping, self.inventory, self.ledger, **self.facts))
        self.assertEqual(78, len(self.mapping["pages"]))
        self.assertEqual(155, sum(len(page["operations"]) for page in self.mapping["pages"]))
        self.assertEqual(Counter({"production": 66, "later-milestone": 10, "deferred": 2}),
                         Counter(page["classification"] for page in self.mapping["pages"]))
        self.assertEqual(Counter({"production": 132, "deferred": 19, "prototype-only": 3, "forbidden": 1}),
                         Counter(op["classification"] for page in self.mapping["pages"] for op in page["operations"]))

    def test_source_hashes_pin_the_human_approved_baseline(self):
        self.assertEqual({INVENTORY_PATH, LEDGER_PATH}, {source["path"] for source in self.mapping["sources"]})
        for source in self.mapping["sources"]:
            self.assertEqual(source["sha256"], hashlib.sha256((ROOT / source["path"]).read_bytes()).hexdigest())

    def test_open_design_project_root_remains_available(self):
        # 导入项目的目录不可改绑；入口必须在原项目的真实目录中。
        project_root = ROOT / "docs/prototypes/wp-0016"
        entry = project_root / "index.html"
        self.assertFalse(project_root.is_symlink())
        self.assertTrue(entry.is_file())
        self.assertFalse(entry.is_symlink())
        self.assertEqual(
            "718a5bee8606607fc6eabf353a47eb79556d7effa51f2d45f207ad6be2db65ec",
            hashlib.sha256(entry.read_bytes()).hexdigest(),
        )

    def test_missing_or_duplicate_page_is_rejected(self):
        self.assert_invalid(lambda mapping: mapping["pages"].pop(), "页面存在遗漏")
        self.assert_invalid(lambda mapping: mapping["pages"].append(mapping["pages"][0]), "页面存在遗漏")

    def test_platform_overview_keeps_m6_scope_with_a_late_owner(self):
        page = next(page for page in self.mapping["pages"] if page["screen"] == "platform-overview")
        self.assertEqual(("M6", "production", "WP-0027"),
                         (page["phase"], page["classification"], page["workPackage"]))
        self.assertEqual([("platform-overview:0", "WP-0027")],
                         [(op["id"], op["workPackage"]) for op in page["operations"]])
        self.assertNotEqual("excluded", page["status"])

    def test_completed_package_cannot_hide_unverified_production(self):
        mapping = copy.deepcopy(self.mapping)
        page = next(page for page in mapping["pages"] if page["screen"] == "platform-overview")
        page["status"] = "pending-contract"
        for operation in page["operations"]:
            operation["status"] = "pending-contract"
            for field in ("contract", "verification", "verifiedCommit"):
                operation.pop(field, None)
        facts = copy.deepcopy(self.facts)
        facts["completed_packages"].add(page["workPackage"])
        errors = validate_implementation_map(mapping, self.inventory, self.ledger, **facts)
        self.assertTrue(any("已完成责任包仍有未验证生产操作" in error for error in errors), errors)

    def test_missing_or_duplicate_operation_is_rejected(self):
        self.assert_invalid(lambda mapping: mapping["pages"][0]["operations"].pop(), "操作存在遗漏")
        self.assert_invalid(lambda mapping: mapping["pages"][0]["operations"].append(mapping["pages"][0]["operations"][0]), "操作存在遗漏")

    def test_unknown_source_reference_is_rejected(self):
        self.assert_invalid(lambda mapping: mapping["pages"][0].update(inventoryPointer="/groups/999/pages/0"), "来源引用错误")
        self.assert_invalid(lambda mapping: mapping["pages"][0]["operations"][0].update(sourceControlIndex=999), "未知索引")

    def test_demo_deferred_and_forbidden_cannot_become_production(self):
        for target in ("console-login:1", "tenant-members:3", "tenant-members:6", "channel-binding:0", "device-credentials:0", "device-start:0"):
            def promote(mapping):
                next(op for page in mapping["pages"] for op in page["operations"] if op["id"] == target)["classification"] = "production"
            self.assert_invalid(promote, "操作范围分类错误")

    def test_cross_package_tasks_cannot_close_with_source_page(self):
        for target in CROSS_PACKAGE_OPERATIONS:
            def assign_to_source_page(mapping):
                page = next(page for page in mapping["pages"] if page["screen"] == target.split(":")[0])
                next(op for op in page["operations"] if op["id"] == target)["workPackage"] = page["workPackage"]
            self.assert_invalid(assign_to_source_page, "操作责任包错误")

    def test_prototype_success_cannot_become_production_evidence(self):
        self.assert_invalid(lambda mapping: mapping["pages"][0]["operations"][0].update(status="completed"), "编造生产完成事实")
        self.assert_invalid(lambda mapping: mapping["pages"][0]["operations"][0].update(api="POST /unapproved"), "未冻结契约")

    def progress_fixture(self, screen, status):
        """仅构造内存反例，不向仓库写入不存在的 ADR、接口或验收证据。"""
        mapping = copy.deepcopy(self.mapping)
        page = next(page for page in mapping["pages"] if page["screen"] == screen)
        for operation in page["operations"]:
            if operation["classification"] != "production":
                continue
            # 仓库入口可已交付；反例必须清除真实进度，再构造待测阶段。
            operation.pop("verification", None)
            operation.pop("verifiedCommit", None)
            operation.update(status=status, contract={
                "adr": "ADR-9999",
                "target": {"kind": "api", "value": "POST /unit-test-only"},
                "saveBoundary": "仅用于守卫单元测试的独立命令边界",
                "authorizationTests": ["tests/unit_test_only.py:test_denied"],
            })
            if status in {"implemented", "verified"}:
                operation["verification"] = {
                    "commands": ["python3 -m unittest tests.unit_test_only"],
                    "evidence": ["project/evidence/unit-test-only.md"],
                }
            if status == "verified":
                operation["verifiedCommit"] = "a" * 40
        page["status"] = "verified" if status == "verified" else "in-progress"
        facts = {
            "verified_commits": self.facts["verified_commits"] | {"a" * 40},
            "evidence_bindings": self.facts["evidence_bindings"] | {
                (operation["workPackage"], "project/evidence/unit-test-only.md", "a" * 40)
                for operation in page["operations"] if operation["classification"] == "production"
            },
            "accepted_adrs": self.facts["accepted_adrs"] | {"ADR-9999"},
            "existing_evidence": self.facts["existing_evidence"] | {"project/evidence/unit-test-only.md"},
            "completed_packages": self.facts["completed_packages"] - {
                operation["workPackage"] for operation in page["operations"]
            },
        }
        return mapping, page, facts

    def test_real_contract_and_evidence_allow_progress(self):
        for status in ("contracted", "implemented", "verified"):
            with self.subTest(status=status):
                mapping, _, facts = self.progress_fixture("console-login", status)
                self.assertEqual([], validate_implementation_map(mapping, self.inventory, self.ledger, **facts))

    def test_progress_without_accepted_contract_is_rejected(self):
        mapping, page, facts = self.progress_fixture("console-login", "contracted")
        facts["accepted_adrs"] = set()
        errors = validate_implementation_map(mapping, self.inventory, self.ledger, **facts)
        self.assertTrue(any("未接受的 ADR" in error for error in errors), errors)
        page["operations"][0].pop("contract")
        errors = validate_implementation_map(mapping, self.inventory, self.ledger, **facts)
        self.assertTrue(any("缺少完整生产契约" in error for error in errors), errors)

    def test_verified_requires_existing_evidence_and_full_commit(self):
        mapping, page, facts = self.progress_fixture("console-login", "verified")
        facts["existing_evidence"] = set()
        page["operations"][0]["verifiedCommit"] = "abc1234"
        errors = validate_implementation_map(mapping, self.inventory, self.ledger, **facts)
        self.assertTrue(any("证据不存在" in error for error in errors), errors)
        self.assertTrue(any("完整提交" in error for error in errors), errors)

    def test_implemented_requires_commands_and_evidence(self):
        mapping, page, facts = self.progress_fixture("console-login", "implemented")
        page["operations"][0].pop("verification")
        errors = validate_implementation_map(mapping, self.inventory, self.ledger, **facts)
        self.assertTrue(any("缺少验证命令与证据" in error for error in errors), errors)

    def test_navigation_contract_can_progress_without_write_api(self):
        mapping, page, facts = self.progress_fixture("platform-roles", "contracted")
        page["operations"][0]["contract"].update(
            target={"kind": "navigation", "value": "/unit-test-accounts"},
            saveBoundary="只改变导航，不执行写入",
        )
        self.assertEqual([], validate_implementation_map(mapping, self.inventory, self.ledger, **facts))

    def test_forty_zeroes_are_not_a_verified_commit(self):
        mapping, page, facts = self.progress_fixture("console-login", "verified")
        page["operations"][0]["verifiedCommit"] = "0" * 40
        errors = validate_implementation_map(mapping, self.inventory, self.ledger, **facts)
        self.assertTrue(any("提交不存在" in error for error in errors), errors)

    def test_repository_facts_verify_only_referenced_real_commits(self):
        current_head = subprocess.check_output(
            ["git", "rev-parse", "HEAD"], cwd=ROOT, text=True,
        ).strip()
        mapping = {"pages": [{"operations": [{"verifiedCommit": current_head}]}]}
        facts = repository_facts(ROOT, mapping)
        self.assertIn(current_head, facts["verified_commits"])
        complete, page, contract_facts = self.progress_fixture("console-login", "verified")
        page["operations"][0]["verifiedCommit"] = current_head
        contract_facts["verified_commits"] |= facts["verified_commits"]
        contract_facts["evidence_bindings"].add(("WP-0017", "project/evidence/unit-test-only.md", current_head))
        self.assertEqual([], validate_implementation_map(complete, self.inventory, self.ledger, **contract_facts))
        mapping["pages"][0]["operations"][0]["verifiedCommit"] = "0" * 40
        facts = repository_facts(ROOT, mapping)
        self.assertNotIn("0" * 40, facts["verified_commits"])
        self.assertNotIn(current_head, facts["verified_commits"])

    def test_prototype_evidence_cannot_verify_a_production_operation(self):
        mapping, page, facts = self.progress_fixture("console-login", "verified")
        operation = page["operations"][0]
        operation["verification"]["evidence"] = ["project/evidence/WP-0016-human-acceptance-20260913.md"]
        errors = validate_implementation_map(mapping, self.inventory, self.ledger, **facts)
        self.assertTrue(any("证据未按责任工作包绑定提交" in error for error in errors), errors)
        facts["evidence_bindings"].add(("WP-0017", operation["verification"]["evidence"][0], "a" * 40))
        errors = validate_implementation_map(mapping, self.inventory, self.ledger, **facts)
        self.assertTrue(any("原型证据不能证明生产完成" in error for error in errors), errors)

    def test_evidence_index_requires_matching_owner_and_commit(self):
        mapping, _, facts = self.progress_fixture("console-login", "verified")
        for binding in (
            ("WP-0018", "project/evidence/unit-test-only.md", "a" * 40),
            ("WP-0017", "project/evidence/unit-test-only.md", "b" * 40),
        ):
            facts["evidence_bindings"] = {binding}
            errors = validate_implementation_map(mapping, self.inventory, self.ledger, **facts)
            self.assertTrue(any("证据未按责任工作包绑定提交" in error for error in errors), errors)

    def test_evidence_index_parses_only_registered_full_commit_bindings(self):
        text = """## WP-0017
| 证据 | 命令或评审 | 提交 | 结果 |
| 正例 | `project/evidence/unit-test-only.md` | `aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa` | 通过 |
| 未提交 | `project/evidence/uncommitted.md` | 待产生 | 待验证 |
## WP-0016
| 原型 | `project/evidence/prototype-only.md` | `bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb` | 原型通过 |
"""
        self.assertEqual({
            ("WP-0017", "project/evidence/unit-test-only.md", "a" * 40),
            ("WP-0016", "project/evidence/prototype-only.md", "b" * 40),
        }, evidence_index_bindings(text))

    def test_page_cannot_finish_before_all_production_operations(self):
        mapping, page, facts = self.progress_fixture("platform-accounts", "verified")
        page["operations"][0]["status"] = "implemented"
        page["operations"][0].pop("verifiedCommit")
        errors = validate_implementation_map(mapping, self.inventory, self.ledger, **facts)
        self.assertTrue(any("页面状态必须汇总" in error for error in errors), errors)

    def test_cross_package_completion_requires_real_dependency_facts(self):
        mapping, _, facts = self.progress_fixture("quick-publication", "verified")
        facts["completed_packages"] -= {"WP-0020", "WP-0022"}
        errors = validate_implementation_map(mapping, self.inventory, self.ledger, **facts)
        self.assertTrue(any("依赖工作包尚未完成" in error for error in errors), errors)
        facts["completed_packages"] |= {"WP-0020", "WP-0022"}
        # 正例同步建立前置包的全部操作事实，不能只翻转完成标记。
        source = next(op for page in mapping["pages"] for op in page["operations"]
                      if op["id"] == "quick-publication:0")
        for page in mapping["pages"]:
            for operation in page["operations"]:
                if operation["classification"] != "production" or operation["workPackage"] not in {"WP-0020", "WP-0022"}:
                    continue
                operation.update(status="verified", contract=copy.deepcopy(source["contract"]),
                                 verification=copy.deepcopy(source["verification"]), verifiedCommit="a" * 40)
                facts["evidence_bindings"].add((operation["workPackage"], "project/evidence/unit-test-only.md", "a" * 40))
            if page["classification"] == "production":
                states = {op["status"] for op in page["operations"] if op["classification"] == "production"}
                page["status"] = "verified" if states == {"verified"} else (
                    "pending-contract" if states == {"pending-contract"} else "in-progress")
        self.assertEqual([], validate_implementation_map(mapping, self.inventory, self.ledger, **facts))

    def test_contract_progress_does_not_accept_unstructured_api_claims(self):
        mapping, page, facts = self.progress_fixture("console-login", "contracted")
        operation = page["operations"][0]
        operation["contract"]["authorizationTests"] = []
        operation["api"] = "POST /unapproved"
        errors = validate_implementation_map(mapping, self.inventory, self.ledger, **facts)
        self.assertTrue(any("缺少完整生产契约" in error for error in errors), errors)
        self.assertTrue(any("未冻结契约" in error for error in errors), errors)


if __name__ == "__main__":
    unittest.main()
