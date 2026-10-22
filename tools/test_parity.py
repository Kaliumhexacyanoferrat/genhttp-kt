#!/usr/bin/env python3
"""
Verifies that the ported Kotlin tests mirror the upstream C# tests.

For every upstream test method ([TestMethod], [Fact], [Theory]) the script expects a Kotlin test function with the
same name (first letter lower-cased, compared case-insensitively without underscores) inside a class with the same
simple name. It also flags constructs that indicate weakened tests.

Usage:
    python tools/test_parity.py                 # all suites
    python tools/test_parity.py --suite glyph11 # one suite
    python tools/test_parity.py --filter Modules/Layouting

Exit code 1 if any problem was found.
"""

import argparse
import io
import os
import re
import sys
from collections import defaultdict

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
UPSTREAM_ROOT = os.environ.get("GENHTTP_UPSTREAM_ROOT", os.path.join(os.path.dirname(ROOT)))

SUITES = {
    "glyph11": (os.path.join(UPSTREAM_ROOT, "Glyph11", "tests", "Tests"),
                os.path.join(ROOT, "Glyph11", "src", "test", "kotlin")),
    "acceptance": (os.path.join(UPSTREAM_ROOT, "GenHTTP", "Testing", "Acceptance"),
                   os.path.join(ROOT, "Testing", "Acceptance", "src", "test", "kotlin")),
}

# upstream folders that are intentionally not ported (see docs/DEVIATIONS.md)
EXCLUDED_UPSTREAM = [
    os.path.join("Engine", "Kestrel"),
    os.path.join("Engine", "Ioxide"),
    os.path.join("Adapters"),
    "bin", "obj", "TestResults",
]

# upstream tests that are intentionally not ported, "Class.Method" -> reason
EXCLUDED_TESTS_FILE = os.path.join(ROOT, "tools", "test_parity_exclusions.txt")

CS_TEST_ATTR = re.compile(r"\[\s*(TestMethod|Fact|Theory)\b")
CS_CLASS = re.compile(r"\b(?:class|record)\s+([A-Za-z_][A-Za-z0-9_]*)")
CS_METHOD = re.compile(r"\b(?:public|private|internal|protected)?\s*(?:static\s+)?(?:async\s+)?[\w<>\[\],?. ]+\s+([A-Za-z_][A-Za-z0-9_]*)\s*\(")

KT_TEST_ANN = re.compile(r"@(Test|ParameterizedTest|MultiEngineTest|MultiEngineFrameworkTest|RepeatedTest|TestFactory)\b")
KT_CLASS = re.compile(r"\b(?:class|object)\s+([A-Za-z_][A-Za-z0-9_]*)")
KT_FUN = re.compile(r"\bfun\s+(?:`([^`]+)`|([A-Za-z_][A-Za-z0-9_]*))\s*\(")

ASSERTION_HINT = re.compile(r"assert|Assert|fail\(|shouldBe|expect|verify|check\(", re.IGNORECASE)


def normalize(name):
    return name.replace("_", "").replace(" ", "").lower()


def is_excluded_path(rel):
    parts = rel.split(os.sep)
    for excluded in EXCLUDED_UPSTREAM:
        ex_parts = excluded.split(os.sep)
        for i in range(len(parts) - len(ex_parts) + 1):
            if parts[i:i + len(ex_parts)] == ex_parts:
                return True
    return False


def load_exclusions():
    result = {}
    if os.path.exists(EXCLUDED_TESTS_FILE):
        with open(EXCLUDED_TESTS_FILE, encoding="utf-8") as f:
            for line in f:
                line = line.strip()
                if not line or line.startswith("#"):
                    continue
                key, _, reason = line.partition(" ")
                result[normalize(key)] = reason.strip()
    return result


def body_after(text, index):
    """Returns the text of the block starting at the first '{' or '=>' after index."""
    brace = text.find("{", index)
    arrow = text.find("=>", index)
    if arrow != -1 and (brace == -1 or arrow < brace):
        end = text.find(";", arrow)
        return text[arrow:end]
    if brace == -1:
        return ""
    depth = 0
    for i in range(brace, len(text)):
        if text[i] == "{":
            depth += 1
        elif text[i] == "}":
            depth -= 1
            if depth == 0:
                return text[brace:i + 1]
    return text[brace:]


def enclosing_class(classes, position):
    current = None
    for pos, name in classes:
        if pos < position:
            current = name
        else:
            break
    return current


def dir_key(rel):
    # directories mirror upstream (lower-cased packages), used to tell apart classes with the same name
    return re.sub(r"[^a-z0-9]", "", os.path.dirname(rel).lower())


def scan_upstream(directory, filter_text):
    tests = {}
    for root, _, files in os.walk(directory):
        for file in files:
            if not file.endswith(".cs"):
                continue
            path = os.path.join(root, file)
            rel = os.path.relpath(path, directory)
            if is_excluded_path(rel) or (filter_text and filter_text not in rel.replace(os.sep, "/")):
                continue
            text = open(path, encoding="utf-8-sig").read()
            classes = [(m.start(), m.group(1)) for m in CS_CLASS.finditer(text)]
            for attr in CS_TEST_ATTR.finditer(text):
                method = CS_METHOD.search(text, attr.end())
                if not method:
                    continue
                cls = enclosing_class(classes, attr.start())
                body = body_after(text, method.end())
                key = normalize(f"{cls}.{method.group(1)}")
                if key in tests and dir_key(tests[key][1]) != dir_key(rel):
                    key = key + "@" + dir_key(rel)
                tests[key] = (f"{cls}.{method.group(1)}", rel, bool(ASSERTION_HINT.search(body)))
    return tests


def scan_kotlin(directory, filter_text):
    tests = {}
    problems = []
    for root, _, files in os.walk(directory):
        for file in files:
            if not file.endswith(".kt") or file in ("MultiEngineTest.kt", "MultiEngineFrameworkTest.kt"):
                continue
            path = os.path.join(root, file)
            rel = os.path.relpath(path, directory)
            # tests without upstream counterpart (see docs/PORTING.md)
            if rel.replace(os.sep, "/").startswith("additional/"):
                continue
            text = open(path, encoding="utf-8").read()
            # ignore examples in comments
            # (string literals are matched first so that e.g. "*/*" does not start a comment)
            text = re.sub(r'"(?:\\.|[^"\\\n])*"|/\*.*?\*/',
                          lambda m: m.group(0) if m.group(0).startswith('"') else " " * len(m.group(0)), text, flags=re.S)
            if re.search(r"@Disabled\b|assumeTrue|assumeFalse|Assumptions\.", text):
                problems.append(f"{rel}: uses @Disabled or assumptions")
            classes = [(m.start(), m.group(1)) for m in KT_CLASS.finditer(text)]
            for ann in KT_TEST_ANN.finditer(text):
                fun = KT_FUN.search(text, ann.end())
                if not fun:
                    continue
                name = fun.group(1) or fun.group(2)
                cls = enclosing_class(classes, ann.start())
                body = body_after(text, fun.end())
                key = normalize(f"{cls}.{name}")
                if key in tests and tests[key][1] != rel:
                    if dir_key(tests[key][1]) == dir_key(rel):
                        problems.append(f"{rel}: duplicate test {cls}.{name}")
                    key = key + "@" + dir_key(rel)
                tests[key] = (f"{cls}.{name}", rel, bool(ASSERTION_HINT.search(body)), body.strip() in ("", "{}", "{ }"))
    return tests, problems


def check_suite(name, filter_text, exclusions):
    upstream_dir, kotlin_dir = SUITES[name]
    if not os.path.isdir(upstream_dir):
        print(f"[{name}] upstream directory not found: {upstream_dir}")
        return 1
    upstream = scan_upstream(upstream_dir, filter_text)
    kotlin, problems = scan_kotlin(kotlin_dir, None) if os.path.isdir(kotlin_dir) else ({}, [])

    missing = []
    for key, (display, rel, has_assert) in sorted(upstream.items(), key=lambda x: x[1][1]):
        if key in exclusions:
            continue
        found = kotlin.get(key)
        twins = [v for k, v in kotlin.items() if k.split("@")[0] == key.split("@")[0]]
        if len(twins) > 1:
            # same-named classes: match the Kotlin test in the mirrored directory
            found = next((v for v in twins if dir_key(v[1]) == dir_key(rel)), None)
        if not found:
            missing.append(f"{display}  ({rel})")
            continue
        if found[3]:
            problems.append(f"{found[1]}: test {found[0]} has an empty body")
        elif has_assert and not found[2]:
            problems.append(f"{found[1]}: test {found[0]} has no assertion but upstream does")

    upstream_names = {k.split("@")[0] for k in upstream}
    extra = [f"{v[0]}  ({v[1]})" for k, v in kotlin.items() if k.split("@")[0] not in upstream_names]
    if filter_text:
        extra = []

    print(f"[{name}] upstream tests: {len(upstream)}, ported: {len(upstream) - len(missing)}, "
          f"missing: {len(missing)}, extra: {len(extra)}, problems: {len(problems)}")
    for m in missing:
        print(f"  MISSING  {m}")
    for e in extra:
        print(f"  EXTRA    {e}")
    for p in problems:
        print(f"  PROBLEM  {p}")
    return 1 if (missing or problems) else 0


def main():
    sys.stdout = io.TextIOWrapper(sys.stdout.buffer, encoding="utf-8", errors="replace")
    parser = argparse.ArgumentParser()
    parser.add_argument("--suite", choices=list(SUITES.keys()))
    parser.add_argument("--filter", help="only check upstream files whose relative path contains this text")
    args = parser.parse_args()

    exclusions = load_exclusions()
    suites = [args.suite] if args.suite else list(SUITES.keys())
    result = 0
    for suite in suites:
        result |= check_suite(suite, args.filter, exclusions)
    sys.exit(result)


if __name__ == "__main__":
    main()
