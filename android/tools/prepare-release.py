"""校验构建版本并为 Actions 准备正式 APK，绝不把 unsigned/debug 包重命名发布。"""
import json
import os
import re
from pathlib import Path

root = Path(__file__).resolve().parents[2]
gradle = (root / "android/app/build.gradle.kts").read_text(encoding="utf-8")
version = re.search(r'versionName\s*=\s*"([0-9]+\.[0-9]+\.[0-9]+)"', gradle).group(1)
code = int(re.search(r'versionCode\s*=\s*(\d+)', gradle).group(1))
tag = "android-v" + version
ref = os.environ.get("REF", "")
if ref.startswith("refs/tags/") and ref != "refs/tags/" + tag:
    raise SystemExit("标签版本与 Android 源码版本不一致")
folder = root / "android/app/build/outputs/apk/release"
metadata = json.loads((folder / "output-metadata.json").read_text())
items = metadata["elements"]
if len(items) != 1 or metadata.get("variantName") != "release":
    raise SystemExit("正式产物必须为单一 release APK")
item = items[0]
if item["versionName"] != version or item["versionCode"] != code or item["outputFile"] != "app-release.apk":
    raise SystemExit("构建产物版本不一致或 APK 尚未签名")
source = folder / item["outputFile"]
if not source.is_file() or source.stat().st_size == 0:
    raise SystemExit("正式 APK 不存在或为空")
target = folder / f"BetterHRBUST-{version}-android.apk"
target.write_bytes(source.read_bytes())
with open(os.environ["GITHUB_OUTPUT"], "a", encoding="utf-8") as output:
    output.write(f"tag={tag}\napk={target.relative_to(root).as_posix()}\n")
print(f"正式产物版本已核对：{version} / versionCode {code}")
