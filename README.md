# react-native-nitro-html-pdf

High-quality HTML to PDF conversion for React Native with vector-based header/footer support.

## Features

- ✅ Vector-based PDF generation (text remains selectable)
- ✅ Custom headers and footers with vector quality
- ✅ Automatic page numbering
- ✅ Multiple page sizes (A4, A3, A5, Letter, Legal)
- ✅ Custom margins
- ✅ iOS and Android support
- ✅ Built with [Nitro Modules](https://nitro.margelo.com/) for optimal performance

## Installation

```sh
npm install react-native-nitro-html-pdf react-native-nitro-modules
```

> `react-native-nitro-modules` is required as this library relies on [Nitro Modules](https://nitro.margelo.com/).

## Usage

### Basic Example

```typescript
import { generatePdf } from 'react-native-nitro-html-pdf';

const result = await generatePdf({
  html: '<h1>Hello World</h1><p>This is a PDF document.</p>',
  fileName: 'document.pdf',
  pageSize: 'A4',
});

console.log('PDF created at:', result.filePath);
```

### With Header and Footer

```typescript
const result = await generatePdf({
  html: '<h1>Invoice</h1><p>Invoice content...</p>',
  fileName: 'invoice.pdf',
  pageSize: 'A4',
  header: '<div style="text-align: center;"><h3>Company Name</h3></div>',
  footer: '<div style="text-align: center;"><p>Thank you!</p></div>',
  headerHeight: 100,  // Required when using header
  footerHeight: 80,   // Required when using footer
  showPageNumbers: true,
  pageNumberFormat: 'Page {page} of {total}',
  marginTop: 20,
  marginBottom: 20,
});
```

## API Reference

### `generatePdf(options: PdfOptions): Promise<PdfResult>`

#### PdfOptions

| Property | Type | Required | Default | Description |
|----------|------|----------|---------|-------------|
| `html` | `string` | ✅ | - | HTML content to convert to PDF |
| `fileName` | `string` | ✅ | - | Output PDF file name (must end with .pdf) |
| `pageSize` | `'A4' \| 'A3' \| 'A5' \| 'LETTER' \| 'LEGAL'` | ❌ | `'A4'` | Page size |
| `header` | `string` | ❌ | - | HTML content for header |
| `footer` | `string` | ❌ | - | HTML content for footer |
| `headerHeight` | `number` | ⚠️ | - | Header height in pixels (required if header is provided) |
| `footerHeight` | `number` | ⚠️ | - | Footer height in pixels (required if footer is provided) |
| `showPageNumbers` | `boolean` | ❌ | `false` | Show page numbers in footer |
| `pageNumberFormat` | `string` | ❌ | `'Page {page} of {total}'` | Page number format |
| `pageNumberFontSize` | `number` | ❌ | `12` | Page number font size |
| `marginTop` | `number` | ❌ | `0` | Top margin in pixels |
| `marginBottom` | `number` | ❌ | `0` | Bottom margin in pixels |
| `marginLeft` | `number` | ❌ | `0` | Left margin in pixels |
| `marginRight` | `number` | ❌ | `0` | Right margin in pixels |
| `directory` | `string` | ❌ | Cache dir | Output directory path |

#### PdfResult

```typescript
interface PdfResult {
  filePath: string;  // Absolute path to generated PDF
  success: boolean;  // Whether generation succeeded
  error?: string;    // Error message if failed
}
```

## Important Notes

### Header and Footer Heights

**You must provide explicit `headerHeight` and `footerHeight` values when using headers or footers.** The library does not auto-calculate these values.

```typescript
// ❌ Wrong - will throw error
await generatePdf({
  html: '...',
  fileName: 'doc.pdf',
  header: '<h3>Header</h3>',  // Missing headerHeight!
});

// ✅ Correct
await generatePdf({
  html: '...',
  fileName: 'doc.pdf',
  header: '<h3>Header</h3>',
  headerHeight: 100,  // Explicit height
});
```

### Page Size Dimensions

- **A4**: 595 × 842 pts (210 × 297 mm)
- **A3**: 842 × 1191 pts (297 × 420 mm)
- **A5**: 420 × 595 pts (148 × 210 mm)
- **Letter**: 612 × 792 pts (8.5 × 11 in)
- **Legal**: 612 × 1008 pts (8.5 × 14 in)

### Margin Behavior

The library automatically adjusts content margins to prevent overlap:
- Total top margin = `marginTop + headerHeight`
- Total bottom margin = `marginBottom + footerHeight + (showPageNumbers ? 20 : 0)`

### Supported HTML/CSS

Both iOS and Android use native WebView rendering, supporting:
- Standard HTML5 tags
- CSS styling (inline, `<style>` tags)
- Tables, lists, images
- Custom fonts (with proper CSS `@font-face`)

## Error Handling

```typescript
try {
  const result = await generatePdf(options);
  
  if (result.success) {
    console.log('PDF created:', result.filePath);
  } else {
    console.error('PDF generation failed:', result.error);
  }
} catch (error) {
  console.error('Unexpected error:', error);
}
```

## Common Errors

| Error | Cause | Solution |
|-------|-------|----------|
| `headerHeight must be provided` | Using header without height | Add `headerHeight` property |
| `footerHeight must be provided` | Using footer without height | Add `footerHeight` property |
| `Combined header, footer, and margins exceed page height` | Heights too large | Reduce header/footer/margin values |
| `File name must end with .pdf` | Invalid file name | Ensure fileName ends with `.pdf` |
| `HTML content cannot be empty` | Empty HTML | Provide valid HTML content |

## Platform Differences

### Android
- Uses `PrintDocumentAdapter` for vector rendering
- PDFs merged using PDFBox `LayerUtility`
- Text remains selectable in output PDF

### iOS
- Uses `UIPrintPageRenderer` for vector rendering
- Native PDF context drawing
- Text remains selectable in output PDF

## Contributing

- [Development workflow](CONTRIBUTING.md#development-workflow)
- [Sending a pull request](CONTRIBUTING.md#sending-a-pull-request)
- [Code of conduct](CODE_OF_CONDUCT.md)

## License

MIT

---

Made with [create-react-native-library](https://github.com/callstack/react-native-builder-bob)
