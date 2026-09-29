import type { SourceDocumentRef } from "./model.js";
import type { ExtractOutput, ExtractedItem, RecalledItem, TailoredSuggestion } from "./stageTypes.js";
import type { Draft } from "./validate/types.js";

export type CandidateDocument = { index: number; ref: SourceDocumentRef; extraction: ExtractOutput };

/**
 * Tailored suggestions → drafts. Source, citation, pages and regulatory typing come from the
 * candidate the suggestion names, never from the tailor's own words, so the validators check what
 * the source said. A suggestion naming no known candidate is invented and dropped.
 */
export function buildDrafts(
  suggestions: TailoredSuggestion[],
  documents: CandidateDocument[],
  recalled: RecalledItem[],
): Draft[] {
  return suggestions.flatMap((s): Draft[] => {
    const docItems = s.candidateIds.flatMap((id) => documentCandidate(id, documents));
    const recallItems = s.candidateIds.flatMap((id) => recallCandidate(id, recalled));
    if (docItems.length === 0 && recallItems.length === 0) return [];

    const figures = [...docItems.map((c) => c.item), ...recallItems].flatMap((item) =>
      item.intervals.map((i) => i.value),
    );
    const base = {
      title: s.title,
      rationale: s.rationale,
      description: s.description,
      componentSlotKey: s.componentSlotKey ?? "",
      componentHint: s.componentHint ?? "",
      rules: [],
      rawRules: s.rules,
      isOneTime: s.isOneTime,
      lastDone: null,
      lastDoneLogId: s.lastDoneLogId,
      matchesExistingTaskId: s.matchesExistingTaskId ?? "",
      intervalDifferenceNote: s.intervalDifferenceNote ?? "",
      mergesStaticIndex: s.mergesStaticIndex ?? -1,
      preselect: false,
      confidence: s.confidence,
    };

    const primary = docItems[0];
    if (primary) {
      const { document } = primary.doc.extraction;
      const { item } = primary;
      return [
        {
          ...base,
          type: item.type,
          referenceNumber: item.referenceNumber ?? "",
          complianceAuthority: item.complianceAuthority ?? "",
          sourceKind: "document",
          citation: document.revision ? `${document.title}, rev ${document.revision}` : document.title,
          pageRef: pageRef(item),
          sourceDocument: primary.doc.ref.blobId,
          evidence: { documentIndex: primary.doc.index, pages: item.pages, sourceFigures: figures },
        },
      ];
    }

    const recall = recallItems[0];
    return [
      {
        ...base,
        type: "routine",
        referenceNumber: "",
        complianceAuthority: "",
        sourceKind: recall.sourceKind,
        citation: recall.sourceKind === "manufacturer_schedule" ? (recall.publication ?? "") : "",
        pageRef: "",
        sourceDocument: "",
        evidence: { documentIndex: null, pages: [], sourceFigures: figures },
      },
    ];
  });
}

function documentCandidate(id: string, documents: CandidateDocument[]) {
  const match = /^d(\d+)\.(\d+)$/.exec(id.trim());
  if (!match) return [];
  const doc = documents.find((d) => d.index === Number(match[1]));
  const item = doc?.extraction.items[Number(match[2])];
  return doc && item ? [{ doc, item }] : [];
}

function recallCandidate(id: string, recalled: RecalledItem[]): RecalledItem[] {
  const match = /^r(\d+)$/.exec(id.trim());
  const item = match ? recalled[Number(match[1])] : undefined;
  return item ? [item] : [];
}

function pageRef(item: ExtractedItem): string {
  if (item.printedPageRef) return `p. ${item.printedPageRef}`;
  return item.pages.length > 0 ? `p. ${item.pages[0]}` : "";
}
