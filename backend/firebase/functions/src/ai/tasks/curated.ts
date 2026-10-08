import airplane from "./curated/airplane.json";
import automotive from "./curated/automotive.json";
import bike from "./curated/bike.json";
import boat from "./curated/boat.json";
import home from "./curated/home.json";
import type { SuggestedRule, TaskSourceKind } from "./model.js";

/**
 * The curated suggestions (design §6.8, PRD R9a): the list of tasks the team keeps per template,
 * returned by every task run ahead of the AI's. They began as the templates' starter packs and are
 * edited by hand in `curated/{templateId}.json`. A change ships with the functions, not the app.
 *
 * An item has a suggestion's fields as the pipeline names them, plus the source kind and citation
 * the team writes. It is always typed routine (R18), whatever its citation says.
 */
export type CuratedItem = {
  title: string;
  description: string;
  /** Empty files the task at Thing level. */
  componentSlotKey: string;
  rules: SuggestedRule[];
  sourceKind: TaskSourceKind;
  /** "14 CFR 91.413", or empty. */
  citation: string;
};

const SOURCE_KINDS: readonly TaskSourceKind[] = ["document", "manufacturer_schedule", "common_practice", "logs"];
const TIME_UNITS = ["days", "months", "years"];

const FILES: unknown[] = [airplane, automotive, bike, boat, home];

const LISTS: ReadonlyMap<string, CuratedItem[]> = new Map(
  FILES.map((file) => {
    const list = parseCuratedFile(file);
    return [list.templateId, list.items];
  }),
);

/** The curated list for [templateId]; empty for `custom` and for a template the server does not know. */
export function curatedListFor(templateId: string): CuratedItem[] {
  return LISTS.get(templateId) ?? [];
}

/** Every template id with a curated list. */
export function curatedTemplateIds(): string[] {
  return [...LISTS.keys()];
}

/** Reads one `curated/*.json`, throwing on anything malformed, so a bad edit fails the tests. */
export function parseCuratedFile(file: unknown): { templateId: string; items: CuratedItem[] } {
  const f = file as { templateId?: unknown; items?: unknown };
  if (typeof f?.templateId !== "string" || f.templateId === "") throw new Error("curated: templateId missing");
  if (!Array.isArray(f.items)) throw new Error(`curated ${f.templateId}: items missing`);
  return { templateId: f.templateId, items: f.items.map((item, i) => parseItem(item, `${f.templateId}[${i}]`)) };
}

function parseItem(raw: unknown, where: string): CuratedItem {
  const item = raw as Record<string, unknown>;
  const text = (key: string): string => {
    const value = item?.[key];
    if (typeof value !== "string") throw new Error(`curated ${where}: ${key} must be text`);
    return value;
  };
  const title = text("title");
  if (title.trim() === "") throw new Error(`curated ${where}: title is empty`);
  const sourceKind = text("sourceKind") as TaskSourceKind;
  if (!SOURCE_KINDS.includes(sourceKind)) throw new Error(`curated ${where}: unknown sourceKind ${sourceKind}`);
  if (!Array.isArray(item.rules) || item.rules.length === 0) throw new Error(`curated ${where}: needs a rule`);
  return {
    title,
    description: text("description"),
    componentSlotKey: text("componentSlotKey"),
    rules: item.rules.map((rule) => parseRule(rule, where)),
    sourceKind,
    citation: text("citation"),
  };
}

function parseRule(raw: unknown, where: string): SuggestedRule {
  const rule = raw as Record<string, unknown>;
  const positive = (value: unknown) => typeof value === "number" && value > 0;
  switch (rule?.kind) {
    case "time":
      if (positive(rule.every) && TIME_UNITS.includes(rule.unit as string)) return rule as SuggestedRule;
      break;
    case "meter":
      if (typeof rule.meterKey === "string" && rule.meterKey !== "" && positive(rule.interval)) return rule as SuggestedRule;
      break;
    case "seasonal": {
      const months = rule.months;
      const valid = Array.isArray(months) && months.length > 0 && months.every((m) => Number.isInteger(m) && m >= 1 && m <= 12);
      if (valid && typeof rule.dayOfMonth === "number") return rule as SuggestedRule;
      break;
    }
    case "on_condition":
      if (typeof rule.description === "string") return rule as SuggestedRule;
      break;
  }
  throw new Error(`curated ${where}: malformed rule ${JSON.stringify(rule)}`);
}
