import { curatedListFor } from "./curated.js";
import type { SuggestionContext, SuggestTasksResult, TaskSuggestion } from "./model.js";
import { fitMeterRules } from "./validate/validators.js";
import { GENERATION_VERSION } from "./version.js";

/**
 * The curated suggestions for one Thing (design §6.8, PRD R9a), fitted without a model:
 *
 * - a slot the Thing does not fill files the item at Thing level, as for an AI suggestion (R22);
 * - a meter rule on a meter the Thing lacks drops out, by the same rule as an AI suggestion's (R23);
 * - an item whose title matches an existing task's, ignoring case and spacing, is Already tracked
 *   (R24).
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
      rules: fitMeterRules(item.rules, context),
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
      originKind: "pre_curated",
    };
  });
}

/** The result a run carries before the model has said anything: the curated list alone. */
export function curatedResult(context: SuggestionContext): SuggestTasksResult {
  return { suggestions: curatedSuggestions(context), documents: [], generationVersion: GENERATION_VERSION };
}


function normalize(title: string): string {
  return title.trim().replace(/\s+/g, " ").toLowerCase();
}
