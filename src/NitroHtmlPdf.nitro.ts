import type { HybridObject } from 'react-native-nitro-modules';

export type PageSize = 'A4' | 'Letter' | 'Legal' | 'A3' | 'A5';

export interface PdfOptions {
  html: string;
  fileName: string;
  directory?: string;
  pageSize?: PageSize;
  width?: number;
  height?: number;
  marginTop?: number;
  marginBottom?: number;
  marginLeft?: number;
  marginRight?: number;
  header?: string;
  footer?: string;
  headerHeight?: number;
  footerHeight?: number;
  showPageNumbers?: boolean;
  pageNumberFormat?: string;
  pageNumberFontSize?: number;
}

export interface PdfResult {
  filePath: string;
  success: boolean;
  error?: string;
}

export interface NitroHtmlPdf
  extends HybridObject<{ ios: 'swift'; android: 'kotlin' }> {
  generatePdf(options: PdfOptions): Promise<PdfResult>;
}
