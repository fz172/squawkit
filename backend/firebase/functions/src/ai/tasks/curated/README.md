# Curated suggestions

One file per template id (`ThingTemplate.id`), holding the tasks every suggestion run for that
template returns first. Design: `docs/ai/task_population_design.md` §6.8; PRD R9a.

They began as the templates' `starter_tasks` (2026-10-02) and are edited by hand from now on. A
change ships with the next functions deploy; no app release. No file for `custom`.

Each item:

| Field              | Meaning                                                                                                                                                                                                                   |
|--------------------|---------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| `title`            | Unique within the file                                                                                                                                                                                                    |
| `description`      | Shown on the card and saved as the task's notes                                                                                                                                                                           |
| `componentSlotKey` | A slot the template defines (`engine`), or `""` for the whole Thing                                                                                                                                                       |
| `rules`            | One or more; two mean whichever comes first. `time` (`every`, `unit`: days/months/years), `meter` (`meterKey` the template defines, `interval`), `seasonal` (`months` 1–12, `dayOfMonth`), `on_condition` (`description`) |
| `preselect`        | Unused by the app since 2026-10-03: nothing starts checked (PRD R27)                                                                                                                                                      |
| `sourceKind`       | `common_practice` or `manufacturer_schedule` (`document` and `logs` are for AI results)                                                                                                                                   |
| `citation`         | Shown as the source, such as `14 CFR 91.413`, or `""`. The task is still typed routine                                                                                                                                    |

`npm test` checks every file: the shape, unique titles, and that each meter and slot exists in the
template.
