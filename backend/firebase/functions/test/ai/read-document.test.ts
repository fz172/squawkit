import { PDFDocument, StandardFonts } from "pdf-lib";
import { describe, expect, it } from "vitest";

import { createDocumentAiOcr } from "../../src/ai/document/ocr/documentAiOcr.js";
import { createMistralOcr } from "../../src/ai/document/ocr/mistralOcr.js";
import type { OcrInput, OcrProvider } from "../../src/ai/document/ocr/types.js";
import { readDocument } from "../../src/ai/document/readDocument.js";
import { AiError } from "../../src/ai/errors.js";

/** A PDF whose pages carry the given text; `null` makes an image-only page (no text layer). */
async function pdf(pages: Array<string | null>): Promise<Uint8Array> {
  const doc = await PDFDocument.create();
  const font = await doc.embedFont(StandardFonts.Helvetica);
  for (const text of pages) {
    const page = doc.addPage();
    if (text) page.drawText(text, { x: 50, y: 700, font, size: 12 });
  }
  return doc.save();
}

function fakeOcr(): OcrProvider & { calls: Array<{ input: OcrInput; pages: number[] }> } {
  const calls: Array<{ input: OcrInput; pages: number[] }> = [];
  return {
    id: "fake-ocr",
    calls,
    async ocr(input, pages) {
      calls.push({ input, pages });
      return {
        pages: pages.map((n) => ({ n, text: `Scanned page ${n}: replace ELT battery every 24 months` })),
        pagesBilled: pages.length,
        costMicros: pages.length * 1000,
      };
    },
  };
}

async function rejection(p: Promise<unknown>): Promise<AiError> {
  return p.then(
    () => expect.fail("expected a rejection"),
    (e: unknown) => e as AiError,
  );
}

const LINE = "Every 100 hours inspect the spark plugs";

describe("readDocument", () => {
  it("reads the text layer without calling OCR", async () => {
    const ocr = fakeOcr();
    const out = await readDocument({ bytes: await pdf([LINE, LINE]), mime: "application/pdf" }, { ocr });

    expect(out.pages.map((p) => [p.n, p.source])).toEqual([
      [1, "text_layer"],
      [2, "text_layer"],
    ]);
    expect(out.pages[0].text).toBe(LINE);
    expect(ocr.calls).toHaveLength(0);
    expect(out.ocr).toEqual({ pagesBilled: 0, costMicros: 0 });
  });

  it("sends only image-only pages to OCR", async () => {
    const ocr = fakeOcr();
    const out = await readDocument({ bytes: await pdf([LINE, null, "x", LINE]), mime: "application/pdf" }, { ocr });

    // "x" is under the text threshold, so it counts as image-only.
    expect(ocr.calls[0].pages).toEqual([2, 3]);
    expect(out.pages.map((p) => p.source)).toEqual(["text_layer", "ocr", "ocr", "text_layer"]);
    expect(out.pages[1].text).toContain("Scanned page 2");
    expect(out.ocr).toEqual({ pagesBilled: 2, costMicros: 2000 });
  });

  it("leaves image-only pages empty without OCR", async () => {
    const out = await readDocument({ bytes: await pdf([LINE, null]), mime: "application/pdf" });
    expect(out.pages[1]).toEqual({ n: 2, text: "", source: "none" });
  });

  it("fails document_unreadable when no page has text", async () => {
    const e = await rejection(readDocument({ bytes: await pdf([null, null]), mime: "application/pdf" }));
    expect(e.code).toBe("document_unreadable");
  });

  it("OCRs a photo and keeps its bytes for the provider", async () => {
    const photo = new Uint8Array([1, 2, 3]);
    const ocr = fakeOcr();
    const out = await readDocument({ bytes: photo, mime: "image/jpeg" }, { ocr });

    expect(ocr.calls[0].pages).toEqual([1]);
    expect(out.pages).toHaveLength(1);
    expect(out.pages[0].source).toBe("ocr");
    expect(out.pages[0].image).toBe(photo);
  });

  it("rejects a photo without OCR, an unknown type, and a corrupt PDF", async () => {
    const photo = await rejection(readDocument({ bytes: new Uint8Array([1]), mime: "image/png" }));
    const docx = await rejection(readDocument({ bytes: new Uint8Array([1]), mime: "application/msword" }));
    const corrupt = await rejection(
      readDocument({ bytes: new TextEncoder().encode("not a pdf"), mime: "application/pdf" }),
    );
    expect([photo.code, docx.code, corrupt.code]).toEqual([
      "document_unreadable",
      "document_unreadable",
      "document_unreadable",
    ]);
  });
});

describe("mistral OCR", () => {
  function fakeFetch(status = 200) {
    const bodies: Array<Record<string, unknown>> = [];
    const fetchImpl = (async (_url: string, init: RequestInit) => {
      bodies.push(JSON.parse(init.body as string));
      return new Response(
        JSON.stringify({
          pages: [
            { index: 1, markdown: "page two" },
            { index: 4, markdown: "page five" },
          ],
          usage_info: { pages_processed: 2 },
        }),
        { status },
      );
    }) as typeof fetch;
    return { fetchImpl, bodies };
  }

  it("asks for 0-based pages of a PDF and maps them back", async () => {
    const { fetchImpl, bodies } = fakeFetch();
    const out = await createMistralOcr({ apiKey: "k", fetchImpl }).ocr(
      { bytes: new Uint8Array([1]), mime: "application/pdf" },
      [2, 5],
    );

    expect(bodies[0].pages).toEqual([1, 4]);
    expect((bodies[0].document as Record<string, string>).type).toBe("document_url");
    expect(out.pages).toEqual([
      { n: 2, text: "page two" },
      { n: 5, text: "page five" },
    ]);
    expect(out).toMatchObject({ pagesBilled: 2, costMicros: 8000 });
  });

  it("sends a photo as an image with no page list", async () => {
    const { fetchImpl, bodies } = fakeFetch();
    await createMistralOcr({ apiKey: "k", fetchImpl }).ocr({ bytes: new Uint8Array([1]), mime: "image/png" }, [1]);
    expect((bodies[0].document as Record<string, string>).image_url).toMatch(/^data:image\/png;base64,/);
    expect(bodies[0].pages).toBeUndefined();
  });

  it("wraps an HTTP failure as a provider error", async () => {
    const { fetchImpl } = fakeFetch(500);
    const e = await rejection(
      createMistralOcr({ apiKey: "k", fetchImpl }).ocr({ bytes: new Uint8Array([1]), mime: "image/png" }, [1]),
    );
    expect(e.code).toBe("provider_error");
  });
});

describe("document AI OCR", () => {
  it("splits a PDF into 15-page requests and maps pages back", async () => {
    const sent: Array<{ url: string; pageCount: number }> = [];
    const transport = async (url: string, body: unknown) => {
      const raw = (body as { rawDocument: { content: string } }).rawDocument.content;
      const chunk = await PDFDocument.load(Buffer.from(raw, "base64"));
      sent.push({ url, pageCount: chunk.getPageCount() });
      const pages = Array.from({ length: chunk.getPageCount() }, (_, i) => ({
        pageNumber: i + 1,
        layout: { textAnchor: { textSegments: [{ startIndex: String(i * 2), endIndex: String(i * 2 + 2) }] } },
      }));
      const text = pages.map((_, i) => `p${i}`).join("");
      return { document: { text, pages } };
    };
    const ocr = createDocumentAiOcr({ processorName: "projects/p/locations/us/processors/abc", transport });
    const wanted = Array.from({ length: 20 }, (_, i) => i + 11); // pages 11..30

    const out = await ocr.ocr({ bytes: await pdf(Array(30).fill(null)), mime: "application/pdf" }, wanted);

    expect(sent.map((s) => s.pageCount)).toEqual([15, 5]);
    expect(sent[0].url).toBe("https://us-documentai.googleapis.com/v1/projects/p/locations/us/processors/abc:process");
    expect(out.pages[0]).toEqual({ n: 11, text: "p0" });
    expect(out.pages[15]).toEqual({ n: 26, text: "p0" });
    expect(out).toMatchObject({ pagesBilled: 20, costMicros: 30000 });
  });
});
