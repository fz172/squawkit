import { PDFDocument } from "pdf-lib";

/**
 * A new PDF holding only the given 1-based pages, in order. No metadata, so the same pages give the
 * same bytes on every run; pdf-lib's default dates would change the eval's request key each time.
 */
export async function slicePdf(bytes: Uint8Array, pages: number[]): Promise<Uint8Array> {
  const source = await PDFDocument.load(bytes, { ignoreEncryption: true });
  const out = await PDFDocument.create({ updateMetadata: false });
  const copied = await out.copyPages(source, pages.map((n) => n - 1));
  copied.forEach((p) => out.addPage(p));
  return out.save();
}
