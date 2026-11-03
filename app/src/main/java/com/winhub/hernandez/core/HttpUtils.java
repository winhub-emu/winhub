package com.winhub.hernandez.core;

import android.app.Activity;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

public abstract class HttpUtils {
    private static void downloadAsync(String url, Callback<String> onDownloadComplete) {
        try {
            HttpURLConnection connection = (HttpURLConnection)(new URL(url)).openConnection();
            // api.github.com rejects requests with no User-Agent (403).
            connection.setRequestProperty("User-Agent", "Bannerlator");
            if (connection.getResponseCode() != HttpURLConnection.HTTP_OK) {
                onDownloadComplete.call(null);
                return;
            }

            byte[] bytes;
            try (InputStream inStream = connection.getInputStream()) {
                bytes = StreamUtils.copyToByteArray(inStream);
            }
            onDownloadComplete.call(new String(bytes, StandardCharsets.UTF_8));
        }
        catch (Exception e) {
            onDownloadComplete.call(null);
        }
    }

    public static void download(final String url, final Callback<String> onDownloadComplete) {
        Executors.newSingleThreadExecutor().execute(() -> downloadAsync(url, onDownloadComplete));
    }

    private static void postAsync(String url, String jsonBody, Callback<String> onComplete) {
        try {
            HttpURLConnection connection = (HttpURLConnection)(new URL(url)).openConnection();
            connection.setRequestMethod("POST");
            connection.setRequestProperty("User-Agent", "Bannerlator");
            connection.setRequestProperty("Content-Type", "application/json");
            connection.setDoOutput(true);
            try (OutputStream outStream = connection.getOutputStream()) {
                outStream.write(jsonBody.getBytes(StandardCharsets.UTF_8));
            }

            int code = connection.getResponseCode();
            InputStream inStream = (code >= 200 && code < 300)
                    ? connection.getInputStream() : connection.getErrorStream();
            if (inStream == null) {
                onComplete.call(null);
                return;
            }
            byte[] bytes;
            try (InputStream in = inStream) {
                bytes = StreamUtils.copyToByteArray(in);
            }
            onComplete.call(code >= 200 && code < 300 ? new String(bytes, StandardCharsets.UTF_8) : null);
        }
        catch (Exception e) {
            onComplete.call(null);
        }
    }

    /** POST a JSON body (Content-Type: application/json); the callback receives the response body or null. */
    public static void post(final String url, final String jsonBody, final Callback<String> onComplete) {
        Executors.newSingleThreadExecutor().execute(() -> postAsync(url, jsonBody, onComplete));
    }

    /**
     * The HTTP status code plus the raw response body (input OR error stream) of a POST. Unlike
     * {@link #post}, the body is surfaced even on a non-2xx status so callers can read a JSON
     * {@code {error}} field (the account endpoints return typed error codes that way). [body] may be
     * null when the connection itself failed; [code] is then 0.
     */
    public static final class HttpResponse {
        public final int code;
        public final String body;
        public HttpResponse(int code, String body) {
            this.code = code;
            this.body = body;
        }
    }

    private static void postWithStatusAsync(String url, String jsonBody, Callback<HttpResponse> onComplete) {
        try {
            HttpURLConnection connection = (HttpURLConnection)(new URL(url)).openConnection();
            connection.setRequestMethod("POST");
            connection.setRequestProperty("User-Agent", "Bannerlator");
            connection.setRequestProperty("Content-Type", "application/json");
            connection.setDoOutput(true);
            try (OutputStream outStream = connection.getOutputStream()) {
                outStream.write(jsonBody.getBytes(StandardCharsets.UTF_8));
            }

            int code = connection.getResponseCode();
            InputStream inStream = (code >= 200 && code < 300)
                    ? connection.getInputStream() : connection.getErrorStream();
            String body = null;
            if (inStream != null) {
                try (InputStream in = inStream) {
                    body = new String(StreamUtils.copyToByteArray(in), StandardCharsets.UTF_8);
                }
            }
            onComplete.call(new HttpResponse(code, body));
        }
        catch (Exception e) {
            onComplete.call(new HttpResponse(0, null));
        }
    }

    /**
     * POST a JSON body and hand back BOTH the status code and the response body (2xx or not), so the
     * caller can read a typed {@code {error}} on a rejection. Runs off the calling thread.
     */
    public static void postWithStatus(final String url, final String jsonBody, final Callback<HttpResponse> onComplete) {
        Executors.newSingleThreadExecutor().execute(() -> postWithStatusAsync(url, jsonBody, onComplete));
    }

    private static void downloadAsync(String url, File destination, AtomicBoolean interruptRef, Callback<Integer> onPublishProgress, Callback<Boolean> onDownloadComplete) {
        try {
            interruptRef.set(false);
            HttpURLConnection connection = (HttpURLConnection)(new URL(url)).openConnection();
            if (connection.getResponseCode() != HttpURLConnection.HTTP_OK) {
                onDownloadComplete.call(false);
                return;
            }

            int contentLength = connection.getContentLength();
            try (InputStream inStream = new BufferedInputStream(connection.getInputStream(), StreamUtils.BUFFER_SIZE);
                 OutputStream outStream = new FileOutputStream(destination)) {

                byte[] buffer = new byte[1024];
                int totalSize = 0;
                int bytesRead;
                while ((bytesRead = inStream.read(buffer)) != -1 && !interruptRef.get()) {
                    totalSize += bytesRead;
                    if (onPublishProgress != null) {
                        int progress = (int)(((float)totalSize / contentLength) * 100);
                        onPublishProgress.call(progress);
                    }
                    outStream.write(buffer, 0, bytesRead);
                }

            }

            onDownloadComplete.call(!interruptRef.get());
        }
        catch (Exception e) {
            onDownloadComplete.call(false);
        }
    }

    /** Byte-level progress for {@link #downloadToFile}: {@code total} is -1 when the server sent no length. */
    public interface ProgressListener {
        void onProgress(long bytes, long total);
    }

    /**
     * Download {@code url} to {@code destination} with no UI of its own. Progress is reported in
     * bytes on the download thread; {@code onComplete} gets true only when the whole body was
     * written and nobody set {@code interruptRef}. A failed or cancelled download leaves no
     * partial file behind. Follows one more redirect by hand for the cases HttpURLConnection
     * refuses to follow itself (a scheme change on the way to the asset host).
     */
    public static void downloadToFile(final String url, final File destination, final AtomicBoolean interruptRef,
                                      final ProgressListener onProgress, final Callback<Boolean> onComplete) {
        Executors.newSingleThreadExecutor().execute(() -> {
            boolean ok = false;
            try {
                interruptRef.set(false);
                HttpURLConnection connection = (HttpURLConnection)(new URL(url)).openConnection();
                connection.setRequestProperty("User-Agent", "Bannerlator");
                int code = connection.getResponseCode();
                if (code / 100 == 3) {
                    String next = connection.getHeaderField("Location");
                    connection.disconnect();
                    if (next == null) { onComplete.call(false); return; }
                    connection = (HttpURLConnection)(new URL(next)).openConnection();
                    connection.setRequestProperty("User-Agent", "Bannerlator");
                    code = connection.getResponseCode();
                }
                if (code != HttpURLConnection.HTTP_OK) {
                    onComplete.call(false);
                    return;
                }
                long total = connection.getContentLengthLong();
                try (InputStream inStream = new BufferedInputStream(connection.getInputStream(), StreamUtils.BUFFER_SIZE);
                     OutputStream outStream = new FileOutputStream(destination)) {
                    byte[] buffer = new byte[64 * 1024];
                    long done = 0;
                    long lastReport = 0;
                    int n;
                    while ((n = inStream.read(buffer)) != -1 && !interruptRef.get()) {
                        outStream.write(buffer, 0, n);
                        done += n;
                        long now = System.currentTimeMillis();
                        if (onProgress != null && (now - lastReport >= 100 || done == total)) {
                            lastReport = now;
                            onProgress.onProgress(done, total);
                        }
                    }
                    ok = !interruptRef.get() && (total < 0 || done == total);
                    if (ok && onProgress != null) onProgress.onProgress(done, total < 0 ? done : total);
                }
            }
            catch (Exception e) {
                ok = false;
            }
            if (!ok && destination.isFile()) destination.delete();
            onComplete.call(ok);
        });
    }

    public static void download(final Activity activity, final String url, final File destination, final Callback<Boolean> onDownloadComplete) {
        final DownloadProgressDialog dialog = new DownloadProgressDialog(activity);
        final AtomicBoolean interruptRef = new AtomicBoolean();
        dialog.show(() -> interruptRef.set(true));
        Executors.newSingleThreadExecutor().execute(() -> {
            downloadAsync(url, destination, interruptRef, (progress) -> {
                activity.runOnUiThread(() -> {
                    dialog.setProgress(progress);
                });
            }, (success) -> {
                if (!success && destination.isFile()) destination.delete();
                activity.runOnUiThread(() -> {
                    dialog.close();
                    onDownloadComplete.call(success);
                });
            });
        });
    }
}
