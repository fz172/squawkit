import { PDFDocument } from "pdf-lib";

/** A new PDF holding only the given 1-based pages, in order. */
export async function slicePdf(bytes: Uint8Array, pages: number[]): Promise<Uint8Array> {
  const source = await PDFDocument.load(bytes, { ignoreEncryption: true });
  const out = await PDFDocument.create();
  const copied = await out.copyPages(source, pages.map((n) => n - 1));
  copied.forEach((p) => out.addPage(p));
  return out.save();
}
