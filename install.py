#!/bin/env python3
import os
import sys
from enum import Enum
import argparse
import glob
import json
from dataclasses import dataclass


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


def main():
    parser = argparse.ArgumentParser(description="Install/update all modules")
    parser.add_argument("--uninstall", action="store_true", help="uninstall")
    parser.add_argument(
        "--output-directory",
        help="save modifications into this directory rather than "
        "overwriting the original files (useful for previewing changes "
        "before applying)",
    )
    parser.add_argument(
        "logseq_directory",
        # metavar="logseq-directory"
        help="path to your graph's logseq directory, where config.edn lives",
    )
    args = parser.parse_args()
    input_directory = args.logseq_directory
    output_directory = args.output_directory or input_directory

    with open(os.path.join(input_directory, "config.edn")) as config_edn:
        config = config_edn.readlines()
    try:
        with open(os.path.join(input_directory, "custom.css")) as custom_css:
            styles = custom_css.readlines()
    except FileNotFoundError:
        styles = [""]

    with open("./config.json") as config_file:
        install_config = json.load(config_file)

    key = ":macros"
    log(f"\nPreparing {key} entries for config.edn...")
    config = install(
        config,
        key=key,
        begin_mark=BEGIN_EDN.format("macros"),
        end_mark=END_EDN.format("macros"),
        include_patterns=install_config["patterns"]["macros"],
        transformation=Transformation.ClojureKeywordString,
        uninstall=args.uninstall,
    )
    key = ":query/result-transforms"
    log(f"\nPreparing {key} entries for config.edn...")
    config = install(
        config,
        key=key,
        begin_mark=BEGIN_EDN.format("query-result-transforms"),
        end_mark=END_EDN.format("query-result-transforms"),
        include_patterns=install_config["patterns"]["query-transforms"],
        transformation=Transformation.ClojureKeywordValue,
        uninstall=args.uninstall,
    )
    key = ":query/views"
    log("\nPreparing :macros entries for config.edn...")
    config = install(
        config,
        key=key,
        begin_mark=BEGIN_EDN.format("query-views"),
        end_mark=END_EDN.format("query-views"),
        include_patterns=install_config["patterns"]["query-views"],
        transformation=Transformation.ClojureKeywordValue,
        uninstall=args.uninstall,
    )
    log("\nPreparing CSS style entries for custom.css...")
    styles = install(
        styles,
        key=EOF,
        begin_mark=BEGIN_CSS.format("styles"),
        end_mark=END_CSS.format("styles"),
        include_patterns=install_config["patterns"]["styles"],
        transformation=Transformation.PlainText,
        uninstall=args.uninstall,
    )
    atomic_overwrite(os.path.join(output_directory, "config.edn"), "".join(config))
    atomic_overwrite(os.path.join(output_directory, "custom.css"), "".join(styles))


def log(msg):
    print(msg, file=sys.stderr)


def atomic_overwrite(path, content):
    log(f"\nWriting {path}...")
    temp_path = path + ".tmp"
    with open(temp_path, "w") as temp_file:
        temp_file.write(content)
    try:
        os.rename(temp_path, path)
    except:
        os.unlink(temp_path)
        raise
    log("Done.")


def install(
    config: list[str],
    key: str,
    begin_mark: str,
    end_mark: str,
    include_patterns: list[str],
    transformation: Transformation,
    uninstall: bool,
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

    compiled = compile_files(indent, include_patterns, transformation)

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


def compile_files(
    indent: str, include_patterns: list[str], transformation: Transformation
) -> list[str]:
    compiled = []
    prev = 0
    for path in (path for pattern in include_patterns for path in glob.glob(pattern)):
        _, name, ext = path.split("/", 2)
        doc_lines = []
        code_lines = []
        if "query." in ext:
            code_lines.append("\\n#+BEGIN_QUERY\n")
        with open(path) as file:
            for line in file:
                if line.lstrip().startswith(";"):
                    doc_lines.append(line)
                else:
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
        if "query." in ext:
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


if __name__ == "__main__":
    main()
