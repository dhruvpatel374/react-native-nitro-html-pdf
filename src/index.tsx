import { NitroModules } from 'react-native-nitro-modules';
import type { NitroHtmlPdf } from './NitroHtmlPdf.nitro';

const NitroHtmlPdfHybridObject =
  NitroModules.createHybridObject<NitroHtmlPdf>('NitroHtmlPdf');

export function multiply(a: number, b: number): number {
  return NitroHtmlPdfHybridObject.multiply(a, b);
}
