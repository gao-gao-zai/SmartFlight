#!/usr/bin/env python3
"""Check translation parity, format arguments and untranslated Chinese source text."""

from pathlib import Path
import re
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[1]
RES = ROOT / "app/src/main/res"
FORMAT = re.compile(r"%(\d+)\$([sdif])")
errors = []


def catalog(folder):
    result = {}
    for element in ET.parse(RES / folder / "strings.xml").getroot():
        name = element.attrib["name"]
        if not re.fullmatch(r"[a-z][a-z0-9_]*", name):
            errors.append(f"Invalid resource name: {name}")
        key = (element.tag, name)
        if key in result:
            errors.append(f"Duplicate resource: {folder}/{name}")
        result[key] = element
    return result


base = catalog("values")
english = catalog("values-en")
if base.keys() != english.keys():
    errors.append(f"Missing English resources: {base.keys() - english.keys()}")
    errors.append(f"English-only resources: {english.keys() - base.keys()}")
for key, element in base.items():
    if key not in english:
        continue
    variants = list(element) if element.tag == "plurals" else [element]
    translated = list(english[key]) if element.tag == "plurals" else [english[key]]
    if element.tag == "plurals":
        if not any(item.attrib.get("quantity") == "other" for item in variants):
            errors.append(f"Missing default plural: {key}")
        if {item.attrib.get("quantity") for item in translated} != {"one", "other"}:
            errors.append(f"English plurals must have one/other: {key}")
    expected = sorted(FORMAT.findall(variants[-1].text or ""))
    for item in translated:
        text = item.text or ""
        if sorted(FORMAT.findall(text)) != expected:
            errors.append(f"Format argument mismatch: {key}")
        if re.search(r"[\u3400-\u9fff]", text):
            errors.append(f"Chinese text in English resource: {key}")

for path in (ROOT / "app/src/main").rglob("*"):
    if path.suffix in {".kt", ".java"}:
        text = path.read_text(encoding="utf-8")
        # Intentionally strict: new Chinese copy belongs in resources, including defaults.
        for line, content in enumerate(text.splitlines(), 1):
            if re.search(r"[\u3400-\u9fff]", content):
                errors.append(f"Chinese source text: {path.relative_to(ROOT)}:{line}")
        for resource_type, name in re.findall(r"R\.(string|plurals)\.([a-zA-Z0-9_]+)", text):
            if (resource_type, name) not in base:
                errors.append(f"Unknown resource: {path.name}: R.{resource_type}.{name}")
    elif path.suffix == ".xml" and "values" not in path.parts:
        if "values-en" in path.parts:
            continue
        if re.search(r"[\u3400-\u9fff]", path.read_text(encoding="utf-8")):
            errors.append(f"Chinese text outside default resources: {path.relative_to(ROOT)}")

if errors:
    raise SystemExit("\n".join(errors))
print(f"Localization checks passed: {len(base)} matching Chinese/English resources")
