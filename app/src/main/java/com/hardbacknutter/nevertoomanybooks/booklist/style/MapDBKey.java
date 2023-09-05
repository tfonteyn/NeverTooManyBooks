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

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.annotation.StringRes;

import java.util.List;
import java.util.Map;
import java.util.Objects;

import com.hardbacknutter.nevertoomanybooks.R;
import com.hardbacknutter.nevertoomanybooks.booklist.style.groups.BooklistGroup;
import com.hardbacknutter.nevertoomanybooks.core.database.DomainExpression;
import com.hardbacknutter.nevertoomanybooks.core.database.Sort;
import com.hardbacknutter.nevertoomanybooks.database.DBDefinitions;
import com.hardbacknutter.nevertoomanybooks.database.DBKey;
import com.hardbacknutter.nevertoomanybooks.database.dao.impl.AuthorDaoImpl;

public final class MapDBKey {

    private static final Map<String, String> DB_KEY_TO_DOMAIN_NAME = Map.ofEntries(
            Map.entry(DBKey.COVER[0], DBKey.BOOK_UUID),
            Map.entry(DBKey.COVER[1], DBKey.BOOK_UUID),

            Map.entry(DBKey.BOOK_CONDITION, DBKey.BOOK_CONDITION),
            Map.entry(DBKey.BOOK_ISBN, DBKey.BOOK_ISBN),
            Map.entry(DBKey.BOOK_PUBLICATION__DATE, DBKey.BOOK_PUBLICATION__DATE),
            Map.entry(DBKey.EDITION__BITMASK, DBKey.EDITION__BITMASK),
            Map.entry(DBKey.FK_AUTHOR, DBKey.AUTHOR_FORMATTED),
            Map.entry(DBKey.FK_BOOKSHELF, DBKey.BOOKSHELF_NAME_CSV),
            Map.entry(DBKey.FK_PUBLISHER, DBKey.PUBLISHER_NAME),
            Map.entry(DBKey.FK_SERIES, DBKey.SERIES_TITLE),
            Map.entry(DBKey.FORMAT, DBKey.FORMAT),
            Map.entry(DBKey.LANGUAGE, DBKey.LANGUAGE),
            Map.entry(DBKey.LOANEE_NAME, DBKey.LOANEE_NAME),
            Map.entry(DBKey.LOCATION, DBKey.LOCATION),
            Map.entry(DBKey.PAGE_COUNT, DBKey.PAGE_COUNT),
            Map.entry(DBKey.RATING, DBKey.RATING),
            Map.entry(DBKey.READ__BOOL, DBKey.READ__BOOL),
            Map.entry(DBKey.SIGNED__BOOL, DBKey.SIGNED__BOOL),
            Map.entry(DBKey.TITLE, DBKey.TITLE),
            Map.entry(DBKey.TITLE_ORIGINAL_LANG, DBKey.TITLE_ORIGINAL_LANG)
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
            Map.entry(DBKey.FK_BOOKSHELF, R.string.lbl_bookshelves),
            Map.entry(DBKey.FK_PUBLISHER, R.string.lbl_publisher),
            Map.entry(DBKey.FK_SERIES, R.string.lbl_series),
            Map.entry(DBKey.FK_TOC_ENTRY, R.string.lbl_table_of_content),
            Map.entry(DBKey.FORMAT, R.string.lbl_format),
            Map.entry(DBKey.GENRE, R.string.lbl_genre),
            Map.entry(DBKey.LANGUAGE, R.string.lbl_language),
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
        return Objects.requireNonNull(DB_KEY_TO_DOMAIN_NAME.get(dbKey), dbKey);
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

    // TODO: Not sure this is the best spot to keep this code. Maybe move it back to BobTask ?
    @NonNull
    public static List<DomainExpression> createDomainExpressions(@NonNull final String dbKey,
                                                                 @NonNull final Sort sort,
                                                                 @NonNull final Style style) {

        if (DBKey.COVER[0].equals(dbKey)
            || DBKey.COVER[1].equals(dbKey)) {
            // We need the (unsorted duh!) UUID for the book to get covers
            return List.of(new DomainExpression(
                    DBDefinitions.DOM_BOOK_UUID,
                    DBDefinitions.TBL_BOOKS,
                    Sort.Unsorted));
        } else {
            switch (dbKey) {
                case DBKey.BOOK_CONDITION: {
                    return List.of(
                            new DomainExpression(
                                    DBDefinitions.DOM_BOOK_CONDITION,
                                    DBDefinitions.TBL_BOOKS,
                                    sort)
                    );
                }
                case DBKey.BOOK_ISBN: {
                    return List.of(
                            new DomainExpression(DBDefinitions.DOM_BOOK_ISBN,
                                                 DBDefinitions.TBL_BOOKS,
                                                 Sort.Unsorted)
                    );
                }
                case DBKey.BOOK_PUBLICATION__DATE: {
                    return List.of(
                            new DomainExpression(
                                    DBDefinitions.DOM_BOOK_DATE_PUBLISHED,
                                    DBDefinitions.TBL_BOOKS,
                                    sort)
                    );
                }
                case DBKey.EDITION__BITMASK: {
                    return List.of(
                            new DomainExpression(
                                    DBDefinitions.DOM_BOOK_EDITION,
                                    DBDefinitions.TBL_BOOKS,
                                    sort)
                    );
                }
                case DBKey.FK_AUTHOR: {
                    return List.of(
                            // primary author only
                            new DomainExpression(
                                    DBDefinitions.DOM_AUTHOR_FORMATTED_FAMILY_FIRST,
                                    AuthorDaoImpl.getDisplayDomainExpression(
                                            style.isShowAuthorByGivenName()),
                                    sort)
                    );
                }
                case DBKey.FK_BOOKSHELF: {
                    return List.of(
                            // Collect a CSV list of the bookshelves the book is on.
                            // It is ALWAYS unsorted, as the list is build by SQLite internals
                            // and the order returned is arbitrary.
                            new DomainExpression(
                                    DBDefinitions.DOM_BOOKSHELF_NAME_CSV,
                                    DBDefinitions.EXP_BOOKSHELF_NAME_CSV,
                                    Sort.Unsorted)
                    );
                }
                case DBKey.FK_PUBLISHER: {
                    return List.of(
                            // primary publisher only
                            new DomainExpression(
                                    DBDefinitions.DOM_PUBLISHER_NAME,
                                    DBDefinitions.TBL_PUBLISHERS,
                                    sort)
                    );
                }
                case DBKey.FK_SERIES: {
                    return List.of(
                            // primary series only
                            new DomainExpression(
                                    DBDefinitions.DOM_SERIES_TITLE,
                                    DBDefinitions.TBL_SERIES,
                                    sort),
                            new DomainExpression(
                                    DBDefinitions.DOM_BOOK_NUM_IN_SERIES,
                                    DBDefinitions.TBL_BOOK_SERIES,
                                    sort)
                    );
                }
                case DBKey.FORMAT: {
                    return List.of(
                            new DomainExpression(
                                    DBDefinitions.DOM_BOOK_FORMAT,
                                    DBDefinitions.TBL_BOOKS,
                                    sort)
                    );
                }
                case DBKey.LANGUAGE: {
                    return List.of(
                            new DomainExpression(DBDefinitions.DOM_BOOK_LANGUAGE,
                                                 DBDefinitions.TBL_BOOKS,
                                                 Sort.Unsorted)
                    );
                }
                case DBKey.LOANEE_NAME: {
                    return List.of(
                            // Used to display/hide the 'lend' icon for each book.
                            new DomainExpression(
                                    DBDefinitions.DOM_LOANEE,
                                    DBDefinitions.TBL_BOOK_LOANEE,
                                    sort)
                    );
                }
                case DBKey.LOCATION: {
                    return List.of(
                            new DomainExpression(
                                    DBDefinitions.DOM_BOOK_LOCATION,
                                    DBDefinitions.TBL_BOOKS,
                                    sort)
                    );
                }
                case DBKey.PAGE_COUNT: {
                    return List.of(
                            new DomainExpression(
                                    DBDefinitions.DOM_BOOK_PAGES,
                                    DBDefinitions.TBL_BOOKS,
                                    sort)
                    );
                }
                case DBKey.RATING: {
                    return List.of(
                            new DomainExpression(
                                    DBDefinitions.DOM_BOOK_RATING,
                                    DBDefinitions.TBL_BOOKS,
                                    sort)
                    );
                }
                case DBKey.READ__BOOL: {
                    return List.of(
                            new DomainExpression(DBDefinitions.DOM_BOOK_READ,
                                                 DBDefinitions.TBL_BOOKS,
                                                 Sort.Unsorted)
                    );
                }
                case DBKey.SIGNED__BOOL: {
                    return List.of(
                            new DomainExpression(
                                    DBDefinitions.DOM_BOOK_SIGNED,
                                    DBDefinitions.TBL_BOOKS,
                                    sort)
                    );
                }
                case DBKey.TITLE: {
                    return List.of(
                            // Title for displaying; do NOT sort on it
                            // Example: "The Dream Master"
                            new DomainExpression(
                                    DBDefinitions.DOM_TITLE,
                                    DBDefinitions.TBL_BOOKS,
                                    Sort.Unsorted),
                            // Title for sorting
                            // Example: "dreammasterthe" OR "thedreammaster"
                            // i.e. depending on user preference, the first format
                            // consists of the original title stripped of whitespace and any
                            // special characters, and with the article/prefix moved to the end.
                            // The second format leaves the article/prefix in its original
                            // location.
                            // The choice between the two formats is a user preference which,
                            // when changed, updates ALL rows in the database with the
                            // newly formatted title.
                            new DomainExpression(
                                    DBDefinitions.DOM_TITLE_OB,
                                    DBDefinitions.TBL_BOOKS,
                                    sort)
                    );
                }
                case DBKey.TITLE_ORIGINAL_LANG: {
                    return List.of(
                            new DomainExpression(
                                    DBDefinitions.DOM_TITLE_ORIGINAL_LANG,
                                    DBDefinitions.TBL_BOOKS,
                                    sort)
                    );
                }

                default:
                    throw new IllegalArgumentException("DBKey missing: " + dbKey);
            }
        }
    }
}
