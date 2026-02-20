package android.print;

import android.os.Bundle;
import android.os.CancellationSignal;
import android.os.Handler;
import android.os.Looper;
import android.os.ParcelFileDescriptor;
import android.util.Log;
import android.webkit.WebView;
import java.io.File;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

public class PdfPrinter {
    private static final String TAG = "PdfPrinter";

    public static String print(WebView webView, File file, PrintAttributes attributes) throws Exception {
        Log.d(TAG, "Starting PDF generation for: " + file.getAbsolutePath());
        PrintDocumentAdapter adapter = webView.createPrintDocumentAdapter(file.getName());
        CountDownLatch latch = new CountDownLatch(1);
        String[] error = new String[1];
        Handler handler = new Handler(Looper.getMainLooper());

        Log.d(TAG, "Calling onLayout...");
        handler.post(() -> {
            adapter.onLayout(null, attributes, null, new PrintDocumentAdapter.LayoutResultCallback() {
                @Override
                public void onLayoutFinished(PrintDocumentInfo info, boolean changed) {
                    Log.d(TAG, "Layout finished. Pages: " + info.getPageCount());
                    handler.post(() -> {
                        try {
                            ParcelFileDescriptor pfd = ParcelFileDescriptor.open(file,
                                    ParcelFileDescriptor.MODE_READ_WRITE | ParcelFileDescriptor.MODE_CREATE);
                            Log.d(TAG, "File descriptor opened, calling onWrite...");

                            adapter.onWrite(new PageRange[]{PageRange.ALL_PAGES}, pfd, new CancellationSignal(),
                                    new PrintDocumentAdapter.WriteResultCallback() {
                                        @Override
                                        public void onWriteFinished(PageRange[] pages) {
                                            Log.d(TAG, "Write finished successfully. Pages written: " + pages.length);
                                            try {
                                                pfd.close();
                                            } catch (Exception e) {
                                                Log.e(TAG, "Error closing file descriptor", e);
                                                error[0] = e.getMessage();
                                            }
                                            latch.countDown();
                                        }

                                        @Override
                                        public void onWriteFailed(CharSequence errorMsg) {
                                            Log.e(TAG, "Write failed: " + errorMsg);
                                            error[0] = errorMsg != null ? errorMsg.toString() : "Write failed";
                                            try {
                                                pfd.close();
                                            } catch (Exception ignored) {}
                                            latch.countDown();
                                        }
                                    });
                        } catch (Exception e) {
                            Log.e(TAG, "Error in onLayoutFinished", e);
                            error[0] = e.getMessage();
                            latch.countDown();
                        }
                    });
                }

                @Override
                public void onLayoutFailed(CharSequence errorMsg) {
                    Log.e(TAG, "Layout failed: " + errorMsg);
                    error[0] = errorMsg != null ? errorMsg.toString() : "Layout failed";
                    latch.countDown();
                }
            }, new Bundle());
        });

        Log.d(TAG, "Waiting for PDF generation to complete...");
        latch.await(30, TimeUnit.SECONDS);
        
        if (error[0] != null) {
            Log.e(TAG, "PDF generation failed: " + error[0]);
        } else {
            Log.d(TAG, "PDF generated successfully at: " + file.getAbsolutePath());
        }
        
        return error[0];
    }
}
