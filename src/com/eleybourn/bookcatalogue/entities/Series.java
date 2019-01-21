/*
 * @copyright 2011 Philip Warner
 * @license GNU General Public License
 *
 * This file is part of Book Catalogue.
 *
 * Book Catalogue is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Book Catalogue is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with Book Catalogue.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.eleybourn.bookcatalogue.entities;

import android.os.Parcel;
import android.os.Parcelable;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.eleybourn.bookcatalogue.BookCatalogueApp;
import com.eleybourn.bookcatalogue.R;
import com.eleybourn.bookcatalogue.database.DBA;
import com.eleybourn.bookcatalogue.database.DBExceptions;
import com.eleybourn.bookcatalogue.debug.Logger;
import com.eleybourn.bookcatalogue.utils.Utils;

import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Class to hold book-related series data.
 * <p>
 * Note:
 * A Series as defined in the database is just id+name.
 * The number is of course related to the book itself.
 * So this class does not represent a Series, but a "BookInSeries"
 *
 * @author Philip Warner
 */
public class Series
        implements Parcelable, Utils.ItemWithIdFixup {

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
     * Trim extraneous punctuation and whitespace from the name.
     */
    private static final String SERIES_REGEX_SUFFIX =
            BookCatalogueApp.getResString(R.string.series_number_prefixes)
                    + "\\s*([0-9.\\-]+|[ivxlcm.\\-]+)\\s*$";
    private static final String SERIES_REGEX_1 = "^\\s*" + SERIES_REGEX_SUFFIX;
    private static final String SERIES_REGEX_2 = "(.*?)(,|\\s)\\s*" + SERIES_REGEX_SUFFIX;
    @SuppressWarnings("FieldCanBeLocal")
    private static final Pattern PATTERN = Pattern.compile("^(.*)\\s*\\((.*)\\)\\s*$");
    /** Pattern used to recognize series numbers embedded in names. */
    private static Pattern mSeriesPat;
    /** Pattern used to remove extraneous text from series positions. */
    private static Pattern mSeriesPosCleanupPat;
    private static Pattern mSeriesIntegerPat;
    /*

     */
    public long id;
    public String name;
    public boolean isComplete;

    private String mNumber;

    /**
     * Constructor that will attempt to parse a single string into a Series name and number.
     */
    public Series(@NonNull final String encodedName) {
        java.util.regex.Matcher m = PATTERN.matcher(encodedName);
        if (m.find()) {
            this.name = m.group(1).trim();
            this.mNumber = cleanupSeriesPosition(m.group(2));
        } else {
            this.name = encodedName.trim();
            this.mNumber = "";
        }
        this.id = 0L;
    }

    /**
     * @param name   of the series
     * @param number number of this book in the series
     */
    public Series(@NonNull final String name,
                  @Nullable final String number
    ) {
        this(0L, name, false, number);
    }

    /**
     * @param name       of the series
     * @param isComplete whether a Series is completed, i.e if the user has all
     *                   they want from this Series.
     * @param number     number of this book in the series
     */
    public Series(@NonNull final String name,
                  final boolean isComplete,
                  @Nullable final String number
    ) {
        this(0L, name, isComplete, number);
    }

    /**
     * @param id         of the series
     * @param name       of the series
     * @param isComplete whether a Series is completed, i.e if the user has all
     *                   they want from this Series.
     * @param number     number of this book in the series
     */
    public Series(final long id,
                  @NonNull final String name,
                  final boolean isComplete,
                  @Nullable final String number
    ) {
        this.id = id;
        this.name = name.trim();
        this.isComplete = isComplete;
        this.mNumber = cleanupSeriesPosition(number);
    }

    protected Series(Parcel in) {
        id = in.readLong();
        name = in.readString();
        isComplete = in.readByte() != 0;
        mNumber = in.readString();
    }

    /**
     * Try to extract a series from a book title.
     *
     * @param title Book title to parse
     */
    @Nullable
    public static SeriesDetails findSeriesFromBookTitle(@Nullable final String title) {
        if (title == null || title.isEmpty()) {
            return null;
        }
        SeriesDetails details = null;
        int openBracket = title.lastIndexOf('(');
        // We want a title that does not START with a bracket!
        if (openBracket >= 1) {
            int closeBracket = title.lastIndexOf(')');
            if (closeBracket > -1 && openBracket < closeBracket) {
                details = new SeriesDetails(title.substring(openBracket + 1, closeBracket),
                                            openBracket);
                if (mSeriesPat == null) {
                    mSeriesPat = Pattern.compile(SERIES_REGEX_2,
                                                 Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
                }
                Matcher matcher = mSeriesPat.matcher(details.getName());
                if (matcher.find()) {
                    details.setName(matcher.group(1));
                    details.position = matcher.group(4);
                }
            }
        }
        return details;
    }

    /**
     * Try to cleanup a series position number by removing superfluous text.
     *
     * @param position Position name to cleanup
     *
     * @return the series number (remember: it's really alphanumeric)
     */
    @NonNull
    private static String cleanupSeriesPosition(@Nullable final String position) {
        if (position == null) {
            return "";
        }

        String pos = position.trim();

        if (mSeriesPosCleanupPat == null) {
            mSeriesPosCleanupPat = Pattern.compile(SERIES_REGEX_1,
                                                   Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
        }
        if (mSeriesIntegerPat == null) {
            String numericExp = "^[0-9]+$";
            mSeriesIntegerPat = Pattern.compile(numericExp);
        }

        Matcher matcher = mSeriesPosCleanupPat.matcher(pos);
        if (matcher.find()) {
            // Try to remove leading zeros.
            pos = matcher.group(2);
            if (mSeriesIntegerPat.matcher(pos).find()) {
                return Long.parseLong(pos) + "";
            }
        }

        return pos;
    }

    /**
     * Remove series from the list where the names are the same, but one entry has a
     * null or empty position.
     * eg. the following list should be processed as indicated:
     * <p>
     * fred(5)
     * fred <-- delete
     * bill <-- delete
     * bill <-- delete
     * bill(1)
     */
    @SuppressWarnings("UnusedReturnValue")
    public static boolean pruneSeriesList(@NonNull final List<Series> list) {

        Map<String, Series> map = new HashMap<>();

        // will be set to true if we deleted items.
        boolean didDeletes = false;

        Iterator<Series> it = list.iterator();
        while (it.hasNext()) {
            Series series = it.next();

            final String name = series.name.trim().toLowerCase();
            if (!map.containsKey(name)) {
                // Just add and continue
                map.put(name, series);
            } else {
                // See if we can purge either
                if (series.mNumber == null || series.mNumber.trim().isEmpty()) {
                    // Always delete series with empty numbers if an equally or more
                    // specific one exists
                    it.remove();
                    didDeletes = true;
                } else {
                    // See if the one in 'index' also has a num
                    Series orig = map.get(name);
                    Objects.requireNonNull(orig);
                    if (orig.mNumber == null || orig.mNumber.trim().isEmpty()) {
                        // Replace with this one, and delete the original
                        map.put(name, series);
                        it.remove();
                        didDeletes = true;
                    } else {
                        // Both have numbers. See if they are the same.
                        if (series.mNumber.trim().toLowerCase()
                                          .equals(orig.mNumber.trim().toLowerCase())) {
                            // Same exact series, delete this one
                            it.remove();
                            didDeletes = true;
                        } //else {
                        // Nothing to do: same series, but different series position
                        //}
                    }
                }
            }
        }

        return didDeletes;

    }

    /**
     * Sets the 'complete' status of the series.
     *
     * @param db         database
     * @param id         series id
     * @param isComplete Flag indicating the user considers this series to be 'complete'
     *
     * @return <tt>true</tt> for success
     */
    public static boolean setComplete(@NonNull final DBA db,
                                      final long id,
                                      final boolean isComplete) {
        Series series = null;
        try {
            // load from database
            series = db.getSeries(id);
            Objects.requireNonNull(series);
            series.setComplete(isComplete);
            return db.updateSeries(series) == 1;
        } catch (DBExceptions.UpdateException e) {
            // log but ignore
            Logger.error(e, "failed to set Series id=" + id + " to complete=" + isComplete);
            // rollback
            if (series != null) {
                series.setComplete(!isComplete);
            }
            return false;
        }
    }

    /**
     * Sets the 'complete' status of the series.
     * @param isComplete Flag indicating the user considers this series to be 'complete'
     */
    public void setComplete(final boolean isComplete) {
        this.isComplete = isComplete;
    }

    /**
     *
     * @return <tt>true</tt> if the series is complete
     */
    public boolean isComplete() {
        return isComplete;
    }

    /** {@link Parcelable}. */
    @Override
    public void writeToParcel(@NonNull final Parcel dest,
                              final int flags
    ) {
        dest.writeLong(id);
        dest.writeString(name);
        dest.writeByte((byte) (isComplete ? 1 : 0));
        dest.writeString(mNumber);
    }

    /** {@link Parcelable}. */
    @SuppressWarnings("SameReturnValue")
    @Override
    public int describeContents() {
        return 0;
    }

    /**
     *
     * @return User visible name; consisting of "name" or "name (nr)"
     */
    @NonNull
    public String getDisplayName() {
        if (mNumber != null && !mNumber.isEmpty()) {
            return name + " (" + mNumber + ')';
        } else {
            return name;
        }
    }

    @NonNull
    public String getSortName() {
        return getDisplayName();
    }

    public String getNumber() {
        return mNumber;
    }

    /**
     * Support for encoding to a text file.
     *
     * @return the object encoded as a String.
     * <p>
     * "name (number)"
     * or
     * "name"
     */
    @Override
    @NonNull
    public String toString() {
        if (mNumber != null && !mNumber.isEmpty()) {
            // start with a space !
            return name + " (" + mNumber + ')';
        } else {
            return name;
        }
    }

    /**
     * Replace local details from another series.
     *
     * @param source Series to copy
     */
    public void copyFrom(@NonNull final Series source) {
        name = source.name;
        mNumber = source.mNumber;
        isComplete = source.isComplete;
        id = source.id;
    }

    @Override
    public long fixupId(@NonNull final DBA db) {
        id = db.getSeriesId(this);
        return id;
    }

    /**
     * Each position in a series ('Elric(1)', 'Elric(2)' etc) will have the same
     * ID, so they are not unique by ID.
     */
    @Override
    public boolean isUniqueById() {
        return false;
    }

    /**
     * Equality.
     * <p>
     * - it's the same Object duh..
     * - one or both of them is 'new' (e.g. id == 0) or their id's are the same
     * AND all their other fields are equal
     * <p>
     * Compare is CASE SENSITIVE ! This allows correcting case mistakes.
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
        if ((this.id != 0) && (that.id != 0) && (this.id != that.id)) {
            return false;
        }
        return Objects.equals(this.name, that.name)
                && (this.isComplete == that.isComplete)
                && Objects.equals(this.mNumber, that.mNumber);

    }

    @Override
    public int hashCode() {
        return Objects.hash(id, name);
    }


    /**
     * Data class giving resulting series info after parsing a series name.
     */
    public static class SeriesDetails {

        @NonNull
        private String mName;

        @Nullable
        public String position;

        public final int startChar;

        SeriesDetails(@NonNull final String name, final int startChar) {
            mName = name;
            this.startChar = startChar;
        }

        @NonNull
        public String getName() {
            return mName;
        }

        public void setName(@NonNull final String name) {
            mName = name;
        }
    }

}
