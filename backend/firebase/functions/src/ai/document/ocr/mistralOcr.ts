import { AiError } from "../../errors.js";
import { describe } from "../../providers/json.js";
import { MIME_PDF } from "../../providers/types.js";
import type { OcrInput, OcrProvider, OcrResult } from "./types.js";

const ENDPOINT = "https://api.mistral.ai/v1/ocr";
const MODEL = "mistral-ocr-latest";
/** $4 per 1,000 pages, as published on 2026-09-28. */
const MICROS_PER_PAGE = 4000;

type MistralOcrResponse = {
  pages: Array<{ index: number; markdown: string }>;
  usage_info?: { pages_processed?: number };
};

export type MistralOcrOptions = {
  apiKey: string;
  fetchImpl?: typeof fetch;
};

export function createMistralOcr(options: MistralOcrOptions): OcrProvider {
  const fetchImpl = options.fetchImpl ?? fetch;

  return {
    id: MODEL,
    async ocr(input: OcrInput, pages: number[]): Promise<OcrResult> {
      const isPdf = input.mime === MIME_PDF;
      const url = `data:${input.mime};base64,${Buffer.from(input.bytes).toString("base64")}`;
      const body = {
        model: MODEL,
        document: isPdf
          ? { type: "document_url", document_url: url }
          : { type: "image_url", image_url: url },
        // Mistral numbers pages from 0.
        ...(isPdf ? { pages: pages.map((n) => n - 1) } : {}),
      };

      let json: MistralOcrResponse;
      try {
        const response = await fetchImpl(ENDPOINT, {
          method: "POST",
          headers: {
            "Content-Type": "application/json",
            Authorization: `Bearer ${options.apiKey}`,
          },
          body: JSON.stringify(body),
        });
        if (!response.ok) throw new Error(`HTTP ${response.status}: ${await response.text()}`);
        json = (await response.json()) as MistralOcrResponse;
      } catch (e) {
        throw new AiError("provider_error", `${MODEL}: ${describe(e)}`, { cause: e });
      }

      const pagesBilled = json.usage_info?.pages_processed ?? json.pages.length;
      return {
        pages: json.pages.map((p) => ({ n: isPdf ? p.index + 1 : 1, text: p.markdown })),
        pagesBilled,
        costMicros: pagesBilled * MICROS_PER_PAGE,
      };
    },
  };
}
