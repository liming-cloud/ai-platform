"""控制面行为测试：当前指针、证据、人工门禁、恢复预算和只读查询。"""

from pathlib import Path
import tempfile
import unittest
from unittest import mock

from scripts import project_governance as g

SHA = 'a' * 40


class ProjectGovernanceTest(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.root = Path(self.temp.name)
        for name in g.REQUIRED_DESIGN_FILES:
            self.put(name, '# 测试\n')
        self.put('docs/architecture/repository.md', '# 目录\n' + ''.join(
            f'\n## {heading}\n正文\n' for heading in g.REQUIRED_REPOSITORY_LAYOUT_SECTIONS))
        self.write_adr('ADR-0001')
        self.write_state()
        self.write_task('WP-0001')
        self.sync()

    def put(self, name, text):
        path = self.root / name
        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_text(text)
        return path

    def write_state(self, product='WP-0001', task='WP-0001', milestone='M0'):
        self.put('docs/tasks/STATE.yaml', f'''schemaVersion: 2
productVersion: foundation-v1
currentMilestone: {milestone}
productFrontier: {product}
currentTask: {task}
updatedAt: 2026-09-21
updatedBy: test
''')

    def write_adr(self, identifier, status='已接受'):
        self.put(f'docs/architecture/decisions/{identifier}-test.md',
                 f'# {identifier}：测试\n\n## 状态\n\n{status}\n')

    def write_task(self, identifier, status='进行中', owner='后端', adrs='ADR-0001', evidence='pending',
                   commit='pending', human='不适用', milestone='M0', extra=''):
        return self.put(f'docs/tasks/{identifier}/README.md', f'''# {identifier}：测试

| 字段 | 值 |
| --- | --- |
| ID | {identifier} |
| 状态 | {status} |
| 负责人 | {owner} |
| 分支 | codex/test |
| 里程碑 | {milestone} |
| ADR | {adrs} |
| 证据 | {evidence} |
| 提交 | {commit} |
| 输入提交 | {SHA} |
| 基线提交 | {SHA} |
| 人工验收 | {human} |
{extra}

''' + ''.join(f'## {section}\n\n内容\n\n' for section in sorted(g.REQUIRED_WORK_PACKAGE_SECTIONS)))

    def sync(self):
        self.put('docs/architecture/decisions/README.md', g.render_decision_index(self.root))
        state = g.parse_state(self.root / 'docs/tasks/STATE.yaml')
        packages = g.load_packages(self.root, g.HistoryStore(self.root))
        self.put('docs/tasks/README.md', g.render_task_index(self.root, state, packages))

    def verify(self):
        self.sync()
        return g.verify(self.root)

    def test_valid_control_plane_passes(self):
        state, packages = self.verify()
        self.assertEqual('WP-0001', state['productFrontier'])
        self.assertIn('WP-0001', packages)

    def test_product_frontier_and_current_task_are_independent(self):
        self.write_task('GOV-0001', owner='治理')
        self.write_state(task='GOV-0001')
        state, _ = self.verify()
        self.assertEqual('WP-0001', state['productFrontier'])
        self.assertEqual('GOV-0001', state['currentTask'])

    def test_governance_task_cannot_be_product_frontier(self):
        self.write_task('GOV-0001', owner='治理')
        self.write_state(product='GOV-0001', task='GOV-0001')
        with self.assertRaisesRegex(g.GovernanceError, '产品前沿必须是 WP'):
            self.verify()

    def test_rejects_parallel_old_state(self):
        self.put('project/STATE.yaml', 'schemaVersion: 1\n')
        with self.assertRaisesRegex(g.GovernanceError, '两个状态源'):
            self.verify()

    def test_rejects_unknown_state_fields(self):
        path = self.root / 'docs/tasks/STATE.yaml'
        path.write_text(path.read_text() + 'lastVerifiedCommit: ' + SHA + '\n')
        with self.assertRaisesRegex(g.GovernanceError, '未知字段'):
            g.verify(self.root)

    def test_missing_work_package_fails(self):
        self.write_state(task='GOV-9999')
        with self.assertRaisesRegex(g.GovernanceError, '不存在或无效的工作包'):
            self.verify()

    def test_one_owner_cannot_have_multiple_active_packages(self):
        self.write_task('WP-0002')
        with self.assertRaisesRegex(g.GovernanceError, '多个进行中的工作包'):
            self.verify()

    def test_unaccepted_adr_is_rejected(self):
        self.write_adr('ADR-0001', status='提议')
        with self.assertRaisesRegex(g.GovernanceError, '未接受的 ADR'):
            self.verify()

    def test_done_package_requires_commit_bound_evidence(self):
        self.write_task('GOV-0001', owner='治理', status='已完成')
        with self.assertRaisesRegex(g.GovernanceError, '已完成但没有证据'):
            self.verify()

    def completed(self, human='不适用', extra=''):
        evidence = 'docs/tasks/GOV-0001/verification.md'
        self.put(evidence, f'# 证据\n\n| 字段 | 值 |\n| --- | --- |\n| 责任任务 | GOV-0001 |\n| 验证提交 | {SHA} |\n| 验证范围 | 仅文档治理 |\n')
        return self.write_task('GOV-0001', owner='治理', status='已完成', commit=SHA, evidence=evidence,
                               human=human, extra=extra)

    def test_technical_completion_does_not_add_human_gate(self):
        self.completed()
        self.verify()

    def test_done_package_rejects_mismatched_evidence_binding(self):
        self.completed()
        p = self.root / 'docs/tasks/GOV-0001/verification.md'
        p.write_text(p.read_text().replace('GOV-0001', 'WP-0001'))
        with self.assertRaisesRegex(g.GovernanceError, '证据未绑定'):
            self.verify()

    def test_done_package_requires_scope(self):
        self.completed()
        p = self.root / 'docs/tasks/GOV-0001/verification.md'
        p.write_text(p.read_text().replace('| 验证范围 | 仅文档治理 |\n', ''))
        with self.assertRaisesRegex(g.GovernanceError, '验证范围'):
            self.verify()

    def test_done_package_rejects_missing_human_acceptance(self):
        self.completed(human='待人工验收')
        with self.assertRaisesRegex(g.GovernanceError, '人工接受'):
            self.verify()

    def test_milestone_cannot_disable_human_gate(self):
        self.completed(extra='| 任务类型 | 里程碑 |')
        with self.assertRaisesRegex(g.GovernanceError, '人工接受'):
            self.verify()

    def test_done_package_rejects_stale_active_statement(self):
        p = self.completed()
        p.write_text(p.read_text() + '\n本工作包保持进行中。\n')
        with self.assertRaisesRegex(g.GovernanceError, '正文仍声明进行中'):
            self.verify()

    def test_rejects_ignored_screenshot_in_docs(self):
        self.put('docs/tasks/GOV-0001/screenshot.PNG', 'image')
        with self.assertRaisesRegex(g.GovernanceError, '截图不得保存在仓库'):
            self.verify()

    def test_rejects_tracked_screenshot_even_if_missing_from_worktree(self):
        (self.root / '.git').mkdir()
        with mock.patch.object(g, 'tracked_files', return_value={'screenshots/login.webp'}):
            with self.assertRaisesRegex(g.GovernanceError, '截图不得保存在仓库'):
                self.verify()

    def test_allows_product_image_outside_docs(self):
        self.put('assets/logo.png', 'asset')
        self.verify()

    def test_repository_layout_requires_boundary_sections(self):
        self.put('docs/architecture/repository.md', '# 不完整\n')
        with self.assertRaisesRegex(g.GovernanceError, '详细目录规划缺少章节'):
            self.verify()

    def test_backend_constraints_are_required(self):
        (self.root / 'docs/architecture/backend.md').unlink()
        with self.assertRaisesRegex(g.GovernanceError, '缺少基座设计文件'):
            self.verify()

    def test_m1_requires_context_modules(self):
        self.write_state(milestone='M1')
        self.write_task('WP-0001', milestone='M1')
        with self.assertRaisesRegex(g.GovernanceError, 'M1 缺少限界上下文模块'):
            self.verify()

    def test_m0_rejects_legacy_tracked_source(self):
        (self.root / '.git').mkdir()
        with (mock.patch.object(g, 'tracked_files', return_value={'backend/pom.xml'}),
              mock.patch.object(g, 'git_commit_exists', return_value=True),
              mock.patch.object(g, 'is_ancestor', return_value=True)):
            with self.assertRaisesRegex(g.GovernanceError, '仍包含旧实现文件'):
                self.verify()

    def test_latest_evidence_is_limited_to_eight_entries(self):
        p = self.root / 'docs/tasks/WP-0001/README.md'
        refs = '\n'.join(f'- docs/tasks/WP-0001/evidence-{i}.md' for i in range(9))
        p.write_text(p.read_text().replace('## 证据\n\n内容', '## 证据\n\n' + refs))
        with self.assertRaisesRegex(g.GovernanceError, '最多保留 8 条'):
            self.verify()

    def test_frontier_is_limited_to_twelve_active_adrs(self):
        ids = [f'ADR-{i:04d}' for i in range(1, 14)]
        for identifier in ids:
            self.write_adr(identifier)
        self.write_task('WP-0001', adrs=', '.join(ids))
        with self.assertRaisesRegex(g.GovernanceError, '最多引用 12 个 ADR'):
            self.verify()

    def test_generated_indexes_cannot_be_manually_changed(self):
        for name in ['docs/architecture/decisions/README.md', 'docs/tasks/README.md']:
            self.sync()
            p = self.root / name
            p.write_text(p.read_text() + '\n伪造完成\n')
            with self.assertRaisesRegex(g.GovernanceError, '生成索引与事实不一致'):
                g.verify(self.root)

    def test_combined_status_and_date_is_supported(self):
        p = self.put('docs/architecture/decisions/ADR-0001-test.md',
                     '# ADR-0001：测试\n\n## 状态和日期\n\n- 状态：已接受\n- 日期：2026-09-21\n')
        self.assertEqual('已接受', g.parse_adr(p).status)

    def test_resume_context_has_twelve_kibibyte_budget(self):
        p = self.root / 'docs/tasks/WP-0001/README.md'
        p.write_text(p.read_text() + '\n' + '长上下文' * 4096)
        with self.assertRaisesRegex(g.GovernanceError, '恢复上下文超过 12 KiB'):
            self.verify()

    def test_current_markdown_link_must_exist(self):
        self.put('docs/design/product-and-ui.md', '[不存在](missing.md)\n')
        with self.assertRaisesRegex(g.GovernanceError, '当前链接失效'):
            self.verify()

    def test_query_does_not_write_files(self):
        before = {str(p): p.read_bytes() for p in self.root.rglob('*') if p.is_file()}
        g.render_status(self.root, resume=True)
        after = {str(p): p.read_bytes() for p in self.root.rglob('*') if p.is_file()}
        self.assertEqual(before, after)

    def test_duplicate_task_cannot_hide_in_backlog(self):
        self.put('docs/tasks/backlog/WP-0001.md', (self.root / 'docs/tasks/WP-0001/README.md').read_text())
        with self.assertRaisesRegex(g.GovernanceError, '编号重复'):
            g.verify(self.root)

    def test_future_milestone_requires_matching_task(self):
        self.write_state(milestone='M7')
        with self.assertRaisesRegex(g.GovernanceError, '里程碑不一致'):
            self.verify()

    def test_new_evidence_is_available_to_the_implementation_matrix(self):
        self.completed()
        bindings = g.current_evidence_bindings(self.root)
        self.assertIn(('GOV-0001', 'docs/tasks/GOV-0001/verification.md', SHA), bindings)

    def test_new_evidence_without_scope_cannot_bind_production_progress(self):
        self.completed()
        p = self.root / 'docs/tasks/GOV-0001/verification.md'
        p.write_text(p.read_text().replace('| 验证范围 | 仅文档治理 |\n', ''))
        self.assertEqual(set(), g.current_evidence_bindings(self.root))

    def test_milestone_advance_requires_previous_human_gate(self):
        self.write_task('WP-0014', owner='里程碑', milestone='M6',
                        extra='| 任务类型 | 里程碑 |', human='待人工验收')
        packages = g.load_packages(self.root, g.HistoryStore(self.root))
        with self.assertRaisesRegex(g.GovernanceError, '人工门禁'):
            g.verify_milestone_transition('M6', 'M7', packages)

    def test_milestone_advance_cannot_skip_unfinished_product_package(self):
        self.write_task('WP-0014', owner='里程碑', milestone='M6', status='已完成',
                        extra='| 任务类型 | 里程碑 |', human='已接受')
        self.write_task('WP-0017', owner='业务', milestone='M6')
        packages = g.load_packages(self.root, g.HistoryStore(self.root))
        with self.assertRaisesRegex(g.GovernanceError, '未完成'):
            g.verify_milestone_transition('M6', 'M7', packages)
