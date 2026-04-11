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

package com.hardbacknutter.nevertoomanybooks.database.dao.impl;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Optional;
import java.util.stream.Collectors;

import com.hardbacknutter.nevertoomanybooks.BuildConfig;
import com.hardbacknutter.nevertoomanybooks.DEBUG_SWITCHES;
import com.hardbacknutter.nevertoomanybooks.booklist.filters.Filter;
import com.hardbacknutter.nevertoomanybooks.booklist.filters.FtsMatchFilter;
import com.hardbacknutter.nevertoomanybooks.booklist.filters.LoaneeFilter;
import com.hardbacknutter.nevertoomanybooks.booklist.filters.NumberListFilter;
import com.hardbacknutter.nevertoomanybooks.core.utils.textnormaliser.TextNormaliser;
import com.hardbacknutter.nevertoomanybooks.database.DBKey;
import com.hardbacknutter.nevertoomanybooks.localsearch.LocalSearchCriteria;
import com.hardbacknutter.util.logger.LoggerFactory;

import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.DOM_PK_ID;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.TBL_BOOKS;

/**
 * Helper methods for preparing a search.
 */
public class FtsDaoHelper {

    private static final String TAG = "FtsDaoHelper";

    @NonNull
    private final TextNormaliser textNormaliser;

    /**
     * Constructor.
     */
    public FtsDaoHelper() {
        textNormaliser = new TextNormaliser();
    }

    /**
     * Prepare a search string for doing an FTS search.
     * <p>
     * All diacritic characters are converted to ASCII.
     * Remove punctuation from the search string to TRY to match the tokeniser.
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
    String prepareSearchText(@Nullable final CharSequence searchText,
                             @Nullable final String domain) {

        if (searchText == null || searchText.length() == 0) {
            return "";
        }

        // Keep only alpha/digit, space and '-' characters.
        // We'll use an array to loop over it.
        final char[] chars = textNormaliser.ftsNormalise(searchText).toCharArray();
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
        // reminder to self: do not prepend with a '*' when using MATCH
        // SqLite FTS supports Prefix Matching only!
        final String cleanedText = parameter.toString().strip();

        if (domain != null) {
            // prepend each word with the FTS column name.
            return Arrays.stream(cleanedText.split(" "))
                         .filter(word -> !word.isEmpty())
                         .map(word -> ' ' + domain + ':' + word)
                         .collect(Collectors.joining());
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
    Optional<String> createMatchClause(@Nullable final CharSequence bookTitle,
                                       @Nullable final CharSequence seriesTitle,
                                       @Nullable final CharSequence author,
                                       @Nullable final CharSequence publisherName,
                                       @Nullable final CharSequence keywords) {

        final String query = (prepareSearchText(keywords, null)
                              + prepareSearchText(author, DBKey.FTS.AUTHOR_NAME)
                              + prepareSearchText(bookTitle, DBKey.TITLE)
                              + prepareSearchText(seriesTitle, DBKey.SERIES.TITLE)
                              + prepareSearchText(publisherName, DBKey.PUBLISHER.NAME))
                .strip();

        return query.isEmpty() ? Optional.empty() : Optional.of(query);
    }


    /**
     * Convert the given criteria to a list of {@link Filter}s.
     *
     * @param searchCriteria to convert
     *
     * @return filters
     */
    @NonNull
    public Collection<Filter> toFilters(@NonNull final LocalSearchCriteria searchCriteria) {
        if (BuildConfig.DEBUG && DEBUG_SWITCHES.BOB_THE_BUILDER) {
            LoggerFactory.getLogger().d(TAG, "toFilters", searchCriteria);
        }
        final Collection<Filter> filters = new ArrayList<>();

        // if we have a list of ID's, we'll ignore other criteria
        if (searchCriteria.getBookIdList().isEmpty()) {
            // Criteria supported by FTS
            createMatchClause(searchCriteria.getFtsBookTitle(),
                              searchCriteria.getFtsSeriesTitle(),
                              searchCriteria.getFtsAuthor(),
                              searchCriteria.getFtsPublisher(),
                              searchCriteria.getFtsKeywords())
                    .map(FtsMatchFilter::new)
                    .ifPresent(filters::add);

            // Add a filter to retrieve only books lend to the given person (exact name).
            final String loanee = searchCriteria.getLoanee();
            if (loanee != null && !loanee.isBlank()) {
                filters.add(new LoaneeFilter(loanee));
            }
        } else {
            // Add a where clause for: "AND books._id IN (list)".
            filters.add(new NumberListFilter<>(TBL_BOOKS, DOM_PK_ID,
                                               searchCriteria.getBookIdList()));
        }

        return filters;
    }
}
