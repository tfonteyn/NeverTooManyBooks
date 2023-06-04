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
package com.hardbacknutter.nevertoomanybooks.database.dao;

import android.database.Cursor;

import androidx.annotation.IntRange;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.List;
import java.util.Optional;

import com.hardbacknutter.nevertoomanybooks.core.database.TransactionException;
import com.hardbacknutter.nevertoomanybooks.core.utils.AlphabeticNormalizer;
import com.hardbacknutter.nevertoomanybooks.database.DBKey;

public interface FtsDao {

    /**
     * Prepare a search string for doing an FTS search.
     * <p>
     * All diacritic characters are converted to ASCII.
     * Remove punctuation from the search string to TRY to match the tokenizer.
     * The only punctuation we allow is a hyphen preceded by a space => negate
     * the next word.
     * Everything else is translated to a space.
     *
     * @param searchText Search criteria to clean
     * @param domain     (optional) domain to prefix the searchText
     *                   or {@code null} for none
     *
     * @return Clean string
     */
    @NonNull
    static String prepareSearchText(@Nullable final String searchText,
                                    @Nullable final String domain) {

        if (searchText == null || searchText.isEmpty()) {
            return "";
        }

        // Convert the text to pure ASCII. We'll use an array to loop over it.
        final char[] chars = AlphabeticNormalizer.normalize(searchText).toCharArray();
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
                parameter.append('-');

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
        if (!Character.isWhitespace(prev) && prev != '-') {
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
     * @param bookTitle     Title related keywords to find
     * @param seriesTitle   Series title related keywords to find
     * @param author        Author related keywords to find
     * @param publisherName Publisher name related keywords to find
     * @param keywords      Keywords to find anywhere in book;
     *                      this includes titles and authors
     *
     * @return an Optional with query string suited to search FTS for the specified parameters.
     */
    @NonNull
    static Optional<String> createMatchString(@Nullable final String bookTitle,
                                              @Nullable final String seriesTitle,
                                              @Nullable final String author,
                                              @Nullable final String publisherName,
                                              @Nullable final String keywords) {

        final String query = (prepareSearchText(keywords, null)
                              + prepareSearchText(author, DBKey.FTS_AUTHOR_NAME)
                              + prepareSearchText(bookTitle, DBKey.TITLE)
                              + prepareSearchText(seriesTitle, DBKey.SERIES_TITLE)
                              + prepareSearchText(publisherName, DBKey.PUBLISHER_NAME)
        ).trim();

        if (query.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(query);
    }

    /**
     * Return a {@link Cursor}, suited for a local-search.
     * This is used by the advanced search activity.
     *
     * @param author        Author related keywords to find
     * @param title         Title related keywords to find
     * @param seriesTitle   Series title related keywords to find
     * @param publisherName Publisher name related keywords to find
     * @param keywords      Keywords to find anywhere in book; this includes titles and authors
     * @param limit         maximum number of rows to return
     *
     * @return a cursor, or {@code null} if all input was empty
     */
    @NonNull
    List<Long> search(@Nullable String author,
                      @Nullable String title,
                      @Nullable String seriesTitle,
                      @Nullable String publisherName,
                      @Nullable String keywords,
                      int limit);

    /**
     * Rebuild the entire FTS database.
     */
    void rebuild();

    /**
     * Insert an FTS record for the given book.
     * <p>
     * <strong>Transaction:</strong> required
     *
     * @param bookId the book id
     *
     * @throws TransactionException a transaction must be started before calling this method
     */
    void insert(@IntRange(from = 1) long bookId)
            throws TransactionException;

    /**
     * Update an FTS record for the given book.
     * <p>
     * <strong>Transaction:</strong> required
     *
     * @param bookId the book id
     *
     * @throws TransactionException a transaction must be started before calling this method
     */
    void update(@IntRange(from = 1) long bookId)
            throws TransactionException;
}
