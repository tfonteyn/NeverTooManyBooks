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
package com.hardbacknutter.nevertoomanybooks.backup.base;

import androidx.annotation.NonNull;

import java.io.IOException;
import java.io.InputStream;

import com.hardbacknutter.nevertoomanybooks.backup.ArchiveContainerEntry;

/**
 * Interface provided by every entity read from an archive file.
 * This class effectively should wrap an archive format specific entry in a format agnostic entry.
 * <p>
 * Note we're also forcing the encapsulation of the {@link ArchiveReader} input stream.
 */
public interface ReaderEntity {

    /**
     * Get the original "file name" (archive entry name) of the object.
     *
     * @return name
     */
    @NonNull
    String getName();

    /**
     * Get the type of this entity.
     *
     * @return Type
     */
    @NonNull
    ArchiveContainerEntry getType();

    /**
     * Get the last modification time of this archive entry in EpochMilli.
     *
     * @return EpochMilli
     */
    long getLastModifiedEpochMilli();

    /**
     * Get the stream to read the entity.
     * Callers <strong>MUST NOT</strong> close this stream.
     * Implementations should close it when appropriate.
     *
     * @return the InputStream
     *
     * @throws IOException on failure
     */
    @NonNull
    InputStream getInputStream()
            throws IOException;
}
