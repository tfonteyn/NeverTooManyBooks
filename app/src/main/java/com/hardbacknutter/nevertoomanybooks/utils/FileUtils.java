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
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.documentfile.provider.DocumentFile;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.channels.FileChannel;

import com.hardbacknutter.nevertoomanybooks.App;
import com.hardbacknutter.nevertoomanybooks.BuildConfig;
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
 * Also see {@link AppDir}.
 */
public final class FileUtils {

    /** Log tag. */
    private static final String TAG = "FileUtils";
    /** buffer size for file copy operations. */
    private static final int FILE_COPY_BUFFER_SIZE = 65535;

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
            } catch (@NonNull final SecurityException e) {
                Logger.error(App.getAppContext(), TAG, e);

            } catch (@NonNull final RuntimeException e) {
                if (BuildConfig.DEBUG /* always */) {
                    Log.d(TAG, "failed to delete file=" + file, e);
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
     * @return {@code true} if the rename worked, this is really a ".exists()" call.
     * and not relying on the OS renameTo call.
     */
    public static boolean rename(@NonNull final File source,
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
     * Given a InputStream, write it to a file.
     * We first write to a temporary file, so an existing 'out' file is not destroyed
     * if the stream somehow fails.
     *
     * @param context  Current context
     * @param is       InputStream to read
     * @param destFile File to write to
     *
     * @return File written to (the one passed in), or {@code null} if writing failed.
     *
     * @throws IOException on failure
     */
    @Nullable
    public static File copyInputStream(@NonNull final Context context,
                                       @Nullable final InputStream is,
                                       @NonNull final File destFile)
            throws ExternalStorageException, IOException {
        if (is == null) {
            return null;
        }
        File tmpFile = AppDir.Cache.getFile(context, "stream.jpg");
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

        DocumentFile destinationFile = destDir.createFile(mimeType, file.getName());
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
     * @param context Application context
     * @param source  File
     * @param destUri Uri
     *
     * @throws IOException on failure
     */
    public static void copy(@NonNull final Context context,
                            @NonNull final File source,
                            @NonNull final Uri destUri)
            throws IOException {
        ContentResolver cr = context.getContentResolver();

        try (InputStream is = new FileInputStream(source);
             OutputStream os = cr.openOutputStream(destUri)) {
            if (os != null) {
                copy(is, os);
            }
        }
    }

    /**
     * Rename the source file to the destFilename; keeping 'copies' of the old file.
     * <p>
     * The number of the copy is added as a SUFFIX to the name.
     *
     * @param source      file to rename
     * @param destination final destination file
     * @param copies      #copies of the previous one to keep
     */
    public static void copyWithBackup(@NonNull final File source,
                                      @NonNull final File destination,
                                      final int copies) {

        String parentDir = source.getParent();
        // remove the oldest copy
        File previous = new File(parentDir, destination + "." + copies);
        delete(previous);

        // now bump each copy up one suffix.
        for (int i = copies - 1; i > 0; i--) {
            File current = new File(parentDir, destination + "." + i);
            rename(current, previous);
            previous = current;
        }

        // Give the previous file a suffix.
        rename(destination, previous);
        // and write the new copy.
        rename(source, destination);
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
        byte[] buffer = new byte[FILE_COPY_BUFFER_SIZE];
        int nRead;
        while ((nRead = is.read(buffer)) > 0) {
            os.write(buffer, 0, nRead);
        }
        os.flush();
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

}
