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
package com.hardbacknutter.nevertoomanybooks.booklist.style;

import androidx.annotation.NonNull;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import com.hardbacknutter.nevertoomanybooks.database.DBKey;

/**
 * Encapsulate the Book fields which can be shown on the Book-list screen
 * as defined <strong>by the current style</strong>.
 * <p>
 * Preference keys must be kept in sync with "res/xml/preferences_style_book_details.xml"
 */
public class BooklistFieldVisibility
        extends FieldVisibility {

    /** The fields which will be visible by default. */
    public static final long DEFAULT = getBitValue(Set.of(
            DBKey.COVER[0],
            DBKey.FK_SERIES));

    public static final String PK_LIST_SHOW_COVERS = "style.booklist.show.thumbnails";
    public static final String PK_LIST_SHOW_AUTHOR = "style.booklist.show.author";
    public static final String PK_LIST_SHOW_PUBLISHER = "style.booklist.show.publisher";
    public static final String PK_LIST_SHOW_PUB_DATE = "style.booklist.show.publication.date";
    public static final String PK_LIST_SHOW_FORMAT = "style.booklist.show.format";
    public static final String PK_LIST_SHOW_LANGUAGE = "style.booklist.show.language";
    public static final String PK_LIST_SHOW_LOCATION = "style.booklist.show.location";
    public static final String PK_LIST_SHOW_RATING = "style.booklist.show.rating";
    public static final String PK_LIST_SHOW_BOOKSHELVES = "style.booklist.show.bookshelves";
    public static final String PK_LIST_SHOW_ISBN = "style.booklist.show.isbn";

    private static final Set<String> DB_KEYS = Set.of(
            DBKey.COVER[0],
            DBKey.COVER[1],
            DBKey.FK_AUTHOR,
            DBKey.FK_SERIES,
            DBKey.FK_PUBLISHER,
            DBKey.FK_BOOKSHELF,

            DBKey.TITLE_ORIGINAL_LANG,
            DBKey.BOOK_CONDITION,
            DBKey.BOOK_ISBN,
            DBKey.BOOK_PUBLICATION__DATE,
            DBKey.FORMAT,
            DBKey.LANGUAGE,
            DBKey.LOCATION,
            DBKey.RATING);

    /** Map preference key to DBKey. */
    private static final Map<String, String> PREF_TO_DB_KEY = new HashMap<>();

    static {
        PREF_TO_DB_KEY.put(PK_LIST_SHOW_COVERS, DBKey.COVER[0]);

        PREF_TO_DB_KEY.put(PK_LIST_SHOW_AUTHOR, DBKey.FK_AUTHOR);
        PREF_TO_DB_KEY.put("style.booklist.show.series", DBKey.FK_SERIES);
        PREF_TO_DB_KEY.put(PK_LIST_SHOW_PUBLISHER, DBKey.FK_PUBLISHER);
        PREF_TO_DB_KEY.put(PK_LIST_SHOW_BOOKSHELVES, DBKey.FK_BOOKSHELF);

        PREF_TO_DB_KEY.put("style.booklist.show.original.title", DBKey.TITLE_ORIGINAL_LANG);
        PREF_TO_DB_KEY.put("style.booklist.show.condition", DBKey.BOOK_CONDITION);
        PREF_TO_DB_KEY.put(PK_LIST_SHOW_ISBN, DBKey.BOOK_ISBN);
        PREF_TO_DB_KEY.put(PK_LIST_SHOW_PUB_DATE, DBKey.BOOK_PUBLICATION__DATE);
        PREF_TO_DB_KEY.put(PK_LIST_SHOW_FORMAT, DBKey.FORMAT);
        PREF_TO_DB_KEY.put(PK_LIST_SHOW_LANGUAGE, DBKey.LANGUAGE);
        PREF_TO_DB_KEY.put(PK_LIST_SHOW_LOCATION, DBKey.LOCATION);
        PREF_TO_DB_KEY.put(PK_LIST_SHOW_RATING, DBKey.RATING);
    }

    static boolean containsKey(@NonNull final String key) {
        return PREF_TO_DB_KEY.containsKey(key);
    }

    @NonNull
    static String getDBKeyForPrefKey(@NonNull final String key) {
        return Objects.requireNonNull(PREF_TO_DB_KEY.get(key));
    }

    /**
     * Constructor.
     */
    BooklistFieldVisibility() {
        super(DB_KEYS, DEFAULT);
    }
}
