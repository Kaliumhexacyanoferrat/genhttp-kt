#!/usr/bin/env python3
"""
Compares the public API surface of GenHTTP (C# sources) with the ABI dumps of genhttp-kt (`api/*.api`).

For every upstream project (API, engine, modules, testing) the public types and their public members are extracted
from the C# sources and looked up in the Kotlin ABI dump of the corresponding project, after applying the naming
rules of PORTING.md (no `I` prefix, camelCase, no `Async` suffix, properties as getters, ...).

The tool is a safety net for the manual API parity pass: it finds upstream declarations without a Kotlin counterpart.
It does not prove functional identity. Intentional differences are listed with a reason in
`tools/api_parity_exclusions.txt` (`<Project>:<Type>` or `<Project>:<Type>.<Member>`, `#` starts a comment).

Usage: python tools/api_parity.py [--filter <project>] [--verbose] [--report docs/api-parity.md]
"""

import argparse
import fnmatch
import os
import re
import sys

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
UPSTREAM_ROOT = os.environ.get("GENHTTP_UPSTREAM_ROOT", os.path.dirname(ROOT))
GENHTTP = os.path.join(UPSTREAM_ROOT, "GenHTTP")
GLYPH11 = os.path.join(UPSTREAM_ROOT, "Glyph11")
EXCLUSIONS_FILE = os.path.join(ROOT, "tools", "api_parity_exclusions.txt")

# upstream project directory -> genhttp-kt project directory
PROJECTS = [("API/General", "API/General"), ("Engine/Shared", "Engine/Shared"), ("Engine/Internal", "Engine/Internal"),
            ("Testing/Testing", "Testing/Testing")]

SKIPPED_DIRS = {"bin", "obj", "Properties"}

TYPE_RE = re.compile(r"\b(?:public|protected)\s+(?:(?:static|sealed|abstract|partial|readonly|unsafe|new|ref|record)\s+)*"
                     r"(class|interface|record|enum|struct)\s+(\w+)")
INTERFACE_MEMBER_RE = re.compile(r"^\s*(?:static\s+abstract\s+|static\s+|abstract\s+)?[\w<>\[\],.?() ]+?\s+(\w+)\s*(?:<[^>]*>)?\s*(\(|\{|=>|;)")
MEMBER_RE = re.compile(r"\b(?:public|protected)\s+(?:(?:static|sealed|abstract|virtual|override|readonly|const|new|async|"
                       r"required|unsafe|extern|implicit|explicit|operator|event)\s+)*([\w<>\[\],.?() ]+?)\s+(\w+)\s*(?:<[^>]*>)?\s*(\(|\{|=>|;|=)")
ENUM_MEMBER_RE = re.compile(r"^\s*(\w+)\s*(=|,|$)")


def strip_comments_and_strings(text):
    text = re.sub(r"/\*.*?\*/", "", text, flags=re.S)
    text = re.sub(r"//[^\n]*", "", text)
    text = re.sub(r'@"(?:[^"]|"")*"', '""', text)
    text = re.sub(r'"""[\s\S]*?"""', '""', text)
    text = re.sub(r'\$?"(?:\\.|[^"\\\n])*"', '""', text)
    text = re.sub(r"'(?:\\.|[^'\\])'", "''", text)
    return text


def find_attribute_free(line):
    # drop attributes like [Obsolete] or [DynamicallyAccessedMembers(...)] in front of declarations / parameters
    return re.sub(r"\[[^\[\]]*(?:\[[^\[\]]*\][^\[\]]*)*\]", "", line)


def parse_csharp_project(path):
    """Returns {type name: {"kind": kind, "members": set, "file": file}} of the public surface of a C# project."""
    types = {}

    for dirpath, dirnames, filenames in os.walk(path):
        dirnames[:] = [d for d in dirnames if d not in SKIPPED_DIRS]

        for filename in filenames:
            if not filename.endswith(".cs") or filename.endswith(".g.cs"):
                continue

            file = os.path.join(dirpath, filename)

            with open(file, encoding="utf-8-sig") as f:
                text = strip_comments_and_strings(f.read())

            parse_csharp_file(text, os.path.relpath(file, path), types)

    return types


def parse_csharp_file(text, file, types):
    depth = 0
    stack = []  # (name, kind, body depth, public)
    pending = None  # type declared, waiting for its opening brace

    for raw in text.split("\n"):
        line = find_attribute_free(raw)

        current = stack[-1] if stack else None
        in_body = current is not None and depth == current[2]

        match = TYPE_RE.search(line)

        if match and (current is None or in_body):
            kind, name = match.group(1), match.group(2)
            visible = (current is None or current[3]) and "public" in line.split(match.group(0))[0] + match.group(0)

            if visible:
                entry = types.setdefault(name, {"kind": kind, "members": set(), "file": file})

                # positional records: public properties from the parameter list
                record = re.search(r"record\s+(?:class\s+|struct\s+)?\w+\s*(?:<[^>]*>)?\s*\(([^)]*)\)", line)
                if record:
                    for param in record.group(1).split(","):
                        parts = param.split("=")[0].strip().split()
                        if len(parts) >= 2:
                            entry["members"].add(parts[-1].split("=")[0].strip())

                if current is not None:
                    types[current[0]]["members"].add(name)

            pending = (name, kind, visible)

        elif in_body and current[3]:
            if current[1] == "enum":
                for item in line.split(","):
                    enum_match = ENUM_MEMBER_RE.match(item)
                    if enum_match and enum_match.group(1) not in ("public", "internal"):
                        types[current[0]]["members"].add(enum_match.group(1))

            elif current[1] == "interface" and not re.search(r"\b(private|internal|protected)\b", line):
                member = INTERFACE_MEMBER_RE.match(line)
                if member and member.group(1) not in ("get", "set", "init", "return", "new", "using", "namespace"):
                    types[current[0]]["members"].add(member.group(1))

            else:
                member = MEMBER_RE.search(line)
                if member and member.group(2) not in ("get", "set", "init"):
                    name = member.group(2)
                    if name != current[0] and not line.strip().startswith(("internal", "private")):
                        if "operator" not in line:
                            types[current[0]]["members"].add(name)

        for char in line:
            if char == "{":
                depth += 1
                if pending is not None:
                    stack.append((pending[0], pending[1], depth, pending[2]))
                    pending = None
            elif char == "}":
                if stack and stack[-1][2] == depth:
                    stack.pop()
                depth -= 1

        # declarations without body (e.g. `public record Foo(int X);`)
        if pending is not None and line.rstrip().endswith(";"):
            pending = None


def parse_kotlin_dump(path):
    """Returns {simple type name: set of member names} of a Kotlin ABI dump; nested types use their own simple name."""
    types = {}
    facades = {}
    current = None

    if not os.path.isdir(path):
        return types

    for filename in os.listdir(path):
        if not filename.endswith(".api"):
            continue

        with open(os.path.join(path, filename), encoding="utf-8") as f:
            for line in f:
                line = line.rstrip()

                header = re.match(r"^public .*?(?:class|interface) ([\w/$]+)(?: : .*)? \{$", line)
                if header:
                    full = header.group(1).split("/")[-1]
                    parts = full.split("$")
                    simple = parts[-1]

                    # companion objects, DefaultImpls etc. contribute to the outer type
                    if simple in ("Companion", "DefaultImpls") and len(parts) > 1:
                        simple = parts[-2]

                    current = types.setdefault(simple, set())

                    # nested types are members of the outer type as well
                    if len(parts) > 1 and parts[-1] not in ("Companion", "DefaultImpls"):
                        types.setdefault(parts[-2], set()).add(parts[-1])

                    continue

                if line == "}":
                    current = None
                    continue

                if current is None:
                    continue

                member = re.match(r"^\s+(?:public|protected) .*?(fun|field) ([^\s(]+)", line)
                if member:
                    current.add(member.group(2))

    return types


def kotlin_names(name):
    """Candidate Kotlin member names for an upstream member name."""
    name = name.lstrip("_")
    base = name[:-5] if name.endswith("Async") and len(name) > 5 else name
    camel = base[:1].lower() + base[1:]
    pascal = base[:1].upper() + base[1:]

    candidates = {name, base, camel, "get" + pascal, "is" + pascal, "set" + pascal, camel + "Of"}

    if base.startswith("Is") and len(base) > 2 and base[2].isupper():
        candidates.add("is" + base[2:])
    if base.startswith("Get") and len(base) > 3:
        candidates.add(base[3:4].lower() + base[4:])
    if base.startswith("Try") and len(base) > 3:
        candidates.add(base[3:4].lower() + base[4:])

    # SCREAMING / acronyms: HTTP -> http
    candidates.add(base.lower())

    # indexers become get / set operators
    if name == "this":
        candidates.update({"get", "set"})

    return candidates


def parse_kotlin_sources(path):
    """Names of public functions and properties declared in Kotlin sources (covers inline reified functions,
    which are not part of ABI dumps)."""
    names = set()

    for dirpath, _, filenames in os.walk(path):
        for filename in filenames:
            if filename.endswith(".kt"):
                with open(os.path.join(dirpath, filename), encoding="utf-8") as f:
                    for match in re.finditer(r"^\s*public\s+(?:[a-z]+\s+)*(?:fun|val|var)\s+(?:<[^>]*>\s+)?(?:[\w.<>?, *]+\.)?(\w+)",
                                             f.read(), re.M):
                        names.add(match.group(1))

    return names


def kotlin_type_names(name):
    candidates = [name]

    if len(name) > 2 and name[0] == "I" and name[1].isupper():
        candidates.append(name[1:])

    for prefix in ("Http", "Api"):
        candidates.append(prefix + name)
        if len(name) > 2 and name[0] == "I" and name[1].isupper():
            candidates.append(prefix + name[1:])

    if name.endswith("Attribute"):
        # annotations clashing with API types carry an `Api` prefix, prefer them
        candidates.insert(0, "Api" + name[:-9])
        candidates.append(name[:-9])

    return candidates


def normalize_member(name):
    # value class / internal mangling: `foo-abc123`, accessor prefixes
    name = name.split("-")[0]
    if name.endswith("$default"):
        name = name[:-8]
    return name


class Exclusions(dict):
    """Exclusion entries, supporting glob patterns (`Modules/IO:WritingStream.*`)."""

    def __contains__(self, key):
        return dict.__contains__(self, key) or any(fnmatch.fnmatchcase(key, pattern) for pattern in self.keys() if "*" in pattern)


def load_exclusions():
    exclusions = Exclusions()

    if os.path.exists(EXCLUSIONS_FILE):
        with open(EXCLUSIONS_FILE, encoding="utf-8") as f:
            for line in f:
                content, _, reason = line.partition("#")
                content = content.strip()
                if content:
                    exclusions[content] = reason.strip()

    return exclusions


def discover_projects():
    projects = list(PROJECTS)

    modules = os.path.join(GENHTTP, "Modules")
    for name in sorted(os.listdir(modules)):
        if os.path.isdir(os.path.join(modules, name)):
            projects.append((f"Modules/{name}", f"Modules/{name}"))

    return projects


def compare(upstream_dir, kotlin_dir, exclusions, project):
    upstream = parse_csharp_project(upstream_dir)
    kotlin = parse_kotlin_dump(os.path.join(ROOT, kotlin_dir, "api"))
    declared = parse_kotlin_sources(os.path.join(ROOT, kotlin_dir, "src", "main", "kotlin"))

    missing_types = []
    missing_members = []
    matched_types = 0
    total_members = 0
    matched_members = 0

    for name, info in sorted(upstream.items()):
        key = f"{project}:{name}"

        if key in exclusions:
            continue

        found = next((candidate for candidate in kotlin_type_names(name) if candidate in kotlin), None)

        if found is None:
            missing_types.append((name, info["file"]))
            continue

        matched_types += 1

        available = {normalize_member(m) for m in kotlin[found]}

        # extension methods of static classes may live in `<Type>Extensions` facades or be inline (not in the dump)
        for facade in (found + "Extensions", name + "Extensions"):
            available |= {normalize_member(m) for m in kotlin.get(facade, set())}
        fallback = declared

        # extension methods of static classes may live in facades named after the C# class
        for member in sorted(info["members"]):
            if f"{key}.{member}" in exclusions:
                continue

            # public C# members are PascalCase, lower case names are misparsed constructor parameters
            if member[:1].islower() and member != "this":
                continue

            total_members += 1

            if any(candidate in available for candidate in kotlin_names(member)):
                matched_members += 1
            elif any(candidate in fallback for candidate in kotlin_names(member)):
                matched_members += 1
            elif member in upstream or any(candidate in kotlin for candidate in kotlin_type_names(member)):
                matched_members += 1  # nested type, checked separately
            else:
                missing_members.append((name, member, info["file"]))

    return {
        "types": len([n for n in upstream if f"{project}:{n}" not in exclusions]),
        "matched_types": matched_types,
        "members": total_members,
        "matched_members": matched_members,
        "missing_types": missing_types,
        "missing_members": missing_members,
        "has_dump": os.path.isdir(os.path.join(ROOT, kotlin_dir, "api")),
    }


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--filter", default=None)
    parser.add_argument("--verbose", action="store_true")
    parser.add_argument("--report", default=None)
    args = parser.parse_args()

    exclusions = load_exclusions()

    projects = [(os.path.join(GENHTTP, up), kt, up) for up, kt in discover_projects()]
    projects.append((os.path.join(GLYPH11, "Glyph11"), "Glyph11", "Glyph11"))

    lines = ["| Project | Types | Members | Missing |", "|---|---|---|---|"]
    details = []
    total_missing = 0

    for upstream_dir, kotlin_dir, project in projects:
        if args.filter and args.filter.lower() not in project.lower():
            continue

        if not os.path.isdir(upstream_dir):
            continue

        result = compare(upstream_dir, kotlin_dir, exclusions, project)

        missing = len(result["missing_types"]) + len(result["missing_members"])
        total_missing += missing

        state = "" if result["has_dump"] else " (no ABI dump)"

        lines.append(f"| {project}{state} | {result['matched_types']}/{result['types']} | "
                     f"{result['matched_members']}/{result['members']} | {missing} |")

        if missing:
            details.append(f"\n### {project}\n")
            for name, file in result["missing_types"]:
                details.append(f"- type `{name}` ({file})")
            for name, member, file in result["missing_members"]:
                details.append(f"- member `{name}.{member}` ({file})")

    output = "\n".join(lines)

    print(output)

    if args.verbose or args.report:
        print("\n".join(details))

    if args.report:
        with open(os.path.join(ROOT, args.report), "w", encoding="utf-8") as f:
            f.write("# API parity report\n\n")
            f.write("Generated by `tools/api_parity.py`: upstream public declarations without a Kotlin counterpart in the "
                    "ABI dumps (after applying the naming rules of PORTING.md). Intentional differences are listed in "
                    "`tools/api_parity_exclusions.txt`.\n\n")
            f.write(output + "\n")
            if details:
                f.write("\n## Missing declarations\n")
                f.write("\n".join(details) + "\n")

    print(f"\n{total_missing} upstream declarations without counterpart")

    return 1 if total_missing else 0


if __name__ == "__main__":
    sys.exit(main())
