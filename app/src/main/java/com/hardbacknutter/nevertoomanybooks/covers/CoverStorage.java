/*
 * @Copyright 2018-2024 HardBackNutter
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
import android.provider.MediaStore;

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
import com.hardbacknutter.nevertoomanybooks.core.storage.FileUtils;
import com.hardbacknutter.nevertoomanybooks.core.storage.VersionedFileService;
import com.hardbacknutter.nevertoomanybooks.database.dao.CoverCacheDao;
import com.hardbacknutter.util.logger.LoggerFactory;

/**
 * Handles persistence for cover files.
 * <p>
 * Storing is generally done as png, but filenames use the ".jpg" extension for historic reasons.
 * Loading an image will always try ".jpg" first, ".png" second.
 * <p>
 * Serves as a wrapper over the file system AND the covers cache dao.
 */
public class CoverStorage {

    /**
     * Preference key: whether backups will be taken of cover files,
     * which will let te user undo transformation and other changes.
     * <p>
     * {@code boolean}
     *
     * @see #isUndoEnabled()
     */
    private static final String PK_ENABLE_UNDO = "image.undo.enabled";

    /**
     * Preference key: whether we're caching resized images in a temporary database.
     * <p>
     * {@code boolean}
     *
     * @see #setImageCachingEnabled(boolean)
     * @see #isImageCachingEnabled()
     */
    private static final String PK_CACHE_RESIZED_IMAGES = "image.cache.resized";

    /** Sub directory of the Covers directory. */
    private static final String TMP_SUB_DIR = "tmp";

    private static final String TAG = "CoverStorage";
    /** The minimum side (height/width) an image must be to be considered valid; in pixels. */
    private static final int MIN_VALID_IMAGE_SIDE = 10;
    /** The minimum size an image file on disk must be to be considered valid; in bytes. */
    private static final int MIN_VALID_IMAGE_FILE_SIZE = 2048;
    private static final String EXT_JPG = ".jpg";
    private static final String EXT_PNG = ".png";
    /** Compression percentage is actually ignored as we're using PNG. */
    private static final int QUALITY = 100;
    private static final String ERROR_INPUT_STREAM_WAS_NULL = "InputStream was NULL";
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

    @NonNull
    private static String createName(@NonNull final String uuid,
                                     @IntRange(from = 0, to = 1) final int cIdx) {
        final String name;
        if (cIdx > 0) {
            name = uuid + "_" + cIdx;
        } else {
            name = uuid;
        }
        return name;
    }

    /**
     * (Re)Initialize storage needs using the configured volume.
     * <p>
     * This method is called during startup, and when/if the user changes the cover volume
     * in the preferences.
     *
     * @throws CoverStorageException The covers directory is not available
     */
    public void initDir()
            throws CoverStorageException {

        final File coverDir = getDir();

        // Prevent thumbnails showing up in the device Image Gallery.
        final File mif = new File(coverDir, MediaStore.MEDIA_IGNORE_FILENAME);
        if (!mif.exists()) {
            try {
                //noinspection ResultOfMethodCallIgnored
                mif.createNewFile();
            } catch (@NonNull final IOException | SecurityException e) {
                throw new CoverStorageException("Failed to write Pictures/.nomedia", e);
            }
        }

        // Create the temporary sub directory if not done yet
        final File tmpDir = new File(coverDir, TMP_SUB_DIR);
        if (!(tmpDir.isDirectory() || tmpDir.mkdirs())) {
            throw new CoverStorageException("Failed to create covers directory: Pictures/tmp");
        }
    }

    /**
     * Create a {@link VersionedFileService} for the temporary directory.
     * <p>
     * <strong>Do NOT cache</strong>: the directory depends on
     * {@link Context#getExternalFilesDirs(String)} which can change
     * (e.g. when the user uses a replaceable sdcard).
     *
     * @return service
     *
     * @throws CoverStorageException on any error
     */
    @NonNull
    private VersionedFileService createVersionedFileService()
            throws CoverStorageException {
        return new VersionedFileService(getTempDir(), 1);
    }

    /**
     * Check if a file is an image with an acceptable size.
     *
     * @param options of which the height/width will be checked
     *
     * @return {@code true} if the image is acceptable.
     */
    boolean isAcceptableSize(@NonNull final BitmapFactory.Options options) {
        return options.outHeight >= MIN_VALID_IMAGE_SIDE
               && options.outWidth >= MIN_VALID_IMAGE_SIDE;
    }

    /**
     * Check if a file is an image with an acceptable size.
     * <p>
     * This is a slow check, use only when import/saving.
     * When displaying do a simple {@code srcFile.exists()} instead.
     * <p>
     * <strong>If the image is not acceptable, then the file will be deleted.</strong>
     *
     * @param srcFile to check
     *
     * @return {@code true} if image is acceptable.
     */
    @AnyThread
    public boolean isAcceptableSize(@Nullable final File srcFile) {
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

        if (isAcceptableSize(opt)) {
            return true;
        }

        FileUtils.delete(srcFile);
        return false;
    }

    /**
     * Get the <strong>permanent</strong> directory where we store covers.
     *
     * @return directory
     *
     * @throws CoverStorageException The covers directory is not available
     */
    @NonNull
    public File getDir()
            throws CoverStorageException {

        final Context context = appContextSupplier.get();

        final int volume = CoverVolume.getVolume(context);

        final File[] externalFilesDirs = context
                .getExternalFilesDirs(Environment.DIRECTORY_PICTURES);

        if (externalFilesDirs == null
            || externalFilesDirs.length <= volume
            || externalFilesDirs[volume] == null
            || !externalFilesDirs[volume].exists()) {
            throw new CoverStorageException("Failed to access covers on volume: " + volume);
        }

        return externalFilesDirs[volume];
    }

    /**
     * Get the <strong>temporary</strong> directory where we store covers.
     * Currently this is a sub directory of the permanent one to facilitate move==renames.
     *
     * @return directory
     *
     * @throws CoverStorageException The covers directory is not available
     */
    @NonNull
    public File getTempDir()
            throws CoverStorageException {
        return new File(getDir(), TMP_SUB_DIR);
    }

    /**
     * Get a temporary file.
     *
     * @return file
     *
     * @throws CoverStorageException The covers directory is not available
     */
    @NonNull
    File getTempFile()
            throws CoverStorageException {
        return new File(getTempDir(), System.nanoTime() + EXT_JPG);
    }

    /**
     * Get the file for this cover. We'll attempt to find a jpg or a png.
     * <p>
     * Any {@link CoverStorageException} is <strong>IGNORED</strong>
     *
     * @param uuid the book UUID
     * @param cIdx 0..n image index
     *
     * @return file
     */
    @NonNull
    public Optional<File> getPersistedFile(@NonNull final String uuid,
                                           @IntRange(from = 0, to = 1) final int cIdx) {
        if (uuid.isEmpty()) {
            return Optional.empty();
        }

        final File coverDir;
        try {
            coverDir = getDir();
        } catch (@NonNull final CoverStorageException e) {
            return Optional.empty();
        }

        final String name = createName(uuid, cIdx);

        @Nullable
        File coverFile;
        // Try finding a jpg
        coverFile = new File(coverDir, name + EXT_JPG);
        if (!coverFile.exists()) {
            // not found, try finding a png
            coverFile = new File(coverDir, name + EXT_PNG);
            if (coverFile.exists()) {
                // rename it to the standard extension regardless of type
                // #isUndoEnabled(String,int) relies on this
                try {
                    FileUtils.rename(coverFile, new File(coverDir, name + EXT_JPG));
                    coverFile = new File(coverDir, name + EXT_JPG);
                } catch (@NonNull final IOException ignore) {
                    // ignore
                }
            } else {
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
     * <p>
     * The uuid and cover-index will be used to construct the destination
     * file name.
     *
     * @param source temp file to persist
     * @param uuid   the book UUID
     * @param cIdx   0..n image index
     *
     * @return permanent file
     *
     * @throws IOException           on generic/other IO failures
     * @throws CoverStorageException The covers directory is not available
     */
    @NonNull
    public File persist(@NonNull final File source,
                        @NonNull final String uuid,
                        @IntRange(from = 0, to = 1) final int cIdx)
            throws IOException, CoverStorageException {

        final String name = createName(uuid, cIdx) + EXT_JPG;
        final File coverDir = getDir();
        final File destination = new File(coverDir, name);

        return persist(source, destination);
    }

    /**
     * Write the given Bitmap to the given destination File.
     *
     * @param source      to handle
     * @param destination the File to write to
     *
     * @return File written to (the one passed in)
     *
     * @throws IOException           on generic/other IO failures
     * @throws CoverStorageException The covers directory is not available
     */
    @NonNull
    public File persist(@NonNull final Bitmap source,
                        @NonNull final File destination)
            throws IOException, CoverStorageException {

        final File tmpFile = getTempFile();
        try (OutputStream os = new FileOutputStream(tmpFile)) {
            if (!source.compress(Bitmap.CompressFormat.PNG, QUALITY, os)) {
                throw new IOException("Bitmap compression failed");
            }
        }
        return persist(tmpFile, destination);
    }

    /**
     * Write the given InputStream to the given destination File.
     *
     * @param source      InputStream to read
     * @param destination the File to write to
     *
     * @return File written to (the one passed in)
     *
     * @throws CoverStorageException The covers directory is not available
     * @throws FileNotFoundException if the input stream was {@code null}
     * @throws IOException           on generic/other IO failures
     */
    @NonNull
    public File persist(@Nullable final InputStream source,
                        @NonNull final File destination)
            throws CoverStorageException,
                   FileNotFoundException,
                   IOException {

        final File tmpFile = writeTempFile(source);
        return persist(tmpFile, destination);
    }

    @NonNull
    private File persist(@NonNull final File source,
                         @NonNull final File destination)
            throws CoverStorageException, IOException {
        try {
            if (isUndoEnabled()) {
                createVersionedFileService().save(destination);
            }
            FileUtils.rename(source, destination);
            return destination;
        } finally {
            FileUtils.delete(source);
        }
    }

    /**
     * Write the given InputStream to a temporary File.
     *
     * @param source InputStream to read
     *
     * @return the File
     *
     * @throws CoverStorageException The covers directory is not available
     * @throws FileNotFoundException if the input stream was {@code null}
     * @throws IOException           on generic/other IO failures
     */
    @NonNull
    File writeTempFile(@Nullable final InputStream source)
            throws CoverStorageException, IOException {

        if (source == null) {
            throw new FileNotFoundException(ERROR_INPUT_STREAM_WAS_NULL);
        }

        final File tmpFile = getTempFile();
        try (OutputStream os = new FileOutputStream(tmpFile)) {
            FileUtils.copy(source, os);
            return tmpFile;

        } catch (@NonNull final IOException e) {
            FileUtils.delete(tmpFile);
            throw e;
        }
    }

    /**
     * Delete the persisted file (if it exists).
     *
     * @param uuid the book UUID
     * @param cIdx 0..n image index
     */
    public void delete(@NonNull final String uuid,
                       @IntRange(from = 0, to = 1) final int cIdx) {

        final Optional<File> persistedFile = getPersistedFile(uuid, cIdx);
        if (persistedFile.isPresent()) {
            final File file = persistedFile.get();
            if (isUndoEnabled()) {
                try {
                    createVersionedFileService().save(file);
                } catch (@NonNull final CoverStorageException e) {
                    LoggerFactory.getLogger().e(TAG, e);
                }
            }
            FileUtils.delete(file);
        }

        // Delete from the cache. And yes, we also delete the ones
        // where != index, but we don't care; it's a cache.
        // If the user flipped the cache on/off we'll
        // not always be cleaning up correctly. It's not that important though.
        if (isImageCachingEnabled()) {
            coverCacheDaoSupplier.get().delete(uuid);
        }
    }

    /**
     * Restore the previous version of the given cover.
     *
     * @param uuid UUID of the book
     * @param cIdx 0..n image index
     *
     * @return {@code true} if the restore was successfully
     *
     * @throws IOException on generic/other IO failures
     */
    public boolean restore(@NonNull final String uuid,
                           @IntRange(from = 0, to = 1) final int cIdx)
            throws IOException {
        if (!isUndoEnabled()) {
            return false;
        }

        final File coverDir;
        try {
            coverDir = getDir();
        } catch (@NonNull final CoverStorageException e) {
            return false;
        }

        // We're relying on the fact that #getPersistedFile
        // would have renamed any remaining png files to jpg by now.
        final String name = createName(uuid, cIdx) + EXT_JPG;

        try {
            return createVersionedFileService().restore(new File(coverDir, name));
        } catch (@NonNull final CoverStorageException ignore) {
            return false;
        }
    }

    /**
     * Check if we <strong>can</strong> restore a previous version
     * of the given cover.
     *
     * @param uuid the book UUID
     * @param cIdx 0..n image index
     *
     * @return {@code true} if there is a previous version
     */
    boolean isUndoEnabled(@NonNull final String uuid,
                          @IntRange(from = 0, to = 1) final int cIdx) {
        if (!isUndoEnabled()) {
            return false;
        }

        final File coverDir;
        try {
            coverDir = getDir();
        } catch (@NonNull final CoverStorageException e) {
            return false;
        }

        // We're relying on the fact that #getPersistedFile
        // would have renamed any remaining png files to jpg by now.
        final String name = createName(uuid, cIdx) + EXT_JPG;

        try {
            return createVersionedFileService().hasBackup(new File(coverDir, name));
        } catch (@NonNull final CoverStorageException ignore) {
            return false;
        }
    }

    /**
     * Check if we need to enable support for 'undo' after cover manipulations.
     * <p>
     * The default is {@code true} unless changed in the user preferences.
     *
     * @return {@code true} if enabled
     */
    private boolean isUndoEnabled() {
        return PreferenceManager.getDefaultSharedPreferences(appContextSupplier.get())
                                .getBoolean(PK_ENABLE_UNDO, true);
    }

    /**
     * Check if caching is enabled.
     *
     * @return {@code true} if resized images are cached in a database.
     */
    public boolean isImageCachingEnabled() {
        return PreferenceManager.getDefaultSharedPreferences(appContextSupplier.get())
                                .getBoolean(PK_CACHE_RESIZED_IMAGES, false);
    }

    /**
     * Enable or disable the image caching database.
     *
     * @param enable flag
     */
    public void setImageCachingEnabled(final boolean enable) {
        PreferenceManager.getDefaultSharedPreferences(appContextSupplier.get())
                         .edit()
                         .putBoolean(PK_CACHE_RESIZED_IMAGES, enable)
                         .apply();
    }

    /**
     * Get a cached image.
     *
     * @param uuid  UUID of the book
     * @param cIdx  0..n image index
     * @param width desired/maximum width
     *
     * @return Bitmap (if cached) or {@code null} if not cached or if the cache was busy
     *
     * @see CoverCacheDao#getCover(String, int, int)
     */
    @Nullable
    public Bitmap getCachedBitmap(@NonNull final String uuid,
                                  @IntRange(from = 0, to = 1) final int cIdx,
                                  final int width) {
        return coverCacheDaoSupplier.get().getCover(uuid, cIdx, width);
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
     *
     * @see CoverCacheDao#saveCover(String, int, Bitmap, int)
     */
    @UiThread
    public void saveToCache(@NonNull final String uuid,
                            @IntRange(from = 0, to = 1) final int cIdx,
                            @NonNull final Bitmap bitmap,
                            final int width) {
        coverCacheDaoSupplier.get().saveCover(uuid, cIdx, bitmap, width);
    }
}
