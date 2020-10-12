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
package com.hardbacknutter.nevertoomanybooks.backup;

import androidx.annotation.NonNull;

import java.util.regex.Pattern;

import com.hardbacknutter.nevertoomanybooks.App;
import com.hardbacknutter.nevertoomanybooks.debug.Logger;
import com.hardbacknutter.nevertoomanybooks.utils.AppLocale;

/**
 * Supported archive entry types.
 * <strong>Not all archive reader/writers will support all types.</strong>
 * <p>
 * Also: if a type needs to change its format, a new type must be created
 * (e.g. StylesXmlV2 etc...) and added to {@link ArchiveReaderAbstract#read}.
 */
public enum ArchiveContainerEntry {
    /** Archive information. */
    InfoHeaderXml,

    /** CSV book list. */
    BooksCsv,
    /** XML book list. */
    BooksXml,
    /** XML Preferences file. */
    PreferencesXml,
    /** XML BooklistStyles file. */
    BooklistStylesXml,

    /** Generic xml file, the root tag should identify the content. */
    XML,

    /** Binary cover file. */
    Cover,
    /** Binary *.db file. */
    Database,

    /** Legacy XML Preferences file. */
    LegacyPreferences,
    /** Legacy binary style file. */
    LegacyBooklistStyles,

    /** Used during reads. */
    Unknown;

    /** Log tag. */
    private static final String TAG = "ArchiveEntityType";

    /** {@link #InfoHeaderXml}. */
    private static final String INFO_XML = "INFO.xml";
    /** {@link #PreferencesXml}. */
    private static final String PREFERENCES_XML = "preferences.xml";
    /** {@link #BooklistStylesXml}. */
    private static final String STYLES_XML = "styles.xml";

    /** {@link #BooksCsv}. */
    private static final String BOOKS_CSV = "books.csv";
    /** {@link #BooksCsv}. */
    private static final Pattern BOOKS_CSV_PATTERN =
            Pattern.compile("^books.*\\.csv$",
                            Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);

    /** {@link #BooksXml}. */
    private static final String BOOKS_XML = "books.xml";

    /** Legacy BookCatalogue. */
    private static final Pattern DB_FILE_PATTERN =
            Pattern.compile(".*\\.db$",
                            Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
    /** Legacy BookCatalogue. */
    private static final String LEGACY_PREFERENCES = "preferences";
    /** Legacy BookCatalogue. */
    private static final Pattern LEGACY_STYLES_PATTERN =
            Pattern.compile("^style.blob.[0-9]*$",
                            Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);

    /**
     * Detect the type of the passed name.
     *
     * @param entityName to get the type of (case insensitive)
     *
     * @return the entity type
     */
    @NonNull
    public static ArchiveContainerEntry getType(@NonNull final String entityName) {
        String name = entityName.toLowerCase(AppLocale.getInstance().getSystemLocale());

        if (name.endsWith(".jpg") || name.endsWith(".png")) {
            return Cover;

        } else if (INFO_XML.equalsIgnoreCase(name)) {
            return InfoHeaderXml;

        } else if (BOOKS_CSV.equalsIgnoreCase(name)
                   || BOOKS_CSV_PATTERN.matcher(name).find()) {
            return BooksCsv;

        } else if (PREFERENCES_XML.equalsIgnoreCase(name)) {
            return PreferencesXml;

        } else if (STYLES_XML.equalsIgnoreCase(name)) {
            return BooklistStylesXml;

        } else if (DB_FILE_PATTERN.matcher(name).find()) {
            return Database;

        } else if (name.endsWith(".xml")) {
            return XML;

        } else if (LEGACY_PREFERENCES.equals(name)) {
            return LegacyPreferences;

        } else if (LEGACY_STYLES_PATTERN.matcher(name).find()) {
            return LegacyBooklistStyles;

        } else {
            Logger.warn(App.getAppContext(), TAG,
                        "getEntryType|Unknown file=" + entityName);
            return Unknown;
        }
    }

    /**
     * Get the name matching the type.
     *
     * @return name
     */
    @NonNull
    public String getName() {
        switch (this) {
            case InfoHeaderXml:
                return INFO_XML;
            case BooksCsv:
                return BOOKS_CSV;
            case BooksXml:
                return BOOKS_XML;
            case PreferencesXml:
                return PREFERENCES_XML;
            case BooklistStylesXml:
                return STYLES_XML;

            case XML:
            case Cover:
            case Database:
            case LegacyPreferences:
            case LegacyBooklistStyles:
            case Unknown:
            default:
                throw new IllegalArgumentException(name());
        }
    }
}
