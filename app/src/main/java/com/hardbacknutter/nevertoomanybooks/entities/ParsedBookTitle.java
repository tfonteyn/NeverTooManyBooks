/*
 * @Copyright 2019 HardBackNutter
 * @License GNU General Public License
 *
 * This file is part of NeverTooManyBooks.
 *
 * In August 2018, this project was forked from:
 * Book Catalogue 5.2.2 @2016 Philip Warner & Evan Leybourn
 *
 * Without their original creation, this project would not exist in its
 * current form. It was however largely rewritten/refactored and any
 * comments on this fork should be directed at HardBackNutter and not
 * at the original creators.
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
package com.hardbacknutter.nevertoomanybooks.entities;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Value class to split a "bookTitle (seriesTitleAndNumber)" into its components.
 */
public class ParsedBookTitle {

    /**
     * Parse "bookTitle (seriesTitleAndNumber)" into "bookTitle" and "seriesTitleAndNumber".
     * <p>
     * group 1: bookTitle
     * group 4: seriesTitleAndNumber
     * We want a title that does not START with a bracket!
     */
    private static final Pattern BOOK_SERIES_PATTERN =
            Pattern.compile("([^(]+.*)\\s\\((.*)\\).*",
                            Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);

    /**
     * Parse "seriesTitle,nr" or "seriesTitle nr" into "seriesTitle" and "nr"
     * <p>
     * group 1: seriesTitle
     * group 4: nr
     */
    private static final Pattern SERIES_AND_NUMBER_PATTERN =
            Pattern.compile("(.*?)(,|\\s)\\s*"
                            + Series.NUMBER_PREFIX_REGEXP + Series.NUMBER_REGEXP,
                            Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
    @NonNull
    private final String mBookTitle;
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

        Matcher matcher = SERIES_AND_NUMBER_PATTERN.matcher(mSeriesTitle);
        if (matcher.find()) {
            mSeriesTitle = matcher.group(1);
            mSeriesNumber = Series.cleanupSeriesNumber(matcher.group(4));
        }
    }

    /**
     * Try to extract a title/series/number from a book title.
     *
     * @param fullTitle Book title to parse
     *
     * @return structure with parsed details of the title, or {@code null}.
     */
    @Nullable
    public static ParsedBookTitle parse(@Nullable final String fullTitle) {
        if (fullTitle == null || fullTitle.isEmpty()) {
            return null;
        }

        Matcher matcher = BOOK_SERIES_PATTERN.matcher(fullTitle);
        if (matcher.find()) {
            String bookTitle = matcher.group(1);
            String seriesTitle = matcher.group(2);
            return new ParsedBookTitle(bookTitle, seriesTitle);
        }
        return null;
    }

    /**
     * @return book title
     */
    @NonNull
    public String getBookTitle() {
        return mBookTitle;
    }

    /**
     * @return series title
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
