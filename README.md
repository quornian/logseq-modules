# Logseq Modules

This project brings a modular system to [Logseq](https://logseq.com)'s configuration, allowing you to work on complex collections of macros, queries, views, styles, and more.

A selection of useful widgets is included, both to be used as-is throughout your graph, and as a basis to demonstrate how to write your own modules.

The aim of this project is to provide an installation mechanism that allows the creation of complex setups, without having to manage sprawling `config.edn` and `custom.css` files manually. It does this by letting you edit each module as a separate set of files that are then bundled into the Logseq config files in a predictable manner.

Since these are not plugins, they will work on all platforms including mobile.

## What You Get in The Box

| Module | Preview / Usage |
|--------|-----------------|
| [Progress Bar](./modules/progress-bar) | ![](./modules/progress-bar/preview.png) |
| | `{{progress-bar 73% Complete, 73}}` |
| [Template Button](./modules/template-button) | ![](./modules/template-button/preview.png) |
| | `{{template-button Example Template}}` |
| [Bar Chart](./modules/bar-chart) | ![](./modules/bar-chart/preview.png) |
| | `{{bar-chart Status Property, status}}`

See the individual module `README.md` files for more information on usage, and some examples.

## Installation

If you're like me, you like to know how things work before installing them. If so, take a look at [How it Works](#how-it-works) below. Otherwise, here's how you install the modules...

Use the `install.py` script to install all modules. Just point it to the directory named `logseq` inside your graph:
```
python3 install.py /path/to/your/graph/logseq
```
If you want to preview what the installation would do, you can offer a secondary output directory:
```
mkdir /tmp/preview
python3 install.py /path/to/your/directory/named/logseq --output-directory /tmp/preview
```
To remove the modules, use the `--uninstall` flag.

## How it Works

Each module can make use of Logseq's [macros](https://docs.logseq.com/#/page/macros), custom [CSS styling](https://docs.logseq.com/#/page/custom.css), and [Advanced Queries](https://docs.logseq.com/#/page/advanced%20queries) (with custom result transforms and views).

A module is defined by a named subdirectory of the `modules` directory, containing one or more of the following files:
```
modules/
  <name-of-module>/
    macro[.query].clj
    query-transform.clj
    query-view.clj
    style.css
```

The contents of each of these files is inserted into the relevant section of the relevant configuration file of Logseq, with minor [transformations](#transformations).

Entries are added to the graph's config file, `logseq/config.edn`, under the `:macros`, `:query/result-transforms`, and `:query/views` sections. Styling is added to the `logseq/custom.css` file.

All entries are inserted in a block that looks like this:
```clojure
;; <logseq-modules query-views>
...
;; </logseq-modules query-views>
```
```css
/* <logseq-modules styles> */
...
/* </logseq-modules styles> */
```

making updates and removal easy.

### Transformations

- The contents of `macro.clj` files are copied as-is into `logseq/config.edn` under the `:macros` mapping, under their `:<name-of-module>` key.
  ```clojure
  ;; File: config.edn
  {:macros
   {:<name-of-module> FILE-CONTENT}}
  ```

- The contents of `macro.query.clj` files are first transformed so that they create an advanced query inside the macro. Logseq requires a blank line before the query block, and for all strings and backslashes to be escaped for this to work. The purpose of this transformation is to allow you to write a normal Clojure source file and have these requirements handled for you. An example transformation looks like this:
  
  Source:
  ```clojure
  ;; File: modules/<name-of-module>/macro.query.clj
  {:title "Hello \"$1\""}
  ```

  Result:
  ```clojure
  ;; File: config.edn
  {:macros
   {:<name-of-module> "\n#+BEGIN_QUERY
   {:title \"Hello \\\"$1\\\"\"}
    #+END_QUERY"}}
  ```

- The contents of `query-transform.clj` and `query-view.clj` are handled similarly to `macro.clj` - copied as-is under the `:query/result-transforms` and `:query/views` sections of `config.edn`, respectively.
  
  ```clojure
  ;; File: config.edn
  {:macros
   {:<name-of-module> FILE-CONTENT}

   :query/result-transforms
   {:<name-of-module> FILE-CONTENT}
   
   :query/views
   {:<name-of-module> FILE-CONTENT}}
  ```

### Further Notes

The `%1`-like arguments to macros are unaffected by the transformations and can be used as normal.

The directory name `<name-of-module>` is used to name the macro,
result-transform, and view, so these names can be used within the same module or even across modules. For example you could have a module that just provides a view, and is used by several other modules, or just as a view to queries in your graph.

In CSS, to target the macro body (at time of writing) you can use `div.macro[data-macro-name="<name-of-module>"]`, or create your own elements with unique class names in your view. It's up to you to make any CSS classes unique and not to collide with Logseq's own.

Note: while I made every attempt to make the insertion logic sound, usual caution should be taken. This tool is provided as-is with absolutely no warranty. Use at your own risk (and back up your files). See below for how to preview the changes before applying them.

## Configuration

If you want only some modules and not others, or to include a different directory, edit the `config.json` file under the "patterns" key. Just make sure the structure remains: `<directory-name>/<name-of-module>/<file-pattern>.clj`.
