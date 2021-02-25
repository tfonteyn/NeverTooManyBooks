/*
 * @Copyright 2018-2021 HardBackNutter
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
package com.hardbacknutter.nevertoomanybooks.backup;

import androidx.annotation.NonNull;

import java.util.Optional;

import com.hardbacknutter.nevertoomanybooks.backup.base.ArchiveEncoding;
import com.hardbacknutter.nevertoomanybooks.backup.base.ArchiveWriter;
import com.hardbacknutter.nevertoomanybooks.booklist.style.ListStyle;
import com.hardbacknutter.nevertoomanybooks.debug.Logger;
import com.hardbacknutter.nevertoomanybooks.entities.Book;
import com.hardbacknutter.nevertoomanybooks.utils.AppLocale;

/**
 * Supported archive entry types.
 * An entry will either be handled directly inside the {@link ArchiveWriter} class,
 * or preferably handed over to an {@link RecordWriter} for second-level archives.
 * <p>
 * <strong>Not all {@link ArchiveEncoding} classes will support all types.</strong>
 * <p>
 * <p>
 * TODO: implement magic-byte checks just like we do on top-level archive files.
 * Checking the prefix of the file name is a bit shaky
 * It's fine for our zip/tar archives were we control the name
 * but NOT suitable for checking standalone files.
 * However, it's not a real issue for now:
 * Csv: can only reads books anyhow
 * Xml: can detect its own format; but importing standalone files is not supported
 * Json: can detect its own format; but importing standalone files is not supported
 */
public enum RecordType {

    /**
     * Archive meta data.
     * Contains a list of key=value pairs.
     * ONLY ONE PER ARCHIVE.
     */
    MetaData("info"),

    /**
     * A <strong>list</strong> of {@link ListStyle} elements.
     * ONLY ONE PER ARCHIVE.
     */
    Styles("styles"),

    /**
     * User and app setting.
     * Contains a list of key=value pairs.
     * ONLY ONE PER ARCHIVE.
     */
    Preferences("preferences"),

    /**
     * Collection of named Certificates.
     * ONLY ONE PER ARCHIVE.
     */
    Certificates("certificates"),

    /**
     * A <strong>list</strong> of {@link Book} elements.
     * All auxiliary data (author, bookshelf, ...) is included in each element.
     * ONLY ONE PER ARCHIVE.
     */
    Books("books"),

    /**
     * A <strong>Single</strong> cover.
     * MULTIPLE per archive.
     */
    Cover(""),

    /**
     * Container element, which in turn can contain other records.
     */
    AutoDetect("data");

    /** Log tag. */
    private static final String TAG = "RecordType";

    /** Used as the fixed archive entry name when <strong>WRITING</strong>. */
    @NonNull
    private final String mName;

    /** Used to detect the archive entry name when <strong>READING</strong>. */
    @NonNull
    private final String mPrefix;

    RecordType(@NonNull final String name) {
        // for now the same, but keeping this open to change
        mName = name;
        mPrefix = name;
    }

    /**
     * Detect the type of the passed name.
     *
     * @param entryName to get the type of (case insensitive)
     *
     * @return the record type
     */
    @NonNull
    public static Optional<RecordType> getType(@NonNull final String entryName) {
        final String name = entryName.toLowerCase(AppLocale.getInstance().getSystemLocale());

        for (final RecordType type : values()) {
            if (name.startsWith(type.mPrefix)) {
                return Optional.of(type);
            }
        }

        Logger.warn(TAG, "getType|Unknown entry=" + entryName);
        return Optional.empty();
    }

    /**
     * Get the name for the type.
     *
     * @return name
     */
    @NonNull
    public String getName() {
        return mName;
    }
}
