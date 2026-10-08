import { readFileSync, readdirSync } from "node:fs";
import { join } from "node:path";
import { describe, expect, it } from "vitest";
import { curatedListFor, curatedTemplateIds, parseCuratedFile } from "../../src/ai/tasks/curated.js";
import { ThingTemplate, type ComponentSlot } from "../../src/generated/proto/thing/template.js";

/** The app's canonical templates, newest version of each id. */
const TEMPLATES_DIR = join(__dirname, "../../../../../core/template/templates/binary");

function latestTemplates(): ThingTemplate[] {
  const newest = new Map<string, ThingTemplate>();
  for (const file of readdirSync(TEMPLATES_DIR).filter((f) => f.endsWith(".pb"))) {
    const template = ThingTemplate.decode(readFileSync(join(TEMPLATES_DIR, file)));
    const seen = newest.get(template.id);
    if (!seen || seen.version < template.version) newest.set(template.id, template);
  }
  return [...newest.values()];
}

function slotKeys(slots: ComponentSlot[]): string[] {
  return slots.flatMap((s) => [s.slotKey, ...slotKeys(s.children)]);
}

const item = {
  title: "Oil change",
  description: "",
  componentSlotKey: "",
  rules: [{ kind: "time", every: 6, unit: "months" }],
  sourceKind: "common_practice",
  citation: "",
};

describe("curated lists", () => {
  it("has one for every template but custom", () => {
    const ids = latestTemplates().map((t) => t.id).filter((id) => id !== "custom");

    expect(curatedTemplateIds().sort()).toEqual(ids.sort());
    expect(curatedListFor("custom")).toEqual([]);
    expect(curatedListFor("no-such-template")).toEqual([]);
  });

  it.each(latestTemplates().filter((t) => t.id !== "custom").map((t) => [t.id, t] as const))(
    "%s: names only meters and slots its template defines",
    (_, template) => {
      const meters = template.meters.map((m) => m.key);
      const slots = slotKeys(template.componentSlots);

      for (const curated of curatedListFor(template.id)) {
        if (curated.componentSlotKey) expect(slots).toContain(curated.componentSlotKey);
        for (const rule of curated.rules) if (rule.kind === "meter") expect(meters).toContain(rule.meterKey);
      }
    },
  );

  it.each(curatedTemplateIds())("%s: no two items share a title", (id) => {
    const titles = curatedListFor(id).map((c) => c.title.trim().toLowerCase());

    expect(new Set(titles).size).toBe(titles.length);
  });
});

describe("parseCuratedFile", () => {
  it("reads a well-formed file", () => {
    expect(parseCuratedFile({ templateId: "automotive", items: [item] })).toEqual({ templateId: "automotive", items: [item] });
  });

  it.each([
    ["no template id", { items: [item] }],
    ["no items", { templateId: "automotive" }],
    ["an empty title", { templateId: "automotive", items: [{ ...item, title: " " }] }],
    ["an unknown source kind", { templateId: "automotive", items: [{ ...item, sourceKind: "folklore" }] }],
    ["no rule", { templateId: "automotive", items: [{ ...item, rules: [] }] }],
    ["a zero interval", { templateId: "automotive", items: [{ ...item, rules: [{ kind: "meter", meterKey: "odometer", interval: 0 }] }] }],
    ["month 13", { templateId: "automotive", items: [{ ...item, rules: [{ kind: "seasonal", months: [13], dayOfMonth: 0 }] }] }],
    ["an unknown rule", { templateId: "automotive", items: [{ ...item, rules: [{ kind: "linked" }] }] }],
  ])("refuses %s", (_, file) => {
    expect(() => parseCuratedFile(file)).toThrow(/curated/);
  });
});
