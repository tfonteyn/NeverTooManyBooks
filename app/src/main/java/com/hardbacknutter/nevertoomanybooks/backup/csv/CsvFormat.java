/*
 * @Copyright 2018-2025 HardBackNutter
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

package com.hardbacknutter.nevertoomanybooks.backup.csv;

import android.content.Context;
import android.os.Parcel;
import android.os.Parcelable;

import androidx.annotation.NonNull;
import androidx.annotation.StringRes;

import java.util.List;
import java.util.Locale;

import com.hardbacknutter.nevertoomanybooks.R;
import com.hardbacknutter.nevertoomanybooks.core.parsers.RatingParser;
import com.hardbacknutter.nevertoomanybooks.core.parsers.RealNumberParser;
import com.hardbacknutter.nevertoomanybooks.database.DBKey;
import com.hardbacknutter.nevertoomanybooks.database.LegacyUpgrades;
import com.hardbacknutter.nevertoomanybooks.entities.Identifier;
import com.hardbacknutter.util.logger.LoggerFactory;

public enum CsvFormat
        implements Parcelable {
    /** A Goodreads export. */
    Goodreads(R.string.site_goodreads) {
        @NonNull
        public String mapColumnName(@NonNull final String name) {
            // From a test export on 2024-04-22:
            // Book Id,Title,
            // Author,Author l-f,Additional Authors,
            // ISBN,ISBN13,
            // My Rating,Average Rating,
            // Publisher,Binding,Number of Pages,
            // Year Published,Original Publication Year,Date Read,Date Added,
            // Bookshelves,Bookshelves with positions,Exclusive Shelf,
            // My Review, Spoiler,
            // Private Notes,
            // Read Count,Owned Copies
            switch (name) {
                case "book id":
                    return Identifier.SID_GOODREADS;
                case "title":
                    return DBKey.TITLE;
                case "author l-f":
                    // Will be decoded during import
                    return DBKey.AUTHOR.FORMATTED_FULL_NAME;
                case "additional authors":
                    // Added in addition to the one above
                    return CsvGoodreads.ADDITIONAL_AUTHORS;
                case "isbn":
                    // ISBN-10; will be used if the "isbn13" field is empty
                    return CsvGoodreads.ISBN10;
                case "isbn13":
                    return DBKey.ISBN;
                case "my rating":
                    return CsvGoodreads.MY_RATING;
                case "average rating":
                    return CsvGoodreads.AVERAGE_RATING;
                case "publisher":
                    return DBKey.PUBLISHER.NAME;
                case "binding":
                    return DBKey.FORMAT;
                case "number of pages":
                    return DBKey.PAGES;
                case "year published":
                    return DBKey.PUBLICATION_DATE;
                case "original publication year":
                    return DBKey.FIRST_PUBLICATION_DATE;
                case "date read":
                    return DBKey.READ_END__DATE;
                case "date added":
                    return DBKey.DATE_ADDED__UTC;
                case "bookshelves":
                    return CsvGoodreads.BOOKSHELVES;
                case "exclusive shelf":
                    return CsvGoodreads.EXCLUSIVE_SHELF;
                case "my review":
                    return CsvGoodreads.MY_REVIEW;
                case "private notes":
                    return DBKey.PERSONAL_NOTES;

                // The next set are ignored for now
                case "author":
                    // ignored in favour of the "author l-f" field
                case "bookshelves with positions":
                    // we don't support positions for bookshelves
                case "spoiler":
                    // I believe this is a flag set when the "my review" field is
                    // considered to contain spoilers - not supported.
                case "read count":
                    // We only support read == true/false
                case "owned copies":
                    // We do not have a concept of multiple copies
                    // (although could be a valid enhancement as we support lending out books)

                    // Just use a bogus name which will be ignored
                    return CsvGoodreads.PREFIX + name;

                default:
                    // Unknown on 2024-04-22; log them for future support
                    LoggerFactory.getLogger()
                                 .w(TAG, "Unknown Goodreads csv column=" + name);
                    return CsvGoodreads.PREFIX + name;
            }
        }

        // The locales are ignored as not needed for Goodreads
        @Override
        @NonNull
        public RatingParser createRatingParser(@NonNull final List<Locale> ignored) {
            return new RatingParser(5);
        }
    },
    /** The original BC format, or the extended but obsolete NTMB 1.x .. 3.x format. */
    BC(R.string.lbl_book_catalogue) {
        @NonNull
        public String mapColumnName(@NonNull final String name) {
            final String mapped = LegacyUpgrades.IDENTIFIERS.get(name);
            return mapped == null ? name : mapped;
        }

        @Override
        @NonNull
        public RatingParser createRatingParser(@NonNull final List<Locale> locales) {
            return new RatingParser(new RealNumberParser(locales), 5);
        }
    },
    /** Anything not explicitly recognized. */
    Unknown(R.string.unknown) {
        @NonNull
        public String mapColumnName(@NonNull final String name) {
            return name;
        }

        @Override
        @NonNull
        public RatingParser createRatingParser(@NonNull final List<Locale> locales) {
            return new RatingParser(new RealNumberParser(locales), 5);
        }
    };

    /** {@link Parcelable}. */
    public static final Creator<CsvFormat> CREATOR = new Creator<>() {
        @Override
        @NonNull
        public CsvFormat createFromParcel(@NonNull final Parcel in) {
            return values()[in.readInt()];
        }

        @Override
        @NonNull
        public CsvFormat[] newArray(final int size) {
            return new CsvFormat[size];
        }
    };
    private static final String TAG = "CsvFormat";
    /** Bundle key to pass this object around. */
    public static final String BKEY = TAG + ":bk";
    @StringRes
    private final int labelId;

    CsvFormat(@StringRes final int labelId) {
        this.labelId = labelId;
    }

    /**
     * Parse the CSV file header line to guess the origin/format.
     *
     * @param header to parse
     *
     * @return format detected
     */
    @NonNull
    static CsvFormat guess(@NonNull final String header) {
        // RELEASE: check the latest Goodreads CSV export file header.
        // A download on 2025-05-06 showed a header starting like this:
        if (header.startsWith(
                "Book Id,Title,Author,Author l-f,Additional Authors,ISBN,ISBN13,")) {
            return CsvFormat.Goodreads;

        } else if (header.startsWith("_id,author_details,title,isbn")
                   || header.startsWith("\"_id\",\"author_details\",\"title\",\"isbn\"")) {
            // We have a pretty good match for original BC files
            return CsvFormat.BC;

        } else if (header.startsWith("\"_id\",")) {
            // It's likely/hopefully a match for BC or NTMB 1-3 formats
            return CsvFormat.BC;

        }

        return CsvFormat.Unknown;
    }

    /**
     * Map a column name as found in the input file to a {@link DBKey} if possible.
     * Columns that need more processing <strong>MUST NOT</strong> use a {@link DBKey}.
     *
     * @param name to map
     *
     * @return mapped name
     */
    @NonNull
    public abstract String mapColumnName(@NonNull String name);

    /**
     * Create a {@link RatingParser} suitable for this format.
     *
     * @param locales to use
     *
     * @return new instance
     */
    @NonNull
    public abstract RatingParser createRatingParser(@NonNull List<Locale> locales);

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(@NonNull final Parcel dest,
                              final int flags) {
        dest.writeInt(ordinal());
    }

    /**
     * The user-displayable name for this format.
     *
     * @param context Current context
     *
     * @return label
     */
    @NonNull
    public CharSequence getLabel(@NonNull final Context context) {
        return context.getString(labelId);
    }
}
