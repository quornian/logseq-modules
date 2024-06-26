#!/bin/env python3
import argparse
import difflib
import glob
import json
import os
import sys
from collections import defaultdict
from dataclasses import dataclass
from enum import Enum

BEGIN_EDN = ";; <logseq-modules {}>"
END_EDN = ";; </logseq-modules {}>"
BEGIN_CSS = "/* <logseq-modules {}> */"
END_CSS = "/* </logseq-modules {}> */"

BOF = "beginning-of-file"
EOF = "end-of-file"


@dataclass
class Location:
    row: int
    col: int


@dataclass
class Range:
    start: Location
    end: Location


class Transformation(Enum):
    PlainText = 0
    ClojureKeywordValue = 1
    ClojureKeywordString = 2


@dataclass
class Module:
    files: list[str]


def main():
    parser = argparse.ArgumentParser(description="Install/update all modules")
    parser.add_argument("--uninstall", action="store_true", help="uninstall")
    parser.add_argument(
        "-o",
        "--output-directory",
        help="save modifications into this directory rather than "
        "overwriting the original files",
    )
    parser.add_argument(
        "-d",
        "--diff",
        action="store_true",
        help="show a diff of changes without applying them",
    )
    parser.add_argument(
        "-c",
        "--color",
        action="store_true",
        help="always use color",
    )
    parser.add_argument(
        "-a",
        "--apply",
        action="store_true",
        help="(also) apply the changes. In diff mode this defaults to false, "
        "so this flag provides a way to do both",
    )
    parser.add_argument(
        "-q",
        "--quiet",
        action="store_true",
        help="hide log messages",
    )
    parser.add_argument(
        "-m",
        "--select",
        nargs="+",
        metavar="MODULE_NAME",
        help="limits the installation to modules given by name to this option",
    )
    parser.add_argument(
        "-x",
        "--export",
        action="store_true",
        help="instead of updating an existing configuration, create an empty "
        "one and export into it",
    )
    parser.add_argument(
        "logseq_directory",
        help="path to your graph's logseq directory, where config.edn lives",
    )
    args = parser.parse_args()
    input_directory = args.logseq_directory
    output_directory = args.output_directory or input_directory

    log_writes = True
    if args.quiet:
        log_writes = False
        log.enabled = False
    if args.diff:
        log.enabled = False

    if args.export:
        if args.uninstall:
            parser.error("Cannot --uninstall when performing --export")
        if os.path.exists(output_directory):
            parser.error("Output directory should not exist prior to --export")
        os.makedirs(output_directory)
        config = [
            "{:macros\n",
            " {}\n",
            " :query/result-transforms\n",
            " {}\n",
            " :query/views\n",
            " {}\n",
            "}\n",
        ]
        styles = []
    else:
        with open(os.path.join(input_directory, "config.edn")) as config_edn:
            config = config_edn.readlines()
        try:
            with open(os.path.join(input_directory, "custom.css")) as custom_css:
                styles = custom_css.readlines()
        except FileNotFoundError:
            styles = [""]

    original_config = config[:]
    original_styles = styles[:]

    with open("./config.json") as config_file:
        install_config = json.load(config_file)

    key = ":macros"
    log(f"\nPreparing {key} entries for config.edn...")
    patterns = install_config["patterns"]["macros"]
    config = install(
        config,
        key=key,
        begin_mark=BEGIN_EDN.format("macros"),
        end_mark=END_EDN.format("macros"),
        paths=match_paths(patterns, args.select),
        transformation=Transformation.ClojureKeywordString,
        uninstall=args.uninstall,
        exclude_marks=args.export,
    )
    key = ":query/result-transforms"
    log(f"\nPreparing {key} entries for config.edn...")
    patterns = install_config["patterns"]["query-transforms"]
    config = install(
        config,
        key=key,
        begin_mark=BEGIN_EDN.format("query-result-transforms"),
        end_mark=END_EDN.format("query-result-transforms"),
        paths=match_paths(patterns, args.select),
        transformation=Transformation.ClojureKeywordValue,
        uninstall=args.uninstall,
        exclude_marks=args.export,
    )
    key = ":query/views"
    log(f"\nPreparing {key} entries for config.edn...")
    patterns = install_config["patterns"]["query-views"]
    config = install(
        config,
        key=key,
        begin_mark=BEGIN_EDN.format("query-views"),
        end_mark=END_EDN.format("query-views"),
        paths=match_paths(patterns, args.select),
        transformation=Transformation.ClojureKeywordValue,
        uninstall=args.uninstall,
        exclude_marks=args.export,
    )
    log("\nPreparing CSS style entries for custom.css...")
    patterns = install_config["patterns"]["styles"]
    styles = install(
        styles,
        key=EOF,
        begin_mark=BEGIN_CSS.format("styles"),
        end_mark=END_CSS.format("styles"),
        paths=match_paths(patterns, args.select),
        transformation=Transformation.PlainText,
        uninstall=args.uninstall,
        exclude_marks=args.export,
    )

    # Normalize changes
    config = trim_whitespace("".join(config).splitlines(True))
    styles = trim_whitespace("".join(styles).splitlines(True))

    if args.diff:
        show_diff("config.edn", original_config, config, args.color)
        show_diff("custom.css", original_styles, styles, args.color)
    if not args.diff or args.apply:
        if log_writes:
            log.enabled = True
        if config != original_config:
            atomic_overwrite(
                os.path.join(output_directory, "config.edn"), "".join(config)
            )
        else:
            log("\nNo config.edn changes")
        if styles != original_styles:
            atomic_overwrite(
                os.path.join(output_directory, "custom.css"), "".join(styles)
            )
        else:
            log("\nNo custom.css changes")


def log(msg):
    if log.enabled:
        print(msg, file=sys.stderr)


log.enabled = True


def atomic_overwrite(path, content):
    log(f"\nWriting {path}...")
    temp_path = path + ".tmp"
    with open(temp_path, "w") as temp_file:
        temp_file.write(content)
    try:
        os.replace(temp_path, path)
    except:
        os.unlink(temp_path)
        raise
    log("Done.")


def match_paths(patterns, select):
    paths = []
    for pattern in patterns:
        for path in glob.glob(pattern):
            [*_, module_name, _] = path.split(os.sep)
            if select and module_name not in select:
                continue
            paths.append(path)
    return paths


def install(
    config: list[str],
    key: str,
    begin_mark: str,
    end_mark: str,
    paths: list[str],
    transformation: Transformation,
    uninstall: bool,
    exclude_marks: bool = False,
):
    insertion = calculate_insertion(config, key, begin_mark, end_mark)

    s, e = insertion.start, insertion.end
    indent = " " * s.col

    before = config[: s.row] + [config[s.row][: s.col]]
    if e.row == len(config):
        assert e.col == 0
        after = []
    else:
        after = [config[e.row][e.col :]] + config[e.row + 1 :]
    if insertion.start == insertion.end:
        assert "".join(config) == "".join(before + after)
        if after and not uninstall:
            after[0] = indent + after[0]

    if uninstall:
        if after and after[0].startswith(indent):
            after[0] = after[0][len(indent) :]
        return before + after

    compiled = compile_files(indent, paths, transformation)

    if exclude_marks:
        begin_mark = end_mark = ""

    config = []
    config.extend(before)
    config.append(f"{begin_mark}\n")
    config.extend(compiled)
    config.append(f"{indent}{end_mark}\n")
    config.extend(after)
    return config


def calculate_insertion(
    config: list[str], key: str, begin_mark: str, end_mark: str
) -> Range:
    begin_loc = find_line(config, begin_mark)
    end_loc = find_line(config, end_mark)

    if begin_loc is not None:
        if end_loc is None:
            raise RuntimeError(f"Missing: '{end_mark}'")
        if config.count(begin_mark) > 1 or config.count(end_mark) > 1:
            raise RuntimeError(f"Multiple: '{begin_mark}' / '{end_mark}'")
        end_loc.row += 1
        end_loc.col = 0
        return Range(begin_loc, end_loc)

    if key is BOF:
        loc = Location(0, 0)
        return Range(loc, loc)
    if key is EOF:
        config.append("\n")
        loc = Location(len(config) - 1, 0)
        return Range(loc, loc)

    loc = find_line(config, key, just_content)

    # Looking for
    #   :key {<-insert-here
    # but could hit comments
    #   :key ;; comment {maybe with braces}
    #   {<-insert here
    row = loc.row
    col = loc.col
    while row < len(config):
        line = config[row]
        content = just_content(line)
        if "{" in content[col:]:
            loc = Location(row, content.index("{", col) + 1)
            return Range(loc, loc)
        row += 1
        col = 0
    raise RuntimeError("Unexpected end of file")


def find_line(config, sought, line_map=lambda x: x) -> Location | None:
    found_at = None
    for row, line in enumerate(config):
        if sought in line_map(line):
            col = line.index(sought)
            if found_at is not None:
                raise RuntimeError(
                    f"Multiple instances of '{sought}' found:\n"
                    f"{found_at}\n"
                    f"{Location(row, col)}"
                )
            found_at = Location(row, col)
    return found_at


def just_content(line):
    """Strip comments and trailing whitespace"""
    if ";;" in line:
        line = line[: line.index(";;")]
    return line.rstrip()


def trim_whitespace(lines):
    for i in range(len(lines)):
        lines[i] = lines[i].rstrip() + "\n"
    return lines


def compile_files(
    indent: str, paths: list[str], transformation: Transformation
) -> list[str]:
    compiled = []
    prev = 0

    paths.sort(key=lambda path: path.split(os.sep)[1])  # Module name
    for path in paths:
        _, name, filename = path.split(os.sep, 2)
        doc_lines = []
        end_doc = False
        code_lines = []
        if "query." in filename:
            code_lines.append("\\n#+BEGIN_QUERY\n")
        with open(path) as file:
            for line in file:
                if not end_doc and line.lstrip().startswith(";"):
                    doc_lines.append(line)
                else:
                    end_doc = True
                    if transformation == Transformation.ClojureKeywordString:
                        line = line.replace("\\", "\\\\").replace(r'"', r"\"")
                        if code_lines:
                            line = indent + line
                    else:
                        line = indent + line
                    code_lines.append(line)
        for line in doc_lines:
            compiled.append(f"{indent}{line}")
        if transformation == Transformation.ClojureKeywordString:
            compiled.append(f'{indent}:{name} "')
        elif transformation == Transformation.ClojureKeywordValue:
            compiled.append(f"{indent}:{name}\n")
        elif transformation == Transformation.PlainText:
            pass
        else:
            raise RuntimeError(f"Unexpected transformation: {transformation}")
        if "query." in filename:
            code_lines.append(f"{indent}#+END_QUERY")
        elif code_lines:
            code_lines[-1] = code_lines[-1].rstrip()
        for line in code_lines:
            compiled.append(f"{line}")
        if transformation == Transformation.ClojureKeywordString:
            compiled.append('"\n')
        else:
            compiled.append("\n")
        log(f"  Compiled {len(compiled) - prev:3} lines from {path}")
        prev = len(compiled)
    return compiled


def parse_error(config, pos, msg):
    line_number = 1 + config[:pos].count("\n")
    return RuntimeError(f"Parse failed at line {line_number}: {msg}")


def show_diff(filename, before, after, force_color=False):
    diff = difflib.unified_diff(before, after, filename, filename)

    if sys.stdout.isatty() or force_color:

        def format(s):
            end = "\x1b[0m"
            if s.endswith("\n"):
                s = s[:-1]
                if s.endswith("\r"):
                    s = s[:-1]
                    end += "\r"
                end += "\n"
            if s.startswith(("+++ ", "--- ")):
                color = "\x1b[1;3m"
            elif s.startswith("@@ "):
                color = "\x1b[36;3m"
            elif s.startswith("-"):
                color = "\x1b[31m"
            elif s.startswith("+"):
                color = "\x1b[32m"
            else:
                color = "\x1b[38;5;245m"
            return f"{color}{s}{end}"

    else:

        def format(s):
            return s

    for line in diff:
        sys.stdout.write(format(line))


if __name__ == "__main__":
    main()
