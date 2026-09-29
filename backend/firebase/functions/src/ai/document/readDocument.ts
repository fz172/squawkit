import { AiError } from "../errors.js";
import { MIME_PDF } from "../providers/types.js";
import type { OcrProvider } from "./ocr/types.js";
import { extractPdfText } from "./pdfText.js";

const IMAGE_MIMES = new Set(["image/jpeg", "image/png", "image/webp"]);

/** Fewer non-space characters than this and a page counts as image-only. */
const MIN_TEXT_CHARS = 20;

export type PageTextSource = "text_layer" | "ocr" | "none";

export type DocumentPage = {
  n: number;
  text: string;
  source: PageTextSource;
  /** Set for a photo, so a provider can also see it. A PDF goes to providers whole. */
  image?: Uint8Array;
};

export type ReadDocumentResult = {
  pages: DocumentPage[];
  ocr: { pagesBilled: number; costMicros: number };
};

export type ReadDocumentOptions = { ocr?: OcrProvider };

/**
 * Page text for one document (design §5.5). Text is always produced, whatever a provider can read
 * natively, because R18's verbatim check and the citation check run on it. The worker loads the
 * bytes from Storage; the eval harness reads them from disk.
 */
export async function readDocument(
  input: { bytes: Uint8Array; mime: string },
  options: ReadDocumentOptions = {},
): Promise<ReadDocumentResult> {
  if (input.mime === MIME_PDF) return readPdf(input.bytes, options);
  if (IMAGE_MIMES.has(input.mime)) return readImage(input.bytes, input.mime, options);
  throw new AiError("document_unreadable", `Unsupported type ${input.mime}`);
}

async function readPdf(bytes: Uint8Array, options: ReadDocumentOptions): Promise<ReadDocumentResult> {
  const pages: DocumentPage[] = (await extractPdfText(bytes)).map((p) =>
    hasText(p.text) ? { ...p, source: "text_layer" } : { ...p, source: "none" },
  );

  const imageOnly = pages.filter((p) => p.source === "none").map((p) => p.n);
  let ocr = { pagesBilled: 0, costMicros: 0 };
  if (imageOnly.length > 0 && options.ocr) {
    const result = await options.ocr.ocr({ bytes, mime: MIME_PDF }, imageOnly);
    ocr = { pagesBilled: result.pagesBilled, costMicros: result.costMicros };
    for (const ocrPage of result.pages) {
      const page = pages.find((p) => p.n === ocrPage.n);
      if (page && page.source === "none" && hasText(ocrPage.text)) {
        page.text = ocrPage.text.trim();
        page.source = "ocr";
      }
    }
  }
  return requireSomeText({ pages, ocr });
}

async function readImage(
  bytes: Uint8Array,
  mime: string,
  options: ReadDocumentOptions,
): Promise<ReadDocumentResult> {
  if (!options.ocr) throw new AiError("document_unreadable", "A photo needs an OCR provider");
  const result = await options.ocr.ocr({ bytes, mime }, [1]);
  const text = result.pages.map((p) => p.text).join("\n").trim();
  return requireSomeText({
    pages: [{ n: 1, text, source: hasText(text) ? "ocr" : "none", image: bytes }],
    ocr: { pagesBilled: result.pagesBilled, costMicros: result.costMicros },
  });
}

function requireSomeText(result: ReadDocumentResult): ReadDocumentResult {
  if (result.pages.every((p) => p.source === "none")) {
    throw new AiError("document_unreadable", `No text on any of ${result.pages.length} pages`);
  }
  return result;
}

function hasText(text: string): boolean {
  return text.replace(/\s/g, "").length >= MIN_TEXT_CHARS;
}
