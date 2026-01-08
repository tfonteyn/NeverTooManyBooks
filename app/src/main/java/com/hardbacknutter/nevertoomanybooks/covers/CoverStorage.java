/*
 * @Copyright 2018-2026 HardBackNutter
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
import androidx.annotation.Discouraged;
import androidx.annotation.IntRange;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.WorkerThread;
import androidx.preference.PreferenceManager;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Supplier;

import com.hardbacknutter.nevertoomanybooks.BuildConfig;
import com.hardbacknutter.nevertoomanybooks.DEBUG_SWITCHES;
import com.hardbacknutter.nevertoomanybooks.core.storage.FileUtils;
import com.hardbacknutter.nevertoomanybooks.core.storage.VersionedFileService;
import com.hardbacknutter.nevertoomanybooks.database.DBKey;
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

    /** Log tag. */
    private static final String TAG = "CoverStorage";

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

    /** Subdirectory of the Covers directory. */
    private static final String TMP_SUB_DIR = "tmp";

    /** The minimum side (height/width) an image must be to be considered valid; in pixels. */
    private static final int MIN_VALID_IMAGE_SIDE = 10;
    /** The minimum size an image file on disk must be to be considered valid; in bytes. */
    private static final int MIN_VALID_IMAGE_FILE_SIZE = 1024;

    /** Main image file extension. */
    private static final String EXT_JPG = ".jpg";
    /** Fallback image file extension. */
    private static final String EXT_PNG = ".png";

    /** Compression percentage is actually ignored as we're using PNG. */
    private static final int QUALITY = 100;

    private static final String ERROR_INPUT_STREAM_WAS_NULL = "InputStream was NULL";

    @NonNull
    private final Supplier<Context> appContextSupplier;
    @NonNull
    private final Supplier<CoverCacheDao> coverCacheDaoSupplier;

    /**
     * Initialised in {@link #initDir()}.
     * <p>
     * <strong>Always use {@link #getDir()} to access elsewhere.</strong>
     */
    private File coverDir;

    /**
     * Constructor.
     *
     * @param appContextSupplier    deferred supplier for the raw Application Context
     * @param coverCacheDaoSupplier deferred supplier for the {@link CoverCacheDao}
     */
    @AnyThread
    public CoverStorage(@NonNull final Supplier<Context> appContextSupplier,
                        @NonNull final Supplier<CoverCacheDao> coverCacheDaoSupplier) {
        this.appContextSupplier = appContextSupplier;
        this.coverCacheDaoSupplier = coverCacheDaoSupplier;
    }

    @AnyThread
    @NonNull
    private static String createName(@NonNull final String uuid,
                                     @IntRange(from = 0, to = 3) final int cIdx) {
        final String name;
        if (cIdx > 0) {
            name = uuid + "_" + cIdx;
        } else {
            // backwards compatibility, use the raw uuid only
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
    @AnyThread
    public void initDir()
            throws CoverStorageException {

        coverDir = ensureDir();

        // Prevent thumbnails showing up in the device Image Gallery.
        final File mif = new File(coverDir, MediaStore.MEDIA_IGNORE_FILENAME);
        if (!mif.exists()) {
            try {
                //noinspection ResultOfMethodCallIgnored
                mif.createNewFile();
            } catch (@NonNull final IOException | SecurityException e) {
                // SecurityException is never thrown as the
                // System.getSecurityManager() always return null
                throw new CoverStorageException("Failed to write Pictures/.nomedia", e);
            }
        }

        // Create the temporary subdirectory if not done yet
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
     * This is a cheap check for {@code null}, file-size and image dimensions
     * without fully decoding the bitmap.
     *
     * @param file to check
     *
     * @return {@code true} if image is acceptable.
     */
    @WorkerThread
    public boolean isAcceptableSize(@Nullable final File file) {
        if (file == null || file.length() < MIN_VALID_IMAGE_FILE_SIZE) {
            return false;
        }

        // Read the image options (without generating a bitmap) to get file size
        final BitmapFactory.Options options = new BitmapFactory.Options();
        options.inJustDecodeBounds = true;
        BitmapFactory.decodeFile(file.getAbsolutePath(), options);

        return options.outHeight >= MIN_VALID_IMAGE_SIDE
               && options.outWidth >= MIN_VALID_IMAGE_SIDE;
    }

    /**
     * Get the <strong>permanent</strong> directory where we store covers.
     *
     * @return directory
     *
     * @throws CoverStorageException The covers directory is not available
     * @see #initDir()
     */
    @NonNull
    public File getDir()
            throws CoverStorageException {
        synchronized (this) {
            // This should never be possible, but see GitHub #184
            if (coverDir == null) {
                coverDir = ensureDir();
            }
        }
        return Objects.requireNonNull(coverDir);
    }

    /**
     * Get the <strong>permanent</strong> directory where we store covers.
     * This method will access the storage volume/directories to make
     * sure they are available.
     *
     * @return directory
     *
     * @throws CoverStorageException The covers directory is not available
     * @see #initDir()
     * @see #getDir()
     */
    @Discouraged(message = "Avoid using this method if possible due to overhead")
    @NonNull
    private File ensureDir()
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
     * Currently, this is a subdirectory of the permanent one to facilitate move==renames.
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
                                           @IntRange(from = 0, to = 3) final int cIdx) {
        if (uuid.isEmpty()) {
            return Optional.empty();
        }

        final String name = createName(uuid, cIdx);

        @Nullable
        File coverFile;
        final File dir;
        try {
            dir = getDir();
        } catch (@NonNull final CoverStorageException e) {
            LoggerFactory.getLogger().e(TAG, e);
            return Optional.empty();
        }

        // Try finding a jpg
        coverFile = new File(dir, name + EXT_JPG);
        // If it exists, it will be a valid file as we check before storing it
        if (coverFile.exists()) {
            if (BuildConfig.DEBUG && DEBUG_SWITCHES.IMAGES) {
                LoggerFactory.getLogger()
                             .e(TAG, new Throwable("getPersistedFile:"),
                                "uuid=" + uuid
                                + "|cIdx=" + cIdx
                                + "|file=" + coverFile.getAbsolutePath());
            }
            return Optional.of(coverFile);

        } else {
            // not found, try finding a png
            coverFile = new File(dir, name + EXT_PNG);
            // If it exists, it will be a valid file as we check before storing it
            if (coverFile.exists()) {
                // rename it to the standard extension regardless of type
                // #isUndoEnabled(String,int) relies on the jpg extension
                try {
                    FileUtils.rename(coverFile, new File(dir, name + EXT_JPG));
                    coverFile = new File(dir, name + EXT_JPG);
                } catch (@NonNull final IOException ignore) {
                    // ignore a rename failure, and return the png file regardless
                }
                if (BuildConfig.DEBUG && DEBUG_SWITCHES.IMAGES) {
                    LoggerFactory.getLogger()
                                 .e(TAG, new Throwable("getPersistedFile:"),
                                    "uuid=" + uuid
                                    + "|cIdx=" + cIdx
                                    + "|file=" + coverFile.getAbsolutePath());
                }
                return Optional.of(coverFile);
            }
        }

        if (BuildConfig.DEBUG && DEBUG_SWITCHES.IMAGES) {
            LoggerFactory.getLogger()
                         .e(TAG, new Throwable("getPersistedFile"),
                            "uuid=" + uuid
                            + "|cIdx=" + cIdx
                            + "|file not found");
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
    @AnyThread
    @NonNull
    public File persist(@NonNull final File source,
                        @NonNull final String uuid,
                        @IntRange(from = 0, to = 3) final int cIdx)
            throws IOException, CoverStorageException {

        final String name = createName(uuid, cIdx) + EXT_JPG;
        final File destination = new File(getDir(), name);

        try {
            if (isUndoEnabled()) {
                createVersionedFileService().save(destination);
            }
            FileUtils.rename(source, destination);
            return destination;
        } finally {
            // Fire and forget
            FileUtils.backgroundDelete(source);
        }
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
    @WorkerThread
    @NonNull
    public File persist(@NonNull final Bitmap source,
                        @NonNull final File destination)
            throws CoverStorageException,
                   IOException {

        final File tmpFile = getTempFile();
        try (OutputStream os = new FileOutputStream(tmpFile)) {
            if (!source.compress(Bitmap.CompressFormat.PNG, QUALITY, os)) {
                throw new IOException("Bitmap compression failed");
            }
        }
        try {
            if (isUndoEnabled()) {
                createVersionedFileService().save(destination);
            }
            FileUtils.rename(tmpFile, destination);
            return destination;
        } finally {
            FileUtils.delete(tmpFile);
        }
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
    @WorkerThread
    @NonNull
    public File persist(@Nullable final InputStream source,
                        @NonNull final File destination)
            throws CoverStorageException,
                   FileNotFoundException,
                   IOException {

        final File tmpFile = writeTempFile(source);
        try {
            if (isUndoEnabled()) {
                createVersionedFileService().save(destination);
            }
            FileUtils.rename(tmpFile, destination);
            return destination;
        } finally {
            FileUtils.delete(tmpFile);
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
    @WorkerThread
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
            // Fire and forget
            FileUtils.backgroundDelete(tmpFile);
            throw e;
        }
    }

    /**
     * Delete the persisted files for the given book uuids.
     * Any errors are logged, but ignored.
     *
     * @param uuids list of book uuid's
     */
    @WorkerThread
    public void delete(@NonNull final List<String> uuids) {
        uuids.forEach(uuid -> {
            for (int cIdx = 0; cIdx < DBKey.NR_OF_BOOK_COVERS; cIdx++) {
                delete(uuid, cIdx);
            }
        });
    }

    /**
     * Delete the persisted file at the given index, for the given uuid.
     * Any errors are logged, but ignored.
     *
     * @param uuid the book UUID
     * @param cIdx 0..n image index
     */
    @WorkerThread
    public void delete(@NonNull final String uuid,
                       @IntRange(from = 0, to = 3) final int cIdx) {

        getPersistedFile(uuid, cIdx).ifPresent(file -> {
            if (isUndoEnabled()) {
                try {
                    createVersionedFileService().save(file);
                } catch (@NonNull final CoverStorageException | IOException e) {
                    LoggerFactory.getLogger().e(TAG, e);
                }
            } else {
                // no undo, just delete it
                FileUtils.delete(file);
            }

            // Delete from the cache.
            // Note we also delete the ones where != index.
            // and if the user flipped the cache on/off we're not
            // always cleaning up correctly.
            // Oh, well... we don't care; it's a cache.
            if (isImageCachingEnabled()) {
                coverCacheDaoSupplier.get().delete(uuid);
            }
        });
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
    @WorkerThread
    public boolean restore(@NonNull final String uuid,
                           @IntRange(from = 0, to = 3) final int cIdx)
            throws IOException {
        if (!isUndoEnabled()) {
            return false;
        }

        // We're relying on the fact that #getPersistedFile
        // would have renamed any remaining png files to jpg by now.
        final String name = createName(uuid, cIdx) + EXT_JPG;

        try {
            return createVersionedFileService().restore(new File(getDir(), name));
        } catch (@NonNull final CoverStorageException e) {
            LoggerFactory.getLogger().e(TAG, e);
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
                          @IntRange(from = 0, to = 3) final int cIdx) {
        if (!isUndoEnabled()) {
            return false;
        }

        // We're relying on the fact that #getPersistedFile
        // would have renamed any remaining png files to jpg by now.
        final String name = createName(uuid, cIdx) + EXT_JPG;

        try {
            return createVersionedFileService().hasBackup(new File(getDir(), name));
        } catch (@NonNull final CoverStorageException e) {
            LoggerFactory.getLogger().e(TAG, e);
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
    @AnyThread
    private boolean isUndoEnabled() {
        return PreferenceManager.getDefaultSharedPreferences(appContextSupplier.get())
                                .getBoolean(PK_ENABLE_UNDO, true);
    }

    /**
     * Check if caching is enabled.
     *
     * @return {@code true} if resized images are cached in a database.
     */
    @AnyThread
    public boolean isImageCachingEnabled() {
        return PreferenceManager.getDefaultSharedPreferences(appContextSupplier.get())
                                .getBoolean(PK_CACHE_RESIZED_IMAGES, false);
    }

    /**
     * Enable or disable the image caching database.
     *
     * @param enable flag
     */
    @AnyThread
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
                                  @IntRange(from = 0, to = 3) final int cIdx,
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
    public void saveToCache(@NonNull final String uuid,
                            @IntRange(from = 0, to = 3) final int cIdx,
                            @NonNull final Bitmap bitmap,
                            final int width) {
        coverCacheDaoSupplier.get().saveCover(uuid, cIdx, bitmap, width);
    }
}
