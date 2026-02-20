package com.margelo.nitro.nitrohtmlpdf

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.pdf.PdfDocument
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.webkit.WebView
import android.webkit.WebViewClient
import com.margelo.nitro.core.Promise
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

class NitroHtmlPdf : HybridNitroHtmlPdfSpec() {
    companion object {
        @JvmStatic
        var appContext: Context? = null
        private const val TAG = "NitroHtmlPdf"
    }
    
    private val context: Context
        get() = appContext ?: throw IllegalStateException("Context not initialized. Call NitroHtmlPdf.appContext = context first.")

    override fun generatePdf(options: PdfOptions): Promise<PdfResult> {
        return Promise.async {
            createPdf(options)
        }
    }

    private fun createPdf(options: PdfOptions): PdfResult {
        Log.d(TAG, "createPdf called")
        val latch = CountDownLatch(1)
        var result: PdfResult? = null

        Handler(Looper.getMainLooper()).post {
            try {
                val directory = options.directory ?: context.cacheDir?.absolutePath ?: "/data/local/tmp"
                val tempFile = File(directory, "temp_${options.fileName}") 
                val finalFile = File(directory, options.fileName)
                
                // First generate selectable text PDF
                val fullHtml = buildFullHtml(options)
                val printAttrs = buildPrintAttributes(options)
                
                val converter = android.print.PdfConverter.getInstance()
                converter.setPdfPrintAttrs(printAttrs)
                
                converter.convert(
                    context,
                    fullHtml,
                    tempFile,
                    false,
                    object : android.print.PdfConverter.ConversionCallback {
                        override fun onSuccess(filePath: String) {
                            Log.d(TAG, "Base PDF created, rendering header/footer...")
                            // Now render header/footer as Canvas overlays
                            renderHeaderFooter(tempFile, finalFile, options, latch) { success, error ->
                                if (success) {
                                    tempFile.delete()
                                    result = PdfResult(finalFile.absolutePath, true, null)
                                } else {
                                    result = PdfResult("", false, error)
                                }
                                latch.countDown()
                            }
                        }
                        
                        override fun onFailure(error: String) {
                            Log.e(TAG, "PDF creation failed: $error")
                            result = PdfResult("", false, error)
                            latch.countDown()
                        }
                    },
                    null
                )
            } catch (e: Exception) {
                Log.e(TAG, "Error in createPdf", e)
                result = PdfResult("", false, e.message)
                latch.countDown()
            }
        }

        latch.await(60, TimeUnit.SECONDS)
        return result ?: PdfResult("", false, "PDF creation timed out")
    }

    private fun renderHeaderFooter(inputFile: File, outputFile: File, options: PdfOptions, mainLatch: CountDownLatch, callback: (Boolean, String?) -> Unit) {
        val hasHeader = !options.header.isNullOrEmpty()
        val hasFooter = !options.footer.isNullOrEmpty()
        
        if (!hasHeader && !hasFooter) {
            inputFile.copyTo(outputFile, overwrite = true)
            callback(true, null)
            return
        }
        
        val headerHeight = if (hasHeader) (options.headerHeight?.toInt() ?: 60) else 0
        val footerHeight = if (hasFooter) (options.footerHeight?.toInt() ?: 60) else 0
        val pageWidth = 595 // A4 width
        
        val webViews = mutableListOf<WebView>()
        var headerWebView: WebView? = null
        var footerWebView: WebView? = null
        
        Handler(Looper.getMainLooper()).post {
            if (hasHeader) {
                headerWebView = createWebView(pageWidth, headerHeight)
                headerWebView!!.loadDataWithBaseURL(null, options.header, "text/html", "UTF-8", null)
                webViews.add(headerWebView!!)
            }
            
            if (hasFooter) {
                footerWebView = createWebView(pageWidth, footerHeight)
                footerWebView!!.loadDataWithBaseURL(null, options.footer, "text/html", "UTF-8", null)
                webViews.add(footerWebView!!)
            }
            
            val loadLatch = CountDownLatch(webViews.size)
            webViews.forEach { webView ->
                webView.webViewClient = object : WebViewClient() {
                    override fun onPageFinished(view: WebView?, url: String?) {
                        loadLatch.countDown()
                    }
                }
            }
            
            Thread {
                loadLatch.await(5, TimeUnit.SECONDS)
                Thread.sleep(300)
                
                try {
                    overlayHeaderFooter(inputFile, outputFile, options, headerWebView, footerWebView)
                    Handler(Looper.getMainLooper()).post {
                        webViews.forEach { it.destroy() }
                    }
                    callback(true, null)
                } catch (e: Exception) {
                    Log.e(TAG, "Error overlaying header/footer", e)
                    callback(false, e.message)
                }
            }.start()
        }
    }

    private fun overlayHeaderFooter(inputFile: File, outputFile: File, options: PdfOptions, headerWebView: WebView?, footerWebView: WebView?) {
        com.tom_roush.pdfbox.android.PDFBoxResourceLoader.init(context)
        val document = com.tom_roush.pdfbox.pdmodel.PDDocument.load(inputFile)
        
        val marginLeft = options.marginLeft?.toFloat() ?: 0f
        val marginTop = options.marginTop?.toFloat() ?: 0f
        val marginBottom = options.marginBottom?.toFloat() ?: 0f
        val headerHeight = options.headerHeight?.toFloat() ?: 60f
        val footerHeight = options.footerHeight?.toFloat() ?: 60f
        val showPageNumbers = options.showPageNumbers ?: false
        val pageNumberFontSize = options.pageNumberFontSize?.toFloat() ?: 12f
        val pageNumberFormat = options.pageNumberFormat ?: "Page {page} of {total}"
        
        val headerBitmap = headerWebView?.let { captureBitmap(it) }
        val footerBitmap = footerWebView?.let { captureBitmap(it) }
        
        val totalPages = document.numberOfPages
        
        for ((pageIndex, page) in document.pages.withIndex()) {
            // Draw header/footer behind content
            val bgStream = com.tom_roush.pdfbox.pdmodel.PDPageContentStream(
                document, page,
                com.tom_roush.pdfbox.pdmodel.PDPageContentStream.AppendMode.PREPEND,
                true, true
            )
            
            if (headerBitmap != null) {
                val pdImage = com.tom_roush.pdfbox.pdmodel.graphics.image.LosslessFactory.createFromImage(document, headerBitmap)
                val imageWidth = page.mediaBox.width - marginLeft * 2
                val imageHeight = headerHeight
                bgStream.drawImage(pdImage, marginLeft, page.mediaBox.height - imageHeight, imageWidth, imageHeight)
            }
            
            if (footerBitmap != null) {
                val pdImage = com.tom_roush.pdfbox.pdmodel.graphics.image.LosslessFactory.createFromImage(document, footerBitmap)
                val imageWidth = page.mediaBox.width - marginLeft * 2
                val imageHeight = footerHeight
                bgStream.drawImage(pdImage, marginLeft, 0f, imageWidth, imageHeight)
            }
            
            bgStream.close()
            
            // Draw page numbers on top of content
            if (showPageNumbers) {
                val fgStream = com.tom_roush.pdfbox.pdmodel.PDPageContentStream(
                    document, page,
                    com.tom_roush.pdfbox.pdmodel.PDPageContentStream.AppendMode.APPEND,
                    true, true
                )
                
                val currentPage = pageIndex + 1
                var pageText = pageNumberFormat
                pageText = pageText.replace("{page}", currentPage.toString())
                pageText = pageText.replace("{total}", totalPages.toString())
                
                fgStream.beginText()
                fgStream.setFont(com.tom_roush.pdfbox.pdmodel.font.PDType1Font.HELVETICA, pageNumberFontSize)
                val textWidth = com.tom_roush.pdfbox.pdmodel.font.PDType1Font.HELVETICA.getStringWidth(pageText) / 1000 * pageNumberFontSize
                val x = (page.mediaBox.width - textWidth) / 2
                val y = footerHeight + 5f
                fgStream.newLineAtOffset(x, y)
                fgStream.showText(pageText)
                fgStream.endText()
                
                fgStream.close()
            }
        }
        
        document.save(outputFile)
        document.close()
        
        headerBitmap?.recycle()
        footerBitmap?.recycle()
    }

    private fun captureBitmap(webView: WebView): Bitmap {
        val bitmap = Bitmap.createBitmap(webView.width, webView.height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        webView.draw(canvas)
        return bitmap
    }

    private fun createWebView(width: Int, height: Int): WebView {
        return WebView(context).apply {
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            measure(
                android.view.View.MeasureSpec.makeMeasureSpec(width, android.view.View.MeasureSpec.EXACTLY),
                android.view.View.MeasureSpec.makeMeasureSpec(height, android.view.View.MeasureSpec.EXACTLY)
            )
            layout(0, 0, width, height)
        }
    }

    private fun buildFullHtml(options: PdfOptions): String {
        val marginTop = options.marginTop?.toInt() ?: 0
        val marginBottom = options.marginBottom?.toInt() ?: 0
        val marginLeft = options.marginLeft?.toInt() ?: 0
        val marginRight = options.marginRight?.toInt() ?: 0
        
        val hasHeader = !options.header.isNullOrEmpty()
        val hasFooter = !options.footer.isNullOrEmpty()
        val headerHeight = if (hasHeader) (options.headerHeight?.toInt() ?: 60) else 0
        val footerHeight = if (hasFooter) (options.footerHeight?.toInt() ?: 60) else 0

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
                        margin: ${totalMarginTop}px ${marginRight}px ${totalMarginBottom}px ${marginLeft}px;
                    }
                    body { 
                        margin: 0;
                        padding: 0;
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
