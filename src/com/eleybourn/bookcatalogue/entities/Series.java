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
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;

import com.eleybourn.bookcatalogue.BookCatalogueApp;
import com.eleybourn.bookcatalogue.R;
import com.eleybourn.bookcatalogue.UniqueId;
import com.eleybourn.bookcatalogue.database.CatalogueDBAdapter;
import com.eleybourn.bookcatalogue.database.DBExceptions;
import com.eleybourn.bookcatalogue.debug.Logger;
import com.eleybourn.bookcatalogue.utils.DateUtils;
import com.eleybourn.bookcatalogue.utils.Utils;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Class to hold book-related series data.
 *
 * @author Philip Warner
 */
public class Series implements Parcelable, Utils.ItemWithIdFixup {

    public static final Creator<Series> CREATOR = new Creator<Series>() {
        @Override
        public Series createFromParcel(Parcel in) {
            return new Series(in);
        }

        @Override
        public Series[] newArray(int size) {
            return new Series[size];
        }
    };
    private static final String SERIES_REGEX_SUFFIX =
            BookCatalogueApp.getResourceString(R.string.series_number_prefixes)
                    /*
                     * Trim extraneous punctuation and whitespace from the titles and authors
                     *
                     * Original code had:
                     *    "\\s*([0-9\\.\\-]+|[ivxlcm\\.\\-]+)\\s*$";
                     *
                     *    Android Studio:
                     *    Reports character escapes that are replaceable with the unescaped character without a
                     *    change in meaning. Note that inside the square brackets of a character class, many
                     *    escapes are unnecessary that would be necessary outside of a character class.
                     *    For example the regex [\.] is identical to [.]
                     */
                    + "\\s*([0-9.\\-]+|[ivxlcm.\\-]+)\\s*$";
    private static final String SERIES_REGEX_1 = "^\\s*" + SERIES_REGEX_SUFFIX;
    private static final String SERIES_REGEX_2 = "(.*?)(,|\\s)\\s*" + SERIES_REGEX_SUFFIX;
    /** Pattern used to recognize series numbers embedded in names */
    private static Pattern mSeriesPat = null;
    /** Pattern used to remove extraneous text from series positions */
    private static Pattern mSeriesPosCleanupPat = null;
    private static Pattern mSeriesIntegerPat = null;
    @SuppressWarnings({"FieldCanBeLocal"})
    private final Pattern PATTERN = Pattern.compile("^(.*)\\s*\\((.*)\\)\\s*$");
    /*
        A Series as defined in the database is really just id+name
        The number is of course related to the book itself.

        ENHANCE: This class does not represent a Series, but a "BookInSeries"
     */
    public long id;
    public String name;
    public boolean isComplete;
    public String number;

    /**
     * Constructor that will attempt to parse a single string into a Series name and number.
     */
    public Series(final @NonNull String encodedName) {
        java.util.regex.Matcher m = PATTERN.matcher(encodedName);
        if (m.find()) {
            this.name = m.group(1).trim();
            this.number = cleanupSeriesPosition(m.group(2));
        } else {
            this.name = encodedName.trim();
            this.number = "";
        }
        this.id = 0L;
    }

    /**
     * @param name   of the series
     * @param number number of this book in the series
     */
    public Series(final @NonNull String name, final @Nullable String number) {
        this(0L, name, false, number);
    }

    /**
     * @param name       of the series
     * @param isComplete whether a Series is completed, i.e if the user has all he wants from this Series.
     * @param number     number of this book in the series
     */
    public Series(final @NonNull String name, final boolean isComplete, final @Nullable String number) {
        this(0L, name, isComplete, number);
    }

    /**
     * @param id         of the series
     * @param name       of the series
     * @param isComplete whether a Series is completed, i.e if the user has all he wants from this Series.
     * @param number     number of this book in the series
     */
    public Series(final long id, final @NonNull String name, final boolean isComplete, final @Nullable String number) {
        this.id = id;
        this.name = name.trim();
        this.isComplete = isComplete;
        this.number = cleanupSeriesPosition(number);
    }

    protected Series(Parcel in) {
        id = in.readLong();
        name = in.readString();
        isComplete = in.readByte() != 0;
        number = in.readString();
    }

    /**
     * Try to extract a series from a book title.
     *
     * @param title Book title to parse
     */
    @Nullable
    public static SeriesDetails findSeriesFromBookTitle(final @Nullable String title) {
        if (title == null || title.isEmpty()) {
            return null;
        }
        SeriesDetails details = null;
        int last = title.lastIndexOf("(");
        if (last >= 1) { // We want a title that does not START with a bracket!
            int close = title.lastIndexOf(")");
            if (close > -1 && last < close) {
                details = new SeriesDetails();
                details.name = title.substring((last + 1), close);
                details.startChar = last;
                if (mSeriesPat == null) {
                    mSeriesPat = Pattern.compile(SERIES_REGEX_2, Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
                }
                Matcher matcher = mSeriesPat.matcher(details.name);
                if (matcher.find()) {
                    details.name = matcher.group(1);
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
    private static String cleanupSeriesPosition(@Nullable String position) {
        if (position == null) {
            return "";
        }

        if (mSeriesPosCleanupPat == null) {
            mSeriesPosCleanupPat = Pattern.compile(SERIES_REGEX_1, Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
        }
        if (mSeriesIntegerPat == null) {
            String numericExp = "^[0-9]+$";
            mSeriesIntegerPat = Pattern.compile(numericExp);
        }

        position = position.trim();
        Matcher matcher = mSeriesPosCleanupPat.matcher(position);
        if (matcher.find()) {
            // Try to remove leading zeros.
            String pos = matcher.group(2);
            Matcher intMatch = mSeriesIntegerPat.matcher(pos);
            if (intMatch.find()) {
                return Long.parseLong(pos) + "";
            } else {
                return pos;
            }
        } else {
            return position;
        }
    }

    /**
     * Remove series from the list where the names are the same, but one entry has a null or empty position.
     * eg. the following list should be processed as indicated:
     *
     * fred(5)
     * fred <-- delete
     * bill <-- delete
     * bill <-- delete
     * bill(1)
     */
    @SuppressWarnings("UnusedReturnValue")
    public static boolean pruneSeriesList(final @Nullable List<Series> list) {
        Objects.requireNonNull(list);

        List<Series> toDelete = new ArrayList<>();
        Map<String, Series> index = new HashMap<>();

        for (Series s : list) {
            final boolean emptyNum = (s.number == null || s.number.trim().isEmpty());
            final String lcName = s.name.trim().toLowerCase();
            final boolean inNames = index.containsKey(lcName);
            if (!inNames) {
                // Just add and continue
                index.put(lcName, s);
            } else {
                // See if we can purge either
                if (emptyNum) {
                    // Always delete series with empty numbers if an equally or more specific one exists
                    toDelete.add(s);
                } else {
                    // See if the one in 'index' also has a num
                    Series orig = index.get(lcName);
                    if (orig.number == null || orig.number.trim().isEmpty()) {
                        // Replace with this one, and add original to the delete list
                        index.put(lcName, s);
                        toDelete.add(orig);
                    } else {
                        // Both have numbers. See if they are the same.
                        if (s.number.trim().toLowerCase().equals(orig.number.trim().toLowerCase())) {
                            // Same exact series, delete this one
                            toDelete.add(s);
                        } //else {
                        // Nothing to do: this is a different series position
                        //}
                    }
                }
            }
        }

        for (Series s : toDelete) {
            list.remove(s);
        }

        return (toDelete.size() > 0);

    }

    public boolean isComplete() {
        return isComplete;
    }

    public void setComplete(final boolean complete) {
        isComplete = complete;
    }

    public static boolean setComplete(final CatalogueDBAdapter db,
                                      final long id,
                                      final boolean isComplete) {
        Series series = null;
        try {
            // load from database
            series = db.getSeries(id);
            Objects.requireNonNull(series);
            series.setComplete(isComplete);
            return (db.updateSeries(series) == 1);
        } catch (Exception e) {
            // log but ignore
            Logger.error(e,"failed to set Series id=" + id + " to complete=" + isComplete);
            // rollback
            if (series != null) {
                series.setComplete(!isComplete);
            }
            return false;
        }
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeLong(id);
        dest.writeString(name);
        dest.writeByte((byte) (isComplete ? 1 : 0));
        dest.writeString(number);
    }

    @SuppressWarnings("SameReturnValue")
    @Override
    public int describeContents() {
        return 0;
    }

    @NonNull
    public String getDisplayName() {
        if (number != null && !number.isEmpty()) {
            return name + " (" + number + ")";
        } else {
            return name;
        }
    }

    @NonNull
    public String getSortName() {
        return getDisplayName();
    }

    /**
     * Support for encoding to a text file
     *
     * @return the object encoded as a String.
     *
     * "name (number)"
     * or
     * "name"
     */
    @Override
    @NonNull
    public String toString() {
        if (number != null && !number.isEmpty()) {
            // start with a space !
            return name + " (" + number + ")";
        } else {
            return name;
        }
    }

    /**
     * Replace local details from another series
     *
     * @param source Series to copy
     */
    public void copyFrom(final @NonNull Series source) {
        name = source.name;
        number = source.number;
        isComplete = source.isComplete;
        id = source.id;
    }

    @Override
    public long fixupId(final @NonNull CatalogueDBAdapter db) {
        this.id = db.getSeriesId(this);
        return this.id;
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
     * Two are the same if:
     *
     * - it's the same Object duh..
     * - one or both of them is 'new' (e.g. id == 0) or their id's are the same
     * AND all their other fields are equal
     *
     * Compare is CASE SENSITIVE ! This allows correcting case mistakes.
     */
    @Override
    public boolean equals(final @Nullable Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        Series that = (Series) o;
        if (this.id == 0 || that.id == 0 || this.id == that.id) {
            return Objects.equals(this.name, that.name)
                    && (this.isComplete == that.isComplete)
                    && Objects.equals(this.number, that.number);
        }
        return false;
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, name);
    }


    /**
     * Data class giving resulting series info after parsing a series name
     *
     * @author Philip Warner
     */
    public static class SeriesDetails {
        public String name;
        @Nullable
        public String position = null;
        public int startChar;
    }

}
