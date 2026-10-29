#!/usr/bin/env python3
"""Enforce the user-confirmed HealthTrend borderless component architecture."""
import re
from pathlib import Path

root = Path(__file__).resolve().parents[1]
source_root = root / "app/src/main/java/com/combustible12/healthtrend"
component = source_root / "AppInputField.kt"
violations = []

def fail(path: Path, line: int, message: str):
    violations.append(f"{path.relative_to(root)}:{line}: {message}")

for path in sorted(source_root.glob("*.kt")):
    for number, line in enumerate(path.read_text().splitlines(), 1):
        for token in ("OutlinedTextField", "OutlinedButton", "OutlinedCard", "BorderStroke", "Modifier.border"):
            if token in line:
                fail(path, number, f"{token} is forbidden; use a solid HT component")
        if path != component and re.search(r"\bBasicTextField\s*\(", line):
            fail(path, number, "BasicTextField may only be used inside AppInputField.kt")
        if path != component and re.search(r"(?<!Basic)\bTextField\s*\(", line):
            fail(path, number, "Material TextField is forbidden; use AppInputField")
        if "FilterChip(" in line and "border=null" not in line:
            fail(path, number, "FilterChip must set border=null")

component_text = component.read_text()
required = ("BasicTextField(", "if(label!=null)Text(label", "cursorBrush=SolidColor(Accent)", "if(isError)Color(0xFFFFF1F1)")
for token in required:
    if token not in component_text:
        violations.append(f"{component.relative_to(root)}: missing required behavior: {token}")
for token in ("TextFieldDefaults", "focusedIndicatorColor", "unfocusedIndicatorColor"):
    if token in component_text:
        violations.append(f"{component.relative_to(root)}: hidden Material indicator is not a true borderless implementation: {token}")

assert not violations, "\n".join(violations)
print("PASS: HT inputs use BasicTextField wrappers; decorative outlines are absent.")
