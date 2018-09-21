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
import com.eleybourn.bookcatalogue.database.CatalogueDBAdapter;
import com.eleybourn.bookcatalogue.utils.Utils;

import java.io.Serializable;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Class to hold book-related series data. Used in lists and import/export.
 *
 * @author Philip Warner
 */
public class Series implements Serializable, Utils.ItemWithIdFixup {
    private static final long serialVersionUID = 1L;

    /**
     * Support for creation via Parcelable
     */
    public static final Parcelable.Creator<Series> CREATOR = new Parcelable.Creator<Series>() {
        public Series createFromParcel(Parcel in) {
            return new Series(in);
        }

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

        So, when are two series equal ?
        TODO: the presence of 'number' in this class creates confusion. Should be moved to the book itself.
     */
    public long id;
    public String name;

    public String number;

    public Series(@NonNull final String name) {
        java.util.regex.Matcher m = PATTERN.matcher(name);
        if (m.find()) {
            this.name = m.group(1).trim();
            this.number = cleanupSeriesPosition(m.group(2));
        } else {
            this.name = name.trim();
            this.number = "";
        }
        this.id = 0L;
    }

    public Series(@NonNull final String name, @Nullable final String number) {
        this(0L, name, number);
    }

    public Series(final long id, @NonNull final String name, @Nullable final String number) {
        this.id = id;
        this.name = name.trim();
        this.number = cleanupSeriesPosition(number);
    }

    private Series(@NonNull final Parcel in) {
        name = in.readString().trim();
        number = in.readString();
        id = in.readLong();
    }

    /**
     * Try to extract a series from a book title.
     *
     * @param title Book title to parse
     */
    @Nullable
    public static SeriesDetails findSeries(@Nullable final String title) {
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
     * @return th series number (remember: number is really alfanum)
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

    @NonNull
    public String toString() {
        return getDisplayName();
    }

    /**
     * Replace local details from another series
     *
     * @param source Author to copy
     */
    public void copyFrom(@NonNull final Series source) {
        name = source.name;
        number = source.number;
        id = source.id;
    }

    @Override
    public long fixupId(@NonNull final CatalogueDBAdapter db) {
        this.id = db.getSeriesId(this);
        return this.id;
    }

    @Override
    public long getId() {
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
     * Two series are equal if:
     * - one or both of them is 'new' (e.g. id == 0) and their names are equal
     * - ids are equal
     *
     * So the number plays NO ROLE !
     *
     * Compare is CASE SENSITIVE !
     */
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        Series series = (Series) o;
        if (id == 0 || series.id == 0) {
            return Objects.equals(name, series.name);
        }
        return (id == series.id);
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
        public String position = null;
        public int startChar;
    }
}
