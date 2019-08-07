/*
 * @Copyright 2019 HardBackNutter
 * @License GNU General Public License
 *
 * This file is part of NeverToManyBooks.
 *
 * In August 2018, this project was forked from:
 * Book Catalogue 5.2.2 @copyright 2010 Philip Warner & Evan Leybourn
 *
 * Without their original creation, this project would not exist in its current form.
 * It was however largely rewritten/refactored and any comments on this fork
 * should be directed at HardBackNutter and not at the original creator.
 *
 * NeverToManyBooks is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * NeverToManyBooks is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 * See the GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with NeverToManyBooks. If not, see <http://www.gnu.org/licenses/>.
 */
package com.hardbacknutter.nevertomanybooks.backup.archivebase;

import android.content.Context;

import androidx.annotation.NonNull;

import java.io.IOException;

/**
 * Interface provided by a backup archiver.
 */
public interface BackupContainer {

    /**
     * Get a BackupReader for the referenced archive.
     *
     * @param context Current context
     *
     * @return a new reader
     *
     * @throws IOException on failure
     */
    @NonNull
    BackupReader newReader(@NonNull Context context)
            throws IOException;

    /**
     * Get a BackupWriter for the referenced archive.
     *
     * @param context Current context
     *
     * @return a new writer
     *
     * @throws IOException on failure
     */
    @NonNull
    BackupWriter newWriter(@NonNull Context context)
            throws IOException;

    /**
     * Get the version of the underlying archiver used to write / required to read archives.
     *
     * @return the version
     */
    @SuppressWarnings("SameReturnValue")
    int getVersion();

    /**
     * Get the <strong>minimum</strong> version of the archive that the reader can still handle.
     *
     * @return the minimum version
     */
    @SuppressWarnings("SameReturnValue")
    int canReadVersion();

    /**
     * Checks if the current archive looks valid. Does not need to be
     * exhaustive. Do not call if an archive is being written.
     *
     * @param context Current context
     *
     * @return {@code true} if valid
     */
    boolean isValid(@NonNull Context context);
}
