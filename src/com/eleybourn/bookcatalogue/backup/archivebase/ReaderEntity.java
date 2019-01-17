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
package com.eleybourn.bookcatalogue.backup.archivebase;

import androidx.annotation.NonNull;

import com.eleybourn.bookcatalogue.utils.RTE.DeserializationException;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.Serializable;
import java.util.Date;

/**
 * Interface provided by every entity read from a backup file.
 *
 * @author pjw
 */
public interface ReaderEntity {

    /** Get the original "file name" (archive entry name) of the object. */
    @NonNull
    String getName();

    /** Get the type of this entity. */
    @NonNull
    BackupEntityType getType();

    /** Modified date from archive entry. */
    @NonNull
    Date getDateModified();


    /** get the stream to read the entity. */
    @NonNull
    InputStream getStream();

    /** Save the data to a directory, using the original file name. */
    void saveToDirectory(@NonNull final File dir)
            throws IOException;

    /** Read the data as a Serializable object. */
    @NonNull
    <T extends Serializable> T getSerializable()
            throws IOException, DeserializationException;

    /** Supported entity types. */
    enum BackupEntityType {
        Books,
        Info,
        Database,
        Preferences,
        BooklistStyles,
        Cover,
        XML,
        Unknown,

        PreferencesPreV200,
        BooklistStylesPreV200,
    }
}
