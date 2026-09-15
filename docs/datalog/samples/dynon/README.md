# Dynon SkyView samples

Two windows cut from real SkyView `USER_LOG_DATA` exports, used by `DynonParserTest`,
`HeaderSnifferTest` and `DataLogImporterImplTest`.

| File | What it is there for |
| --- | --- |
| `skyview_single_session.csv` | One power-on, every row dated. The straightforward case, and a file name the exporter did not write, so the parser finds no tail number and says so rather than guessing |
| `2019-04-28-N1234X-SN0001-15_3_4_4867-USER_LOG_DATA.csv` | Four power-on sessions in one download. The first two carry a GPS date; the last two never got a fix and take their date from the file name |

## What a SkyView file looks like

One header row of long names with the unit in parentheses, then the samples. **No metadata header
at all** — not the recorder, not its software, not the aeroplane. The exporter's own file name,
`<date>-<tail>-SN<serial>-<firmware>-USER_LOG_DATA.csv`, is the only place a SkyView states any of
that, which is why the parser reads it.

`Session Time` counts seconds since the unit powered on and **resets at every power cycle**. A
download holds every session since the last one: the file these windows come from held twenty-one,
spanning a month. Each becomes its own data log.

## Anonymisation

Following the rule the Garmin samples use (design §6.4):

- Every latitude and longitude is offset by one constant, so the track shape survives but the
  location does not.
- The tail number and unit serial in the file name are replaced with fictitious ones. Since the
  parser reads both out of the name, renaming the file is what anonymises the record.

Rows are otherwise verbatim, including the `UNKNOWN_DATE_TIME` the recorder writes when it has no
fix, and one truncated row the original file also contains.

## The thermocouple channels

Every `Thermocouple 1` to `Thermocouple 14` column is **empty in both files**. The installer labels
the channels in the SkyView itself and the export writes those labels instead, as `CHT 1`, `EGT 1`,
`CHT L TEMPERATURE` and so on. So nothing here exercises the channel-mapping prompt the requirements
assume, and none was built. Add one when a file turns up that needs it.
