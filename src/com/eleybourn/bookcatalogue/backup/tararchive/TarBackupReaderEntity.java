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
package com.eleybourn.bookcatalogue.backup.tararchive;

import android.support.annotation.NonNull;

import com.eleybourn.bookcatalogue.BuildConfig;
import com.eleybourn.bookcatalogue.DEBUG_SWITCHES;
import com.eleybourn.bookcatalogue.backup.archivebase.ReaderEntityAbstract;
import com.eleybourn.bookcatalogue.debug.Logger;

import org.apache.commons.compress.archivers.tar.TarArchiveEntry;

import java.io.InputStream;
import java.util.Date;

/**
 * Implementation of TAR-specific ReaderEntity functions. Not much to do.
 *
 * @author pjw
 */
public class TarBackupReaderEntity extends ReaderEntityAbstract {
    @NonNull
    private final TarBackupReader mReader;
    @NonNull
    private final TarArchiveEntry mEntry;
    @NonNull
    private final BackupEntityType mType;

    /**
     * Constructor
     *
     * @param reader Parent
     * @param entry  Corresponding archive entry
     * @param type   Type of item
     */
    TarBackupReaderEntity(final @NonNull TarBackupReader reader,
                          final @NonNull TarArchiveEntry entry,
                          final @NonNull BackupEntityType type) {
        mReader = reader;
        mEntry = entry;
        mType = type;
        if (DEBUG_SWITCHES.BACKUP && BuildConfig.DEBUG) {
            Logger.info(this, " constructor: type=" + type + ", name=" + entry.getName());
        }
    }

    @NonNull
    @Override
    public BackupEntityType getType() {
        return mType;
    }

    @NonNull
    @Override
    public InputStream getStream() {
        return mReader.getInput();
    }

    @NonNull
    @Override
    public String getName() {
        return mEntry.getName();
    }

    @NonNull
    @Override
    public Date getDateModified() {
        return mEntry.getLastModifiedDate();
    }
}
