/*
 * @Copyright 2018-2022 HardBackNutter
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
package com.hardbacknutter.nevertoomanybooks.io;

import androidx.annotation.NonNull;

import java.io.IOException;
import java.io.InputStream;
import java.util.Optional;

/**
 * Interface provided by every record read from an archive file.
 * This class effectively should wrap an archive record in a encoding-agnostic record.
 * <p>
 * Note we're also forcing the encapsulation of the {@link DataReader} input stream.
 */
public interface ArchiveReaderRecord {

    /**
     * Get the stream to read the record from.
     * Callers <strong>MUST NOT</strong> close this stream.
     * Implementations should close it when appropriate.
     *
     * @return the InputStream
     *
     * @throws IOException on generic/other IO failures
     */
    @NonNull
    InputStream getInputStream()
            throws IOException;

    /**
     * Get the type of this record.
     *
     * @return type
     */
    @NonNull
    Optional<RecordType> getType();

    /**
     * Get the encoding of this record.
     *
     * @return Encoding
     */
    @NonNull
    Optional<RecordEncoding> getEncoding();

    /**
     * Get the original "file name" (archive record name) of the object.
     * <p>
     * Primarily/only used for cover files.
     *
     * @return name
     */
    @NonNull
    String getName();

    /**
     * Get the last modification time of this archive record in EpochMilli.
     * <p>
     * Primarily/only used for cover files.
     *
     * @return EpochMilli
     */
    long getLastModifiedEpochMilli();
}
