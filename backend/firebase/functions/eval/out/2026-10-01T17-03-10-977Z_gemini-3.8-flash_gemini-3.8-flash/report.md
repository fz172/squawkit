# Task suggestion eval

`fast=gemini-3.8-flash` · `strong=gemini-3.8-flash` · `locate=keywords` · `recallTier=fast` · `claude=vertex` · `attachPdf=true` · `ocr=none` · `repeat=3` · `generationVersion=tasks-4`

## Hard gates: PASS

| Gate | Violations |
|---|---|
| AD/SB typed without that document | 0 |
| Reference number not verbatim in the document | 0 |
| Document suggestion its cited page does not state | 0 |
| Meter key outside the template | 0 |
| Runs without valid output after one retry | 0 |
| Suggested a must-not-appear task | 0 |

## Document cases: below the bar (9 runs)

| Measure | Result | Bar |
|---|---|---|
| Recall | 79% | ≥ 90% |
| Interval accuracy | 89% | ≥ 95% |
| Citation accuracy | 96% | ≥ 95% |
| p90 latency, 3+ documents | 505.4 s | < 180.0 s |
| Precision (soft target, not a gate) | 75% | ≥ 70% |

5 document suggestions duplicate a task another suggestion matched.

## Cost and speed

- 18 runs, 1 failed
- Mean cost per run: $0.160
- Mean cost per document: $0.192
- p90 latency on a warm cache: — (R19: < 5 s)

## Cases

| Case | Status | Suggestions | Recall | Intervals | Citations | Precision | Duplicates | Not in key | Cost | Time | Reviewed |
|---|---|---|---|---|---|---|---|---|---|---|---|
| sling-tsi-no-docs | succeeded | 7 (manufacturer schedule 7) | — | — | — | — | 0 | 0 | $0.100 | 200.8 s | no |
| sling-tsi-no-docs | succeeded | 7 (manufacturer schedule 7) | — | — | — | — | 0 | 0 | $0.176 | 357.6 s | no |
| sling-tsi-no-docs | succeeded | 6 (manufacturer schedule 6) | — | — | — | — | 0 | 0 | $0.045 | 119.9 s | no |
| toyota-sienna-hybrid-2022-no-docs | succeeded | 8 (manufacturer schedule 8) | — | — | — | — | 0 | 0 | $0.151 | 339.1 s | no |
| toyota-sienna-hybrid-2022-no-docs | succeeded | 7 (manufacturer schedule 7) | — | — | — | — | 0 | 0 | $0.038 | 67.8 s | no |
| toyota-sienna-hybrid-2022-no-docs | succeeded | 8 (manufacturer schedule 8) | — | — | — | — | 0 | 0 | $0.089 | 150.4 s | no |
| triumph-t100-2024-no-docs | succeeded | 6 (manufacturer schedule 6) | — | — | — | — | 0 | 0 | $0.094 | 180.8 s | no |
| triumph-t100-2024-no-docs | succeeded | 6 (manufacturer schedule 6) | — | — | — | — | 0 | 0 | $0.127 | 246.0 s | no |
| triumph-t100-2024-no-docs | succeeded | 6 (manufacturer schedule 6) | — | — | — | — | 0 | 0 | $0.031 | 54.9 s | no |
| toyota-sienna-hybrid-2022-guide | succeeded | 14 (document 14) | 100% | 100% | 100% | 100% | 0 | 0 | $0.180 | 245.4 s | yes |
| toyota-sienna-hybrid-2022-guide | succeeded | 15 (document 14, common practice 1) | 100% | 100% | 100% | 100% | 0 | 0 | $0.207 | 266.0 s | yes |
| toyota-sienna-hybrid-2022-guide | succeeded | 15 (document 14, common practice 1) | 100% | 100% | 100% | 100% | 0 | 0 | $0.141 | 236.8 s | yes |
| triumph-t100-2024-handbook | succeeded | 15 (document 15) | 91% | 82% | 100% | 73% | 0 | 4 | $0.227 | 326.1 s | yes |
| triumph-t100-2024-handbook | succeeded | 14 (document 14) | 91% | 91% | 100% | 79% | 0 | 3 | $0.124 | 194.2 s | yes |
| triumph-t100-2024-handbook | succeeded | 15 (document 15) | 82% | 100% | 100% | 67% | 0 | 5 | $0.198 | 237.0 s | yes |
| sling-tsi-three-manuals | succeeded | 43 (document 42, manufacturer schedule 1) | 100% | 85% | 92% | 60% | 4 | 13 | $0.379 | 370.9 s | yes |
| sling-tsi-three-manuals | succeeded | 38 (document 37, manufacturer schedule 1) | 88% | 72% | 88% | 65% | 1 | 12 | $0.344 | 505.4 s | yes |
| sling-tsi-three-manuals | failed: provider_error | 0  | 0% | — | — | — | 0 | 0 | $0.229 | 344.6 s | yes |

### triumph-t100-2024-handbook

- missed: valve clearance check
- interval differs: air cleaner replacement ↔ Air cleaner - renew
- interval differs: fuel filter replacement ↔ Fuel filter - renew
- not in the answer key: 600-mile / 6-month inspection
- not in the answer key: 20,000-mile inspection
- not in the answer key: Throttle body plate (butterfly) - check/clean
- not in the answer key: Side stand pivot pin - clean/grease

### triumph-t100-2024-handbook

- missed: valve clearance check
- interval differs: air cleaner replacement ↔ Air cleaner - renew
- not in the answer key: 600-mile / 6-month inspection
- not in the answer key: 20,000-mile inspection
- not in the answer key: Side stand pivot pin - clean/grease

### triumph-t100-2024-handbook

- missed: valve clearance check
- missed: drive chain wear check
- not in the answer key: 600-mile / 6-month inspection
- not in the answer key: 20,000-mile inspection
- not in the answer key: 500-mile inspection
- not in the answer key: Throttle body plate (butterfly) - check/clean
- not in the answer key: Side stand pivot pin - clean/grease

### sling-tsi-three-manuals

- interval differs: airframe 100-hour / annual condition inspection ↔ Condition inspection
- interval differs: engine coolant replacement ↔ Engine coolant replacement
- interval differs: propeller initial 25-hour inspection ↔ Initial 25-hour engine inspection
- interval differs: engine 50-hour check ↔ First 50-hour service
- wrong page: First 25-hour engine oil change
- wrong page: Initial 25-hour engine inspection
- duplicate: 50-hour / annual service
- duplicate: Lubricate engine choke cable
- duplicate: Replace lubrication system rubber hoses
- duplicate: Replace carbon brushes (mini sliprings)
- not in the answer key: Lubricate heater activation cables
- not in the answer key: Replace rear spar bolt
- not in the answer key: Replace main spar attachment hardware
- not in the answer key: Replace elevator trim tab control horn
- not in the answer key: Replace main undercarriage attachment hardware
- not in the answer key: Replace connecting hose of air intake system
- not in the answer key: Replace rubber plate under expansion tank
- not in the answer key: Replace turbocharger air intake hose
- not in the answer key: Inspect and clean screen in turbo oil sump
- not in the answer key: Check and clean oil tank
- not in the answer key: Lubricate turbocharger wastegate lever
- not in the answer key: Coat exposed surface of propeller shaft with corrosion inhibitor
- not in the answer key: Engine cleaning

### sling-tsi-three-manuals

- missed: weight and balance check
- missed: engine rubber hose replacement (5-year)
- interval differs: airframe 100-hour / annual condition inspection ↔ 100-hour inspection
- interval differs: engine 50-hour check ↔ Every 50 hours / annual engine and propeller service
- interval differs: throttle and heater cable lubrication ↔ Lubricate engine and heater cables
- interval differs: replace air ducting (SCAT/CAT tubing) ↔ Air intake system connecting hose replacement
- interval differs: engine air filter service ↔ Air intake hose replacement
- interval differs: engine coolant replacement ↔ Rotax 915 iS Coolant Replacement
- interval differs: propeller initial 50-hour inspection ↔ First 50 hours service inspection
- wrong page: Air intake system connecting hose replacement
- wrong page: Air intake hose replacement
- wrong page: First 50 hours service inspection
- duplicate: First 25 hours engine oil change
- not in the answer key: Replace all air ducting
- not in the answer key: Mass & Balance
- not in the answer key: Replace rear spar bolt and main spar attachment hardware
- not in the answer key: Replace elevator trim tab control horn
- not in the answer key: Replace main undercarriage attachment hardware
- not in the answer key: Cooling system rubber hoses replacement
- not in the answer key: Lubrication system rubber hoses replacement
- not in the answer key: V-belt replacement
- not in the answer key: Rubber plate replacement
- not in the answer key: Lubricate the wastegate lever
- not in the answer key: Coat exposed surface of propeller shaft with corrosion inhibitor
- not in the answer key: Engine cleaning

### sling-tsi-three-manuals

- ended failed, expected otherwise
- missed: airframe 100-hour / annual condition inspection
- missed: airframe 2000-hour service
- missed: throttle and heater cable lubrication
- missed: replace air ducting (SCAT/CAT tubing)
- missed: compass swing
- missed: weight and balance check
- missed: muffler / heat exchanger inspection
- missed: engine overhaul (TBO)
- missed: engine 100-hour / annual check
- missed: engine 200-hour check
- missed: engine 600-hour check
- missed: torsion shaft replacement
- missed: spark plug replacement
- missed: engine fuel filter replacement
- missed: engine rubber hose replacement (5-year)
- missed: propeller 100-hour / annual inspection
- missed: propeller carbon brush replacement
