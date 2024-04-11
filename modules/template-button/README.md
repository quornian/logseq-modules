# Template Button

A button for inserting a template as a sibling after the current block, removing the current block in the process (unless `<keep-on-click>` is set).

### Usage
```
{{template-button <template-name>[, <keep-on-click>]}}
```
- `<template-name>` - the name of a template, as set by the `template::` property of a block, to insert.
- `<keep-on-click>` (written as `keep` or not set) - whether the block containing the button should be kept once the template has been inserted below it. Kept blocks allow multiple items to be added using the same button.
### Example
```
{{template-button Example Template, keep}}
```
> ![](./preview.png)
