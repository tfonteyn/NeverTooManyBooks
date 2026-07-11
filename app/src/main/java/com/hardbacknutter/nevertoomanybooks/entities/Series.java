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
package com.hardbacknutter.nevertoomanybooks.entities;

import android.content.Context;
import android.os.Parcel;
import android.os.Parcelable;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import com.hardbacknutter.nevertoomanybooks.R;
import com.hardbacknutter.nevertoomanybooks.ServiceLocator;
import com.hardbacknutter.nevertoomanybooks.booklist.style.Style;
import com.hardbacknutter.nevertoomanybooks.core.utils.LocaleListUtils;
import com.hardbacknutter.nevertoomanybooks.core.utils.ParcelUtils;
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
 * <p>
 * Not actively used yet, but these are some wikidata claims:
 * <a href="https://www.wikidata.org/wiki/Property:P179">P179: Series</a>
 * <a href="https://www.wikidata.org/wiki/Property:P478">P478: volume nr</a>
 * <p>
 * <a href="https://www.wikidata.org/wiki/Property:P3589">P3589: Grand Comics Database (GCD) id</a>
 * <a href="https://www.wikidata.org/wiki/Property:P5792">P5792: NooSFere</a>
 * <a href="https://www.wikidata.org/wiki/Property:P5905">P5905: Comic Vine</a>
 * <a href="https://www.wikidata.org/wiki/Property:P6947">P6947: Goodreads</a>
 * <a href="https://www.wikidata.org/wiki/Property:P7648">P7648: Overdrive</a>
 * <a href="https://www.wikidata.org/wiki/Property:P8619">P8619: BDGest / Bedetheque?</a>
 * <a href="https://www.wikidata.org/wiki/Property:P10318">P10318: Douban</a>
 */
public class Series
        implements Parcelable, Entity, Mergeable, IdentifierOwner {

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
     * Look for "some text" that does not START with a bracket!
     * <p>
     * The result is parsed a second time as "title" and "number" strings.
     */
    private static final Pattern TEXT1_BR_TEXT2_BR_PATTERN =
            Pattern.compile("([^(]+.*)\\s*\\((.*)\\).*",
                            Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);

    /** Variant of the above, with an additional 3rd part. */
    private static final Pattern TEXT1_BR_TEXT2_BR_TEXT3_PATTERN =
            Pattern.compile("([^(]+.*)\\s*\\((.*)\\)\\s*(.*)\\s*",
                            Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);

    /**
     * Look for titles with embedded digits AS PART OF the title,
     * while there is NO series number.
     * The basic assumptions is that an embedded digit will
     * be formed by a 'letter', followed by the '-' sign, and one or more consecutive
     * digits and does NOT have any additional whitespace followed by a digit.
     */
    private static final Pattern TITLE_WITH_EMBEDDED_DIGITS =
            Pattern.compile(
                    // whitespace at the start
                    "^\\s*"
                    // Capture the title group(1)
                    + "(.*?\\p{L}-\\d+\\s*\\D*)"
                    // whitespace to the end
                    + "\\s*$",
                    Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);


    /**
     * The possible prefixes to a number as seen in the wild.
     * These are stripped out.
     */
    private static final String NUMBER_PREFIX_TO_STRIP =
            "(?:"
            + ",|"
            + "#|"
            + ",\\s*#|"
            + "number|num|num\\.|no|no\\.|nr|nr\\.|"
            + "book|bk|bk\\.|"
            + "volume|vol|vol\\.|"
            + "tome|t\\.|"
            + "part|pt\\.|"
            + "deel|dl\\.|"
            // or none
            + ")"
            // followed by (optional) whitespace
            + "\\s*";

    /**
     * A hierarchically formed number.
     * It optionally starts with a '-' or '+', followed by at least 1 digit,
     * followed by digits mixed with .-+_ characters.
     */
    private static final String HIERARCHICAL_NUMBER =
            "[-+]??\\d[\\d.\\-+_]*?";

    /**
     * A roman number 1..1000+.
     */
    private static final String ROMAN_NUMBER =
            "(?=.)M*(C[MD]|D?C{0,3})(X[CL]|L?X{0,3})(I[XV]|V?I{0,3})";

    /**
     * The main pattern for parsing a series-number string.
     */
    private static final String NUMBER_REGEXP =
            NUMBER_PREFIX_TO_STRIP
            // Capture the number group
            + "("
            // 1st possibility:
            + HIERARCHICAL_NUMBER

            + "|"

            // 2nd possibility:
            + HIERARCHICAL_NUMBER
            // with an optional alphanumeric suffix if separated by a '|'
            + "\\|\\S*?"

            + "|"

            // 3rd possibility:
            + ROMAN_NUMBER
            // with an optional alphanumeric suffix if separated by a '|'
            + "\\|\\S*?"

            + "|"

            // 4th possibility:
            // prefix: a mandatory single whitespace and an optional '('
            + "\\s[(]?"
            + ROMAN_NUMBER
            // suffix: an optional ')' until EOL
            + "[)]?$"

            + ")";

    /**
     * Parse a string into title + number.
     * Formats supported: see unit test for this class.
     *
     * @see #from(String)
     */
    private static final Pattern TITLE_NUMBER_PATTERN = Pattern.compile(
            // whitespace at the start
            "^\\s*"
            // Capture the title group(1)
            + "(.*?)"
            // (optional) whitespace followed by (optional) delimiter ',' and (optional) whitespace
            + "\\s*,*\\s*"
            // Capture the number group(2)
            + NUMBER_REGEXP
            // whitespace to the end
            + "\\s*$",
            Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);

    /**
     * Parse a string into a number.
     * Formats supported: see unit test for this class.
     *
     * @see #from(String, String)
     */
    private static final Pattern NUMBER_PATTERN = Pattern.compile(
            "^\\s*" + NUMBER_REGEXP + "\\s*$",
            Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);

    /**
     * Checks if a number string consists of digits only; i.e. it's a positive integer.
     */
    private static final Pattern PURE_NUMERICAL_PATTERN = Pattern.compile("^\\d+$");

    /** Simple lookup for roman numerals 1..5 to Arabic. */
    private static final Map<String, String> ROMAN_TO_ARABIC = Map.of(
            "I", "1.", "II", "2.", "III", "3", "IV", "4.", "V", "5."
    );

    @NonNull
    private final List<Identifier.Value> identifiers = new ArrayList<>();

    /** Row ID. */
    private long id;
    /** Series title. */
    @NonNull
    private String title = "";
    /** whether we have all we want from this Series / if the Series is finished. */
    private boolean complete;
    /** number (alphanumeric) of a book in this Series. */
    @NonNull
    private String number = "";

    @Nullable
    private PublicationFrequency frequency;

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
        title = rowData.getString(DBKey.SERIES.TITLE);
        complete = rowData.getBoolean(DBKey.SERIES.COMPLETE);

        setIdentifiers(ServiceLocator.getInstance().getSeriesIdentifierDao().getByFkId(this.id));

        // optional domain, not always used.
        if (rowData.contains(DBKey.SERIES.BOOK_SERIES_NUMBER)) {
            number = rowData.getString(DBKey.SERIES.BOOK_SERIES_NUMBER);
        } else {
            number = "";
        }

        if (rowData.contains(DBKey.PUBLICATION_FREQUENCY.TYPE)) {
            frequency = new PublicationFrequency(
                    PublicationFrequency.Type.byId(
                            rowData.getInt(DBKey.PUBLICATION_FREQUENCY.TYPE)),
                    rowData.getInt(DBKey.PUBLICATION_FREQUENCY.CADENCE),
                    rowData.getBoolean(DBKey.PUBLICATION_FREQUENCY.IS_ORDINAL));
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
        //noinspection deprecation
        frequency = in.readParcelable(PublicationFrequency.class.getClassLoader());

        ParcelUtils.readParcelableList(in, identifiers, getClass().getClassLoader());
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
            final String uTitle = matcher.group(1);
            if (uTitle != null) {
                return from(uTitle, matcher.group(2));
            }
        }

        // HORRENDOUS, HORRIBLE HACK...
        if ("Blake's 7".equalsIgnoreCase(text)) {
            return new Series(text);
        }

        // matches only titles without an actual number following them
        matcher = TITLE_WITH_EMBEDDED_DIGITS.matcher(text);
        if (matcher.find()) {
            final String uTitle = StringCoder.unEscape(matcher.group(1));
            return new Series(uTitle);
        }

        matcher = TITLE_NUMBER_PATTERN.matcher(text);
        if (matcher.find()) {
            final String uTitle = StringCoder.unEscape(matcher.group(1));
            final String uNumber = StringCoder.unEscape(matcher.group(2));

            final Series newSeries = new Series(uTitle);
            newSeries.setNumber(normaliseNumber(uNumber));
            return newSeries;

        } else {
            // no number part found
            final String uTitle = StringCoder.unEscape(text);
            return new Series(uTitle);
        }
    }

    /**
     * Variant of {@link Series#from(String)} allowing 3 parts.
     * <p>
     * <strong>Note:</strong> Used by specific search-engines only. We could make this
     * method the default {@link Series#from} but that would add overhead for most sites.
     * <p>
     * "Some Title (I) 12"  ==> "Some Title", "1.12"
     * "Some Title (II) 13"  ==> "Some Title", "2.13"
     * "Some Title (III) 14"  ==> "Some Title", "3.14"
     * "Some Title (Special) 15"  ==> "Some Title (Special)", "15"
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
                    // We explicitly only check for 1..5
                    final String roman = ROMAN_TO_ARABIC.get(middle);
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
            final Matcher matcher = NUMBER_PATTERN.matcher(uNumber);
            if (matcher.find()) {
                newSeries.setNumber(normaliseNumber(matcher.group(1)));
                return newSeries;
            }
        }
        newSeries.setNumber(uNumber);
        return newSeries;
    }

    /**
     * Should be called after all regex parsing is done.
     * This does any final normalisation as needed.
     *
     * @param source to clean
     *
     * @return final resulting number string
     */
    @NonNull
    private static String normaliseNumber(@Nullable final String source) {
        if (source == null) {
            return "";
        }
        String number = source.strip();
        if (number.isEmpty()) {
            return "";
        }

        // We have a number of some sorts.
        // If it's purely numeric, remove any leading zeros.
        if (PURE_NUMERICAL_PATTERN.matcher(number).find()) {
            try {
                number = String.valueOf(Long.parseLong(number));
            } catch (@NonNull final NumberFormatException ignore) {
                // we should never get here... flw
            }
        }
        return number;
    }

    @Override
    public void writeToParcel(@NonNull final Parcel dest,
                              final int flags) {
        dest.writeLong(id);
        dest.writeString(title);
        dest.writeByte((byte) (complete ? 1 : 0));
        dest.writeString(number);
        dest.writeParcelable(frequency, flags);
        ParcelUtils.writeParcelableList(dest, identifiers, flags);
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

    @NonNull
    @Override
    public List<Identifier.Value> getIdentifiers() {
        return identifiers;
    }

    @Override
    public void setIdentifiers(@NonNull final Collection<Identifier.Value> ivs) {
        // The incoming list might be physically OUR list
        // ONLY clear/update if it's not; otherwise no action needed
        if (ivs != identifiers) {
            identifiers.clear();
            identifiers.addAll(ivs);
        }
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
            final ReorderHelper reorderHelper = new ReorderHelper(
                    LocaleListUtils.asList(context.getResources().getConfiguration().getLocales()));
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
     * Get the {@link PublicationFrequency}.
     *
     * @return frequency, or {@code null} for unknown
     */
    @Nullable
    public PublicationFrequency getPublicationFrequency() {
        return frequency;
    }

    /**
     * Set the given {@link PublicationFrequency}.
     * <p>
     * A {@code null} or a type {@link PublicationFrequency.Type#Unknown} will delete it.
     *
     * @param frequency to set
     */
    public void setPublicationFrequency(@Nullable final PublicationFrequency frequency) {
        if (frequency == null || frequency.getType() == PublicationFrequency.Type.Unknown) {
            this.frequency = null;
        } else {
            this.frequency = frequency;
        }
    }

    /**
     * <strong>Replace</strong> local details with those from the given Series.
     *
     * @param source            to copy from
     * @param includeBookFields Flag to force copying the Book related fields as well
     */
    public void copyFrom(@NonNull final Series source,
                         final boolean includeBookFields) {
        title = source.title;
        complete = source.complete;

        identifiers.clear();
        // deep copy
        identifiers.addAll(source.identifiers.stream()
                                             .map(Identifier.Value::new)
                                             .collect(Collectors.toList()));

        if (includeBookFields) {
            number = source.number;
        }
    }

    /**
     * <strong>Merge</strong> local details with those from the given Series.
     * The <em>title</em> is never merged.
     *
     * @param source            to copy from
     * @param includeBookFields Flag to force copying the Book related fields as well
     *
     * @return {@code true} if this Series was modified in any way
     */
    public boolean merge(@NonNull final Series source,
                         final boolean includeBookFields) {

        // If both have a number set, and they are different,
        // abort, we can't merge.
        if (!number.isEmpty() && !source.number.isEmpty()
            && !number.equals(source.number)) {
            return false;
        }

        if (includeBookFields) {
            // If we have one, we 'win'
            if (number.isEmpty()) {
                number = source.number;
            }
        }

        // overwrite the id unless we're 'new'
        if (source.getId() > 0) {
            this.id = source.getId();
        }

        // If we have one, we 'win'
        if (frequency == null) {
            frequency = source.frequency;
        }

        identifiers.addAll(source.getIdentifiers());
        ServiceLocator.getInstance().getIdentifierDao().pruneList(identifiers);

        return true;
    }

    /**
     * Get the Locale for a Series.
     * This is defined as the Locale for the language from the first book in the Series.
     *
     * @param userLocale Current Locale
     *
     * @return the Locale of the Series
     */
    @NonNull
    public Optional<Locale> getLocale(@NonNull final Locale userLocale) {
        //TODO: need a reliable way to cache the Locale here. i.e. store the language of a series.
        if (id <= 0) {
            return Optional.empty();
        }
        return ServiceLocator.getInstance()
                             .getSeriesDao()
                             .getLanguage(id)
                             .flatMap(s -> ServiceLocator.getInstance()
                                                         .getAppLocale()
                                                         .getLocale(s, userLocale));
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
     * Enhanced {@link #equals(Object)}.
     *
     * @param that to compare to
     *
     * @return {@code true} if equals
     */
    public boolean isIdentical(@Nullable final Series that) {
        return equals(that)
               && complete == that.complete
               && number.equals(that.number)
               && identifiers.equals(that.identifiers);
    }

    /**
     * Equality: <strong>id, title</strong>.
     * <ul>
     *   <li>'complete' is a user setting and is ignored here.</li>
     *   <li>'number' is a book field and is ignored here.</li>
     *   <li>'identifiers' is ignored here.</li>
     * </ul>
     *
     * <strong>Comparing is DIACRITIC and CASE SENSITIVE</strong>:
     * This allows correcting case mistakes even with identical ID.
     *
     * @see #isIdentical(Series)
     */
    @Override
    public boolean equals(@Nullable final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final Series that = (Series) o;
        // if both 'exist' but have different ID's -> different.
        if (id != 0 && that.id != 0 && id != that.id) {
            return false;
        }

        // The ids MAY be different, but at least one is != 0
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
               + ", frequency=" + frequency
               + ", identifiers=" + identifiers
               + '}';
    }
}
