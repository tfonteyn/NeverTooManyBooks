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
package com.hardbacknutter.nevertoomanybooks.backup.csv;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

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
 * as used by the CSV format.
 * <p>
 * This extends the internally used #fromString(String) constructor.
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
    /** pre-configured coder/decoder for Author elements. */
    @Nullable
    private static StringList<Author> sAuthorUtils;
    /** pre-configured coder/decoder for Series elements. */
    @Nullable
    private static StringList<Series> sSeriesUtils;
    /** pre-configured coder/decoder for TocEntry elements. */
    @Nullable
    private static StringList<TocEntry> sTocUtils;
    /** pre-configured  coder/decoder for Bookshelf elements. */
    @Nullable
    private static StringList<Bookshelf> sBookshelfUtils;

    /**
     * StringList factory for a Author.
     * <ul>Format:
     * <li>authorName * {json}</li>
     * </ul>
     * <ul>With authorName:
     * <li>writing out: "family, givenNames"</li>
     * <li>reading in: see {@link Author#fromString(String)}</li>
     * </ul>
     *
     * @return StringList factory
     */
    @NonNull
    static StringList<Author> getAuthorCoder() {
        if (sAuthorUtils == null) {
            sAuthorUtils = new StringList<>(new StringList.Factory<Author>() {
                @Override
                @NonNull
                public Author decode(@NonNull final String encodedString) {
                    List<String> elements =
                            new StringList<String>().decodeElementList(encodedString);
                    Author author = Author.fromString(elements.get(0));
                    if (elements.size() > 1) {
                        try {
                            JSONObject details = new JSONObject(elements.get(1));
                            author.setComplete(details.optBoolean("complete"));
                            author.setType(details.optInt("type"));
                        } catch (@NonNull final JSONException ignore) {
                        }
                    }
                    return author;
                }

                @NonNull
                @Override
                public String encode(@NonNull final Author author) {
                    // Note the use of Author.NAME_SEPARATOR between family and given-names,
                    // i.e. the names are considered ONE field with a private separator.
                    String s = escapeListItem(author.getFamilyName(), Author.NAME_SEPARATOR, ' ')
                               + Author.NAME_SEPARATOR + ' '
                               + escapeListItem(author.getGivenNames(), Author.NAME_SEPARATOR);

                    JSONObject details = new JSONObject();
                    try {
                        if (author.isComplete()) {
                            details.put("complete", author.isComplete());
                        }
                        if (author.getType() != 0) {
                            details.put("type", author.getType());
                        }
                    } catch (@NonNull final JSONException e) {
                        throw new IllegalStateException(e);
                    }
                    if (details.length() != 0) {
                        s += ' ' + String.valueOf(getFieldSeparator()) + ' ' + details.toString();
                    }
                    return s;
                }
            });
        }
        return sAuthorUtils;
    }

    /**
     * StringList factory for a Series.
     * <ul>Format:
     * <li>title (number) * {json}</li>
     * <li>title * {json}</li>
     * </ul>
     * number: alpha-numeric, a proposed format is "1","1.0","1a", "1|omnibus" etc.
     * i.e. starting with a number (int or float) with optional alphanumeric characters trailing.
     *
     * @return StringList factory
     */
    @NonNull
    static StringList<Series> getSeriesCoder() {
        if (sSeriesUtils == null) {
            sSeriesUtils = new StringList<>(new StringList.Factory<Series>() {
                @Override
                @NonNull
                public Series decode(@NonNull final String encodedString) {
                    List<String> elements =
                            new StringList<String>().decodeElementList(encodedString);
                    Series series = Series.fromString(elements.get(0));
                    if (elements.size() > 1) {
                        try {
                            JSONObject details = new JSONObject(elements.get(1));
                            series.setComplete(details.optBoolean("complete"));
                        } catch (@NonNull final JSONException ignore) {
                        }
                    }
                    return series;
                }

                @NonNull
                @Override
                public String encode(@NonNull final Series series) {
                    String numberStr;
                    if (!series.getNumber().isEmpty()) {
                        // start with a space for readability
                        // the surrounding () are NOT escaped as they are part of the format.
                        numberStr = " ("
                                    + escapeListItem(series.getNumber(), '(')
                                    + ')';
                    } else {
                        numberStr = "";
                    }
                    String s = escapeListItem(series.getTitle(), '(') + numberStr;

                    JSONObject details = new JSONObject();
                    try {
                        if (series.isComplete()) {
                            details.put("complete", series.isComplete());
                        }
                    } catch (@NonNull final JSONException e) {
                        throw new IllegalStateException(e);
                    }
                    if (details.length() != 0) {
                        s += ' ' + String.valueOf(getFieldSeparator()) + ' ' + details.toString();
                    }
                    return s;
                }
            });
        }
        return sSeriesUtils;
    }

    /**
     * StringList factory for a TocEntry.
     * <ul>Format:
     * <li>title (date) * authorName * {json}</li>
     * <li>title * authorName * {json}</li>
     * </ul>
     * authorName: see {@link #getAuthorCoder()}
     * date: see {@link #DATE_PATTERN}.
     *
     * @return StringList factory
     */
    @NonNull
    static StringList<TocEntry> getTocCoder() {
        if (sTocUtils == null) {
            sTocUtils = new StringList<>(new StringList.Factory<TocEntry>() {
                /**
                 * Attempts to parse a single string into an TocEntry.
                 * <ul>The date *must* match a patter of a (partial) SQL date string:
                 * <li>(YYYY)</li>
                 * <li>(YYYY-MM)</li>
                 * <li>(YYYY-MM-DD)</li>
                 * <li>(YYYY-DD-MM) might work depending on the user's locale. Not tested.</li>
                 * </ul>
                 * v82 had no dates: Giants In The Sky * Blish, James
                 * <ul>V200+ also accepts:
                 * <li>Giants In The Sky (1952) * Blish, James</li>
                 * <li>Giants In The Sky (1952-03) * Blish, James</li>
                 * <li>Giants In The Sky (1952-03-22) * Blish, James</li>
                 * </ul>
                 */
                @Override
                @NonNull
                public TocEntry decode(@NonNull final String encodedString) {
                    List<String> elements =
                            new StringList<String>().decodeElementList(encodedString);
                    String title = elements.get(0);
                    Author author = Author.fromString(elements.get(1));

                    Matcher matcher = DATE_PATTERN.matcher(title);
                    if (matcher.find()) {
                        // strip out the found pattern (including the brackets)
                        title = title.replace(matcher.group(0), "").trim();
                        return new TocEntry(author, title, matcher.group(1));
                    } else {
                        return new TocEntry(author, title, "");
                    }
                }

                @NonNull
                @Override
                public String encode(@NonNull final TocEntry tocEntry) {
                    String yearStr;
                    if (!tocEntry.getFirstPublication().isEmpty()) {
                        // start with a space for readability
                        yearStr = " (" + tocEntry.getFirstPublication() + ')';
                    } else {
                        yearStr = "";
                    }
                    return escapeListItem(tocEntry.getTitle(), '(')
                           + yearStr
                           + ' ' + String.valueOf(getFieldSeparator()) + ' '
                           + getAuthorCoder().encodeElement(tocEntry.getAuthor());
                }
            });
        }
        return sTocUtils;
    }

    /**
     * StringList factory for a Bookshelf.
     * <ul>Format:
     * <li>shelfName * {json}</li>
     * </ul>
     *
     * @return StringList factory
     */
    @NonNull
    static StringList<Bookshelf> getBookshelfCoder() {
        if (sBookshelfUtils == null) {
            sBookshelfUtils = new StringList<>(new StringList.Factory<Bookshelf>() {

                /**
                 * Backwards compatibility rules ',' (not using the default '|').
                 */
                @Override
                public char getListSeparator() {
                    return ',';
                }

                @Override
                @NonNull
                public Bookshelf decode(@NonNull final String encodedString) {
                    List<String> elements =
                            new StringList<String>().decodeElementList(encodedString);
                    String name = elements.get(0);
                    String uuid = null;
                    if (elements.size() > 1) {
                        try {
                            JSONObject details = new JSONObject(elements.get(1));
                            uuid = details.optString("style");
                            // it's quite possible that the UUID is not a style we (currently)
                            // know. But that does not matter as we'll check it upon first access.
                        } catch (@NonNull final JSONException ignore) {
                        }
                    }

                    if (uuid != null && !uuid.isEmpty()) {
                        return new Bookshelf(name, uuid);
                    }
                    // the right thing to do would be: get a database, then get the 'real'
                    // default style. As this is a lot of overkill for importing,
                    // we're just using the builtin default.
                    return new Bookshelf(name, BooklistStyle.Builtin.DEFAULT);
                }

                @NonNull
                @Override
                public String encode(@NonNull final Bookshelf bookshelf) {
                    String s = escapeListItem(bookshelf.getName());

                    JSONObject details = new JSONObject();
                    try {
                        if (!bookshelf.getStyleUuid().isEmpty()) {
                            details.put("style", bookshelf.getStyleUuid());
                        }
                    } catch (@NonNull final JSONException e) {
                        throw new IllegalStateException(e);
                    }

                    if (details.length() != 0) {
                        s += ' ' + String.valueOf(getFieldSeparator()) + ' ' + details.toString();
                    }
                    return s;
                }
            });
        }
        return sBookshelfUtils;
    }

    private CsvCoder() {
    }
}
