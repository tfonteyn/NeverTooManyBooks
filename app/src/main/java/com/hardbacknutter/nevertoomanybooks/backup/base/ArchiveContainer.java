/*
 * @Copyright 2020 HardBackNutter
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
package com.hardbacknutter.nevertoomanybooks.backup.base;

import androidx.annotation.NonNull;

/**
 * Archive formats (partially) supported.
 * <p>
 * This is the top level, i.e. the actual file we read/write.
 * Handled by {@link ArchiveReader} and {@link ArchiveWriter}.
 */
public enum ArchiveContainer {
    /** The default full backup/restore support. Text files are compressed, images are not. */
    Zip(".zip"),
    /** Books as a CSV file; full support for export/import. */
    Csv(".csv"),
    /**
     * Books as a JSON file; full support for export/import.
     * <p>
     * ENHANCE: JSON export/import is experimental, not exposed to the user yet.
     * Added as top-level for easy testing only.
     * Real usage will likely be limited as part of a zip archive.
     */
    Json(".json"),
    /** XML <strong>Export only</strong>. */
    Xml(".xml"),
    /** Database. */
    SqLiteDb(".db"),
    /** The legacy full backup/restore support. NOT compressed. */
    Tar(".tar"),
    /** The archive we tried to read from was not identified. */
    Unknown("");

    @NonNull
    private final String mExtension;

    ArchiveContainer(@NonNull final String extension) {
        mExtension = extension;
    }

    /**
     * Get the <strong>proposed</strong> archive file extension for writing an output file.
     *
     * @return file name extension starting with a '.'
     */
    @NonNull
    public String getFileExt() {
        return mExtension;
    }
}
