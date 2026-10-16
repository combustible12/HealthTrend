#!/usr/bin/env python3
"""Guard the user-confirmed boundary: report data import has no OCR."""
from pathlib import Path

root = Path(__file__).resolve().parents[1]
report_import = (root / "app/src/main/java/com/combustible12/healthtrend/ReportImport.kt").read_text()
main_activity = (root / "app/src/main/java/com/combustible12/healthtrend/MainActivity.kt").read_text()
image_documents = (root / "app/src/main/java/com/combustible12/healthtrend/ImageDocuments.kt").read_text()

forbidden_import = (
    "TextRecognition", "ChineseTextRecognizerOptions", "ReportOcr", "OcrCell",
    "TakePicture", "OpenMultipleDocuments", "fun recognize(",
)
found = [token for token in forbidden_import if token in report_import]
assert not found, f"ReportImport OCR boundary violated: {found}"

forbidden_ui = ("拍照识别检查报告", "importer.camera", "importer.gallery")
found = [token for token in forbidden_ui if token in main_activity]
assert not found, f"Report import UI exposes OCR: {found}"

required_image_ocr = ("TextRecognition", "ChineseTextRecognizerOptions")
missing = [token for token in required_image_ocr if token not in image_documents]
assert not missing, f"Image Documents OCR was removed unexpectedly: {missing}"

print("PASS: report import has no OCR; Image Documents OCR remains available.")
