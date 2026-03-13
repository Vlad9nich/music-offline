import tempfile
import unittest
from pathlib import Path

from fastapi.testclient import TestClient

from app.config import Settings
from app.main import create_app


class OcrApiTest(unittest.TestCase):
    def setUp(self) -> None:
        self.temp_dir = tempfile.TemporaryDirectory()
        base_dir = Path(self.temp_dir.name)
        settings = Settings(
            data_dir=base_dir,
            db_path=base_dir / "ocr.db",
            auth_token="test-token",
            rate_limit_requests=2,
            rate_limit_window_seconds=60,
            job_ttl_seconds=3600,
        )
        app = create_app(settings=settings)
        self.client = TestClient(app)
        self.client.__enter__()

    def tearDown(self) -> None:
        self.client.__exit__(None, None, None)
        self.temp_dir.cleanup()

    def test_protected_endpoint_requires_bearer_token(self) -> None:
        response = self.client.post("/v1/ocr", json={"candidates": []})

        self.assertEqual(response.status_code, 401)
        payload = response.json()
        self.assertEqual(payload["error"]["code"], "unauthorized")
        self.assertTrue(payload["error"]["requestId"])

    def test_rate_limit_returns_structured_error(self) -> None:
        headers = {"Authorization": "Bearer test-token"}

        first = self.client.post("/v1/ocr", json={"candidates": []}, headers=headers)
        second = self.client.post("/v1/ocr", json={"candidates": []}, headers=headers)
        third = self.client.post("/v1/ocr", json={"candidates": []}, headers=headers)

        self.assertEqual(first.status_code, 200)
        self.assertEqual(second.status_code, 200)
        self.assertEqual(third.status_code, 429)
        payload = third.json()
        self.assertEqual(payload["error"]["code"], "rate_limit_exceeded")
        self.assertTrue(payload["error"]["requestId"])


if __name__ == "__main__":
    unittest.main()
