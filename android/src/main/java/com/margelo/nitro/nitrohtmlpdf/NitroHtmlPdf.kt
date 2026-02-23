package com.margelo.nitro.nitrohtmlpdf

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.webkit.WebView
import android.webkit.WebViewClient
import com.margelo.nitro.core.Promise
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPageContentStream
import com.tom_roush.pdfbox.pdmodel.common.PDRectangle
import com.tom_roush.pdfbox.multipdf.LayerUtility
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

class NitroHtmlPdf : HybridNitroHtmlPdfSpec() {
    companion object {
        @JvmStatic
        var appContext: Context? = null
        private const val TAG = "NitroHtmlPdf"
        private const val TIMEOUT_SECONDS = 60L
    }
    
    private val context: Context
        get() = appContext ?: throw IllegalStateException("Context not initialized. Call NitroHtmlPdf.appContext = context first.")

    override fun generatePdf(options: PdfOptions): Promise<PdfResult> {
        return Promise.async {
            createPdf(options)
        }
    }

    private fun createPdf(options: PdfOptions): PdfResult {
        // Validate inputs
        val validationError = validateOptions(options)
        if (validationError != null) {
            return PdfResult("", false, null, validationError)
        }
        
        val latch = CountDownLatch(1)
        var result: PdfResult? = null

        Handler(Looper.getMainLooper()).post {
            try {
                val directory = options.directory ?: context.cacheDir?.absolutePath ?: "/data/local/tmp"
                val dirFile = File(directory)
                if (!dirFile.exists() && !dirFile.mkdirs()) {
                    result = PdfResult("", false, null, "Failed to create directory: $directory")
                    latch.countDown()
                    return@post
                }
                
                val finalFile = File(directory, options.fileName)
                val hasHeader = !options.header.isNullOrEmpty()
                val hasFooter = !options.footer.isNullOrEmpty()
                
                if (!hasHeader && !hasFooter) {
                    // No header/footer, simple conversion
                    val fullHtml = buildFullHtml(options)
                    val printAttrs = buildPrintAttributes(options)
                    val converter = android.print.PdfConverter.getInstance()
                    converter.setPdfPrintAttrs(printAttrs)
                    converter.convert(context, fullHtml, finalFile, false,
                        object : android.print.PdfConverter.ConversionCallback {
                            override fun onSuccess(filePath: String) {
                                val pageCount = try { PDDocument.load(finalFile).use { it.numberOfPages } } catch (e: Exception) { null }
                                result = PdfResult(filePath, true, pageCount?.toDouble(), null)
                                latch.countDown()
                            }
                            override fun onFailure(error: String) {
                                result = PdfResult("", false, null, error)
                                latch.countDown()
                            }
                        }, null)
                } else {
                    // Generate with vector header/footer
                    generatePdfWithVectorHeaderFooter(options, finalFile) { success, error ->
                        val pageCount = if (success) try { PDDocument.load(finalFile).use { it.numberOfPages } } catch (e: Exception) { null } else null
                        result = if (success) PdfResult(finalFile.absolutePath, true, pageCount?.toDouble(), null)
                                 else PdfResult("", false, null, error)
                        latch.countDown()
                    }
                }
            } catch (e: Exception) {
                result = PdfResult("", false, null, "PDF creation failed: ${e.message}")
                latch.countDown()
            }
        }

        latch.await(TIMEOUT_SECONDS, TimeUnit.SECONDS)
        return result ?: PdfResult("", false, null, "PDF generation timeout after $TIMEOUT_SECONDS seconds")
    }
    
    private fun validateOptions(options: PdfOptions): String? {
        if (options.html.isBlank()) {
            return "HTML content cannot be empty"
        }
        
        if (options.fileName.isBlank()) {
            return "File name cannot be empty"
        }
        
        if (!options.fileName.endsWith(".pdf", ignoreCase = true)) {
            return "File name must end with .pdf"
        }
        
        val hasHeader = !options.header.isNullOrEmpty()
        val hasFooter = !options.footer.isNullOrEmpty()
        
        if (hasHeader && (options.headerHeight == null || options.headerHeight!! <= 0)) {
            return "headerHeight must be provided and greater than 0 when using header"
        }
        
        if (hasFooter && (options.footerHeight == null || options.footerHeight!! <= 0)) {
            return "footerHeight must be provided and greater than 0 when using footer"
        }
        
        val pageSize = getPageSize(options.pageSize?.name ?: "A4")
        val headerHeight = options.headerHeight?.toFloat() ?: 0f
        val footerHeight = options.footerHeight?.toFloat() ?: 0f
        val marginTop = options.marginTop?.toFloat() ?: 0f
        val marginBottom = options.marginBottom?.toFloat() ?: 0f
        
        if (headerHeight + footerHeight + marginTop + marginBottom >= pageSize.height) {
            return "Combined header, footer, and margins exceed page height"
        }
        
        if (options.pageNumberFontSize != null && options.pageNumberFontSize!! <= 0) {
            return "pageNumberFontSize must be greater than 0"
        }
        
        return null
    }

    private fun generatePdfWithVectorHeaderFooter(options: PdfOptions, outputFile: File, callback: (Boolean, String?) -> Unit) {
        val tempFiles = mutableListOf<File>()
        
        try {
            val contentFile = File.createTempFile("content_", ".pdf", context.cacheDir)
            tempFiles.add(contentFile)
            
            val headerFile = if (!options.header.isNullOrEmpty()) {
                File.createTempFile("header_", ".pdf", context.cacheDir).also { tempFiles.add(it) }
            } else null
            
            val footerFile = if (!options.footer.isNullOrEmpty()) {
                File.createTempFile("footer_", ".pdf", context.cacheDir).also { tempFiles.add(it) }
            } else null
            
            val completionLatch = CountDownLatch(tempFiles.size)
            val errors = mutableListOf<String>()
            
            // Generate content PDF using PdfConverter
            val fullHtml = buildFullHtml(options)
            val printAttrs = buildPrintAttributes(options)
            val contentConverter = android.print.PdfConverter.getInstance()
            contentConverter.setPdfPrintAttrs(printAttrs)
            contentConverter.convert(context, fullHtml, contentFile, false,
                object : android.print.PdfConverter.ConversionCallback {
                    override fun onSuccess(filePath: String) {
                        completionLatch.countDown()
                    }
                    override fun onFailure(error: String) {
                        errors.add("Content generation failed: $error")
                        completionLatch.countDown()
                    }
                }, null)
            
            // Generate header PDF
            if (headerFile != null && !options.header.isNullOrEmpty()) {
                val headerConverter = android.print.PdfConverter.getInstance()
                headerConverter.setPdfPrintAttrs(buildPrintAttributes(options))
                headerConverter.convert(context, wrapHtml(options.header!!), headerFile, false,
                    object : android.print.PdfConverter.ConversionCallback {
                        override fun onSuccess(filePath: String) {
                            completionLatch.countDown()
                        }
                        override fun onFailure(error: String) {
                            errors.add("Header generation failed: $error")
                            completionLatch.countDown()
                        }
                    }, null)
            }
            
            // Generate footer PDF
            if (footerFile != null && !options.footer.isNullOrEmpty()) {
                val footerConverter = android.print.PdfConverter.getInstance()
                footerConverter.setPdfPrintAttrs(buildPrintAttributes(options))
                footerConverter.convert(context, wrapHtml(options.footer!!), footerFile, false,
                    object : android.print.PdfConverter.ConversionCallback {
                        override fun onSuccess(filePath: String) {
                            completionLatch.countDown()
                        }
                        override fun onFailure(error: String) {
                            errors.add("Footer generation failed: $error")
                            completionLatch.countDown()
                        }
                    }, null)
            }
            
            // Wait and merge
            Thread {
                try {
                    val completed = completionLatch.await(45, TimeUnit.SECONDS)
                    
                    if (!completed) {
                        callback(false, "PDF generation timed out")
                        cleanupTempFiles(tempFiles)
                        return@Thread
                    }
                    
                    if (errors.isNotEmpty()) {
                        callback(false, errors.joinToString("; "))
                        cleanupTempFiles(tempFiles)
                        return@Thread
                    }
                    
                    mergePdfs(contentFile, headerFile, footerFile, outputFile, options)
                    callback(true, null)
                } catch (e: Exception) {
                    callback(false, "PDF merge failed: ${e.message}")
                } finally {
                    cleanupTempFiles(tempFiles)
                }
            }.start()
            
        } catch (e: Exception) {
            cleanupTempFiles(tempFiles)
            callback(false, "PDF generation setup failed: ${e.message}")
        }
    }
    
    private fun cleanupTempFiles(files: List<File>) {
        files.forEach { file ->
            try {
                if (file.exists()) {
                    file.delete()
                }
            } catch (e: Exception) {
                // Silently ignore cleanup errors
            }
        }
    }
    
    private fun printWebViewToPdf(webView: WebView, outputFile: File, printAttrs: android.print.PrintAttributes, callback: (Boolean, String?) -> Unit) {
        Handler(Looper.getMainLooper()).post {
            android.print.PdfPrintHelper.printWebViewToPdf(webView, outputFile, printAttrs,
                object : android.print.PdfPrintHelper.PrintCallback {
                    override fun onSuccess() {
                        callback(true, null)
                    }
                    override fun onFailure(error: String?) {
                        callback(false, error)
                    }
                })
        }
    }
    
    private fun mergePdfs(contentFile: File, headerFile: File?, footerFile: File?, outputFile: File, options: PdfOptions) {
        com.tom_roush.pdfbox.android.PDFBoxResourceLoader.init(context)
        
        var contentDoc: PDDocument? = null
        var headerDoc: PDDocument? = null
        var footerDoc: PDDocument? = null
        
        try {
            contentDoc = PDDocument.load(contentFile)
            headerDoc = headerFile?.let { PDDocument.load(it) }
            footerDoc = footerFile?.let { PDDocument.load(it) }
            
            if (contentDoc.numberOfPages == 0) {
                throw IllegalStateException("Content PDF has no pages")
            }
            
            val layerUtility = LayerUtility(contentDoc)
            val marginLeft = options.marginLeft?.toFloat() ?: 0f
            val marginRight = options.marginRight?.toFloat() ?: 0f
            val marginTop = options.marginTop?.toFloat() ?: 0f
            val marginBottom = options.marginBottom?.toFloat() ?: 0f
            val headerHeight = options.headerHeight?.toFloat() ?: 0f
            val footerHeight = options.footerHeight?.toFloat() ?: 0f
            val showPageNumbers = options.showPageNumbers ?: false
            val pageNumberFontSize = options.pageNumberFontSize?.toFloat() ?: 12f
            val pageNumberFormat = options.pageNumberFormat ?: "Page {page} of {total}"
            
            val totalPages = contentDoc.numberOfPages
            
            for ((pageIndex, contentPage) in contentDoc.pages.withIndex()) {
                val pageHeight = contentPage.mediaBox.height
                val pageWidth = contentPage.mediaBox.width
                val availableWidth = pageWidth - marginLeft - marginRight
                
                // Prepend header
                if (headerDoc != null) {
                    val headerPage = headerDoc.getPage(0)
                    val headerForm = layerUtility.importPageAsForm(headerDoc, 0)
                    val headerPageHeight = headerPage.mediaBox.height
                    
                    val stream = PDPageContentStream(contentDoc, contentPage, PDPageContentStream.AppendMode.PREPEND, true, true)
                    stream.saveGraphicsState()
                    
                    stream.addRect(marginLeft, pageHeight - marginTop - headerHeight, availableWidth, headerHeight)
                    stream.clip()
                    
                    val matrix = com.tom_roush.pdfbox.util.Matrix()
                    matrix.translate(marginLeft, pageHeight - marginTop - headerPageHeight)
                    stream.transform(matrix)
                    stream.drawForm(headerForm)
                    
                    stream.restoreGraphicsState()
                    stream.close()
                }
                
                // Prepend footer
                if (footerDoc != null) {
                    val footerPage = footerDoc.getPage(0)
                    val footerForm = layerUtility.importPageAsForm(footerDoc, 0)
                    
                    val stream = PDPageContentStream(contentDoc, contentPage, PDPageContentStream.AppendMode.PREPEND, true, true)
                    stream.saveGraphicsState()
                    
                    stream.addRect(marginLeft, marginBottom, availableWidth, footerHeight)
                    stream.clip()
                    
                    val matrix = com.tom_roush.pdfbox.util.Matrix()
                    matrix.translate(marginLeft, marginBottom)
                    stream.transform(matrix)
                    stream.drawForm(footerForm)
                    
                    stream.restoreGraphicsState()
                    stream.close()
                }
                
                // Append page numbers
                if (showPageNumbers) {
                    val stream = PDPageContentStream(contentDoc, contentPage, PDPageContentStream.AppendMode.APPEND, true, true)
                    val currentPage = pageIndex + 1
                    var pageText = pageNumberFormat
                    pageText = pageText.replace("{page}", currentPage.toString())
                    pageText = pageText.replace("{total}", totalPages.toString())
                    stream.beginText()
                    stream.setFont(com.tom_roush.pdfbox.pdmodel.font.PDType1Font.HELVETICA, pageNumberFontSize)
                    val textWidth = com.tom_roush.pdfbox.pdmodel.font.PDType1Font.HELVETICA.getStringWidth(pageText) / 1000 * pageNumberFontSize
                    
                    val xPos = (pageWidth - textWidth) / 2
                    val yPos = footerHeight
                    
                    stream.newLineAtOffset(xPos, yPos)
                    stream.showText(pageText)
                    stream.endText()
                    stream.close()
                }
            }
            
            contentDoc.save(outputFile)
        } finally {
            contentDoc?.close()
            headerDoc?.close()
            footerDoc?.close()
        }
    }
    
    private fun wrapHtml(html: String): String {
        return """<!DOCTYPE html>
<html>
<head>
    <meta charset='UTF-8'>
    <meta name='viewport' content='width=device-width, initial-scale=1.0'>
    <style>
        * { margin: 0; padding: 0; box-sizing: border-box; }
        html, body { 
            margin: 0; 
            padding: 0; 
            width: 100%;
            height: 100%;
        }
        body > * {
            width: 100%;
        }
    </style>
</head>
<body>$html</body>
</html>"""
    }

    private fun createWebView(width: Int, height: Int): WebView {
        return WebView(context).apply {
            settings.javaScriptEnabled = false
            settings.domStorageEnabled = true
            measure(
                android.view.View.MeasureSpec.makeMeasureSpec(width, android.view.View.MeasureSpec.EXACTLY),
                android.view.View.MeasureSpec.makeMeasureSpec(height, android.view.View.MeasureSpec.EXACTLY)
            )
            layout(0, 0, width, height)
        }
    }
    

    private fun getPageSize(size: String): PDRectangle {
        return when (size) {
            "A4" -> PDRectangle.A4
            "LETTER" -> PDRectangle.LETTER
            "LEGAL" -> PDRectangle.LEGAL
            "A3" -> PDRectangle.A3
            "A5" -> PDRectangle.A5
            else -> PDRectangle.A4
        }
    }

    private fun buildFullHtml(options: PdfOptions): String {
        val marginTop = options.marginTop?.toInt() ?: 0
        val marginBottom = options.marginBottom?.toInt() ?: 0
        val marginLeft = options.marginLeft?.toInt() ?: 0
        val marginRight = options.marginRight?.toInt() ?: 0
        
        val hasHeader = !options.header.isNullOrEmpty()
        val hasFooter = !options.footer.isNullOrEmpty()
        val headerHeight = if (hasHeader) (options.headerHeight?.toInt() ?: 0) else 0
        val footerHeight = if (hasFooter) (options.footerHeight?.toInt() ?: 0) else 0

        // Match iOS logic exactly:
        // top margin = marginTop + headerHeight
        // bottom margin = marginBottom + footerHeight + (showPageNumbers ? 20 : 0)
        val totalMarginTop = marginTop + headerHeight
        val totalMarginBottom = marginBottom + footerHeight + if (options.showPageNumbers == true) 20 else 0

        return """
            <!DOCTYPE html>
            <html>
            <head>
                <meta charset="UTF-8">
                <style>
                    @page {
                        margin: ${totalMarginTop}pt ${marginRight}pt ${totalMarginBottom}pt ${marginLeft}pt;
                        size: auto;
                    }
                    html, body { 
                        margin: 0;
                        padding: 0;
                        width: 100%;
                        height: auto;
                    }
                </style>
            </head>
            <body>
                ${options.html}
            </body>
            </html>
        """.trimIndent()
    }

    private fun buildPrintAttributes(options: PdfOptions): android.print.PrintAttributes {
        val builder = android.print.PrintAttributes.Builder()
        builder.setMediaSize(getMediaSize(options.pageSize?.name ?: "A4"))
        builder.setResolution(android.print.PrintAttributes.Resolution("pdf", "pdf", 600, 600))
        builder.setMinMargins(android.print.PrintAttributes.Margins.NO_MARGINS)
        return builder.build()
    }

    private fun getMediaSize(size: String): android.print.PrintAttributes.MediaSize {
        return when (size) {
            "A4" -> android.print.PrintAttributes.MediaSize.ISO_A4
            "LETTER" -> android.print.PrintAttributes.MediaSize.NA_LETTER
            "LEGAL" -> android.print.PrintAttributes.MediaSize.NA_LEGAL
            "A3" -> android.print.PrintAttributes.MediaSize.ISO_A3
            "A5" -> android.print.PrintAttributes.MediaSize.ISO_A5
            else -> android.print.PrintAttributes.MediaSize.ISO_A4
        }
    }
}
