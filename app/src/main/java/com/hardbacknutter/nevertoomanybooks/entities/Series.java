/*
 * @Copyright 2018-2024 HardBackNutter
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
package com.hardbacknutter.nevertoomanybooks.entities;

import android.content.Context;
import android.os.Parcel;
import android.os.Parcelable;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.hardbacknutter.nevertoomanybooks.R;
import com.hardbacknutter.nevertoomanybooks.ServiceLocator;
import com.hardbacknutter.nevertoomanybooks.booklist.style.Style;
import com.hardbacknutter.nevertoomanybooks.core.utils.StringCoder;
import com.hardbacknutter.nevertoomanybooks.database.DBDefinitions;
import com.hardbacknutter.nevertoomanybooks.database.DBKey;
import com.hardbacknutter.nevertoomanybooks.utils.ReorderHelper;

/**
 * Represents a Series.
 *
 * <p>
 * <strong>Note:</strong> the Series "number" is a column of {@link DBDefinitions#TBL_BOOK_SERIES}
 * So this class does not strictly represent a Series, but a "BookInSeries" without the book-id...
 * When the number is disregarded, it is a real Series representation.
 * (and I agree, this is NOT good practice).
 * <p>
 * The patterns defined are certainly not foolproof.
 * The extraction of numbers and the meaning of brackets works well enough for books,
 * but a particular pain is the titles/series for comics.
 */
public class Series
        implements Parcelable, Entity, Mergeable {

    /** {@link Parcelable}. */
    public static final Creator<Series> CREATOR = new Creator<>() {
        @Override
        @NonNull
        public Series createFromParcel(@NonNull final Parcel source) {
            return new Series(source);
        }

        @Override
        @NonNull
        public Series[] newArray(final int size) {
            return new Series[size];
        }
    };

    /**
     * Parse "some text (some more text)" into "some text" and "some more text".
     * <p>
     * We want a "some text" that does not START with a bracket!
     */
    private static final Pattern TEXT1_BR_TEXT2_BR_PATTERN =
            Pattern.compile("([^(]+.*)\\s*\\((.*)\\).*",
                            Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);

    /** Variant of the above, with an additional 3rd part. */
    private static final Pattern TEXT1_BR_TEXT2_BR_TEXT3_PATTERN =
            Pattern.compile("([^(]+.*)\\s*\\((.*)\\)\\s*(.*)\\s*",
                            Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);

    private static final String NUMBER_REGEXP =
            // The possible prefixes to a number as seen in the wild.
            "(?:"
            + ",|"
            + "#|"
            + ",\\s*#|"
            + "number|num|num.|no|no.|nr|nr.|"
            + "book|bk|bk.|"
            + "volume|vol|vol.|"
            + "tome|"
            + "part|pt.|"
            + "deel|dl.|"
            // or no prefix
            + ")"
            // whitespace between prefix and actual number
            + "\\s*"
            // Capture the number group
            + "("

            // numeric numerals allowing for .-_ but must start with a digit.
            + /* */ "\\d[\\d.\\-_]*"
            // no alphanumeric suffix

            + "|"

            // numeric numerals allowing for .-_ but must start with a digit.
            + /* */ "\\d[\\d.\\-_]*"
            // optional alphanumeric suffix if separated by a '|'
            + /* */ "\\|\\S*?"

            + "|"

            // roman numerals prefixed by either '(' or whitespace
            + /* */ "\\s[(]?"
            // roman numerals
            + "(?=.)M*(C[MD]|D?C{0,3})(X[CL]|L?X{0,3})(I[XV]|V?I{0,3})"
            //     + /* */ "[ivxlcm.]+"
            // must be suffixed by either ')' or EOL
            // no alphanumeric suffix
            + /* */ "[)]?$"

            + ")";

    /**
     * Parse a string into title + number. Used by {@link #from(String)}.
     * Formats supported: see unit test for this class.
     * <p>
     * FAIL: "Blake's 7" and similar Series titles will fail UNLESS there is an actual number:
     * i.e. "Blake's 7 1" should give "Blake's 7" and number 1
     * but "Blake's 7" will give "Blake's" and number 7
     */
    private static final Pattern TITLE_NUMBER_PATTERN = Pattern.compile(
            // whitespace at the start
            "^\\s*"
            // Capture the title group(1)
            + "(.*?)"
            // delimiter ',' and/or whitespace
            + "\\s*,*\\s*"
            // Capture the number group(2)
            + /* */ NUMBER_REGEXP
            // whitespace to the end
            + "\\s*$",
            Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);

    /**
     * Remove extraneous text from Series number. Used by {@link #from}.
     */
    private static final Pattern NUMBER_CLEANUP_PATTERN =
            Pattern.compile("^\\s*" + NUMBER_REGEXP + "\\s*$",
                            Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);

    /** Remove any leading zeros from Series number. */
    private static final Pattern PURE_NUMERICAL_PATTERN = Pattern.compile("^\\d+$");

    private static final Map<String, String> ROMAN_NUMERALS = Map.of(
            "I", "1.", "II", "2.", "III", "3", "IV", "4.", "V", "5."
    );

    /** Row ID. */
    private long id;
    /** Series title. */
    @NonNull
    private String title;
    /** whether we have all we want from this Series / if the Series is finished. */
    private boolean complete;
    /** number (alphanumeric) of a book in this Series. */
    @NonNull
    private String number;

    /**
     * Copy constructor.
     *
     * @param series            to copy
     * @param includeBookFields Flag to force copying the Book related fields as well
     */
    public Series(@NonNull final Series series,
                  final boolean includeBookFields) {
        copyFrom(series, includeBookFields);
    }

    /**
     * Constructor.
     *
     * @param title of the Series
     */
    public Series(@NonNull final String title) {
        this.title = title;
        number = "";
    }

    /**
     * Full constructor with optional book number.
     *
     * @param id      ID of the Series in the database.
     * @param rowData with data
     */
    public Series(final long id,
                  @NonNull final DataHolder rowData) {
        this.id = id;
        title = rowData.getString(DBKey.SERIES_TITLE);
        complete = rowData.getBoolean(DBKey.SERIES_IS_COMPLETE);
        // optional domain, not always used.
        if (rowData.contains(DBKey.SERIES_BOOK_NUMBER)) {
            number = rowData.getString(DBKey.SERIES_BOOK_NUMBER);
        } else {
            number = "";
        }
    }

    /**
     * {@link Parcelable} Constructor.
     *
     * @param in Parcel to construct the object from
     */
    private Series(@NonNull final Parcel in) {
        id = in.readLong();
        //noinspection DataFlowIssue
        title = in.readString();
        complete = in.readByte() != 0;
        //noinspection DataFlowIssue
        number = in.readString();
    }

    /**
     * Constructor that will attempt to parse a single string into a Series title and number.
     *
     * @param text string to decode
     *
     * @return the Series
     */
    @NonNull
    public static Series from(@NonNull final String text) {
        // First check if we can simplify the decoding.
        // This makes the pattern easier to maintain.
        Matcher matcher = TEXT1_BR_TEXT2_BR_PATTERN.matcher(text);
        if (matcher.find()) {
            final String g1 = matcher.group(1);
            if (g1 != null) {
                return from(g1, matcher.group(2));
            }
        }

        // HORRENDOUS, HORRIBLE HACK...
        if ("Blake's 7".equalsIgnoreCase(text)) {
            return new Series(text);
        }

        // We now know that brackets do NOT separate the number part
        matcher = TITLE_NUMBER_PATTERN.matcher(text);
        if (matcher.find()) {

            final String uTitle = StringCoder.unEscape(matcher.group(1));
            String uNumber = StringCoder.unEscape(matcher.group(2));

            final Series newSeries = new Series(uTitle);
            // If it's purely numeric, remove any leading zeros.
            if (PURE_NUMERICAL_PATTERN.matcher(uNumber).find()) {
                uNumber = String.valueOf(Long.parseLong(uNumber));
            }
            newSeries.setNumber(uNumber);
            return newSeries;

        } else {
            // no number part found
            final String uTitle = StringCoder.unEscape(text.trim());
            return new Series(uTitle);
        }
    }

    /**
     * Variant of {@link Series#from(String)} allowing 3 parts.
     * <p>
     * "Some Title (I) 12"  ==> "Some Title", "1.12"
     * "Some Title (II) 13"  ==> "Some Title", "2.13"
     * "Some Title (III) 14"  ==> "Some Title", "3.14"
     * "Some Title (Special) 15"  ==> "Some Title (Special)", "15"
     * <p>
     * <strong>Note:</strong> we could make this method the default {@link Series#from}
     * but that would add overhead for most sites.
     *
     * @param text string to decode
     *
     * @return the Series
     */
    @NonNull
    public static Series from3(@NonNull final String text) {
        final Series series;

        // Detect "title (middle) number" and "title (number)"
        final Matcher matcher = TEXT1_BR_TEXT2_BR_TEXT3_PATTERN.matcher(text);
        if (matcher.find()) {
            final String prefix = matcher.group(1);
            final String middle = matcher.group(2);
            final String suffix = matcher.group(3);

            if (prefix != null) {
                if (suffix != null && !suffix.isEmpty()) {
                    // the suffix group is the number.
                    series = from(prefix, suffix);

                    // Cover a special case were the middle group is potentially
                    // a roman numeral which should be prefixed to the number.
                    final String roman = ROMAN_NUMERALS.get(middle);
                    if (roman != null) {
                        series.setNumber(roman + series.getNumber());
                    } else {
                        // But if it wasn't... add it back to the title including
                        // the brackets we stripped off initially.
                        series.setTitle(prefix + '(' + middle + ')');
                    }
                    return series;

                } else {
                    // the middle group is the number.
                    return from(prefix, middle);
                }
            }
        }

        // didn't match the specific pattern, handle as normal.
        return from(text);
    }

    /**
     * Constructor that will attempt to parse a number.
     *
     * @param title  for the Series; used as is.
     * @param number (optional) number for the Series; will get cleaned up.
     *
     * @return the Series
     */
    @NonNull
    public static Series from(@NonNull final String title,
                              @Nullable final String number) {
        final String uTitle = StringCoder.unEscape(title);
        final String uNumber = StringCoder.unEscape(number);

        final Series newSeries = new Series(uTitle);
        if (!uNumber.isEmpty()) {
            final Matcher matcher = NUMBER_CLEANUP_PATTERN.matcher(uNumber);
            if (matcher.find()) {
                String cleanNumber = matcher.group(1);
                if (cleanNumber != null && !cleanNumber.isEmpty()) {
                    // If it's purely numeric, remove any leading zeros.
                    if (PURE_NUMERICAL_PATTERN.matcher(cleanNumber).find()) {
                        try {
                            cleanNumber = String.valueOf(Long.parseLong(cleanNumber));
                        } catch (@NonNull final NumberFormatException ignore) {
                            // ignore
                        }
                    }
                    newSeries.setNumber(cleanNumber);
                } else {
                    newSeries.setNumber(uNumber);
                }
            } else {
                newSeries.setNumber(uNumber);
            }
        }

        return newSeries;
    }

    /**
     * Helper method.
     * <p>
     * Look for a book title; if present try to get a Series from it and clean the book title.
     * <p>
     * TODO: we probably call this from some SearchEngine's that don't need it.
     *
     * @param book to process
     */
    public static void checkForSeriesNameInTitle(@NonNull final Book book) {
        final String fullTitle = book.getString(DBKey.TITLE, null);
        if (fullTitle != null && !fullTitle.isEmpty()) {
            final Matcher matcher = TEXT1_BR_TEXT2_BR_PATTERN.matcher(fullTitle);
            if (matcher.find()) {
                // the cleansed title
                final String bookTitle = matcher.group(1);
                if (bookTitle != null) {
                    // the series title/number
                    final String seriesTitleWithNumber = matcher.group(2);

                    if (seriesTitleWithNumber != null && !seriesTitleWithNumber.isEmpty()) {
                        // add to the TOP of the list.
                        book.add(0, from(seriesTitleWithNumber));

                        // and store cleansed book title back
                        book.putString(DBKey.TITLE, bookTitle);
                    }
                }
            }
        }
    }

    @Override
    public void writeToParcel(@NonNull final Parcel dest,
                              final int flags) {
        dest.writeLong(id);
        dest.writeString(title);
        dest.writeByte((byte) (complete ? 1 : 0));
        dest.writeString(number);
    }

    @SuppressWarnings("SameReturnValue")
    @Override
    public int describeContents() {
        return 0;
    }

    /**
     * Get the 'complete' status of the Series.
     *
     * @return {@code true} if the Series is complete
     */
    public boolean isComplete() {
        return complete;
    }

    /**
     * Set the 'complete' status of the Series.
     *
     * @param isComplete Flag indicating the user considers this Series to be 'complete'
     */
    public void setComplete(final boolean isComplete) {
        complete = isComplete;
    }

    @Override
    public long getId() {
        return id;
    }

    @Override
    public void setId(final long id) {
        this.id = id;
    }

    /**
     * Get the user visible title.
     *
     * @param context Current context
     *
     * @return "title" or "title (nr)"
     */
    @Override
    @NonNull
    public String getLabel(@NonNull final Context context,
                           @Nullable final Details details,
                           @NonNull final Style style) {

        final String label;
        if (style.isShowReorderedTitle()) {
            final ReorderHelper reorderHelper = ServiceLocator.getInstance().getReorderHelper();
            // Using the locale here is overkill;  see #getLocale(..)
            label = reorderHelper.reorder(context, title);
        } else {
            label = title;
        }

        if (number.isEmpty()) {
            return label;
        } else {
            return context.getString(R.string.a_bracket_b_bracket, label, number);
        }
    }

    /**
     * Get the <strong>unformatted</strong> title.
     *
     * @return the title
     */
    @NonNull
    public String getTitle() {
        return title;
    }

    /**
     * Set the unformatted title; as entered manually by the user.
     *
     * @param title to use
     */
    public void setTitle(@NonNull final String title) {
        this.title = title;
    }

    /**
     * Get the unformatted number.
     *
     * @return number (as a string)
     */
    @NonNull
    public String getNumber() {
        return number;
    }

    /**
     * Set the unformatted number; as entered manually by the user.
     *
     * @param number to use, a {@code null} is replaced by "".
     */
    public void setNumber(@Nullable final String number) {
        this.number = Objects.requireNonNullElse(number, "");
    }

    /**
     * Replace local details from another Series.
     *
     * @param source            Series to copy from
     * @param includeBookFields Flag to force copying the Book related fields as well
     */
    public void copyFrom(@NonNull final Series source,
                         final boolean includeBookFields) {
        title = source.title;
        complete = source.complete;
        if (includeBookFields) {
            number = source.number;
        }
    }

    /**
     * Get the Locale for a Series.
     * This is defined as the Locale for the language from the first book in the Series.
     *
     * @param context Current context
     *
     * @return the Locale of the Series
     */
    @NonNull
    public Optional<Locale> getLocale(@NonNull final Context context) {
        //TODO: need a reliable way to cache the Locale here. i.e. store the language of a series.
        if (id > 0) {
            final String lang = ServiceLocator.getInstance().getSeriesDao().getLanguage(id);
            if (!lang.isEmpty()) {
                return ServiceLocator.getInstance().getAppLocale().getLocale(context, lang);
            }
        }

        return Optional.empty();
    }

    @NonNull
    @Override
    public List<String> getNameFields() {
        return List.of(title);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, title);
    }

    /**
     * {@link #equals(Object)} checks the basic fields.
     * This method additionally checks any user and book fields.
     *
     * @param that to compare to
     *
     * @return {@code true} if <strong>all</strong> fields are equal
     */
    public boolean isIdentical(@Nullable final Series that) {
        return equals(that)
               && complete == that.complete
               && Objects.equals(number, that.number);
    }

    /**
     * Equality: <strong>id, title</strong>.
     * <ul>
     *   <li>'isComplete' is a user setting and is ignored here.</li>
     *   <li>'number' is a book field and is ignored here.</li>
     * </ul>
     *
     * <strong>Comparing is DIACRITIC and CASE SENSITIVE</strong>:
     * This allows correcting case mistakes even with identical ID.
     *
     * @see #isIdentical(Series)
     */
    @Override
    public boolean equals(@Nullable final Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj == null || getClass() != obj.getClass()) {
            return false;
        }
        final Series that = (Series) obj;
        // if both 'exist' but have different ID's -> different.
        if (id != 0 && that.id != 0 && id != that.id) {
            return false;
        }
        return Objects.equals(title, that.title);
    }

    @Override
    @NonNull
    public String toString() {
        return "Series{"
               + "id=" + id
               + ", title=`" + title + '`'
               + ", complete=" + complete
               + ", number=`" + number + '`'
               + '}';
    }
}
