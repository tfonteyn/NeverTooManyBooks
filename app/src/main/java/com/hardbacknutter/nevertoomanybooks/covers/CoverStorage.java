/*
 * @Copyright 2018-2023 HardBackNutter
 * @License GNU General Public License
 *
 * This file is part of NeverTooManyBooks.
 *
 * NeverTooManyBooks is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * NeverTooManyBooks is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 * See the GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with NeverTooManyBooks. If not, see <http://www.gnu.org/licenses/>.
 */

package com.hardbacknutter.nevertoomanybooks.covers;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Environment;
import android.util.Log;

import androidx.annotation.AnyThread;
import androidx.annotation.IntRange;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.UiThread;
import androidx.preference.PreferenceManager;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Optional;
import java.util.function.Supplier;

import com.hardbacknutter.nevertoomanybooks.BuildConfig;
import com.hardbacknutter.nevertoomanybooks.DEBUG_SWITCHES;
import com.hardbacknutter.nevertoomanybooks.core.LoggerFactory;
import com.hardbacknutter.nevertoomanybooks.core.storage.FileUtils;
import com.hardbacknutter.nevertoomanybooks.core.storage.StorageException;
import com.hardbacknutter.nevertoomanybooks.database.dao.CoverCacheDao;
import com.hardbacknutter.nevertoomanybooks.settings.Prefs;

/**
 * Handles persistence for cover files.
 * <p>
 * Storing is done as jpg; loading tries jpg first, png second.
 * <p>
 * Serves as a wrapper over the file system AND the covers cache dao.
 */
public class CoverStorage {

    /** Sub directory of the Covers directory. */
    static final String DIR_TMP = "tmp";

    private static final String TAG = "CoverStorage";

    /** The minimum side (height/width) an image must be to be considered valid; in pixels. */
    private static final int MIN_VALID_IMAGE_SIDE = 10;

    /** The minimum size an image file on disk must be to be considered valid; in bytes. */
    private static final int MIN_VALID_IMAGE_FILE_SIZE = 2048;

    @NonNull
    private final Supplier<Context> appContextSupplier;

    @NonNull
    private final Supplier<CoverCacheDao> coverCacheDaoSupplier;

    /**
     * Constructor.
     *
     * @param appContextSupplier    deferred supplier for the raw Application Context
     * @param coverCacheDaoSupplier deferred supplier for the {@link CoverCacheDao}
     */
    public CoverStorage(@NonNull final Supplier<Context> appContextSupplier,
                        @NonNull final Supplier<CoverCacheDao> coverCacheDaoSupplier) {
        this.appContextSupplier = appContextSupplier;
        this.coverCacheDaoSupplier = coverCacheDaoSupplier;
    }

    /**
     * Check if a file is an image with an acceptable size.
     * <p>
     * This is a slow check, use only when import/saving.
     * When displaying do a simple {@code srcFile.exists()} instead.
     * <p>
     * <strong>If the file is not acceptable, then it will be deleted.</strong>
     *
     * @param srcFile to check
     *
     * @return {@code true} if file is acceptable.
     */
    @AnyThread
    public static boolean isAcceptableSize(@Nullable final File srcFile) {
        if (srcFile == null) {
            return false;
        }

        if (srcFile.length() < MIN_VALID_IMAGE_FILE_SIZE) {
            FileUtils.delete(srcFile);
            return false;
        }

        // Read the image files to get file size
        final BitmapFactory.Options opt = new BitmapFactory.Options();
        opt.inJustDecodeBounds = true;
        BitmapFactory.decodeFile(srcFile.getAbsolutePath(), opt);

        // minimal size required
        final boolean tooSmall = isTooSmall(opt);

        // cleanup bad files.
        if (tooSmall) {
            FileUtils.delete(srcFile);
        }
        return !tooSmall;
    }

    static boolean isTooSmall(@NonNull final BitmapFactory.Options opt) {
        return opt.outHeight < MIN_VALID_IMAGE_SIDE || opt.outWidth < MIN_VALID_IMAGE_SIDE;
    }

    /**
     * Get the *permanent* directory where we store covers.
     *
     * @param context Current context
     *
     * @return directory
     *
     * @throws StorageException The covers directory is not available
     */
    @NonNull
    static File getDir(@NonNull final Context context)
            throws StorageException {

        final int volume = CoverVolume.getVolume(context);

        final File[] externalFilesDirs =
                context.getExternalFilesDirs(Environment.DIRECTORY_PICTURES);

        if (externalFilesDirs == null
            || externalFilesDirs.length < volume
            || externalFilesDirs[volume] == null
            || !externalFilesDirs[volume].exists()) {
            throw new CoverStorageException("No volume: " + volume);
        }

        return externalFilesDirs[volume];
    }

    /**
     * Get the *permanent* directory where we store covers.
     *
     * @return directory
     *
     * @throws StorageException The covers directory is not available
     * @see CoverStorage#getDir(Context)
     */
    @NonNull
    public File getDir()
            throws StorageException {
        return getDir(appContextSupplier.get());
    }

    /**
     * Get the *temporary* directory where we store covers.
     * Currently this is a sub directory of the permanent one to facilitate move==renames.
     *
     * @return directory
     *
     * @throws StorageException The covers directory is not available
     */
    @NonNull
    public File getTempDir()
            throws StorageException {
        return new File(getDir(), DIR_TMP);
    }

    /**
     * Get the file for this cover. We'll attempt to find a jpg or a png.
     * <p>
     * Any {@link StorageException} is <strong>IGNORED</strong>
     *
     * @param uuid the book UUID
     * @param cIdx 0..n image index
     *
     * @return file
     */
    @NonNull
    public Optional<File> getPersistedFile(@NonNull final String uuid,
                                           final int cIdx) {
        if (uuid.isEmpty()) {
            return Optional.empty();
        }

        final File coverDir;
        try {
            coverDir = getDir(appContextSupplier.get());
        } catch (@NonNull final StorageException e) {
            if (BuildConfig.DEBUG /* always */) {
                Log.d(TAG, "getPersistedCoverFile", e);
            }
            return Optional.empty();
        }

        final String name;
        if (cIdx > 0) {
            name = uuid + "_" + cIdx;
        } else {
            name = uuid;
        }

        File coverFile;
        // Try finding a jpg
        coverFile = new File(coverDir, name + ".jpg");
        if (!coverFile.exists()) {
            // not found, try finding a png
            coverFile = new File(coverDir, name + ".png");
            if (!coverFile.exists()) {
                coverFile = null;
            }
        }

        if (BuildConfig.DEBUG && DEBUG_SWITCHES.COVERS) {
            LoggerFactory.getLogger()
                         .e(TAG, new Throwable("getPersistedFile"),
                            "uuid=" + uuid
                            + "|cIdx=" + cIdx
                            + "|file="
                            + (coverFile == null ? "null" : coverFile.getAbsolutePath()));
        }

        if (coverFile != null && coverFile.exists()) {
            return Optional.of(coverFile);
        }
        return Optional.empty();
    }

    /**
     * Persist the given temporary file to a permanent location.
     *
     * @param uuid the book UUID
     * @param cIdx 0..n image index
     * @param file temp file to persist
     *
     * @return permanent file
     *
     * @throws IOException      on generic/other IO failures
     * @throws StorageException The covers directory is not available
     */
    @NonNull
    public File persist(@NonNull final String uuid,
                        final int cIdx,
                        @NonNull final File file)
            throws IOException, StorageException {
        final String name;
        if (cIdx > 0) {
            name = uuid + "_" + cIdx + ".jpg";
        } else {
            name = uuid + ".jpg";
        }

        final File destination = new File(getDir(appContextSupplier.get()), name);
        FileUtils.rename(file, destination);
        return destination;
    }

    /**
     * Given a InputStream with an image, write it to a file in the temp directory.
     * We first write to a temporary file, so an existing 'out' file is not destroyed
     * if the stream somehow fails.
     *
     * @param is      InputStream to read
     * @param dstFile the File to write to
     *
     * @return File written to (the one passed in)
     *
     * @throws StorageException      The covers directory is not available
     * @throws FileNotFoundException if the input stream was {@code null}
     * @throws IOException           on generic/other IO failures
     */
    @NonNull
    public File persist(@Nullable final InputStream is,
                        @NonNull final File dstFile)
            throws StorageException,
                   FileNotFoundException,
                   IOException {
        if (is == null) {
            throw new FileNotFoundException("InputStream was NULL");
        }

        final File tmpFile = new File(getTempDir(), System.nanoTime() + ".jpg");
        try (OutputStream os = new FileOutputStream(tmpFile)) {
            FileUtils.copy(is, os);
            // rename to real output file
            FileUtils.rename(tmpFile, dstFile);
            return dstFile;
        } finally {
            FileUtils.delete(tmpFile);
        }
    }

    /**
     * Delete the persisted file (if it exists).
     *
     * @param uuid the book UUID
     * @param cIdx 0..n image index
     */
    public void delete(@NonNull final String uuid,
                       final int cIdx) {
        getPersistedFile(uuid, cIdx).ifPresent(FileUtils::delete);
        // Delete from the cache. And yes, we also delete the ones
        // where != index, but we don't care; it's a cache.
        // If the user flipped the cache on/off we'll
        // not always be cleaning up correctly. It's not that important though.
        if (isImageCachingEnabled()) {
            coverCacheDaoSupplier.get().delete(uuid);
        }
    }

    /**
     * Check if caching is enabled.
     *
     * @return {@code true} if resized images are cached in a database.
     */
    public boolean isImageCachingEnabled() {
        return PreferenceManager.getDefaultSharedPreferences(appContextSupplier.get())
                                .getBoolean(Prefs.pk_image_cache_resized, false);
    }

    public void setImageCachingEnabled(final boolean enable) {
        PreferenceManager.getDefaultSharedPreferences(appContextSupplier.get())
                         .edit()
                         .putBoolean(Prefs.pk_image_cache_resized, enable)
                         .apply();
    }

    /**
     * Get a cached image.
     *
     * @param uuid   UUID of the book
     * @param cIdx   0..n image index
     * @param width  desired/maximum width
     * @param height desired/maximum height
     *
     * @return Bitmap (if cached) or {@code null} if not cached or if the cache was busy
     *
     * @see CoverCacheDao#getCover(String, int, int, int)
     */
    @Nullable
    public Bitmap getCachedBitmap(@NonNull final String uuid,
                                  final int cIdx,
                                  final int width,
                                  final int height) {
        final CoverCacheDao coverCacheDao = coverCacheDaoSupplier.get();
        if (!coverCacheDao.isBusy()) {
            return coverCacheDao.getCover(uuid, cIdx, width, height);
        }
        return null;
    }

    /**
     * Save the passed bitmap to the cache.
     * <p>
     * This will either insert or update a row in the database.
     * Failures are ignored; this is just a cache.
     *
     * @param uuid   UUID of the book
     * @param cIdx   0..n image index
     * @param bitmap to save
     * @param width  desired/maximum width
     * @param height desired/maximum height
     *
     * @see CoverCacheDao#saveCover(String, int, Bitmap, int, int)
     */
    @UiThread
    public void saveToCache(@NonNull final String uuid,
                            @IntRange(from = 0, to = 1) final int cIdx,
                            @NonNull final Bitmap bitmap,
                            final int width,
                            final int height) {
        coverCacheDaoSupplier.get().saveCover(uuid, cIdx, bitmap, width, height);
    }
}
