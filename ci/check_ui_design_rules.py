#!/usr/bin/env python3
"""Enforce the user-confirmed HealthTrend input-field design rule."""
import re
from pathlib import Path

root = Path(__file__).resolve().parents[1]
source_root = root / "app/src/main/java/com/combustible12/healthtrend"
component = source_root / "AppInputField.kt"

violations = []
for path in source_root.glob("*.kt"):
    text = path.read_text()
    if "OutlinedTextField" in text:
        violations.append(f"{path.name}: OutlinedTextField is forbidden")
    if path != component and re.search(r"(?<![A-Za-z])TextField\s*\(", text):
        violations.append(f"{path.name}: use AppInputField instead of TextField")

component_text = component.read_text()
required = (
    "focusedIndicatorColor=Color.Transparent",
    "unfocusedIndicatorColor=Color.Transparent",
    "disabledIndicatorColor=Color.Transparent",
    "if(label!=null)Text(label",
)
missing = [token for token in required if token not in component_text]
if missing:
    violations.append(f"AppInputField.kt: required no-border behavior missing: {missing}")

assert not violations, "\n".join(violations)
print("PASS: all HealthTrend inputs use external labels and borderless solid fields.")
