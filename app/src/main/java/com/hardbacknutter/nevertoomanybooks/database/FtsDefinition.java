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

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.hardbacknutter.nevertoomanybooks.database.definitions.ColumnInfo;
import com.hardbacknutter.nevertoomanybooks.database.definitions.Domain;
import com.hardbacknutter.nevertoomanybooks.database.definitions.TableDefinition;
import com.hardbacknutter.nevertoomanybooks.utils.ParseUtils;

import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.DOM_BOOK_DESCRIPTION;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.DOM_BOOK_GENRE;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.DOM_BOOK_ISBN;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.DOM_BOOK_LOCATION;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.DOM_BOOK_PRIVATE_NOTES;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.DOM_PUBLISHER_NAME;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.DOM_SERIES_TITLE;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.DOM_TITLE;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.KEY_AUTHOR_FAMILY_NAME;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.KEY_AUTHOR_GIVEN_NAMES;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.KEY_BOOK_AUTHOR_POSITION;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.KEY_BOOK_NUM_IN_SERIES;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.KEY_BOOK_PUBLISHER_POSITION;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.KEY_BOOK_SERIES_POSITION;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.KEY_BOOK_TOC_ENTRY_POSITION;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.KEY_DESCRIPTION;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.KEY_FK_BOOK;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.KEY_GENRE;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.KEY_ISBN;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.KEY_LOCATION;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.KEY_PK_ID;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.KEY_PRIVATE_NOTES;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.KEY_PUBLISHER_NAME;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.KEY_SERIES_TITLE;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.KEY_TITLE;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.TBL_AUTHORS;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.TBL_BOOKS;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.TBL_BOOK_AUTHOR;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.TBL_BOOK_PUBLISHER;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.TBL_BOOK_SERIES;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.TBL_BOOK_TOC_ENTRIES;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.TBL_PUBLISHERS;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.TBL_SERIES;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.TBL_TOC_ENTRIES;

/**
 * FTS definitions and helpers.
 * reminder: no need for a type nor constraints: https://sqlite.org/fts3.html
 */
public class FtsDefinition {

    public static final TableDefinition TBL_FTS_BOOKS;
    /** FTS Primary key. */
    public static final String KEY_FTS_BOOK_ID = "docid";
    /**
     * {@link #TBL_FTS_BOOKS}
     * specific formatted list; example: "stephen baxter;arthur c. clarke;"
     */
    private static final Domain DOM_FTS_AUTHOR_NAME;
    private static final Domain DOM_FTS_TOC_ENTRY_TITLE;
    /** {@link #TBL_FTS_BOOKS}. Semi-colon concatenated authors. */
    private static final String KEY_FTS_AUTHOR_NAME = "author_name";
    /** {@link #TBL_FTS_BOOKS}. Semi-colon concatenated titles. */
    private static final String KEY_FTS_TOC_ENTRY_TITLE = "toc_title";

    private static final String SELECT_ = "SELECT ";
    private static final String _FROM_ = " FROM ";
    private static final String _WHERE_ = " WHERE ";
    private static final String _ORDER_BY_ = " ORDER BY ";
    private static final String _AS_ = " AS ";

    static {
        DOM_FTS_AUTHOR_NAME =
                new Domain.Builder(KEY_FTS_AUTHOR_NAME, ColumnInfo.TYPE_TEXT).build();

        DOM_FTS_TOC_ENTRY_TITLE =
                new Domain.Builder(KEY_FTS_TOC_ENTRY_TITLE, ColumnInfo.TYPE_TEXT).build();

        TBL_FTS_BOOKS = createTableDefinition("books_fts");
    }

    @NonNull
    static TableDefinition createTableDefinition(@NonNull final String name) {
        return new TableDefinition(name)
                .setType(TableDefinition.TableType.FTS4)
                .addDomains(DOM_TITLE,
                            DOM_FTS_AUTHOR_NAME,
                            DOM_SERIES_TITLE,
                            DOM_PUBLISHER_NAME,

                            DOM_BOOK_DESCRIPTION,
                            DOM_BOOK_PRIVATE_NOTES,
                            DOM_BOOK_GENRE,
                            DOM_BOOK_LOCATION,
                            DOM_BOOK_ISBN,

                            DOM_FTS_TOC_ENTRY_TITLE
                           );
    }

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
        final char[] chars = ParseUtils.toAscii(searchText).toCharArray();
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
            for (final String word : cleanedText.split(" ")) {
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

    /**
     * Sql specific for FTS.
     */
    public static final class Sql {

        /**
         * The full UPDATE statement.
         * The parameter order MUST match the order expected in INSERT.
         */
        public static final String UPDATE =
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

        /** Used during insert of a book. Minimal column list. Ordered by position. */
        static final String GET_AUTHORS_BY_BOOK_ID =
                SELECT_ + TBL_AUTHORS.dotAs(KEY_AUTHOR_FAMILY_NAME)
                + ',' + TBL_AUTHORS.dotAs(KEY_AUTHOR_GIVEN_NAMES)
                + _FROM_ + TBL_BOOK_AUTHOR.ref() + TBL_BOOK_AUTHOR.join(TBL_AUTHORS)
                + _WHERE_ + TBL_BOOK_AUTHOR.dot(KEY_FK_BOOK) + "=?"
                + _ORDER_BY_ + TBL_BOOK_AUTHOR.dot(KEY_BOOK_AUTHOR_POSITION);

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


        /** Used during insert of a book. Minimal column list. Ordered by position. */
        static final String GET_SERIES_BY_BOOK_ID =
                SELECT_ + TBL_SERIES.dot(KEY_SERIES_TITLE) + "||' '||"
                + " COALESCE(" + TBL_BOOK_SERIES.dot(KEY_BOOK_NUM_IN_SERIES) + ",'')"
                + _AS_ + KEY_SERIES_TITLE
                + _FROM_ + TBL_BOOK_SERIES.ref() + TBL_BOOK_SERIES.join(TBL_SERIES)
                + _WHERE_ + TBL_BOOK_SERIES.dot(KEY_FK_BOOK) + "=?"
                + _ORDER_BY_ + TBL_BOOK_SERIES.dot(KEY_BOOK_SERIES_POSITION);

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
    }
}
