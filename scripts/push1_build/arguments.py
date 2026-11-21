"""Command-line argument validation shared by Push 1 build runners."""


def positive_integer(value: str, option: str) -> int:
    """Parse a strictly positive integer or stop with an actionable message."""
    try:
        parsed = int(value)
    except (TypeError, ValueError):
        raise SystemExit(f"{option} must be a positive integer") from None
    if parsed <= 0:
        raise SystemExit(f"{option} must be a positive integer")
    return parsed
