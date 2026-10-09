import json
import os
import shutil
import subprocess
import sys
import tempfile
import unittest
from pathlib import Path

class PrepareReleaseTest(unittest.TestCase):
    def run_case(self, *, ref="refs/heads/main", filename="app-release.apk", version="1.1.1", code=3, variant="release"):
        with tempfile.TemporaryDirectory() as temp:
            root = Path(temp)
            tools = root / "android/tools"
            tools.mkdir(parents=True)
            script = tools / "prepare-release.py"
            shutil.copyfile(Path(__file__).with_name("prepare-release.py"), script)
            app = root / "android/app"
            app.mkdir(parents=True)
            (app / "build.gradle.kts").write_text('versionName = "1.1.1"\nversionCode = 3', encoding="utf-8")
            folder = app / "build/outputs/apk/release"
            folder.mkdir(parents=True)
            (folder / filename).write_bytes(b"fixture")
            (folder / "output-metadata.json").write_text(json.dumps({"variantName": variant, "elements": [{
                "versionName": version, "versionCode": code, "outputFile": filename}]}), encoding="utf-8")
            output = root / "output"
            result = subprocess.run([sys.executable, str(script)], env=dict(os.environ, REF=ref, GITHUB_OUTPUT=str(output)), capture_output=True, text=True)
            return result.returncode, output.read_text() if output.exists() else ""

    def test_matching_signed_release_is_named_with_its_actual_version(self):
        code, output = self.run_case(ref="refs/tags/android-v1.1.1")
        self.assertEqual(code, 0)
        self.assertIn("tag=android-v1.1.1", output)
        self.assertIn("BetterHRBUST-1.1.1-android.apk", output)

    def test_unsigned_debug_wrong_tag_and_version_are_rejected(self):
        for options in [dict(filename="app-release-unsigned.apk"), dict(variant="debug"),
                        dict(ref="refs/tags/android-v1.1.0"), dict(version="1.1.0"), dict(code=2)]:
            with self.subTest(options=options):
                code, output = self.run_case(**options)
                self.assertNotEqual(code, 0)
                self.assertEqual(output, "")

if __name__ == "__main__":
    unittest.main()
