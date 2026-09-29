import path from "node:path";

import { AiError } from "../errors.js";
import { describe } from "../providers/json.js";

export type PdfTextPage = { n: number; text: string };

/** The PDF text layer, one entry per page, via pdfjs-dist. Image-only pages come back empty. */
export async function extractPdfText(bytes: Uint8Array): Promise<PdfTextPage[]> {
  // pdfjs-dist is ESM-only; this package compiles to CommonJS.
  const pdfjs = await import("pdfjs-dist/legacy/build/pdf.mjs");
  const loadingTask = pdfjs.getDocument({
    // pdfjs transfers the buffer to its worker, so give it a copy.
    data: new Uint8Array(bytes),
    standardFontDataUrl: `${path.dirname(require.resolve("pdfjs-dist/package.json"))}/standard_fonts/`,
    verbosity: 0,
  });
  try {
    const pdf = await loadingTask.promise;
    const pages: PdfTextPage[] = [];
    for (let n = 1; n <= pdf.numPages; n++) {
      const content = await (await pdf.getPage(n)).getTextContent();
      let text = "";
      for (const item of content.items) {
        if (!("str" in item)) continue;
        text += item.str + (item.hasEOL ? "\n" : " ");
      }
      pages.push({ n, text: text.trim() });
    }
    return pages;
  } catch (e) {
    throw new AiError("document_unreadable", `pdf: ${describe(e)}`, { cause: e });
  } finally {
    await loadingTask.destroy();
  }
}
