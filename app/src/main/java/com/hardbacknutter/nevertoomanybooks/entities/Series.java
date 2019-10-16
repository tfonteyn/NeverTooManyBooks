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

import android.content.Context;
import android.os.Parcel;
import android.os.Parcelable;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.hardbacknutter.nevertoomanybooks.database.DAO;
import com.hardbacknutter.nevertoomanybooks.database.DBDefinitions;
import com.hardbacknutter.nevertoomanybooks.database.cursors.CursorMapper;
import com.hardbacknutter.nevertoomanybooks.utils.LocaleUtils;
import com.hardbacknutter.nevertoomanybooks.utils.ParseUtils;

/**
 * Class to hold book-related Series data.
 *
 * <p>
 * <strong>Note:</strong> the Series "number" is a column of {@link DBDefinitions#TBL_BOOK_SERIES}
 * So this class does not strictly represent a Series, but a "BookInSeries"
 * When the number is disregarded, it is a real Series representation.
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
                    /* or no prefix */
            + ")"
            // whitespace between prefix and actual number
            + "\\s*"
            // Capture the number group
            + "("
            // numeric numerals allowing for .-_ but must start with a digit.
            + "[0-9][0-9.\\-_]*"
            // optional alphanumeric suffix.
            + "\\S*?"

            + "|"

            // roman numerals are accepted ONLY if they are prefixed by either '(' or a space
            + "\\s[(]?"
            // roman numerals allowing for .-_
            + "[ivxlcm.\\-_]+"
            // roman numerals are accepted ONLY if they are suffixed by either ')' or EOL
            + "[)]?$"
            // roman numerals do not support an alphanumeric suffixes.
            + ")";

    /**
     * Parse a string into title + number. Used by {@link #fromString(String)}.
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
     * Remove extraneous text from Series number. Used by {@link #fromString(String, String)}.
     */
    private static final Pattern NUMBER_CLEANUP_PATTERN =
            Pattern.compile("^\\s*" + NUMBER_REGEXP + "\\s*$",
                            Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);

    /** Remove any leading zeros from Series number. */
    private static final Pattern PURE_NUMERICAL_PATTERN = Pattern.compile("^[0-9]+$");

    private long mId;
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
     * @param id     ID of the Series in the database.
     * @param mapper for the cursor.
     */
    public Series(final long id,
                  @NonNull final CursorMapper mapper) {
        mId = id;
        mTitle = mapper.getString(DBDefinitions.KEY_SERIES_TITLE);
        mIsComplete = mapper.getBoolean(DBDefinitions.KEY_SERIES_IS_COMPLETE);
        // optional domain, not always used.
        if (mapper.contains(DBDefinitions.KEY_BOOK_NUM_IN_SERIES)) {
            mNumber = mapper.getString(DBDefinitions.KEY_BOOK_NUM_IN_SERIES);
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
     * @param fullTitle string to decode
     *
     * @return the Series
     */
    @NonNull
    public static Series fromString(@NonNull final String fullTitle) {
        // First check if we can simplify the decoding.
        // This makes the pattern easier to maintain.
        Matcher matcher = TEXT1_BR_TEXT2_BR_PATTERN.matcher(fullTitle);
        if (matcher.find()) {
            //noinspection ConstantConditions
            return fromString(matcher.group(1), matcher.group(2));
        }

        // HORRENDOUS, HORRIBLE HACK...
        if ("Blake's 7".equalsIgnoreCase(fullTitle)) {
            return new Series(fullTitle);
        }

        // We now know that brackets do NOT separate the number part
        matcher = TITLE_NUMBER_PATTERN.matcher(fullTitle);
        if (matcher.find()) {

            //noinspection ConstantConditions
            String uTitle = ParseUtils.unEscape(matcher.group(1));
            //noinspection ConstantConditions
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
            String uTitle = ParseUtils.unEscape(fullTitle.trim());
            return new Series(uTitle);
        }
    }

    /**
     * Constructor that will attempt to parse a number.
     *
     * @param title  for the Series; used as is.
     * @param number for the Series; will get cleaned up.
     *
     * @return the Series
     */
    @NonNull
    public static Series fromString(@NonNull final String title,
                                    @NonNull final String number) {
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
        List<Series> toDelete = new ArrayList<>();
        // will be set to true if we modify the list.
        boolean modified = false;
        Iterator<Series> it = list.iterator();

        while (it.hasNext()) {
            Series series = it.next();

            Locale locale;
            if (isBatchMode) {
                locale = bookLocale;
            } else {
                locale = series.getLocale(db, bookLocale);
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
                    //noinspection ConstantConditions
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

        for (Series s : toDelete) {
            list.remove(s);
        }

        // now repeat but taking the id into account.
        // (the order in the || is important...
        return ItemWithFixableId.pruneList(list, context, db, bookLocale, isBatchMode)
               || modified;
    }

    /**
     * @return {@code true} if the Series is complete
     */
    public boolean isComplete() {
        return mIsComplete;
    }

    /**
     * Sets the 'complete' status of the Series.
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
        if (!mNumber.isEmpty()) {
            return mTitle + " (" + mNumber + ')';
        } else {
            return mTitle;
        }
    }

    /**
     * @return the title suitable for sorting (on screen)
     */
    @NonNull
    public String getSorting() {
        if (!mNumber.isEmpty()) {
            return mTitle + " (" + mNumber + ')';
        } else {
            return mTitle;
        }
    }

    /**
     * @return the unformatted title
     */
    @NonNull
    @Override
    public String getTitle() {
        return mTitle;
    }

    public void setTitle(@NonNull final String title) {
        mTitle = title;
    }

    /**
     * @return the unformatted number
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
     * @param db         Database Access
     * @param bookLocale Locale to use if the Series does not have a Locale of its own.
     *
     * @return the Locale of the Series
     */
    @NonNull
    @Override
    public Locale getLocale(@NonNull final DAO db,
                            @NonNull final Locale bookLocale) {

        //FIXME: need a reliable way to cache the Locale here. See also {@link #pruneList}
        // were we use batch mode.
        String iso3Language = db.getSeriesLanguage(mId);
        if (!iso3Language.isEmpty()) {
            Locale seriesLocale = LocaleUtils.getLocale(iso3Language);
            if (seriesLocale != null) {
                return seriesLocale;
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
     * <li>The 'isComplete' is a user setting.</li>
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

}
