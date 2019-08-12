/*
 * @Copyright 2019 HardBackNutter
 * @License GNU General Public License
 *
 * This file is part of NeverToManyBooks.
 *
 * In August 2018, this project was forked from:
 * Book Catalogue 5.2.2 @copyright 2010 Philip Warner & Evan Leybourn
 *
 * Without their original creation, this project would not exist in its current form.
 * It was however largely rewritten/refactored and any comments on this fork
 * should be directed at HardBackNutter and not at the original creator.
 *
 * NeverToManyBooks is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * NeverToManyBooks is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 * See the GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with NeverToManyBooks. If not, see <http://www.gnu.org/licenses/>.
 */
package com.hardbacknutter.nevertomanybooks.entities;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Value class giving resulting series info after parsing a book title.
 */
public class ParsedBookTitle {

    /**
     * Parse series title/numbers embedded in a book title.
     * group 1: title
     * group 4: number
     */
    private static final Pattern SERIES_FROM_BOOK_TITLE_PATTERN =
            Pattern.compile("(.*?)(,|\\s)\\s*"
                            + Series.NUMBER_PREFIX_REGEXP
                            + Series.NUMBER_REGEXP,
                            Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
    @NonNull
    private String mBookTitle;
    @NonNull
    private String mSeriesTitle;
    @NonNull
    private String mSeriesNumber = "";

    /**
     * Constructor.
     *
     * @param bookTitle   The cleaned up book title. No further processing is done.
     * @param seriesTitle Series title string to process.
     */
    private ParsedBookTitle(@NonNull final String bookTitle,
                            @NonNull final String seriesTitle) {
        mBookTitle = bookTitle;
        mSeriesTitle = seriesTitle;

        Matcher matcher = SERIES_FROM_BOOK_TITLE_PATTERN.matcher(mSeriesTitle);
        if (matcher.find()) {
            mSeriesTitle = matcher.group(1);
            mSeriesNumber = Series.cleanupSeriesNumber(matcher.group(4));
        }
    }

    /**
     * Try to extract a title/series/number from a book title.
     * FIXME: the only format supported is "bookTitle (seriesTitleAndNumber)"
     *
     * @param fullTitle Book title to parse
     *
     * @return structure with parsed details of the title, or {@code null}.
     */
    @Nullable
    public static ParsedBookTitle parseBrackets(@Nullable final String fullTitle) {
        if (fullTitle == null || fullTitle.isEmpty()) {
            return null;
        }

        int openBracket = fullTitle.lastIndexOf('(');
        // We want a title that does not START with a bracket!
        if (openBracket >= 1) {
            int closeBracket = fullTitle.lastIndexOf(')');
            if (closeBracket > -1 && openBracket < closeBracket) {
                String bookTitle = fullTitle.substring(0, openBracket - 1).trim();
                String seriesTitle = fullTitle.substring(openBracket + 1, closeBracket);

                return new ParsedBookTitle(bookTitle, seriesTitle);
            }
        }
        return null;
    }

    @NonNull
    public String getBookTitle() {
        return mBookTitle;
    }

    /**
     * @return series title, can be empty, never {@code null}
     */
    @NonNull
    public String getSeriesTitle() {
        return mSeriesTitle;
    }

    /**
     * @return the number (aka position) of a book in the series, can be empty, never {@code null}
     */
    @NonNull
    public String getSeriesNumber() {
        return mSeriesNumber;
    }
}
