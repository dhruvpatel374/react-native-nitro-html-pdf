package android.print

import android.content.Context
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.ParcelFileDescriptor
import android.util.Log
import android.webkit.WebView
import android.webkit.WebViewClient
import com.tom_roush.pdfbox.pdmodel.PDDocument
import java.io.File

class PdfConverter private constructor() : Runnable {

    companion object {
        private const val TAG = "PdfConverter"
        
        @Volatile
        private var sInstance: PdfConverter? = null
        
        @JvmStatic
        fun getInstance(): PdfConverter {
            return sInstance ?: synchronized(this) {
                sInstance ?: PdfConverter().also { sInstance = it }
            }
        }
    }

    interface ConversionCallback {
        fun onSuccess(filePath: String)
        fun onFailure(error: String)
    }

    private var mContext: Context? = null
    private var mHtmlString: String? = null
    private var mPdfFile: File? = null
    private var mPdfPrintAttrs: PrintAttributes? = null
    private var mIsCurrentlyConverting = false
    private var mWebView: WebView? = null
    private var mCallback: ConversionCallback? = null
    private var mBaseURL: String? = null
    private var mTimeoutHandler: Handler? = null
    private var mTimeoutRunnable: Runnable? = null
    private val CONVERSION_TIMEOUT_MS = 30000L

    override fun run() {
        try {
            mContext?.let { context ->
                mWebView = WebView(context).apply {
                    webViewClient = object : WebViewClient() {
                        override fun onPageFinished(view: WebView?, url: String?) {
                            try {
                                if (Build.VERSION.SDK_INT < Build.VERSION_CODES.KITKAT) {
                                    throw RuntimeException("call requires API level 19")
                                } else {
                                    val documentAdapter = createPrintDocumentAdapter()
                                    documentAdapter.onLayout(
                                        null, 
                                        getPdfPrintAttrs(), 
                                        null, 
                                        object : PrintDocumentAdapter.LayoutResultCallback() {
                                            override fun onLayoutFailed(error: CharSequence?) {
                                                Log.e(TAG, "PDF layout failed: $error")
                                                mCallback?.onFailure(error?.toString() ?: "Layout failed")
                                                destroy()
                                            }
                                        },
                                        null
                                    )
                                    
                                    documentAdapter.onWrite(
                                        arrayOf(PageRange.ALL_PAGES),
                                        getOutputFileDescriptor(),
                                        null,
                                        object : PrintDocumentAdapter.WriteResultCallback() {
                                            override fun onWriteFinished(pages: Array<PageRange>?) {
                                                try {
                                                    mPdfFile?.let { file ->
                                                        val myDocument = PDDocument.load(file)
                                                        myDocument.close()
                                                        mCallback?.onSuccess(file.absolutePath)
                                                    }
                                                } catch (e: Exception) {
                                                    Log.e(TAG, "Error finishing PDF write", e)
                                                    mCallback?.onFailure(e.message ?: "Write error")
                                                } finally {
                                                    mIsCurrentlyConverting = false
                                                    destroy()
                                                }
                                            }

                                            override fun onWriteFailed(error: CharSequence?) {
                                                val errorResult = error?.toString() ?: "Write failed"
                                                Log.e(TAG, "PDF write failed: $errorResult")
                                                mCallback?.onFailure(errorResult)
                                                mIsCurrentlyConverting = false
                                                destroy()
                                            }

                                            override fun onWriteCancelled() {
                                                Log.d(TAG, "PDF write cancelled")
                                                mCallback?.onFailure("PDF generation was cancelled")
                                                mIsCurrentlyConverting = false
                                                destroy()
                                            }
                                        }
                                    )
                                }
                            } catch (e: Exception) {
                                Log.e(TAG, "Error in onPageFinished", e)
                                mCallback?.onFailure("Error processing loaded page: ${e.message}")
                                mIsCurrentlyConverting = false
                                destroy()
                            }
                        }

                        override fun onReceivedError(view: WebView?, errorCode: Int, description: String?, failingUrl: String?) {
                            Log.e(TAG, "WebView error: $description (code: $errorCode)")
                            mCallback?.onFailure("WebView error: $description")
                            mIsCurrentlyConverting = false
                            destroy()
                        }
                    }

                    settings.apply {
                        textZoom = 100
                        defaultTextEncodingName = "utf-8"
                        allowFileAccess = true
                        javaScriptEnabled = true
                    }

                    mHtmlString?.let { html ->
                        loadDataWithBaseURL(mBaseURL, html, "text/HTML", "utf-8", null)
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error in run method", e)
            mCallback?.onFailure("Failed to setup WebView for PDF conversion: ${e.message}")
            mIsCurrentlyConverting = false
            destroy()
        }
    }

    fun getPdfPrintAttrs(): PrintAttributes? {
        return mPdfPrintAttrs ?: getDefaultPrintAttrs()
    }

    fun setPdfPrintAttrs(printAttrs: PrintAttributes?) {
        mPdfPrintAttrs = printAttrs
    }
    
    /**
     * Force reset the converter state. Use this if the converter gets stuck.
     */
    fun forceReset() {
        Log.w(TAG, "Force resetting PdfConverter state")
        destroy()
    }
    
    /**
     * Get current conversion state for debugging
     */
    fun isCurrentlyConverting(): Boolean {
        return mIsCurrentlyConverting
    }

    fun convert(
        context: Context?,
        htmlString: String?,
        file: File?,
        shouldEncode: Boolean,
        callback: ConversionCallback?,
        baseURL: String?
    ) {
        if (context == null) throw Exception("context can't be null")
        if (htmlString == null) throw Exception("htmlString can't be null")
        if (file == null) throw Exception("file can't be null")

        synchronized(this) {
            if (mIsCurrentlyConverting) {
                Log.w(TAG, "PDF conversion already in progress, waiting...")
                Thread {
                    var waited = 0
                    while (mIsCurrentlyConverting && waited < 30000) {
                        Thread.sleep(100)
                        waited += 100
                    }
                    if (mIsCurrentlyConverting) {
                        callback?.onFailure("Previous conversion did not complete")
                        return@Thread
                    }
                    convert(context, htmlString, file, shouldEncode, callback, baseURL)
                }.start()
                return
            }

            Log.d(TAG, "Starting PDF conversion for file: ${file.absolutePath}")

            try {
                mContext = context
                mHtmlString = htmlString
                mPdfFile = file
                mIsCurrentlyConverting = true
                mCallback = callback
                mBaseURL = baseURL
                
                setupTimeout()
                runOnUiThread(this)
            } catch (e: Exception) {
                Log.e(TAG, "Error setting up PDF conversion", e)
                mIsCurrentlyConverting = false
                cancelTimeout()
                callback?.onFailure("Failed to setup PDF conversion: ${e.message}")
            }
        }
    }

    private fun getOutputFileDescriptor(): ParcelFileDescriptor? {
        return try {
            mPdfFile?.let { file ->
                file.createNewFile()
                ParcelFileDescriptor.open(
                    file,
                    ParcelFileDescriptor.MODE_TRUNCATE or ParcelFileDescriptor.MODE_READ_WRITE
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to open ParcelFileDescriptor", e)
            null
        }
    }

    private fun getDefaultPrintAttrs(): PrintAttributes? {
        return if (Build.VERSION.SDK_INT < Build.VERSION_CODES.KITKAT) {
            null
        } else {
            PrintAttributes.Builder()
                .setMediaSize(PrintAttributes.MediaSize.NA_LETTER)
                .setResolution(
                    PrintAttributes.Resolution("RESOLUTION_ID", "RESOLUTION_ID", 600, 600)
                )
                .setMinMargins(PrintAttributes.Margins.NO_MARGINS)
                .build()
        }
    }

    private fun runOnUiThread(runnable: Runnable) {
        mContext?.let { context ->
            val handler = Handler(context.mainLooper)
            handler.post(runnable)
        }
    }

    private fun setupTimeout() {
        cancelTimeout()
        mTimeoutHandler = Handler(Looper.getMainLooper())
        mTimeoutRunnable = Runnable {
            Log.w(TAG, "PDF conversion timed out after ${CONVERSION_TIMEOUT_MS}ms")
            mCallback?.onFailure("PDF conversion timed out")
            destroy()
        }
        mTimeoutHandler?.postDelayed(mTimeoutRunnable!!, CONVERSION_TIMEOUT_MS)
    }
    
    private fun cancelTimeout() {
        mTimeoutRunnable?.let { runnable ->
            mTimeoutHandler?.removeCallbacks(runnable)
        }
        mTimeoutHandler = null
        mTimeoutRunnable = null
    }

    private fun destroy() {
        try {
            cancelTimeout()
            mWebView?.let { webView ->
                try {
                    webView.stopLoading()
                    webView.destroy()
                } catch (e: Exception) {
                    Log.w(TAG, "Error destroying WebView", e)
                }
            }
            mContext = null
            mHtmlString = null
            mPdfFile = null
            mPdfPrintAttrs = null
            mWebView = null
            mCallback = null
            mBaseURL = null
            mIsCurrentlyConverting = false
        } catch (e: Exception) {
            Log.e(TAG, "Error in destroy() method", e)
            mIsCurrentlyConverting = false
        }
    }
}
