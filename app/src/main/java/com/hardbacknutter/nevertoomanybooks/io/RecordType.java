/*
 * @Copyright 2018-2023 HardBackNutter
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

import java.util.EnumSet;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;

import com.hardbacknutter.nevertoomanybooks.BuildConfig;
import com.hardbacknutter.nevertoomanybooks.booklist.style.Style;
import com.hardbacknutter.nevertoomanybooks.core.LoggerFactory;
import com.hardbacknutter.nevertoomanybooks.entities.Book;
import com.hardbacknutter.nevertoomanybooks.entities.Bookshelf;
import com.hardbacknutter.nevertoomanybooks.sync.calibre.CalibreCustomField;
import com.hardbacknutter.nevertoomanybooks.sync.calibre.CalibreLibrary;

/**
 * Supported archive entry types.
 * An entry will either be handled directly inside the {@link DataWriter} class,
 * or preferably handed over to an {@link RecordWriter} for second-level archives.
 * <p>
 * <strong>Not all reader/writer classes will support all types.</strong>
 * <p>
 * TODO: implement magic-byte checks just like we do on top-level archive files.
 * Checking the prefix of the file name is a bit shaky
 * It's fine for our zip/tar archives were we control the name
 * but NOT suitable for checking standalone files.
 * However, it's not a real issue for now:
 * Csv: only supports books anyhow.
 * Json: can detect its own format.
 * Xml: can detect its own format; importing standalone files is not supported anyhow.
 */
public enum RecordType {

    /**
     * Archive meta data.
     * Contains a list of key=value pairs.
     * ONLY ONE PER ARCHIVE.
     * <p>
     * Adding this type to the {@link DataWriterHelperBase} has no effect.
     * The encoding decides whether to write meta-data.
     * <p>
     * Adding this type to the {@link DataReaderHelperBase} has no effect.
     * The reader will always try and detect meta-data.
     */
    MetaData("info"),

    /**
     * {@link Style} element(s).
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
     * All information needed to encode/decode {@link Bookshelf} elements.
     * ONLY ONE PER ARCHIVE.
     */
    Bookshelves("bookshelves"),

    /**
     * All information needed to encode/decode {@link CalibreLibrary} elements.
     * ONLY ONE PER ARCHIVE.
     */
    CalibreLibraries("calibrelibraries"),

    /**
     * All information needed to encode/decode
     * {@link CalibreCustomField} elements.
     * ONLY ONE PER ARCHIVE.
     */
    CalibreCustomFields("calibrecustomfields"),

    /**
     * All information needed to encode/decode
     * {@code Pair<String, String> with UUID and Date-added as iso utc timestamp} elements.
     * ONLY ONE PER ARCHIVE.
     */
    DeletedBooks("deletedbooks"),

    /**
     * All information needed to encode/decode {@link Book} elements.
     * All auxiliary data (author, bookshelf, ...) is included as needed.
     * ONLY ONE PER ARCHIVE.
     * <p>
     * If Books are written then {@link #Bookshelves} <strong>MUST</strong> precede it.
     */
    Books("books"),

    /**
     * A <strong>Single</strong> cover.
     * MULTIPLE per archive.
     */
    Cover(""),

    /**
     * The full database file.
     * ONLY ONE PER ARCHIVE.
     */
    Database("database"),

    /**
     * Container element, which in turn can contain other records.
     */
    AutoDetect("data");

    /** Log tag. */
    private static final String TAG = "RecordType";

    /** Used as the fixed archive entry name when <strong>WRITING</strong>. */
    @NonNull
    private final String name;

    /** Used to detect the archive entry name when <strong>READING</strong>. */
    @SuppressWarnings("FieldNotUsedInToString")
    @NonNull
    private final String prefix;

    RecordType(@NonNull final String name) {
        // do toLowerCase to avoid the developer doing a boo-boo
        this.name = name.toLowerCase(Locale.ENGLISH);
        // for now the same, but keeping this open to change
        prefix = this.name;
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
        final String name = entryName.toLowerCase(Locale.ENGLISH);

        for (final RecordType type : values()) {
            if (name.startsWith(type.prefix)) {
                return Optional.of(type);
            }
        }

        if (BuildConfig.DEBUG /* always */) {
            LoggerFactory.getLogger().d(TAG, "getType", "Unknown entry=" + entryName);
        }
        return Optional.empty();
    }

    /**
     * Resolve the given record types by adding any related ones.
     * The incoming set is not modified.
     * <p>
     * Dev. note: this is a quick hack.... we'll probably regret doing this in the future.
     *
     * @param recordTypes the base set
     *
     * @return a new unmodifiable Set
     */
    @NonNull
    public static Set<RecordType> addRelatedTypes(@NonNull final Set<RecordType> recordTypes) {
        final Set<RecordType> all = EnumSet.copyOf(recordTypes);
        // If we're doing preferences, then implicitly handle calibre custom fields as well
        if (recordTypes.contains(Preferences)) {
            all.add(CalibreCustomFields);
        }

        // If we're doing books, then we must do its dependencies:
        if (recordTypes.contains(Books)) {
            all.add(Bookshelves);
            all.add(CalibreLibraries);
            all.add(DeletedBooks);
        }

        return all;
    }

    /**
     * Get the name for the type.
     *
     * @return name
     */
    @NonNull
    public String getName() {
        return name;
    }

    @Override
    @NonNull
    public String toString() {
        return "RecordType{"
               + "name=`" + name + '`'
               + '}';
    }
}
