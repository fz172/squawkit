# G1000 samples

Three 240-row windows cut from two real Garmin G1000 logs, used by `GarminG1000ParserTest` and
`HeaderSnifferTest`.

| File | Airframe | What it is there for |
| --- | --- | --- |
| `log_150513_081128_XX1.csv` | Piston, six-cylinder | The `#` units row, CHT/EGT/TIT banks, tanks named by side, a `bool` column, and a start with no GPS fix |
| `log_240810_104802_XX2.csv` | Turbine | Power-up: the first four rows carry no date, time or offset at all |
| `log_240810_110536_XX3.csv` | Turbine | The climb: ITT and the spool speeds are alive, and ground speed makes it airborne |

## Anonymisation

Following the rule the G3X samples use (design §6.4):

- Every latitude and longitude is offset by one constant, so the track shape survives but the
  location does not.
- The filename ident is replaced with a fictitious one. It is the only place a real airport appeared,
  and the parser reads it into `start_location_ident`.
- `system_id` was already zeroed by the recorder in both source logs, and a G1000 header carries no
  tail number at all — it names the airframe type, not the aircraft.

The two turbine windows come from one source log, so their headers are identical; the third file's
name carries its own first clocked minute rather than the original power-up time.

Rows are otherwise verbatim. The expected values in the tests were derived from these files with a
separate script rather than from the parser, so a parser bug reads as a mismatch instead of being
pinned as the answer.
