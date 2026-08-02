import json
import os
import subprocess
import tempfile
import threading
import unittest
from http import HTTPStatus
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from pathlib import Path


REPOSITORY_ROOT = Path(__file__).resolve().parents[2]
SCRIPT = REPOSITORY_ROOT / "scripts/development/acceptance-real-model.sh"
TEST_API_KEY = "m2-test-api-key-not-for-production"
TEST_ANSWER = "仅用于验证脱敏摘要的合成回答"


class AcceptanceServer(BaseHTTPRequestHandler):
    requests: list[tuple[str, str, dict | str]] = []
    catalog_types: dict[str, str] = {}
    catalog_entries: dict[str, dict] = {}
    configuration_types: dict[str, str] = {}
    configuration_entries: dict[str, dict] = {}
    next_identifier = 1000

    def log_message(self, format, *args):
        return

    def do_GET(self):
        if self.path.split("?", 1)[0] == "/api/v1/controlled-model-catalog":
            self.reply({
                "items": list(self.catalog_entries.values()),
                "pageNumber": 1,
                "pageSize": 100,
                "totalElements": len(self.catalog_entries),
                "totalPages": 1,
                "hasPrevious": False,
                "hasNext": False,
            })
            return
        if self.path.split("?", 1)[0] == "/api/v1/model-configurations":
            self.reply({
                "items": list(self.configuration_entries.values()),
                "pageNumber": 1,
                "pageSize": 100,
                "totalElements": len(self.configuration_entries),
                "totalPages": 1,
                "hasPrevious": False,
                "hasNext": False,
            })
            return
        if self.path.endswith("/index-versions"):
            self.reply({
                "items": [{"id": "1006", "status": "READY", "version": 1}],
                "pageNumber": 1,
                "pageSize": 20,
                "totalElements": 1,
                "totalPages": 1,
                "hasPrevious": False,
                "hasNext": False,
            })
            return
        raise AssertionError(f"未预期的读取路径：{self.path}")

    def do_POST(self):
        body = self.read_body()
        self.requests.append(("POST", self.path, body))
        if self.path == "/api/v1/auth/sessions":
            self.reply({"tokenValue": "m2-test-token"})
            return
        if self.path == "/api/v1/controlled-model-catalog":
            identifier = self.next_id()
            self.catalog_types[identifier] = body["modelType"]
            self.catalog_entries[identifier] = {
                "id": identifier,
                "providerKind": body["providerKind"],
                "endpoint": body["endpoint"],
                "modelCode": body["modelCode"],
                "modelType": body["modelType"],
                "status": "DRAFT",
            }
            self.reply(self.catalog_entries[identifier])
            return
        if self.path.startswith("/api/v1/controlled-model-catalog/"):
            identifier = self.path.split("/")[4]
            self.catalog_entries[identifier]["status"] = "PUBLISHED"
            self.reply(self.catalog_entries[identifier])
            return
        if self.path == "/api/v1/model-configurations":
            identifier = self.next_id()
            self.configuration_types[identifier] = self.catalog_types[
                body["catalogEntryId"]
            ]
            self.configuration_entries[identifier] = {
                "id": identifier,
                "scope": body["scope"],
                "catalogEntryId": body["catalogEntryId"],
                "status": "DRAFT",
                "modelType": self.configuration_types[identifier],
                "credentialConfigured": True,
            }
            self.reply({
                key: value
                for key, value in self.configuration_entries[identifier].items()
                if key != "modelType"
            })
            return
        if self.path.startswith("/api/v1/model-configurations/"):
            identifier = self.path.split("/")[4]
            self.configuration_entries[identifier]["status"] = "ACTIVE"
            self.reply({
                key: value
                for key, value in self.configuration_entries[identifier].items()
                if key != "modelType"
            })
            return
        if self.path == "/api/v1/knowledge-spaces":
            self.reply({"id": "1004"})
            return
        if self.path == "/api/v1/data-sources":
            self.reply({"id": "1005"})
            return
        if self.path.split("?", 1)[0].endswith("/content"):
            self.reply({"status": "CONTENT_READY", "version": 1})
            return
        if self.path.endswith("/data-sources/1005/publish"):
            self.reply({"status": "PUBLISHED", "version": 2})
            return
        if self.path.endswith("/index-versions"):
            self.reply({"id": "1006", "status": "BUILDING", "version": 0})
            return
        if self.path.endswith("/index-versions/1006/publish"):
            self.reply({"id": "1006", "status": "ACTIVE", "version": 2})
            return
        if self.path == "/api/v1/assistants":
            self.reply({"id": "1007"})
            return
        if self.path == "/api/v1/assistants/1007/versions":
            self.reply({"id": "1008", "status": "DRAFT", "version": 0})
            return
        if self.path == "/api/v1/assistant-versions/1008/publish":
            self.reply({"id": "1008", "status": "PUBLISHED", "version": 1})
            return
        if self.path == "/api/v1/query-runs":
            self.reply({
                "status": "ANSWERED",
                "answer": TEST_ANSWER,
                "citations": [{"sequence": 1}],
            })
            return
        raise AssertionError(f"未预期的写入路径：{self.path}")

    def read_body(self):
        content_length = int(self.headers.get("Content-Length", "0"))
        if content_length == 0:
            return None
        raw = self.rfile.read(content_length)
        if self.headers.get("Content-Type", "").startswith("application/json"):
            try:
                return json.loads(raw)
            except json.JSONDecodeError as error:
                raise AssertionError(
                    f"JSON 请求体为空或无效：{self.path}"
                ) from error
        return raw.decode("utf-8")

    @classmethod
    def next_id(cls):
        cls.next_identifier += 1
        return str(cls.next_identifier)

    def reply(self, body):
        encoded = json.dumps(body).encode("utf-8")
        self.send_response(HTTPStatus.OK)
        self.send_header("Content-Type", "application/json")
        self.send_header("Content-Length", str(len(encoded)))
        self.end_headers()
        self.wfile.write(encoded)

class RealModelAcceptanceTest(unittest.TestCase):

    def setUp(self):
        AcceptanceServer.requests = []
        AcceptanceServer.catalog_types = {}
        AcceptanceServer.catalog_entries = {}
        AcceptanceServer.configuration_types = {}
        AcceptanceServer.configuration_entries = {}
        AcceptanceServer.next_identifier = 1000
        self.server = ThreadingHTTPServer(("127.0.0.1", 0), AcceptanceServer)
        self.thread = threading.Thread(target=self.server.serve_forever)
        self.thread.start()
        self.addCleanup(self.stop_server)

    def stop_server(self):
        self.server.shutdown()
        self.thread.join()
        self.server.server_close()

    def test_script_runs_the_governed_path_without_leaking_key_or_answer(self):
        with tempfile.TemporaryDirectory() as directory:
            key_file = Path(directory) / "api-key"
            key_file.write_text(TEST_API_KEY, encoding="utf-8")
            key_file.chmod(0o600)
            api_root = f"http://127.0.0.1:{self.server.server_port}/api/v1"
            environment = {
                **os.environ,
                "FOUNDATION_M2_API_KEY_FILE": str(key_file),
                "FOUNDATION_ACCEPTANCE_CONTROL_API_ROOT": api_root,
                "FOUNDATION_ACCEPTANCE_QUERY_API_ROOT": api_root,
                "FOUNDATION_M2_HTTP_TIMEOUT_SECONDS": "3",
                "FOUNDATION_M2_INDEX_TIMEOUT_SECONDS": "3",
            }
            completed = subprocess.run(
                [str(SCRIPT)],
                cwd=REPOSITORY_ROOT,
                env=environment,
                check=True,
                capture_output=True,
                text=True,
            )

        summary = json.loads(completed.stdout)
        self.assertEqual("PASSED", summary["status"])
        self.assertEqual(1, summary["citationCount"])
        self.assertNotIn(TEST_API_KEY, completed.stdout)
        self.assertNotIn(TEST_API_KEY, completed.stderr)
        self.assertNotIn(TEST_ANSWER, completed.stdout)
        self.assertNotIn("m2-test-token", completed.stdout)

        configuration_requests = [
            body
            for method, path, body in AcceptanceServer.requests
            if method == "POST" and path == "/api/v1/model-configurations"
        ]
        self.assertEqual(2, len(configuration_requests))
        self.assertTrue(
            all(body["apiKey"] == TEST_API_KEY for body in configuration_requests)
        )
        login_requests = [
            body
            for method, path, body in AcceptanceServer.requests
            if method == "POST" and path == "/api/v1/auth/sessions"
        ]
        self.assertEqual(
            ["platform-admin", "admin"],
            [body["loginName"] for body in login_requests],
        )
        self.assertTrue(any(
            path == "/api/v1/query-runs"
            for _, path, _ in AcceptanceServer.requests
        ))

    def test_script_reuses_published_catalog_entries(self):
        AcceptanceServer.catalog_types = {"9001": "CHAT", "9002": "EMBEDDING"}
        AcceptanceServer.next_identifier = 1003
        AcceptanceServer.catalog_entries = {
            "9001": {
                "id": "9001",
                "providerKind": "OPENAI_COMPATIBLE",
                "endpoint": "https://ark.cn-beijing.volces.com/api/v3",
                "modelCode": "doubao-seed-2-1-turbo-260628",
                "modelType": "CHAT",
                "status": "PUBLISHED",
            },
            "9002": {
                "id": "9002",
                "providerKind": "OPENAI_COMPATIBLE",
                "endpoint": "https://ark.cn-beijing.volces.com/api/v3",
                "modelCode": "doubao-embedding-vision-251215",
                "modelType": "EMBEDDING",
                "status": "PUBLISHED",
            },
        }
        AcceptanceServer.configuration_types = {"9003": "CHAT", "9004": "EMBEDDING"}
        AcceptanceServer.configuration_entries = {
            "9003": {
                "id": "9003",
                "scope": "PERSONAL",
                "catalogEntryId": "9001",
                "modelType": "CHAT",
                "status": "ACTIVE",
                "credentialConfigured": True,
            },
            "9004": {
                "id": "9004",
                "scope": "PERSONAL",
                "catalogEntryId": "9002",
                "modelType": "EMBEDDING",
                "status": "ACTIVE",
                "credentialConfigured": True,
            },
        }
        with tempfile.TemporaryDirectory() as directory:
            key_file = Path(directory) / "api-key"
            key_file.write_text(TEST_API_KEY, encoding="utf-8")
            key_file.chmod(0o600)
            api_root = f"http://127.0.0.1:{self.server.server_port}/api/v1"
            completed = subprocess.run(
                [str(SCRIPT)],
                cwd=REPOSITORY_ROOT,
                env={
                    **os.environ,
                    "FOUNDATION_M2_API_KEY_FILE": str(key_file),
                    "FOUNDATION_ACCEPTANCE_CONTROL_API_ROOT": api_root,
                    "FOUNDATION_ACCEPTANCE_QUERY_API_ROOT": api_root,
                    "FOUNDATION_M2_HTTP_TIMEOUT_SECONDS": "3",
                    "FOUNDATION_M2_INDEX_TIMEOUT_SECONDS": "3",
                },
                check=True,
                capture_output=True,
                text=True,
            )

        self.assertEqual("PASSED", json.loads(completed.stdout)["status"])
        self.assertFalse(any(
            path == "/api/v1/controlled-model-catalog"
            for _, path, _ in AcceptanceServer.requests
        ))
        self.assertFalse(any(
            path == "/api/v1/model-configurations"
            for _, path, _ in AcceptanceServer.requests
        ))

        AcceptanceServer.requests = []
        with tempfile.TemporaryDirectory() as directory:
            key_file = Path(directory) / "api-key"
            key_file.write_text(TEST_API_KEY, encoding="utf-8")
            key_file.chmod(0o600)
            completed = subprocess.run(
                [str(SCRIPT)],
                cwd=REPOSITORY_ROOT,
                env={
                    **os.environ,
                    "FOUNDATION_M2_API_KEY_FILE": str(key_file),
                    "FOUNDATION_ACCEPTANCE_CONTROL_API_ROOT": api_root,
                    "FOUNDATION_ACCEPTANCE_QUERY_API_ROOT": api_root,
                    "FOUNDATION_M2_HTTP_TIMEOUT_SECONDS": "3",
                    "FOUNDATION_M2_INDEX_TIMEOUT_SECONDS": "3",
                    "FOUNDATION_M2_FORCE_NEW_PERSONAL_CONFIGURATIONS": "true",
                },
                check=True,
                capture_output=True,
                text=True,
            )

        self.assertEqual("PASSED", json.loads(completed.stdout)["status"])
        self.assertFalse(any(
            path == "/api/v1/controlled-model-catalog"
            for _, path, _ in AcceptanceServer.requests
        ))
        created_configurations = [
            body
            for method, path, body in AcceptanceServer.requests
            if method == "POST" and path == "/api/v1/model-configurations"
        ]
        self.assertEqual(2, len(created_configurations))

    def test_missing_key_file_fails_before_any_http_request(self):
        api_root = f"http://127.0.0.1:{self.server.server_port}/api/v1"
        environment = {
            **os.environ,
            "FOUNDATION_M2_API_KEY_FILE": "/tmp/m2-key-does-not-exist",
            "FOUNDATION_ACCEPTANCE_CONTROL_API_ROOT": api_root,
            "FOUNDATION_ACCEPTANCE_QUERY_API_ROOT": api_root,
        }
        completed = subprocess.run(
            [str(SCRIPT)],
            cwd=REPOSITORY_ROOT,
            env=environment,
            capture_output=True,
            text=True,
        )

        self.assertNotEqual(0, completed.returncode)
        self.assertEqual("", completed.stdout)
        self.assertIn("API Key 文件不可读", completed.stderr)
        self.assertEqual([], AcceptanceServer.requests)

    def test_key_file_with_group_or_other_read_permission_fails_before_request(self):
        with tempfile.TemporaryDirectory() as directory:
            key_file = Path(directory) / "api-key"
            key_file.write_text(TEST_API_KEY, encoding="utf-8")
            key_file.chmod(0o644)
            api_root = f"http://127.0.0.1:{self.server.server_port}/api/v1"
            environment = {
                **os.environ,
                "FOUNDATION_M2_API_KEY_FILE": str(key_file),
                "FOUNDATION_ACCEPTANCE_CONTROL_API_ROOT": api_root,
                "FOUNDATION_ACCEPTANCE_QUERY_API_ROOT": api_root,
            }
            completed = subprocess.run(
                [str(SCRIPT)],
                cwd=REPOSITORY_ROOT,
                env=environment,
                capture_output=True,
                text=True,
            )

        self.assertNotEqual(0, completed.returncode)
        self.assertEqual("", completed.stdout)
        self.assertIn("API Key 文件权限过宽", completed.stderr)
        self.assertEqual([], AcceptanceServer.requests)

    def test_labeled_key_file_only_submits_the_key_value(self):
        with tempfile.TemporaryDirectory() as directory:
            key_file = Path(directory) / "api-key"
            key_file.write_text(f"火山方舟 API Key：{TEST_API_KEY}", encoding="utf-8")
            key_file.chmod(0o600)
            api_root = f"http://127.0.0.1:{self.server.server_port}/api/v1"
            completed = subprocess.run(
                [str(SCRIPT)],
                cwd=REPOSITORY_ROOT,
                env={
                    **os.environ,
                    "FOUNDATION_M2_API_KEY_FILE": str(key_file),
                    "FOUNDATION_ACCEPTANCE_CONTROL_API_ROOT": api_root,
                    "FOUNDATION_ACCEPTANCE_QUERY_API_ROOT": api_root,
                },
                capture_output=True,
                text=True,
            )

        self.assertEqual("PASSED", json.loads(completed.stdout)["status"])
        configuration_requests = [
            body
            for method, path, body in AcceptanceServer.requests
            if method == "POST" and path == "/api/v1/model-configurations"
        ]
        self.assertTrue(
            all(body["apiKey"] == TEST_API_KEY for body in configuration_requests)
        )


if __name__ == "__main__":
    unittest.main()
