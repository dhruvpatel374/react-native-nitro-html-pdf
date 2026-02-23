import type { HybridObject } from 'react-native-nitro-modules';

/**
 * Supported PDF page sizes
 */
export type PageSize = 'A4' | 'Letter' | 'Legal' | 'A3' | 'A5';

/**
 * Options for PDF generation
 */
export interface PdfOptions {
  /**
   * HTML content to convert to PDF
   * @required
   */
  html: string;

  /**
   * Output PDF file name (must end with .pdf)
   * @required
   */
  fileName: string;

  /**
   * Output directory path
   * @default Cache directory
   */
  directory?: string;

  /**
   * Page size for the PDF
   * @default 'A4'
   */
  pageSize?: PageSize;

  /**
   * Custom page width in points (overrides pageSize)
   */
  width?: number;

  /**
   * Custom page height in points (overrides pageSize)
   */
  height?: number;

  /**
   * Top margin in pixels
   * @default 0
   */
  marginTop?: number;

  /**
   * Bottom margin in pixels
   * @default 0
   */
  marginBottom?: number;

  /**
   * Left margin in pixels
   * @default 0
   */
  marginLeft?: number;

  /**
   * Right margin in pixels
   * @default 0
   */
  marginRight?: number;

  /**
   * HTML content for page header
   * @note Requires headerHeight to be specified
   */
  header?: string;

  /**
   * HTML content for page footer
   * @note Requires footerHeight to be specified
   */
  footer?: string;

  /**
   * Header height in pixels
   * @required when header is provided
   * @note Must be greater than 0
   */
  headerHeight?: number;

  /**
   * Footer height in pixels
   * @required when footer is provided
   * @note Must be greater than 0
   */
  footerHeight?: number;

  /**
   * Show page numbers in footer
   * @default false
   */
  showPageNumbers?: boolean;

  /**
   * Page number format string
   * Use {page} for current page and {total} for total pages
   * @default 'Page {page} of {total}'
   * @example 'Page {page} of {total}'
   * @example '{page}/{total}'
   */
  pageNumberFormat?: string;

  /**
   * Font size for page numbers
   * @default 12
   */
  pageNumberFontSize?: number;
}

/**
 * Result of PDF generation
 */
export interface PdfResult {
  /**
   * Absolute path to the generated PDF file
   */
  filePath: string;

  /**
   * Whether the PDF generation was successful
   */
  success: boolean;

  /**
   * Number of pages in the generated PDF
   */
  numberOfPages?: number;

  /**
   * Error message if generation failed
   */
  error?: string;
}

/**
 * NitroHtmlPdf hybrid object interface
 */
export interface NitroHtmlPdf
  extends HybridObject<{ ios: 'swift'; android: 'kotlin' }> {
  /**
   * Generate a PDF from HTML content
   * @param options - PDF generation options
   * @returns Promise resolving to PdfResult
   * @throws Error if validation fails or generation times out
   */
  generatePdf(options: PdfOptions): Promise<PdfResult>;
}
