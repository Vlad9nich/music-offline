import unittest

from app.config import Settings
from app.ocr_pipeline import build_llm_payload, coerce_llm_candidates, normalize_candidate_fields, parse_llm_json


class OcrPipelineNormalizationTest(unittest.TestCase):
    def test_build_llm_payload_uses_openrouter_multimodal_chat_format(self) -> None:
        settings = Settings(data_dir=__import__("pathlib").Path("."), db_path=__import__("pathlib").Path("ocr.db"))
        payload = build_llm_payload(b"\x89PNG\r\n\x1a\nfake-image-bytes", settings, strict=False)

        self.assertEqual(payload["model"], settings.llm_model)
        self.assertEqual(payload["response_format"], {"type": "json_object"})
        self.assertEqual(payload["messages"][0]["role"], "system")
        self.assertEqual(payload["messages"][1]["role"], "user")
        self.assertEqual(payload["messages"][1]["content"][0]["type"], "text")
        self.assertEqual(payload["messages"][1]["content"][1]["type"], "image_url")
        self.assertTrue(payload["messages"][1]["content"][1]["image_url"]["url"].startswith("data:image/png;base64,"))

    def test_parse_llm_json_extracts_candidates_from_fenced_json(self) -> None:
        parsed = parse_llm_json(
            """```json
            {"candidates":[{"candidateId":"1","screenshotIndex":0,"rawText":"Фото со звездой | Lida","artistGuess":"Lida","titleGuess":"Фото со звездой","confidence":0.94}]}
            ```"""
        )

        self.assertEqual(len(parsed["candidates"]), 1)
        self.assertEqual(parsed["candidates"][0]["titleGuess"], "Фото со звездой")
        self.assertEqual(parsed["candidates"][0]["artistGuess"], "Lida")

    def test_coerce_llm_candidates_rebuilds_raw_text_and_keeps_confidence_bounded(self) -> None:
        parsed = coerce_llm_candidates(
            [
                {
                    "candidateId": "1",
                    "screenshotIndex": 0,
                    "rawText": "неловко плим",
                    "artistGuess": "плим",
                    "titleGuess": "неловко",
                    "confidence": 1.4,
                }
            ]
        )

        self.assertEqual(parsed[0]["rawText"], "неловко | плим")
        self.assertEqual(parsed[0]["confidence"], 1.0)

    def test_normalize_candidate_fields_repairs_cyrillic_artist_title(self) -> None:
        candidate = {
            "candidateId": "1",
            "screenshotIndex": 0,
            "rawText": "неловко | 02:21 | пЛм",
            "artistGuess": "02:21",
            "titleGuess": "неловко",
            "confidence": 0.92,
            "bbox": [91, 28, 725, 79],
        }

        normalized = normalize_candidate_fields(candidate)

        self.assertEqual(normalized["titleGuess"], "Неловко")
        self.assertEqual(normalized["artistGuess"], "ПЛМ")

    def test_normalize_candidate_fields_repairs_common_music_ocr_typos(self) -> None:
        candidate = {
            "candidateId": "2",
            "screenshotIndex": 0,
            "rawText": "ЧСЕ | чс | Lire Сеpега иреп",
            "artistGuess": "Чс",
            "titleGuess": "ЧСЕ",
            "confidence": 0.71,
            "bbox": [12, 40, 201, 80],
        }

        normalized = normalize_candidate_fields(candidate)

        self.assertEqual(normalized["titleGuess"], "ЧСВ")
        self.assertEqual(normalized["artistGuess"], "Lida, Серега Пират")

    def test_normalize_candidate_fields_repairs_blum_track(self) -> None:
        candidate = {
            "candidateId": "3",
            "screenshotIndex": 0,
            "rawText": "ELUM | Lige.",
            "artistGuess": "Lige.",
            "titleGuess": "ELUM",
            "confidence": 0.74,
            "bbox": [61, 103, 105, 142],
        }

        normalized = normalize_candidate_fields(candidate)

        self.assertEqual(normalized["titleGuess"], "BLUM")
        self.assertEqual(normalized["artistGuess"], "Lida, SQWOZ BAB")


if __name__ == "__main__":
    unittest.main()
