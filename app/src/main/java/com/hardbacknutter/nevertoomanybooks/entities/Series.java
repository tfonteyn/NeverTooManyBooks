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
package com.hardbacknutter.nevertoomanybooks.entities;

import android.content.Context;
import android.os.Parcel;
import android.os.Parcelable;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.json.JSONException;
import org.json.JSONObject;

import com.hardbacknutter.nevertoomanybooks.database.DAO;
import com.hardbacknutter.nevertoomanybooks.database.DBDefinitions;
import com.hardbacknutter.nevertoomanybooks.utils.LocaleUtils;
import com.hardbacknutter.nevertoomanybooks.utils.ParseUtils;

/**
 * Class to hold book-related Series data.
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
        implements Parcelable, ItemWithFixableId, Entity, ItemWithTitle {

    /** {@link Parcelable}. */
    public static final Creator<Series> CREATOR =
            new Creator<Series>() {
                @Override
                public Series createFromParcel(@NonNull final Parcel source) {
                    return new Series(source);
                }

                @Override
                public Series[] newArray(final int size) {
                    return new Series[size];
                }
            };

    /**
     * Parse "some text (some more text)" into "some text" and "some more text".
     * <p>
     * We want a "some text" that does not START with a bracket!
     */
    public static final Pattern TEXT1_BR_TEXT2_BR_PATTERN =
            Pattern.compile("([^(]+.*)"
                            + "\\s*"
                            + "\\("
                            + /* */ "(.*)"
                            + "\\).*",
                            Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);

    /** Variant of the above, with an additional 3rd part. */
    private static final Pattern TEXT1_BR_TEXT2_BR_TEXT3_PATTERN =
            Pattern.compile("([^(]+.*)"
                            + "\\s*"
                            + "\\("
                            + /* */ "(.*)"
                            + "\\)"
                            + "\\s*(.*)\\s*",
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
            + /* */ "[0-9][0-9.\\-_]*"
            // no alphanumeric suffix

            + "|"

            // numeric numerals allowing for .-_ but must start with a digit.
            + /* */ "[0-9][0-9.\\-_]*"
            // optional alphanumeric suffix if separated by a '|'
            + /* */ "\\|\\S*?"

            + "|"

            // roman numerals prefixed by either '(' or whitespace
            + /* */ "\\s[(]?"
            // roman numerals
            + "(?=.)M*(C[MD]|D?C{0,3})(X[CL]|L?X{0,3})(I[XV]|V?I{0,3})"
//            + /* */ "[ivxlcm.]+"
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
    private static final String TITLE_NUMBER_REGEXP =
            // whitespace at the start
            "^\\s*"
            // Capture the title group(1)
            + "(.*?)"
            // delimiter ',' and/or whitespace
            + "\\s*[,]*\\s*"
            // Capture the number group(2)
            + /* */ NUMBER_REGEXP
            // whitespace to the end
            + "\\s*$";

    private static final Pattern TITLE_NUMBER_PATTERN =
            Pattern.compile(TITLE_NUMBER_REGEXP, Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);

    /**
     * Remove extraneous text from Series number. Used by {@link #from}.
     */
    private static final Pattern NUMBER_CLEANUP_PATTERN =
            Pattern.compile("^\\s*" + NUMBER_REGEXP + "\\s*$",
                            Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);

    /** Remove any leading zeros from Series number. */
    private static final Pattern PURE_NUMERICAL_PATTERN = Pattern.compile("^[0-9]+$");
    /** Row ID. */
    private long mId;
    /** Series title. */
    @NonNull
    private String mTitle;
    /** whether we have all we want from this Series / if the Series is finished. */
    private boolean mIsComplete;
    /** number (alphanumeric) of a book in this Series. */
    @NonNull
    private String mNumber;

    /**
     * Constructor.
     *
     * @param title of the Series
     */
    public Series(@NonNull final String title) {
        mTitle = title;
        mNumber = "";
    }

    /**
     * Constructor.
     *
     * @param title      of the Series
     * @param isComplete whether a Series is completed, i.e if the user has all
     *                   they want from this Series.
     */
    public Series(@NonNull final String title,
                  final boolean isComplete) {
        mTitle = title;
        mIsComplete = isComplete;
        mNumber = "";
    }

    /**
     * Full constructor with optional book number.
     *
     * @param id      ID of the Series in the database.
     * @param rowData with data
     */
    public Series(final long id,
                  @NonNull final DataHolder rowData) {
        mId = id;
        mTitle = rowData.getString(DBDefinitions.KEY_SERIES_TITLE);
        mIsComplete = rowData.getBoolean(DBDefinitions.KEY_SERIES_IS_COMPLETE);
        // optional domain, not always used.
        if (rowData.contains(DBDefinitions.KEY_BOOK_NUM_IN_SERIES)) {
            mNumber = rowData.getString(DBDefinitions.KEY_BOOK_NUM_IN_SERIES);
        } else {
            mNumber = "";
        }
    }

    /**
     * {@link Parcelable} Constructor.
     *
     * @param in Parcel to construct the object from
     */
    private Series(@NonNull final Parcel in) {
        mId = in.readLong();
        //noinspection ConstantConditions
        mTitle = in.readString();
        mIsComplete = in.readInt() != 0;
        //noinspection ConstantConditions
        mNumber = in.readString();
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
            String g1 = matcher.group(1);
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

            String uTitle = ParseUtils.unEscape(matcher.group(1));
            String uNumber = ParseUtils.unEscape(matcher.group(2));

            Series newSeries = new Series(uTitle);
            // If it's purely numeric, remove any leading zeros.
            if (PURE_NUMERICAL_PATTERN.matcher(uNumber).find()) {
                uNumber = String.valueOf(Long.parseLong(uNumber));
            }
            newSeries.setNumber(uNumber);
            return newSeries;

        } else {
            // no number part found
            String uTitle = ParseUtils.unEscape(text.trim());
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
     *
     * <strong>Note:</strong> we could make this method the default {@link Series#from}
     * but that would add overhead for most sites.
     *
     * @param text string to decode
     *
     * @return the Series
     */
    @NonNull
    public static Series from3(@NonNull final String text) {
        Series series;

        // Detect "title (middle) number" and "title (number)"
        Matcher matcher = TEXT1_BR_TEXT2_BR_TEXT3_PATTERN.matcher(text);
        if (matcher.find()) {
            String prefix = matcher.group(1);
            String middle = matcher.group(2);
            String suffix = matcher.group(3);

            if (prefix != null) {
                if (suffix != null && !suffix.isEmpty()) {
                    // the suffix group is the number.
                    series = from(prefix, suffix);

                    // Cover a special case were the middle group is potentially
                    // a roman numeral which should be prefixed to the number.
                    if ("I".equals(middle)) {
                        series.setNumber("1." + series.getNumber());
                    } else if ("II".equals(middle)) {
                        series.setNumber("2." + series.getNumber());
                    } else if ("III".equals(middle)) {
                        series.setNumber("3." + series.getNumber());
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

        // did't match the specific pattern, handle as normal.
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
        String uTitle = ParseUtils.unEscape(title);
        String uNumber = ParseUtils.unEscape(number);

        Series newSeries = new Series(uTitle);
        if (!uNumber.isEmpty()) {
            Matcher matcher = NUMBER_CLEANUP_PATTERN.matcher(uNumber);
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
     * Remove Series from the list where the titles are the same, but one entry has a
     * {@code null} or empty number.
     * e.g. the following list should be processed as indicated:
     * <p>
     * fred(5)
     * fred <-- delete
     * bill <-- delete
     * bill <-- delete
     * bill(1)
     * fred(5) <-- delete
     * fred(6)
     *
     * @param list        to prune
     * @param context     Current context
     * @param db          Database Access;
     * @param bookLocale  Locale to use if the item does not have a Locale of its own.
     * @param isBatchMode set to {@code true} to force the use of the bookLocale,
     *                    instead of taking a round trip to the database to try and guess
     *                    the locale. Should be used for example during an import.
     *
     * @return {@code true} if the list was modified.
     */
    public static boolean pruneList(@NonNull final List<Series> list,
                                    @NonNull final Context context,
                                    @NonNull final DAO db,
                                    @NonNull final Locale bookLocale,
                                    final boolean isBatchMode) {
        Map<String, Series> hashMap = new HashMap<>();
        Collection<Series> toDelete = new ArrayList<>();
        // will be set to true if we modify the list.
        boolean modified = false;
        Iterator<Series> it = list.iterator();

        while (it.hasNext()) {
            Series series = it.next();

            Locale locale;
            if (isBatchMode) {
                locale = bookLocale;
            } else {
                locale = series.getLocale(context, db, bookLocale);
            }

            String title = series.getTitle().trim().toLowerCase(locale);
            String number = series.getNumber().trim().toLowerCase(locale);

            if (!hashMap.containsKey(title)) {
                // Not there, so just add and continue
                hashMap.put(title, series);

            } else {
                // See if we can purge either one.
                if (number.isEmpty()) {
                    // Always delete Series with empty numbers
                    // if an equal or more specific one exists
                    it.remove();
                    modified = true;

                } else {
                    // See if the previous one also has a number
                    Series previous = hashMap.get(title);
                    if (previous != null) {
                        if (previous.getNumber().trim().isEmpty()) {
                            // it doesn't. Remove the previous; we keep the current one.
                            toDelete.add(previous);
                            modified = true;
                            // and update our map (replaces the previous one)
                            hashMap.put(title, series);

                        } else {
                            // Both have numbers. See if they are the same.
                            if (number.equals(previous.getNumber().trim().toLowerCase(locale))) {
                                // Same exact Series, delete this one
                                it.remove();
                                modified = true;
                            }
                            //else {
                            // Nothing to do: this is a different Series number, keep both
                            //}
                        }
                    }
                }
            }
        }

        for (Series s : toDelete) {
            list.remove(s);
        }

        // now repeat but taking the id into account.
        // (the order in the || is important...)
        return ItemWithFixableId.pruneList(list, context, db, bookLocale, isBatchMode)
               || modified;
    }

    /**
     * Get the 'complete' status of the Series.
     *
     * @return {@code true} if the Series is complete
     */
    public boolean isComplete() {
        return mIsComplete;
    }

    /**
     * Set the 'complete' status of the Series.
     *
     * @param isComplete Flag indicating the user considers this Series to be 'complete'
     */
    public void setComplete(final boolean isComplete) {
        mIsComplete = isComplete;
    }

    @SuppressWarnings("SameReturnValue")
    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(@NonNull final Parcel dest,
                              final int flags) {
        dest.writeLong(mId);
        dest.writeString(mTitle);
        dest.writeInt(mIsComplete ? 1 : 0);
        dest.writeString(mNumber);
    }

    /**
     * Write the extra data to the JSONObject.
     *
     * @param data which {@link #fromJson(JSONObject)} will read
     *
     * @throws JSONException on failure
     */
    public void toJson(@NonNull final JSONObject data)
            throws JSONException {

        if (mIsComplete) {
            data.put(DBDefinitions.KEY_SERIES_IS_COMPLETE, true);
        }
    }

    /**
     * Read the extra data from the JSONObject.
     *
     * @param data as written by {@link #toJson(JSONObject)}
     */
    public void fromJson(@NonNull final JSONObject data) {
        if (data.has(DBDefinitions.KEY_SERIES_IS_COMPLETE)) {
            mIsComplete = data.optBoolean(DBDefinitions.KEY_SERIES_IS_COMPLETE);
        } else if (data.has("complete")) {
            mIsComplete = data.optBoolean("complete");
        }
    }

    @Override
    public long getId() {
        return mId;
    }

    public void setId(final long id) {
        mId = id;
    }

    /**
     * Get the user visible title.
     *
     * @param context Current context
     *
     * @return "title" or "title (nr)"
     */
    @NonNull
    public String getLabel(@NonNull final Context context) {

        Locale locale = LocaleUtils.getUserLocale(context);
        // overkill...  see the getLocale method for more comments
        // try (DAO db = new DAO(TAG)) {
        //     locale = getLocale(context, db, LocaleUtils.getUserLocale(context));
        // }
        String title = reorderTitleForDisplaying(context, locale);

        if (!mNumber.isEmpty()) {
            return title + " (" + mNumber + ')';
        } else {
            return title;
        }
    }

    /**
     * Get the unformatted title.
     *
     * @return title
     */
    @NonNull
    @Override
    public String getTitle() {
        return mTitle;
    }

    /**
     * Set the unformatted title; as entered manually by the user.
     *
     * @param title to use
     */
    public void setTitle(@NonNull final String title) {
        mTitle = title;
    }

    /**
     * Get the unformatted number.
     *
     * @return number (as a string)
     */
    @NonNull
    public String getNumber() {
        return mNumber;
    }

    /**
     * Set the unformatted number; as entered manually by the user.
     *
     * @param number to use, a {@code null} is replaced by "".
     */
    public void setNumber(@Nullable final String number) {
        if (number == null) {
            mNumber = "";
        } else {
            mNumber = number;
        }
    }

    /**
     * Replace local details from another Series.
     *
     * @param source            Series to copy from
     * @param includeBookFields Flag to force copying the Book related fields as well
     */
    public void copyFrom(@NonNull final Series source,
                         final boolean includeBookFields) {
        mTitle = source.mTitle;
        mIsComplete = source.mIsComplete;
        if (includeBookFields) {
            mNumber = source.mNumber;
        }
    }

    /**
     * Get the Locale for a Series.
     * This is defined as the Locale for the language from the first book in the Series.
     *
     * @param context    Current context
     * @param db         Database Access
     * @param bookLocale Locale to use if the Series does not have a Locale of its own.
     *
     * @return the Locale of the Series
     */
    @NonNull
    @Override
    public Locale getLocale(@NonNull final Context context,
                            @Nullable final DAO db,
                            @NonNull final Locale bookLocale) {

        //TODO: need a reliable way to cache the Locale here. i.e. store the language of a series.
        // See also {@link #pruneList}
        // were we use batch mode. Also: a french book belonging to a dutch series...
        // the series title OB is wrong. For now this is partially mitigated by making
        // entering the book language mandatory.
        if (db != null) {
            String lang = db.getSeriesLanguage(mId);
            if (!lang.isEmpty()) {
                Locale seriesLocale = LocaleUtils.getLocale(context, lang);
                if (seriesLocale != null) {
                    return seriesLocale;
                }
            }
        }
        return bookLocale;
    }

    @Override
    public long fixId(@NonNull final Context context,
                      @NonNull final DAO db,
                      @NonNull final Locale bookLocale) {
        mId = db.getSeriesId(context, this, bookLocale);
        return mId;
    }

    /**
     * A Series with a given Title is <strong>NOT</strong> defined by a unique ID.
     * In a list of Series, the number of a book in a Series ('Elric(1)', 'Elric(2)' etc)
     * can be different, while the Series itself will have the same ID, so it's not unique by ID.
     */
    @SuppressWarnings("SameReturnValue")
    @Override
    public boolean isUniqueById() {
        return false;
    }

    /**
     * Equality: <strong>id, title, number</strong>.
     * <ul>
     *      <li>The 'isComplete' is a user setting.</li>
     * </ul>
     *
     * @return hash
     */
    @Override
    public int hashCode() {
        return Objects.hash(mId, mTitle, mNumber);
    }

    /**
     * Equality: <strong>id, title, number</strong>.
     * <p>
     * <li>it's the same Object</li>
     * <li>one or both of them are 'new' (e.g. id == 0) or have the same ID<br>
     * AND title are equal</li>
     * <li>if both are 'new' check if title/number are equal</li>
     * <p>
     * Compare is CASE SENSITIVE ! This allows correcting case mistakes even with identical ID.
     */
    @Override
    public boolean equals(@Nullable final Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj == null || getClass() != obj.getClass()) {
            return false;
        }
        Series that = (Series) obj;
        // if both 'exist' but have different ID's -> different.
        if (mId != 0 && that.mId != 0 && mId != that.mId) {
            return false;
        }
        return Objects.equals(mTitle, that.mTitle)
               && Objects.equals(mNumber, that.mNumber);
    }

    @Override
    @NonNull
    public String toString() {
        return "Series{"
               + "mId=" + mId
               + ", mTitle=`" + mTitle + '`'
               + ", mIsComplete=" + mIsComplete
               + ", mNumber=`" + mNumber + '`'
               + '}';
    }

    public enum Details {
        Full, Normal, Short
    }
}
