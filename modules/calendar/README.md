# Calendar

A calendar showing the past week, current week, and two weeks ahead.

Each day includes:

- All blocks/tasks scheduled for the day (with or without times)
- Unscheduled tasks on the journal page for the day
- Any "event" blocks on the journal page (those starting with `HH:MM - `)

### Usage
```
{{calendar}}
calendar/show-weekends:: <show-weekends>
calendar/highlight-overdue:: <highlight-overdue>
```
- `<show-weekends>` - `true` (default) or `false` - whether to include or hide weekend days.
- `<highlight-overdue>` - `true` (default) or `false` - whether to highlight past tasks that are still to do.

### Example
```
{{calendar}}
calendar/highlight-overdue:: true
```
> ![](./preview.png)
