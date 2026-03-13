import tempfile
import unittest
from pathlib import Path

from app.job_store import SqliteJobStore


class SqliteJobStoreTest(unittest.TestCase):
    def setUp(self) -> None:
        self.temp_dir = tempfile.TemporaryDirectory()
        base_dir = Path(self.temp_dir.name)
        self.store = SqliteJobStore(base_dir / "jobs.db")

    def tearDown(self) -> None:
        self.temp_dir.cleanup()

    def test_job_payload_persists_between_reads(self) -> None:
        self.store.create_job(
            job_id="job-1",
            total_files=2,
            job_dir="/tmp/job-1",
            source_files=["a.jpg", "b.jpg"],
            expires_at=9999999999,
        )
        self.store.update_job(
            "job-1",
            status="processing",
            processed_files=1,
            candidates=[{"candidateId": "cand-1", "titleGuess": "Track"}],
        )

        payload = self.store.get_job("job-1")

        self.assertIsNotNone(payload)
        assert payload is not None
        self.assertEqual(payload["jobId"], "job-1")
        self.assertEqual(payload["status"], "processing")
        self.assertEqual(payload["processedFiles"], 1)
        self.assertEqual(payload["candidates"][0]["candidateId"], "cand-1")

    def test_recoverable_jobs_include_processing_and_queued(self) -> None:
        self.store.create_job(
            job_id="queued-job",
            total_files=1,
            job_dir="/tmp/queued-job",
            source_files=["shot.jpg"],
            expires_at=9999999999,
        )
        self.store.create_job(
            job_id="done-job",
            total_files=1,
            job_dir="/tmp/done-job",
            source_files=["done.jpg"],
            expires_at=9999999999,
        )
        self.store.update_job("done-job", status="completed", processed_files=1, candidates=[])

        recoverable = self.store.list_recoverable_jobs()

        self.assertEqual(len(recoverable), 1)
        self.assertEqual(recoverable[0]["jobId"], "queued-job")


if __name__ == "__main__":
    unittest.main()
