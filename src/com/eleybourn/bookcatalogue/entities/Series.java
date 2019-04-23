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

import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.eleybourn.bookcatalogue.database.DBA;
import com.eleybourn.bookcatalogue.database.cursors.ColumnMapper;
import com.eleybourn.bookcatalogue.utils.LocaleUtils;
import com.eleybourn.bookcatalogue.utils.StringList;
import com.eleybourn.bookcatalogue.utils.Utils;

import static com.eleybourn.bookcatalogue.database.DBDefinitions.DOM_BOOK_SERIES_NUM;
import static com.eleybourn.bookcatalogue.database.DBDefinitions.DOM_SERIES_IS_COMPLETE;
import static com.eleybourn.bookcatalogue.database.DBDefinitions.DOM_SERIES_TITLE;

/**
 * Class to hold book-related series data.
 * <p>
 * Note:
 * A Series as defined in the database is just id+name (and isComplete)
 * <p>
 * The number is of course related to the book itself.
 * So this class does not represent a Series, but a "BookInSeries"
 *
 * @author Philip Warner
 */
public class Series
        implements Parcelable, Utils.ItemWithIdFixup, Entity {

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
     * Regular expression - TODO: make this user controlled ? Add more languages here?
     * <p>
     * or use //Resources res = LocaleUtils.getLocalizedResources(mContext, bookLocale);
     */
    private static final String NUMBER_PREFIXES =
            "(#|number|num|num.|no|no.|nr|nr.|book|bk|bk.|volume|vol|vol.|tome|part|pt.|)";

    /**
     * Trim extraneous punctuation and whitespace from the name.
     * Combine the alpha part with the numeric part. The latter supports roman numerals as well.
     */
    private static final String SERIES_REGEX_SUFFIX =
            NUMBER_PREFIXES + "\\s*([0-9.\\-]+|[ivxlcm.\\-]+)\\s*$";

    /** Parse a string into name + number. */
    private static final Pattern FROM_STRING_PATTERN = Pattern.compile("^(.*)\\s*\\((.*)\\)\\s*$");

    /** Parse series name/numbers embedded in a book title. */
    private static final Pattern SERIES_FROM_BOOK_TITLE_PATTERN =
            Pattern.compile("(.*?)(,|\\s)\\s*" + SERIES_REGEX_SUFFIX,
                            Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);

    /** Remove extraneous text from series position. */
    private static final Pattern POS_CLEANUP_PATTERN =
            Pattern.compile("^\\s*" + SERIES_REGEX_SUFFIX,
                            Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);

    /** Remove any leading zeros from series position. */
    private static final Pattern POS_REMOVE_LEADING_ZEROS_PATTERN = Pattern.compile("^[0-9]+$");

    private long mId;
    @NonNull
    private String mName;
    /** whether we have all we want from this Series / if the series is finished. */
    private boolean mIsComplete;
    /** number (alphanumeric) of a book in this series. */
    @NonNull
    private String mNumber;

    /**
     * Constructor.
     *
     * @param name of the series
     */
    public Series(@NonNull final String name) {
        mName = name;
        mNumber = "";
    }

    /**
     * Constructor.
     *
     * @param name       of the series
     * @param isComplete whether a Series is completed, i.e if the user has all
     *                   they want from this Series.
     */
    public Series(@NonNull final String name,
                  final boolean isComplete) {
        mName = name;
        mIsComplete = isComplete;
        mNumber = "";
    }

    /**
     * Full constructor.
     *
     * @param id         of the series
     * @param name       of the series
     * @param isComplete whether a Series is completed, i.e if the user has all
     *                   they want from this Series.
     */
    @SuppressWarnings("unused")
    public Series(final long id,
                  @NonNull final String name,
                  final boolean isComplete) {
        mId = id;
        mName = name.trim();
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
                  @NonNull final ColumnMapper mapper) {
        mId = id;
        mName = mapper.getString(DOM_SERIES_TITLE);
        mIsComplete = mapper.getBoolean(DOM_SERIES_IS_COMPLETE);
        // optional domain, not always used.
        if (mapper.contains(DOM_BOOK_SERIES_NUM)) {
            mNumber = mapper.getString(DOM_BOOK_SERIES_NUM);
        } else {
            mNumber = "";
        }
    }

    /** {@link Parcelable}. */
    protected Series(@NonNull final Parcel in) {
        mId = in.readLong();
        //noinspection ConstantConditions
        mName = in.readString();
        mIsComplete = in.readByte() != 0;
        //noinspection ConstantConditions
        mNumber = in.readString();
    }

    /**
     * Constructor that will attempt to parse a single string into a Series name and number.
     *
     * @param fromString string to decode
     *
     * @return the series
     */
    public static Series fromString(@NonNull final String fromString) {
        Matcher matcher = FROM_STRING_PATTERN.matcher(fromString);
        if (matcher.find()) {
            Series newSeries = new Series(matcher.group(1).trim());
            newSeries.setNumber(cleanupSeriesPosition(matcher.group(2)));
            return newSeries;
        } else {
            return new Series(fromString.trim());
        }
    }

    /**
     * Try to extract a series from a book title.
     *
     * @param title Book title to parse
     *
     * @return structure with parsed details of the Series
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

                Matcher matcher = SERIES_FROM_BOOK_TITLE_PATTERN.matcher(details.getName());
                if (matcher.find()) {
                    details.setName(matcher.group(1));
                    details.setPosition(matcher.group(4));
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
    public static String cleanupSeriesPosition(@Nullable final String position) {
        if (position == null) {
            return "";
        }

        String pos = position.trim();

        Matcher matcher = POS_CLEANUP_PATTERN.matcher(pos);
        if (matcher.find()) {
            pos = matcher.group(2);
            if (POS_REMOVE_LEADING_ZEROS_PATTERN.matcher(pos).find()) {
                return String.valueOf(Long.parseLong(pos));
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
     *
     * @param list to check
     *
     * @return {@code true} is the list was modified in any way.
     */
    @SuppressWarnings("UnusedReturnValue")
    public static boolean pruneSeriesList(@NonNull final List<Series> list) {

        //TOMF: book locale or user-preferred?
        Locale locale = LocaleUtils.getPreferredLocal();

        // keep track of what we have
        Map<String, Series> map = new HashMap<>();

        // will be set to true if we deleted items.
        boolean modified = false;

        Iterator<Series> it = list.iterator();
        while (it.hasNext()) {
            Series series = it.next();

            final String name = series.getName().trim().toLowerCase(locale);
            if (!map.containsKey(name)) {
                // Not there, so just add and continue
                map.put(name, series);

            } else {
                // See if we can purge either one.
                if (series.getNumber().trim().isEmpty()) {
                    // Always delete series with empty numbers if an equally or more
                    // specific one exists
                    it.remove();
                    modified = true;

                } else {
                    // See if the one in 'index' also has a num
                    Series orig = map.get(name);
                    //noinspection ConstantConditions
                    if (orig.getNumber() == null || orig.getNumber().trim().isEmpty()) {
                        // Replace with this one, and delete the original
                        map.put(name, series);
                        it.remove();
                        modified = true;

                    } else {
                        // Both have numbers. See if they are the same.
                        if (series.getNumber().trim().toLowerCase(locale)
                                  .equals(orig.getNumber().trim().toLowerCase(locale))) {
                            // Same exact series, delete this one
                            it.remove();
                            modified = true;
                        } //else {
                        // Nothing to do: same series, but different series position
                        //}
                    }
                }
            }
        }
        return modified;
    }

    /**
     * Sets the 'complete' status of the series.
     *
     * @param db         database
     * @param id         series id
     * @param isComplete Flag indicating the user considers this series to be 'complete'
     *
     * @return {@code true} for success
     */
    public static boolean setComplete(@NonNull final DBA db,
                                      final long id,
                                      final boolean isComplete) {
        // load from database
        Series series = db.getSeries(id);
        //noinspection ConstantConditions
        series.setComplete(isComplete);
        int rowsAffected = db.updateSeries(series);
        return rowsAffected == 1;
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

    @Override
    public void writeToParcel(@NonNull final Parcel dest,
                              final int flags) {
        dest.writeLong(mId);
        dest.writeString(mName);
        dest.writeByte((byte) (mIsComplete ? 1 : 0));
        dest.writeString(mNumber);
    }

    @SuppressWarnings("SameReturnValue")
    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public long getId() {
        return mId;
    }

    public void setId(final long id) {
        mId = id;
    }

    /**
     * @return User visible name; consisting of "name" or "name (nr)"
     */
    @NonNull
    public String getLabel() {
        if (!mNumber.isEmpty()) {
            return mName + " (" + mNumber + ')';
        } else {
            return mName;
        }
    }

    /**
     * @return the name suitable for sorting (on screen)
     */
    @NonNull
    public String getSortName() {
        return getLabel();
    }

    /**
     * @return the unformatted name
     */
    @NonNull
    public String getName() {
        return mName;
    }

    public void setName(@NonNull final String name) {
        mName = name;
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
     * @param number to use, cannot be null.
     */
    public void setNumber(@NonNull final String number) {
        mNumber = number;
    }


    @Override
    @NonNull
    public String toString() {
        return "Series{"
                + "mId=" + mId
                + ", mName=`" + mName + '`'
                + ", mIsComplete=" + mIsComplete
                + ", mNumber=`" + mNumber + '`'
                + '}';
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
    @NonNull
    public String stringEncoded() {
        String numberStr;
        if (!mNumber.isEmpty()) {
            // for display reasons, start the number part with a space !
            numberStr = " (" + mNumber + ')';
        } else {
            numberStr = "";
        }
        return StringList.escapeListItem('(', mName) + numberStr;
    }

    /**
     * Replace local details from another series.
     *
     * @param source Series to copy from
     */
    public void copyFrom(@NonNull final Series source) {
        mName = source.mName;
        mIsComplete = source.mIsComplete;
        mNumber = source.mNumber;
    }

    @Override
    public long fixupId(@NonNull final DBA db) {
        mId = db.getSeriesId(this);
        return mId;
    }

    /**
     * Each position in a series ('Elric(1)', 'Elric(2)' etc) will have the same
     * ID, so they are not unique by ID.
     */
    @SuppressWarnings("SameReturnValue")
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
        if ((mId != 0) && (that.mId != 0) && (mId != that.mId)) {
            return false;
        }
        return Objects.equals(mName, that.mName)
                && (mIsComplete == that.mIsComplete)
                && Objects.equals(mNumber, that.mNumber);

    }

    @Override
    public int hashCode() {
        return Objects.hash(mId, mName);
    }

    /**
     * Value class giving resulting series info after parsing a series name.
     */
    public static class SeriesDetails {

        public final int startChar;
        @NonNull
        private String mPosition = "";
        @NonNull
        private String mName;

        SeriesDetails(@NonNull final String name,
                      final int startChar) {
            mName = name;
            this.startChar = startChar;
        }

        /**
         * @return series name
         */
        @NonNull
        public String getName() {
            return mName;
        }

        /**
         * @param name of series
         */
        public void setName(@NonNull final String name) {
            mName = name;
        }

        /**
         * @return the position, aka the 'number' of the book in this series
         */
        @NonNull
        public String getPosition() {
            return mPosition;
        }

        /**
         * Clean and store the position of a book.
         *
         * @param position the position/number of a book in this series; can be null
         */
        public void setPosition(@Nullable final String position) {
            mPosition = cleanupSeriesPosition(position);
        }
    }

}
