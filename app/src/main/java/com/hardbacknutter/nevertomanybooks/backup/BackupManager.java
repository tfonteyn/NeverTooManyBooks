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
package com.hardbacknutter.nevertomanybooks.backup;

import android.content.Context;

import androidx.annotation.NonNull;

import com.hardbacknutter.nevertomanybooks.App;
import com.hardbacknutter.nevertomanybooks.backup.archivebase.BackupContainer;
import com.hardbacknutter.nevertomanybooks.backup.archivebase.BackupReader;
import com.hardbacknutter.nevertomanybooks.backup.archivebase.BackupWriter;
import com.hardbacknutter.nevertomanybooks.backup.tararchive.TarBackupContainer;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;

/**
 * Encapsulates the actual container used for backup/restore.
 */
public final class BackupManager {

    /** Last full backup date. */
    public static final String PREF_LAST_BACKUP_DATE = "Backup.LastDate";
    /** Last full backup file path. */
    public static final String PREF_LAST_BACKUP_FILE = "Backup.LastFile";
    /** see {@link #isArchive(File)}. */
    public static final String ARCHIVE_EXTENSION = ".bcbk";

    /** Constructor. */
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
    public static BackupReader getReader(@NonNull final Context context,
                                         @NonNull final File file)
            throws IOException {
        if (!file.exists()) {
            throw new FileNotFoundException("Attempt to open non-existent backup file");
        }

        // We only support one backup format; so we use that. In future we would need to
        // explore the file to determine which format to use
        BackupContainer bkp = new TarBackupContainer(file);
        // Each format should provide a validator of some kind
        if (!bkp.isValid(context)) {
            throw new IOException("not a valid archive");
        }

        return bkp.newReader(context);
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
    static BackupWriter getWriter(@NonNull final Context context,
                                  @NonNull final File file)
            throws IOException {

        // We only support one backup format; so we use that.
        BackupContainer bkp = new TarBackupContainer(file);

        return bkp.newWriter(context);
    }

    /**
     * Simple check on the file being an archive.
     *
     * @param file to check
     *
     * @return {@code true} if it's an archive
     */
    public static boolean isArchive(@NonNull final File file) {
        String name = file.getName().toLowerCase(App.getSystemLocale());
        // our own extension
        return name.endsWith(ARCHIVE_EXTENSION)
               // TarBackupContainer reads (duh) .tar files.
               || name.endsWith(".tar");
    }
}
