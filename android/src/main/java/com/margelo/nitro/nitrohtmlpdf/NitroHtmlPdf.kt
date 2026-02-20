package com.margelo.nitro.nitrohtmlpdf

import android.content.Context
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import android.os.Handler
import android.os.Looper
import android.webkit.WebView
import android.webkit.WebViewClient
import com.margelo.nitro.core.Promise
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.math.ceil

class NitroHtmlPdf : HybridNitroHtmlPdfSpec() {
    companion object {
        @JvmStatic
        var appContext: Context? = null
    }
    
    private val context: Context
        get() = appContext ?: throw IllegalStateException("Context not initialized. Call NitroHtmlPdf.appContext = context first.")

    override fun generatePdf(options: PdfOptions): Promise<PdfResult> {
        return Promise.async {
            createPdf(options)
        }
    }

    private fun createPdf(options: PdfOptions): PdfResult {
        val latch = CountDownLatch(1)
        var result: PdfResult? = null

        Handler(Looper.getMainLooper()).post {
            try {
                val pageSizeString = options.pageSize?.name ?: "A4"
                val pageSize = getPageSize(pageSizeString, options.width, options.height)
                val headerHeight = options.headerHeight?.toFloat() ?: 0f
                val footerHeight = options.footerHeight?.toFloat() ?: 0f

                var headerWebView: WebView? = null
                var footerWebView: WebView? = null
                val webViews = mutableListOf<WebView>()

                if (!options.header.isNullOrEmpty() && headerHeight > 0) {
                    headerWebView = createWebView(pageSize.width.toInt(), headerHeight.toInt())
                    val wrappedHeader = "<!DOCTYPE html><html><head><meta name='viewport' content='width=device-width,initial-scale=1,maximum-scale=1'><style>*{margin:0!important;padding:0!important;box-sizing:border-box;}html,body{margin:0!important;padding:0!important;width:100%;height:100%;overflow:hidden;}</style></head><body>${options.header}</body></html>"
                    headerWebView.loadDataWithBaseURL(null, wrappedHeader, "text/html", "UTF-8", null)
                    webViews.add(headerWebView)
                }

                if (!options.footer.isNullOrEmpty() && footerHeight > 0) {
                    footerWebView = createWebView(pageSize.width.toInt(), footerHeight.toInt())
                    val wrappedFooter = "<!DOCTYPE html><html><head><meta name='viewport' content='width=device-width,initial-scale=1,maximum-scale=1'><style>*{margin:0!important;padding:0!important;box-sizing:border-box;}html,body{margin:0!important;padding:0!important;width:100%;height:100%;overflow:hidden;}</style></head><body>${options.footer}</body></html>"
                    footerWebView.loadDataWithBaseURL(null, wrappedFooter, "text/html", "UTF-8", null)
                    webViews.add(footerWebView)
                }

                val contentWebView = createWebView(pageSize.width.toInt(), pageSize.height.toInt())
                contentWebView.loadDataWithBaseURL(null, options.html, "text/html", "UTF-8", null)
                webViews.add(contentWebView)

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
                    Thread.sleep(500)
                    Handler(Looper.getMainLooper()).post {
                        result = renderPdf(contentWebView, headerWebView, footerWebView, options)
                        webViews.forEach { it.destroy() }
                        latch.countDown()
                    }
                }.start()

            } catch (e: Exception) {
                result = PdfResult("", false, e.message)
                latch.countDown()
            }
        }

        latch.await()
        return result ?: PdfResult("", false, "Failed to create PDF")
    }

    private fun createWebView(width: Int, height: Int): WebView {
        return WebView(context).apply {
            settings.javaScriptEnabled = true
            settings.loadWithOverviewMode = false
            settings.useWideViewPort = false
            settings.domStorageEnabled = true
            settings.setSupportZoom(false)
            setInitialScale(100)
            measure(
                android.view.View.MeasureSpec.makeMeasureSpec(width, android.view.View.MeasureSpec.EXACTLY),
                android.view.View.MeasureSpec.makeMeasureSpec(height, android.view.View.MeasureSpec.EXACTLY)
            )
            layout(0, 0, width, height)
        }
    }

    private fun renderPdf(
        webView: WebView,
        headerWebView: WebView?,
        footerWebView: WebView?,
        options: PdfOptions
    ): PdfResult {
        return try {
            val pageSizeString = options.pageSize?.name ?: "A4"
            val pageSize = getPageSize(pageSizeString, options.width, options.height)
            val headerHeight = options.headerHeight?.toFloat() ?: 0f
            val footerHeight = options.footerHeight?.toFloat() ?: 0f
            val marginTop = (options.marginTop?.toFloat() ?: 0f) + headerHeight
            val marginBottom = (options.marginBottom?.toFloat() ?: 0f) + footerHeight + if (options.showPageNumbers == true) 20f else 0f
            val marginLeft = options.marginLeft?.toFloat() ?: 0f
            val marginRight = options.marginRight?.toFloat() ?: 0f

            webView.measure(
                android.view.View.MeasureSpec.makeMeasureSpec(pageSize.width.toInt(), android.view.View.MeasureSpec.EXACTLY),
                android.view.View.MeasureSpec.makeMeasureSpec(0, android.view.View.MeasureSpec.UNSPECIFIED)
            )
            webView.layout(0, 0, pageSize.width.toInt(), webView.measuredHeight)
            
            val contentHeight = webView.measuredHeight.toFloat()
            val printableHeight = pageSize.height - marginTop - marginBottom
            val totalPages = ceil(contentHeight / printableHeight).toInt().coerceAtLeast(1)

            val pdfDocument = PdfDocument()

            for (pageIndex in 0 until totalPages) {
                val pageInfo = PdfDocument.PageInfo.Builder(
                    pageSize.width.toInt(),
                    pageSize.height.toInt(),
                    pageIndex
                ).create()

                val page = pdfDocument.startPage(pageInfo)
                val canvas = page.canvas

                canvas.save()
                canvas.translate(marginLeft, marginTop)
                canvas.clipRect(0f, 0f, pageSize.width - marginLeft - marginRight, printableHeight)
                canvas.translate(0f, -(pageIndex * printableHeight))
                webView.draw(canvas)
                canvas.restore()

                if (headerWebView != null && headerHeight > 0) {
                    canvas.save()
                    canvas.translate(marginLeft, 0f)
                    headerWebView.draw(canvas)
                    canvas.restore()
                }

                if (options.showPageNumbers == true) {
                    val currentPage = pageIndex + 1
                    var pageText = options.pageNumberFormat ?: "Page {page} of {total}"
                    pageText = pageText.replace("{page}", currentPage.toString())
                    pageText = pageText.replace("{total}", totalPages.toString())

                    val paint = Paint().apply {
                        textSize = options.pageNumberFontSize?.toFloat() ?: 12f
                        color = android.graphics.Color.BLACK
                        textAlign = Paint.Align.CENTER
                    }
                    val x = pageSize.width / 2
                    val y = pageSize.height - footerHeight - 5f
                    canvas.drawText(pageText, x, y, paint)
                }

                if (footerWebView != null && footerHeight > 0) {
                    canvas.save()
                    canvas.translate(marginLeft, pageSize.height - footerHeight)
                    footerWebView.draw(canvas)
                    canvas.restore()
                }

                pdfDocument.finishPage(page)
            }

            val directory = options.directory ?: context.cacheDir?.absolutePath ?: "/data/local/tmp"
            val file = File(directory, options.fileName)
            FileOutputStream(file).use { pdfDocument.writeTo(it) }
            pdfDocument.close()

            PdfResult(file.absolutePath, true, null)
        } catch (e: Exception) {
            PdfResult("", false, e.message)
        }
    }

    private fun getPageSize(size: String, width: Double?, height: Double?): Size {
        if (width != null && height != null) {
            return Size(width.toFloat(), height.toFloat())
        }

        return when (size) {
            "A4" -> Size(595f, 842f)
            "LETTER" -> Size(612f, 792f)
            "LEGAL" -> Size(612f, 1008f)
            "A3" -> Size(842f, 1191f)
            "A5" -> Size(420f, 595f)
            else -> Size(595f, 842f)
        }
    }

    private data class Size(val width: Float, val height: Float)
}
