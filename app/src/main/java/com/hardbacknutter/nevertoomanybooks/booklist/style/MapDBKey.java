/*
 * @Copyright 2018-2024 HardBackNutter
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
import androidx.annotation.StringRes;

import java.util.Map;
import java.util.Objects;

import com.hardbacknutter.nevertoomanybooks.R;
import com.hardbacknutter.nevertoomanybooks.booklist.style.groups.BooklistGroup;
import com.hardbacknutter.nevertoomanybooks.database.DBKey;

public final class MapDBKey {

    private static final Map<String, String> DB_KEY_TO_DOMAIN_NAME = Map.ofEntries(
            Map.entry(DBKey.COVER[0], DBKey.BOOK_UUID),
            Map.entry(DBKey.COVER[1], DBKey.BOOK_UUID),

            Map.entry(DBKey.FK_AUTHOR, DBKey.AUTHOR_FORMATTED),
            Map.entry(DBKey.FK_BOOKSHELF, DBKey.BOOKSHELF_NAME_CSV),
            Map.entry(DBKey.FK_PUBLISHER, DBKey.PUBLISHER_NAME),
            Map.entry(DBKey.FK_SERIES, DBKey.SERIES_TITLE)
    );

    private static final Map<String, Integer> DB_KEY_TO_LABEL_RES_ID = Map.ofEntries(
            Map.entry(DBKey.COVER[0], R.string.lbl_cover_front),
            Map.entry(DBKey.COVER[1], R.string.lbl_cover_back),

            Map.entry(DBKey.AUTHOR_REAL_AUTHOR, R.string.lbl_author_pseudonym),
            Map.entry(DBKey.AUTHOR_TYPE__BITMASK, R.string.lbl_author_type),
            Map.entry(DBKey.BOOK_CONDITION, R.string.lbl_condition),
            Map.entry(DBKey.BOOK_CONDITION_COVER, R.string.lbl_dust_cover),
            Map.entry(DBKey.BOOK_ISBN, R.string.lbl_isbn),
            Map.entry(DBKey.BOOK_PUBLICATION__DATE, R.string.lbl_date_published),
            Map.entry(DBKey.COLOR, R.string.lbl_color),
            Map.entry(DBKey.DATE_ADDED__UTC, R.string.lbl_date_added),
            Map.entry(DBKey.DATE_LAST_UPDATED__UTC, R.string.lbl_date_last_updated),
            Map.entry(DBKey.DESCRIPTION, R.string.lbl_description),
            Map.entry(DBKey.EDITION__BITMASK, R.string.lbl_edition),
            Map.entry(DBKey.FIRST_PUBLICATION__DATE, R.string.lbl_date_first_publication),
            Map.entry(DBKey.FK_AUTHOR, R.string.lbl_author),
            Map.entry(DBKey.FK_BOOKSHELF, R.string.lbl_bookshelf),
            Map.entry(DBKey.FK_PUBLISHER, R.string.lbl_publisher),
            Map.entry(DBKey.FK_SERIES, R.string.lbl_series),
            Map.entry(DBKey.FK_TOC_ENTRY, R.string.lbl_table_of_content),
            Map.entry(DBKey.FORMAT, R.string.lbl_format),
            Map.entry(DBKey.GENRE, R.string.lbl_genre),
            Map.entry(DBKey.LANGUAGE, R.string.lbl_language),
            // TEST: should this be R.string.lbl_lend_out instead?
            Map.entry(DBKey.LOANEE_NAME, R.string.lbl_lending),
            Map.entry(DBKey.LOCATION, R.string.lbl_location),
            Map.entry(DBKey.PAGE_COUNT, R.string.lbl_pages),
            Map.entry(DBKey.PERSONAL_NOTES, R.string.lbl_personal_notes),
            Map.entry(DBKey.PRICE_LISTED, R.string.lbl_price_listed),
            Map.entry(DBKey.PRICE_PAID, R.string.lbl_price_paid),
            Map.entry(DBKey.RATING, R.string.lbl_rating),
            Map.entry(DBKey.READ_END__DATE, R.string.lbl_read_end),
            Map.entry(DBKey.READ_START__DATE, R.string.lbl_read_start),
            Map.entry(DBKey.READ__BOOL, R.string.lbl_read),
            Map.entry(DBKey.READ_PROGRESS, R.string.lbl_track_progress),
            Map.entry(DBKey.SIGNED__BOOL, R.string.lbl_signed),
            Map.entry(DBKey.TITLE, R.string.lbl_title),
            Map.entry(DBKey.TITLE_ORIGINAL_LANG, R.string.lbl_original_title),

            // The BooklistGroup specific domains for sorting
            Map.entry(BooklistGroup.BlgKey.SORT_AUTHOR, R.string.lbl_author),
            Map.entry(BooklistGroup.BlgKey.SORT_BOOKSHELF, R.string.lbl_bookshelf),
            Map.entry(BooklistGroup.BlgKey.SORT_PUBLISHER, R.string.lbl_publisher),
            Map.entry(BooklistGroup.BlgKey.SORT_SERIES_TITLE, R.string.lbl_series),
            Map.entry(BooklistGroup.BlgKey.SORT_SERIES_NUM_FLOAT, R.string.lbl_series_num)
    );

    private MapDBKey() {
    }

    /**
     * Map the {@link DBKey} to the actual domain name. These are usually identical,
     * but will be different for e.g. id columns to linked tables an others.
     *
     * @param dbKey to map
     *
     * @return actual domain name
     */
    @NonNull
    public static String getDomainName(@NonNull final String dbKey) {
        final String domainName = DB_KEY_TO_DOMAIN_NAME.get(dbKey);
        return Objects.requireNonNullElse(domainName, dbKey);
    }

    /**
     * Get the matching label for the given key.
     *
     * @param context Current context
     * @param dbKey   to fetch
     *
     * @return human readable label
     *
     * @throws NullPointerException if the key is invalid
     */
    @NonNull
    public static String getLabel(@NonNull final Context context,
                                  @NonNull final String dbKey) {
        @StringRes
        final int resId = Objects.requireNonNull(DB_KEY_TO_LABEL_RES_ID.get(dbKey), dbKey);
        return context.getString(resId);
    }
}
