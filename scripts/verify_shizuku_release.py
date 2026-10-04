#!/usr/bin/env python3
"""Verify the actual optimized DEX still exposes Shizuku's reflective constructors."""
import pathlib
import re
import subprocess
import sys
import tempfile
import zipfile

apk = pathlib.Path(sys.argv[1])
dexdump = sys.argv[2]
descriptor = "Lcom/gaozay/smartflight/shizuku/ShizukuCommandService;"
found = False
with zipfile.ZipFile(apk) as archive, tempfile.TemporaryDirectory() as directory:
    for name in archive.namelist():
        if not re.fullmatch(r"classes\d*\.dex", name):
            continue
        path = pathlib.Path(directory) / name
        path.write_bytes(archive.read(name))
        output = subprocess.check_output([dexdump, str(path)], text=True)
        for block in re.split(r"(?=Class #\d+ -)", output):
            if not re.search(r"Class descriptor\s*:\s*'" + re.escape(descriptor) + "'", block):
                continue
            signatures = re.findall(r"name\s*:\s*'<init>'\s+type\s*:\s*'([^']+)'", block)
            assert {"()V", "(Landroid/content/Context;)V"} <= set(signatures), signatures
            found = True
assert found, "Shizuku Binder service class is missing from optimized DEX"
print("Verified Shizuku reflective entry points:", apk.name)
