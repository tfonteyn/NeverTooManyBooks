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

/**
 * Class to hold book-related series data.
 * <p>
 * <b>Note:</b> the Series "number" is a column of {@link DBDefinitions#TBL_BOOK_SERIES}
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
     * The possible prefixes to a number as seen in the wild.
     * <p>
     * TODO: make this user controlled ? Add more languages here?
     * or use //Resources res = LocaleUtils.getLocalizedResources(mContext, series.getLocale());
     */
    static final String NUMBER_PREFIX_REGEXP =
            "(#|number|num|num.|no|no.|nr|nr.|book|bk|bk.|volume|vol|vol.|tome|part|pt.|deel|dl.|)";
    /** The numeric part of the number. Supports roman numerals as well. */
    static final String NUMBER_REGEXP = "\\s*([0-9.\\-]+|[ivxlcm.\\-]+)\\s*$";

    /** Remove extraneous text from series number. */
    private static final Pattern NUMBER_CLEANUP_PATTERN =
            Pattern.compile("^\\s*" + NUMBER_PREFIX_REGEXP + NUMBER_REGEXP,
                            Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);

    /** Remove any leading zeros from series number. */
    private static final Pattern REMOVE_LEADING_ZEROS_PATTERN =
            Pattern.compile("^[0-9]+$");

    /**
     * Parse a string into title + number. Used by the constructor.
     * Format: "The title (number)"
     * where '(' and ')' are fixed, and the 'number' is alpha numeric.
     */
    private static final Pattern CONSTRUCT_FROM_STRING_PATTERN =
            Pattern.compile("^(.*)\\s*\\((.*)\\)\\s*$");

    private long mId;
    @NonNull
    private String mTitle;
    /** whether we have all we want from this Series / if the series is finished. */
    private boolean mIsComplete;
    /** number (alphanumeric) of a book in this series. */
    @NonNull
    private String mNumber;

    /** cached locale. */
    private Locale mLocale;

    /**
     * Constructor.
     *
     * @param title of the series
     */
    public Series(@NonNull final String title) {
        mTitle = title;
        mNumber = "";
    }

    /**
     * Constructor.
     *
     * @param title      of the series
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
     * The format is expected to be "title" or "title (nr)"
     *
     * @param fromString string to decode
     *
     * @return the series
     */
    @NonNull
    public static Series fromString(@NonNull final String fromString) {
        Matcher matcher = CONSTRUCT_FROM_STRING_PATTERN.matcher(fromString);
        if (matcher.find()) {
            Series newSeries = new Series(matcher.group(1).trim());
            newSeries.setNumber(cleanupSeriesNumber(matcher.group(2)));
            return newSeries;
        } else {
            return new Series(fromString.trim());
        }
    }

    /**
     * Try to cleanup a series number by removing superfluous text.
     *
     * @param numberToClean to cleanup
     *
     * @return the series number (alphanumeric), can be empty, but never {@code null}
     */
    @NonNull
    public static String cleanupSeriesNumber(@Nullable final String numberToClean) {
        if (numberToClean == null || numberToClean.trim().isEmpty()) {
            return "";
        }

        String number = numberToClean.trim();

        Matcher matcher = NUMBER_CLEANUP_PATTERN.matcher(number);
        if (matcher.find()) {
            number = matcher.group(2);
            if (REMOVE_LEADING_ZEROS_PATTERN.matcher(number).find()) {
                return String.valueOf(Long.parseLong(number));
            }
        }

        return number;
    }

    /**
     * Remove series from the list where the titles are the same, but one entry has a
     * {@code null} or empty number.
     * e.g. the following list should be processed as indicated:
     * <p>
     * fred(5)
     * fred <-- delete
     * bill <-- delete
     * bill <-- delete
     * bill(1)
     *
     * @param list to check
     */
    public static void pruneSeriesList(@NonNull final List<Series> list) {
        List<Series> toDelete = new ArrayList<>();
        Map<String, Series> index = new HashMap<>();

        for (Series series : list) {
            Locale locale = series.getLocale();

            final String title = series.getTitle().trim().toLowerCase(locale);
            final boolean emptyNum = series.getNumber().trim().isEmpty();

            if (!index.containsKey(title)) {
                // Not there, so just add and continue
                index.put(title, series);

            } else {
                // See if we can purge either one.
                if (emptyNum) {
                    // Always delete Series with empty numbers if an equally
                    // or more specific one exists
                    toDelete.add(series);
                } else {
                    // See if the previous one also has a number
                    Series previous = index.get(title);
                    //noinspection ConstantConditions
                    if (previous.getNumber().trim().isEmpty()) {
                        // Replace with this Series, and mark previous Series for delete
                        index.put(title, series);
                        toDelete.add(previous);
                    } else {
                        // Both have numbers. See if they are the same.
                        if (series.getNumber().trim().toLowerCase(locale)
                                  .equals(previous.getNumber().trim().toLowerCase(locale))) {
                            // Same exact Series, delete this one
                            toDelete.add(series);
                        }
                        //else {
                        // Nothing to do: this is a different series number, keep both
                        //}
                    }
                }
            }
        }

        for (Series s : toDelete) {
            list.remove(s);
        }

        toDelete.size();
    }

    /**
     * @return {@code true} if the series is complete
     */
    public boolean isComplete() {
        return mIsComplete;
    }

    /**
     * Sets the 'complete' status of the series.
     *
     * @param isComplete Flag indicating the user considers this series to be 'complete'
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
     * @return User visible title; consisting of "title" or "title (nr)"
     */
    @NonNull
    public String getLabel() {
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
    public String getSortingTitle() {
        return getLabel();
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
     * @param number to use, cannot be {@code null}.
     */
    public void setNumber(@NonNull final String number) {
        mNumber = number;
    }

    /**
     * Replace local details from another series.
     *
     * @param source Series to copy from
     */
    public void copyFrom(@NonNull final Series source) {
        mTitle = source.mTitle;
        mIsComplete = source.mIsComplete;
        mNumber = source.mNumber;
    }

    /**
     * Stopgap.... makes the code elsewhere clean.
     * <p>
     * ENHANCE: The locale of the Series
     * should be based on either a specific language setting for
     * the Series itself, or on the locale of the primary book.
     * Neither is implemented for now. So we cheat.
     *
     * @return the locale of the Series
     */
    @NonNull
    @Override
    public Locale getLocale() {
        if (mLocale == null) {
            mLocale = LocaleUtils.getPreferredLocale();
        }
        return mLocale;
    }

    @Override
    public long fixId(@NonNull final Context context,
                      @NonNull final DAO db,
                      @NonNull final Locale seriesLocale) {
        mId = db.getSeriesId(context, this, seriesLocale);
        return mId;
    }

    /**
     * A Series with a given Title is <strong>NOT</strong> defined by a unique id.
     * In a list of Series, the number of a book in a Series ('Elric(1)', 'Elric(2)' etc)
     * can be different, while the Series itself will have the same ID, so it's not unique by ID.
     */
    @SuppressWarnings("SameReturnValue")
    @Override
    public boolean isUniqueById() {
        return false;
    }

    /**
     * Uniqueness in a {@code List<Series>} includes the number.
     *
     * @return hash
     */
    @Override
    public int uniqueHashCode() {
        return Objects.hash(mId, mTitle, mNumber);
    }

    /**
     * Equality: <strong>id, title</strong>.
     *
     * @return hash
     */
    @Override
    public int hashCode() {
        return Objects.hash(mId, mTitle);
    }

    /**
     * Equality: <strong>id, title</strong>.
     * <p>
     * <li>it's the same Object</li>
     * <li>one or both of them are 'new' (e.g. id == 0) or have the same id<br>
     * AND title are equal</li>
     * <p>
     * Compare is CASE SENSITIVE ! This allows correcting case mistakes even with identical id.
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
        return Objects.equals(mTitle, that.mTitle);
    }

    @Override
    @NonNull
    public String toString() {
        return "Series{"
               + "mId=" + mId
               + ", mTitle=`" + mTitle + '`'
               + ", mIsComplete=" + mIsComplete
               + ", mNumber=`" + mNumber + '`'
               + ", mLocale=" + mLocale
               + '}';
    }

}
