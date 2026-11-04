package com.winhub.hernandez.core;

import com.winhub.hernandez.contents.Downloader;

import java.io.File;
import java.io.IOException;

/**
 * Downloaded game-mod DLLs (core.GameMods), kept in the app's files dir under their sha256 so a release
 * file is fetched once and reused for every game. A file is only kept after its size and sha256 match.
 */
public final class GameModCache {
    private GameModCache() {}

    static File dir(File filesDir) {
        return new File(filesDir, "mods");
    }

    static File file(File filesDir, GameMods.Asset asset) {
        return new File(dir(filesDir), asset.sha256 + ".dll");
    }

    /** The verified local copy of {@code asset}, or null when it has not been downloaded. */
    public static File cached(File filesDir, GameMods.Asset asset) {
        File f = file(filesDir, asset);
        return f.isFile() && f.length() == asset.size ? f : null;
    }

    /**
     * {@code asset}, downloaded first when it is not cached yet (blocking: call off the UI thread). Throws
     * when the download fails or the file does not match the release's size and sha256.
     */
    public static File get(File filesDir, GameMods.Asset asset, Downloader.ProgressListener progress) throws IOException {
        File have = cached(filesDir, asset);
        if (have != null) return have;
        File d = dir(filesDir);
        if (!d.isDirectory() && !d.mkdirs()) throw new IOException("cannot create " + d);
        File tmp = new File(d, asset.sha256 + ".part");
        tmp.delete();
        if (!Downloader.downloadFile(asset.url, tmp, progress)) {
            tmp.delete();
            throw new IOException("download failed");
        }
        try {
            if (tmp.length() != asset.size) throw new IOException("size " + tmp.length() + ", expected " + asset.size);
            String sha = GameMods.sha256(tmp);
            if (!sha.equals(asset.sha256)) throw new IOException("sha256 " + sha + ", expected " + asset.sha256);
        } catch (IOException e) {
            tmp.delete();
            throw e;
        } catch (Exception e) {
            tmp.delete();
            throw new IOException(e.getMessage(), e);
        }
        File out = file(filesDir, asset);
        out.delete();
        if (!tmp.renameTo(out)) {
            tmp.delete();
            throw new IOException("cannot rename to " + out);
        }
        return out;
    }
}
