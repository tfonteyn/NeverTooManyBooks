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

// This is an interface which does nothing except embed the type enum...
// It's only reason for existence is to be a mirror of the ArchiveReaderRecord interface
// TODO: merge the writer and reader record definitions
public interface ArchiveWriterRecord {

    /**
     * Supported archive entry types.
     * An entry will either be handled directly inside the {@link ArchiveWriter} class,
     * or preferably handed over to an {@link RecordWriter} for second-level archives.
     * <p>
     * <strong>Not all {@link ArchiveType} classes will support all types.</strong>
     */
    enum Type {
        /** Archive information. ONLY ONE PER ARCHIVE. */
        InfoHeader("info"),
        /** Book list. ONLY ONE PER ARCHIVE. */
        Books("books"),
        /** Preferences file. ONLY ONE PER ARCHIVE. */
        Preferences("preferences"),
        /** BooklistStyles file. ONLY ONE PER ARCHIVE. */
        Styles("styles"),
        /** Covers, multiple per archive. */
        Cover("image");

        /** Used as the fixed archive entry name when writing. */
        @NonNull
        private final String mName;

        Type(@NonNull final String name) {
            mName = name;
        }

        /**
         * Get the name <strong>prefix</strong> matching the type.
         *
         * @return name
         */
        @NonNull
        public String getName() {
            return mName;
        }
    }
}
