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
package com.hardbacknutter.nevertoomanybooks.database;

import android.app.SearchManager;
import android.widget.AutoCompleteTextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.hardbacknutter.nevertoomanybooks.booklist.style.BooklistStyle;
import com.hardbacknutter.nevertoomanybooks.database.definitions.Domain;
import com.hardbacknutter.nevertoomanybooks.entities.Author;
import com.hardbacknutter.nevertoomanybooks.entities.AuthorWork;
import com.hardbacknutter.nevertoomanybooks.entities.Book;
import com.hardbacknutter.nevertoomanybooks.entities.Bookshelf;
import com.hardbacknutter.nevertoomanybooks.entities.Publisher;
import com.hardbacknutter.nevertoomanybooks.entities.Series;
import com.hardbacknutter.nevertoomanybooks.entities.TocEntry;
import com.hardbacknutter.nevertoomanybooks.goodreads.GoodreadsManager;
import com.hardbacknutter.nevertoomanybooks.searches.SearchEngineRegistry;

import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.KEY_AUTHOR_FAMILY_NAME;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.KEY_AUTHOR_FAMILY_NAME_OB;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.KEY_AUTHOR_FORMATTED;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.KEY_AUTHOR_FORMATTED_GIVEN_FIRST;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.KEY_AUTHOR_GIVEN_NAMES;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.KEY_AUTHOR_GIVEN_NAMES_OB;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.KEY_AUTHOR_IS_COMPLETE;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.KEY_BOOKSHELF_BL_TOP_OFFSET;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.KEY_BOOKSHELF_BL_TOP_POS;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.KEY_BOOKSHELF_NAME;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.KEY_BOOK_AUTHOR_POSITION;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.KEY_BOOK_AUTHOR_TYPE_BITMASK;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.KEY_BOOK_CONDITION;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.KEY_BOOK_CONDITION_COVER;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.KEY_BOOK_COUNT;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.KEY_BOOK_NUM_IN_SERIES;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.KEY_BOOK_PUBLISHER_POSITION;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.KEY_BOOK_SERIES_POSITION;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.KEY_BOOK_TOC_ENTRY_POSITION;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.KEY_BOOK_UUID;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.KEY_COLOR;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.KEY_DATE_ACQUIRED;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.KEY_DATE_FIRST_PUBLICATION;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.KEY_DATE_PUBLISHED;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.KEY_DESCRIPTION;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.KEY_EDITION_BITMASK;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.KEY_EID_CALIBRE_UUID;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.KEY_EID_GOODREADS_BOOK;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.KEY_FK_AUTHOR;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.KEY_FK_BOOK;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.KEY_FK_BOOKSHELF;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.KEY_FK_PUBLISHER;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.KEY_FK_SERIES;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.KEY_FK_STYLE;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.KEY_FK_TOC_ENTRY;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.KEY_FORMAT;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.KEY_FTS_AUTHOR_NAME;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.KEY_FTS_BOOK_ID;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.KEY_FTS_TOC_ENTRY_TITLE;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.KEY_GENRE;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.KEY_ISBN;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.KEY_LANGUAGE;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.KEY_LOANEE;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.KEY_LOCATION;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.KEY_PAGES;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.KEY_PK_ID;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.KEY_PRICE_LISTED;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.KEY_PRICE_LISTED_CURRENCY;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.KEY_PRICE_PAID;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.KEY_PRICE_PAID_CURRENCY;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.KEY_PRINT_RUN;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.KEY_PRIVATE_NOTES;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.KEY_PUBLISHER_NAME;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.KEY_PUBLISHER_NAME_OB;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.KEY_RATING;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.KEY_READ;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.KEY_READ_END;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.KEY_READ_START;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.KEY_SERIES_IS_COMPLETE;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.KEY_SERIES_TITLE;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.KEY_SERIES_TITLE_OB;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.KEY_SIGNED;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.KEY_STYLE_IS_BUILTIN;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.KEY_TITLE;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.KEY_TITLE_OB;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.KEY_TOC_BITMASK;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.KEY_TOC_TYPE;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.KEY_UTC_ADDED;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.KEY_UTC_GOODREADS_LAST_SYNC_DATE;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.KEY_UTC_LAST_UPDATED;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.KEY_UUID;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.TBL_AUTHORS;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.TBL_BOOKLIST_STYLES;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.TBL_BOOKS;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.TBL_BOOKSHELF;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.TBL_BOOK_AUTHOR;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.TBL_BOOK_BOOKSHELF;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.TBL_BOOK_LIST_NODE_STATE;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.TBL_BOOK_LOANEE;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.TBL_BOOK_PUBLISHER;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.TBL_BOOK_SERIES;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.TBL_BOOK_TOC_ENTRIES;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.TBL_FTS_BOOKS;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.TBL_PUBLISHERS;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.TBL_SERIES;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.TBL_TOC_ENTRIES;

public class DAOSql {

    /**
     * In addition to SQLite's default BINARY collator (others: NOCASE and RTRIM),
     * Android supplies two more.
     * LOCALIZED: using the system's current Locale,
     * UNICODE  : Unicode Collation Algorithm and not tailored to the current Locale.
     * <p>
     * We tried 'Collate UNICODE' but it seemed to be case sensitive.
     * We ended up with 'Ursula Le Guin' and 'Ursula le Guin'.
     * <p>
     * We now use Collate LOCALE and check to see if it is case sensitive.
     * We *hope* in the future Android will add LOCALE_CI (or equivalent).
     * <p>
     * <strong>Note:</strong> Important to have a space at the start
     */
    public static final String _COLLATION = " Collate LOCALIZED";

    private static final String SELECT_ = "SELECT ";
    private static final String _FROM_ = " FROM ";
    private static final String _WHERE_ = " WHERE ";
    private static final String _ORDER_BY_ = " ORDER BY ";
    private static final String SELECT_DISTINCT_ = "SELECT DISTINCT ";
    private static final String _AS_ = " AS ";

    /**
     * Commonly used SQL table columns.
     */
    public static final class SqlColumns {

        /**
         * Expression for the domain {@link DBDefinitions#DOM_BOOKSHELF_NAME_CSV}.
         * <p>
         * The order of the returned names will be arbitrary.
         * We could add an ORDER BY GROUP_CONCAT(... if we GROUP BY
         */
        public static final String EXP_BOOKSHELF_NAME_CSV =
                "("
                + "SELECT GROUP_CONCAT(" + TBL_BOOKSHELF.dot(KEY_BOOKSHELF_NAME) + ",', ')"
                + _FROM_ + TBL_BOOKSHELF.ref() + TBL_BOOKSHELF.join(TBL_BOOK_BOOKSHELF)
                + _WHERE_ + TBL_BOOKS.dot(KEY_PK_ID) + "=" + TBL_BOOK_BOOKSHELF.dot(KEY_FK_BOOK)
                + ")";
        /**
         * Expression for the domain {@link DBDefinitions#DOM_PUBLISHER_NAME_CSV}.
         * <p>
         * The order of the returned names will be arbitrary.
         * We could add an ORDER BY GROUP_CONCAT(... if we GROUP BY
         */
        public static final String EXP_PUBLISHER_NAME_CSV =
                "("
                + "SELECT GROUP_CONCAT(" + TBL_PUBLISHERS.dot(KEY_PUBLISHER_NAME) + ",', ')"
                + _FROM_ + TBL_PUBLISHERS.ref() + TBL_PUBLISHERS.join(TBL_BOOK_PUBLISHER)
                + _WHERE_ + TBL_BOOKS.dot(KEY_PK_ID) + "=" + TBL_BOOK_PUBLISHER.dot(KEY_FK_BOOK)
                + ")";
        private static final String _WHEN_ = " WHEN ";
        private static final String CASE = "CASE";
        private static final String _THEN_ = " THEN ";
        private static final String _ELSE_ = " ELSE ";
        private static final String _END = " END";
        /**
         * SQL column: return 1 if the book is available, 0 if not.
         * {@link DBDefinitions#KEY_LOANEE_AS_BOOLEAN}
         */
        public static final String EXP_LOANEE_AS_BOOLEAN =
                CASE
                + _WHEN_ + TBL_BOOK_LOANEE.dot(KEY_LOANEE) + " IS NULL THEN 1"
                + " ELSE 0"
                + _END;
        /**
         * SQL column: return "" if the book is available, "loanee name" if not.
         */
        public static final String EXP_BOOK_LOANEE_OR_EMPTY =
                CASE
                + _WHEN_ + TBL_BOOK_LOANEE.dot(KEY_LOANEE) + " IS NULL THEN ''"
                + _ELSE_ + TBL_BOOK_LOANEE.dot(KEY_LOANEE)
                + _END;

        /**
         * Single column, with the formatted name of the Author.
         * Note how the 'otherwise' will always concatenate the names without white space.
         *
         * @param givenNameFirst {@code true}
         *                       If no given name -> "FamilyName"
         *                       otherwise -> "GivenNamesFamilyName"
         *                       {@code false}
         *                       If no given name -> "FamilyName"
         *                       otherwise -> "FamilyNameGivenNames"
         *
         * @return column expression
         */
        @NonNull
        public static String getSortAuthor(final boolean givenNameFirst) {
            if (givenNameFirst) {
                return CASE
                       + _WHEN_ + TBL_AUTHORS.dot(KEY_AUTHOR_GIVEN_NAMES_OB) + "=''"
                       + _THEN_ + TBL_AUTHORS.dot(KEY_AUTHOR_FAMILY_NAME_OB)

                       + _ELSE_ + TBL_AUTHORS.dot(KEY_AUTHOR_GIVEN_NAMES_OB)
                       + "||" + TBL_AUTHORS.dot(KEY_AUTHOR_FAMILY_NAME_OB)
                       + _END;
            } else {
                return CASE
                       + _WHEN_ + TBL_AUTHORS.dot(KEY_AUTHOR_GIVEN_NAMES_OB) + "=''"
                       + _THEN_ + TBL_AUTHORS.dot(KEY_AUTHOR_FAMILY_NAME_OB)

                       + _ELSE_ + TBL_AUTHORS.dot(KEY_AUTHOR_FAMILY_NAME_OB)
                       + "||" + TBL_AUTHORS.dot(KEY_AUTHOR_GIVEN_NAMES_OB)
                       + _END;
            }
        }

        /**
         * Single column, with the formatted name of the Author.
         *
         * @param tableAlias     to prefix
         * @param givenNameFirst {@code true}
         *                       If no given name -> "FamilyName"
         *                       otherwise -> "GivenNames FamilyName"
         *                       {@code false}
         *                       If no given name -> "FamilyName"
         *                       otherwise -> "FamilyName, GivenNames"
         *
         * @return column expression
         */
        @NonNull
        public static String getDisplayAuthor(@NonNull final String tableAlias,
                                              final boolean givenNameFirst) {
            if (givenNameFirst) {
                return CASE
                       + _WHEN_ + tableAlias + '.' + KEY_AUTHOR_GIVEN_NAMES + "=''"
                       + _THEN_ + tableAlias + '.' + KEY_AUTHOR_FAMILY_NAME
                       + _ELSE_
                       + tableAlias + '.' + KEY_AUTHOR_GIVEN_NAMES
                       + "||' '||" + tableAlias + '.' + KEY_AUTHOR_FAMILY_NAME
                       + _END;
            } else {
                return CASE
                       + _WHEN_ + tableAlias + '.' + KEY_AUTHOR_GIVEN_NAMES + "=''"
                       + _THEN_ + tableAlias + '.' + KEY_AUTHOR_FAMILY_NAME
                       + _ELSE_
                       + tableAlias + '.' + KEY_AUTHOR_FAMILY_NAME
                       + "||', '||" + tableAlias + '.' + KEY_AUTHOR_GIVEN_NAMES
                       + _END;
            }
        }

        /**
         * If the field has a time part, convert it to local time.
         * This deals with legacy 'date-only' dates.
         * The logic being that IF they had a time part it would be UTC.
         * Without a time part, we assume the zone is local (or irrelevant).
         *
         * @param fieldSpec fully qualified field name
         *
         * @return expression
         */
        @NonNull
        private static String localDateTimeExpression(@NonNull final String fieldSpec) {
            return CASE
                   + _WHEN_ + fieldSpec + " GLOB '*-*-* *' "
                   + " THEN datetime(" + fieldSpec + ", 'localtime')"
                   + _ELSE_ + fieldSpec
                   + _END;
        }

        /**
         * General remark on the use of GLOB instead of 'strftime(format, date)':
         * strftime() only works on full date(time) strings. i.e. 'YYYY-MM-DD*'
         * for all other formats, it will fail to extract the fields.
         *
         * Create a GLOB expression to get the 'year' from a text date field in a standard way.
         * <p>
         * Just look for 4 leading numbers. We don't care about anything else.
         * <p>
         * See <a href="https://www.sqlitetutorial.net/sqlite-glob/">sqlite-glob</a>
         *
         * @param fieldSpec fully qualified field name
         * @param toLocal   if set, first convert the fieldSpec to local time from UTC
         *
         * @return expression
         */
        @NonNull
        public static String year(@NonNull String fieldSpec,
                                  final boolean toLocal) {

            //TODO: This covers a timezone offset for Dec-31 / Jan-01 only - how important is this?
            if (toLocal) {
                fieldSpec = localDateTimeExpression(fieldSpec);
            }
            return CASE
                   // YYYY
                   + _WHEN_ + fieldSpec + " GLOB '[0-9][0-9][0-9][0-9]*'"
                   + " THEN SUBSTR(" + fieldSpec + ",1,4)"
                   // invalid
                   + " ELSE ''"
                   + _END;
        }

        /**
         * Create a GLOB expression to get the 'month' from a text date field in a standard way.
         * <p>
         * Just look for 4 leading numbers followed by '-' and by 2 or 1 digit.
         * We don't care about anything else.
         *
         * @param fieldSpec fully qualified field name
         * @param toLocal   if set, first convert the fieldSpec to local time from UTC
         *
         * @return expression
         */
        @NonNull
        public static String month(@NonNull String fieldSpec,
                                   final boolean toLocal) {
            if (toLocal) {
                fieldSpec = localDateTimeExpression(fieldSpec);
            }
            return CASE
                   // YYYY-MM
                   + _WHEN_ + fieldSpec + " GLOB '[0-9][0-9][0-9][0-9]-[0-9][0-9]*'"
                   + " THEN SUBSTR(" + fieldSpec + ",6,2)"
                   // YYYY-M
                   + _WHEN_ + fieldSpec + " GLOB '[0-9][0-9][0-9][0-9]-[0-9]*'"
                   + " THEN SUBSTR(" + fieldSpec + ",6,1)"
                   // invalid
                   + " ELSE ''"
                   + _END;
        }

        /**
         * Create a GLOB expression to get the 'day' from a text date field in a standard way.
         * <p>
         * Just look for 4 leading numbers followed by '-' and by 2 or 1 digit,
         * and then by '-' and 1 or two digits.
         * We don't care about anything else.
         *
         * @param fieldSpec fully qualified field name
         * @param toLocal   if set, first convert the fieldSpec to local time from UTC
         *
         * @return expression
         */
        @NonNull
        public static String day(@NonNull String fieldSpec,
                                 final boolean toLocal) {
            if (toLocal) {
                fieldSpec = localDateTimeExpression(fieldSpec);
            }
            // Just look for 4 leading numbers followed by 2 or 1 digit then another 2 or 1 digit.
            // We don't care about anything else.
            return CASE
                   // YYYY-MM-DD
                   + _WHEN_ + fieldSpec + " GLOB '[0-9][0-9][0-9][0-9]-[0-9][0-9]-[0-9][0-9]*'"
                   + " THEN SUBSTR(" + fieldSpec + ",9,2)"
                   // YYYY-M-DD
                   + _WHEN_ + fieldSpec + " GLOB '[0-9][0-9][0-9][0-9]-[0-9]-[0-9][0-9]*'"
                   + " THEN SUBSTR(" + fieldSpec + ",8,2)"
                   // YYYY-MM-D
                   + _WHEN_ + fieldSpec + " GLOB '[0-9][0-9][0-9][0-9]-[0-9][0-9]-[0-9]*'"
                   + " THEN SUBSTR(" + fieldSpec + ",9,1)"
                   // YYYY-M-D
                   + _WHEN_ + fieldSpec + " GLOB '[0-9][0-9][0-9][0-9]-[0-9]-[0-9]*'"
                   + " THEN SUBSTR(" + fieldSpec + ",8,1)"
                   // invalid
                   + " ELSE ''"
                   + _END;
        }
    }

    /**
     * Count/exist statements.
     */
    static final class SqlCount {

        /**
         * Count all {@link Book}'s.
         */
        static final String BOOKS =
                "SELECT COUNT(*) FROM " + TBL_BOOKS.getName();

        /**
         * Count the number of {@link Book}'s in a {@link Series}.
         */
        static final String BOOKS_IN_SERIES =
                "SELECT COUNT(" + KEY_FK_BOOK + ") FROM " + TBL_BOOK_SERIES.getName()
                + _WHERE_ + KEY_FK_SERIES + "=?";

        /**
         * Count the number of {@link Book}'s by an {@link Author}.
         */
        static final String BOOKS_BY_AUTHOR =
                "SELECT COUNT(" + KEY_FK_BOOK + ") FROM " + TBL_BOOK_AUTHOR.getName()
                + _WHERE_ + KEY_FK_AUTHOR + "=?";

        /**
         * Count the number of {@link Book}'s by an {@link Publisher}.
         */
        static final String BOOKS_BY_PUBLISHER =
                "SELECT COUNT(" + KEY_FK_BOOK + ") FROM " + TBL_BOOK_PUBLISHER.getName()
                + _WHERE_ + KEY_FK_PUBLISHER + "=?";

        /**
         * Count the number of {@link TocEntry}'s by an {@link Author}.
         */
        static final String TOC_ENTRIES_BY_AUTHOR =
                "SELECT COUNT(" + KEY_PK_ID + ") FROM " + TBL_TOC_ENTRIES.getName()
                + _WHERE_ + KEY_FK_AUTHOR + "=?";

        /**
         * Check if a {@link Book} exists.
         */
        static final String BOOK_EXISTS =
                "SELECT COUNT(*) " + _FROM_ + TBL_BOOKS.getName()
                + _WHERE_ + KEY_PK_ID + "=?";
    }

    /**
     * Sql SELECT to lookup a single item.
     */
    static final class SqlGet {

        /**
         * Get an {@link Author} by the Author id.
         */
        static final String AUTHOR =
                SqlSelectFullTable.AUTHORS + _WHERE_ + KEY_PK_ID + "=?";

        /**
         * Get a {@link Series} by the Series id.
         */
        static final String SERIES =
                SqlSelectFullTable.SERIES + _WHERE_ + KEY_PK_ID + "=?";

        /**
         * Get a {@link Publisher} by the Publisher id.
         */
        static final String PUBLISHER =
                SqlSelectFullTable.PUBLISHERS + _WHERE_ + KEY_PK_ID + "=?";

        /**
         * Get a {@link Bookshelf} by the Bookshelf id.
         */
        static final String BOOKSHELF =
                SqlSelectFullTable.BOOKSHELVES + _WHERE_ + TBL_BOOKSHELF.dot(KEY_PK_ID) + "=?";

        /**
         * Get the UUID of a {@link Book} by the Book id.
         */
        static final String BOOK_UUID_BY_ID =
                SELECT_ + KEY_BOOK_UUID + _FROM_ + TBL_BOOKS.getName()
                + _WHERE_ + KEY_PK_ID + "=?";

        /**
         * Get the ISBN of a {@link Book} by the Book id.
         */
        static final String BOOK_ISBN_BY_BOOK_ID =
                SELECT_ + KEY_ISBN + _FROM_ + TBL_BOOKS.getName()
                + _WHERE_ + KEY_PK_ID + "=?";

        /**
         * Get the title of a {@link Book} by the Book id.
         */
        static final String BOOK_TITLE_BY_BOOK_ID =
                SELECT_ + KEY_TITLE + _FROM_ + TBL_BOOKS.getName()
                + _WHERE_ + KEY_PK_ID + "=?";

        /**
         * Get the name of the loanee of a {@link Book} by the Book id.
         */
        static final String LOANEE_BY_BOOK_ID =
                SELECT_ + KEY_LOANEE + _FROM_ + TBL_BOOK_LOANEE.getName()
                + _WHERE_ + KEY_FK_BOOK + "=?";
        /**
         * Get a {@link Bookshelf} by its name.
         */
        static final String BOOKSHELF_BY_NAME =
                SqlSelectFullTable.BOOKSHELVES
                + _WHERE_ + TBL_BOOKSHELF.dot(KEY_BOOKSHELF_NAME) + "=?" + _COLLATION;
        /**
         * Get the language (ISO3) code for a Series.
         * This is defined as the language code for the first book in the Series.
         */
        static final String SERIES_LANGUAGE =
                SELECT_ + TBL_BOOKS.dotAs(KEY_LANGUAGE)
                + _FROM_ + TBL_BOOK_SERIES.ref() + TBL_BOOK_SERIES.join(TBL_BOOKS)
                + _WHERE_ + TBL_BOOK_SERIES.dot(KEY_FK_SERIES) + "=?"
                + _ORDER_BY_ + TBL_BOOK_SERIES.dot(KEY_BOOK_NUM_IN_SERIES)
                + " LIMIT 1";
        /**
         * Get the last-update-date for a {@link Book} by its id.
         */
        static final String LAST_UPDATE_DATE_BY_BOOK_ID =
                SELECT_ + KEY_UTC_LAST_UPDATED + _FROM_ + TBL_BOOKS.getName()
                + _WHERE_ + KEY_PK_ID + "=?";
    }

    /**
     * Sql SELECT to lookup an ID.
     */
    static final class SqlGetId {

        /**
         * Get the id of a {@link Book} by UUID.
         */
        static final String BY_UUID =
                SELECT_ + KEY_PK_ID + _FROM_ + TBL_BOOKS.getName()
                + _WHERE_ + KEY_BOOK_UUID + "=?";

        /**
         * Get the id of a {@link BooklistStyle} by UUID.
         */
        static final String BOOKLIST_STYLE_ID_BY_UUID =
                SELECT_ + KEY_PK_ID + _FROM_ + TBL_BOOKLIST_STYLES.getName()
                + _WHERE_ + KEY_UUID + "=?";

        /**
         * Get the id of a {@link Bookshelf} by name.
         * The lookup is by EQUALITY and CASE-SENSITIVE.
         */
        static final String BOOKSHELF_ID_BY_NAME =
                SELECT_ + KEY_PK_ID + _FROM_ + TBL_BOOKSHELF.getName()
                + _WHERE_ + KEY_BOOKSHELF_NAME + "=?" + _COLLATION;

        /**
         * Get the id of a {@link Author} by name.
         * The lookup is by EQUALITY and CASE-SENSITIVE.
         * Can return more than one row if the KEY_AUTHOR_GIVEN_NAMES_OB is empty.
         */
        static final String AUTHOR_ID_BY_NAME =
                SELECT_ + KEY_PK_ID + _FROM_ + TBL_AUTHORS.getName()
                + _WHERE_ + KEY_AUTHOR_FAMILY_NAME_OB + "=?" + _COLLATION
                + " AND " + KEY_AUTHOR_GIVEN_NAMES_OB + "=?" + _COLLATION;

        /**
         * Get the id of a {@link Series} by Title.
         * The lookup is by EQUALITY and CASE-SENSITIVE.
         * Searches KEY_SERIES_TITLE_OB on both "The Title" and "Title, The"
         */
        static final String SERIES_ID_BY_NAME =
                SELECT_ + KEY_PK_ID + _FROM_ + TBL_SERIES.getName()
                + _WHERE_ + KEY_SERIES_TITLE_OB + "=?" + _COLLATION
                + " OR " + KEY_SERIES_TITLE_OB + "=?" + _COLLATION;

        /**
         * Get the id of a {@link Publisher} by name.
         * The lookup is by EQUALITY and CASE-SENSITIVE.
         */
        static final String PUBLISHER_ID_BY_NAME =
                SELECT_ + KEY_PK_ID + _FROM_ + TBL_PUBLISHERS.getName()
                + _WHERE_ + KEY_PUBLISHER_NAME_OB + "=?" + _COLLATION
                + " OR " + KEY_PUBLISHER_NAME_OB + "=?" + _COLLATION;

        /**
         * Get the id of a {@link TocEntry} by Title.
         * The lookup is by EQUALITY and CASE-SENSITIVE.
         * Search KEY_TITLE_OB on both "The Title" and "Title, The"
         */
        static final String TOC_ENTRY_ID_BY_TITLE_AND_AUTHOR =
                SELECT_ + KEY_PK_ID + _FROM_ + TBL_TOC_ENTRIES.getName()
                + _WHERE_ + KEY_FK_AUTHOR + "=?"
                + " AND (" + KEY_TITLE_OB + "=? " + _COLLATION
                + " OR " + KEY_TITLE_OB + "=?" + _COLLATION + ')';
    }

    /**
     * Return a single text column for use with {@link AutoCompleteTextView}.
     */
    static final class SqlAutoCompleteText {

        /** name only. */
        static final String LOANEE =
                SELECT_DISTINCT_ + KEY_LOANEE
                + _FROM_ + TBL_BOOK_LOANEE.getName()
                + _WHERE_ + KEY_LOANEE + "<> ''"
                + _ORDER_BY_ + KEY_LOANEE + _COLLATION;

        /** name only. */
        static final String AUTHORS_FAMILY_NAMES =
                SELECT_DISTINCT_ + KEY_AUTHOR_FAMILY_NAME + ',' + KEY_AUTHOR_FAMILY_NAME_OB
                + _FROM_ + TBL_AUTHORS.getName()
                + _ORDER_BY_ + KEY_AUTHOR_FAMILY_NAME_OB + _COLLATION;

        /** name only. */
        static final String AUTHORS_GIVEN_NAMES =
                SELECT_DISTINCT_ + KEY_AUTHOR_GIVEN_NAMES + ',' + KEY_AUTHOR_GIVEN_NAMES_OB
                + _FROM_ + TBL_AUTHORS.getName()
                + _WHERE_ + KEY_AUTHOR_GIVEN_NAMES_OB + "<> ''"
                + _ORDER_BY_ + KEY_AUTHOR_GIVEN_NAMES_OB + _COLLATION;

        /** name only. */
        static final String AUTHORS_FORMATTED_NAMES =
                SELECT_
                + SqlColumns.getDisplayAuthor(TBL_AUTHORS.getAlias(), false)
                + _AS_ + KEY_AUTHOR_FORMATTED
                + ',' + KEY_AUTHOR_FAMILY_NAME_OB
                + ',' + KEY_AUTHOR_GIVEN_NAMES_OB
                + _FROM_ + TBL_AUTHORS.ref()
                + _ORDER_BY_ + KEY_AUTHOR_FAMILY_NAME_OB + _COLLATION
                + ',' + KEY_AUTHOR_GIVEN_NAMES_OB + _COLLATION;

        /** name only. */
        static final String AUTHORS_FORMATTED_NAMES_GIVEN_FIRST =
                SELECT_ + SqlColumns.getDisplayAuthor(TBL_AUTHORS.getAlias(), true)
                + _AS_ + KEY_AUTHOR_FORMATTED_GIVEN_FIRST
                + ',' + KEY_AUTHOR_FAMILY_NAME_OB
                + ',' + KEY_AUTHOR_GIVEN_NAMES_OB
                + _FROM_ + TBL_AUTHORS.ref()
                + _ORDER_BY_ + KEY_AUTHOR_FAMILY_NAME_OB + _COLLATION
                + ',' + KEY_AUTHOR_GIVEN_NAMES_OB + _COLLATION;

        /** name only. */
        static final String SERIES_NAME =
                SELECT_ + KEY_SERIES_TITLE
                + ',' + KEY_SERIES_TITLE_OB
                + _FROM_ + TBL_SERIES.getName()
                + _ORDER_BY_ + KEY_SERIES_TITLE_OB + _COLLATION;

        /** name only. */
        static final String PUBLISHERS_NAME =
                SELECT_DISTINCT_ + KEY_PUBLISHER_NAME
                + ',' + KEY_PUBLISHER_NAME_OB
                + _FROM_ + TBL_PUBLISHERS.getName()
                + _ORDER_BY_ + KEY_PUBLISHER_NAME_OB + _COLLATION;

        /** name only. */
        static final String FORMATS =
                SELECT_DISTINCT_ + KEY_FORMAT
                + _FROM_ + TBL_BOOKS.getName()
                + _WHERE_ + KEY_FORMAT + "<> ''"
                + _ORDER_BY_ + KEY_FORMAT + _COLLATION;

        /** name only. */
        static final String COLORS =
                SELECT_DISTINCT_ + KEY_COLOR
                + _FROM_ + TBL_BOOKS.getName()
                + _WHERE_ + KEY_COLOR + "<> ''"
                + _ORDER_BY_ + KEY_COLOR + _COLLATION;

        /** name only. */
        static final String GENRES =
                SELECT_DISTINCT_ + KEY_GENRE
                + _FROM_ + TBL_BOOKS.getName()
                + _WHERE_ + KEY_GENRE + "<> ''"
                + _ORDER_BY_ + KEY_GENRE + _COLLATION;

        /** name only. */
        static final String LANGUAGES =
                SELECT_DISTINCT_ + KEY_LANGUAGE
                + _FROM_ + TBL_BOOKS.getName()
                + _WHERE_ + KEY_LANGUAGE + "<> ''"
                + _ORDER_BY_ + KEY_UTC_LAST_UPDATED + _COLLATION;

        /** name only. */
        static final String LOCATIONS =
                SELECT_DISTINCT_ + KEY_LOCATION
                + _FROM_ + TBL_BOOKS.getName()
                + _WHERE_ + KEY_LOCATION + "<> ''"
                + _ORDER_BY_ + KEY_LOCATION + _COLLATION;
    }

    /**
     * Sql SELECT of a single table, without a WHERE clause.
     */
    static final class SqlSelectFullTable {

        /** {@link Author}, all columns. */
        static final String AUTHORS = "SELECT * FROM " + TBL_AUTHORS.getName();

        /** {@link Series}, all columns. */
        static final String SERIES = "SELECT * FROM " + TBL_SERIES.getName();

        /** {@link Publisher}, all columns. */
        static final String PUBLISHERS = "SELECT * FROM " + TBL_PUBLISHERS.getName();

        /** {@link TocEntry}, all columns. */
        static final String TOC_ENTRIES = "SELECT * FROM " + TBL_TOC_ENTRIES.getName();

        /** {@link BooklistStyle} all columns. */
        static final String BOOKLIST_STYLES =
                "SELECT * FROM " + TBL_BOOKLIST_STYLES.getName();

        /** {@link Bookshelf} all columns; linked with the styles table. */
        static final String BOOKSHELVES =
                SELECT_ + TBL_BOOKSHELF.dotAs(KEY_PK_ID)
                + ',' + TBL_BOOKSHELF.dotAs(KEY_BOOKSHELF_NAME)
                + ',' + TBL_BOOKSHELF.dotAs(KEY_BOOKSHELF_BL_TOP_POS)
                + ',' + TBL_BOOKSHELF.dotAs(KEY_BOOKSHELF_BL_TOP_OFFSET)
                + ',' + TBL_BOOKSHELF.dotAs(KEY_FK_STYLE)
                + ',' + TBL_BOOKLIST_STYLES.dotAs(KEY_UUID)
                + _FROM_ + TBL_BOOKSHELF.ref() + TBL_BOOKSHELF.join(TBL_BOOKLIST_STYLES);

        /** User defined {@link Bookshelf} all columns; linked with the styles table. */
        static final String BOOKSHELVES_USER_SHELVES =
                SqlSelectFullTable.BOOKSHELVES
                + _WHERE_ + TBL_BOOKSHELF.dot(KEY_PK_ID) + ">0"
                + _ORDER_BY_ + KEY_BOOKSHELF_NAME + _COLLATION;

        /** Book UUID only, for accessing all cover image files. */
        static final String ALL_BOOK_UUID =
                SELECT_ + KEY_BOOK_UUID + _FROM_ + TBL_BOOKS.getName();

        /**
         * All Book titles for a rebuild of the {@link DBDefinitions#KEY_TITLE_OB} column.
         */
        static final String BOOK_TITLES =
                // The index of KEY_PK_ID, KEY_TITLE, KEY_TITLE_OB is hardcoded - don't change!
                SELECT_ + KEY_PK_ID + ',' + KEY_TITLE + ',' + KEY_TITLE_OB
                + ',' + KEY_LANGUAGE
                + _FROM_ + TBL_BOOKS.getName();

        /**
         * All Series for a rebuild of the {@link DBDefinitions#KEY_SERIES_TITLE_OB} column.
         */
        static final String SERIES_TITLES =
                // The index of KEY_PK_ID, KEY_SERIES_TITLE, KEY_SERIES_TITLE_OB is hardcoded
                // Don't change!
                SELECT_ + KEY_PK_ID + ',' + KEY_SERIES_TITLE + ',' + KEY_SERIES_TITLE_OB
                + _FROM_ + TBL_SERIES.getName();

        /**
         * All Publishers for a rebuild of the {@link DBDefinitions#KEY_PUBLISHER_NAME_OB} column.
         */
        static final String PUBLISHER_NAMES =
                // The index of KEY_PK_ID, KEY_PUBLISHER_NAME, KEY_PUBLISHER_NAME_OB is hardcoded
                // Don't change!
                SELECT_ + KEY_PK_ID + ',' + KEY_PUBLISHER_NAME + ',' + KEY_PUBLISHER_NAME_OB
                + _FROM_ + TBL_PUBLISHERS.getName();

        /**
         * All Series for a rebuild of the {@link DBDefinitions#KEY_TITLE_OB} column.
         */
        static final String TOC_ENTRY_TITLES =
                // The index of KEY_PK_ID, KEY_TITLE, KEY_TITLE_OB is hardcoded - don't change!
                SELECT_ + KEY_PK_ID + ',' + KEY_TITLE + ',' + KEY_TITLE_OB
                + _FROM_ + TBL_TOC_ENTRIES.getName();

        /**
         * The SELECT and FROM clause for getting a book (list).
         * <p>
         */
        static final String SQL_BOOK;

        static {
            // Developer: adding fields ? Now is a good time to update {@link Book#duplicate}/
            // Note we could use TBL_BOOKS.dot("*")
            // We'ld fetch the unneeded TITLE_OB field, but that would be ok.
            // Nevertheless, listing the fields here gives a better understanding
            final StringBuilder sqlBookTmp = new StringBuilder(
                    SELECT_ + TBL_BOOKS.dotAs(KEY_PK_ID)
                    + ',' + TBL_BOOKS.dotAs(KEY_BOOK_UUID)
                    + ',' + TBL_BOOKS.dotAs(KEY_TITLE)
                    // publication data
                    + ',' + TBL_BOOKS.dotAs(KEY_ISBN)
                    + ',' + TBL_BOOKS.dotAs(KEY_TOC_BITMASK)
                    + ',' + TBL_BOOKS.dotAs(KEY_DATE_PUBLISHED)
                    + ',' + TBL_BOOKS.dotAs(KEY_PRINT_RUN)
                    + ',' + TBL_BOOKS.dotAs(KEY_PRICE_LISTED)
                    + ',' + TBL_BOOKS.dotAs(KEY_PRICE_LISTED_CURRENCY)
                    + ',' + TBL_BOOKS.dotAs(KEY_DATE_FIRST_PUBLICATION)
                    + ',' + TBL_BOOKS.dotAs(KEY_FORMAT)
                    + ',' + TBL_BOOKS.dotAs(KEY_COLOR)
                    + ',' + TBL_BOOKS.dotAs(KEY_GENRE)
                    + ',' + TBL_BOOKS.dotAs(KEY_LANGUAGE)
                    + ',' + TBL_BOOKS.dotAs(KEY_PAGES)
                    // Main/public description about the content/publication
                    + ',' + TBL_BOOKS.dotAs(KEY_DESCRIPTION)

                    // partially edition info, partially user-owned info.
                    + ',' + TBL_BOOKS.dotAs(KEY_EDITION_BITMASK)
                    // user notes
                    + ',' + TBL_BOOKS.dotAs(KEY_PRIVATE_NOTES)
                    + ',' + TBL_BOOKS.dotAs(KEY_BOOK_CONDITION)
                    + ',' + TBL_BOOKS.dotAs(KEY_BOOK_CONDITION_COVER)
                    + ',' + TBL_BOOKS.dotAs(KEY_LOCATION)
                    + ',' + TBL_BOOKS.dotAs(KEY_SIGNED)
                    + ',' + TBL_BOOKS.dotAs(KEY_RATING)
                    + ',' + TBL_BOOKS.dotAs(KEY_READ)
                    + ',' + TBL_BOOKS.dotAs(KEY_READ_START)
                    + ',' + TBL_BOOKS.dotAs(KEY_READ_END)
                    + ',' + TBL_BOOKS.dotAs(KEY_DATE_ACQUIRED)
                    + ',' + TBL_BOOKS.dotAs(KEY_PRICE_PAID)
                    + ',' + TBL_BOOKS.dotAs(KEY_PRICE_PAID_CURRENCY)
                    // added/updated
                    + ',' + TBL_BOOKS.dotAs(KEY_UTC_ADDED)
                    + ',' + TBL_BOOKS.dotAs(KEY_UTC_LAST_UPDATED));

            for (Domain domain : SearchEngineRegistry.getExternalIdDomains()) {
                sqlBookTmp.append(',').append(TBL_BOOKS.dotAs(domain.getName()));
            }

            //NEWTHINGS: adding a new search engine: optional: add engine specific keys
            sqlBookTmp.append(',').append(TBL_BOOKS.dotAs(KEY_UTC_GOODREADS_LAST_SYNC_DATE));

            sqlBookTmp.append(',').append(TBL_BOOKS.dotAs(KEY_EID_CALIBRE_UUID));

            SQL_BOOK = sqlBookTmp.toString()
                       // COALESCE nulls to "" for the LEFT OUTER JOIN'ed tables
                       + ',' + "COALESCE(" + TBL_BOOK_LOANEE.dot(KEY_LOANEE) + ", '')" + _AS_
                       + KEY_LOANEE

                       + _FROM_ + TBL_BOOKS.ref() + TBL_BOOKS.leftOuterJoin(TBL_BOOK_LOANEE);
        }
    }

    /**
     * Sql SELECT returning a list, with a WHERE clause.
     */
    static final class SqlSelect {

        static final String BOOK_ID_LIST_BY_TOC_ENTRY_ID =
                SELECT_ + KEY_FK_BOOK + _FROM_ + TBL_BOOK_TOC_ENTRIES.getName()
                + _WHERE_ + KEY_FK_TOC_ENTRY + "=?";
        /**
         * Find the {@link Book} id based on a search for the ISBN (both 10 & 13).
         */
        static final String BY_VALID_ISBN =
                SELECT_ + KEY_PK_ID + _FROM_ + TBL_BOOKS.getName()
                + _WHERE_ + KEY_ISBN + " LIKE ? OR " + KEY_ISBN + " LIKE ?";
        /**
         * Find the {@link Book} id based on a search for the ISBN.
         * The isbn need not be valid and can in fact be any code whatsoever.
         */
        static final String BY_ISBN =
                SELECT_ + KEY_PK_ID + _FROM_ + TBL_BOOKS.getName()
                + _WHERE_ + KEY_ISBN + " LIKE ?";

        static final String BOOK_TITLES_FOR_TOC =
                SELECT_ + TBL_BOOKS.dot(KEY_PK_ID) + ',' + TBL_BOOKS.dot(KEY_TITLE)
                + _FROM_ + TBL_BOOK_TOC_ENTRIES.ref() + TBL_BOOK_TOC_ENTRIES.join(TBL_BOOKS)
                + _WHERE_ + TBL_BOOK_TOC_ENTRIES.dot(KEY_FK_TOC_ENTRY) + "=?"
                + _ORDER_BY_ + TBL_BOOKS.dot(KEY_TITLE_OB);

        /**
         * All Bookshelves for a Book; ordered by name.
         */
        static final String BOOKSHELVES_BY_BOOK_ID =
                SELECT_DISTINCT_
                + TBL_BOOKSHELF.dotAs(KEY_PK_ID)
                + ',' + TBL_BOOKSHELF.dotAs(KEY_BOOKSHELF_NAME)
                + ',' + TBL_BOOKSHELF.dotAs(KEY_BOOKSHELF_BL_TOP_POS)
                + ',' + TBL_BOOKSHELF.dotAs(KEY_BOOKSHELF_BL_TOP_OFFSET)
                + ',' + TBL_BOOKSHELF.dotAs(KEY_FK_STYLE)
                + ',' + TBL_BOOKLIST_STYLES.dotAs(KEY_UUID)

                + _FROM_ + TBL_BOOK_BOOKSHELF.ref()
                + TBL_BOOK_BOOKSHELF.join(TBL_BOOKSHELF)
                + TBL_BOOKSHELF.join(TBL_BOOKLIST_STYLES)
                + _WHERE_ + TBL_BOOK_BOOKSHELF.dot(KEY_FK_BOOK) + "=?"
                + _ORDER_BY_ + TBL_BOOKSHELF.dot(KEY_BOOKSHELF_NAME) + _COLLATION;

        /**
         * All Authors for a Book; ordered by position, family, given.
         */
        static final String AUTHORS_BY_BOOK_ID =
                SELECT_DISTINCT_ + TBL_AUTHORS.dotAs(KEY_PK_ID)
                + ',' + TBL_AUTHORS.dotAs(KEY_AUTHOR_FAMILY_NAME)
                + ',' + TBL_AUTHORS.dotAs(KEY_AUTHOR_FAMILY_NAME_OB)
                + ',' + TBL_AUTHORS.dotAs(KEY_AUTHOR_GIVEN_NAMES)
                + ',' + TBL_AUTHORS.dotAs(KEY_AUTHOR_GIVEN_NAMES_OB)
                + ',' + TBL_AUTHORS.dotAs(KEY_AUTHOR_IS_COMPLETE)
                + ',' + SqlColumns.getDisplayAuthor(TBL_AUTHORS.getAlias(), false)
                + _AS_ + KEY_AUTHOR_FORMATTED
                + ',' + TBL_BOOK_AUTHOR.dotAs(KEY_BOOK_AUTHOR_POSITION)
                + ',' + TBL_BOOK_AUTHOR.dotAs(KEY_BOOK_AUTHOR_TYPE_BITMASK)

                + _FROM_ + TBL_BOOK_AUTHOR.ref() + TBL_BOOK_AUTHOR.join(TBL_AUTHORS)
                + _WHERE_ + TBL_BOOK_AUTHOR.dot(KEY_FK_BOOK) + "=?"
                + _ORDER_BY_
                + TBL_BOOK_AUTHOR.dot(KEY_BOOK_AUTHOR_POSITION)
                + ',' + KEY_AUTHOR_FAMILY_NAME_OB + _COLLATION
                + ',' + KEY_AUTHOR_GIVEN_NAMES_OB + _COLLATION;

        /**
         * All Series for a Book; ordered by position, name.
         */
        static final String SERIES_BY_BOOK_ID =
                SELECT_DISTINCT_ + TBL_SERIES.dotAs(KEY_PK_ID)
                + ',' + TBL_SERIES.dotAs(KEY_SERIES_TITLE)
                + ',' + TBL_SERIES.dotAs(KEY_SERIES_TITLE_OB)
                + ',' + TBL_SERIES.dotAs(KEY_SERIES_IS_COMPLETE)
                + ',' + TBL_BOOK_SERIES.dotAs(KEY_BOOK_NUM_IN_SERIES)
                + ',' + TBL_BOOK_SERIES.dotAs(KEY_BOOK_SERIES_POSITION)

                + _FROM_ + TBL_BOOK_SERIES.ref() + TBL_BOOK_SERIES.join(TBL_SERIES)
                + _WHERE_ + TBL_BOOK_SERIES.dot(KEY_FK_BOOK) + "=?"
                + _ORDER_BY_ + TBL_BOOK_SERIES.dot(KEY_BOOK_SERIES_POSITION)
                + ',' + TBL_SERIES.dot(KEY_SERIES_TITLE_OB) + _COLLATION;

        /**
         * All Publishers for a Book; ordered by position, name.
         */
        static final String PUBLISHER_BY_BOOK_ID =
                SELECT_DISTINCT_ + TBL_PUBLISHERS.dotAs(KEY_PK_ID)
                + ',' + TBL_PUBLISHERS.dotAs(KEY_PUBLISHER_NAME)
                + ',' + TBL_PUBLISHERS.dotAs(KEY_PUBLISHER_NAME_OB)
                + ',' + TBL_BOOK_PUBLISHER.dotAs(KEY_BOOK_PUBLISHER_POSITION)

                + _FROM_ + TBL_BOOK_PUBLISHER.ref() + TBL_BOOK_PUBLISHER.join(TBL_PUBLISHERS)
                + _WHERE_ + TBL_BOOK_PUBLISHER.dot(KEY_FK_BOOK) + "=?"
                + _ORDER_BY_ + TBL_BOOK_PUBLISHER.dot(KEY_BOOK_PUBLISHER_POSITION)
                + ',' + TBL_PUBLISHERS.dot(KEY_PUBLISHER_NAME_OB) + _COLLATION;

        /**
         * All TocEntry's for a Book; ordered by position in the book.
         */
        static final String TOC_ENTRIES_BY_BOOK_ID =
                SELECT_ + TBL_TOC_ENTRIES.dotAs(KEY_PK_ID)
                + ',' + TBL_TOC_ENTRIES.dotAs(KEY_FK_AUTHOR)
                + ',' + TBL_TOC_ENTRIES.dotAs(KEY_TITLE)
                + ',' + TBL_TOC_ENTRIES.dotAs(KEY_DATE_FIRST_PUBLICATION)
                // for convenience, we fetch the Author here
                + ',' + TBL_AUTHORS.dotAs(KEY_AUTHOR_FAMILY_NAME)
                + ',' + TBL_AUTHORS.dotAs(KEY_AUTHOR_GIVEN_NAMES)
                + ',' + TBL_AUTHORS.dotAs(KEY_AUTHOR_IS_COMPLETE)

                // count the number of books this TOC entry is present in.
                + ',' + "(SELECT COUNT(*) FROM " + TBL_BOOK_TOC_ENTRIES.getName()
                // use the full table name on the left as we need a full table scan
                + _WHERE_ + TBL_BOOK_TOC_ENTRIES.getName() + '.' + KEY_FK_TOC_ENTRY
                // but filtered on the results from the main query (i.e. alias on the right).
                + "=" + TBL_TOC_ENTRIES.dot(KEY_PK_ID) + ") AS " + KEY_BOOK_COUNT

                + _FROM_ + TBL_TOC_ENTRIES.ref()
                + TBL_TOC_ENTRIES.join(TBL_BOOK_TOC_ENTRIES)
                + TBL_TOC_ENTRIES.join(TBL_AUTHORS)
                + _WHERE_ + TBL_BOOK_TOC_ENTRIES.dot(KEY_FK_BOOK) + "=?"
                + _ORDER_BY_ + TBL_BOOK_TOC_ENTRIES.dot(KEY_BOOK_TOC_ENTRY_POSITION);

        /**
         * All Books (id only!) for a given Author.
         */
        static final String BOOK_IDS_BY_AUTHOR_ID =
                SELECT_ + TBL_BOOKS.dotAs(KEY_PK_ID)
                + _FROM_ + TBL_BOOKS.ref() + TBL_BOOKS.join(TBL_BOOK_AUTHOR)
                + _WHERE_ + TBL_BOOK_AUTHOR.dot(KEY_FK_AUTHOR) + "=?";

        /**
         * All Books (id only!) for a given Series.
         */
        static final String BOOK_IDS_BY_SERIES_ID =
                SELECT_ + TBL_BOOKS.dotAs(KEY_PK_ID)
                + _FROM_ + TBL_BOOKS.ref() + TBL_BOOKS.join(TBL_BOOK_SERIES)
                + _WHERE_ + TBL_BOOK_SERIES.dot(KEY_FK_SERIES) + "=?";

        /**
         * All Books (id only!) for a given Publisher.
         */
        static final String BOOK_IDS_BY_PUBLISHER_ID =
                SELECT_ + TBL_BOOKS.dotAs(KEY_PK_ID)
                + _FROM_ + TBL_BOOKS.ref() + TBL_BOOKS.join(TBL_BOOK_PUBLISHER)
                + _WHERE_ + TBL_BOOK_PUBLISHER.dot(KEY_FK_PUBLISHER) + "=?";

        /**
         * All TocEntry's for an Author.
         * <p>
         * ORDER BY clause NOT added here, as this statement is used in a union as well.
         * <p>
         * We need KEY_TITLE_OB as it will be used to ORDER BY
         */
        static final String TOC_ENTRIES_BY_AUTHOR_ID =
                SELECT_ + "'" + AuthorWork.TYPE_TOC + "' AS " + KEY_TOC_TYPE
                + ',' + TBL_TOC_ENTRIES.dotAs(KEY_PK_ID)
                + ',' + TBL_TOC_ENTRIES.dotAs(KEY_TITLE)
                + ',' + TBL_TOC_ENTRIES.dotAs(KEY_TITLE_OB)
                + ',' + TBL_TOC_ENTRIES.dotAs(KEY_DATE_FIRST_PUBLICATION)
                // count the number of books this TOC entry is present in.
                + ", COUNT(" + TBL_TOC_ENTRIES.dot(KEY_PK_ID) + ") AS " + KEY_BOOK_COUNT;

        /**
         * All Book titles and their first pub. date, for an Author..
         * <p>
         * ORDER BY clause NOT added here, as this statement is used in a union as well.
         * <p>
         * We need KEY_TITLE_OB as it will be used to ORDER BY
         */
        static final String BOOK_TITLES_BY_AUTHOR_ID =
                SELECT_ + "'" + AuthorWork.TYPE_BOOK + "' AS " + KEY_TOC_TYPE
                + ',' + TBL_BOOKS.dotAs(KEY_PK_ID)
                + ',' + TBL_BOOKS.dotAs(KEY_TITLE)
                + ',' + TBL_BOOKS.dotAs(KEY_TITLE_OB)
                + ',' + TBL_BOOKS.dotAs(KEY_DATE_FIRST_PUBLICATION)
                + ",1 AS " + KEY_BOOK_COUNT;

        /**
         * A subset of book columns, to be used for searches on Goodreads.
         */
        static final String BOOK_COLUMNS_FOR_GOODREADS_SEARCH =
                SELECT_ + TBL_BOOKS.dotAs(KEY_PK_ID)
                + ',' + TBL_BOOKS.dotAs(KEY_TITLE)
                + ',' + TBL_BOOKS.dotAs(KEY_ISBN)
                + ',' + SqlColumns.getDisplayAuthor(TBL_AUTHORS.getAlias(), true)
                + _AS_ + KEY_AUTHOR_FORMATTED_GIVEN_FIRST

                + _FROM_ + TBL_BOOKS.ref()
                + TBL_BOOKS.join(TBL_BOOK_AUTHOR) + TBL_BOOK_AUTHOR.join(TBL_AUTHORS)
                + _WHERE_ + TBL_BOOKS.dot(KEY_PK_ID) + "=?";
    }

    /**
     * Sql specific for syncing books with Goodreads.
     */
    static final class SqlGoodreadsSendBook {

        /**
         * Base SELECT from {@link DBDefinitions#TBL_BOOKS} for the fields
         * we need to send a Book to Goodreads.
         * <p>
         * Must be kept in sync with {@link GoodreadsManager#sendOneBook}
         */
        private static final String BASE_SELECT =
                SELECT_ + KEY_PK_ID
                + ',' + KEY_ISBN
                + ',' + KEY_EID_GOODREADS_BOOK
                + ',' + KEY_READ
                + ',' + KEY_READ_START
                + ',' + KEY_READ_END
                + ',' + KEY_RATING
                + _FROM_ + TBL_BOOKS.getName();

        /**
         * Get the needed {@link Book} fields for sending to Goodreads.
         * <p>
         * param KEY_PK_ID of the Book
         */
        static final String SINGLE_BOOK =
                BASE_SELECT + _WHERE_ + KEY_PK_ID + "=?";

        /**
         * Get the needed {@link Book} fields for sending to Goodreads.
         * <p>
         * param KEY_PK_ID of the first Book
         * <p>
         * Send all books.
         */
        static final String ALL_BOOKS =
                BASE_SELECT + _WHERE_ + KEY_PK_ID + ">?"
                + _ORDER_BY_ + KEY_PK_ID;

        /**
         * Get the needed {@link Book} fields for sending to Goodreads.
         * <p>
         * param KEY_PK_ID of the first Book
         * <p>
         * Send only new/updated books
         */
        static final String UPDATED_BOOKS =
                BASE_SELECT + _WHERE_ + KEY_PK_ID + ">?"
                + " AND " + KEY_UTC_LAST_UPDATED + '>' + KEY_UTC_GOODREADS_LAST_SYNC_DATE
                + _ORDER_BY_ + KEY_PK_ID;
    }

    /**
     * Sql INSERT.
     */
    static final class SqlInsert {

        static final String INSERT_INTO_ = "INSERT INTO ";

        static final String BOOKSHELF =
                INSERT_INTO_ + TBL_BOOKSHELF.getName()
                + '(' + KEY_BOOKSHELF_NAME
                + ',' + KEY_FK_STYLE
                + ',' + KEY_BOOKSHELF_BL_TOP_POS
                + ',' + KEY_BOOKSHELF_BL_TOP_OFFSET

                + ") VALUES (?,?,?,?)";

        static final String AUTHOR =
                INSERT_INTO_ + TBL_AUTHORS.getName()
                + '(' + KEY_AUTHOR_FAMILY_NAME
                + ',' + KEY_AUTHOR_FAMILY_NAME_OB
                + ',' + KEY_AUTHOR_GIVEN_NAMES
                + ',' + KEY_AUTHOR_GIVEN_NAMES_OB
                + ',' + KEY_AUTHOR_IS_COMPLETE
                + ") VALUES (?,?,?,?,?)";

        static final String SERIES =
                INSERT_INTO_ + TBL_SERIES.getName()
                + '(' + KEY_SERIES_TITLE
                + ',' + KEY_SERIES_TITLE_OB
                + ',' + KEY_SERIES_IS_COMPLETE
                + ") VALUES (?,?,?)";

        static final String PUBLISHER =
                INSERT_INTO_ + TBL_PUBLISHERS.getName()
                + '(' + KEY_PUBLISHER_NAME
                + ',' + KEY_PUBLISHER_NAME_OB
                + ") VALUES (?,?)";

        static final String TOC_ENTRY =
                INSERT_INTO_ + TBL_TOC_ENTRIES.getName()
                + '(' + KEY_FK_AUTHOR
                + ',' + KEY_TITLE
                + ',' + KEY_TITLE_OB
                + ',' + KEY_DATE_FIRST_PUBLICATION
                + ") VALUES (?,?,?,?)";


        static final String BOOK_TOC_ENTRY =
                INSERT_INTO_ + TBL_BOOK_TOC_ENTRIES.getName()
                + '(' + KEY_FK_TOC_ENTRY
                + ',' + KEY_FK_BOOK
                + ',' + KEY_BOOK_TOC_ENTRY_POSITION
                + ") VALUES (?,?,?)";

        static final String BOOK_BOOKSHELF =
                INSERT_INTO_ + TBL_BOOK_BOOKSHELF.getName()
                + '(' + KEY_FK_BOOK
                + ',' + KEY_FK_BOOKSHELF
                + ") VALUES (?,?)";

        static final String BOOK_AUTHOR =
                INSERT_INTO_ + TBL_BOOK_AUTHOR.getName()
                + '(' + KEY_FK_BOOK
                + ',' + KEY_FK_AUTHOR
                + ',' + KEY_BOOK_AUTHOR_POSITION
                + ',' + KEY_BOOK_AUTHOR_TYPE_BITMASK
                + ") VALUES(?,?,?,?)";

        static final String BOOK_SERIES =
                INSERT_INTO_ + TBL_BOOK_SERIES.getName()
                + '(' + KEY_FK_BOOK
                + ',' + KEY_FK_SERIES
                + ',' + KEY_BOOK_NUM_IN_SERIES
                + ',' + KEY_BOOK_SERIES_POSITION
                + ") VALUES(?,?,?,?)";

        static final String BOOK_PUBLISHER =
                INSERT_INTO_ + TBL_BOOK_PUBLISHER.getName()
                + '(' + KEY_FK_BOOK
                + ',' + KEY_FK_PUBLISHER
                + ',' + KEY_BOOK_PUBLISHER_POSITION
                + ") VALUES(?,?,?)";


        static final String BOOK_LOANEE =
                INSERT_INTO_ + TBL_BOOK_LOANEE.getName()
                + '(' + KEY_FK_BOOK
                + ',' + KEY_LOANEE
                + ") VALUES(?,?)";


        static final String BOOKLIST_STYLE =
                INSERT_INTO_ + TBL_BOOKLIST_STYLES.getName()
                + '(' + KEY_UUID
                + ',' + KEY_STYLE_IS_BUILTIN
                + ") VALUES (?,?)";
    }

    /**
     * Sql UPDATE.
     */
    static final class SqlUpdate {

        static final String UPDATE_ = "UPDATE ";
        static final String _SET_ = " SET ";

        /**
         * Update a single Book's KEY_UTC_LAST_UPDATED to 'now'
         */
        static final String TOUCH =
                UPDATE_ + TBL_BOOKS.getName()
                + _SET_ + KEY_UTC_LAST_UPDATED + "=current_timestamp"
                + _WHERE_ + KEY_PK_ID + "=?";

        /**
         * Global rename.
         */
        static final String FORMAT =
                UPDATE_ + TBL_BOOKS.getName()
                + _SET_ + KEY_UTC_LAST_UPDATED + "=current_timestamp"
                + ',' + KEY_FORMAT + "=?"
                + _WHERE_ + KEY_FORMAT + "=?";

        /**
         * Global rename.
         */
        static final String COLOR =
                UPDATE_ + TBL_BOOKS.getName()
                + _SET_ + KEY_UTC_LAST_UPDATED + "=current_timestamp"
                + ',' + KEY_COLOR + "=?"
                + _WHERE_ + KEY_COLOR + "=?";

        /**
         * Global rename.
         */
        static final String GENRE =
                UPDATE_ + TBL_BOOKS.getName()
                + _SET_ + KEY_UTC_LAST_UPDATED + "=current_timestamp"
                + ',' + KEY_GENRE + "=?"
                + _WHERE_ + KEY_GENRE + "=?";

        /**
         * Global rename.
         */
        static final String LANGUAGE =
                UPDATE_ + TBL_BOOKS.getName()
                + _SET_ + KEY_UTC_LAST_UPDATED + "=current_timestamp"
                + ',' + KEY_LANGUAGE + "=?"
                + _WHERE_ + KEY_LANGUAGE + "=?";

        /**
         * Global rename.
         */
        static final String LOCATION =
                UPDATE_ + TBL_BOOKS.getName()
                + _SET_ + KEY_UTC_LAST_UPDATED + "=current_timestamp"
                + ',' + KEY_LOCATION + "=?"
                + _WHERE_ + KEY_LOCATION + "=?";

        /**
         * Update a single Book's read status
         * and read_end date using a safe date construct.
         */
        static final String READ =
                UPDATE_ + TBL_BOOKS.getName()
                + _SET_ + KEY_UTC_LAST_UPDATED + "=current_timestamp"
                + ',' + KEY_READ + "=?"
                + ',' + KEY_READ_END + "=COALESCE(date(?, 'utc'),'')"
                + _WHERE_ + KEY_PK_ID + "=?";
        /**
         * Update a single Book's last sync date with Goodreads.
         * Do NOT update the {@link DBDefinitions#KEY_UTC_LAST_UPDATED} field.
         */
        static final String GOODREADS_LAST_SYNC_DATE =
                UPDATE_ + TBL_BOOKS.getName()
                + _SET_ + KEY_UTC_GOODREADS_LAST_SYNC_DATE + "=current_timestamp"
                + _WHERE_ + KEY_PK_ID + "=?";
        /**
         * Update a single Book's Goodreads id.
         * Do NOT update the {@link DBDefinitions#KEY_UTC_LAST_UPDATED} field.
         */
        static final String GOODREADS_BOOK_ID =
                UPDATE_ + TBL_BOOKS.getName()
                + _SET_ + KEY_EID_GOODREADS_BOOK + "=?"
                + _WHERE_ + KEY_PK_ID + "=?";
        /**
         * Update a single {@link TocEntry} using a safe date construct.
         */
        static final String TOCENTRY =
                UPDATE_ + TBL_TOC_ENTRIES.getName()
                + _SET_ + KEY_TITLE + "=?"
                + ',' + KEY_TITLE_OB + "=?"
                + ',' + KEY_DATE_FIRST_PUBLICATION + "=COALESCE(date(?, 'utc'),'')"
                + _WHERE_ + KEY_PK_ID + "=?";
    }

    /**
     * Sql DELETE.
     * <p>
     * All 'link' tables will be updated due to their FOREIGN KEY constraints.
     * The 'other-side' of a link table is cleaned by triggers.
     */
    static final class SqlDelete {

        static final String _NOT_IN_ = " NOT IN ";
        private static final String DELETE_FROM_ = "DELETE FROM ";
        /** Delete a {@link Book}. */
        static final String BOOK_BY_ID =
                DELETE_FROM_ + TBL_BOOKS.getName() + _WHERE_ + KEY_PK_ID + "=?";
        /** Delete a {@link Bookshelf}. */
        static final String BOOKSHELF_BY_ID =
                DELETE_FROM_ + TBL_BOOKSHELF.getName() + _WHERE_ + KEY_PK_ID + "=?";
        /** Delete an {@link Author}. */
        static final String AUTHOR_BY_ID =
                DELETE_FROM_ + TBL_AUTHORS.getName() + _WHERE_ + KEY_PK_ID + "=?";
        /** Delete a {@link Series}. */
        static final String SERIES_BY_ID =
                DELETE_FROM_ + TBL_SERIES.getName() + _WHERE_ + KEY_PK_ID + "=?";
        /** Delete a {@link Publisher}. */
        static final String PUBLISHER_BY_ID =
                DELETE_FROM_ + TBL_PUBLISHERS.getName() + _WHERE_ + KEY_PK_ID + "=?";
        /** Delete a {@link TocEntry}. */
        static final String TOC_ENTRY =
                DELETE_FROM_ + TBL_TOC_ENTRIES.getName() + _WHERE_ + KEY_PK_ID + "=?";
        /** Delete a {@link BooklistStyle}. */
        static final String STYLE_BY_ID =
                DELETE_FROM_ + TBL_BOOKLIST_STYLES.getName() + _WHERE_ + KEY_PK_ID + "=?";
        /**
         * Delete the link between a {@link Book} and an {@link Author}.
         * <p>
         * This is done when a book is updated; first delete all links, then re-create them.
         */
        static final String BOOK_AUTHOR_BY_BOOK_ID =
                DELETE_FROM_ + TBL_BOOK_AUTHOR.getName() + _WHERE_ + KEY_FK_BOOK + "=?";
        /**
         * Delete the link between a {@link Book} and a {@link Bookshelf}.
         * <p>
         * This is done when a book is updated; first delete all links, then re-create them.
         */
        static final String BOOK_BOOKSHELF_BY_BOOK_ID =
                DELETE_FROM_ + TBL_BOOK_BOOKSHELF.getName() + _WHERE_ + KEY_FK_BOOK + "=?";
        /**
         * Delete the link between a {@link Book} and a {@link Series}.
         * <p>
         * This is done when a book is updated; first delete all links, then re-create them.
         */
        static final String BOOK_SERIES_BY_BOOK_ID =
                DELETE_FROM_ + TBL_BOOK_SERIES.getName() + _WHERE_ + KEY_FK_BOOK + "=?";
        /**
         * Delete the link between a {@link Book} and a {@link Publisher}.
         * <p>
         * This is done when a book is updated; first delete all links, then re-create them.
         */
        static final String BOOK_PUBLISHER_BY_BOOK_ID =
                DELETE_FROM_ + TBL_BOOK_PUBLISHER.getName() + _WHERE_ + KEY_FK_BOOK + "=?";
        /**
         * Delete the link between a {@link Book} and a {@link TocEntry}.
         * <p>
         * This is done when a TOC is updated; first delete all links, then re-create them.
         */
        static final String BOOK_TOC_ENTRIES_BY_BOOK_ID =
                DELETE_FROM_ + TBL_BOOK_TOC_ENTRIES.getName() + _WHERE_ + KEY_FK_BOOK + "=?";
        /**
         * Delete the loan of a {@link Book}; i.e. 'return the book'.
         */
        static final String BOOK_LOANEE_BY_BOOK_ID =
                DELETE_FROM_ + TBL_BOOK_LOANEE.getName() + _WHERE_ + KEY_FK_BOOK + "=?";
        /**
         * Purge an {@link Author} if no longer in use (check both book_author AND toc_entries).
         */
        static final String PURGE_AUTHORS =
                DELETE_FROM_ + TBL_AUTHORS.getName()
                + _WHERE_ + KEY_PK_ID + _NOT_IN_
                + "(SELECT DISTINCT " + KEY_FK_AUTHOR + _FROM_ + TBL_BOOK_AUTHOR.getName() + ')'
                + " AND " + KEY_PK_ID + _NOT_IN_
                + "(SELECT DISTINCT " + KEY_FK_AUTHOR + _FROM_ + TBL_TOC_ENTRIES.getName() + ')';

        /**
         * Purge a {@link Series} if no longer in use.
         */
        static final String PURGE_SERIES =
                DELETE_FROM_ + TBL_SERIES.getName()
                + _WHERE_ + KEY_PK_ID + _NOT_IN_
                + "(SELECT DISTINCT " + KEY_FK_SERIES + _FROM_ + TBL_BOOK_SERIES.getName() + ')';

        /**
         * Purge a {@link Publisher} if no longer in use.
         */
        static final String PURGE_PUBLISHERS =
                DELETE_FROM_ + TBL_PUBLISHERS.getName()
                + _WHERE_ + KEY_PK_ID + _NOT_IN_
                + "(SELECT DISTINCT " + KEY_FK_PUBLISHER
                + _FROM_ + TBL_BOOK_PUBLISHER.getName() + ')';


        static final String BOOK_LIST_NODE_STATE_BY_BOOKSHELF =
                DELETE_FROM_ + DBDefinitions.TBL_BOOK_LIST_NODE_STATE
                + _WHERE_ + KEY_FK_BOOKSHELF + "=?";

        static final String BOOK_LIST_NODE_STATE_BY_STYLE =
                DELETE_FROM_ + DBDefinitions.TBL_BOOK_LIST_NODE_STATE
                + _WHERE_ + KEY_FK_STYLE + "=?";

        /** Maintenance/debug usage. Simple clear all state data. */
        static final String PURGE_BOOK_LIST_NODE_STATE = DELETE_FROM_ + TBL_BOOK_LIST_NODE_STATE;

        private SqlDelete() {
        }
    }

    /**
     * Sql specific for FTS.
     */
    public static final class SqlFTS {

        /** Used during insert of a book. Minimal column list. Ordered by position. */
        static final String GET_AUTHORS_BY_BOOK_ID =
                SELECT_ + TBL_AUTHORS.dotAs(KEY_AUTHOR_FAMILY_NAME)
                + ',' + TBL_AUTHORS.dotAs(KEY_AUTHOR_GIVEN_NAMES)
                + _FROM_ + TBL_BOOK_AUTHOR.ref() + TBL_BOOK_AUTHOR.join(TBL_AUTHORS)
                + _WHERE_ + TBL_BOOK_AUTHOR.dot(KEY_FK_BOOK) + "=?"
                + _ORDER_BY_ + TBL_BOOK_AUTHOR.dot(KEY_BOOK_AUTHOR_POSITION);

        /** Used during insert of a book. Minimal column list. Ordered by position. */
        static final String GET_SERIES_BY_BOOK_ID =
                SELECT_ + TBL_SERIES.dot(KEY_SERIES_TITLE) + "||' '||"
                + " COALESCE(" + TBL_BOOK_SERIES.dot(KEY_BOOK_NUM_IN_SERIES) + ",'')"
                + _AS_ + KEY_SERIES_TITLE
                + _FROM_ + TBL_BOOK_SERIES.ref() + TBL_BOOK_SERIES.join(TBL_SERIES)
                + _WHERE_ + TBL_BOOK_SERIES.dot(KEY_FK_BOOK) + "=?"
                + _ORDER_BY_ + TBL_BOOK_SERIES.dot(KEY_BOOK_SERIES_POSITION);

        /** Used during insert of a book. Minimal column list. Ordered by position. */
        static final String GET_PUBLISHERS_BY_BOOK_ID =
                SELECT_ + TBL_PUBLISHERS.dotAs(KEY_PUBLISHER_NAME)
                + _FROM_ + TBL_BOOK_PUBLISHER.ref() + TBL_BOOK_PUBLISHER.join(TBL_PUBLISHERS)
                + _WHERE_ + TBL_BOOK_PUBLISHER.dot(KEY_FK_BOOK) + "=?"
                + _ORDER_BY_ + TBL_BOOK_PUBLISHER.dot(KEY_BOOK_PUBLISHER_POSITION);

        /** Used during insert of a book. Minimal column list. Ordered by position. */
        static final String GET_TOC_TITLES_BY_BOOK_ID =
                SELECT_ + TBL_TOC_ENTRIES.dotAs(KEY_TITLE)
                + _FROM_ + TBL_TOC_ENTRIES.ref() + TBL_TOC_ENTRIES.join(TBL_BOOK_TOC_ENTRIES)
                + _WHERE_ + TBL_BOOK_TOC_ENTRIES.dot(KEY_FK_BOOK) + "=?"
                + _ORDER_BY_ + TBL_BOOK_TOC_ENTRIES.dot(KEY_BOOK_TOC_ENTRY_POSITION);

        /** the body of an INSERT INTO [table]. Used more than once. */
        static final String INSERT_BODY =
                " (" + KEY_TITLE
                + ',' + KEY_FTS_AUTHOR_NAME
                + ',' + KEY_SERIES_TITLE
                + ',' + KEY_DESCRIPTION
                + ',' + KEY_PRIVATE_NOTES
                + ',' + KEY_PUBLISHER_NAME
                + ',' + KEY_GENRE
                + ',' + KEY_LOCATION
                + ',' + KEY_ISBN
                + ',' + KEY_FTS_TOC_ENTRY_TITLE

                + ',' + KEY_FTS_BOOK_ID
                + ") VALUES (?,?,?,?,?,?,?,?,?,?,?)";

        /**
         * The full INSERT statement.
         * The parameter order MUST match the order expected in UPDATE.
         */
        static final String INSERT = "INSERT INTO " + TBL_FTS_BOOKS.getName() + INSERT_BODY;

        /**
         * The full UPDATE statement.
         * The parameter order MUST match the order expected in INSERT.
         */
        static final String UPDATE =
                "UPDATE " + TBL_FTS_BOOKS.getName()
                + " SET " + KEY_TITLE + "=?"
                + ',' + KEY_FTS_AUTHOR_NAME + "=?"
                + ',' + KEY_SERIES_TITLE + "=?"
                + ',' + KEY_DESCRIPTION + "=?"
                + ',' + KEY_PRIVATE_NOTES + "=?"
                + ',' + KEY_PUBLISHER_NAME + "=?"
                + ',' + KEY_GENRE + "=?"
                + ',' + KEY_LOCATION + "=?"
                + ',' + KEY_ISBN + "=?"
                + ',' + KEY_FTS_TOC_ENTRY_TITLE + "=?"

                + _WHERE_ + KEY_FTS_BOOK_ID + "=?";

        /** Standard Local-search. */
        static final String SEARCH_SUGGESTIONS =
                // KEY_FTS_BOOKS_PK is the _id into the books table.
                SELECT_ + KEY_FTS_BOOK_ID + _AS_ + KEY_PK_ID
                + ',' + (TBL_FTS_BOOKS.dot(KEY_TITLE)
                         + _AS_ + SearchManager.SUGGEST_COLUMN_TEXT_1)
                + ',' + (TBL_FTS_BOOKS.dot(KEY_FTS_AUTHOR_NAME)
                         + _AS_ + SearchManager.SUGGEST_COLUMN_TEXT_2)
                + ',' + (TBL_FTS_BOOKS.dot(KEY_TITLE)
                         + _AS_ + SearchManager.SUGGEST_COLUMN_INTENT_DATA)
                + _FROM_ + TBL_FTS_BOOKS.getName()
                + _WHERE_ + TBL_FTS_BOOKS.getName() + " MATCH ?";

        /** Advanced Local-search. */
        static final String SEARCH
                = SELECT_
                  // KEY_FTS_BOOKS_PK is the _id into the books table.
                  + KEY_FTS_BOOK_ID + _AS_ + KEY_PK_ID
                  + _FROM_ + TBL_FTS_BOOKS.getName()
                  + _WHERE_ + TBL_FTS_BOOKS.getName() + " MATCH ?"
                  + " LIMIT ?";

        /** Used during a full FTS rebuild. Minimal column list. */
        static final String ALL_BOOKS =
                SELECT_ + KEY_PK_ID
                + ',' + KEY_TITLE
                + ',' + KEY_DESCRIPTION
                + ',' + KEY_PRIVATE_NOTES
                + ',' + KEY_GENRE
                + ',' + KEY_LOCATION
                + ',' + KEY_ISBN
                + _FROM_ + TBL_BOOKS.getName();

        /** Used during insert of a book. Minimal column list. */
        static final String BOOK_BY_ID = ALL_BOOKS + _WHERE_ + KEY_PK_ID + "=?";

        /**
         * Cleanup a search string as preparation for an FTS search.
         * <p>
         * All diacritic characters are converted to ASCII.
         * Remove punctuation from the search string to TRY to match the tokenizer.
         * The only punctuation we allow is a hyphen preceded by a space => negate the next word.
         * Everything else is translated to a space.
         *
         * @param searchText Search criteria to clean
         * @param domain     (optional) domain to prefix the searchText or {@code null} for none
         *
         * @return Clean string
         */
        @NonNull
        static String cleanupFtsCriterion(@Nullable final String searchText,
                                          @Nullable final String domain) {

            if (searchText == null || searchText.isEmpty()) {
                return "";
            }

            // Convert the text to pure ASCII. We'll use an array to loop over it.
            final char[] chars = DAO.toAscii(searchText).toCharArray();
            // Cached length
            final int len = chars.length;
            // Initial position
            int pos = 0;
            // 'previous' character
            char prev = ' ';

            // Output buffer
            final StringBuilder parameter = new StringBuilder();

            // Loop over array
            while (pos < len) {
                char current = chars[pos];
                // If current is letter or digit, use it.
                if (Character.isLetterOrDigit(current)) {
                    parameter.append(current);

                } else if (current == '-' && Character.isWhitespace(prev)) {
                    // Allow negation if preceded by space
                    parameter.append(current);

                } else {
                    // Turn everything else in whitespace
                    current = ' ';

                    if (!Character.isWhitespace(prev)) {
                        // If prev character was non-ws, and not negation, make wildcard
                        if (prev != '-') {
                            parameter.append('*');
                        }
                        // Append a whitespace only when last char was not a whitespace
                        parameter.append(' ');
                    }
                }
                prev = current;
                pos++;
            }

            // append a wildcard if prev character was non-ws, and not negation
            if (!Character.isWhitespace(prev) && (prev != '-')) {
                parameter.append('*');
            }
            // reminder to self: we do not need to prepend with a '*' for MATCH to work.
            final String cleanedText = parameter.toString().trim();

            if (domain != null) {
                // prepend each word with the FTS column name.
                final StringBuilder result = new StringBuilder();
                for (String word : cleanedText.split(" ")) {
                    if (!word.isEmpty()) {
                        result.append(' ').append(domain).append(':').append(word);
                    }
                }
                return result.toString();
            } else {
                // no domain, return as-is
                return cleanedText;
            }
        }

        /**
         * Create a string suited to be used with MATCH.
         *
         * @param author        Author related keywords to find
         * @param title         Title related keywords to find
         * @param seriesTitle   Series title related keywords to find
         * @param publisherName Publisher name related keywords to find
         * @param keywords      Keywords to find anywhere in book; this includes titles and authors
         *
         * @return an query string suited to search FTS for the specified parameters,
         * or {@code ""} if all input was empty
         */
        @NonNull
        public static String createMatchString(@Nullable final String author,
                                               @Nullable final String title,
                                               @Nullable final String seriesTitle,
                                               @Nullable final String publisherName,
                                               @Nullable final String keywords) {

            return (cleanupFtsCriterion(keywords, null)
                    + cleanupFtsCriterion(author, KEY_FTS_AUTHOR_NAME)
                    + cleanupFtsCriterion(title, KEY_TITLE)
                    + cleanupFtsCriterion(seriesTitle, KEY_SERIES_TITLE)
                    + cleanupFtsCriterion(publisherName, KEY_PUBLISHER_NAME))
                    .trim();
        }
    }
}
