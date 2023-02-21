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

package com.hardbacknutter.nevertoomanybooks.tasks;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.io.File;
import java.io.FileFilter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.attribute.BasicFileAttributes;

import com.hardbacknutter.nevertoomanybooks.core.storage.FileUtils;

/**
 * File utilities with support for a {@link ProgressListener}; for use from tasks.
 */
public final class TaskFileUtils {
    private TaskFileUtils() {
    }

    /**
     * Recursively copy the source Directory to the destination Directory.
     *
     * @param sourceDir   directory
     * @param destDir     directory
     * @param progressListener (optional) to check for user cancellation
     *
     * @throws IOException on generic/other IO failures
     */
    static void copyDirectory(@NonNull final File sourceDir,
                              @NonNull final File destDir,
                              @Nullable final ProgressListener progressListener)
            throws IOException {
        // sanity check
        if (sourceDir.isDirectory() && destDir.isDirectory()) {
            //noinspection ConstantConditions
            for (final File file : sourceDir.listFiles()) {
                if (progressListener != null && progressListener.isCancelled()) {
                    return;
                }
                final BasicFileAttributes fileAttr =
                        Files.readAttributes(file.toPath(), BasicFileAttributes.class);
                if (fileAttr.isRegularFile()) {
                    FileUtils.copy(file, new File(destDir, file.getName()));

                } else if (fileAttr.isDirectory()) {
                    final File destSubDir = new File(destDir, file.getName());
                    //noinspection ResultOfMethodCallIgnored
                    destSubDir.mkdir();
                    copyDirectory(file, destSubDir, progressListener);
                }
            }
        }
    }

    /**
     * Recursively delete files.
     * Does <strong>NOT</strong> delete the actual directory or any actual subdirectories.
     *
     * @param root        directory
     * @param filter      (optional) to apply; {@code null} for all files.
     * @param progressListener (optional) to check for user cancellation
     *
     * @return number of bytes deleted
     *
     * @see FileUtils#deleteDirectory(File, FileFilter)
     */
    public static long deleteDirectory(@NonNull final File root,
                                       @Nullable final FileFilter filter,
                                       @Nullable final ProgressListener progressListener) {
        long totalSize = 0;
        // sanity check
        if (root.isDirectory()) {
            //noinspection ConstantConditions
            for (final File file : root.listFiles(filter)) {
                if (progressListener != null && progressListener.isCancelled()) {
                    return totalSize;
                }
                if (file.isFile()) {
                    totalSize += file.length();
                    FileUtils.delete(file);
                } else if (file.isDirectory()) {
                    totalSize += deleteDirectory(file, filter, progressListener);
                }
            }
        }
        return totalSize;
    }
}
