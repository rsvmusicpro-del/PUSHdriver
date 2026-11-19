from pathlib import Path

from push1_build.arguments import positive_integer
import importlib.util
import tempfile
import unittest

ROOT = Path(__file__).resolve().parents[2]
PATCHING = ROOT / "scripts/push1_build/patching.py"
SPEC = importlib.util.spec_from_file_location("push1_patching", PATCHING)
MODULE = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(MODULE)


class PatchHelperTests(unittest.TestCase):
    def test_positive_integer_accepts_valid_values(self):
        self.assertEqual(positive_integer("1", "--fuzz-seeds"), 1)
        self.assertEqual(positive_integer("50", "--fuzz-seeds"), 50)

    def test_positive_integer_rejects_zero_negative_and_invalid_values(self):
        for value in ("0", "-1", "not-a-number"):
            with self.subTest(value=value):
                with self.assertRaises(SystemExit):
                    positive_integer(value, "--fuzz-seeds")

    def test_replace_once_is_idempotent_after_exact_replacement(self):
        with tempfile.TemporaryDirectory() as directory:
            path = Path(directory) / "sample.txt"
            path.write_text("before target after", encoding="utf-8")
            MODULE.replace_once(path, "target", "replacement", "sample patch")
            MODULE.replace_once(path, "target", "replacement", "sample patch")
            self.assertEqual(path.read_text(encoding="utf-8"), "before replacement after")

    def test_replace_once_rejects_ambiguous_source_matches(self):
        with tempfile.TemporaryDirectory() as directory:
            path = Path(directory) / "sample.txt"
            path.write_text("target and target", encoding="utf-8")
            with self.assertRaises(SystemExit):
                MODULE.replace_once(path, "target", "replacement", "ambiguous patch")

    def test_replace_java_method_ignores_braces_in_literals_and_comments(self):
        source = '''class Example
{
    void target ()
    {
        String close = "}";
        char brace = '}';
        // }
        /* } { */
        String block = """
            { this is text, not a Java block }
        """;
        if (brace == '}')
        {
            close = "{";
        }
    }

    void untouched ()
    {
        // This method must survive the replacement.
    }
}
'''
        with tempfile.TemporaryDirectory() as directory:
            path = Path(directory) / "Example.java"
            path.write_text(source, encoding="utf-8")
            MODULE.replace_java_method(
                path,
                "    void target ()",
                "    void target ()\n    {\n        int replacement = 1;\n    }",
                "replace target method",
            )
            result = path.read_text(encoding="utf-8")
        self.assertIn("int replacement = 1;", result)
        self.assertIn("void untouched ()", result)
        self.assertNotIn('String close = "}"', result)


if __name__ == "__main__":
    unittest.main()
