import hashlib
import importlib.util
import json
import sys
import tempfile
import unittest
from pathlib import Path


SCRIPT = Path(__file__).with_name("demo_fixtures.py")
SPEC = importlib.util.spec_from_file_location("demo_fixtures", SCRIPT)
MODULE = importlib.util.module_from_spec(SPEC)
assert SPEC.loader is not None
sys.modules[SPEC.name] = MODULE
SPEC.loader.exec_module(MODULE)


class DemoFixturesTest(unittest.TestCase):
    def setUp(self):
        self.temp_dir = tempfile.TemporaryDirectory()
        self.root = Path(self.temp_dir.name)
        self.fixture_dir = self.root / "verification/fixtures/store"
        self.fixture_dir.mkdir(parents=True)
        self.content = "[合成数据]\n[记录: product-001]\n名称: 示例商品\n"
        self.content_path = self.fixture_dir / "knowledge.txt"
        self.content_path.write_text(self.content, encoding="utf-8")
        self.search_path = self.fixture_dir / "search-cases.json"
        self.search_path.write_text(
            json.dumps(
                {
                    "schemaVersion": 1,
                    "cases": [
                        {
                            "id": "store-search-001",
                            "query": "示例商品在哪里？",
                            "expectedRecordIds": ["product-001"],
                            "mustMention": ["示例商品"],
                        }
                    ],
                },
                ensure_ascii=False,
            ),
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
                        self.content.encode("utf-8")
                    ).hexdigest(),
                }
            ],
        }
        (self.root / "verification/fixtures/manifest.json").write_text(
            json.dumps(manifest, ensure_ascii=False),
            encoding="utf-8",
        )

    def tearDown(self):
        self.temp_dir.cleanup()

    def test_valid_fixture_passes(self):
        result = MODULE.verify(self.root)
        self.assertEqual(1, result.fixture_count)
        self.assertEqual(1, result.search_case_count)

    def test_content_digest_must_match(self):
        self.content_path.write_text(self.content + "被修改\n", encoding="utf-8")
        with self.assertRaisesRegex(MODULE.FixtureError, "摘要"):
            MODULE.verify(self.root)

    def test_fixture_must_be_marked_as_synthetic(self):
        content = self.content.replace("[合成数据]\n", "")
        self.content_path.write_text(content, encoding="utf-8")
        manifest_path = self.root / "verification/fixtures/manifest.json"
        manifest = json.loads(manifest_path.read_text(encoding="utf-8"))
        manifest["fixtures"][0]["sha256"] = hashlib.sha256(
            content.encode("utf-8")
        ).hexdigest()
        manifest_path.write_text(
            json.dumps(manifest, ensure_ascii=False),
            encoding="utf-8",
        )
        with self.assertRaisesRegex(MODULE.FixtureError, "合成数据"):
            MODULE.verify(self.root)

    def test_search_case_ids_must_be_unique(self):
        search = json.loads(self.search_path.read_text(encoding="utf-8"))
        search["cases"].append(search["cases"][0])
        self.search_path.write_text(
            json.dumps(search, ensure_ascii=False),
            encoding="utf-8",
        )
        with self.assertRaisesRegex(MODULE.FixtureError, "重复"):
            MODULE.verify(self.root)

    def test_required_answer_point_must_exist_in_knowledge(self):
        search = json.loads(self.search_path.read_text(encoding="utf-8"))
        search["cases"][0]["mustMention"] = ["不存在的答案"]
        self.search_path.write_text(
            json.dumps(search, ensure_ascii=False),
            encoding="utf-8",
        )
        with self.assertRaisesRegex(MODULE.FixtureError, "答案要点不存在"):
            MODULE.verify(self.root)


if __name__ == "__main__":
    unittest.main()
