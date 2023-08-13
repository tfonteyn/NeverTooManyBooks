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
package com.hardbacknutter.nevertoomanybooks.core.storage;

import android.os.Build;
import android.os.StatFs;
import android.system.ErrnoException;
import android.system.OsConstants;
import android.webkit.MimeTypeMap;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;
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
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.CRC32;

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
 */
public final class FileUtils {

    /**
     * Buffer size for file copy operations.
     * 8192 is what Android 10 android.os.FileUtils.copy uses.
     */
    private static final int FILE_COPY_BUFFER_SIZE = 8192;
    private static final String ERROR_SOURCE_MISSING = "Source does not exist: ";
    private static final String ERROR_FAILED_TO_RENAME = "Failed to rename: ";

    private FileUtils() {
    }

    /**
     * Copy the InputStream to the OutputStream.
     * Neither stream is closed here.
     *
     * @param is InputStream
     * @param os OutputStream
     *
     * @throws IOException on generic/other IO failures
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
     * Copy the source File to the destination File.
     *
     * @param source      file
     * @param destination file
     *
     * @throws IOException on generic/other IO failures
     */
    public static void copy(@NonNull final File source,
                            @NonNull final File destination)
            throws IOException {
        try (FileInputStream fis = new FileInputStream(source);
             FileOutputStream fos = new FileOutputStream(destination)) {
            copy(fis, fos);
        }
    }

    /**
     * Copy the source File to the destination File.
     *
     * @param source      file
     * @param destination file
     *
     * @throws IOException on generic/other IO failures
     */
    public static void copy(@NonNull final FileInputStream source,
                            @NonNull final FileOutputStream destination)
            throws IOException {

        try (FileChannel inChannel = source.getChannel();
             FileChannel outChannel = destination.getChannel()) {
            inChannel.transferTo(0, inChannel.size(), outChannel);
        }
    }

    /**
     * Rename the given "file" to "file.1", keeping 'copies' of the old file,
     * i.e. the number of the copy is added as a SUFFIX to the name.
     * <p>
     * Upon return, the "file" is no longer available.
     *
     * <strong>Important:</strong> it's a 'rename', so single volume use only!
     *
     * @param file   file to rename
     * @param copies #copies of the previous one to keep
     *
     * @throws IOException on generic/other IO failures
     */
    public static void renameAsBackup(@NonNull final File file,
                                      final int copies)
            throws IOException {
        // remove the oldest copy
        File previous = new File(file + "." + copies);
        delete(previous);

        // now bump each copy up one suffix.
        for (int i = copies - 1; i > 0; i--) {
            final File current = new File(file + "." + i);
            if (current.exists()) {
                rename(current, previous);
            }
            previous = current;
        }

        // Rename the current file giving it a suffix.
        if (file.exists()) {
            rename(file, previous);
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
     * @throws FileNotFoundException if the source does not exist
     * @throws IOException           on generic/other IO failures
     */
    public static void rename(@NonNull final File source,
                              @NonNull final File destination)
            throws IOException {

        //sanity check
        if (source.getAbsolutePath().equals(destination.getAbsolutePath())) {
            return;
        }

        if (!source.exists()) {
            throw new FileNotFoundException(ERROR_SOURCE_MISSING + source);
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
     * Convenience wrapper for {@link File#delete()}.
     *
     * @param file to delete
     */
    public static void delete(@Nullable final File file) {
        if (file != null && file.exists()) {
            try {
                //noinspection ResultOfMethodCallIgnored
                file.delete();
            } catch (@NonNull final /* SecurityException */ RuntimeException ignore) {
                // ignore
            }
        }
    }

    /**
     * Recursively delete files.
     * Does <strong>NOT</strong> delete the actual directory or any actual subdirectories.
     *
     * @param root   directory
     * @param filter (optional) to apply; {@code null} for all files.
     *
     * @return number of bytes deleted
     */
    public static long deleteDirectory(@NonNull final File root,
                                       @Nullable final FileFilter filter) {
        long totalSize = 0;
        // sanity check
        if (root.isDirectory()) {
            final File[] files = root.listFiles(filter);
            // sanity check
            if (files != null) {
                for (final File file : files) {
                    if (file.isFile()) {
                        totalSize += file.length();
                        delete(file);
                    } else if (file.isDirectory()) {
                        totalSize += deleteDirectory(file, filter);
                    }
                }
            }
        }
        return totalSize;
    }

    /**
     * Get the space free in this directory.
     *
     * @param root directory
     *
     * @return number of bytes free
     *
     * @throws IOException on generic/other IO failures
     */
    public static long getFreeSpace(@NonNull final File root)
            throws IOException {
        try {
            return new StatFs(root.getPath()).getAvailableBytes();

        } catch (@NonNull final IllegalArgumentException e) {
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
            final File[] files = root.listFiles(filter);
            // sanity check
            if (files != null) {
                for (final File file : files) {
                    if (file.isFile()) {
                        totalSize += file.length();
                    } else if (file.isDirectory()) {
                        totalSize += getUsedSpace(file, filter);
                    }
                }
            }
        }
        return totalSize;
    }

    /**
     * Collect applicable files for the given directory - subdirectories are ignored.
     *
     * @param root   directory to collect from
     * @param filter (optional) to apply; {@code null} for all files.
     *
     * @return list of files
     */
    @NonNull
    public static List<File> collectFiles(@NonNull final File root,
                                          @Nullable final FileFilter filter) {
        return collectFiles(root, filter, Integer.MAX_VALUE);
    }

    // allow faster testing by limiting the number of files
    @VisibleForTesting
    @NonNull
    public static List<File> collectFiles(@NonNull final File root,
                                          @Nullable final FileFilter filter,
                                          final int maxFiles) {
        final List<File> list = new ArrayList<>();
        // sanity check
        if (root.isDirectory()) {
            final File[] files = root.listFiles(filter);
            if (files != null && files.length > 0) {
                int i = 0;
                while (i < files.length && list.size() < maxFiles) {
                    if (files[i].isFile()) {
                        list.add(files[i]);
                    }
                    i++;
                }
            }
        }
        return list;
    }

    /**
     * Calculate the CRC32 checksum for the given file.
     *
     * @param file to parse
     *
     * @return crc32
     *
     * @throws IOException on generic/other IO failures
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
     * Mutate the given filename to make it valid for a FAT or ext4 filesystem,
     * replacing any invalid characters with "_".
     * <p>
     * The main usage is when dealing with
     * {@link DocumentFile#createFile(String, String)} which DOES, and
     * {@link DocumentFile#findFile(String)} which does NOT
     * convert invalid characters.
     * <p>
     * Combines the hidden methods:
     * <ul>
     *  <li>{@link android.os.FileUtils}#buildValidFatFilename</li>
     *  <li>{@link android.os.FileUtils}#buildValidExtFilename</li>
     *  <li>{@link android.os.FileUtils}#trimFilename</li>
     * </ul>
     *
     * @param name the file name to mutate
     *
     * @return the actual file name to use
     *
     * @throws FileNotFoundException on ...
     */
    @NonNull
    public static String buildValidFilename(@Nullable final String name)
            throws FileNotFoundException {
        if (name == null || name.isEmpty() || ".".equals(name) || "..".equals(name)) {
            throw new FileNotFoundException();
        }

        final StringBuilder sb = new StringBuilder(name.length());
        for (int i = 0; i < name.length(); i++) {
            final char c = name.charAt(i);
            if (isValidFilenameChar(c)) {
                sb.append(c);
            } else {
                sb.append('_');
            }
        }
        // Even though vfat allows 255 UCS-2 chars, we might eventually write to
        // ext4 through a FUSE layer, so use that limit.
        int maxBytes = 255;
        byte[] raw = sb.toString().getBytes(StandardCharsets.UTF_8);
        if (raw.length > maxBytes) {
            maxBytes -= 3;
            while (raw.length > maxBytes) {
                sb.deleteCharAt(sb.length() / 2);
                raw = sb.toString().getBytes(StandardCharsets.UTF_8);
            }
            sb.insert(sb.length() / 2, "...");
        }
        return sb.toString();
    }

    /**
     * Check file name character.
     * <p>
     * Combines the hidden methods:
     * <ul>
     *  <li>android.os.FileUtils#isValidFatFilenameChar</li>
     *  <li>android.os.FileUtils#isValidExtFilenameChar</li>
     * </ul>
     *
     * @param c char to check
     *
     * @return flag
     */
    private static boolean isValidFilenameChar(final char c) {
        if (c <= 0x1f) {
            return false;
        }
        switch (c) {
            case '"':
            case '*':
            case '/':
            case ':':
            case '<':
            case '>':
            case '?':
            case '\\':
            case '|':
            case 0x7F:
                return false;
            default:
                return true;
        }
    }

    /**
     * Try to determine the MIME type associated with the given file extension.
     *
     * @param fileExt to check
     *
     * @return the MIME type
     */
    @NonNull
    public static String getMimeTypeFromExtension(@NonNull final String fileExt) {
        final String mimeType = MimeTypeMap.getSingleton().getMimeTypeFromExtension(fileExt);
        if (mimeType != null) {
            return mimeType;
        }

        if ("db".equals(fileExt)) {
            // https://www.iana.org/assignments/media-types/application/vnd.sqlite3
            // application/x-sqlite3 is deprecated
            return "application/vnd.sqlite3";
        }

        // fallback
        return "application/" + fileExt;
    }

    /**
     * Check if the given Exception is an IOException representing a  "Disk Full" error.
     *
     * @param e to check
     *
     * @return {@code true} if it is
     */
    public static boolean isDiskFull(@Nullable final Exception e) {
        return e instanceof IOException
               && e.getCause() instanceof ErrnoException
               && ((ErrnoException) e.getCause()).errno == OsConstants.ENOSPC;
    }
}
