import type { DocumentPage } from "../document/readDocument.js";

/**
 * Finding the schedule pages (design §6.2), so extraction reads only them. The bake-off picks the
 * method; `all` is the baseline it is measured against.
 */
export type LocateMethod = "keywords" | "model" | "all";

/** A document this short is read whole whatever the method. */
export const READ_WHOLE_BELOW_PAGES = 30;
/** Most schedule pages sent to extraction, before neighbours are added. */
export const MAX_SCHEDULE_PAGES = 40;
/** Title, revision and applicability are almost always on the first pages. */
const FRONT_PAGES = 2;

const SCHEDULE_TERMS: RegExp[] = [
  /\b(maintenance|inspection|service|lubrication)\s+(schedule|program(me)?|intervals?|chart|table)\b/gi,
  /\bevery\s+\d/gi,
  /\b\d[\d,.]*\s*(h|hr|hrs|hours|flight hours|months?|years?|mi|miles|km|kilometers|cycles|landings)\b/gi,
  /\binterval\b/gi,
  /\b(inspect|replace|check|lubricate|overhaul|service|clean|test)\b/gi,
  /\b(tbo|annual|100[- ]?h(ou)?r)\b/gi,
];

/** A page needs this many schedule-term hits, or a tenth of the best page's, to be located. */
const MIN_PAGE_SCORE = 5;

/**
 * Pages ranked by schedule-term hits. The floor is mostly absolute: a schedule's table pages score
 * far below its dense introduction page, so a cut relative to the best page drops the table.
 */
export function locateByKeywords(pages: DocumentPage[]): number[] {
  const scored = pages
    .map((p) => ({ n: p.n, score: score(p.text) }))
    .sort((a, b) => b.score - a.score || a.n - b.n);
  const floor = Math.max(MIN_PAGE_SCORE, (scored[0]?.score ?? 0) / 10);
  return scored
    .filter((p) => p.score >= floor)
    .slice(0, MAX_SCHEDULE_PAGES)
    .map((p) => p.n);
}

/** One line per page for the model locator: the page number and its opening words. */
export function pageDigest(pages: DocumentPage[], charsPerPage = 240): string {
  return pages
    .map((p) => `[page ${p.n}] ${p.text.replace(/\s+/g, " ").slice(0, charsPerPage)}`)
    .join("\n");
}

/** The pages extraction reads: the located ones, their neighbours, and the front pages. */
export function withContext(located: number[], pageCount: number): number[] {
  const wanted = new Set<number>();
  for (let n = 1; n <= Math.min(FRONT_PAGES, pageCount); n++) wanted.add(n);
  for (const n of located.slice(0, MAX_SCHEDULE_PAGES)) {
    for (const m of [n - 1, n, n + 1]) if (m >= 1 && m <= pageCount) wanted.add(m);
  }
  return [...wanted].sort((a, b) => a - b);
}

function score(text: string): number {
  return SCHEDULE_TERMS.reduce((sum, re) => sum + (text.match(re)?.length ?? 0), 0);
}
