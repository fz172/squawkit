# Avidyne Entegra samples

Four windows cut from real Avidyne `Engine_*_out.log` exports, used by `AvidyneParserTest` and
`HeaderSnifferTest`.

| File | Columns | What it is there for |
| --- | --- | --- |
| `Engine_060212_152326_out.log` | 27 | The plain case on an older unit: no software id, no turbo columns |
| `Engine_050911_233509_out.log` | 27 | Starts at 23:35 and runs past midnight, so the date has to advance |
| `Engine_120313_184105_out.log` | 32 | A turbo unit: pressure and density altitude, turbine inlet, the quoted discrete channels |
| `Engine_090121_191809_out.log` | 32 | The same unit correcting its clock backwards by 84 seconds, four rows in |

## What an Avidyne file looks like

```
Avidyne Engine Data Log; DAU Software ID: 5.0
3/13/12 18:41:05
"TIME","LAT","LON","PALT","DALT","E1",…
18:41:06,-0.0000,-0.0000,1172,1765, 1006,…
```

Three header lines: a title that may name the data acquisition unit's software, the log's start
date and time, then quoted column names. **No column states a unit** and **no row carries a date** —
every row has a time of day and nothing else, which is why the two edge cases above are the whole
difficulty of the format.

Every file also opens with one frame of sensor defaults, written before the log it just announced:
outside air at -40, oil at -19 or -67, pressure altitude at -16000, the engine stopped. Its clock is
a few seconds *earlier* than the start on line 2, which is what identifies it. The parser drops
leading rows on that basis rather than on being first, so the clock correction later in the fourth
file survives.

## Anonymisation

Following the rule the Garmin and Dynon samples use (design §6.4): every latitude and longitude is
offset by one constant, so the track shape survives but the location does not. A zero pair means the
unit had no fix and is left alone, since shifting it would invent a position.

The file names are Avidyne's own and carry no tail number or serial. Nothing else in the format
identifies an aircraft.

Rows are otherwise verbatim, including the sentinel values and the battery ammeter that reads a
constant -99 on the airframe that has none fitted.
