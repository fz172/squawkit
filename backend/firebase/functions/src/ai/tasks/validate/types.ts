import type { DocumentPage } from "../../document/readDocument.js";
import type { DocType, SuggestionContext, TaskSuggestion } from "../model.js";
import type { Confidence, FlatRule } from "../stageTypes.js";

/** A suggestion between the tailor and the result, carrying what the validators check it against. */
export type Draft = Omit<TaskSuggestion, "suggestionId"> & {
  /** The tailor's rules, typed into `rules` by the schema rule. */
  rawRules: FlatRule[];
  lastDoneLogId: string | null;
  confidence: Confidence;
  evidence: {
    /** Index into ValidationInput.documents, for a document source. */
    documentIndex: number | null;
    pages: number[];
    /** Interval figures as the sources state them, for the citation check. */
    sourceFigures: number[];
  };
};

export type ValidatedDocument = {
  blobId: string;
  docType: DocType;
  pages: DocumentPage[];
  matchesThing: boolean;
};

export type ValidationInput = {
  context: SuggestionContext;
  documents: ValidatedDocument[];
};

export type Validator = (drafts: Draft[], input: ValidationInput) => Draft[];
