#!/usr/bin/env python3
"""Reject source changes that omit mandatory rules/progress/test synchronization.

No Android SDK, network, third-party packages, or patient data needed.
Use: python3 ci/check_change_sync.py --base <git-revision>
     python3 ci/check_change_sync.py --self-test
"""
import argparse
import re
import subprocess
import sys

RULES = "docs/USER_CONFIRMED_RULES.md"
PROGRESS = "docs/DEVELOPMENT_CHECKLIST.md"
HANDOFF = "docs/AI_HANDOFF.md"
POLICY = "docs/CHANGE_SYNC_POLICY.md"
AUDIT = "docs/TEST_AUDIT_CURRENT.md"


def is_test(path):
    return ("/src/test/" in "/" + path or "/src/androidTest/" in "/" + path) and (
        path.endswith(".kt") or path.endswith(".java")
    )


def is_source(path):
    return path.startswith("app/src/main/") and (
        path.endswith(".kt") or path.endswith(".java")
    )


def is_rules(path):
    return path in {RULES, PROGRESS, HANDOFF, POLICY}


def violations(changed, tracked):
    changed = set(changed)
    tracked = set(tracked)
    failures = []
    source = {p for p in changed if is_source(p)}
    tests = {p for p in changed if is_test(p)}
    relevant = source | tests
    if relevant:
        needed = {RULES, PROGRESS, HANDOFF, AUDIT}
        missing = needed - changed
        if missing:
            failures.append("Code/test changes missing synchronized files: " + ", ".join(sorted(missing)))
        if source and not tests:
            failures.append("App source changed without updating any unit/instrumentation regression test.")
    if PROGRESS in changed and RULES not in changed and relevant:
        failures.append("Progress and user rules must be reviewed in the same code/test change.")
    if relevant:
        all_tests = {p for p in tracked if is_test(p)}
        if not all_tests:
            failures.append("No tracked Kotlin/Java test files found; cannot audit tests.")
    if any(p.startswith("app/src/") for p in changed) and not relevant:
        # Non-Kotlin resources/manifest changes still need recorded progress and a test-impact audit.
        if not {PROGRESS, AUDIT}.issubset(changed):
            failures.append("App resources changed without progress + test-impact audit updates.")
    return failures


def git(*args):
    result = subprocess.run(["git", *args], capture_output=True, text=True, check=True)
    return result.stdout


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--base", default="HEAD^")
    parser.add_argument("--self-test", action="store_true")
    args = parser.parse_args()
    if args.self_test:
        tracked = {"app/src/test/java/Test.kt"}
        cases = [
            ({"app/src/main/java/X.kt"}, True),
            ({"app/src/main/java/X.kt", "app/src/test/java/Test.kt", RULES, PROGRESS, HANDOFF, AUDIT}, False),
            ({"docs/example.md"}, False),
            ({"app/src/main/res/layout/view.xml"}, True),
            ({"app/src/main/res/layout/view.xml", PROGRESS, AUDIT}, False),
            ({"app/src/test/java/Test.kt"}, True),
        ]
        for changed, fails in cases:
            assert bool(violations(changed, tracked)) == fails, (changed, fails)
        print("PASS: six self-tests for change synchronization.")
        return 0
    try:
        changed = git("diff", "--name-only", "--diff-filter=ACMRD", args.base, "HEAD").splitlines()
        tracked = git("ls-files").splitlines()
    except subprocess.CalledProcessError as error:
        print("Cannot inspect git history:", error.stderr, file=sys.stderr)
        return 2
    errors = violations(changed, tracked)
    print(f"Tracked tests: {sum(is_test(p) for p in tracked)}; changed files: {len(changed)}")
    if errors:
        for error in errors:
            print("FAIL:", error, file=sys.stderr)
        print("Update every affected test assertion and current documents, or explain no behavior change in the audit.")
        return 1
    print("PASS: synchronized change files present (not a substitute for test correctness).")
    return 0


if __name__ == "__main__":
    sys.exit(main())
