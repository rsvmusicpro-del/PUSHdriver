"""Reduce deterministic generated failures to the shortest failing action prefix.

This reducer deliberately makes no claim that arbitrary actions can be deleted:
its monotonicity assumption is only that a failing prefix remains failing when
more actions are appended to the same deterministic scenario.
"""

from collections.abc import Callable


def minimal_failing_prefix(max_actions: int, reproduces: Callable[[int], bool]) -> int | None:
    """Return the shortest failing prefix in [1, max_actions], or None.

    ``reproduces(n)`` must replay the same seed and view with the first ``n``
    generated actions. Binary search is valid for a first-failure prefix because
    the predicate is monotone: once a prefix fails, every longer prefix fails.
    """
    if max_actions < 1:
        raise ValueError("max_actions must be at least 1")
    if not reproduces(max_actions):
        return None

    low, high = 1, max_actions
    while low < high:
        middle = (low + high) // 2
        if reproduces(middle):
            high = middle
        else:
            low = middle + 1
    return low
