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

package com.hardbacknutter.nevertoomanybooks.booklist;

import androidx.annotation.NonNull;

import java.util.List;

import com.hardbacknutter.nevertoomanybooks.booklist.grouping.BooklistGroup;
import com.hardbacknutter.nevertoomanybooks.booklist.style.Style;
import com.hardbacknutter.nevertoomanybooks.core.database.Domain;
import com.hardbacknutter.nevertoomanybooks.core.database.DomainExpression;
import com.hardbacknutter.nevertoomanybooks.core.database.Sort;
import com.hardbacknutter.nevertoomanybooks.core.database.SqLiteDataType;
import com.hardbacknutter.nevertoomanybooks.database.DBDefinitions;
import com.hardbacknutter.nevertoomanybooks.database.DBKey;
import com.hardbacknutter.nevertoomanybooks.database.dao.impl.AuthorDaoImpl;

import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.TBL_BOOK_SERIES;

public final class DBExpr {

    static final DomainExpression BOOK_ID =
            new DomainExpression(DBDefinitions.DOM_FK_BOOK,
                                 DBDefinitions.TBL_BOOKS.dot(DBKey.PK_ID),
                                 Sort.Unsorted);
    static final DomainExpression UUID =
            new DomainExpression(DBDefinitions.DOM_BOOK_UUID,
                                 DBDefinitions.TBL_BOOKS,
                                 Sort.Unsorted);
    static final DomainExpression ISBN =
            new DomainExpression(DBDefinitions.DOM_BOOK_ISBN,
                                 DBDefinitions.TBL_BOOKS,
                                 Sort.Unsorted);
    static final DomainExpression READ =
            new DomainExpression(DBDefinitions.DOM_BOOK_READ,
                                 DBDefinitions.TBL_BOOKS,
                                 Sort.Unsorted);
    static final DomainExpression LANGUAGE =
            new DomainExpression(DBDefinitions.DOM_BOOK_LANGUAGE,
                                 DBDefinitions.TBL_BOOKS,
                                 Sort.Unsorted);
    static final DomainExpression AUTHOR_ID =
            new DomainExpression(DBDefinitions.DOM_FK_AUTHOR,
                                 DBDefinitions.TBL_BOOK_AUTHOR,
                                 Sort.Unsorted);
    static final DomainExpression BOOK_NODE_GROUP =
            new DomainExpression(DBDefinitions.DOM_BL_NODE_GROUP,
                                 String.valueOf(BooklistGroup.BOOK),
                                 Sort.Unsorted);
    static final List<DomainExpression> CALIBRE = List.of(
            new DomainExpression(DBDefinitions.DOM_CALIBRE_BOOK_ID,
                                 DBDefinitions.TBL_CALIBRE_BOOKS,
                                 Sort.Unsorted),
            new DomainExpression(DBDefinitions.DOM_CALIBRE_BOOK_UUID,
                                 DBDefinitions.TBL_CALIBRE_BOOKS,
                                 Sort.Unsorted),
            new DomainExpression(DBDefinitions.DOM_CALIBRE_BOOK_MAIN_FORMAT,
                                 DBDefinitions.TBL_CALIBRE_BOOKS,
                                 Sort.Unsorted),
            new DomainExpression(DBDefinitions.DOM_FK_CALIBRE_LIBRARY,
                                 DBDefinitions.TBL_CALIBRE_BOOKS,
                                 Sort.Unsorted)
    );

    private static final DomainExpression READ_PROGRESS =
            new DomainExpression(DBDefinitions.DOM_BOOK_READ_PROGRESS,
                                 DBDefinitions.TBL_BOOKS,
                                 Sort.Unsorted);

    private static final String _FROM_ = " FROM ";
    private static final String _WHERE_ = " WHERE ";

    /**
     * Expression for the domain {@link DBDefinitions#DOM_BOOKSHELF_NAMES_AS_CSV}.
     * The order of the returned names will be arbitrary.
     * Sorting the CSV is done from code just before displaying.
     */
    private static final DomainExpression BOOKSHELVES_CSV = new DomainExpression(
            DBDefinitions.DOM_BOOKSHELF_NAMES_AS_CSV,
            "(SELECT GROUP_CONCAT("
            // Must use only a single comma, no extra spaces.
            // We will split this string in code, sort it, and then reformat/show it.
            + DBDefinitions.TBL_BOOKSHELF.dot(DBKey.BOOKSHELF.NAME) + ",',')"
            + _FROM_ + DBDefinitions.TBL_BOOKSHELF.startJoin(DBDefinitions.TBL_BOOK_BOOKSHELF)
            + _WHERE_
            + DBDefinitions.TBL_BOOKS.dot(DBKey.PK_ID) + '='
            + DBDefinitions.TBL_BOOK_BOOKSHELF.dot(DBKey.FK_BOOK)
            + ')',
            Sort.Unsorted);

    /**
     * Expression for the domain {@link DBDefinitions#DOM_PUBLISHER_NAMES_AS_CSV}.
     * The order of the returned names will be arbitrary.
     * Sorting the CSV is done from code just before displaying.
     *
     * NOT USED YET. Would need to add the OB as well
     */
    private static final DomainExpression PUBLISHER_NAMES_CSV = new DomainExpression(
            DBDefinitions.DOM_PUBLISHER_NAMES_AS_CSV,
            "(SELECT GROUP_CONCAT("
            + DBDefinitions.TBL_PUBLISHERS.dot(DBKey.PUBLISHER.NAME) + ",', ')"
            + _FROM_ + DBDefinitions.TBL_PUBLISHERS.startJoin(DBDefinitions.TBL_BOOK_PUBLISHER)
            + _WHERE_
            + DBDefinitions.TBL_BOOKS.dot(DBKey.PK_ID) + '='
            + DBDefinitions.TBL_BOOK_PUBLISHER.dot(DBKey.FK_BOOK)
            + ')',
            Sort.Unsorted);

    private DBExpr() {
    }

    /**
     * Get a list of {@link DomainExpression} for the given dbKey.
     * <strong>CHECK THE SWITCH</strong>: some are designed to be
     * retrieved by their {@code FK_*} key.
     * <p>
     * NEWTHINGS: BookLevelField: add field
     *
     * @param dbKey to get
     * @param sort  how-to sort; ignored when n/a
     * @param style to use; only used by some expressions
     *
     * @return list
     *
     * @throws IllegalArgumentException (debug) if we're missing a key
     */
    @NonNull
    public static List<DomainExpression> forBookLevelField(@NonNull final String dbKey,
                                                           @NonNull final Sort sort,
                                                           @NonNull final Style style) {
        if (DBKey.COVER[0].equals(dbKey)) {
            // We need the (unsorted duh!) UUID for the book to get covers
            return List.of(
                    new DomainExpression(
                            DBDefinitions.DOM_BOOK_UUID,
                            DBDefinitions.TBL_BOOKS,
                            Sort.Unsorted));
        } else {
            switch (dbKey) {
                case DBKey.CONDITION_BOOK: {
                    return List.of(
                            new DomainExpression(
                                    DBDefinitions.DOM_BOOK_CONDITION,
                                    DBDefinitions.TBL_BOOKS,
                                    sort)
                    );
                }
                case DBKey.DATE_ACQUIRED: {
                    return List.of(
                            new DomainExpression(
                                    DBDefinitions.DOM_BOOK_DATE_ACQUIRED,
                                    DBDefinitions.TBL_BOOKS,
                                    sort)
                    );
                }
                case DBKey.DATE_ADDED__UTC: {
                    return List.of(
                            new DomainExpression(
                                    DBDefinitions.DOM_DATE_ADDED__UTC,
                                    DBDefinitions.TBL_BOOKS,
                                    sort)
                    );
                }
                case DBKey.DATE_LAST_UPDATED__UTC: {
                    return List.of(
                            new DomainExpression(
                                    DBDefinitions.DOM_LAST_UPDATED__UTC,
                                    DBDefinitions.TBL_BOOKS,
                                    sort)
                    );
                }
                case DBKey.EDITION: {
                    return List.of(
                            new DomainExpression(
                                    DBDefinitions.DOM_BOOK_EDITION,
                                    DBDefinitions.TBL_BOOKS,
                                    sort)
                    );
                }
                case DBKey.FIRST_PUBLICATION_DATE: {
                    return List.of(
                            new DomainExpression(
                                    DBDefinitions.DOM_DATE_FIRST_PUBLICATION,
                                    DBDefinitions.TBL_BOOKS,
                                    sort)
                    );
                }
                case DBKey.FK_AUTHOR: {
                    return List.of(
                            // primary author only
                            // Formatted and sorted
                            new DomainExpression(
                                    DBDefinitions.DOM_AUTHOR_FORMATTED,
                                    AuthorDaoImpl.getDisplayDomainExpression(
                                            style.isShowAuthorByGivenName()),
                                    sort)
                    );
                }
                case DBKey.FK_BOOKSHELF: {
                    return List.of(BOOKSHELVES_CSV);
                }
                case DBKey.FK_PUBLISHER: {
                    // TODO: perhaps get a csv list of publisher names instead?
                    //   return List.of(PUBLISHER_NAMES_CSV);
                    return List.of(
                            // primary publisher only

                            // Displaying; do NOT sort on it
                            new DomainExpression(
                                    DBDefinitions.DOM_PUBLISHER_NAME,
                                    DBDefinitions.TBL_PUBLISHERS,
                                    Sort.Unsorted),
                            // Sorting
                            new DomainExpression(
                                    DBDefinitions.DOM_PUBLISHER_NAME_OB,
                                    DBDefinitions.TBL_PUBLISHERS,
                                    sort)
                    );
                }
                case DBKey.FK_SERIES: {
                    return List.of(
                            // primary series only

                            // Displaying; do NOT sort on it
                            new DomainExpression(
                                    DBDefinitions.DOM_SERIES_TITLE,
                                    DBDefinitions.TBL_SERIES,
                                    Sort.Unsorted),
                            // Sorting
                            new DomainExpression(
                                    DBDefinitions.DOM_SERIES_TITLE_OB,
                                    DBDefinitions.TBL_SERIES,
                                    sort),
                            // The series number in the base data in sorted order.
                            // This field is NOT displayed.
                            // Casting it as a float allows for the possibility of 3.1,
                            // or even 3.1|Omnibus 3-10" as a series number.
                            new DomainExpression(
                                    new Domain.Builder(
                                            BooklistGroup.BlgDBKey.SORT_SERIES_NUM_FLOAT,
                                            SqLiteDataType.Real)
                                            .build(),
                                    "CAST("
                                    + TBL_BOOK_SERIES.dot(DBKey.SERIES.BOOK_SERIES_NUMBER)
                                    + " AS REAL)",
                                    sort),
                            // The series number in the base data in sorted order.
                            // This field is displayed.
                            // Covers non-numeric data (where the above float would fail)
                            new DomainExpression(
                                    DBDefinitions.DOM_BOOK_SERIES_NUMBER,
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
                case DBKey.ISBN: {
                    return List.of(ISBN);
                }
                case DBKey.LANGUAGE: {
                    return List.of(
                            new DomainExpression(
                                    DBDefinitions.DOM_BOOK_LANGUAGE,
                                    DBDefinitions.ALIAS_LANG_MAPPINGS_LANGUAGE
                                    + "." + DBKey.LANG_MAPPING.DISPLAY_NAME,
                                    sort));
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
                case DBKey.PAGES: {
                    return List.of(
                            new DomainExpression(
                                    DBDefinitions.DOM_BOOK_PAGES,
                                    DBDefinitions.TBL_BOOKS,
                                    sort)
                    );
                }
                case DBKey.PUBLICATION_DATE: {
                    return List.of(
                            new DomainExpression(
                                    DBDefinitions.DOM_BOOK_DATE_PUBLISHED,
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
                    return List.of(READ);
                }
                case DBKey.READ_PROGRESS: {
                    return List.of(READ_PROGRESS);
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
                case DBKey.TRANSLATION_ORIGINAL_TITLE: {
                    return List.of(
                            new DomainExpression(
                                    DBDefinitions.DOM_TRANSLATION_ORIGINAL_TITLE,
                                    DBDefinitions.TBL_BOOKS,
                                    sort)
                    );
                }
                case DBKey.TRANSLATION_ORIGINAL_LANGUAGE: {
                    return List.of(
                            new DomainExpression(
                                    DBDefinitions.DOM_TRANSLATION_ORIGINAL_LANGUAGE,
                                    DBDefinitions.ALIAS_LANG_MAPPINGS_ORIGINAL_LANGUAGE
                                    + "." + DBKey.LANG_MAPPING.DISPLAY_NAME,
                                    sort)
                    );
                }
                default:
                    throw new IllegalArgumentException("DBKey missing: " + dbKey);
            }
        }
    }
}
