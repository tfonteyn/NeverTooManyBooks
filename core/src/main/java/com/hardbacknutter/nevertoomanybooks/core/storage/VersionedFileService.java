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

package com.hardbacknutter.nevertoomanybooks.core.storage;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.io.File;
import java.io.IOException;

import com.hardbacknutter.util.logger.LoggerFactory;

/**
 * Provides a rudimentary versioning for files.
 * Previous versions of a file can be kept in the same directory of the file,
 * or in a common directory given at creation time.
 */
public class VersionedFileService {

    private static final String TAG = "VersionedFileService";

    private final int copies;
    @Nullable
    private final File backupDir;

    /**
     * Constructor. The backup will be created in the same directory as the file.
     *
     * @param copies Number of copies to keep.
     */
    public VersionedFileService(final int copies) {
        this.backupDir = null;
        this.copies = copies;
    }

    /**
     * Constructor.
     *
     * @param backupDir Where to put the backup files.
     *                  Must be on the same volume as the file(s).
     * @param copies    Number of copies to keep.
     */
    public VersionedFileService(@NonNull final File backupDir,
                                final int copies) {
        this.backupDir = backupDir;
        this.copies = copies;
    }

    /**
     * Rename the given "file" to "file.1", keeping {@link #copies} of the old file,
     * i.e. the number of the copy is added as a SUFFIX to the name.
     * <p>
     * Upon success, the "file" is no longer available.
     * Any exception is ignored. The presence of "file" is not defined, and should be assume
     * to be no longer available.
     * <p>
     * <strong>Important:</strong> it's a 'rename', so single volume use only!
     *
     * @param file file to rename
     */
    public void save(@NonNull final File file) {

        final String backupFilePath = createBackupFilePath(file);

        // remove the oldest copy (if there is one)
        File previous = new File(backupFilePath + "." + copies);
        FileUtils.delete(previous);

        try {
            // now bump each copy up one suffix.
            for (int i = copies - 1; i > 0; i--) {
                final File current = new File(backupFilePath + "." + i);
                if (current.exists()) {
                    FileUtils.rename(current, previous);
                }
                previous = current;
            }

            // Rename the current file giving it a suffix.
            if (file.exists()) {
                FileUtils.rename(file, previous);
            }
        } catch (@NonNull final IOException e) {
            LoggerFactory.getLogger().e(TAG, e);
        }
    }

    /**
     * Check if the given filename has a backup file in storage.
     *
     * @param file to check
     *
     * @return {@code true} if there is a backup file stored matching the given name
     */
    public boolean hasBackup(@NonNull final File file) {
        final String backupFilePath = createBackupFilePath(file);
        return new File(backupFilePath + ".1").exists();
    }

    /**
     * Restore the previous version of the file.
     *
     * @param file to restore
     *
     * @return {@code true} if we restored successfully
     *
     * @throws IOException on generic/other IO failures
     */
    public boolean restore(@NonNull final File file)
            throws IOException {

        final String backupFilePath = createBackupFilePath(file);

        File previous = new File(backupFilePath + ".1");
        if (previous.exists()) {
            // Remove the current file, and re-instate the previous one
            FileUtils.delete(file);
            FileUtils.rename(previous, file);

            // now bump each copy down one suffix.
            for (int i = 2; i < copies; i++) {
                final File current = new File(backupFilePath + "." + i);
                if (current.exists()) {
                    FileUtils.rename(current, previous);
                }
                previous = current;
            }
        }
        return true;
    }

    @NonNull
    private String createBackupFilePath(@NonNull final File file) {
        final String backupFilePath;
        if (backupDir == null) {
            backupFilePath = file.getPath();
        } else {
            backupFilePath = new File(backupDir, file.getName()).getPath();
        }
        return backupFilePath;
    }
}
