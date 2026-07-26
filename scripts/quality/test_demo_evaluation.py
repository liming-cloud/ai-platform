import hashlib
import importlib.util
import json
import sys
import tempfile
import unittest
from pathlib import Path


SCRIPT = Path(__file__).with_name("demo_evaluation.py")
SPEC = importlib.util.spec_from_file_location("demo_evaluation", SCRIPT)
MODULE = importlib.util.module_from_spec(SPEC)
assert SPEC.loader is not None
sys.modules[SPEC.name] = MODULE
SPEC.loader.exec_module(MODULE)


class DemoEvaluationTest(unittest.TestCase):
    def setUp(self):
        self.temp_dir = tempfile.TemporaryDirectory()
        self.root = Path(self.temp_dir.name)
        fixture_dir = self.root / "verification/fixtures/store"
        fixture_dir.mkdir(parents=True)
        content = (
            "[合成数据]\n"
            "[记录: product-001]\n"
            "名称: 青柚气泡水\n"
            "位置: 一层 A 区\n"
        )
        knowledge_path = fixture_dir / "knowledge.txt"
        knowledge_path.write_text(content, encoding="utf-8")
        cases = {
            "schemaVersion": 1,
            "cases": [
                {
                    "id": "store-search-001",
                    "query": "青柚气泡水在哪里？",
                    "expectedRecordIds": ["product-001"],
                    "mustMention": ["一层 A 区"],
                }
            ],
        }
        (fixture_dir / "search-cases.json").write_text(
            json.dumps(cases, ensure_ascii=False),
            encoding="utf-8",
        )
        manifest = {
            "schemaVersion": 1,
            "fixtures": [
                {
                    "scenario": "store",
                    "tenantCode": "demo-store",
                    "knowledgeFile": "store/knowledge.txt",
                    "searchCasesFile": "store/search-cases.json",
                    "sha256": hashlib.sha256(
                        content.encode("utf-8")
                    ).hexdigest(),
                }
            ],
        }
        (self.root / "verification/fixtures/manifest.json").write_text(
            json.dumps(manifest, ensure_ascii=False),
            encoding="utf-8",
        )
        self.bundle = {
            "schemaVersion": 1,
            "runtime": {"providerKind": "DETERMINISTIC"},
            "cases": [
                {
                    "id": "store-search-001",
                    "scenario": "store",
                    "response": {
                        "status": "ANSWERED",
                        "answer": "商品位于一层 A 区。",
                        "citations": [
                            {"quote": "[记录: product-001]\n位置: 一层 A 区"}
                        ],
                    },
                }
            ],
            "crossTenantCases": [
                {
                    "id": "cross-001",
                    "forbiddenRecordIds": ["campus-library-001"],
                    "response": {
                        "status": "ANSWERED",
                        "answer": "本租户只有商品资料。",
                        "citations": [
                            {"quote": "[记录: product-001]\n名称: 青柚气泡水"}
                        ],
                    },
                }
            ],
            "refusalCases": [
                {
                    "id": "refusal-001",
                    "response": {
                        "status": "REFUSED",
                        "answer": None,
                        "refusalReason": "INSUFFICIENT_EVIDENCE",
                        "citations": [],
                    },
                }
            ],
        }

    def tearDown(self):
        self.temp_dir.cleanup()

    def test_complete_bundle_passes_all_gates(self):
        report = MODULE.evaluate(self.root, self.bundle)

        self.assertTrue(report["passed"])
        self.assertEqual(1.0, report["metrics"]["targetHitRate"])
        self.assertEqual(1.0, report["metrics"]["citationCompleteness"])
        self.assertEqual(1.0, report["metrics"]["answerPointCoverage"])
        self.assertEqual(0, report["metrics"]["crossTenantLeakCount"])
        self.assertEqual(1.0, report["metrics"]["expectedRefusalRate"])

    def test_missing_expected_record_fails_closed(self):
        self.bundle["cases"][0]["response"]["citations"] = [
            {"quote": "[记录: product-999]\n位置: 一层 A 区"}
        ]

        report = MODULE.evaluate(self.root, self.bundle)

        self.assertFalse(report["passed"])
        self.assertEqual(0.0, report["metrics"]["targetHitRate"])
        self.assertIn(
            "缺少预期记录",
            report["caseResults"][0]["failures"][0],
        )

    def test_cross_tenant_record_or_non_refusal_fails(self):
        self.bundle["crossTenantCases"][0]["response"]["citations"] = [
            {"quote": "[记录: campus-library-001]\n开放时间"}
        ]
        self.bundle["refusalCases"][0]["response"] = {
            "status": "ANSWERED",
            "answer": "无法确认。",
            "refusalReason": None,
            "citations": [],
        }

        report = MODULE.evaluate(self.root, self.bundle)

        self.assertFalse(report["passed"])
        self.assertEqual(1, report["metrics"]["crossTenantLeakCount"])
        self.assertEqual(0.0, report["metrics"]["expectedRefusalRate"])

    def test_external_provider_or_incomplete_case_set_is_rejected(self):
        self.bundle["runtime"]["providerKind"] = "OPENAI"
        self.bundle["cases"] = []

        with self.assertRaisesRegex(
            MODULE.EvaluationError,
            "确定性|不完整",
        ):
            MODULE.evaluate(self.root, self.bundle)


if __name__ == "__main__":
    unittest.main()
