import { curatedListFor, type CuratedItem } from "./curated.js";
import type { SuggestionContext, SuggestTasksResult, TaskSuggestion } from "./model.js";
import { meterRule } from "./validate/validators.js";
import type { Draft } from "./validate/types.js";
import { GENERATION_VERSION } from "./version.js";

/**
 * The curated suggestions for one Thing (design §6.8, PRD R9a), fitted without a model:
 *
 * - a slot the Thing does not fill files the item at Thing level, as for an AI suggestion (R22);
 * - a meter rule on a meter the Thing lacks drops out, by the same rule as an AI suggestion's (R23);
 * - an item whose title matches an existing task's, ignoring case and spacing, is Already tracked
 *   and not ticked (R24); otherwise the file's own tick stands.
 *
 * Ids are `c<index into the template's list>`, so a card keeps its id from the first result to the
 * merged one.
 */
export function curatedSuggestions(context: SuggestionContext): TaskSuggestion[] {
  const slots = new Set(context.components.map((c) => c.slotKey));
  const existing = new Map(context.existingTasks.map((t) => [normalize(t.title), t.id]));
  return curatedListFor(context.templateId).map((item, index) => {
    const matchesExistingTaskId = existing.get(normalize(item.title)) ?? "";
    return {
      suggestionId: `c${index}`,
      title: item.title,
      rationale: "",
      description: item.description,
      componentSlotKey: slots.has(item.componentSlotKey) ? item.componentSlotKey : "",
      componentHint: "",
      rules: fitRules(item, context),
      isOneTime: false,
      firstDue: null,
      type: "routine",
      referenceNumber: "",
      complianceAuthority: "",
      sourceKind: item.sourceKind,
      citation: item.citation,
      pageRef: "",
      sourcePages: [],
      sourceDocument: "",
      lastDone: null,
      matchesExistingTaskId,
      intervalDifferenceNote: "",
      mergesStaticIndex: -1,
      preselect: item.preselect && !matchesExistingTaskId,
      originKind: "pre_curated",
    };
  });
}

/** The result a run carries before the model has said anything: the curated list alone. */
export function curatedResult(context: SuggestionContext): SuggestTasksResult {
  return { suggestions: curatedSuggestions(context), documents: [], generationVersion: GENERATION_VERSION };
}

/** The item's rules after the meter rule (§6.7 rule 2), which works on drafts. */
function fitRules(item: CuratedItem, context: SuggestionContext) {
  const draft = { rules: item.rules } as Draft;
  return meterRule([draft], { context, documents: [], today: "" })[0].rules;
}

function normalize(title: string): string {
  return title.trim().replace(/\s+/g, " ").toLowerCase();
}
