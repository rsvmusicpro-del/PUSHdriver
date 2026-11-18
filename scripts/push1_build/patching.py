"""Deterministic source patch helpers used by the Push 1 assembler."""
from pathlib import Path


def replace_once(path: Path, old: str, new: str, label: str) -> None:
    """Replace exactly one source fragment, allowing an already-applied patch."""
    content = path.read_text(encoding="utf-8")
    if new and new in content and (old in new or old not in content):
        return
    if content.count(old) != 1:
        raise SystemExit(f"{label}: expected exactly one source match")
    path.write_text(content.replace(old, new, 1), encoding="utf-8")


def _matching_brace(source: str, opening: int) -> int:
    """Return the character after a Java method's closing brace.

    Ignore braces inside line/block comments, string/character literals and
    Java text blocks; nested Java blocks are counted normally.
    """
    depth = 0
    index = opening
    state = "code"
    escaped = False
    while index < len(source):
        char = source[index]
        following = source[index + 1] if index + 1 < len(source) else ""

        if state == "code":
            if char == "/" and following == "/":
                state = "line_comment"
                index += 2
                continue
            if char == "/" and following == "*":
                state = "block_comment"
                index += 2
                continue
            if source.startswith('"""', index):
                state = "text_block"
                index += 3
                continue
            if char == '"':
                state = "string"
            elif char == "'":
                state = "character"
            elif char == "{":
                depth += 1
            elif char == "}":
                depth -= 1
                if depth == 0:
                    return index + 1

        elif state == "line_comment":
            if char in "\r\n":
                state = "code"

        elif state == "block_comment":
            if char == "*" and following == "/":
                state = "code"
                index += 2
                continue

        elif state in ("string", "character"):
            if escaped:
                escaped = False
            elif char == "\\":
                escaped = True
            elif (state == "string" and char == '"') or (state == "character" and char == "'"):
                state = "code"

        elif state == "text_block":
            if char == "\\":
                index += 2
                continue
            if source.startswith('"""', index):
                state = "code"
                index += 3
                continue

        index += 1

    return -1


def replace_java_method(path: Path, signature: str, replacement: str, label: str) -> None:
    """Replace one Java method while respecting comments and literal braces."""
    source = path.read_text(encoding="utf-8")
    start = source.find(signature)
    if start < 0:
        raise SystemExit(f"{label}: method signature not found")
    opening = source.find("{", start)
    if opening < 0:
        raise SystemExit(f"{label}: method body not found")
    end = _matching_brace(source, opening)
    if end < 0:
        raise SystemExit(f"{label}: unclosed method body")
    path.write_text(source[:start] + replacement + source[end:], encoding="utf-8")
