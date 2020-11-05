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
 */
public enum ArchiveContainer {
    /** The default full backup/restore support. Text files are compressed, images are not. */
    Zip,

    /** Books as a CSV file; full support for export/import. */
    CsvBooks,

    /** XML <strong>Export only</strong>. */
    Xml,
    /** Database. */
    SqLiteDb,

    /** The legacy full backup/restore support. NOT compressed. */
    Tar,

    /** The archive we tried to read from was not identified. */
    Unknown;

    /**
     * Get the <strong>proposed</strong> archive file extension.
     *
     * @return file name extension starting with a '.'
     */
    @NonNull
    public String getFileExt() {
        switch (this) {
            case Zip:
                return ".zip";
            case Xml:
                return ".xml";
            case CsvBooks:
                return ".csv";
            case SqLiteDb:
                return ".db";
            case Tar:
                return ".tar";

            case Unknown:
            default:
                throw new IllegalArgumentException(name());
        }
    }
}
