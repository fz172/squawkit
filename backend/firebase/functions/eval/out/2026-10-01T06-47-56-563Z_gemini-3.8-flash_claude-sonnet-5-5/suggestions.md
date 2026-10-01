# gemini-3.8-flash + claude-sonnet-5-5, recall on fast

`fast=gemini-3.8-flash` · `strong=claude-sonnet-5-5` · `locate=keywords` · `recallTier=fast` · `claude=direct` · `attachPdf=true` · `ocr=none` · `repeat=3` · `generationVersion=tasks-4`

Hard gates: **pass** · document recall 96% · intervals 93% · citations 97% · precision 87% · mean cost $0.444 per run · full scoring in [report.md](report.md)

## sling-tsi-three-manuals

**succeeded** · 36 suggestions · recall 100% (17/17) · intervals 90% · citations 97% · precision 83% · $0.717 · 168.7 s

Documents: Maintenance Manual rev 1.3 (maintenance manual) · Maintenance Manual Line for Rotax Engine Type 915 i A / C24 Series rev Rev. 3 (Ed.0 / May 01 2025) (maintenance manual) · PERIODIC (100-HR / ANNUAL) INSPECTION & SERVICE CHECKLIST rev Rev 1 (service instruction)

| # | Suggestion | Component | Schedule | First due | Source | Pre-selected | Matched expected |
|---|---|---|---|---|---|---|---|
| 1 | 100-hour / annual condition inspection (airframe) | Thing | every 100 airframe hours or every 12 months | last done 2026-06-10 | document: Maintenance Manual, rev 1.3, p. 67, PDF p. 83, 59, 63 | yes | airframe 100-hour / annual condition inspection |
| 2 | Engine 100-hour / 12-month inspection | engine | every 100 engine hours or every 12 months | last done 2026-06-10 | document: Maintenance Manual Line for Rotax Engine Type 915 i A / C24 Series, rev Rev. 3 (Ed.0 / May 01 2025), p. 05-20-00 pp. 2, 8-17, PDF p. 55, 58, 64, 65, 66, 67, 68, 69, 70, 71, 72, 73 | yes | engine 100-hour / annual check |
| 3 | Engine oil and filter change | engine | every 100 engine hours or every 12 months | last done 2026-06-10 | document: Maintenance Manual, rev 1.3, p. 67, PDF p. 83, 80 | yes | engine oil and filter change |
| 4 | Engine and propeller service (50 hours / annual) | engine | every 50 engine hours or every 12 months | — | document: Maintenance Manual, rev 1.3, p. 66, PDF p. 82 | yes | — |
| 5 | Engine 50-hour inspection | engine | every 50 engine hours | — | document: Maintenance Manual Line for Rotax Engine Type 915 i A / C24 Series, rev Rev. 3 (Ed.0 / May 01 2025), p. 05-20-00 pp. 2, 8, 9, 11, 13, PDF p. 58, 64, 65, 67, 69 | yes | engine 50-hour check |
| 6 | Engine 200-hour inspection (additional checks) | engine | every 200 engine hours | — | document: Maintenance Manual Line for Rotax Engine Type 915 i A / C24 Series, rev Rev. 3 (Ed.0 / May 01 2025), p. 05-20-00 pp. 2, 8, 9, 12, 13, PDF p. 58, 64, 65, 68, 69 | yes | engine 200-hour check |
| 7 | Engine 600-hour inspection (additional checks) | engine | every 600 engine hours | — | document: Maintenance Manual Line for Rotax Engine Type 915 i A / C24 Series, rev Rev. 3 (Ed.0 / May 01 2025), p. 05-20-00 pp. 2, 8, 14-16, PDF p. 58, 64, 70, 71, 72 | yes | engine 600-hour check |
| 8 | Replace spark plugs | engine | every 400 engine hours | — | document: Maintenance Manual Line for Rotax Engine Type 915 i A / C24 Series, rev Rev. 3 (Ed.0 / May 01 2025), p. 9, PDF p. 65 | yes | spark plug replacement |
| 9 | Replace fuel filter | engine | every 200 engine hours or every 2 years | — | document: Maintenance Manual Line for Rotax Engine Type 915 i A / C24 Series, rev Rev. 3 (Ed.0 / May 01 2025), p. 14, PDF p. 70 | yes | engine fuel filter replacement |
| 10 | Lubricate wastegate lever | engine | every 100 engine hours | — | document: Maintenance Manual Line for Rotax Engine Type 915 i A / C24 Series, rev Rev. 3 (Ed.0 / May 01 2025), p. 15, PDF p. 71 | yes | — |
| 11 | Engine cleaning | engine | every 100 engine hours | — | document: Maintenance Manual Line for Rotax Engine Type 915 i A / C24 Series, rev Rev. 3 (Ed.0 / May 01 2025), p. 16, PDF p. 72 | yes | — |
| 12 | Coat propeller shaft with corrosion inhibitor | engine | every 100 engine hours | — | document: Maintenance Manual Line for Rotax Engine Type 915 i A / C24 Series, rev Rev. 3 (Ed.0 / May 01 2025), p. 15, PDF p. 71 | yes | — |
| 13 | Change torsion shaft | engine | every 600 engine hours | — | document: Maintenance Manual Line for Rotax Engine Type 915 i A / C24 Series, rev Rev. 3 (Ed.0 / May 01 2025), p. 16, PDF p. 72 | yes | torsion shaft replacement |
| 14 | Engine general overhaul (TBO) | engine | every 1,200 engine hours or every 15 years | — | document: Maintenance Manual Line for Rotax Engine Type 915 i A / C24 Series, rev Rev. 3 (Ed.0 / May 01 2025), p. 4, PDF p. 52 | yes | engine overhaul (TBO) |
| 15 | Replace engine rubber hoses | engine | every 5 years | — | document: Maintenance Manual Line for Rotax Engine Type 915 i A / C24 Series, rev Rev. 3 (Ed.0 / May 01 2025), p. 6, PDF p. 54 | yes | engine rubber hose replacement (5-year) |
| 16 | Replace rubber plate under expansion tank | engine | every 5 years | — | document: Maintenance Manual Line for Rotax Engine Type 915 i A / C24 Series, rev Rev. 3 (Ed.0 / May 01 2025), p. 6, PDF p. 54 | yes | — |
| 17 | Replace fuel pressure regulator assembly | engine | every 5 years | — | document: Maintenance Manual Line for Rotax Engine Type 915 i A / C24 Series, rev Rev. 3 (Ed.0 / May 01 2025), p. 6, PDF p. 54 | yes | fuel pressure regulator replacement |
| 18 | Replace engine coolant | engine | on condition | — | document: Maintenance Manual, rev 1.3, p. 65, PDF p. 81 | yes | engine coolant replacement |
| 19 | Air filter remove and clean or replace | engine | every 100 engine hours or every 12 months | — | document: Maintenance Manual, rev 1.3, p. 67, PDF p. 83, 80 | yes | engine air filter service (interval differs) |
| 20 | Lubrication of airframe points (100 hours / yearly) | Thing | every 100 airframe hours or every 12 months | — | document: Maintenance Manual, rev 1.3, p. 63, PDF p. 79 | yes | airframe lubrication |
| 21 | Lubricate throttle, choke and heater cables (300 hours) | Thing | every 300 airframe hours | — | document: Maintenance Manual, rev 1.3, p. 63, PDF p. 79 | yes | throttle and heater cable lubrication (interval differs) |
| 22 | Replace air ducting | Thing | every 300 airframe hours | — | document: Maintenance Manual, rev 1.3, p. 67, PDF p. 83 | yes | replace air ducting (SCAT/CAT tubing) |
| 23 | 2000-hour airframe service checklist | Thing | every 2,000 airframe hours | — | document: Maintenance Manual, rev 1.3, p. 56, PDF p. 72, 83 | yes | airframe 2000-hour service |
| 24 | Compass swing | Thing | every 12 months | — | document: Maintenance Manual, rev 1.3, p. 52, PDF p. 68 | yes | compass swing |
| 25 | Mass and balance check | Thing | every 5 years | — | document: Maintenance Manual, rev 1.3, p. 52, PDF p. 68 | yes | weight and balance check |
| 26 | Altimeter & pitot-static system test | Thing | every 24 months | — | document: Maintenance Manual, rev 1.3, p. 53, PDF p. 69 | yes | pitot-static system test |
| 27 | Ballistic parachute rocket service | Thing | every 6 years | — | document: Maintenance Manual, rev 1.3, p. 65, PDF p. 81 | yes | ballistic parachute rocket service |
| 28 | Ballistic parachute canopy repack | Thing | every 6 years | — | document: Maintenance Manual, rev 1.3, p. 65, PDF p. 81 | yes | ballistic parachute repack |
| 29 | Muffler / heat exchanger disassemble and inspect | engine | every 5 years | — | document: Maintenance Manual, rev 1.3, p. 65, PDF p. 81 | yes | muffler / heat exchanger inspection |
| 30 | Propeller 100-hour / annual inspection | propeller | every 100 prop hours or every 12 months | — | document: PERIODIC (100-HR / ANNUAL) INSPECTION & SERVICE CHECKLIST, rev Rev 1, p. 2, PDF p. 2, 5, 6, 7, 8, 9 | yes | propeller 100-hour / annual inspection |
| 31 | Replace carbon brushes in sensor-brush assembly | propeller | every 300 prop hours | — | document: PERIODIC (100-HR / ANNUAL) INSPECTION & SERVICE CHECKLIST, rev Rev 1, p. 9, PDF p. 9, 10 | yes | propeller carbon brush replacement |
| 32 | Lubricate propeller hub and blade assemblies | propeller | every 100 prop hours or every 12 months | — | document: PERIODIC (100-HR / ANNUAL) INSPECTION & SERVICE CHECKLIST, rev Rev 1, p. 10, PDF p. 10 | yes | propeller hub regrease |
| 33 | Engine 25-hour inspection (first only) | engine | once | 25 engine hours | document: Maintenance Manual Line for Rotax Engine Type 915 i A / C24 Series, rev Rev. 3 (Ed.0 / May 01 2025), p. 05-20-00 pp. 2, 8-17, PDF p. 58, 64, 65, 66, 67, 68, 70, 71, 72, 73 | no | engine first 25-hour check |
| 34 | Airframe 25-hour inspection (first only) | Thing | once | 25 airframe hours | document: Maintenance Manual, rev 1.3, p. 66, PDF p. 82, 63 | no | airframe first 25-hour inspection |
| 35 | Engine oil change after first 25 hours | engine | once | 25 engine hours | document: Maintenance Manual, rev 1.3, p. 66, PDF p. 82 | no | — |
| 36 | First 50 hours engine and propeller service | engine | once | 50 engine hours | document: Maintenance Manual, rev 1.3, p. 66, PDF p. 82 | no | propeller initial 50-hour inspection (interval differs) (wrong page) |

<details><summary>Rationale and description</summary>

**100-hour / annual condition inspection (airframe).** Sling recommends the airframe inspection every 100 hours or annually, whichever comes first, read with the Rotax and propeller checklists.

Complete the checklist in paragraph 2.5.1 of the maintenance manual. Covers battery, brake fluid, fuel filter and lines, propeller bolts, control surfaces, tyres, air ducting and parachute bay. The annual limit matches the 12-month condition inspection for an experimental aircraft (14 CFR Part 43 Appendix D). If the aircraft is used for hire, 14 CFR 91.409(b) also requires a 100-hour inspection.

**Engine 100-hour / 12-month inspection.** Rotax recommends inspecting the engine every 100 hours or 12 months, whichever comes first.

Tolerance is ±10 hr and ±2 months. The differential pressure, oil tank and crankcase pressure checks apply when leaded AVGAS is used for more than 30% of operation. Includes spark plug check, magnetic plug, oil filter inspection, exhaust, fuel system, ECU fault memory, wastegate and propeller shaft corrosion checks, plus an engine test run with LANE check.

**Engine oil and filter change.** Rotax and Sling recommend an oil and filter change every 100 hours or annually, whichever comes first.

Drain the oil, refill with about 3 litres, fit a new oil filter and clean the turbo oil sump screen. If operated predominantly on leaded AVGAS (more than 30% of engine time), change the oil every 25 hours and the filter every 50 hours. The starter pack suggests every 50 engine hours.

**Engine and propeller service (50 hours / annual).** Sling recommends performing all engine and propeller service requirements every 50 hours or annually, whichever comes first.

Perform all service requirements in the latest Rotax 915 iS manuals and in the propeller manufacturer's operator manual.

**Engine 50-hour inspection.** Rotax recommends but does not require a 50-hour engine inspection.

Checks the compression by the differential pressure method (required every 50 hours up to 400 hours only for engines with part no. 815253 rectangular ring, S/N 9122005 to 10004312). Oil filter cut-open and turbo sump screen inspection apply when leaded fuel is used more than 30% of the time.

**Engine 200-hour inspection (additional checks).** Rotax recommends a 100-hour check plus additional checks every 200 hours.

Differential pressure check, spark plug connector pull-off force (minimum 30 N), oil tank check and wiring harness inspection, together with the 100-hour check.

**Engine 600-hour inspection (additional checks).** Rotax recommends additional checks every 600 hours on top of the 100- and 200-hour checks.

Check ECU and fuse box mountings, gear set, overload clutches and intermediate shaft wear. The EMS and airframe circuit test applies to the 915 iSc C24 only.

**Replace spark plugs.** Rotax recommends replacing the spark plugs every 400 hours.

Replace every 400 hours; every 200 hours when leaded AVGAS is used for more than 30% of operation. Use genuine Rotax plugs.

**Replace fuel filter.** Rotax recommends replacing the genuine fuel filter every 200 hours or 2 years, whichever comes first.

Replace after the first 100 hours, then every 200 hours or at most 2 years. If a non-Rotax filter is used, follow the aircraft manufacturer's specification (SI-PAC-007).

**Lubricate wastegate lever.** Rotax recommends lubricating the wastegate lever every 100 hours.

Lubricate the wastegate lever every 100 hours (first at 25 hours).

**Engine cleaning.** Rotax recommends cleaning the engine every 100 hours.

Engine cleaning at 100 hours (first at 25 hours).

**Coat propeller shaft with corrosion inhibitor.** Rotax recommends coating the exposed propeller shaft with corrosion inhibitor every 100 hours.

Do not apply inhibitor to the propeller/flange contact surface. See MMH Chap. 72-10-00.

**Change torsion shaft.** Rotax recommends changing the torsion shaft every 600 hours.

Change the torsion shaft at 600 hours. See MMH Chap. 72-10-00.

**Engine general overhaul (TBO).** Rotax sets the 915 iS overhaul interval at 1200 hours or 15 years, whichever comes first.

Extension or exceeding of the TBO by 5% or 6 months (whichever comes first) is allowed. Overhaul per the current Overhaul Manual.

**Replace engine rubber hoses.** Rotax recommends replacing the engine's rubber hoses every 5 years, in addition to visual inspections.

Covers the cooling system hoses (genuine Rotax silicone hoses are on-condition), lubrication system hoses supplied with the engine, the air intake connecting hose and the turbocharger-to-airbox hose, plus fuel hoses and other rubber parts.

**Replace rubber plate under expansion tank.** Rotax recommends replacing the rubber plate under the expansion tank every 5 years.

Replace every 5 years.

**Replace fuel pressure regulator assembly.** Rotax recommends replacing the fuel pressure regulator every 5 years.

Replace only the pressure regulator, not the housing, every 5 years.

**Replace engine coolant.** Rotax and Sling recommend replacing the coolant per the coolant manufacturer's instructions, and at the latest at overhaul.

The documents give no fixed interval; the 2-year interval comes from general knowledge for conventional coolant. Flush the system if there are heavy deposits on the expansion tank or radiator cap, or if the coolant maker requires a change interval.

**Air filter remove and clean or replace.** Sling recommends cleaning or replacing the air filter every 100 hours or annually.

Clean more often in a dusty environment and replace if necessary.

**Lubrication of airframe points (100 hours / yearly).** Sling recommends lubricating the control system and gear points every 100 hours or yearly.

Lubricate with approved lubricants: rudder and pedal cable terminals and guides, elevator pushrod guide and torque tube bushes, trim tab hinge, aileron pushrod guide, flap torque tube bushes and actuator ends, pedal bushes, control sticks, nose gear support blocks and main wheel bearings. Canopy hinges and latches as needed.

**Lubricate throttle, choke and heater cables (300 hours).** Sling recommends lubricating the engine control cables every 300 hours.

Lubricate the engine throttle cable, engine choke cable and heater activation cables.

**Replace air ducting.** Sling recommends replacing all air ducting every 300 hours.

Replace CAT and SCAT tubing every 300 hours; inspect its condition at each 100-hour / annual inspection.

**2000-hour airframe service checklist.** Sling recommends a detailed structural service every 2000 hours, in conjunction with engine overhaul.

Per paragraph 2.5.2: remove the wings for spar inspection, replace the AN5-10A rear spar bolt with AN6-10A, replace main spar and main undercarriage attachment hardware, replace the elevator trim tab control horn, inspect rudder cables, stabiliser attachments, hinge brackets and bell cranks, and remove the main landing gear for crack checks. Done with the 100-hour checklist and the engine and propeller checklists.

**Compass swing.** Sling recommends verifying compass accuracy with a compass swing every 12 months.

Read with SA CATS 43.02.18.

**Mass and balance check.** Sling recommends a mass and balance check every 5 years.

Read with SA CATS 43.02.07.

**Altimeter & pitot-static system test.** The manual covers a pitot-static test; the starter pack's regulatory 24-month interval for IFR operations applies here.

Test for freedom from obstructions and leaks, test the warning unit and pitot heater, check altimeters, airspeed indicators and VSI for accuracy. 14 CFR 91.411 requires this within the preceding 24 calendar months for IFR. The Sling manual performs it at the annual inspection (every 1 year).

**Ballistic parachute rocket service.** Sling recommends servicing the ballistic parachute rocket every 6 years (Magnum 901) or 12 years (BRS).

Interval is 6 years for a Magnum 901 and 12 years for a BRS. The shorter 6-year interval is used because the installed unit is not recorded. Count from the last service date.

**Ballistic parachute canopy repack.** Sling recommends repacking the ballistic parachute canopy every 6 years.

Applies to both Magnum 901 and BRS systems.

**Muffler / heat exchanger disassemble and inspect.** Sling recommends disassembling and inspecting the muffler and heat exchanger assembly every 5 years.

See paragraph 7.1.1 for removal and disassembly instructions.

**Propeller 100-hour / annual inspection.** Airmaster recommends the propeller periodic inspection every 100 hours or annually.

Per ASI-7-1-1: inspect the spinner, blades, retention nuts and bearings, hub and pitch change mechanism, slipring and sensor-brush assembly. Also performed at the first 25, 50 and 100 hours. Can be done with the hub mounted on the engine flange.

**Replace carbon brushes in sensor-brush assembly.** Airmaster recommends replacing the carbon brushes at the prescribed service interval per ASI-7-1-2.

Interval is 300 hours for standard sliprings and 600 hours for mini sliprings; 300 hours is used here. Replace earlier if required.

**Lubricate propeller hub and blade assemblies.** Airmaster recommends re-greasing the hub and blade assemblies with the periodic inspection.

Wipe old grease from the hub bores and blade retention assemblies, then lubricate per ASI-4-5 (mobilgrease28 recommended).

**Engine 25-hour inspection (first only).** Rotax recommends a one-time inspection after the first 25 hours on new or overhauled engines.

Uses the checks of the 100-hour inspection marked in the 25 column, including spark plug check, magnetic plug, hoses, wiring, ECU faults, wastegate and a test run with LANE check. Oil change, cleaning and wastegate lubrication are separate items.

**Airframe 25-hour inspection (first only).** Sling recommends a one-time airframe inspection after the first 25 hours of operation.

Complete the checklist in paragraph 2.5 and rectify any shortcomings. Also perform the propeller and engine service requirements.

**Engine oil change after first 25 hours.** Sling recommends a first oil change after the initial 25 hours.

Refer to the latest Rotax 915 iS / 916 iS manuals.

**First 50 hours engine and propeller service.** Sling recommends a one-time engine and propeller service after the first 50 hours.

Perform all engine service requirements per the Rotax manuals and all propeller service requirements per the propeller manual.

</details>

- Duplicate of a matched task: Engine and propeller service (50 hours / annual)
- Duplicate of a matched task: Engine oil change after first 25 hours
- Not in the answer key: Lubricate wastegate lever
- Not in the answer key: Engine cleaning
- Not in the answer key: Coat propeller shaft with corrosion inhibitor
- Not in the answer key: Replace rubber plate under expansion tank

## sling-tsi-three-manuals

**succeeded** · 33 suggestions · recall 100% (17/17) · intervals 81% · citations 96% · precision 82% · $0.723 · 172.1 s

Documents: Maintenance Manual rev 1.3 (maintenance manual) · Maintenance Manual Line for Rotax Engine Type 915 i A / C24 Series rev Rev. 3 (Ed.0 / May 01 2025) (maintenance manual) · PERIODIC (100-HR / ANNUAL) INSPECTION & SERVICE CHECKLIST rev Rev 1 (service instruction)

| # | Suggestion | Component | Schedule | First due | Source | Pre-selected | Matched expected |
|---|---|---|---|---|---|---|---|
| 1 | 100-hour / annual condition inspection (airframe) | Thing | every 100 airframe hours or every 12 months | last done 2026-06-10 | document: Maintenance Manual, rev 1.3, p. 67, PDF p. 83, 59, 63 | yes | — |
| 2 | Engine 100-hour / 12-month inspection | engine | every 100 engine hours or every 12 months | last done 2026-06-10 | document: Maintenance Manual Line for Rotax Engine Type 915 i A / C24 Series, rev Rev. 3 (Ed.0 / May 01 2025), p. 05-20-00 pp. 2, 8-17, PDF p. 55, 58, 64, 65, 66, 67, 68, 69, 70, 71, 72, 73 | yes | engine 100-hour / annual check |
| 3 | Engine oil and filter change | engine | every 100 engine hours or every 12 months | last done 2026-06-10 | document: Maintenance Manual, rev 1.3, p. 67, PDF p. 83, 80 | yes | engine oil and filter change (interval differs) |
| 4 | Engine and propeller service (50 hours / annual) | engine | every 50 engine hours or every 12 months | — | document: Maintenance Manual, rev 1.3, p. 66, PDF p. 82 | yes | — |
| 5 | Engine 50-hour inspection | engine | every 50 engine hours | — | document: Maintenance Manual Line for Rotax Engine Type 915 i A / C24 Series, rev Rev. 3 (Ed.0 / May 01 2025), p. 05-20-00 pp. 2, 8, 9, 11, 13, PDF p. 58, 64, 65, 67, 69 | yes | — |
| 6 | Engine 200-hour inspection (additional checks) | engine | every 200 engine hours | — | document: Maintenance Manual Line for Rotax Engine Type 915 i A / C24 Series, rev Rev. 3 (Ed.0 / May 01 2025), p. 05-20-00 pp. 2, 8, 9, 12, 13, PDF p. 58, 64, 65, 68, 69 | yes | — |
| 7 | Engine 600-hour inspection (additional checks) | engine | every 600 engine hours | — | document: Maintenance Manual Line for Rotax Engine Type 915 i A / C24 Series, rev Rev. 3 (Ed.0 / May 01 2025), p. 05-20-00 pp. 2, 8, 14-16, PDF p. 58, 64, 70, 71, 72 | yes | — |
| 8 | Replace spark plugs | engine | every 400 engine hours | — | document: Maintenance Manual Line for Rotax Engine Type 915 i A / C24 Series, rev Rev. 3 (Ed.0 / May 01 2025), p. 9, PDF p. 65 | yes | — |
| 9 | Replace fuel filter | engine | every 200 engine hours or every 2 years | — | document: Maintenance Manual Line for Rotax Engine Type 915 i A / C24 Series, rev Rev. 3 (Ed.0 / May 01 2025), p. 14, PDF p. 70 | yes | — |
| 10 | Lubricate wastegate lever | engine | every 100 engine hours | — | document: Maintenance Manual Line for Rotax Engine Type 915 i A / C24 Series, rev Rev. 3 (Ed.0 / May 01 2025), p. 15, PDF p. 71 | yes | — |
| 11 | Engine cleaning | engine | every 100 engine hours | — | document: Maintenance Manual Line for Rotax Engine Type 915 i A / C24 Series, rev Rev. 3 (Ed.0 / May 01 2025), p. 16, PDF p. 72 | yes | — |
| 12 | Coat propeller shaft with corrosion inhibitor | engine | every 100 engine hours | — | document: Maintenance Manual Line for Rotax Engine Type 915 i A / C24 Series, rev Rev. 3 (Ed.0 / May 01 2025), p. 15, PDF p. 71 | yes | — |
| 13 | Change torsion shaft | engine | every 600 engine hours | — | document: Maintenance Manual Line for Rotax Engine Type 915 i A / C24 Series, rev Rev. 3 (Ed.0 / May 01 2025), p. 16, PDF p. 72 | yes | torsion shaft replacement |
| 14 | Engine general overhaul (TBO) | engine | every 1,200 engine hours or every 15 years | — | document: Maintenance Manual Line for Rotax Engine Type 915 i A / C24 Series, rev Rev. 3 (Ed.0 / May 01 2025), p. 4, PDF p. 52 | yes | engine overhaul (TBO) |
| 15 | Replace engine rubber hoses | engine | every 5 years | — | document: Maintenance Manual Line for Rotax Engine Type 915 i A / C24 Series, rev Rev. 3 (Ed.0 / May 01 2025), p. 6, PDF p. 54 | yes | — |
| 16 | Replace rubber plate under expansion tank | engine | every 5 years | — | document: Maintenance Manual Line for Rotax Engine Type 915 i A / C24 Series, rev Rev. 3 (Ed.0 / May 01 2025), p. 6, PDF p. 54 | yes | — |
| 17 | Replace fuel pressure regulator assembly | engine | every 5 years | — | document: Maintenance Manual Line for Rotax Engine Type 915 i A / C24 Series, rev Rev. 3 (Ed.0 / May 01 2025), p. 6, PDF p. 54 | yes | fuel pressure regulator replacement |
| 18 | Replace engine coolant | engine | on condition | — | document: Maintenance Manual, rev 1.3, p. 65, PDF p. 81 | yes | — |
| 19 | Air filter remove and clean or replace | engine | every 100 engine hours or every 12 months | — | document: Maintenance Manual, rev 1.3, p. 67, PDF p. 83, 80 | yes | — |
| 20 | Lubrication of airframe points (100 hours / yearly) | Thing | every 100 airframe hours or every 12 months | — | document: Maintenance Manual, rev 1.3, p. 63, PDF p. 79 | yes | — |
| 21 | Lubricate throttle, choke and heater cables (300 hours) | Thing | every 300 airframe hours | — | document: Maintenance Manual, rev 1.3, p. 63, PDF p. 79 | yes | — |
| 22 | Replace air ducting | Thing | every 300 airframe hours | — | document: Maintenance Manual, rev 1.3, p. 67, PDF p. 83 | yes | — |
| 23 | 2000-hour airframe service checklist | Thing | every 2,000 airframe hours | — | document: Maintenance Manual, rev 1.3, p. 56, PDF p. 72, 83 | yes | airframe 2000-hour service |
| 24 | Compass swing | Thing | every 12 months | — | document: Maintenance Manual, rev 1.3, p. 52, PDF p. 68 | yes | compass swing |
| 25 | Mass and balance check | Thing | every 5 years | — | document: Maintenance Manual, rev 1.3, p. 52, PDF p. 68 | yes | weight and balance check |
| 26 | Altimeter & pitot-static system test | Thing | every 24 months | — | document: Maintenance Manual, rev 1.3, p. 53, PDF p. 69 | yes | — |
| 27 | Ballistic parachute rocket service | Thing | every 6 years | — | document: Maintenance Manual, rev 1.3, p. 65, PDF p. 81 | yes | — |
| 28 | Ballistic parachute canopy repack | Thing | every 6 years | — | document: Maintenance Manual, rev 1.3, p. 65, PDF p. 81 | yes | ballistic parachute repack |
| 29 | Muffler / heat exchanger disassemble and inspect | engine | every 5 years | — | document: Maintenance Manual, rev 1.3, p. 65, PDF p. 81 | yes | muffler / heat exchanger inspection |
| 30 | Propeller 100-hour / annual inspection | propeller | every 100 prop hours or every 12 months | — | document: PERIODIC (100-HR / ANNUAL) INSPECTION & SERVICE CHECKLIST, rev Rev 1, p. 2, PDF p. 2, 5, 6, 7, 8, 9 | yes | propeller 100-hour / annual inspection |
| 31 | Replace carbon brushes in sensor-brush assembly | propeller | every 300 prop hours | — | document: PERIODIC (100-HR / ANNUAL) INSPECTION & SERVICE CHECKLIST, rev Rev 1, p. 9, PDF p. 9, 10 | yes | propeller carbon brush replacement |
| 32 | Lubricate propeller hub and blade assemblies | propeller | every 100 prop hours or every 12 months | — | document: PERIODIC (100-HR / ANNUAL) INSPECTION & SERVICE CHECKLIST, rev Rev 1, p. 10, PDF p. 10 | yes | propeller hub regrease |
| 33 | Engine 25-hour inspection (first only) | engine | once | 25 engine hours | document: Maintenance Manual Line for Rotax Engine Type 915 i A / C24 Series, rev Rev. 3 (Ed.0 / May 01 2025), p. 05-20-00 pp. 2, 8-17, PDF p. 58, 64, 65, 66, 67, 68, 70, 71, 72, 73 | no | — |
| 34 | Airframe 25-hour inspection (first only) | Thing | once | 25 airframe hours | document: Maintenance Manual, rev 1.3, p. 66, PDF p. 82, 63 | no | — |
| 35 | Engine oil change after first 25 hours | engine | once | 25 engine hours | document: Maintenance Manual, rev 1.3, p. 66, PDF p. 82 | no | — |
| 36 | First 50 hours engine and propeller service | engine | once | 50 engine hours | document: Maintenance Manual, rev 1.3, p. 66, PDF p. 82 | no | — |

<details><summary>Rationale and description</summary>

**100-hour / annual condition inspection (airframe).** Sling recommends the airframe inspection every 100 hours or annually, whichever comes first, read with the Rotax and propeller checklists.

Complete the checklist in paragraph 2.5.1 of the maintenance manual. Covers battery, brake fluid, fuel filter and lines, propeller bolts, control surfaces, tyres, air ducting and parachute bay. The annual limit matches the 12-month condition inspection for an experimental aircraft (14 CFR Part 43 Appendix D). If the aircraft is used for hire, 14 CFR 91.409(b) also requires a 100-hour inspection.

**Engine 100-hour / 12-month inspection.** Rotax recommends inspecting the engine every 100 hours or 12 months, whichever comes first.

Tolerance is ±10 hr and ±2 months. The differential pressure, oil tank and crankcase pressure checks apply when leaded AVGAS is used for more than 30% of operation. Includes spark plug check, magnetic plug, oil filter inspection, exhaust, fuel system, ECU fault memory, wastegate and propeller shaft corrosion checks, plus an engine test run with LANE check.

**Engine oil and filter change.** Rotax and Sling recommend an oil and filter change every 100 hours or annually, whichever comes first.

Drain the oil, refill with about 3 litres, fit a new oil filter and clean the turbo oil sump screen. If operated predominantly on leaded AVGAS (more than 30% of engine time), change the oil every 25 hours and the filter every 50 hours. The starter pack suggests every 50 engine hours.

**Engine and propeller service (50 hours / annual).** Sling recommends performing all engine and propeller service requirements every 50 hours or annually, whichever comes first.

Perform all service requirements in the latest Rotax 915 iS manuals and in the propeller manufacturer's operator manual.

**Engine 50-hour inspection.** Rotax recommends but does not require a 50-hour engine inspection.

Checks the compression by the differential pressure method (required every 50 hours up to 400 hours only for engines with part no. 815253 rectangular ring, S/N 9122005 to 10004312). Oil filter cut-open and turbo sump screen inspection apply when leaded fuel is used more than 30% of the time.

**Engine 200-hour inspection (additional checks).** Rotax recommends a 100-hour check plus additional checks every 200 hours.

Differential pressure check, spark plug connector pull-off force (minimum 30 N), oil tank check and wiring harness inspection, together with the 100-hour check.

**Engine 600-hour inspection (additional checks).** Rotax recommends additional checks every 600 hours on top of the 100- and 200-hour checks.

Check ECU and fuse box mountings, gear set, overload clutches and intermediate shaft wear. The EMS and airframe circuit test applies to the 915 iSc C24 only.

**Replace spark plugs.** Rotax recommends replacing the spark plugs every 400 hours.

Replace every 400 hours; every 200 hours when leaded AVGAS is used for more than 30% of operation. Use genuine Rotax plugs.

**Replace fuel filter.** Rotax recommends replacing the genuine fuel filter every 200 hours or 2 years, whichever comes first.

Replace after the first 100 hours, then every 200 hours or at most 2 years. If a non-Rotax filter is used, follow the aircraft manufacturer's specification (SI-PAC-007).

**Lubricate wastegate lever.** Rotax recommends lubricating the wastegate lever every 100 hours.

Lubricate the wastegate lever every 100 hours (first at 25 hours).

**Engine cleaning.** Rotax recommends cleaning the engine every 100 hours.

Engine cleaning at 100 hours (first at 25 hours).

**Coat propeller shaft with corrosion inhibitor.** Rotax recommends coating the exposed propeller shaft with corrosion inhibitor every 100 hours.

Do not apply inhibitor to the propeller/flange contact surface. See MMH Chap. 72-10-00.

**Change torsion shaft.** Rotax recommends changing the torsion shaft every 600 hours.

Change the torsion shaft at 600 hours. See MMH Chap. 72-10-00.

**Engine general overhaul (TBO).** Rotax sets the 915 iS overhaul interval at 1200 hours or 15 years, whichever comes first.

Extension or exceeding of the TBO by 5% or 6 months (whichever comes first) is allowed. Overhaul per the current Overhaul Manual.

**Replace engine rubber hoses.** Rotax recommends replacing the engine's rubber hoses every 5 years, in addition to visual inspections.

Covers the cooling system hoses (genuine Rotax silicone hoses are on-condition), lubrication system hoses supplied with the engine, the air intake connecting hose and the turbocharger-to-airbox hose, plus fuel hoses and other rubber parts.

**Replace rubber plate under expansion tank.** Rotax recommends replacing the rubber plate under the expansion tank every 5 years.

Replace every 5 years.

**Replace fuel pressure regulator assembly.** Rotax recommends replacing the fuel pressure regulator every 5 years.

Replace only the pressure regulator, not the housing, every 5 years.

**Replace engine coolant.** Rotax and Sling recommend replacing the coolant per the coolant manufacturer's instructions, and at the latest at overhaul.

The documents give no fixed interval; the 2-year interval comes from general knowledge for conventional coolant. Flush the system if there are heavy deposits on the expansion tank or radiator cap, or if the coolant maker requires a change interval.

**Air filter remove and clean or replace.** Sling recommends cleaning or replacing the air filter every 100 hours or annually.

Clean more often in a dusty environment and replace if necessary.

**Lubrication of airframe points (100 hours / yearly).** Sling recommends lubricating the control system and gear points every 100 hours or yearly.

Lubricate with approved lubricants: rudder and pedal cable terminals and guides, elevator pushrod guide and torque tube bushes, trim tab hinge, aileron pushrod guide, flap torque tube bushes and actuator ends, pedal bushes, control sticks, nose gear support blocks and main wheel bearings. Canopy hinges and latches as needed.

**Lubricate throttle, choke and heater cables (300 hours).** Sling recommends lubricating the engine control cables every 300 hours.

Lubricate the engine throttle cable, engine choke cable and heater activation cables.

**Replace air ducting.** Sling recommends replacing all air ducting every 300 hours.

Replace CAT and SCAT tubing every 300 hours; inspect its condition at each 100-hour / annual inspection.

**2000-hour airframe service checklist.** Sling recommends a detailed structural service every 2000 hours, in conjunction with engine overhaul.

Per paragraph 2.5.2: remove the wings for spar inspection, replace the AN5-10A rear spar bolt with AN6-10A, replace main spar and main undercarriage attachment hardware, replace the elevator trim tab control horn, inspect rudder cables, stabiliser attachments, hinge brackets and bell cranks, and remove the main landing gear for crack checks. Done with the 100-hour checklist and the engine and propeller checklists.

**Compass swing.** Sling recommends verifying compass accuracy with a compass swing every 12 months.

Read with SA CATS 43.02.18.

**Mass and balance check.** Sling recommends a mass and balance check every 5 years.

Read with SA CATS 43.02.07.

**Altimeter & pitot-static system test.** The manual covers a pitot-static test; the starter pack's regulatory 24-month interval for IFR operations applies here.

Test for freedom from obstructions and leaks, test the warning unit and pitot heater, check altimeters, airspeed indicators and VSI for accuracy. 14 CFR 91.411 requires this within the preceding 24 calendar months for IFR. The Sling manual performs it at the annual inspection (every 1 year).

**Ballistic parachute rocket service.** Sling recommends servicing the ballistic parachute rocket every 6 years (Magnum 901) or 12 years (BRS).

Interval is 6 years for a Magnum 901 and 12 years for a BRS. The shorter 6-year interval is used because the installed unit is not recorded. Count from the last service date.

**Ballistic parachute canopy repack.** Sling recommends repacking the ballistic parachute canopy every 6 years.

Applies to both Magnum 901 and BRS systems.

**Muffler / heat exchanger disassemble and inspect.** Sling recommends disassembling and inspecting the muffler and heat exchanger assembly every 5 years.

See paragraph 7.1.1 for removal and disassembly instructions.

**Propeller 100-hour / annual inspection.** Airmaster recommends the propeller periodic inspection every 100 hours or annually.

Per ASI-7-1-1: inspect the spinner, blades, retention nuts and bearings, hub and pitch change mechanism, slipring and sensor-brush assembly. Also performed at the first 25, 50 and 100 hours. Can be done with the hub mounted on the engine flange.

**Replace carbon brushes in sensor-brush assembly.** Airmaster recommends replacing the carbon brushes at the prescribed service interval per ASI-7-1-2.

Interval is 300 hours for standard sliprings and 600 hours for mini sliprings; 300 hours is used here. Replace earlier if required.

**Lubricate propeller hub and blade assemblies.** Airmaster recommends re-greasing the hub and blade assemblies with the periodic inspection.

Wipe old grease from the hub bores and blade retention assemblies, then lubricate per ASI-4-5 (mobilgrease28 recommended).

**Engine 25-hour inspection (first only).** Rotax recommends a one-time inspection after the first 25 hours on new or overhauled engines.

Uses the checks of the 100-hour inspection marked in the 25 column, including spark plug check, magnetic plug, hoses, wiring, ECU faults, wastegate and a test run with LANE check. Oil change, cleaning and wastegate lubrication are separate items.

**Airframe 25-hour inspection (first only).** Sling recommends a one-time airframe inspection after the first 25 hours of operation.

Complete the checklist in paragraph 2.5 and rectify any shortcomings. Also perform the propeller and engine service requirements.

**Engine oil change after first 25 hours.** Sling recommends a first oil change after the initial 25 hours.

Refer to the latest Rotax 915 iS / 916 iS manuals.

**First 50 hours engine and propeller service.** Sling recommends a one-time engine and propeller service after the first 50 hours.

Perform all engine service requirements per the Rotax manuals and all propeller service requirements per the propeller manual.

</details>

- Not in the answer key: Fire extinguisher service
- Not in the answer key: Lubricate the wastegate lever
- Not in the answer key: Coat propeller shaft with corrosion inhibitor
- Not in the answer key: Engine cleaning
- Not in the answer key: Replace air intake hoses
- Not in the answer key: Replace rubber plate under expansion tank

## sling-tsi-three-manuals

**succeeded** · 37 suggestions · recall 94% (16/17) · intervals 90% · citations 97% · precision 81% · $0.757 · 191.9 s

Documents: Maintenance Manual rev 1.3 (maintenance manual) · Maintenance Manual Line for Rotax Engine Type 915 i A / C24 Series rev Rev. 3 (Ed.0 / May 01 2025) (maintenance manual) · PERIODIC (100-HR / ANNUAL) INSPECTION & SERVICE CHECKLIST rev Rev 1 (service instruction)

| # | Suggestion | Component | Schedule | First due | Source | Pre-selected | Matched expected |
|---|---|---|---|---|---|---|---|
| 1 | 100-hour / annual condition inspection (airframe) | Thing | every 100 airframe hours or every 12 months | last done 2026-06-10 | document: Maintenance Manual, rev 1.3, p. 67, PDF p. 83, 59, 63 | yes | airframe 100-hour / annual condition inspection |
| 2 | Engine 100-hour / 12-month inspection | engine | every 100 engine hours or every 12 months | last done 2026-06-10 | document: Maintenance Manual Line for Rotax Engine Type 915 i A / C24 Series, rev Rev. 3 (Ed.0 / May 01 2025), p. 05-20-00 pp. 2, 8-17, PDF p. 55, 58, 64, 65, 66, 67, 68, 69, 70, 71, 72, 73 | yes | — |
| 3 | Engine oil and filter change | engine | every 100 engine hours or every 12 months | last done 2026-06-10 | document: Maintenance Manual, rev 1.3, p. 67, PDF p. 83, 80 | yes | engine oil and filter change |
| 4 | Engine and propeller service (50 hours / annual) | engine | every 50 engine hours or every 12 months | — | document: Maintenance Manual, rev 1.3, p. 66, PDF p. 82 | yes | — |
| 5 | Engine 50-hour inspection | engine | every 50 engine hours | — | document: Maintenance Manual Line for Rotax Engine Type 915 i A / C24 Series, rev Rev. 3 (Ed.0 / May 01 2025), p. 05-20-00 pp. 2, 8, 9, 11, 13, PDF p. 58, 64, 65, 67, 69 | yes | — |
| 6 | Engine 200-hour inspection (additional checks) | engine | every 200 engine hours | — | document: Maintenance Manual Line for Rotax Engine Type 915 i A / C24 Series, rev Rev. 3 (Ed.0 / May 01 2025), p. 05-20-00 pp. 2, 8, 9, 12, 13, PDF p. 58, 64, 65, 68, 69 | yes | — |
| 7 | Engine 600-hour inspection (additional checks) | engine | every 600 engine hours | — | document: Maintenance Manual Line for Rotax Engine Type 915 i A / C24 Series, rev Rev. 3 (Ed.0 / May 01 2025), p. 05-20-00 pp. 2, 8, 14-16, PDF p. 58, 64, 70, 71, 72 | yes | — |
| 8 | Replace spark plugs | engine | every 400 engine hours | — | document: Maintenance Manual Line for Rotax Engine Type 915 i A / C24 Series, rev Rev. 3 (Ed.0 / May 01 2025), p. 9, PDF p. 65 | yes | spark plug replacement |
| 9 | Replace fuel filter | engine | every 200 engine hours or every 2 years | — | document: Maintenance Manual Line for Rotax Engine Type 915 i A / C24 Series, rev Rev. 3 (Ed.0 / May 01 2025), p. 14, PDF p. 70 | yes | engine fuel filter replacement |
| 10 | Lubricate wastegate lever | engine | every 100 engine hours | — | document: Maintenance Manual Line for Rotax Engine Type 915 i A / C24 Series, rev Rev. 3 (Ed.0 / May 01 2025), p. 15, PDF p. 71 | yes | — |
| 11 | Engine cleaning | engine | every 100 engine hours | — | document: Maintenance Manual Line for Rotax Engine Type 915 i A / C24 Series, rev Rev. 3 (Ed.0 / May 01 2025), p. 16, PDF p. 72 | yes | — |
| 12 | Coat propeller shaft with corrosion inhibitor | engine | every 100 engine hours | — | document: Maintenance Manual Line for Rotax Engine Type 915 i A / C24 Series, rev Rev. 3 (Ed.0 / May 01 2025), p. 15, PDF p. 71 | yes | — |
| 13 | Change torsion shaft | engine | every 600 engine hours | — | document: Maintenance Manual Line for Rotax Engine Type 915 i A / C24 Series, rev Rev. 3 (Ed.0 / May 01 2025), p. 16, PDF p. 72 | yes | torsion shaft replacement |
| 14 | Engine general overhaul (TBO) | engine | every 1,200 engine hours or every 15 years | — | document: Maintenance Manual Line for Rotax Engine Type 915 i A / C24 Series, rev Rev. 3 (Ed.0 / May 01 2025), p. 4, PDF p. 52 | yes | engine overhaul (TBO) |
| 15 | Replace engine rubber hoses | engine | every 5 years | — | document: Maintenance Manual Line for Rotax Engine Type 915 i A / C24 Series, rev Rev. 3 (Ed.0 / May 01 2025), p. 6, PDF p. 54 | yes | — |
| 16 | Replace rubber plate under expansion tank | engine | every 5 years | — | document: Maintenance Manual Line for Rotax Engine Type 915 i A / C24 Series, rev Rev. 3 (Ed.0 / May 01 2025), p. 6, PDF p. 54 | yes | — |
| 17 | Replace fuel pressure regulator assembly | engine | every 5 years | — | document: Maintenance Manual Line for Rotax Engine Type 915 i A / C24 Series, rev Rev. 3 (Ed.0 / May 01 2025), p. 6, PDF p. 54 | yes | fuel pressure regulator replacement |
| 18 | Replace engine coolant | engine | on condition | — | document: Maintenance Manual, rev 1.3, p. 65, PDF p. 81 | yes | — |
| 19 | Air filter remove and clean or replace | engine | every 100 engine hours or every 12 months | — | document: Maintenance Manual, rev 1.3, p. 67, PDF p. 83, 80 | yes | — |
| 20 | Lubrication of airframe points (100 hours / yearly) | Thing | every 100 airframe hours or every 12 months | — | document: Maintenance Manual, rev 1.3, p. 63, PDF p. 79 | yes | — |
| 21 | Lubricate throttle, choke and heater cables (300 hours) | Thing | every 300 airframe hours | — | document: Maintenance Manual, rev 1.3, p. 63, PDF p. 79 | yes | — |
| 22 | Replace air ducting | Thing | every 300 airframe hours | — | document: Maintenance Manual, rev 1.3, p. 67, PDF p. 83 | yes | — |
| 23 | 2000-hour airframe service checklist | Thing | every 2,000 airframe hours | — | document: Maintenance Manual, rev 1.3, p. 56, PDF p. 72, 83 | yes | — |
| 24 | Compass swing | Thing | every 12 months | — | document: Maintenance Manual, rev 1.3, p. 52, PDF p. 68 | yes | compass swing |
| 25 | Mass and balance check | Thing | every 5 years | — | document: Maintenance Manual, rev 1.3, p. 52, PDF p. 68 | yes | weight and balance check |
| 26 | Altimeter & pitot-static system test | Thing | every 24 months | — | document: Maintenance Manual, rev 1.3, p. 53, PDF p. 69 | yes | pitot-static system test |
| 27 | Ballistic parachute rocket service | Thing | every 6 years | — | document: Maintenance Manual, rev 1.3, p. 65, PDF p. 81 | yes | ballistic parachute rocket service |
| 28 | Ballistic parachute canopy repack | Thing | every 6 years | — | document: Maintenance Manual, rev 1.3, p. 65, PDF p. 81 | yes | ballistic parachute repack |
| 29 | Muffler / heat exchanger disassemble and inspect | engine | every 5 years | — | document: Maintenance Manual, rev 1.3, p. 65, PDF p. 81 | yes | muffler / heat exchanger inspection |
| 30 | Propeller 100-hour / annual inspection | propeller | every 100 prop hours or every 12 months | — | document: PERIODIC (100-HR / ANNUAL) INSPECTION & SERVICE CHECKLIST, rev Rev 1, p. 2, PDF p. 2, 5, 6, 7, 8, 9 | yes | propeller 100-hour / annual inspection |
| 31 | Replace carbon brushes in sensor-brush assembly | propeller | every 300 prop hours | — | document: PERIODIC (100-HR / ANNUAL) INSPECTION & SERVICE CHECKLIST, rev Rev 1, p. 9, PDF p. 9, 10 | yes | propeller carbon brush replacement |
| 32 | Lubricate propeller hub and blade assemblies | propeller | every 100 prop hours or every 12 months | — | document: PERIODIC (100-HR / ANNUAL) INSPECTION & SERVICE CHECKLIST, rev Rev 1, p. 10, PDF p. 10 | yes | propeller hub regrease |
| 33 | Engine 25-hour inspection (first only) | engine | once | 25 engine hours | document: Maintenance Manual Line for Rotax Engine Type 915 i A / C24 Series, rev Rev. 3 (Ed.0 / May 01 2025), p. 05-20-00 pp. 2, 8-17, PDF p. 58, 64, 65, 66, 67, 68, 70, 71, 72, 73 | no | — |
| 34 | Airframe 25-hour inspection (first only) | Thing | once | 25 airframe hours | document: Maintenance Manual, rev 1.3, p. 66, PDF p. 82, 63 | no | — |
| 35 | Engine oil change after first 25 hours | engine | once | 25 engine hours | document: Maintenance Manual, rev 1.3, p. 66, PDF p. 82 | no | — |
| 36 | First 50 hours engine and propeller service | engine | once | 50 engine hours | document: Maintenance Manual, rev 1.3, p. 66, PDF p. 82 | no | — |

<details><summary>Rationale and description</summary>

**100-hour / annual condition inspection (airframe).** Sling recommends the airframe inspection every 100 hours or annually, whichever comes first, read with the Rotax and propeller checklists.

Complete the checklist in paragraph 2.5.1 of the maintenance manual. Covers battery, brake fluid, fuel filter and lines, propeller bolts, control surfaces, tyres, air ducting and parachute bay. The annual limit matches the 12-month condition inspection for an experimental aircraft (14 CFR Part 43 Appendix D). If the aircraft is used for hire, 14 CFR 91.409(b) also requires a 100-hour inspection.

**Engine 100-hour / 12-month inspection.** Rotax recommends inspecting the engine every 100 hours or 12 months, whichever comes first.

Tolerance is ±10 hr and ±2 months. The differential pressure, oil tank and crankcase pressure checks apply when leaded AVGAS is used for more than 30% of operation. Includes spark plug check, magnetic plug, oil filter inspection, exhaust, fuel system, ECU fault memory, wastegate and propeller shaft corrosion checks, plus an engine test run with LANE check.

**Engine oil and filter change.** Rotax and Sling recommend an oil and filter change every 100 hours or annually, whichever comes first.

Drain the oil, refill with about 3 litres, fit a new oil filter and clean the turbo oil sump screen. If operated predominantly on leaded AVGAS (more than 30% of engine time), change the oil every 25 hours and the filter every 50 hours. The starter pack suggests every 50 engine hours.

**Engine and propeller service (50 hours / annual).** Sling recommends performing all engine and propeller service requirements every 50 hours or annually, whichever comes first.

Perform all service requirements in the latest Rotax 915 iS manuals and in the propeller manufacturer's operator manual.

**Engine 50-hour inspection.** Rotax recommends but does not require a 50-hour engine inspection.

Checks the compression by the differential pressure method (required every 50 hours up to 400 hours only for engines with part no. 815253 rectangular ring, S/N 9122005 to 10004312). Oil filter cut-open and turbo sump screen inspection apply when leaded fuel is used more than 30% of the time.

**Engine 200-hour inspection (additional checks).** Rotax recommends a 100-hour check plus additional checks every 200 hours.

Differential pressure check, spark plug connector pull-off force (minimum 30 N), oil tank check and wiring harness inspection, together with the 100-hour check.

**Engine 600-hour inspection (additional checks).** Rotax recommends additional checks every 600 hours on top of the 100- and 200-hour checks.

Check ECU and fuse box mountings, gear set, overload clutches and intermediate shaft wear. The EMS and airframe circuit test applies to the 915 iSc C24 only.

**Replace spark plugs.** Rotax recommends replacing the spark plugs every 400 hours.

Replace every 400 hours; every 200 hours when leaded AVGAS is used for more than 30% of operation. Use genuine Rotax plugs.

**Replace fuel filter.** Rotax recommends replacing the genuine fuel filter every 200 hours or 2 years, whichever comes first.

Replace after the first 100 hours, then every 200 hours or at most 2 years. If a non-Rotax filter is used, follow the aircraft manufacturer's specification (SI-PAC-007).

**Lubricate wastegate lever.** Rotax recommends lubricating the wastegate lever every 100 hours.

Lubricate the wastegate lever every 100 hours (first at 25 hours).

**Engine cleaning.** Rotax recommends cleaning the engine every 100 hours.

Engine cleaning at 100 hours (first at 25 hours).

**Coat propeller shaft with corrosion inhibitor.** Rotax recommends coating the exposed propeller shaft with corrosion inhibitor every 100 hours.

Do not apply inhibitor to the propeller/flange contact surface. See MMH Chap. 72-10-00.

**Change torsion shaft.** Rotax recommends changing the torsion shaft every 600 hours.

Change the torsion shaft at 600 hours. See MMH Chap. 72-10-00.

**Engine general overhaul (TBO).** Rotax sets the 915 iS overhaul interval at 1200 hours or 15 years, whichever comes first.

Extension or exceeding of the TBO by 5% or 6 months (whichever comes first) is allowed. Overhaul per the current Overhaul Manual.

**Replace engine rubber hoses.** Rotax recommends replacing the engine's rubber hoses every 5 years, in addition to visual inspections.

Covers the cooling system hoses (genuine Rotax silicone hoses are on-condition), lubrication system hoses supplied with the engine, the air intake connecting hose and the turbocharger-to-airbox hose, plus fuel hoses and other rubber parts.

**Replace rubber plate under expansion tank.** Rotax recommends replacing the rubber plate under the expansion tank every 5 years.

Replace every 5 years.

**Replace fuel pressure regulator assembly.** Rotax recommends replacing the fuel pressure regulator every 5 years.

Replace only the pressure regulator, not the housing, every 5 years.

**Replace engine coolant.** Rotax and Sling recommend replacing the coolant per the coolant manufacturer's instructions, and at the latest at overhaul.

The documents give no fixed interval; the 2-year interval comes from general knowledge for conventional coolant. Flush the system if there are heavy deposits on the expansion tank or radiator cap, or if the coolant maker requires a change interval.

**Air filter remove and clean or replace.** Sling recommends cleaning or replacing the air filter every 100 hours or annually.

Clean more often in a dusty environment and replace if necessary.

**Lubrication of airframe points (100 hours / yearly).** Sling recommends lubricating the control system and gear points every 100 hours or yearly.

Lubricate with approved lubricants: rudder and pedal cable terminals and guides, elevator pushrod guide and torque tube bushes, trim tab hinge, aileron pushrod guide, flap torque tube bushes and actuator ends, pedal bushes, control sticks, nose gear support blocks and main wheel bearings. Canopy hinges and latches as needed.

**Lubricate throttle, choke and heater cables (300 hours).** Sling recommends lubricating the engine control cables every 300 hours.

Lubricate the engine throttle cable, engine choke cable and heater activation cables.

**Replace air ducting.** Sling recommends replacing all air ducting every 300 hours.

Replace CAT and SCAT tubing every 300 hours; inspect its condition at each 100-hour / annual inspection.

**2000-hour airframe service checklist.** Sling recommends a detailed structural service every 2000 hours, in conjunction with engine overhaul.

Per paragraph 2.5.2: remove the wings for spar inspection, replace the AN5-10A rear spar bolt with AN6-10A, replace main spar and main undercarriage attachment hardware, replace the elevator trim tab control horn, inspect rudder cables, stabiliser attachments, hinge brackets and bell cranks, and remove the main landing gear for crack checks. Done with the 100-hour checklist and the engine and propeller checklists.

**Compass swing.** Sling recommends verifying compass accuracy with a compass swing every 12 months.

Read with SA CATS 43.02.18.

**Mass and balance check.** Sling recommends a mass and balance check every 5 years.

Read with SA CATS 43.02.07.

**Altimeter & pitot-static system test.** The manual covers a pitot-static test; the starter pack's regulatory 24-month interval for IFR operations applies here.

Test for freedom from obstructions and leaks, test the warning unit and pitot heater, check altimeters, airspeed indicators and VSI for accuracy. 14 CFR 91.411 requires this within the preceding 24 calendar months for IFR. The Sling manual performs it at the annual inspection (every 1 year).

**Ballistic parachute rocket service.** Sling recommends servicing the ballistic parachute rocket every 6 years (Magnum 901) or 12 years (BRS).

Interval is 6 years for a Magnum 901 and 12 years for a BRS. The shorter 6-year interval is used because the installed unit is not recorded. Count from the last service date.

**Ballistic parachute canopy repack.** Sling recommends repacking the ballistic parachute canopy every 6 years.

Applies to both Magnum 901 and BRS systems.

**Muffler / heat exchanger disassemble and inspect.** Sling recommends disassembling and inspecting the muffler and heat exchanger assembly every 5 years.

See paragraph 7.1.1 for removal and disassembly instructions.

**Propeller 100-hour / annual inspection.** Airmaster recommends the propeller periodic inspection every 100 hours or annually.

Per ASI-7-1-1: inspect the spinner, blades, retention nuts and bearings, hub and pitch change mechanism, slipring and sensor-brush assembly. Also performed at the first 25, 50 and 100 hours. Can be done with the hub mounted on the engine flange.

**Replace carbon brushes in sensor-brush assembly.** Airmaster recommends replacing the carbon brushes at the prescribed service interval per ASI-7-1-2.

Interval is 300 hours for standard sliprings and 600 hours for mini sliprings; 300 hours is used here. Replace earlier if required.

**Lubricate propeller hub and blade assemblies.** Airmaster recommends re-greasing the hub and blade assemblies with the periodic inspection.

Wipe old grease from the hub bores and blade retention assemblies, then lubricate per ASI-4-5 (mobilgrease28 recommended).

**Engine 25-hour inspection (first only).** Rotax recommends a one-time inspection after the first 25 hours on new or overhauled engines.

Uses the checks of the 100-hour inspection marked in the 25 column, including spark plug check, magnetic plug, hoses, wiring, ECU faults, wastegate and a test run with LANE check. Oil change, cleaning and wastegate lubrication are separate items.

**Airframe 25-hour inspection (first only).** Sling recommends a one-time airframe inspection after the first 25 hours of operation.

Complete the checklist in paragraph 2.5 and rectify any shortcomings. Also perform the propeller and engine service requirements.

**Engine oil change after first 25 hours.** Sling recommends a first oil change after the initial 25 hours.

Refer to the latest Rotax 915 iS / 916 iS manuals.

**First 50 hours engine and propeller service.** Sling recommends a one-time engine and propeller service after the first 50 hours.

Perform all engine service requirements per the Rotax manuals and all propeller service requirements per the propeller manual.

</details>

- Missed: throttle and heater cable lubrication
- Duplicate of a matched task: Replace rubber hoses of the lubrication system
- Duplicate of a matched task: Lubrication - 300 hour points
- Not in the answer key: Lubricate the wastegate lever
- Not in the answer key: Coat propeller shaft with corrosion inhibitor
- Not in the answer key: Replace connecting hose of the air intake system
- Not in the answer key: Replace rubber plate under expansion tank
- Not in the answer key: Replace air intake hose (turbocharger to airbox)

## toyota-sienna-hybrid-2022-guide

**succeeded** · 11 suggestions · recall 89% (8/9) · intervals 100% · citations 90% · precision 100% · $0.294 · 93.3 s

Documents: 2022 Warranty & Maintenance Guide (other)

| # | Suggestion | Component | Schedule | First due | Source | Pre-selected | Matched expected |
|---|---|---|---|---|---|---|---|
| 1 | 5,000-mile / 6-month inspection | Thing | every 5,000 odometer or every 6 months | — | document: 2022 Warranty & Maintenance Guide, p. 38, PDF p. 38, 40 | yes | 5,000-mile scheduled maintenance |
| 2 | Rotate tires | Thing | every 5,000 odometer or every 6 months | last done 2026-03-14 | document: 2022 Warranty & Maintenance Guide, p. 38, PDF p. 40, 59 | yes | rotate tires |
| 3 | Replace engine oil and oil filter | Thing | every 5,000 odometer or every 6 months | last done 2026-03-14 | document: 2022 Warranty & Maintenance Guide, p. 56, PDF p. 40, 58 | yes | replace engine oil and oil filter |
| 4 | 30,000-mile / 36-month inspection | Thing | every 30,000 odometer or every 36 months | — | document: 2022 Warranty & Maintenance Guide, p. 39, PDF p. 41, 45, 49, 53 | yes | 30,000-mile inspection (wrong page) |
| 5 | Replace cabin air filter | Thing | every 30,000 odometer or every 36 months | — | document: 2022 Warranty & Maintenance Guide, p. 41, PDF p. 43, 56 | yes | replace cabin air filter |
| 6 | Replace engine air filter | Thing | every 30,000 odometer or every 36 months | — | document: 2022 Warranty & Maintenance Guide, p. 41, PDF p. 43, 57 | yes | replace engine air filter |
| 7 | Clean HV battery cooling intake filter | Thing | every 20,000 odometer or every 24 months | — | document: 2022 Warranty & Maintenance Guide, p. 40, PDF p. 42, 41, 58 | yes | clean HV battery cooling intake filter |
| 8 | Brake fluid flush | Thing | every 24 months | — | common practice | yes | — |
| 9 | Replace engine coolant | Thing | every 50,000 odometer or every 60 months | — | document: 2022 Warranty & Maintenance Guide, p. 50, PDF p. 52, 53, 57 | yes | replace engine coolant |
| 10 | Replace inverter coolant | Thing | every 50,000 odometer or every 60 months | — | document: 2022 Warranty & Maintenance Guide, p. 51, PDF p. 53, 57 | yes | replace inverter coolant |
| 11 | Replace spark plugs | Thing | every 120,000 odometer or every 144 months | — | document: 2022 Warranty & Maintenance Guide, p. 53, PDF p. 55, 59 | yes | replace spark plugs |

<details><summary>Rationale and description</summary>

**5,000-mile / 6-month inspection.** Toyota recommends this basic inspection every 5,000 miles or six months, whichever comes first.

Check driver's floor mat installation, inspect and adjust all fluid levels, inspect wiper blades, visually inspect brake linings/drums and pads/discs, and inspect the HV battery cooling intake filter (clean if dirty). Reset the oil replacement reminder after maintenance.

**Rotate tires.** Toyota recommends rotating the tires at every 5,000-mile / 6-month service step.

Rotate tires per the Owner's Manual, and check for damage and uneven wear when doing so. The manual lists this every 5,000 miles or 6 months; the starter pack suggests 6,000 miles.

**Replace engine oil and oil filter.** Toyota recommends the shorter 5,000-mile / 6-month oil interval unless you can confirm genuine 0W-16 oil is used every time.

Drain and refill the engine oil and replace the filter. If Genuine Toyota 0W-16 is used, the interval extends to 10,000 miles or 12 months. If 0W-20 mineral oil is used instead, or you mostly drive in Special Operating Conditions, replace at 5,000 miles or 6 months. Reset the oil replacement reminder afterward.

**30,000-mile / 36-month inspection.** Toyota recommends a comprehensive chassis, brake, steering and cooling-system inspection every 30,000 miles or 36 months.

The guide lists a 15,000/45,000/75,000/105,000-mile step (ball joints, brake lines and hoses, drive shaft boots, engine/inverter coolant, exhaust, radiator and condenser, steering gear and linkage, cabin air filter inspection) and a 30,000/60,000/90,000/120,000-mile step (the same items plus brake pad and disc thickness measurement, transmission leakage, fuel lines and tank cap gasket, and rear differential oil on AWD). Each step recurs every 30,000 miles / 36 months, so together they fall about every 15,000 miles. If 0W-16 oil was not used at the last oil change, replace the oil and filter at the 15,000-mile step.

**Replace cabin air filter.** Toyota recommends replacing the cabin air filter every 30,000 miles or 36 months.

Replace at the specified interval. Driving in heavy traffic, on dirt roads or in urban, desert or dusty areas may shorten the filter's life, so replace it more often there. The starter pack suggests 12 months, and a general source suggests 20,000 miles / 24 months.

**Replace engine air filter.** Toyota recommends replacing the engine air filter every 30,000 miles or 36 months.

Replace at the specified interval. When inspecting, check for damage, excessive wear and oiliness, and replace if necessary. Replace sooner on dusty roads. The starter pack suggests 12 months.

**Clean HV battery cooling intake filter.** Toyota recommends cleaning the hybrid battery cooling intake filter every 20,000 miles or 24 months.

See "Hybrid Battery (Traction Battery) Air Intake Vent" in the "Maintenance and Care" section of the Owner's Manual for the cleaning procedure. It is also visually checked at each 5,000-mile inspection.

**Brake fluid flush.** Brake fluid absorbs water over time, so a flush about every two years is a common recommendation.

Drain and flush the old brake fluid and refill with fresh DOT 3 or DOT 4 fluid as specified. A general source suggests 30,000 miles / 36 months; the 24-month starter-pack interval is used here.

**Replace engine coolant.** Toyota recommends replacing the engine coolant every 50,000 miles or 60 months after the first change.

The first replacement is at 100,000 miles / 120 months, then every 50,000 miles / 60 months. Drain the cooling system and refill; a qualified technician should do this. Intervals assume Genuine Toyota Super Long-Life Coolant or a similar non-silicate, non-amine, non-borate ethylene-glycol coolant; other coolants may need different intervals.

**Replace inverter coolant.** Toyota recommends replacing the inverter coolant every 50,000 miles or 60 months after the first change.

The first replacement is at 150,000 miles / 180 months, then every 50,000 miles / 60 months. Intervals assume Genuine Toyota Super Long-Life Coolant or similar; other ethylene-glycol coolants may need different intervals.

**Replace spark plugs.** Toyota recommends replacing the spark plugs every 120,000 miles or 144 months.

Install new plugs of the same type as originally equipped (iridium). Spark plug maintenance is required under the terms of the Emissions Control Warranty. A qualified technician should do this, and the ignition coils and boots can be checked at the same time.

</details>

- Missed: 15,000-mile inspection

## toyota-sienna-hybrid-2022-guide

**succeeded** · 10 suggestions · recall 89% (8/9) · intervals 100% · citations 90% · precision 100% · $0.313 · 105.8 s

Documents: 2022 Warranty & Maintenance Guide (other)

| # | Suggestion | Component | Schedule | First due | Source | Pre-selected | Matched expected |
|---|---|---|---|---|---|---|---|
| 1 | 5,000-mile / 6-month inspection | Thing | every 5,000 odometer or every 6 months | — | document: 2022 Warranty & Maintenance Guide, p. 38, PDF p. 38, 40 | yes | 5,000-mile scheduled maintenance |
| 2 | Rotate tires | Thing | every 5,000 odometer or every 6 months | last done 2026-03-14 | document: 2022 Warranty & Maintenance Guide, p. 38, PDF p. 40, 59 | yes | rotate tires |
| 3 | Replace engine oil and oil filter | Thing | every 5,000 odometer or every 6 months | last done 2026-03-14 | document: 2022 Warranty & Maintenance Guide, p. 56, PDF p. 40, 58 | yes | replace engine oil and oil filter |
| 4 | 30,000-mile / 36-month inspection | Thing | every 30,000 odometer or every 36 months | — | document: 2022 Warranty & Maintenance Guide, p. 39, PDF p. 41, 45, 49, 53 | yes | 30,000-mile inspection (wrong page) |
| 5 | Replace cabin air filter | Thing | every 30,000 odometer or every 36 months | — | document: 2022 Warranty & Maintenance Guide, p. 41, PDF p. 43, 56 | yes | replace cabin air filter |
| 6 | Replace engine air filter | Thing | every 30,000 odometer or every 36 months | — | document: 2022 Warranty & Maintenance Guide, p. 41, PDF p. 43, 57 | yes | replace engine air filter |
| 7 | Clean HV battery cooling intake filter | Thing | every 20,000 odometer or every 24 months | — | document: 2022 Warranty & Maintenance Guide, p. 40, PDF p. 42, 41, 58 | yes | clean HV battery cooling intake filter |
| 8 | Brake fluid flush | Thing | every 24 months | — | common practice | yes | — |
| 9 | Replace engine coolant | Thing | every 50,000 odometer or every 60 months | — | document: 2022 Warranty & Maintenance Guide, p. 50, PDF p. 52, 53, 57 | yes | replace engine coolant |
| 10 | Replace inverter coolant | Thing | every 50,000 odometer or every 60 months | — | document: 2022 Warranty & Maintenance Guide, p. 51, PDF p. 53, 57 | yes | replace inverter coolant |
| 11 | Replace spark plugs | Thing | every 120,000 odometer or every 144 months | — | document: 2022 Warranty & Maintenance Guide, p. 53, PDF p. 55, 59 | yes | replace spark plugs |

<details><summary>Rationale and description</summary>

**5,000-mile / 6-month inspection.** Toyota recommends this basic inspection every 5,000 miles or six months, whichever comes first.

Check driver's floor mat installation, inspect and adjust all fluid levels, inspect wiper blades, visually inspect brake linings/drums and pads/discs, and inspect the HV battery cooling intake filter (clean if dirty). Reset the oil replacement reminder after maintenance.

**Rotate tires.** Toyota recommends rotating the tires at every 5,000-mile / 6-month service step.

Rotate tires per the Owner's Manual, and check for damage and uneven wear when doing so. The manual lists this every 5,000 miles or 6 months; the starter pack suggests 6,000 miles.

**Replace engine oil and oil filter.** Toyota recommends the shorter 5,000-mile / 6-month oil interval unless you can confirm genuine 0W-16 oil is used every time.

Drain and refill the engine oil and replace the filter. If Genuine Toyota 0W-16 is used, the interval extends to 10,000 miles or 12 months. If 0W-20 mineral oil is used instead, or you mostly drive in Special Operating Conditions, replace at 5,000 miles or 6 months. Reset the oil replacement reminder afterward.

**30,000-mile / 36-month inspection.** Toyota recommends a comprehensive chassis, brake, steering and cooling-system inspection every 30,000 miles or 36 months.

The guide lists a 15,000/45,000/75,000/105,000-mile step (ball joints, brake lines and hoses, drive shaft boots, engine/inverter coolant, exhaust, radiator and condenser, steering gear and linkage, cabin air filter inspection) and a 30,000/60,000/90,000/120,000-mile step (the same items plus brake pad and disc thickness measurement, transmission leakage, fuel lines and tank cap gasket, and rear differential oil on AWD). Each step recurs every 30,000 miles / 36 months, so together they fall about every 15,000 miles. If 0W-16 oil was not used at the last oil change, replace the oil and filter at the 15,000-mile step.

**Replace cabin air filter.** Toyota recommends replacing the cabin air filter every 30,000 miles or 36 months.

Replace at the specified interval. Driving in heavy traffic, on dirt roads or in urban, desert or dusty areas may shorten the filter's life, so replace it more often there. The starter pack suggests 12 months, and a general source suggests 20,000 miles / 24 months.

**Replace engine air filter.** Toyota recommends replacing the engine air filter every 30,000 miles or 36 months.

Replace at the specified interval. When inspecting, check for damage, excessive wear and oiliness, and replace if necessary. Replace sooner on dusty roads. The starter pack suggests 12 months.

**Clean HV battery cooling intake filter.** Toyota recommends cleaning the hybrid battery cooling intake filter every 20,000 miles or 24 months.

See "Hybrid Battery (Traction Battery) Air Intake Vent" in the "Maintenance and Care" section of the Owner's Manual for the cleaning procedure. It is also visually checked at each 5,000-mile inspection.

**Brake fluid flush.** Brake fluid absorbs water over time, so a flush about every two years is a common recommendation.

Drain and flush the old brake fluid and refill with fresh DOT 3 or DOT 4 fluid as specified. A general source suggests 30,000 miles / 36 months; the 24-month starter-pack interval is used here.

**Replace engine coolant.** Toyota recommends replacing the engine coolant every 50,000 miles or 60 months after the first change.

The first replacement is at 100,000 miles / 120 months, then every 50,000 miles / 60 months. Drain the cooling system and refill; a qualified technician should do this. Intervals assume Genuine Toyota Super Long-Life Coolant or a similar non-silicate, non-amine, non-borate ethylene-glycol coolant; other coolants may need different intervals.

**Replace inverter coolant.** Toyota recommends replacing the inverter coolant every 50,000 miles or 60 months after the first change.

The first replacement is at 150,000 miles / 180 months, then every 50,000 miles / 60 months. Intervals assume Genuine Toyota Super Long-Life Coolant or similar; other ethylene-glycol coolants may need different intervals.

**Replace spark plugs.** Toyota recommends replacing the spark plugs every 120,000 miles or 144 months.

Install new plugs of the same type as originally equipped (iridium). Spark plug maintenance is required under the terms of the Emissions Control Warranty. A qualified technician should do this, and the ignition coils and boots can be checked at the same time.

</details>

- Missed: 15,000-mile inspection

## toyota-sienna-hybrid-2022-guide

**succeeded** · 11 suggestions · recall 100% (9/9) · intervals 100% · citations 100% · precision 100% · $0.289 · 88.9 s

Documents: 2022 Warranty & Maintenance Guide (other)

| # | Suggestion | Component | Schedule | First due | Source | Pre-selected | Matched expected |
|---|---|---|---|---|---|---|---|
| 1 | 5,000-mile / 6-month inspection | Thing | every 5,000 odometer or every 6 months | — | document: 2022 Warranty & Maintenance Guide, p. 38, PDF p. 38, 40 | yes | 5,000-mile scheduled maintenance |
| 2 | Rotate tires | Thing | every 5,000 odometer or every 6 months | last done 2026-03-14 | document: 2022 Warranty & Maintenance Guide, p. 38, PDF p. 40, 59 | yes | rotate tires |
| 3 | Replace engine oil and oil filter | Thing | every 5,000 odometer or every 6 months | last done 2026-03-14 | document: 2022 Warranty & Maintenance Guide, p. 56, PDF p. 40, 58 | yes | replace engine oil and oil filter |
| 4 | 30,000-mile / 36-month inspection | Thing | every 30,000 odometer or every 36 months | — | document: 2022 Warranty & Maintenance Guide, p. 39, PDF p. 41, 45, 49, 53 | yes | 30,000-mile inspection |
| 5 | Replace cabin air filter | Thing | every 30,000 odometer or every 36 months | — | document: 2022 Warranty & Maintenance Guide, p. 41, PDF p. 43, 56 | yes | replace cabin air filter |
| 6 | Replace engine air filter | Thing | every 30,000 odometer or every 36 months | — | document: 2022 Warranty & Maintenance Guide, p. 41, PDF p. 43, 57 | yes | replace engine air filter |
| 7 | Clean HV battery cooling intake filter | Thing | every 20,000 odometer or every 24 months | — | document: 2022 Warranty & Maintenance Guide, p. 40, PDF p. 42, 41, 58 | yes | clean HV battery cooling intake filter |
| 8 | Brake fluid flush | Thing | every 24 months | — | common practice | yes | — |
| 9 | Replace engine coolant | Thing | every 50,000 odometer or every 60 months | — | document: 2022 Warranty & Maintenance Guide, p. 50, PDF p. 52, 53, 57 | yes | replace engine coolant |
| 10 | Replace inverter coolant | Thing | every 50,000 odometer or every 60 months | — | document: 2022 Warranty & Maintenance Guide, p. 51, PDF p. 53, 57 | yes | replace inverter coolant |
| 11 | Replace spark plugs | Thing | every 120,000 odometer or every 144 months | — | document: 2022 Warranty & Maintenance Guide, p. 53, PDF p. 55, 59 | yes | replace spark plugs |

<details><summary>Rationale and description</summary>

**5,000-mile / 6-month inspection.** Toyota recommends this basic inspection every 5,000 miles or six months, whichever comes first.

Check driver's floor mat installation, inspect and adjust all fluid levels, inspect wiper blades, visually inspect brake linings/drums and pads/discs, and inspect the HV battery cooling intake filter (clean if dirty). Reset the oil replacement reminder after maintenance.

**Rotate tires.** Toyota recommends rotating the tires at every 5,000-mile / 6-month service step.

Rotate tires per the Owner's Manual, and check for damage and uneven wear when doing so. The manual lists this every 5,000 miles or 6 months; the starter pack suggests 6,000 miles.

**Replace engine oil and oil filter.** Toyota recommends the shorter 5,000-mile / 6-month oil interval unless you can confirm genuine 0W-16 oil is used every time.

Drain and refill the engine oil and replace the filter. If Genuine Toyota 0W-16 is used, the interval extends to 10,000 miles or 12 months. If 0W-20 mineral oil is used instead, or you mostly drive in Special Operating Conditions, replace at 5,000 miles or 6 months. Reset the oil replacement reminder afterward.

**30,000-mile / 36-month inspection.** Toyota recommends a comprehensive chassis, brake, steering and cooling-system inspection every 30,000 miles or 36 months.

The guide lists a 15,000/45,000/75,000/105,000-mile step (ball joints, brake lines and hoses, drive shaft boots, engine/inverter coolant, exhaust, radiator and condenser, steering gear and linkage, cabin air filter inspection) and a 30,000/60,000/90,000/120,000-mile step (the same items plus brake pad and disc thickness measurement, transmission leakage, fuel lines and tank cap gasket, and rear differential oil on AWD). Each step recurs every 30,000 miles / 36 months, so together they fall about every 15,000 miles. If 0W-16 oil was not used at the last oil change, replace the oil and filter at the 15,000-mile step.

**Replace cabin air filter.** Toyota recommends replacing the cabin air filter every 30,000 miles or 36 months.

Replace at the specified interval. Driving in heavy traffic, on dirt roads or in urban, desert or dusty areas may shorten the filter's life, so replace it more often there. The starter pack suggests 12 months, and a general source suggests 20,000 miles / 24 months.

**Replace engine air filter.** Toyota recommends replacing the engine air filter every 30,000 miles or 36 months.

Replace at the specified interval. When inspecting, check for damage, excessive wear and oiliness, and replace if necessary. Replace sooner on dusty roads. The starter pack suggests 12 months.

**Clean HV battery cooling intake filter.** Toyota recommends cleaning the hybrid battery cooling intake filter every 20,000 miles or 24 months.

See "Hybrid Battery (Traction Battery) Air Intake Vent" in the "Maintenance and Care" section of the Owner's Manual for the cleaning procedure. It is also visually checked at each 5,000-mile inspection.

**Brake fluid flush.** Brake fluid absorbs water over time, so a flush about every two years is a common recommendation.

Drain and flush the old brake fluid and refill with fresh DOT 3 or DOT 4 fluid as specified. A general source suggests 30,000 miles / 36 months; the 24-month starter-pack interval is used here.

**Replace engine coolant.** Toyota recommends replacing the engine coolant every 50,000 miles or 60 months after the first change.

The first replacement is at 100,000 miles / 120 months, then every 50,000 miles / 60 months. Drain the cooling system and refill; a qualified technician should do this. Intervals assume Genuine Toyota Super Long-Life Coolant or a similar non-silicate, non-amine, non-borate ethylene-glycol coolant; other coolants may need different intervals.

**Replace inverter coolant.** Toyota recommends replacing the inverter coolant every 50,000 miles or 60 months after the first change.

The first replacement is at 150,000 miles / 180 months, then every 50,000 miles / 60 months. Intervals assume Genuine Toyota Super Long-Life Coolant or similar; other ethylene-glycol coolants may need different intervals.

**Replace spark plugs.** Toyota recommends replacing the spark plugs every 120,000 miles or 144 months.

Install new plugs of the same type as originally equipped (iridium). Spark plug maintenance is required under the terms of the Emissions Control Warranty. A qualified technician should do this, and the ignition coils and boots can be checked at the same time.

</details>

## triumph-t100-2024-handbook

**succeeded** · 14 suggestions · recall 100% (11/11) · intervals 100% · citations 100% · precision 93% · $0.294 · 92.5 s

Documents: Owner's Handbook rev issue 1 (owners manual)

| # | Suggestion | Component | Schedule | First due | Source | Pre-selected | Matched expected |
|---|---|---|---|---|---|---|---|
| 1 | First service inspection | Thing | once | 600 odometer | document: Owner's Handbook, rev issue 1, p. 99, 100, PDF p. 99, 100 | no | first service |
| 2 | Annual / 10,000-mile inspection | Thing | every 10,000 odometer or every 1 years | — | document: Owner's Handbook, rev issue 1, p. 99, 100, PDF p. 97, 99, 100 | yes | annual / 10,000 mile service |
| 3 | 20,000-mile inspection (valve clearances, camshaft timing) | Thing | every 20,000 odometer | — | document: Owner's Handbook, rev issue 1, p. 99, PDF p. 99 | yes | valve clearance check |
| 4 | Engine oil & filter | Thing | every 10,000 odometer or every 1 years | — | document: Owner's Handbook, rev issue 1, p. 99, PDF p. 99 | yes | engine oil and filter change |
| 5 | Air cleaner - renew | Thing | every 10,000 odometer | — | document: Owner's Handbook, rev issue 1, p. 99, PDF p. 99 | yes | air cleaner replacement |
| 6 | Fuel filter - renew | Thing | every 20,000 odometer | — | document: Owner's Handbook, rev issue 1, p. 99, PDF p. 99 | yes | fuel filter replacement |
| 7 | Spark plugs - renew | Thing | every 20,000 odometer | — | document: Owner's Handbook, rev issue 1, p. 99, PDF p. 99 | yes | spark plugs |
| 8 | Coolant - renew | Thing | every 3 years | — | document: Owner's Handbook, rev issue 1, p. 99, 107, PDF p. 99, 107 | yes | coolant replacement |
| 9 | Fork oil - renew | Thing | every 40,000 odometer | — | document: Owner's Handbook, rev issue 1, p. 99, PDF p. 99 | yes | fork oil change |
| 10 | Headstock bearings - lubricate | Thing | every 20,000 odometer | — | document: Owner's Handbook, rev issue 1, p. 99, PDF p. 99 | yes | headstock bearing lubrication |
| 11 | Brake fluid - renew | Thing | every 2 years | — | document: Owner's Handbook, rev issue 1, p. 100, PDF p. 100 | yes | brake fluid replacement |
| 12 | Drive chain - wear check & adjustment | Thing | every 500 odometer | — | document: Owner's Handbook, rev issue 1, p. 100, 111, 114, PDF p. 100, 111, 114 | yes | drive chain wear check |
| 13 | Drive chain - lubricate | Thing | every 200 odometer | — | document: Owner's Handbook, rev issue 1, p. 100, 111, PDF p. 100, 111 | yes | drive chain lubrication |
| 14 | Side stand pivot pin - clean/grease | Thing | every 10,000 odometer | — | document: Owner's Handbook, rev issue 1, p. 100, PDF p. 100 | yes | — |

<details><summary>Rationale and description</summary>

**First service inspection.** Triumph recommends a first service at 600 miles (1000 km) or 6 months, whichever comes first.

One-time first service: check for leaks, run a full Autoscan with the Triumph diagnostic tool, check coolant, clutch cable, tyres, wheels, steering, suspension, brakes, drive chain, lights and fasteners, renew the oil and filter, then road test and reset the service indicator. The manual gives 600 miles (1000 km) or 6 months, whichever comes first. The 6-month limit runs from a date this vehicle does not record.

**Annual / 10,000-mile inspection.** Triumph recommends this scheduled inspection every 10,000 miles (16,000 km) or every year, whichever comes first.

Scheduled inspection: check for leaks, fuel system, throttle body plates, cooling system and hoses, clutch cable, tyres, wheels and bearings, steering, suspension, headstock bearings, brakes, drive chain, lights and electrics, fasteners, stands, and outstanding service bulletins. Run a full Autoscan and a road test, then reset the service indicator. Bikes ridden under 10,000 miles (16,000 km) a year are serviced annually, with mileage-based items done when their mileage is reached.

**20,000-mile inspection (valve clearances, camshaft timing).** Triumph recommends checking valve clearances and camshaft timing every 20,000 miles (32,000 km).

Additional engine checks at 20,000 miles (32,000 km), done together with the 10,000-mile inspection: check valve clearances and camshaft timing.

**Engine oil & filter.** Triumph recommends renewing the engine oil and filter every 10,000 miles (16,000 km) or every year, whichever comes first.

Renew the engine oil and oil filter. The manual marks both at the first service, the annual service and every 10,000 miles, whichever comes first.

**Air cleaner - renew.** Triumph recommends renewing the air cleaner element every 10,000 miles (16,000 km).

Renew the air cleaner element every 10,000 miles (16,000 km). Renew sooner if the bike is used on dusty roads.

**Fuel filter - renew.** Triumph recommends renewing the fuel filter every 20,000 miles (32,000 km).

Renew the fuel filter every 20,000 miles (32,000 km).

**Spark plugs - renew.** Triumph recommends renewing the spark plugs every 20,000 miles (32,000 km).

Renew the spark plugs every 20,000 miles (32,000 km).

**Coolant - renew.** Triumph recommends renewing the coolant every 3 years regardless of mileage.

Renew the coolant every 3 years regardless of mileage. The manual says an authorised Triumph dealer should do this. Use the coolant type the manual specifies.

**Fork oil - renew.** Triumph recommends renewing the fork oil every 40,000 miles (64,000 km).

Renew the fork oil every 40,000 miles (64,000 km).

**Headstock bearings - lubricate.** Triumph recommends lubricating the headstock bearings every 20,000 miles (32,000 km).

Lubricate the headstock bearings every 20,000 miles (32,000 km).

**Brake fluid - renew.** Triumph recommends renewing the brake fluid every 2 years regardless of mileage.

Renew the front and rear brake fluid every 2 years regardless of mileage, and check the brake lines and hoses for cracking and leaks.

**Drive chain - wear check & adjustment.** Triumph recommends checking the drive chain wear and slack every 500 miles (800 km).

Check the chain wear and slack, adjust as needed, and check the sprockets and rubbing strip. Every 500 miles (800 km). Check more often in extreme conditions such as high-speed riding or salty or gritted roads.

**Drive chain - lubricate.** Triumph recommends lubricating the drive chain every 200 miles (300 km) and after wet rides.

Lubricate the chain every 200 miles (300 km), after riding in wet weather or on wet roads, and whenever it looks dry. Do it more often in extreme conditions such as high-speed riding or salty or gritted roads.

**Side stand pivot pin - clean/grease.** Triumph recommends cleaning and greasing the side stand pivot pin every 10,000 miles (16,000 km).

Clean and grease the side stand pivot pin every 10,000 miles (16,000 km).

</details>

- Not in the answer key: Side stand pivot pin - clean/grease

## triumph-t100-2024-handbook

**succeeded** · 14 suggestions · recall 91% (10/11) · intervals 100% · citations 100% · precision 86% · $0.301 · 90.8 s

Documents: Owner's Handbook rev issue 1 (owners manual)

| # | Suggestion | Component | Schedule | First due | Source | Pre-selected | Matched expected |
|---|---|---|---|---|---|---|---|
| 1 | First service inspection | Thing | once | 600 odometer | document: Owner's Handbook, rev issue 1, p. 99, 100, PDF p. 99, 100 | no | — |
| 2 | Annual / 10,000-mile inspection | Thing | every 10,000 odometer or every 1 years | — | document: Owner's Handbook, rev issue 1, p. 99, 100, PDF p. 97, 99, 100 | yes | — |
| 3 | 20,000-mile inspection (valve clearances, camshaft timing) | Thing | every 20,000 odometer | — | document: Owner's Handbook, rev issue 1, p. 99, PDF p. 99 | yes | — |
| 4 | Engine oil & filter | Thing | every 10,000 odometer or every 1 years | — | document: Owner's Handbook, rev issue 1, p. 99, PDF p. 99 | yes | engine oil and filter change |
| 5 | Air cleaner - renew | Thing | every 10,000 odometer | — | document: Owner's Handbook, rev issue 1, p. 99, PDF p. 99 | yes | air cleaner replacement |
| 6 | Fuel filter - renew | Thing | every 20,000 odometer | — | document: Owner's Handbook, rev issue 1, p. 99, PDF p. 99 | yes | fuel filter replacement |
| 7 | Spark plugs - renew | Thing | every 20,000 odometer | — | document: Owner's Handbook, rev issue 1, p. 99, PDF p. 99 | yes | spark plugs |
| 8 | Coolant - renew | Thing | every 3 years | — | document: Owner's Handbook, rev issue 1, p. 99, 107, PDF p. 99, 107 | yes | coolant replacement |
| 9 | Fork oil - renew | Thing | every 40,000 odometer | — | document: Owner's Handbook, rev issue 1, p. 99, PDF p. 99 | yes | fork oil change |
| 10 | Headstock bearings - lubricate | Thing | every 20,000 odometer | — | document: Owner's Handbook, rev issue 1, p. 99, PDF p. 99 | yes | headstock bearing lubrication |
| 11 | Brake fluid - renew | Thing | every 2 years | — | document: Owner's Handbook, rev issue 1, p. 100, PDF p. 100 | yes | brake fluid replacement |
| 12 | Drive chain - wear check & adjustment | Thing | every 500 odometer | — | document: Owner's Handbook, rev issue 1, p. 100, 111, 114, PDF p. 100, 111, 114 | yes | — |
| 13 | Drive chain - lubricate | Thing | every 200 odometer | — | document: Owner's Handbook, rev issue 1, p. 100, 111, PDF p. 100, 111 | yes | — |
| 14 | Side stand pivot pin - clean/grease | Thing | every 10,000 odometer | — | document: Owner's Handbook, rev issue 1, p. 100, PDF p. 100 | yes | — |

<details><summary>Rationale and description</summary>

**First service inspection.** Triumph recommends a first service at 600 miles (1000 km) or 6 months, whichever comes first.

One-time first service: check for leaks, run a full Autoscan with the Triumph diagnostic tool, check coolant, clutch cable, tyres, wheels, steering, suspension, brakes, drive chain, lights and fasteners, renew the oil and filter, then road test and reset the service indicator. The manual gives 600 miles (1000 km) or 6 months, whichever comes first. The 6-month limit runs from a date this vehicle does not record.

**Annual / 10,000-mile inspection.** Triumph recommends this scheduled inspection every 10,000 miles (16,000 km) or every year, whichever comes first.

Scheduled inspection: check for leaks, fuel system, throttle body plates, cooling system and hoses, clutch cable, tyres, wheels and bearings, steering, suspension, headstock bearings, brakes, drive chain, lights and electrics, fasteners, stands, and outstanding service bulletins. Run a full Autoscan and a road test, then reset the service indicator. Bikes ridden under 10,000 miles (16,000 km) a year are serviced annually, with mileage-based items done when their mileage is reached.

**20,000-mile inspection (valve clearances, camshaft timing).** Triumph recommends checking valve clearances and camshaft timing every 20,000 miles (32,000 km).

Additional engine checks at 20,000 miles (32,000 km), done together with the 10,000-mile inspection: check valve clearances and camshaft timing.

**Engine oil & filter.** Triumph recommends renewing the engine oil and filter every 10,000 miles (16,000 km) or every year, whichever comes first.

Renew the engine oil and oil filter. The manual marks both at the first service, the annual service and every 10,000 miles, whichever comes first.

**Air cleaner - renew.** Triumph recommends renewing the air cleaner element every 10,000 miles (16,000 km).

Renew the air cleaner element every 10,000 miles (16,000 km). Renew sooner if the bike is used on dusty roads.

**Fuel filter - renew.** Triumph recommends renewing the fuel filter every 20,000 miles (32,000 km).

Renew the fuel filter every 20,000 miles (32,000 km).

**Spark plugs - renew.** Triumph recommends renewing the spark plugs every 20,000 miles (32,000 km).

Renew the spark plugs every 20,000 miles (32,000 km).

**Coolant - renew.** Triumph recommends renewing the coolant every 3 years regardless of mileage.

Renew the coolant every 3 years regardless of mileage. The manual says an authorised Triumph dealer should do this. Use the coolant type the manual specifies.

**Fork oil - renew.** Triumph recommends renewing the fork oil every 40,000 miles (64,000 km).

Renew the fork oil every 40,000 miles (64,000 km).

**Headstock bearings - lubricate.** Triumph recommends lubricating the headstock bearings every 20,000 miles (32,000 km).

Lubricate the headstock bearings every 20,000 miles (32,000 km).

**Brake fluid - renew.** Triumph recommends renewing the brake fluid every 2 years regardless of mileage.

Renew the front and rear brake fluid every 2 years regardless of mileage, and check the brake lines and hoses for cracking and leaks.

**Drive chain - wear check & adjustment.** Triumph recommends checking the drive chain wear and slack every 500 miles (800 km).

Check the chain wear and slack, adjust as needed, and check the sprockets and rubbing strip. Every 500 miles (800 km). Check more often in extreme conditions such as high-speed riding or salty or gritted roads.

**Drive chain - lubricate.** Triumph recommends lubricating the drive chain every 200 miles (300 km) and after wet rides.

Lubricate the chain every 200 miles (300 km), after riding in wet weather or on wet roads, and whenever it looks dry. Do it more often in extreme conditions such as high-speed riding or salty or gritted roads.

**Side stand pivot pin - clean/grease.** Triumph recommends cleaning and greasing the side stand pivot pin every 10,000 miles (16,000 km).

Clean and grease the side stand pivot pin every 10,000 miles (16,000 km).

</details>

- Missed: valve clearance check
- Not in the answer key: 20,000-mile major inspection
- Not in the answer key: Side stand pivot pin - clean/grease

## triumph-t100-2024-handbook

**succeeded** · 14 suggestions · recall 100% (11/11) · intervals 100% · citations 100% · precision 93% · $0.305 · 96.0 s

Documents: Owner's Handbook rev issue 1 (owners manual)

| # | Suggestion | Component | Schedule | First due | Source | Pre-selected | Matched expected |
|---|---|---|---|---|---|---|---|
| 1 | First service inspection | Thing | once | 600 odometer | document: Owner's Handbook, rev issue 1, p. 99, 100, PDF p. 99, 100 | no | — |
| 2 | Annual / 10,000-mile inspection | Thing | every 10,000 odometer or every 1 years | — | document: Owner's Handbook, rev issue 1, p. 99, 100, PDF p. 97, 99, 100 | yes | annual / 10,000 mile service |
| 3 | 20,000-mile inspection (valve clearances, camshaft timing) | Thing | every 20,000 odometer | — | document: Owner's Handbook, rev issue 1, p. 99, PDF p. 99 | yes | — |
| 4 | Engine oil & filter | Thing | every 10,000 odometer or every 1 years | — | document: Owner's Handbook, rev issue 1, p. 99, PDF p. 99 | yes | engine oil and filter change |
| 5 | Air cleaner - renew | Thing | every 10,000 odometer | — | document: Owner's Handbook, rev issue 1, p. 99, PDF p. 99 | yes | air cleaner replacement |
| 6 | Fuel filter - renew | Thing | every 20,000 odometer | — | document: Owner's Handbook, rev issue 1, p. 99, PDF p. 99 | yes | fuel filter replacement |
| 7 | Spark plugs - renew | Thing | every 20,000 odometer | — | document: Owner's Handbook, rev issue 1, p. 99, PDF p. 99 | yes | spark plugs |
| 8 | Coolant - renew | Thing | every 3 years | — | document: Owner's Handbook, rev issue 1, p. 99, 107, PDF p. 99, 107 | yes | coolant replacement |
| 9 | Fork oil - renew | Thing | every 40,000 odometer | — | document: Owner's Handbook, rev issue 1, p. 99, PDF p. 99 | yes | fork oil change |
| 10 | Headstock bearings - lubricate | Thing | every 20,000 odometer | — | document: Owner's Handbook, rev issue 1, p. 99, PDF p. 99 | yes | headstock bearing lubrication |
| 11 | Brake fluid - renew | Thing | every 2 years | — | document: Owner's Handbook, rev issue 1, p. 100, PDF p. 100 | yes | brake fluid replacement |
| 12 | Drive chain - wear check & adjustment | Thing | every 500 odometer | — | document: Owner's Handbook, rev issue 1, p. 100, 111, 114, PDF p. 100, 111, 114 | yes | — |
| 13 | Drive chain - lubricate | Thing | every 200 odometer | — | document: Owner's Handbook, rev issue 1, p. 100, 111, PDF p. 100, 111 | yes | drive chain lubrication |
| 14 | Side stand pivot pin - clean/grease | Thing | every 10,000 odometer | — | document: Owner's Handbook, rev issue 1, p. 100, PDF p. 100 | yes | — |

<details><summary>Rationale and description</summary>

**First service inspection.** Triumph recommends a first service at 600 miles (1000 km) or 6 months, whichever comes first.

One-time first service: check for leaks, run a full Autoscan with the Triumph diagnostic tool, check coolant, clutch cable, tyres, wheels, steering, suspension, brakes, drive chain, lights and fasteners, renew the oil and filter, then road test and reset the service indicator. The manual gives 600 miles (1000 km) or 6 months, whichever comes first. The 6-month limit runs from a date this vehicle does not record.

**Annual / 10,000-mile inspection.** Triumph recommends this scheduled inspection every 10,000 miles (16,000 km) or every year, whichever comes first.

Scheduled inspection: check for leaks, fuel system, throttle body plates, cooling system and hoses, clutch cable, tyres, wheels and bearings, steering, suspension, headstock bearings, brakes, drive chain, lights and electrics, fasteners, stands, and outstanding service bulletins. Run a full Autoscan and a road test, then reset the service indicator. Bikes ridden under 10,000 miles (16,000 km) a year are serviced annually, with mileage-based items done when their mileage is reached.

**20,000-mile inspection (valve clearances, camshaft timing).** Triumph recommends checking valve clearances and camshaft timing every 20,000 miles (32,000 km).

Additional engine checks at 20,000 miles (32,000 km), done together with the 10,000-mile inspection: check valve clearances and camshaft timing.

**Engine oil & filter.** Triumph recommends renewing the engine oil and filter every 10,000 miles (16,000 km) or every year, whichever comes first.

Renew the engine oil and oil filter. The manual marks both at the first service, the annual service and every 10,000 miles, whichever comes first.

**Air cleaner - renew.** Triumph recommends renewing the air cleaner element every 10,000 miles (16,000 km).

Renew the air cleaner element every 10,000 miles (16,000 km). Renew sooner if the bike is used on dusty roads.

**Fuel filter - renew.** Triumph recommends renewing the fuel filter every 20,000 miles (32,000 km).

Renew the fuel filter every 20,000 miles (32,000 km).

**Spark plugs - renew.** Triumph recommends renewing the spark plugs every 20,000 miles (32,000 km).

Renew the spark plugs every 20,000 miles (32,000 km).

**Coolant - renew.** Triumph recommends renewing the coolant every 3 years regardless of mileage.

Renew the coolant every 3 years regardless of mileage. The manual says an authorised Triumph dealer should do this. Use the coolant type the manual specifies.

**Fork oil - renew.** Triumph recommends renewing the fork oil every 40,000 miles (64,000 km).

Renew the fork oil every 40,000 miles (64,000 km).

**Headstock bearings - lubricate.** Triumph recommends lubricating the headstock bearings every 20,000 miles (32,000 km).

Lubricate the headstock bearings every 20,000 miles (32,000 km).

**Brake fluid - renew.** Triumph recommends renewing the brake fluid every 2 years regardless of mileage.

Renew the front and rear brake fluid every 2 years regardless of mileage, and check the brake lines and hoses for cracking and leaks.

**Drive chain - wear check & adjustment.** Triumph recommends checking the drive chain wear and slack every 500 miles (800 km).

Check the chain wear and slack, adjust as needed, and check the sprockets and rubbing strip. Every 500 miles (800 km). Check more often in extreme conditions such as high-speed riding or salty or gritted roads.

**Drive chain - lubricate.** Triumph recommends lubricating the drive chain every 200 miles (300 km) and after wet rides.

Lubricate the chain every 200 miles (300 km), after riding in wet weather or on wet roads, and whenever it looks dry. Do it more often in extreme conditions such as high-speed riding or salty or gritted roads.

**Side stand pivot pin - clean/grease.** Triumph recommends cleaning and greasing the side stand pivot pin every 10,000 miles (16,000 km).

Clean and grease the side stand pivot pin every 10,000 miles (16,000 km).

</details>

- Not in the answer key: Side stand pivot pin - clean/grease
