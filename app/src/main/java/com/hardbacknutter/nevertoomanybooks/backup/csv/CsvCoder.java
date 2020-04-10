/*
 * @Copyright 2020 HardBackNutter
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
package com.hardbacknutter.nevertoomanybooks.backup.csv;

import androidx.annotation.NonNull;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.json.JSONException;
import org.json.JSONObject;

import com.hardbacknutter.nevertoomanybooks.booklist.BooklistStyle;
import com.hardbacknutter.nevertoomanybooks.entities.Author;
import com.hardbacknutter.nevertoomanybooks.entities.Bookshelf;
import com.hardbacknutter.nevertoomanybooks.entities.Series;
import com.hardbacknutter.nevertoomanybooks.entities.TocEntry;
import com.hardbacknutter.nevertoomanybooks.utils.StringList;

/**
 * Provides encoding/decoding of specific objects to/from a string format
 * as used by the CSV format for <strong>export/import</strong>.
 * <p>
 * This extends the internally used #from(String) constructor.
 *
 * <strong>Note:</strong> In the format definition, the " * {json}" suffix is optional
 * and can be missing.
 */
final class CsvCoder {

    /**
     * Find the publication year in a string like "some title (1978-04-22)".
     * <p>
     * The pattern finds (1987), (1978-04) or (1987-04-22)
     * Result is found in group 1.
     */
    private static final Pattern DATE_PATTERN = Pattern.compile("\\("
                                                                + "([1|2]\\d\\d\\d"
                                                                + "|[1|2]\\d\\d\\d-\\d\\d"
                                                                + "|[1|2]\\d\\d\\d-\\d\\d-\\d\\d)"
                                                                + "\\)");

    private CsvCoder() {
    }

    /**
     * StringList factory for a Author.
     * <ul>Format:
     *      <li>authorName * {json}</li>
     * </ul>
     * <ul>With authorName:
     *      <li>writing out: "family, givenNames"</li>
     *      <li>reading in: see {@link Author#from(String)}</li>
     * </ul>
     *
     * @return StringList factory
     */
    @NonNull
    static StringList<Author> getAuthorCoder() {
        return new StringList<>(new StringList.Factory<Author>() {
            private final char[] escapeChars = {Author.NAME_SEPARATOR, ' ', '(', ')'};

            @Override
            @NonNull
            public Author decode(@NonNull final String element) {
                List<String> parts = StringList.newInstance().decodeElement(element);
                Author author = Author.from(parts.get(0));
                if (parts.size() > 1) {
                    try {
                        JSONObject details = new JSONObject(parts.get(1));
                        author.fromJson(details);
                    } catch (@NonNull final JSONException ignore) {
                        // ignore
                    }
                }
                return author;
            }

            @NonNull
            @Override
            public String encode(@NonNull final Author author) {
                // Note the use of Author.NAME_SEPARATOR between family and given-names,
                // i.e. the names are considered ONE field with a private separator.
                String result =
                        escape(author.getFamilyName(), escapeChars)
                        + Author.NAME_SEPARATOR + ' '
                        + escape(author.getGivenNames(), escapeChars);

                JSONObject details = new JSONObject();
                try {
                    author.toJson(details);
                } catch (@NonNull final JSONException e) {
                    throw new IllegalStateException(e);
                }

                if (details.length() != 0) {
                    result += ' ' + String.valueOf(getObjectSeparator())
                              + ' ' + details.toString();
                }
                return result;
            }
        });
    }

    /**
     * StringList factory for a Series.
     * <ul>Format:
     *      <li>title (number) * {json}</li>
     *      <li>title * {json}</li>
     * </ul>
     * number: alpha-numeric, a proposed format is "1","1.0","1a", "1|omnibus" etc.
     * i.e. starting with a number (int or float) with optional alphanumeric characters trailing.
     *
     * @return StringList factory
     */
    @NonNull
    static StringList<Series> getSeriesCoder() {
        return new StringList<>(new StringList.Factory<Series>() {
            private final char[] escapeChars = {'(', ')'};

            @Override
            @NonNull
            public Series decode(@NonNull final String element) {
                List<String> parts = StringList.newInstance().decodeElement(element);
                Series series = Series.from(parts.get(0));
                if (parts.size() > 1) {
                    try {
                        JSONObject details = new JSONObject(parts.get(1));
                        series.fromJson(details);
                    } catch (@NonNull final JSONException ignore) {
                        // ignore
                    }
                }
                return series;
            }

            @NonNull
            @Override
            public String encode(@NonNull final Series series) {
                String result = escape(series.getTitle(), escapeChars);
                if (!series.getNumber().isEmpty()) {
                    // start with a space for readability
                    // the surrounding () are NOT escaped as they are part of the format.
                    result += " (" + escape(series.getNumber(), escapeChars) + ')';
                }

                JSONObject details = new JSONObject();
                try {
                    series.toJson(details);
                } catch (@NonNull final JSONException e) {
                    throw new IllegalStateException(e);
                }

                if (details.length() != 0) {
                    result += ' ' + String.valueOf(getObjectSeparator())
                              + ' ' + details.toString();
                }
                return result;
            }
        });
    }

    /**
     * StringList factory for a TocEntry.
     * <ul>Format:
     *      <li>title (date) * authorName * {json}</li>
     *      <li>title * authorName * {json}</li>
     * </ul>
     * authorName: see {@link #getAuthorCoder()}
     * date: see {@link #DATE_PATTERN}.
     *
     * @return StringList factory
     */
    @NonNull
    static StringList<TocEntry> getTocCoder() {
        return new StringList<>(new StringList.Factory<TocEntry>() {
            private final char[] escapeChars = {'(', ')'};

            /**
             * Attempts to parse a single string into an TocEntry.
             * <ul>The date *must* match a patter of a (partial) SQL date string:
             *      <li>(YYYY)</li>
             *      <li>(YYYY-MM)</li>
             *      <li>(YYYY-MM-DD)</li>
             *      <li>(YYYY-DD-MM) might work depending on the user's Locale. Not tested.</li>
             * </ul>
             * BookCatalogue had no dates: Giants In The Sky * Blish, James
             * <ul>We now also accept:
             *      <li>Giants In The Sky (1952) * Blish, James</li>
             *      <li>Giants In The Sky (1952-03) * Blish, James</li>
             *      <li>Giants In The Sky (1952-03-22) * Blish, James</li>
             * </ul>
             */
            @Override
            @NonNull
            public TocEntry decode(@NonNull final String element) {
                List<String> parts = StringList.newInstance().decodeElement(element);
                String title = parts.get(0);
                Author author = Author.from(parts.get(1));

                Matcher matcher = DATE_PATTERN.matcher(title);
                if (matcher.find()) {
                    String g1 = matcher.group(0);
                    if (g1 != null) {
                        // strip out the found pattern (including the brackets)
                        title = title.replace(g1, "").trim();
                        return new TocEntry(author, title, matcher.group(1));
                    }
                }
                return new TocEntry(author, title, "");
            }

            @NonNull
            @Override
            public String encode(@NonNull final TocEntry tocEntry) {
                String result = escape(tocEntry.getTitle(), escapeChars);

                if (!tocEntry.getFirstPublication().isEmpty()) {
                    // start with a space for readability
                    // the surrounding () are NOT escaped as they are part of the format.
                    result += " (" + tocEntry.getFirstPublication() + ')';
                }

                return result
                       + ' ' + getObjectSeparator()
                       // we only use the name here
                       + ' ' + getAuthorCoder().encodeElement(tocEntry.getAuthor());
            }
        });
    }


    @NonNull
    static StringList<Bookshelf> getBookshelfCoder(@NonNull final BooklistStyle defaultStyle) {
        return new StringList<>(new BookshelfCoderFactory(defaultStyle));
    }

    /**
     * StringList factory for a Bookshelf.
     * <ul>Format:
     *      <li>shelfName * {json}</li>
     * </ul>
     */
    public static class BookshelfCoderFactory
            implements StringList.Factory<Bookshelf> {

        @NonNull
        private final BooklistStyle defaultStyle;
        @NonNull
        private final char[] escapeChars = {'(', ')'};

        /**
         * Constructor.
         *
         * @param defaultStyle to use for bookshelves without a style set.
         */
        BookshelfCoderFactory(@NonNull final BooklistStyle defaultStyle) {
            this.defaultStyle = defaultStyle;
        }

        /**
         * Backwards compatibility rules ',' (not using the default '|').
         */
        @Override
        public char getElementSeparator() {
            return ',';
        }

        @Override
        @NonNull
        public Bookshelf decode(@NonNull final String element) {
            List<String> parts = StringList.newInstance().decodeElement(element);
            Bookshelf bookshelf = new Bookshelf(parts.get(0), defaultStyle);
            if (parts.size() > 1) {
                try {
                    JSONObject details = new JSONObject(parts.get(1));
                    bookshelf.fromJson(details);
                } catch (@NonNull final JSONException ignore) {
                    // ignore
                }
            }
            return bookshelf;
        }

        @NonNull
        @Override
        public String encode(@NonNull final Bookshelf bookshelf) {
            String result = escape(bookshelf.getName(), escapeChars);

            JSONObject details = new JSONObject();
            try {
                bookshelf.toJson(details);
            } catch (@NonNull final JSONException e) {
                throw new IllegalStateException(e);
            }

            if (details.length() != 0) {
                result += ' ' + String.valueOf(getObjectSeparator())
                          + ' ' + details.toString();
            }
            return result;
        }

    }
}
