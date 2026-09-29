import { GoogleAuth } from "google-auth-library";
import { PDFDocument } from "pdf-lib";

import { AiError } from "../../errors.js";
import { describe } from "../../providers/json.js";
import { MIME_PDF } from "../../providers/types.js";
import type { OcrInput, OcrPage, OcrProvider, OcrResult } from "./types.js";

/** Synchronous Enterprise Document OCR takes at most 15 pages per request. */
const PAGES_PER_REQUEST = 15;
/** $1.50 per 1,000 pages for the first 5M a month, as published on 2026-09-28. */
const MICROS_PER_PAGE = 1500;

type TextSegment = { startIndex?: string; endIndex?: string };

type ProcessResponse = {
  document?: {
    text?: string;
    pages?: Array<{ pageNumber?: number; layout?: { textAnchor?: { textSegments?: TextSegment[] } } }>;
  };
};

/** Posts one process request and returns its JSON. Injected in tests. */
export type DocumentAiTransport = (url: string, body: unknown) => Promise<ProcessResponse>;

export type DocumentAiOcrOptions = {
  /** `projects/{project}/locations/{location}/processors/{id}`, an Enterprise OCR processor. */
  processorName: string;
  transport?: DocumentAiTransport;
};

export function createDocumentAiOcr(options: DocumentAiOcrOptions): OcrProvider {
  const location = /\/locations\/([^/]+)\//.exec(options.processorName)?.[1];
  if (!location) throw new Error(`Bad processor name ${options.processorName}`);
  const url = `https://${location}-documentai.googleapis.com/v1/${options.processorName}:process`;
  const transport = options.transport ?? adcTransport();

  return {
    id: "document-ai-ocr",
    async ocr(input: OcrInput, pages: number[]): Promise<OcrResult> {
      const chunks =
        input.mime === MIME_PDF ? await splitPdf(input.bytes, pages) : [{ bytes: input.bytes, pages: [1] }];
      const out: OcrPage[] = [];
      try {
        for (const chunk of chunks) {
          const response = await transport(url, {
            rawDocument: { content: Buffer.from(chunk.bytes).toString("base64"), mimeType: input.mime },
          });
          out.push(...pagesOf(response, chunk.pages));
        }
      } catch (e) {
        throw new AiError("provider_error", `document-ai: ${describe(e)}`, { cause: e });
      }
      const pagesBilled = chunks.reduce((sum, c) => sum + c.pages.length, 0);
      return { pages: out, pagesBilled, costMicros: pagesBilled * MICROS_PER_PAGE };
    },
  };
}

/** Each chunk is a new PDF of up to 15 of the wanted pages; `pages` maps its pages back. */
async function splitPdf(bytes: Uint8Array, pages: number[]) {
  const source = await PDFDocument.load(bytes, { ignoreEncryption: true });
  const chunks: Array<{ bytes: Uint8Array; pages: number[] }> = [];
  for (let i = 0; i < pages.length; i += PAGES_PER_REQUEST) {
    const wanted = pages.slice(i, i + PAGES_PER_REQUEST);
    const chunk = await PDFDocument.create();
    const copied = await chunk.copyPages(source, wanted.map((n) => n - 1));
    copied.forEach((p) => chunk.addPage(p));
    chunks.push({ bytes: await chunk.save(), pages: wanted });
  }
  return chunks;
}

function pagesOf(response: ProcessResponse, originalPages: number[]): OcrPage[] {
  const text = response.document?.text ?? "";
  return (response.document?.pages ?? []).map((page, i) => {
    const segments = page.layout?.textAnchor?.textSegments ?? [];
    const pageText = segments
      .map((s) => text.slice(Number(s.startIndex ?? 0), Number(s.endIndex ?? 0)))
      .join("");
    const n = originalPages[(page.pageNumber ?? i + 1) - 1] ?? originalPages[i];
    return { n, text: pageText };
  });
}

function adcTransport(): DocumentAiTransport {
  const auth = new GoogleAuth({ scopes: ["https://www.googleapis.com/auth/cloud-platform"] });
  return async (url, body) => {
    const client = await auth.getClient();
    const response = await client.request<ProcessResponse>({ url, method: "POST", data: body });
    return response.data;
  };
}
