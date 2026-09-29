/** An OCR pre-processor for pages with no text layer (design §5.5). The bake-off picks one. */
export interface OcrProvider {
  readonly id: string;
  /**
   * Text for the given 1-based pages of a PDF, or for the single page of an image (pass `[1]`).
   * Pages the provider returns nothing for are left out of the result.
   */
  ocr(input: OcrInput, pages: number[]): Promise<OcrResult>;
}

export type OcrInput = { bytes: Uint8Array; mime: string };

export type OcrPage = { n: number; text: string };

export type OcrResult = { pages: OcrPage[]; pagesBilled: number; costMicros: number };
