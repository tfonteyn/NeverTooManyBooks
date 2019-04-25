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

import android.content.Context;

import androidx.annotation.NonNull;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;

import com.eleybourn.bookcatalogue.backup.archivebase.BackupContainer;
import com.eleybourn.bookcatalogue.backup.archivebase.BackupReader;
import com.eleybourn.bookcatalogue.backup.tararchive.TarBackupContainer;

/**
 * Class for public static methods relating to backup/restore.
 *
 * @author pjw
 */
public final class BackupManager {

    /** Last full backup date. */
    public static final String PREF_LAST_BACKUP_DATE = "Backup.LastDate";
    /** Last full backup file path. */
    public static final String PREF_LAST_BACKUP_FILE = "Backup.LastFile";

    private BackupManager() {
    }

    /**
     * Create a BackupReader for the specified file.
     *
     * @param context caller context
     * @param file    to read from
     *
     * @return a new reader
     *
     * @throws IOException on failure
     */
    @NonNull
    public static BackupReader readFrom(@NonNull final Context context,
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
}
