import { PDFDocument } from "pdf-lib";
import { describe, expect, it } from "vitest";

import { slicePdf } from "../../src/ai/document/pdfSlice.js";

async function threePages(): Promise<Uint8Array> {
  const doc = await PDFDocument.create();
  [100, 200, 300].forEach((w) => doc.addPage([w, 100]));
  return doc.save();
}

describe("slicePdf", () => {
  it("keeps only the given pages, in order", async () => {
    const out = await PDFDocument.load(await slicePdf(await threePages(), [3, 1]));
    expect(out.getPages().map((p) => p.getWidth())).toEqual([300, 100]);
  });

  it("gives the same bytes every time", async () => {
    const source = await threePages();
    const first = await slicePdf(source, [2]);
    await new Promise((r) => setTimeout(r, 1100));
    expect(Buffer.from(await slicePdf(source, [2])).equals(Buffer.from(first))).toBe(true);
  });
});
