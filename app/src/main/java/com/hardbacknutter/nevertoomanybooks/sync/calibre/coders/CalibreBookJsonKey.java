/*
 * @Copyright 2018-2026 HardBackNutter
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
package com.hardbacknutter.nevertoomanybooks.sync.calibre.coders;

/**
 * These are the field names the Calibre Content Server uses/expects in its AJAX API.
 * See {@link BookCoder} for an example of the JSON blob.
 * <p>
 * There are extra NTMB keys defined.
 */
@SuppressWarnings("WeakerAccess")
public final class CalibreBookJsonKey {

    /** PUBLICATION_METADATA_FIELDS: Ordered list. */
    public static final String AUTHOR_ARRAY = "authors";
    /** SOCIAL_METADATA_FIELDS: html string. */
    public static final String COMMENTS = "comments";
    /** PUBLICATION_METADATA_FIELDS: The URL when reading; Base64 encoded image when writing. */
    public static final String COVER = "cover";
    /**
     * Not explicitly defined in Calibre, only seen in "ajax.py".
     * Contains the main/first? format of the ebook file.
     */
    public static final String EBOOK_FORMAT = "main_format";
    /** CALIBRE_METADATA_FIELDS: The Calibre book id. */
    public static final String ID = "application_id";
    /** SOCIAL_METADATA_FIELDS: Not an array, but an object with key:value pairs. */
    public static final String IDENTIFIERS = "identifiers";
    /** PUBLICATION_METADATA_FIELDS: Ordered list. */
    public static final String LANGUAGES_ARRAY = "languages";
    /** PUBLICATION_METADATA_FIELDS: iso date string; timezone aware. */
    public static final String LAST_MODIFIED = "last_modified";
    /**
     * CALIBRE_METADATA_FIELDS: {@code int} Calculated page count.
     * null values are None or 0.
     * -1 is no countable # formats.
     * -2 is error processing formats,
     * -3 is DRMed.
     */
    public static final String PAGES = "pages";
    /** PUBLICATION_METADATA_FIELDS: iso date string. */
    public static final String PUBLICATION_DATE = "pubdate";
    /** PUBLICATION_METADATA_FIELDS: single name. */
    public static final String PUBLISHER = "publisher";
    /**
     * SOCIAL_METADATA_FIELDS: {@code float} 0..10.
     * <p>
     * Right...  the reality of this field is:
     * <p>
     * When READING the field using the ajax api, the value
     * will be a {@code float} in the range of {@code 0.0 to 5.0} in steps of {@code 0.5}.
     * <p>
     * When WRITING the field using {@code /cdb/set-fields (Endpoints#PUSH_CHANGES)}
     * we must send an int 0..10.
     *
     * The custom field "#rating" on the other hand is
     */
    public static final String RATING = "rating";
    /** SOCIAL_METADATA_FIELDS: single series name. */
    public static final String SERIES = "series";
    /** SOCIAL_METADATA_FIELDS: {@code float}; The number of a book in the series. . */
    public static final String SERIES_INDEX = "series_index";
    /** SOCIAL_METADATA_FIELDS: Ordered list. */
    public static final String TAGS_ARRAY = "tags";
    /** PUBLICATION_METADATA_FIELDS: title, always present. */
    public static final String TITLE = "title";
    /** USER_METADATA_FIELDS: field_metadata + actual #value#. */
    public static final String USER_METADATA = "user_metadata";
    /** PUBLICATION_METADATA_FIELDS: type-4 uuid. */
    public static final String UUID = "uuid";

    /** The list of virtual libraries (if our plugin is installed). */
    public static final String NTMB_VIRTUAL_LIBRARY_LIST = "ntmb:vlibs";

    private CalibreBookJsonKey() {
    }
}
