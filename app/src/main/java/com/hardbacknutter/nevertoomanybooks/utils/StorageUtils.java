/*
 * @Copyright 2020 HardBackNutter
 * @License GNU General Public License
 *
 * This file is part of NeverTooManyBooks.
 *
 * In August 2018, this project was forked from:
 * Book Catalogue 5.2.2 @2016 Philip Warner & Evan Leybourn
 *
 * Without their original creation, this project would not exist in its
 * current form. It was however largely rewritten/refactored and any
 * comments on this fork should be directed at HardBackNutter and not
 * at the original creators.
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
package com.hardbacknutter.nevertoomanybooks.utils;

import android.content.ContentResolver;
import android.content.Context;
import android.net.Uri;
import android.os.Environment;
import android.os.StatFs;
import android.provider.MediaStore;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import androidx.documentfile.provider.DocumentFile;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.channels.FileChannel;
import java.util.Objects;

import com.hardbacknutter.nevertoomanybooks.BuildConfig;
import com.hardbacknutter.nevertoomanybooks.R;
import com.hardbacknutter.nevertoomanybooks.database.CoversDAO;
import com.hardbacknutter.nevertoomanybooks.database.DBHelper;
import com.hardbacknutter.nevertoomanybooks.debug.Logger;
import com.hardbacknutter.nevertoomanybooks.utils.exceptions.ExternalStorageException;

/**
 * Class to wrap common storage related functions.
 * <p>
 * "External Storage" == what Android considers non-application-private storage.
 * i.e the user has access to it.
 * ('external' refers to old android phones where Shared Storage was *always* on an sdcard)
 * Also referred to as "Shared Storage" because all apps have access to it.
 * For the sake of clarity (confusion?) we'll call it "Shared Storage" only.
 * <p>
 * TODO: implement the sample code for 'watching'  Environment.getExternalStorageDirectory()
 * and/or isExternalStorageRemovable()
 * <p>
 * <strong>Important:</strong> Any changes to the directories used
 * must be mirrored in "res/xml/provider_paths.xml"
 * <p>
 * <strong>Note:</strong> we use the external cache directory, so a 'rename' works as is.
 * see {@link #renameFile(File, File)}
 * <p>
 * TODO: ExternalStorageException added were appropriate, but other than here we don't catch them.
 * <p>
 * ENHANCE: using the private directories was an improvement (and mandatory to target Android 10)
 * but would be nice to use the contentResolver API and keep our covers in a sub directory
 * of the users Pictures folder.
 * PRO: preserved on uninstall; user has full access.
 * CON: added to the user media, which could be overload.
 */
public final class StorageUtils {

    /** error result code for {@link #getSharedStorageFreeSpace}. */
    public static final int ERROR_CANNOT_STAT = -2;
    private static final String TAG = "StorageUtils";
    /** buffer size for file copy operations. */
    private static final int FILE_COPY_BUFFER_SIZE = 32768;

    /**
     * Filenames *STARTING* with this prefix are considered purgeable.
     */
    private static final String[] PURGEABLE_FILE_PREFIXES = new String[]{
            "tmp", "DbUpgrade", "DbExport", Logger.ERROR_LOG_FILE};

    /** Mime type for exporting database files. */
    private static final String MIME_TYPE_SQLITE = "application/x-sqlite3";
    private static final int TO_MEGABYTES = 1_000_000;
    private static final int TO_KILOBYTES = 1_000;

    private StorageUtils() {
    }

    /**
     * Make sure the Shared Storage directories are accessible.
     * <p>
     * Only called from StartupActivity, after permissions have been granted.
     *
     * @param context Current context
     *
     * @return 0 for all ok, or a StringRes with the appropriate error.
     * <p>
     * // Not needed any longer, as we only use getExternalFilesDir(String)
     * // and getExternalCacheDir(). Leaving this in comment as a reminder.
     * //@RequiresPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE)
     */
    @StringRes
    public static int initSharedDirectories(@NonNull final Context context)
            throws SecurityException {

        if (!isExternalStorageMounted()) {
            return R.string.error_storage_not_accessible;
        }

        try {
            // check we can get our root.
            getRootDir(context);

            // and create the log dir if needed.
            File dir = Logger.getLogDir(context);
            if (!(dir.isDirectory() || dir.mkdirs())) {
                return R.string.error_storage_not_writable;
            }

            // Prevent thumbnails showing up in the device Image Gallery.
            //noinspection ResultOfMethodCallIgnored
            new File(getCoverDir(context), MediaStore.MEDIA_IGNORE_FILENAME).createNewFile();

            return 0;

        } catch (@NonNull final ExternalStorageException | IOException e) {
            Logger.error(context, TAG, e, "initSharedDirectories failed");
            return R.string.error_storage_not_writable;
        }
    }

    /**
     * @param context Current context
     *
     * @return Space in bytes free on Shared Storage,
     * or {@link #ERROR_CANNOT_STAT} on error accessing it
     */
    public static long getSharedStorageFreeSpace(@NonNull final Context context) {
        try {
            StatFs stat = new StatFs(getRootDir(context).getPath());
            return stat.getAvailableBlocksLong() * stat.getBlockSizeLong();
        } catch (@NonNull final IllegalArgumentException | ExternalStorageException e) {
            Logger.error(context, TAG, e);
            return ERROR_CANNOT_STAT;
        }
    }

    /**
     * Check the current state of the primary shared/external storage media.
     */
    @SuppressWarnings("BooleanMethodIsAlwaysInverted")
    public static boolean isExternalStorageMounted() {
        /*
         * Returns the current state of the primary Shared Storage media.
         *
         * @see Environment#getExternalStorageDirectory()
         * @return one of {@link #MEDIA_UNKNOWN}, {@link #MEDIA_REMOVED},
         *         {@link #MEDIA_UNMOUNTED}, {@link #MEDIA_CHECKING},
         *         {@link #MEDIA_NOFS}, {@link #MEDIA_MOUNTED},
         *         {@link #MEDIA_MOUNTED_READ_ONLY}, {@link #MEDIA_SHARED},
         *         {@link #MEDIA_BAD_REMOVAL}, or {@link #MEDIA_UNMOUNTABLE}.
         */
        return Environment.MEDIA_MOUNTED.equals(Environment.getExternalStorageState());
    }

    /**
     * Get the cache directory.
     *
     * @return File
     *
     * @throws ExternalStorageException if the Shared Storage media is not available (not mounted)
     */
    public static File getCacheDir(@NonNull final Context context)
            throws ExternalStorageException {
        File dir = context.getExternalCacheDir();
        if (dir == null) {
            throw new ExternalStorageException();
        }
        return dir;
    }

    /**
     * Root external storage aka Shared Storage.
     *
     * @return the Shared Storage <strong>root</strong> Directory object
     *
     * @throws ExternalStorageException if the Shared Storage media is not available (not mounted)
     */
    public static File getRootDir(@NonNull final Context context)
            throws ExternalStorageException {
        File dir = context.getExternalFilesDir(null);
        if (dir == null) {
            throw new ExternalStorageException();
        }
        return dir;
    }

    /**
     * Covers storage location.
     *
     * @return the Shared Storage <strong>covers</strong> Directory object
     *
     * @throws ExternalStorageException if the Shared Storage media is not available (not mounted)
     */
    public static File getCoverDir(@NonNull final Context context)
            throws ExternalStorageException {
        File dir = context.getExternalFilesDir(Environment.DIRECTORY_PICTURES);
        if (dir == null) {
            throw new ExternalStorageException();
        }
        return dir;
    }

    /**
     * return the cover for the given uuid. We'll attempt to find a jpg or a png.
     * If no file found, a jpg place holder is returned.
     * Keep in mind that internally we always use PNG compression (except for the cache).
     * So a jpg named file can be a png encoded file. (But we don't need to care about that.)
     *
     * The index only gets appended to the name if it's > 0.
     *
     * @param context Current context
     * @param uuid    UUID of the book
     * @param cIdx    0..n image index
     *
     * @return The File object for existing files, or a new jpg placeholder.
     *
     * @throws ExternalStorageException if the Shared Storage media is not available (not mounted)
     */
    @NonNull
    public static File getCoverFileForUuid(@NonNull final Context context,
                                           @NonNull final String uuid,
                                           final int cIdx)
            throws ExternalStorageException {
        final File coverDir = getCoverDir(context);

        String name = uuid + (cIdx > 0 ? "_" + cIdx : "");

        final File jpg = new File(coverDir, name + ".jpg");
        if (jpg.exists()) {
            return jpg;
        }
        // could be a png
        final File png = new File(coverDir, name + ".png");
        if (png.exists()) {
            return png;
        }

        // we need a new file, return a placeholder
        return jpg;
    }

    /**
     * Get a temporary file for a cover being processed.
     *
     * @param context Current context
     * @param name    for the file.
     *
     * @return a temp cover file spec.
     *
     * @throws ExternalStorageException if the Shared Storage media is not available (not mounted)
     */
    public static File getTempCoverFile(@NonNull final Context context,
                                        @NonNull final String name)
            throws ExternalStorageException {
        return new File(getCacheDir(context), "tmp" + name + ".jpg");
    }

    /**
     * Given a InputStream, write it to a file.
     * We first write to a temporary file, so an existing 'out' file is not destroyed
     * if the stream somehow fails.
     *
     * @param context Current context
     * @param is      InputStream to read
     * @param file    File to write to
     *
     * @return File written to (the one passed in), or {@code null} if writing failed.
     */
    public static File saveInputStreamToFile(@NonNull final Context context,
                                             @Nullable final InputStream is,
                                             @NonNull final File file) {
        Objects.requireNonNull(is, "no InputStream");

        File tmpFile = getTempCoverFile(context, "is");
        try (OutputStream os = new FileOutputStream(tmpFile)) {
            if (copy(is, os) > 0) {
                // All OK, rename to real output file
                renameFile(tmpFile, file);
                return file;
            }
        } catch (@NonNull final IOException ignore) {
            // ignore
        } finally {
            deleteFile(tmpFile);
        }

        return null;
    }

    /**
     * Count size + (optional) Cleanup any purgeable files.
     * <p>
     * Does <strong>not</strong> enter subdirectories.
     *
     * @param context      Current context
     * @param reallyDelete if {@code true}, delete files, if {@code false} only count bytes
     *
     * @return the total size in bytes of purgeable/purged files.
     */
    public static long purgeFiles(@NonNull final Context context,
                                  final boolean reallyDelete) {
        long totalSize = 0;
        try {
            totalSize += purgeDir(Logger.getLogDir(context), reallyDelete);
            totalSize += purgeDir(getCoverDir(context), reallyDelete);
            totalSize += purgeDir(getRootDir(context), reallyDelete);
            totalSize += purgeDir(getCacheDir(context), reallyDelete);

        } catch (@NonNull final SecurityException | ExternalStorageException e) {
            // not critical, just log it.
            Logger.error(context, TAG, e);
        }
        return totalSize;
    }

    /**
     * Does <strong>not</strong> enter subdirectories.
     *
     * @param dir          to purge
     * @param reallyDelete if {@code true}, delete files, if {@code false} only count bytes
     *
     * @return number of bytes (potentially) deleted
     */
    private static long purgeDir(@NonNull final File dir,
                                 final boolean reallyDelete)
            throws ExternalStorageException {
        long totalSize = 0;
        if (dir.isDirectory()) {
            //noinspection ConstantConditions
            for (String name : dir.list()) {
                for (String prefix : PURGEABLE_FILE_PREFIXES) {
                    if (name.startsWith(prefix)) {
                        File file = new File(name);
                        if (file.isFile()) {
                            totalSize += file.length();
                            if (reallyDelete) {
                                deleteFile(file);
                            }
                        }
                    }
                }
            }
        }
        return totalSize;
    }

    /**
     * Convenience wrapper for {@link File#delete()}.
     *
     * @param file to delete
     */
    public static void deleteFile(@Nullable final File file) {
        if (file != null && file.exists()) {
            try {
                //noinspection ResultOfMethodCallIgnored
                file.delete();
            } catch (@NonNull final SecurityException e) {
                Logger.error(TAG, e);

            } catch (@NonNull final RuntimeException e) {
                if (BuildConfig.DEBUG /* always */) {
                    Log.d(TAG, "failed to delete file=" + file, e);
                }
            }
        }
    }

    /**
     * ENHANCE: make suitable for multiple filesystems using {@link #copyFile(File, File)}
     * Android docs {@link File#renameTo(File)}: Both paths be on the same mount point.
     *
     * @param source      File to rename
     * @param destination new name
     *
     * @return {@code true} if the rename worked, this is really a ".exists()" call.
     * and not relying on the OS renameTo call.
     */
    public static boolean renameFile(@NonNull final File source,
                                     @NonNull final File destination) {
        if (source.exists()) {
            try {
                //noinspection ResultOfMethodCallIgnored
                source.renameTo(destination);
            } catch (@NonNull final RuntimeException e) {
                if (BuildConfig.DEBUG /* always */) {
                    Log.d(TAG, "failed to rename source=" + source
                               + "TO destination" + destination, e);
                }
            }
        }
        return destination.exists();
    }

    /**
     * Convenience method to export all database files.
     *
     * @param context        Current context
     * @param destinationDir the folder where to copy the files to
     *
     * @throws IOException on failure
     */
    public static void exportDatabaseFiles(@NonNull final Context context,
                                           @NonNull final DocumentFile destinationDir)
            throws IOException {

        exportFile(context, DBHelper.getDatabasePath(context),
                   MIME_TYPE_SQLITE, destinationDir);

        File coversDb = CoversDAO.CoversDbHelper.getDatabasePath(context);
        if (coversDb.exists()) {
            exportFile(context, coversDb, MIME_TYPE_SQLITE, destinationDir);
        }
    }

    /**
     * Export the source File to the destination directory specified by the DocumentFile.
     *
     * @param context        Current context
     * @param file           to copy
     * @param mimeType       to use for writing
     * @param destinationDir the folder where to copy the file to
     *
     * @throws IOException on failure
     */
    private static void exportFile(@NonNull final Context context,
                                   @NonNull final File file,
                                   @SuppressWarnings("SameParameterValue")
                                   @NonNull final String mimeType,
                                   @NonNull final DocumentFile destinationDir)
            throws IOException {

        DocumentFile destinationFile = destinationDir.createFile(mimeType, file.getName());
        if (destinationFile == null) {
            throw new IOException("destination file was NULL");
        }

        Uri destinationUri = destinationFile.getUri();
        try (InputStream is = new FileInputStream(file);
             OutputStream os = context.getContentResolver().openOutputStream(destinationUri)) {
            if (os == null) {
                throw new IOException("OutputStream was NULL");
            }
            copy(is, os);
        }
    }

    /**
     * Export the source File to the destination Uri.
     *
     * @param context     Application context
     * @param source      File
     * @param destination Uri
     *
     * @throws IOException on failure
     */
    public static void exportFile(@NonNull final Context context,
                                  @NonNull final File source,
                                  @NonNull final Uri destination)
            throws IOException {
        ContentResolver cr = context.getContentResolver();

        try (InputStream is = new FileInputStream(source);
             OutputStream os = cr.openOutputStream(destination)) {
            if (os != null) {
                copy(is, os);
            }
        }
    }

    /**
     * @param source          file to export
     * @param destinationFile destination file name
     */
    public static void copyFileWithBackup(@NonNull final File source,
                                          @NonNull final File destinationFile) {
        try {
            // rename the existing file
            renameFile(destinationFile, new File(destinationFile.getPath() + ".bak"));
            // and create a new copy
            copyFile(source, destinationFile);

        } catch (@NonNull final IOException e) {
            Logger.error(TAG, e);
        }
    }

    /**
     * Rename the source file to the destFilename; keeping 'copies' of the old file.
     *
     * @param source      file to rename
     * @param destination final destination file
     * @param copies      #copies of the previous one to keep
     */
    public static void copyFileWithBackup(@NonNull final File source,
                                          @NonNull final File destination,
                                          final int copies)
            throws ExternalStorageException {

        String parentDir = source.getParent();
        // remove the oldest copy
        File previous = new File(parentDir, destination + "." + copies);
        deleteFile(previous);

        // now bump each copy up one suffix.
        for (int i = copies - 1; i > 0; i--) {
            File current = new File(parentDir, destination + "." + i);
            renameFile(current, previous);
            previous = current;
        }

        // Give the previous file a suffix.
        renameFile(destination, previous);
        // and write the new copy.
        renameFile(source, destination);
    }

    /**
     * Private filesystem only - Copy the source File to the destination File.
     *
     * @param source      file
     * @param destination file
     *
     * @throws IOException on failure
     */
    public static void copyFile(@NonNull final File source,
                                @NonNull final File destination)
            throws IOException {
        try (InputStream is = new FileInputStream(source);
             OutputStream os = new FileOutputStream(destination)) {
            copy(is, os);
        } catch (@NonNull final FileNotFoundException ignore) {
        }
    }

    /**
     * Copy the InputStream to the OutputStream.
     * Neither stream is closed here.
     *
     * @param is InputStream
     * @param os OutputStream
     *
     * @return total number of bytes copied.
     *
     * @throws IOException on failure
     */
    public static int copy(@NonNull final InputStream is,
                           @NonNull final OutputStream os)
            throws IOException {
        byte[] buffer = new byte[FILE_COPY_BUFFER_SIZE];
        int total = 0;
        int nRead;
        while ((nRead = is.read(buffer)) > 0) {
            os.write(buffer, 0, nRead);
            total += nRead;
        }
        os.flush();

        return total;
    }

    /**
     * Channels are FAST... TODO: replace old method with this one.
     * but needs testing, never used it on Android myself
     *
     * @param source      file
     * @param destination file
     *
     * @throws IOException on failure
     */
    @SuppressWarnings("unused")
    private static void copyFile2(@NonNull final File source,
                                  @NonNull final File destination)
            throws IOException {
        FileInputStream is = new FileInputStream(source);
        FileOutputStream os = new FileOutputStream(destination);
        FileChannel inChannel = is.getChannel();
        FileChannel outChannel = os.getChannel();

        try {
            inChannel.transferTo(0, inChannel.size(), outChannel);
        } finally {
            if (inChannel != null) {
                inChannel.close();
            }
            outChannel.close();
            is.close();
            os.close();
        }
    }

    /**
     * Format a number of bytes in a human readable form.
     * <p>
     * 2019-03-16: decimal as per <a href="https://en.wikipedia.org/wiki/File_size">IEC</a>
     *
     * @param context Current context
     * @param bytes   to format
     *
     * @return formatted # bytes
     */
    @NonNull
    public static String formatFileSize(@NonNull final Context context,
                                        final float bytes) {
        if (bytes < 3_000) {
            // Show 'bytes' if < 3k
            return context.getString(R.string.bytes, bytes);
        } else if (bytes < 250_000) {
            // Show Kb if less than 250kB
            return context.getString(R.string.kilobytes, bytes / TO_KILOBYTES);
        } else {
            // Show MB otherwise...
            return context.getString(R.string.megabytes, bytes / TO_MEGABYTES);
        }
    }
}
