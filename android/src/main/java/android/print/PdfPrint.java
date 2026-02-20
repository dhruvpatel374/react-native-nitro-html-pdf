package android.print;

import android.os.Bundle;
import android.os.CancellationSignal;
import android.os.ParcelFileDescriptor;
import android.util.Log;
import java.io.FileOutputStream;
import java.io.InputStream;

public class PdfPrint {
    private static final String TAG = "PdfPrint";
    private final PrintAttributes printAttributes;

    public PdfPrint(PrintAttributes attributes) {
        this.printAttributes = attributes;
    }

    public interface PrintCallback {
        void onSuccess();
        void onFailure(String error);
    }

    public void print(PrintDocumentAdapter adapter, FileOutputStream output, PrintCallback callback) {
        try {
            Log.d(TAG, "Creating pipe...");
            ParcelFileDescriptor[] pipe = ParcelFileDescriptor.createPipe();
            ParcelFileDescriptor readFd = pipe[0];
            ParcelFileDescriptor writeFd = pipe[1];

            Log.d(TAG, "Calling onLayout...");
            adapter.onLayout(null, printAttributes, null, new PrintDocumentAdapter.LayoutResultCallback() {
                @Override
                public void onLayoutFinished(PrintDocumentInfo info, boolean changed) {
                    Log.d(TAG, "onLayoutFinished - Pages: " + info.getPageCount());
                    Log.d(TAG, "Calling onWrite...");
                    adapter.onWrite(new PageRange[]{PageRange.ALL_PAGES}, writeFd, new CancellationSignal(),
                            new PrintDocumentAdapter.WriteResultCallback() {
                                @Override
                                public void onWriteFinished(PageRange[] pages) {
                                    Log.d(TAG, "onWriteFinished - Pages: " + pages.length);
                                    try {
                                        writeFd.close();
                                        Log.d(TAG, "Reading from pipe...");
                                        InputStream input = new ParcelFileDescriptor.AutoCloseInputStream(readFd);
                                        byte[] buffer = new byte[8192];
                                        int bytesRead;
                                        int totalBytes = 0;
                                        while ((bytesRead = input.read(buffer)) != -1) {
                                            output.write(buffer, 0, bytesRead);
                                            totalBytes += bytesRead;
                                        }
                                        Log.d(TAG, "Wrote " + totalBytes + " bytes");
                                        output.flush();
                                        output.close();
                                        input.close();
                                        Log.d(TAG, "PDF write successful");
                                        callback.onSuccess();
                                    } catch (Exception e) {
                                        Log.e(TAG, "Error in onWriteFinished", e);
                                        callback.onFailure(e.getMessage());
                                    }
                                }

                                @Override
                                public void onWriteFailed(CharSequence error) {
                                    Log.e(TAG, "onWriteFailed: " + error);
                                    try {
                                        writeFd.close();
                                        readFd.close();
                                        output.close();
                                    } catch (Exception ignored) {}
                                    callback.onFailure(error != null ? error.toString() : "Write failed");
                                }
                            });
                }

                @Override
                public void onLayoutFailed(CharSequence error) {
                    Log.e(TAG, "onLayoutFailed: " + error);
                    try {
                        writeFd.close();
                        readFd.close();
                        output.close();
                    } catch (Exception ignored) {}
                    callback.onFailure(error != null ? error.toString() : "Layout failed");
                }
            }, new Bundle());
        } catch (Exception e) {
            Log.e(TAG, "Exception in print", e);
            callback.onFailure(e.getMessage());
        }
    }
}
