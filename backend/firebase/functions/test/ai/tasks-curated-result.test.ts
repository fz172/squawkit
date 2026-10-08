import { describe, expect, it } from "vitest";
import { curatedListFor } from "../../src/ai/tasks/curated.js";
import { curatedResult, curatedSuggestions } from "../../src/ai/tasks/curatedResult.js";
import { GENERATION_VERSION } from "../../src/ai/tasks/version.js";
import { airplaneContext } from "./fixtures.js";

const byTitle = (title: string) => (s: { title: string }) => s.title === title;

describe("curatedSuggestions", () => {
  it("returns the template's whole list, in order, as curated, routine suggestions", () => {
    const suggestions = curatedSuggestions(airplaneContext({ existingTasks: [] }));

    expect(suggestions.map((s) => s.title)).toEqual(curatedListFor("airplane").map((c) => c.title));
    expect(suggestions.map((s) => s.suggestionId)).toEqual(suggestions.map((_, i) => `c${i}`));
    for (const s of suggestions) {
      expect(s).toMatchObject({ originKind: "pre_curated", type: "routine", referenceNumber: "", sourceDocument: "", lastDone: null });
    }
  });

  it("keeps the file's source, citation and tick", () => {
    const transponder = curatedSuggestions(airplaneContext({ existingTasks: [] })).find(byTitle("Transponder test"))!;

    expect(transponder).toMatchObject({ sourceKind: "common_practice", citation: "14 CFR 91.413" });
    expect(transponder.rules).toEqual([{ kind: "time", every: 24, unit: "months" }]);
  });

  it("files an item at Thing level when the Thing does not fill its slot", () => {
    const filled = curatedSuggestions(airplaneContext()).find(byTitle("Oil change"))!;
    const unfilled = curatedSuggestions(airplaneContext({ components: [] })).find(byTitle("Oil change"))!;

    expect(filled.componentSlotKey).toBe("engine");
    expect(unfilled.componentSlotKey).toBe("");
  });

  it("drops a rule on a meter the Thing lacks, as for an AI suggestion", () => {
    const noHours = airplaneContext({ meters: [] });
    const hundredHour = curatedSuggestions(noHours).find(byTitle("100-hour inspection"))!;

    expect(hundredHour.rules).toEqual([{ kind: "on_condition", description: "Every 100 airframe hours" }]);
  });

  it("marks an item the Thing already tracks, whatever its case or spacing, and does not tick it", () => {
    const context = airplaneContext({
      existingTasks: [{ id: "task-elt", title: "  elt   INSPECTION ", componentSlotKey: "", rules: [], type: "routine", referenceNumber: "" }],
    });
    const elt = curatedSuggestions(context).find(byTitle("ELT inspection"))!;

    expect(elt).toMatchObject({ matchesExistingTaskId: "task-elt" });
  });

  it("has nothing for custom or an unknown template", () => {
    expect(curatedSuggestions(airplaneContext({ templateId: "custom" }))).toEqual([]);
    expect(curatedSuggestions(airplaneContext({ templateId: "spaceship" }))).toEqual([]);
  });
});

describe("curatedResult", () => {
  it("carries the list, no documents and the generation version", () => {
    const result = curatedResult(airplaneContext());

    expect(result.suggestions).toHaveLength(curatedListFor("airplane").length);
    expect(result.documents).toEqual([]);
    expect(result.generationVersion).toBe(GENERATION_VERSION);
  });
});
