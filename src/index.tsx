import { NitroModules } from 'react-native-nitro-modules';
import type { NitroHtmlPdf, PdfOptions, PdfResult } from './NitroHtmlPdf.nitro';

const NitroHtmlPdfHybridObject =
  NitroModules.createHybridObject<NitroHtmlPdf>('NitroHtmlPdf');

export async function generatePdf(options: PdfOptions): Promise<PdfResult> {
  return await NitroHtmlPdfHybridObject.generatePdf(options);
}

export type { PdfOptions, PdfResult, PageSize } from './NitroHtmlPdf.nitro';
