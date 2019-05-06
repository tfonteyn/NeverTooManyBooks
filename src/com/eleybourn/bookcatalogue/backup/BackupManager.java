/*
 * @copyright 2013 Philip Warner
 * @license GNU General Public License
 *
 * This file is part of Book Catalogue.
 *
 * Book Catalogue is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Book Catalogue is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with Book Catalogue.  If not, see <http://www.gnu.org/licenses/>.
 */
package com.eleybourn.bookcatalogue.backup;

import androidx.annotation.NonNull;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;

import com.eleybourn.bookcatalogue.backup.archivebase.BackupContainer;
import com.eleybourn.bookcatalogue.backup.archivebase.BackupReader;
import com.eleybourn.bookcatalogue.backup.archivebase.BackupWriter;
import com.eleybourn.bookcatalogue.backup.tararchive.TarBackupContainer;
import com.eleybourn.bookcatalogue.utils.LocaleUtils;

/**
 * Encapsulates the actual container used for backup/restore.
 */
public final class BackupManager {

    /** Last full backup date. */
    public static final String PREF_LAST_BACKUP_DATE = "Backup.LastDate";
    /** Last full backup file path. */
    public static final String PREF_LAST_BACKUP_FILE = "Backup.LastFile";
    /** @see #isArchive(File) */
    public static final String ARCHIVE_EXTENSION = ".bcbk";

    private BackupManager() {
    }

    /**
     * Create a BackupReader for the specified file.
     *
     * @param file to read from
     *
     * @return a new reader
     *
     * @throws IOException on failure
     */
    @NonNull
    public static BackupReader getReader(@NonNull final File file)
            throws IOException {
        if (!file.exists()) {
            throw new FileNotFoundException("Attempt to open non-existent backup file");
        }

        // We only support one backup format; so we use that. In future we would need to
        // explore the file to determine which format to use
        BackupContainer bkp = new TarBackupContainer(file);
        // Each format should provide a validator of some kind
        if (!bkp.isValid()) {
            throw new IOException("not a valid archive");
        }

        return bkp.newReader();
    }

    /**
     * Create a BackupWriter for the specified file.
     *
     * @param file to read from
     *
     * @return a new writer
     *
     * @throws IOException on failure
     */
    @NonNull
    public static BackupWriter getWriter(@NonNull final File file)
            throws IOException {

        // We only support one backup format; so we use that.
        BackupContainer bkp = new TarBackupContainer(file);

        return bkp.newWriter();
    }

    /**
     * Simple check on the file being an archive.
     *
     * @param file to check
     *
     * @return {@code true} if it's an archive
     */
    public static boolean isArchive(@NonNull final File file) {
        return file.getName().toLowerCase(LocaleUtils.getSystemLocale())
                   .endsWith(ARCHIVE_EXTENSION);
    }
}
