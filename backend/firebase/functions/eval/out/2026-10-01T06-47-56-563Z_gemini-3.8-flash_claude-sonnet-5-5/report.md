# Task suggestion eval

`fast=gemini-3.8-flash` · `strong=claude-sonnet-5-5` · `locate=keywords` · `recallTier=fast` · `claude=direct` · `attachPdf=true` · `ocr=none` · `repeat=3` · `generationVersion=tasks-4`

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
| Recall | 96% | ≥ 90% |
| Interval accuracy | 93% | ≥ 95% |
| Citation accuracy | 97% | ≥ 95% |
| p90 latency, 3+ documents | 191.9 s | < 180.0 s |
| Precision (soft target, not a gate) | 87% | ≥ 70% |

4 document suggestions duplicate a task another suggestion matched.

## Cost and speed

- 9 runs, 0 failed
- Mean cost per run: $0.444
- Mean cost per document: $0.266
- p90 latency on a warm cache: — (R19: < 5 s)

## Cases

| Case | Status | Suggestions | Recall | Intervals | Citations | Precision | Duplicates | Not in key | Cost | Time | Reviewed |
|---|---|---|---|---|---|---|---|---|---|---|---|
| sling-tsi-three-manuals | succeeded | 36 (document 36) | 100% | 90% | 97% | 83% | 2 | 4 | $0.717 | 168.7 s | yes |
| sling-tsi-three-manuals | succeeded | 33 (document 33) | 100% | 81% | 96% | 82% | 0 | 6 | $0.723 | 172.1 s | yes |
| sling-tsi-three-manuals | succeeded | 37 (document 37) | 94% | 90% | 97% | 81% | 2 | 5 | $0.757 | 191.9 s | yes |
| toyota-sienna-hybrid-2022-guide | succeeded | 11 (document 10, common practice 1) | 89% | 100% | 90% | 100% | 0 | 0 | $0.294 | 93.3 s | yes |
| toyota-sienna-hybrid-2022-guide | succeeded | 10 (document 10) | 89% | 100% | 90% | 100% | 0 | 0 | $0.313 | 105.8 s | yes |
| toyota-sienna-hybrid-2022-guide | succeeded | 11 (document 11) | 100% | 100% | 100% | 100% | 0 | 0 | $0.289 | 88.9 s | yes |
| triumph-t100-2024-handbook | succeeded | 14 (document 14) | 100% | 100% | 100% | 93% | 0 | 1 | $0.294 | 92.5 s | yes |
| triumph-t100-2024-handbook | succeeded | 14 (document 14) | 91% | 100% | 100% | 86% | 0 | 2 | $0.301 | 90.8 s | yes |
| triumph-t100-2024-handbook | succeeded | 14 (document 14) | 100% | 100% | 100% | 93% | 0 | 1 | $0.305 | 96.0 s | yes |

### sling-tsi-three-manuals

- interval differs: engine air filter service ↔ Air filter remove and clean or replace
- interval differs: throttle and heater cable lubrication ↔ Lubricate throttle, choke and heater cables (300 hours)
- interval differs: propeller initial 50-hour inspection ↔ First 50 hours engine and propeller service
- wrong page: First 50 hours engine and propeller service
- duplicate: Engine and propeller service (50 hours / annual)
- duplicate: Engine oil change after first 25 hours
- not in the answer key: Lubricate wastegate lever
- not in the answer key: Engine cleaning
- not in the answer key: Coat propeller shaft with corrosion inhibitor
- not in the answer key: Replace rubber plate under expansion tank

### sling-tsi-three-manuals

- interval differs: engine air filter service ↔ Air filter cleaning or replacement
- interval differs: engine 50-hour check ↔ Engine 50-hour / annual service
- interval differs: spark plug replacement ↔ Spark plug replacement
- interval differs: engine oil and filter change ↔ Engine oil and filter change
- interval differs: propeller initial 50-hour inspection ↔ Propeller 50-hour / annual service
- wrong page: Propeller 50-hour / annual service
- not in the answer key: Fire extinguisher service
- not in the answer key: Lubricate the wastegate lever
- not in the answer key: Coat propeller shaft with corrosion inhibitor
- not in the answer key: Engine cleaning
- not in the answer key: Replace air intake hoses
- not in the answer key: Replace rubber plate under expansion tank

### sling-tsi-three-manuals

- missed: throttle and heater cable lubrication
- interval differs: engine air filter service ↔ Air filter removal and cleaning or replacement
- interval differs: engine 50-hour check ↔ First 50-hour engine and propeller service
- interval differs: propeller initial 50-hour inspection ↔ 50-hour / annual engine and propeller service
- wrong page: 50-hour / annual engine and propeller service
- duplicate: Replace rubber hoses of the lubrication system
- duplicate: Lubrication - 300 hour points
- not in the answer key: Lubricate the wastegate lever
- not in the answer key: Coat propeller shaft with corrosion inhibitor
- not in the answer key: Replace connecting hose of the air intake system
- not in the answer key: Replace rubber plate under expansion tank
- not in the answer key: Replace air intake hose (turbocharger to airbox)

### toyota-sienna-hybrid-2022-guide

- missed: 15,000-mile inspection
- wrong page: 30,000-mile / 36-month inspection

### toyota-sienna-hybrid-2022-guide

- missed: 15,000-mile inspection
- wrong page: 30,000-mile / 36-month inspection

### triumph-t100-2024-handbook

- not in the answer key: Side stand pivot pin - clean/grease

### triumph-t100-2024-handbook

- missed: valve clearance check
- not in the answer key: 20,000-mile major inspection
- not in the answer key: Side stand pivot pin - clean/grease

### triumph-t100-2024-handbook

- not in the answer key: Side stand pivot pin - clean/grease
