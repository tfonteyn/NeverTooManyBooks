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

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.eleybourn.bookcatalogue.database.DAO;
import com.eleybourn.bookcatalogue.database.DBDefinitions;
import com.eleybourn.bookcatalogue.database.cursors.ColumnMapper;
import com.eleybourn.bookcatalogue.utils.LocaleUtils;
import com.eleybourn.bookcatalogue.utils.StringList;

/**
 * Class to hold book-related series data.
 * <p>
 * <b>Note:</b>
 * A Series as defined in the database is just id+name (and isComplete)
 * <p>
 * The number is of course related to the book itself.
 * So this class does not represent a Series, but a "BookInSeries"
 *
 * @author Philip Warner
 */
public class Series
        implements Parcelable, ItemWithIdFixup, Entity {

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
     * or use //Resources res = LocaleUtils.getLocalizedResources(mContext, series.getLocale());
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
     * Full constructor with optional book number.
     *
     * @param id     ID of the Series in the database.
     * @param mapper for the cursor.
     */
    public Series(final long id,
                  @NonNull final ColumnMapper mapper) {
        mId = id;
        mName = mapper.getString(DBDefinitions.KEY_SERIES_TITLE);
        mIsComplete = mapper.getBoolean(DBDefinitions.KEY_SERIES_IS_COMPLETE);
        // optional domain, not always used.
        if (mapper.contains(DBDefinitions.KEY_BOOK_NUM_IN_SERIES)) {
            mNumber = mapper.getString(DBDefinitions.KEY_BOOK_NUM_IN_SERIES);
        } else {
            mNumber = "";
        }
    }

    /** {@link Parcelable}. */
    protected Series(@NonNull final Parcel in) {
        mId = in.readLong();
        //noinspection ConstantConditions
        mName = in.readString();
        mIsComplete = in.readInt() != 0;
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
     * {@code null} or empty position.
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
    public static void pruneSeriesList(List<Series> list) {
        List<Series> toDelete = new ArrayList<>();
        Map<String, Series> index = new HashMap<>();

        for (Series series : list) {
            Locale locale = series.getLocale();

            final String name = series.getName().trim().toLowerCase(locale);
            final boolean emptyNum = series.getNumber().trim().isEmpty();

            if (!index.containsKey(name)) {
                // Not there, so just add and continue
                index.put(name, series);

            } else {
                // See if we can purge either one.
                if (emptyNum) {
                    // Always delete Series with empty numbers if an equally or more specific one exists
                    toDelete.add(series);
                } else {
                    // See if the previous one also has a number
                    Series previous = index.get(name);
                    //noinspection ConstantConditions
                    if (previous.getNumber().trim().isEmpty()) {
                        // Replace with this Series, and mark previous Series for delete
                        index.put(name, series);
                        toDelete.add(previous);
                    } else {
                        // Both have numbers. See if they are the same.
                        if (series.getNumber().trim().toLowerCase(locale)
                                  .equals(previous.getNumber().trim().toLowerCase(locale))) {
                            // Same exact Series, delete this one
                            toDelete.add(series);
                        }
                        //else {
                        // Nothing to do: this is a different series position
                        // keep both
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

    @Override
    public void writeToParcel(@NonNull final Parcel dest,
                              final int flags) {
        dest.writeLong(mId);
        dest.writeString(mName);
        dest.writeInt(mIsComplete ? 1 : 0);
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
     * @param number to use, cannot be {@code null}.
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
            // the surrounding () are NOT escaped as they are part of the format.
            numberStr = " (" + StringList.escapeListItem(mNumber, '(') + ')';
        } else {
            numberStr = "";
        }
        return StringList.escapeListItem(mName, '(') + numberStr;
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
    public Locale getLocale() {
        return LocaleUtils.getPreferredLocal();
    }

    @Override
    public long fixupId(@NonNull final DAO db) {
        return fixupId(db, getLocale());
    }

    @Override
    public long fixupId(@NonNull final DAO db,
                        @NonNull final Locale locale) {
        mId = db.getSeriesId(this, locale);
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
     * <li>it's the same Object</li>
     * <li>one or both of them are 'new' (e.g. id == 0) or have the same id<br>
     * AND all other fields are equal</li>
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
        // one or both are 'new' or their ID's are the same.
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
        void setName(@NonNull final String name) {
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
         * @param position the position/number of a book in this series; can be {@code null}
         */
        void setPosition(@Nullable final String position) {
            mPosition = cleanupSeriesPosition(position);
        }
    }

}
