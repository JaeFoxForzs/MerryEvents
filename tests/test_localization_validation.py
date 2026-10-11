import json
import os
import subprocess
import tempfile
import unittest
from pathlib import Path


REPOSITORY_ROOT = Path(__file__).resolve().parents[1]
GRADLE_WRAPPER = REPOSITORY_ROOT / ("gradlew.bat" if os.name == "nt" else "gradlew")


class LocalizationValidationTest(unittest.TestCase):
    def setUp(self):
        self.temp_dir = tempfile.TemporaryDirectory()
        self.fixture_root = Path(self.temp_dir.name)
        self.lang_dir = self.fixture_root / "src/main/resources/assets/merryevents/lang"
        self.java_dir = self.fixture_root / "src/main/java"
        self.lang_dir.mkdir(parents=True)
        self.java_dir.mkdir(parents=True)
        (self.fixture_root / "src/client/java").mkdir(parents=True)

    def tearDown(self):
        self.temp_dir.cleanup()

    def write_locales(self, english, russian):
        (self.lang_dir / "en_us.json").write_text(
            english if isinstance(english, str) else json.dumps(english, ensure_ascii=False),
            encoding="utf-8",
        )
        (self.lang_dir / "ru_ru.json").write_text(
            russian if isinstance(russian, str) else json.dumps(russian, ensure_ascii=False),
            encoding="utf-8",
        )

    def write_java(self, source):
        (self.java_dir / "LocalizationFixture.java").write_text(source, encoding="utf-8")

    def run_validator(self):
        command = [
            str(GRADLE_WRAPPER),
            "validateAndFormatLocalization",
            f"-PlocalizationRoot={self.fixture_root}",
            "--console=plain",
        ]
        if os.name == "nt":
            command = subprocess.list2cmdline(command)
        return subprocess.run(
            command,
            cwd=REPOSITORY_ROOT,
            capture_output=True,
            text=True,
            shell=os.name == "nt",
            check=False,
        )

    def assert_validation_fails_with(self, expected_message):
        result = self.run_validator()
        output = result.stdout + result.stderr
        self.assertNotEqual(result.returncode, 0, output)
        self.assertIn(expected_message, output)

    def test_accepts_matching_translations_referenced_from_java(self):
        self.write_locales(
            {"fixture.greeting": "Hello %s"},
            {"fixture.greeting": "Привет %s"},
        )
        self.write_java(
            'import net.minecraft.text.Text;\n'
            'class LocalizationFixture {\n'
            '  Text message() { return Text.translatable("fixture.greeting"); }\n'
            '}\n'
        )

        result = self.run_validator()

        self.assertEqual(result.returncode, 0, result.stdout + result.stderr)

    def test_rejects_invalid_json(self):
        self.write_locales('{"fixture.greeting": }', {"fixture.greeting": "Привет"})

        self.assert_validation_fails_with("invalid JSON")

    def test_rejects_duplicate_json_keys(self):
        self.write_locales(
            '{"fixture.greeting": "Hello", "fixture.greeting": "Hi"}',
            {"fixture.greeting": "Привет"},
        )

        self.assert_validation_fails_with("Duplicate JSON Keys")

    def test_rejects_missing_and_extra_locale_keys(self):
        self.write_locales(
            {"fixture.greeting": "Hello"},
            {"fixture.unexpected": "Лишний ключ"},
        )
        self.write_java(
            'import net.minecraft.text.Text;\n'
            'class LocalizationFixture {\n'
            '  Text message() { return Text.translatable("fixture.greeting"); }\n'
            '}\n'
        )

        result = self.run_validator()
        output = result.stdout + result.stderr

        self.assertNotEqual(result.returncode, 0, output)
        self.assertIn("Missing Keys in Locales", output)
        self.assertIn("fixture.greeting", output)
        self.assertIn("Extra Keys in Locales", output)
        self.assertIn("fixture.unexpected", output)

    def test_rejects_placeholder_mismatches(self):
        self.write_locales(
            {"fixture.greeting": "Hello %s"},
            {"fixture.greeting": "Привет %d"},
        )
        self.write_java(
            'import net.minecraft.text.Text;\n'
            'class LocalizationFixture {\n'
            '  Text message() { return Text.translatable("fixture.greeting"); }\n'
            '}\n'
        )

        self.assert_validation_fails_with("Placeholder Mismatches")

    def test_rejects_missing_unused_and_hardcoded_translations(self):
        self.write_locales(
            {
                "fixture.used": "Used",
                "fixture.unused": "Unused",
            },
            {
                "fixture.used": "Используется",
                "fixture.unused": "Не используется",
            },
        )
        self.write_java(
            'import net.minecraft.text.Text;\n'
            'class LocalizationFixture {\n'
            '  Text message() { return Text.translatable("fixture.missing"); }\n'
            '  Text label() { return Text.literal("Hardcoded label"); }\n'
            '}\n'
        )

        result = self.run_validator()
        output = result.stdout + result.stderr

        self.assertNotEqual(result.returncode, 0, output)
        self.assertIn("fixture.missing", output)
        self.assertIn("fixture.unused", output)
        self.assertIn("Hardcoded User-Facing Text", output)
        self.assertIn("Hardcoded label", output)


if __name__ == "__main__":
    unittest.main()
