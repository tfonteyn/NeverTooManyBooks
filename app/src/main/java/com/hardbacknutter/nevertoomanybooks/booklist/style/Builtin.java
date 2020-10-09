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
package com.hardbacknutter.nevertoomanybooks.booklist.style;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

import com.hardbacknutter.nevertoomanybooks.R;
import com.hardbacknutter.nevertoomanybooks.booklist.groups.BooklistGroup;

/**
 * Collection of system-defined booklist styles.
 * <p>
 * The UUID's should never be changed.
 */
public final class Builtin {

    // NEWTHINGS: BooklistStyle. Make sure to update the max id when adding a style!
    // and make sure a row is added to the database styles table.
    // next max is -20
    public static final int MAX_ID = -19;

    /**
     * Note the hardcoded negative ID's.
     * These id/uuid's should NEVER be changed as they will
     * get stored in preferences and serialized. Take care not to add duplicates.
     */
    static final int AUTHOR_THEN_SERIES_ID = -1;
    private static final String AUTHOR_THEN_SERIES_UUID
            = "6a82c4c0-48f1-4130-8a62-bbf478ffe184";

    static final String DEFAULT_STYLE_UUID = AUTHOR_THEN_SERIES_UUID;

    private static final int UNREAD_AUTHOR_THEN_SERIES_ID = -2;
    private static final String UNREAD_AUTHOR_THEN_SERIES_UUID
            = "f479e979-c43f-4b0b-9c5b-6942964749df";

    private static final int COMPACT_ID = -3;
    private static final String COMPACT_UUID
            = "5e4c3137-a05f-4c4c-853a-bd1dacb6cd16";

    private static final int TITLE_FIRST_LETTER_ID = -4;
    private static final String TITLE_FIRST_LETTER_UUID
            = "16b4ecdf-edef-4bf2-a682-23f7230446c8";

    private static final int SERIES_ID = -5;
    private static final String SERIES_UUID
            = "ad55ebc3-f79d-4cc2-a27d-f06ff0bf2335";

    private static final int GENRE_ID = -6;
    private static final String GENRE_UUID
            = "edc5c178-60f0-40e7-9674-e08445b6c942";

    private static final int LENDING_ID = -7;
    private static final String LENDING_UUID
            = "e4f1c364-2cbe-467e-a0c1-3ae71bd56fa3";

    private static final int READ_AND_UNREAD_ID = -8;
    private static final String READ_AND_UNREAD_UUID
            = "e3678890-7785-4870-9213-333a68293a49";

    private static final int PUBLICATION_DATA_ID = -9;
    private static final String PUBLICATION_DATA_UUID
            = "182f5d3c-8fd7-4f3a-b5b0-0c93551d1796";

    private static final int DATE_ADDED_ID = -10;
    private static final String DATE_ADDED_UUID
            = "95d7afc0-a70a-4f1f-8d77-aa7ebc60e521";

    private static final int DATE_ACQUIRED_ID = -11;
    private static final String DATE_ACQUIRED_UUID
            = "b3255b1f-5b07-4b3e-9700-96c0f8f35a58";

    private static final int AUTHOR_AND_YEAR_ID = -12;
    private static final String AUTHOR_AND_YEAR_UUID
            = "7c9ad91e-df7c-415a-a205-cdfabff5465d";

    private static final int FORMAT_ID = -13;
    private static final String FORMAT_UUID
            = "bdc43f17-2a95-42ef-b0f8-c750ef920f28";

    private static final int DATE_READ_ID = -14;
    private static final String DATE_READ_UUID
            = "034fe547-879b-4fa0-997a-28d769ba5a84";

    private static final int LOCATION_ID = -15;
    private static final String LOCATION_UUID
            = "e21a90c9-5150-49ee-a204-0cab301fc5a1";

    private static final int LANGUAGE_ID = -16;
    private static final String LANGUAGE_UUID
            = "00379d95-6cb2-40e6-8c3b-f8278f34750a";

    private static final int RATING_ID = -17;
    private static final String RATING_UUID
            = "20a2ebdf-81a7-4eca-a3a9-7275062b907a";

    private static final int BOOKSHELF_ID = -18;
    private static final String BOOKSHELF_UUID
            = "999d383e-6e76-416a-86f9-960c729aa718";

    private static final int DATE_LAST_UPDATE_ID = -19;
    private static final String DATE_LAST_UPDATE_UUID
            = "427a0da5-0779-44b6-89e9-82772e5ad5ef";
    /**
     * Use the NEGATIVE builtin style id to get the UUID for it. Element 0 is not used.
     * NEVER change the order.
     */
    private static final String[] ID_UUID = {
            "",
            AUTHOR_THEN_SERIES_UUID,
            UNREAD_AUTHOR_THEN_SERIES_UUID,
            COMPACT_UUID,
            TITLE_FIRST_LETTER_UUID,
            SERIES_UUID,

            GENRE_UUID,
            LENDING_UUID,
            READ_AND_UNREAD_UUID,
            PUBLICATION_DATA_UUID,
            DATE_ADDED_UUID,

            DATE_ACQUIRED_UUID,
            AUTHOR_AND_YEAR_UUID,
            FORMAT_UUID,
            DATE_READ_UUID,
            LOCATION_UUID,

            LANGUAGE_UUID,
            RATING_UUID,
            BOOKSHELF_UUID,
            DATE_LAST_UPDATE_UUID,
            };
    /**
     * We keep a cache of Builtin styles in memory as it's to costly to keep
     * re-creating {@link BooklistStyle} objects.
     * Pre-loaded on first access.
     * Re-loaded when the Locale changes.
     * <p>
     * Key: uuid of style.
     */
    private static final Map<String, BooklistStyle> S_BUILTIN_STYLES = new LinkedHashMap<>();
    /**
     * Hardcoded initial/default style. Avoids having the create the full set of styles just
     * to load the default one. Created on first access in {@link #getDefault}.
     */
    private static BooklistStyle sDefaultStyle;

    private Builtin() {
    }

    /**
     * Get all builtin styles.
     *
     * @param context Current context
     *
     * @return an ordered unmodifiableMap of all builtin styles.
     */
    @SuppressWarnings("SameReturnValue")
    @NonNull
    static Map<String, BooklistStyle> getStyles(@NonNull final Context context) {

        if (S_BUILTIN_STYLES.isEmpty()) {
            create(context);
        }
        return Collections.unmodifiableMap(S_BUILTIN_STYLES);
    }

    @NonNull
    public static BooklistStyle getDefault(@NonNull final Context context) {
        if (sDefaultStyle == null) {
            sDefaultStyle = new BooklistStyle(context,
                                              AUTHOR_THEN_SERIES_ID,
                                              AUTHOR_THEN_SERIES_UUID,
                                              R.string.style_builtin_author_series,
                                              BooklistGroup.AUTHOR,
                                              BooklistGroup.SERIES);
        }
        return sDefaultStyle;
    }

    @NonNull
    public static String getUuidById(final int id) {
        return ID_UUID[id];
    }

    @Nullable
    static BooklistStyle getStyle(@NonNull final Context context,
                                  @NonNull final String uuid) {
        return getStyles(context).get(uuid);
    }

    private static void create(@NonNull final Context context) {

        BooklistStyle style;

        // Author/Series
        style = getDefault(context);
        S_BUILTIN_STYLES.put(style.getUuid(), style);

        // Unread
        style = new BooklistStyle(context,
                                  UNREAD_AUTHOR_THEN_SERIES_ID,
                                  UNREAD_AUTHOR_THEN_SERIES_UUID,
                                  R.string.style_builtin_unread,
                                  BooklistGroup.AUTHOR,
                                  BooklistGroup.SERIES);
        S_BUILTIN_STYLES.put(style.getUuid(), style);
        style.getFilters().setFilter(Filters.PK_FILTER_READ, false);

        // Compact
        style = new BooklistStyle(context,
                                  COMPACT_ID,
                                  COMPACT_UUID,
                                  R.string.style_builtin_compact,
                                  BooklistGroup.AUTHOR);
        S_BUILTIN_STYLES.put(style.getUuid(), style);
        style.getTextScale().setScale(TextScale.TEXT_SCALE_1_SMALL);
        style.getListScreenBookFields()
             .setShowField(ListScreenBookFields.PK_COVERS, false);

        // Title
        style = new BooklistStyle(context,
                                  TITLE_FIRST_LETTER_ID,
                                  TITLE_FIRST_LETTER_UUID,
                                  R.string.style_builtin_first_letter_book_title,
                                  BooklistGroup.BOOK_TITLE_LETTER);
        S_BUILTIN_STYLES.put(style.getUuid(), style);

        // Series
        style = new BooklistStyle(context,
                                  SERIES_ID,
                                  SERIES_UUID,
                                  R.string.style_builtin_series,
                                  BooklistGroup.SERIES);
        S_BUILTIN_STYLES.put(style.getUuid(), style);

        // Genre
        style = new BooklistStyle(context,
                                  GENRE_ID,
                                  GENRE_UUID,
                                  R.string.style_builtin_genre,
                                  BooklistGroup.GENRE,
                                  BooklistGroup.AUTHOR,
                                  BooklistGroup.SERIES);
        S_BUILTIN_STYLES.put(style.getUuid(), style);

        // Lending
        style = new BooklistStyle(context,
                                  LENDING_ID,
                                  LENDING_UUID,
                                  R.string.style_builtin_lending,
                                  BooklistGroup.LENDING,
                                  BooklistGroup.AUTHOR,
                                  BooklistGroup.SERIES);
        S_BUILTIN_STYLES.put(style.getUuid(), style);

        // Read & Unread
        style = new BooklistStyle(context,
                                  READ_AND_UNREAD_ID,
                                  READ_AND_UNREAD_UUID,
                                  R.string.style_builtin_read_and_unread,
                                  BooklistGroup.READ_STATUS,
                                  BooklistGroup.AUTHOR,
                                  BooklistGroup.SERIES);
        S_BUILTIN_STYLES.put(style.getUuid(), style);

        // Publication date
        style = new BooklistStyle(context,
                                  PUBLICATION_DATA_ID,
                                  PUBLICATION_DATA_UUID,
                                  R.string.style_builtin_publication_date,
                                  BooklistGroup.DATE_PUBLISHED_YEAR,
                                  BooklistGroup.DATE_PUBLISHED_MONTH,
                                  BooklistGroup.AUTHOR,
                                  BooklistGroup.SERIES);
        S_BUILTIN_STYLES.put(style.getUuid(), style);

        // Added date
        style = new BooklistStyle(context,
                                  DATE_ADDED_ID,
                                  DATE_ADDED_UUID,
                                  R.string.style_builtin_added_date,
                                  BooklistGroup.DATE_ADDED_YEAR,
                                  BooklistGroup.DATE_ADDED_MONTH,
                                  BooklistGroup.DATE_ADDED_DAY,
                                  BooklistGroup.AUTHOR);
        S_BUILTIN_STYLES.put(style.getUuid(), style);

        // Acquired date
        style = new BooklistStyle(context,
                                  DATE_ACQUIRED_ID,
                                  DATE_ACQUIRED_UUID,
                                  R.string.style_builtin_acquired_date,
                                  BooklistGroup.DATE_ACQUIRED_YEAR,
                                  BooklistGroup.DATE_ACQUIRED_MONTH,
                                  BooklistGroup.DATE_ACQUIRED_DAY,
                                  BooklistGroup.AUTHOR);
        S_BUILTIN_STYLES.put(style.getUuid(), style);

        // Author/Publication date
        style = new BooklistStyle(context,
                                  AUTHOR_AND_YEAR_ID,
                                  AUTHOR_AND_YEAR_UUID,
                                  R.string.style_builtin_author_year,
                                  BooklistGroup.AUTHOR,
                                  BooklistGroup.DATE_PUBLISHED_YEAR,
                                  BooklistGroup.SERIES);
        S_BUILTIN_STYLES.put(style.getUuid(), style);

        // Format
        style = new BooklistStyle(context,
                                  FORMAT_ID,
                                  FORMAT_UUID,
                                  R.string.style_builtin_format,
                                  BooklistGroup.FORMAT);
        S_BUILTIN_STYLES.put(style.getUuid(), style);

        // Read date
        style = new BooklistStyle(context,
                                  DATE_READ_ID,
                                  DATE_READ_UUID,
                                  R.string.style_builtin_read_date,
                                  BooklistGroup.DATE_READ_YEAR,
                                  BooklistGroup.DATE_READ_MONTH,
                                  BooklistGroup.AUTHOR);
        S_BUILTIN_STYLES.put(style.getUuid(), style);
        style.getFilters().setFilter(Filters.PK_FILTER_READ, true);

        // Location
        style = new BooklistStyle(context,
                                  LOCATION_ID,
                                  LOCATION_UUID,
                                  R.string.style_builtin_location,
                                  BooklistGroup.LOCATION,
                                  BooklistGroup.AUTHOR,
                                  BooklistGroup.SERIES);
        S_BUILTIN_STYLES.put(style.getUuid(), style);

        // Location
        style = new BooklistStyle(context,
                                  LANGUAGE_ID,
                                  LANGUAGE_UUID,
                                  R.string.style_builtin_language,
                                  BooklistGroup.LANGUAGE,
                                  BooklistGroup.AUTHOR,
                                  BooklistGroup.SERIES);
        S_BUILTIN_STYLES.put(style.getUuid(), style);

        // Rating
        style = new BooklistStyle(context,
                                  RATING_ID,
                                  RATING_UUID,
                                  R.string.style_builtin_rating,
                                  BooklistGroup.RATING,
                                  BooklistGroup.AUTHOR,
                                  BooklistGroup.SERIES);
        S_BUILTIN_STYLES.put(style.getUuid(), style);

        // Bookshelf
        style = new BooklistStyle(context,
                                  BOOKSHELF_ID,
                                  BOOKSHELF_UUID,
                                  R.string.style_builtin_bookshelf,
                                  BooklistGroup.BOOKSHELF,
                                  BooklistGroup.AUTHOR,
                                  BooklistGroup.SERIES);
        S_BUILTIN_STYLES.put(style.getUuid(), style);

        // Update date
        style = new BooklistStyle(context,
                                  DATE_LAST_UPDATE_ID,
                                  DATE_LAST_UPDATE_UUID,
                                  R.string.style_builtin_update_date,
                                  BooklistGroup.DATE_LAST_UPDATE_YEAR,
                                  BooklistGroup.DATE_LAST_UPDATE_MONTH,
                                  BooklistGroup.DATE_LAST_UPDATE_DAY);
        S_BUILTIN_STYLES.put(style.getUuid(), style);
        style.getListScreenBookFields()
             .setShowField(ListScreenBookFields.PK_AUTHOR, true);

        // NEWTHINGS: BooklistStyle: add a new builtin style
    }
}
