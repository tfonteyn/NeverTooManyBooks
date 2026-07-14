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

package com.hardbacknutter.nevertoomanybooks.searchengines;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.hardbacknutter.nevertoomanybooks.database.DBKey;
import com.hardbacknutter.nevertoomanybooks.entities.Book;
import com.hardbacknutter.nevertoomanybooks.entities.Series;

import org.jsoup.nodes.Element;
import org.jsoup.nodes.Node;

public final class SearchEngineUtils {

    /** All non-rendering characters to REMOVE. */
    private static final Pattern P1_INVISIBLE_CONTROLS = Pattern.compile(
            // Category 'Format' (includes Bidi, Zero-Width joiners/non-joiners, etc.)
            // Category 'Private Use'
            // Category 'Not Assigned' (Unassigned characters)
            "\\p{Cf}|\\p{Co}|\\p{Cn}"
    );

    /** Non-standard whitespace to replace with a space. */
    private static final Pattern P2_ODD_WHITESPACE = Pattern.compile(
            "["
            // Non-Breaking Spaces
            + "\u00A0\u202F\u205F"
            // Quad/Figure Spaces
            + "\u2000-\u200A"
            // Ideographic Space
            + "\u3000"
            // Zero-Width No-Break Space (acts like a space)
            + "\uFEFF"
            + "]+"
    );

    /** Replace repeated/special whitespace characters with a single space. */
    private static final Pattern P3_WHITESPACE_REDUCTION = Pattern.compile("\\s+");

    /** Fields can contain div tags which we remove to make the text shorter. */
    private static final Pattern DIV_PATTERN = Pattern.compile("(\n*\\s*<div>\\s*|\\s*</div>)");
    /** Convert "&amp;" to '&'. */
    private static final Pattern AMPERSAND_LITERAL = Pattern.compile("&amp;", Pattern.LITERAL);
    /** a CR is replaced with a space. */
    private static final Pattern CR_LITERAL = Pattern.compile("\n", Pattern.LITERAL);


    /**
     * Trim extraneous punctuation from the end of titles, authors and other types of names.
     *
     * @see #cleanName(String)
     */
    private static final Pattern END_PUNCTUATION_PATTERN =
            Pattern.compile("[,.':;`~@#$%^&*(\\-=_+]*$");

    /** Keep only alpha/digit and space characters. */
    private static final Pattern KEEP_PATTERN = Pattern.compile("[^\\p{Alpha}\\d ]");

    /**
     * Used to parse a book title and derive a series name/number from it.
     * <p>
     * Parse "some text (some more text)" into "some text" and "some more text".
     * Look for "some text" that does not START with a bracket!
     * <p>
     * The result is parsed a second time as "title" and "number" strings.
     */
    private static final Pattern SERIES_FROM_BOOK_TITLE_PATTERN =
            Pattern.compile("([^(]+.*)\\s*\\((.*)\\).*",
                            Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);

    private SearchEngineUtils() {
    }

    /**
     * Filter a string of all non-digits.
     *
     * @param s string to parse
     *
     * @return stripped string
     */
    @NonNull
    public static String digits(@Nullable final CharSequence s) {
        if (s == null || s.length() == 0) {
            return "";
        }
        final StringBuilder sb = new StringBuilder();
        for (int i = 0; i < s.length(); i++) {
            final char c = s.charAt(i);
            if (Character.isDigit(c)) {
                sb.append(c);
            }
        }
        // ... but let empty Strings here just return.
        return sb.toString();
    }

    /**
     * Clean the given text.
     * <p>
     * Currently cleans up {@code &}, {@code div} and {@code \n}
     * and a range of Unicode control characters.
     *
     * @param s to clean
     *
     * @return cleansed string
     */
    @NonNull
    public static String cleanText(@NonNull final String s) {
        String text = s;
        // Replace any cr/lf with a single space
        text = CR_LITERAL.matcher(text).replaceAll(" ");

        // Remove all unwanted invisible chars
        text = P1_INVISIBLE_CONTROLS.matcher(text).replaceAll("");
        // Replace all special whitespace with a regular single space.
        text = P2_ODD_WHITESPACE.matcher(text).replaceAll(" ");
        // Reduce all remaining multiple whitespace
        text = P3_WHITESPACE_REDUCTION.matcher(text).replaceAll(" ");

        // Special rules:

        // replace any HTML encoded ampersands.
        if (text.contains("&")) {
            text = AMPERSAND_LITERAL.matcher(text).replaceAll(Matcher.quoteReplacement("&"));
        }

        // div elements only create empty lines, we remove them to save screen space
        if (text.contains("<div>")) {
            text = DIV_PATTERN.matcher(text).replaceAll("");
        }

        // add more rules when needed.

        return text.strip();
    }

    @NonNull
    public static String cleanText(@NonNull final Node node) {
        return cleanText(node.toString());
    }

    @NonNull
    public static String cleanText(@NonNull final Element element) {
        return cleanText(element.text());
    }

    /**
     * Variant of {@link #cleanText(String)} which does additional clean-up
     * specific to author names and titles.
     *
     * @param s to clean
     *
     * @return cleansed string
     */
    @NonNull
    public static String cleanName(@NonNull final String s) {
        final String text = cleanText(s);
        // remove any junk characters from the end of the string
        return END_PUNCTUATION_PATTERN.matcher(text).replaceAll("").strip();
    }

    @NonNull
    public static String cleanName(@NonNull final Element element) {
        return cleanName(element.text());
    }

    /**
     * Sanitise a parameter string for use in a url search.
     *
     * @param s to clean
     *
     * @return cleansed string
     */
    @NonNull
    public static String encodeSearchString(@Nullable final String s) {
        if (s == null || s.isEmpty()) {
            return "";
        }

        String text = s;
        // Replace all special whitespace with a regular single space.
        text = P2_ODD_WHITESPACE.matcher(text).replaceAll(" ");
        // Reduce all remaining multiple whitespace
        text = P3_WHITESPACE_REDUCTION.matcher(text).replaceAll(" ");
        // And keep only alphanumeric characters and the space
        return KEEP_PATTERN.matcher(text).replaceAll("");
    }

    /**
     * Look for a book title; if present try to get a {@link Series} from it
     * and clean up the book title.
     * <p>
     * The pattern we look for:  "Book title (series and number)"
     * as we've seen this used on a number of websites.
     * <p>
     * We do <strong>NOT</strong> look for "title #123" style pattern
     * as this is typically used for a magazine and issue number.
     * <p>
     * TODO: we probably call this from some SearchEngine's that don't need it.
     *
     * @param book to process
     */
    public static void parseSeriesNameInTitle(@NonNull final Book book) {
        final String fullTitle = book.getString(DBKey.TITLE, null);
        if (fullTitle == null || fullTitle.isEmpty()) {
            return;
        }
        final Matcher matcher = SERIES_FROM_BOOK_TITLE_PATTERN.matcher(fullTitle);
        if (!matcher.find()) {
            return;
        }
        // the cleansed title
        final String bookTitle = matcher.group(1);
        // the series title/number
        final String seriesTitleWithNumber = matcher.group(2);
        // Sanity check
        if (bookTitle == null || seriesTitleWithNumber == null || seriesTitleWithNumber.isEmpty()) {
            return;
        }

        // Set the cleansed book title
        book.setTitle(bookTitle);
        // and add the series to the TOP of the list.
        book.add(0, Series.from(seriesTitleWithNumber));

    }
}
