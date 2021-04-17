/*
 * @Copyright 2018-2021 HardBackNutter
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
package com.hardbacknutter.nevertoomanybooks.utils;

import android.content.ContentResolver;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.os.StatFs;
import android.provider.OpenableColumns;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.documentfile.provider.DocumentFile;

import java.io.File;
import java.io.FileFilter;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.channels.FileChannel;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.CRC32;

import com.hardbacknutter.nevertoomanybooks.BuildConfig;
import com.hardbacknutter.nevertoomanybooks.R;
import com.hardbacknutter.nevertoomanybooks.debug.Logger;

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
 * Also see {@link AppDir}.
 */
public final class FileUtils {

    /** Log tag. */
    private static final String TAG = "FileUtils";
    /**
     * Buffer size for file copy operations.
     * 8192 is what Android 10 android.os.FileUtils.copy uses.
     */
    private static final int FILE_COPY_BUFFER_SIZE = 8192;
    /** Bytes to Mb: decimal as per <a href="https://en.wikipedia.org/wiki/File_size">IEC</a>. */
    private static final int TO_MEGABYTES = 1_000_000;
    /** Bytes to Kb: decimal as per <a href="https://en.wikipedia.org/wiki/File_size">IEC</a>. */
    private static final int TO_KILOBYTES = 1_000;
    private static final String ERROR_SOURCE_MISSING = "Source does not exist: ";
    private static final String ERROR_FAILED_TO_RENAME = "Failed to rename: ";
    private static final String ERROR_COULD_NOT_RESOLVE_URI = "Could not resolve uri=";
    private static final String ERROR_UNKNOWN_SCHEME_FOR_URI = "Unknown scheme for uri: ";
    private static final String ERROR_COULD_NOT_CREATE_FILE = "Could not create file=";

    private FileUtils() {
    }

    /**
     * Convenience wrapper for {@link File#delete()}.
     *
     * @param file to delete
     */
    public static void delete(@Nullable final File file) {
        if (file != null && file.exists()) {
            try {
                //noinspection ResultOfMethodCallIgnored
                file.delete();
            } catch (@NonNull final /* SecurityException */ RuntimeException e) {
                if (BuildConfig.DEBUG /* always */) {
                    Logger.e(TAG, "delete|file=" + file, e);
                }
            }
        }
    }

    /**
     * ENHANCE: make suitable for multiple filesystems using {@link #copy(File, File)}
     * Android docs {@link File#renameTo(File)}: Both paths be on the same mount point.
     * But we use the external app directory solely, so a 'rename' works as is for now.
     *
     * @param source      File to rename
     * @param destination new name
     *
     * @throws IOException on failure
     */
    public static void rename(@NonNull final File source,
                              @NonNull final File destination)
            throws IOException {

        //sanity check
        if (source.getAbsolutePath().equals(destination.getAbsolutePath())) {
            if (BuildConfig.DEBUG /* always */) {
                Logger.e(TAG, "renameOrThrow|source==destination=="
                              + source.getAbsolutePath(), new Throwable()
                        );
            }
            return;
        }

        if (!source.exists()) {
            throw new IOException(ERROR_SOURCE_MISSING + source);
        }

        try {
            if (source.renameTo(destination)) {
                return;
            }
            throw new IOException(ERROR_FAILED_TO_RENAME + source + " TO " + destination);

        } catch (@NonNull final SecurityException e) {
            throw new IOException(ERROR_FAILED_TO_RENAME + source + " TO " + destination, e);
        }
    }

    /**
     * Given a InputStream, write it to a file.
     * We first write to a temporary file, so an existing 'out' file is not destroyed
     * if the stream somehow fails.
     *
     * @param is       InputStream to read
     * @param destFile File to write to
     *
     * @return File written to (the one passed in)
     *
     * @throws FileNotFoundException if the input stream was {@code null}
     * @throws IOException           on failure
     */
    @NonNull
    public static File copyInputStream(@Nullable final InputStream is,
                                       @NonNull final File destFile)
            throws IOException {
        if (is == null) {
            throw new FileNotFoundException("InputStream was NULL");
        }

        final File tmpFile = new File(AppDir.Temp.getDir(), System.nanoTime() + ".jpg");
        try (OutputStream os = new FileOutputStream(tmpFile)) {
            copy(is, os);
            // rename to real output file
            rename(tmpFile, destFile);
            return destFile;
        } finally {
            delete(tmpFile);
        }
    }

    /**
     * Export the source File to the destination directory specified by the DocumentFile.
     *
     * @param context  Current context
     * @param file     to copy
     * @param mimeType to use for writing
     * @param destDir  the folder where to copy the file to
     *
     * @throws IOException on failure
     */
    public static void copy(@NonNull final Context context,
                            @NonNull final File file,
                            @SuppressWarnings("SameParameterValue")
                            @NonNull final String mimeType,
                            @NonNull final DocumentFile destDir)
            throws IOException {

        final DocumentFile destinationFile = destDir.createFile(mimeType, file.getName());
        if (destinationFile == null) {
            throw new IOException(ERROR_COULD_NOT_CREATE_FILE + file.getName());
        }

        final Uri destinationUri = destinationFile.getUri();
        try (InputStream is = new FileInputStream(file);
             OutputStream os = context.getContentResolver().openOutputStream(destinationUri)) {
            if (os == null) {
                throw new IOException(ERROR_COULD_NOT_RESOLVE_URI + destinationUri);
            }
            copy(is, os);
        }
    }

    /**
     * Export the source File to the destination Uri.
     *
     * @param context Current context
     * @param source  File
     * @param destUri Uri
     *
     * @throws IOException on failure
     */
    public static void copy(@NonNull final Context context,
                            @NonNull final File source,
                            @NonNull final Uri destUri)
            throws IOException {

        try (InputStream is = new FileInputStream(source);
             OutputStream os = context.getContentResolver().openOutputStream(destUri)) {
            if (os != null) {
                copy(is, os);
            }
        }
    }

    /**
     * Copy the source file to the destFilename; keeping 'copies' of the old file.
     * <p>
     * The number of the copy is added as a SUFFIX to the name.
     *
     * @param source      file to copy
     * @param destination final destination file
     * @param copies      #copies of the previous one to keep
     *
     * @throws IOException on failure
     */
    public static void copyWithBackup(@NonNull final File source,
                                      @NonNull final File destination,
                                      final int copies)
            throws IOException {

        // remove the oldest copy
        File previous = new File(destination + "." + copies);
        delete(previous);

        // now bump each copy up one suffix.
        for (int i = copies - 1; i > 0; i--) {
            final File current = new File(destination + "." + i);
            if (current.exists()) {
                rename(current, previous);
            }
            previous = current;
        }

        // Give the previous file a suffix.
        if (destination.exists()) {
            rename(destination, previous);
        }

        // and write the new copy.
        copy(source, destination);
    }

    /**
     * Copy the InputStream to the OutputStream.
     * Neither stream is closed here.
     *
     * @param is InputStream
     * @param os OutputStream
     *
     * @throws IOException on failure
     */
    public static void copy(@NonNull final InputStream is,
                            @NonNull final OutputStream os)
            throws IOException {
        if (Build.VERSION.SDK_INT >= 29) {
            android.os.FileUtils.copy(is, os);

        } else {
            final byte[] buffer = new byte[FILE_COPY_BUFFER_SIZE];
            int nRead;
            while ((nRead = is.read(buffer)) > 0) {
                os.write(buffer, 0, nRead);
            }
            os.flush();
        }
    }

    /**
     * Private filesystem only - Copy the source File to the destination File.
     *
     * @param source      file
     * @param destination file
     *
     * @throws IOException on failure
     */
    public static void copy(@NonNull final File source,
                            @NonNull final File destination)
            throws IOException {
        try (InputStream is = new FileInputStream(source);
             OutputStream os = new FileOutputStream(destination)) {
            copy(is, os);
        } catch (@NonNull final FileNotFoundException ignore) {
            // ignore
        }
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
    private static void copy2(@NonNull final File source,
                              @NonNull final File destination)
            throws IOException {
        try (FileChannel inChannel = new FileInputStream(source).getChannel();
             FileChannel outChannel = new FileOutputStream(destination).getChannel()) {
            inChannel.transferTo(0, inChannel.size(), outChannel);
        }
    }

    /**
     * Format a number of bytes in a human readable form.
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

    /**
     * Get the name and size of the content behind a Uri.
     * <p>
     * Dev Note: alternatively use {@link DocumentFile} 'from' methods
     * and {@link DocumentFile#getName()} / {@link DocumentFile#length()} but that's TWO queries.
     *
     * @param context Current context
     * @param uri     to inspect
     *
     * @return a UriInfo
     */
    @NonNull
    public static UriInfo getUriInfo(@NonNull final Context context,
                                     @NonNull final Uri uri) {

        final String scheme = uri.getScheme();
        if (scheme == null) {
            throw new IllegalStateException(ERROR_UNKNOWN_SCHEME_FOR_URI + uri);
        }

        if (ContentResolver.SCHEME_CONTENT.equals(scheme)) {
            final ContentResolver contentResolver = context.getContentResolver();
            final String[] columns = new String[]{OpenableColumns.DISPLAY_NAME,
                                                  OpenableColumns.SIZE};
            try (Cursor cursor = contentResolver.query(uri, columns, null, null, null)) {
                if (cursor != null && cursor.moveToFirst()) {

                    // display name
                    final String name = cursor.getString(0);
                    // 0 for a directory
                    final long size = cursor.getLong(1);

                    // sanity check, according to the android.provider.OpenableColumns
                    // documentation, the name and size MUST be present.
                    if (name != null && !name.isEmpty()) {
                        return new UriInfo(uri, name, size);
                    }
                }
            }
        } else if (ContentResolver.SCHEME_FILE.equals(scheme)) {
            final String path = uri.getPath();
            if (path != null) {
                final File file = new File(path);
                // sanity check
                if (file.exists()) {
                    return new UriInfo(uri, file.getName(), file.length());
                }
            }
        } else if (scheme.startsWith("http")) {
            return new UriInfo(uri, uri.toString(), 0);
        }

        throw new IllegalStateException(ERROR_UNKNOWN_SCHEME_FOR_URI + uri);
    }

    /**
     * Calculate the CRC32 checksum for the given file.
     *
     * @param file to parse
     *
     * @return crc32
     *
     * @throws IOException on failure
     */
    @NonNull
    public static CRC32 getCrc32(@NonNull final File file)
            throws IOException {
        final CRC32 crc32 = new CRC32();
        final byte[] buffer = new byte[FILE_COPY_BUFFER_SIZE];
        try (InputStream is = new FileInputStream(file)) {
            int nRead;
            while ((nRead = is.read(buffer)) > 0) {
                crc32.update(buffer, 0, nRead);
            }
        }
        return crc32;
    }

    /**
     * Get the space free in this directory.
     *
     * @param root directory
     *
     * @return number of bytes free
     *
     * @throws IOException on failure
     */
    public static long getFreeSpace(@NonNull final File root)
            throws IOException {
        try {
            return new StatFs(root.getPath()).getAvailableBytes();

        } catch (@NonNull final IllegalArgumentException e) {
            Logger.error(TAG, e);
            throw new IOException(e);
        }
    }

    /**
     * Recursively get the space used by this directory.
     *
     * @param root   directory
     * @param filter (optional) to apply; {@code null} for all files.
     *
     * @return number of bytes used
     */
    public static long getUsedSpace(@NonNull final File root,
                                    @Nullable final FileFilter filter) {
        long totalSize = 0;
        // sanity check
        if (root.isDirectory()) {
            //noinspection ConstantConditions
            for (final File file : root.listFiles(filter)) {
                if (file.isFile()) {
                    totalSize += file.length();
                } else if (file.isDirectory()) {
                    totalSize += getUsedSpace(file, filter);
                }
            }
        }
        return totalSize;
    }

    /**
     * Recursively delete files.
     * Does <strong>NOT</strong> delete the directory or any subdirectories.
     *
     * @param root   directory
     * @param filter (optional) to apply; {@code null} for all files.
     *
     * @return number of bytes deleted
     */
    public static long deleteFiles(@NonNull final File root,
                                   @Nullable final FileFilter filter) {
        long totalSize = 0;
        // sanity check
        if (root.isDirectory()) {
            //noinspection ConstantConditions
            for (final File file : root.listFiles(filter)) {
                if (file.isFile()) {
                    totalSize += file.length();
                    delete(file);
                } else if (file.isDirectory()) {
                    totalSize += deleteFiles(file, filter);
                }
            }
        }
        return totalSize;
    }

    /**
     * Recursively collect applicable files for the given directory.
     *
     * @param root   directory to collect from
     * @param filter (optional) to apply; {@code null} for all files.
     *
     * @return list of files
     */
    @SuppressWarnings("WeakerAccess")
    @NonNull
    public static List<File> collectFiles(@NonNull final File root,
                                          @Nullable final FileFilter filter) {
        final List<File> files = new ArrayList<>();
        // sanity check
        if (root.isDirectory()) {
            //noinspection ConstantConditions
            for (final File file : root.listFiles(filter)) {
                if (file.isFile()) {
                    files.add(file);
                } else if (file.isDirectory()) {
                    files.addAll(collectFiles(file, filter));
                }
            }
        }
        return files;
    }

    public static class UriInfo {

        @NonNull
        private final Uri mUri;
        @NonNull
        private final String mDisplayName;
        private final long mSize;

        public UriInfo(@NonNull final Uri uri,
                       @NonNull final String displayName,
                       final long size) {
            mUri = uri;
            mDisplayName = displayName;
            mSize = size;
        }

        @NonNull
        public Uri getUri() {
            return mUri;
        }

        @NonNull
        public String getDisplayName() {
            return mDisplayName;
        }

        public long getSize() {
            return mSize;
        }
    }
}
