package android.print;

import android.os.Bundle;
import android.os.CancellationSignal;
import android.os.ParcelFileDescriptor;
import android.util.Log;
import android.webkit.WebView;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;

public class PdfPrintHelper {
    private static final String TAG = "PdfPrintHelper";

    public interface PrintCallback {
        void onSuccess();
        void onFailure(String error);
    }

    public static void printWebViewToPdf(WebView webView, File outputFile, PrintAttributes printAttrs, PrintCallback callback) {
        try {
            Log.d(TAG, "Starting printWebViewToPdf for: " + outputFile.getName());
            PrintDocumentAdapter adapter = webView.createPrintDocumentAdapter("doc");
            ParcelFileDescriptor[] pipe = ParcelFileDescriptor.createPipe();
            ParcelFileDescriptor readFd = pipe[0];
            ParcelFileDescriptor writeFd = pipe[1];

            adapter.onLayout(null, printAttrs, null, new PrintDocumentAdapter.LayoutResultCallback() {
                @Override
                public void onLayoutFinished(PrintDocumentInfo info, boolean changed) {
                    Log.d(TAG, "Layout finished, pages: " + info.getPageCount());
                    adapter.onWrite(new PageRange[]{PageRange.ALL_PAGES}, writeFd, new CancellationSignal(),
                            new PrintDocumentAdapter.WriteResultCallback() {
                                @Override
                                public void onWriteFinished(PageRange[] pages) {
                                    try {
                                        Log.d(TAG, "Write finished, reading data...");
                                        writeFd.close();
                                        FileInputStream input = new FileInputStream(readFd.getFileDescriptor());
                                        FileOutputStream output = new FileOutputStream(outputFile);
                                        byte[] buffer = new byte[8192];
                                        int bytesRead;
                                        int totalBytes = 0;
                                        while ((bytesRead = input.read(buffer)) != -1) {
                                            output.write(buffer, 0, bytesRead);
                                            totalBytes += bytesRead;
                                        }
                                        output.flush();
                                        output.close();
                                        input.close();
                                        readFd.close();
                                        Log.d(TAG, "PDF written successfully: " + totalBytes + " bytes to " + outputFile.getName());
                                        callback.onSuccess();
                                    } catch (Exception e) {
                                        Log.e(TAG, "Error writing PDF", e);
                                        callback.onFailure(e.getMessage());
                                    }
                                }

                                @Override
                                public void onWriteFailed(CharSequence error) {
                                    Log.e(TAG, "Write failed: " + error);
                                    try {
                                        writeFd.close();
                                        readFd.close();
                                    } catch (Exception ignored) {}
                                    callback.onFailure(error != null ? error.toString() : "Write failed");
                                }
                            });
                }

                @Override
                public void onLayoutFailed(CharSequence error) {
                    Log.e(TAG, "Layout failed: " + error);
                    try {
                        writeFd.close();
                        readFd.close();
                    } catch (Exception ignored) {}
                    callback.onFailure(error != null ? error.toString() : "Layout failed");
                }
            }, new Bundle());
        } catch (Exception e) {
            Log.e(TAG, "Error in printWebViewToPdf", e);
            callback.onFailure(e.getMessage());
        }
    }
}
