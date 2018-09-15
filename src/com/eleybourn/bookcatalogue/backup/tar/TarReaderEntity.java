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
package com.eleybourn.bookcatalogue.backup.tar;

import android.support.annotation.NonNull;

import com.eleybourn.bookcatalogue.backup.ReaderEntityAbstract;

import org.apache.commons.compress.archivers.tar.TarArchiveEntry;

import java.io.InputStream;
import java.util.Date;

/**
 * Implementation of TAR-specific ReaderEntity functions. Not much to do.
 *
 * @author pjw
 */
public class TarReaderEntity extends ReaderEntityAbstract {
    private final TarBackupReader mReader;
    private final TarArchiveEntry mEntry;
    private final BackupEntityType mType;

    /**
     * Constructor
     *
     * @param reader Parent
     * @param entry  Corresponding archive entry
     * @param type   Type of item
     */
    TarReaderEntity(@NonNull final TarBackupReader reader, @NonNull final TarArchiveEntry entry,
                    @NonNull final BackupEntityType type) {
        mReader = reader;
        mEntry = entry;
        mType = type;
    }

    @Override
    public BackupEntityType getType() {
        return mType;
    }

    @Override
    public InputStream getStream() {
        return mReader.getInput();
    }

    @Override
    public String getName() {
        return mEntry.getName();
    }

    @Override
    public Date getDateModified() {
        return mEntry.getLastModifiedDate();
    }
}
