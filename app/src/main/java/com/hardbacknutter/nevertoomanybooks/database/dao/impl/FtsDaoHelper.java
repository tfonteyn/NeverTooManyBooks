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

package com.hardbacknutter.nevertoomanybooks.database.dao.impl;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.text.Normalizer;
import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import com.hardbacknutter.nevertoomanybooks.database.DBKey;

/**
 * Helper to normalize strings before inserting them into the FTS table,
 * and methods to prepare for a search.
 */
public final class FtsDaoHelper {

    private static final String LIST_DELIMITER = "; ";

    /**
     * Keep only alpha/digit and space characters.
     *
     * @see #normalize(CharSequence)
     */
    private static final Pattern NORMALIZER_PATTERN =
            Pattern.compile("[^\\p{Alpha}\\d ]");

    /**
     * Keep only alpha/digit, space and the '-' characters.
     *
     * @see #prepareSearchText(String, String)
     */
    private static final Pattern PREPARE_SEARCH_TEXT_PATTERN =
            Pattern.compile("[^\\p{Alpha}\\d -]");

    private FtsDaoHelper() {
    }

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
    public static String prepareSearchText(@Nullable final String searchText,
                                           @Nullable final String domain) {

        if (searchText == null || searchText.isEmpty()) {
            return "";
        }

        // Convert the text to pure alpha/digits. We'll use an array to loop over it.
        final String normalized = Normalizer.normalize(searchText, Normalizer.Form.NFD);
        final char[] chars = PREPARE_SEARCH_TEXT_PATTERN.matcher(normalized)
                                                        .replaceAll("")
                                                        .toCharArray();
        // Initial position
        int pos = 0;
        // 'previous' character
        char prev = ' ';

        // Output buffer
        final StringBuilder parameter = new StringBuilder();

        // Loop over array
        while (pos < chars.length) {
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
    public static Optional<String> createMatchClause(@Nullable final String bookTitle,
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
     * Normalize the given text by stripping all non-alpha/digits.
     *
     * @param text to normalize
     *
     * @return normalized string
     */
    @NonNull
    static String normalize(@NonNull final CharSequence text) {
        final String normalized = Normalizer.normalize(text, Normalizer.Form.NFD);
        return NORMALIZER_PATTERN.matcher(normalized).replaceAll("");
    }

    /**
     * Normalize each element in the list by stripping all non-alpha/digits;
     * and concatenate them to a semi-colon separated string-list.
     *
     * @param list to normalize
     *
     * @return normalized string
     */
    @NonNull
    static String normalize(@NonNull final List<String> list) {
        return list.stream()
                   .map(FtsDaoHelper::normalize)
                   .collect(Collectors.joining(LIST_DELIMITER));
    }
}
