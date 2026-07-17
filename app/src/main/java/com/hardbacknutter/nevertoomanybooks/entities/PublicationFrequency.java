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
import android.content.res.Resources;
import android.os.Parcel;
import android.os.Parcelable;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.Arrays;
import java.util.Objects;

import com.hardbacknutter.nevertoomanybooks.R;
import com.hardbacknutter.nevertoomanybooks.database.DBKey;

public final class PublicationFrequency
        implements Parcelable {

    /** {@link Parcelable}. */
    public static final Creator<PublicationFrequency> CREATOR = new Creator<>() {
        @Override
        @NonNull
        public PublicationFrequency createFromParcel(@NonNull final Parcel in) {
            return new PublicationFrequency(in);
        }

        @Override
        @NonNull
        public PublicationFrequency[] newArray(final int size) {
            return new PublicationFrequency[size];
        }
    };

    @NonNull
    private final Type type;
    // e.g., 1 = Every, 2 = Every two, 3 = Three times per...
    private final int cadence;
    // true = "X times per [Type]", false = "Every X [Type]s"
    private final boolean isOrdinal;

    /**
     * Constructor.
     *
     * @param type      time unit, Daily, Weekly...
     * @param cadence   how many times per [Type] / interval in [Type]s
     * @param isOrdinal {@code true} = "X times per [Type]",
     *                  {@code false} = "Every X [Type]s"
     */
    public PublicationFrequency(@NonNull final Type type,
                                final int cadence,
                                final boolean isOrdinal) {
        this.type = type;
        this.cadence = cadence;
        this.isOrdinal = isOrdinal;
    }

    /**
     * Constructor.
     *
     * @param rowData with data
     */
    public PublicationFrequency(@NonNull final DataHolder rowData) {
        this.type = Type.byId(rowData.getInt(DBKey.PUBLICATION_FREQUENCY.TYPE));
        this.cadence = rowData.getInt(DBKey.PUBLICATION_FREQUENCY.CADENCE);
        this.isOrdinal = rowData.getBoolean(DBKey.PUBLICATION_FREQUENCY.IS_ORDINAL);
    }

    private PublicationFrequency(@NonNull final Parcel in) {
        //noinspection DataFlowIssue,deprecation
        type = in.readParcelable(Type.class.getClassLoader());
        cadence = in.readInt();
        isOrdinal = in.readByte() != 0;
    }

    /**
     * Convert the {@code MARC21} code to a frequency record.
     * <p>
     * frequencyCode:
     * <pre>
     *   a - Annual (Every 1 Year)
     *   b - Bimonthly (Every 2 months)
     *   c - Semiweekly (2 times a week)
     *   d - Daily
     *   e - Biweekly (Every 2 weeks)
     *   f - Semiannual (Every 6 months)
     *   g - Biennial (Every 2 years)
     *   h - Triennial (Every 3 years)
     *   i - Three times a week
     *   j - Three times a month
     *   k - Continuously updated
     *   m - Monthly
     *   q - Quarterly (Every 3 months)
     *   s - Semimonthly (2 times a month)
     *   t - Three times a year (Every 4 months)
     *   u - Unknown
     *   w - Weekly
     *   z - Other
     *   | - No attempt to code
     * </pre>
     * <p>
     * regularityCode:
     * <pre>
     *   n - Normalized irregular
     *   r - Regular
     *   u - Unknown
     *   x - Completely irregular
     *   | - No attempt to code
     * </pre>
     *
     * @param frequencyCode  marc21 character cf008/18
     * @param regularityCode marc21 character cf008/19
     *
     * @return frequency
     *
     * @see <a href="https://www.loc.gov/marc/bibliographic/bd008s.html">MARC21 spec</a>
     */
    @NonNull
    public static PublicationFrequency fromMarc21(final char frequencyCode,
                                                  final char regularityCode) {
        // Handle explicit unknown or uncoded status
        if (regularityCode == 'u' || frequencyCode == 'u') {
            return new PublicationFrequency(Type.Unknown, 0, false);
        }

        // If it's completely irregular ('x'), use Unknown
        if (regularityCode == 'x' && frequencyCode == '#') {
            return new PublicationFrequency(Type.Unknown, 0, false);
        }

        switch (frequencyCode) {
            case 'a':
                // Annual
                return new PublicationFrequency(PublicationFrequency.Type.Yearly, 1, false);
            case 'b':
                // Bimonthly (Every 2 months)
                return new PublicationFrequency(PublicationFrequency.Type.Monthly, 2, false);
            case 'c':
                // Semiweekly (2 times a week)
                return new PublicationFrequency(PublicationFrequency.Type.Weekly, 2, true);
            case 'd':
                // Daily
                return new PublicationFrequency(PublicationFrequency.Type.Daily, 1, false);
            case 'e':
                // Biweekly (Every 2 weeks)
                return new PublicationFrequency(PublicationFrequency.Type.Weekly, 2, false);
            case 'f':
                // Semiannual (Every 6 months)
                return new PublicationFrequency(PublicationFrequency.Type.Monthly, 6, false);
            case 'g':
                // Biennial (Every 2 years)
                return new PublicationFrequency(PublicationFrequency.Type.Yearly, 2, false);
            case 'h':
                // Triennial (Every 3 years)
                return new PublicationFrequency(PublicationFrequency.Type.Yearly, 3, false);
            case 'i':
                // Three times a week
                return new PublicationFrequency(PublicationFrequency.Type.Weekly, 3, true);
            case 'j':
                // Three times a month
                return new PublicationFrequency(PublicationFrequency.Type.Monthly, 3, true);
            case 'm':
                // Monthly
                return new PublicationFrequency(PublicationFrequency.Type.Monthly, 1, false);
            case 'q':
                // Quarterly (Every 3 months)
                return new PublicationFrequency(PublicationFrequency.Type.Monthly, 3, false);
            case 's':
                // Semimonthly (2 times a month)
                return new PublicationFrequency(PublicationFrequency.Type.Monthly, 2, true);
            case 't':
                // Three times a year
                return new PublicationFrequency(PublicationFrequency.Type.Yearly, 3, true);
            case 'w':
                // Weekly
                return new PublicationFrequency(PublicationFrequency.Type.Weekly, 1, false);

            case 'k':
                // Continuously updated
            case 'z':
                // Other
            case '|':
                // No attempt to code
            default:
                return new PublicationFrequency(PublicationFrequency.Type.Unknown, 0, false);
        }
    }

    /**
     * Convert the {@code Unimarc} code to a frequency record.
     * <p>
     * frequencyCode:
     * <pre>
     *   a - daily
     *   b - semiweekly (twice a week)
     *   c - weekly
     *   d - biweekly (every two weeks)
     *   e - semimonthly (twice a month)
     *   f - monthly
     *   g - bimonthly (every two months)
     *   h - quarterly
     *   i - three times a year
     *   k - annual
     *   l - biennial (every two years)
     *   m - triennial (every three years)
     *   n t- hree times a week
     *   o - three times a month
     *   p - continuously updated
     *   u - unknown
     *   y - no frequency (i.e. irregular) See also character position 2 below.
     *   z - other
     * </pre>
     * <p>
     * regularityCode:
     * <pre>
     *   a - regular
     *   b - normalised irregular
     *   u - not known
     *   y - irregular
     * </pre>
     *
     * @param frequencyCode  unimarc character df110/1
     * @param regularityCode unimarc character df110/2
     *
     * @return frequency
     *
     * @see <a href="https://www.loc.gov/marc/bibliographic/bd008s.html">MARC21 spec</a>
     */
    @NonNull
    public static PublicationFrequency fromUnimarc(final char frequencyCode,
                                                   final char regularityCode) {
        // Handle explicitly irregular or unknown
        if (regularityCode == 'y' || regularityCode == 'u') {
            return new PublicationFrequency(Type.Unknown, 0, false);
        }

        switch (frequencyCode) {
            case 'a':
                return new PublicationFrequency(Type.Daily, 1, false);
            case 'b':
                // Semiweekly
                return new PublicationFrequency(Type.Weekly, 2, true);
            case 'c':
                return new PublicationFrequency(Type.Weekly, 1, false);
            case 'd':
                // Biweekly
                return new PublicationFrequency(Type.Weekly, 2, false);
            case 'e':
                // Semimonthly
                return new PublicationFrequency(Type.Monthly, 2, true);
            case 'f':
                return new PublicationFrequency(Type.Monthly, 1, false);
            case 'g':
                // Bimonthly
                return new PublicationFrequency(Type.Monthly, 2, false);
            case 'h':
                // Quarterly
                return new PublicationFrequency(Type.Monthly, 3, false);
            case 'i':
                // 3 times/year
                return new PublicationFrequency(Type.Yearly, 3, true);
            case 'k':
                return new PublicationFrequency(Type.Yearly, 1, false);
            case 'l':
                // Biennial
                return new PublicationFrequency(Type.Yearly, 2, false);
            case 'm':
                // Triennial
                return new PublicationFrequency(Type.Yearly, 3, false);
            case 'n':
                // 3 times/week
                return new PublicationFrequency(Type.Weekly, 3, true);
            case 'o':
                // 3 times/month
                return new PublicationFrequency(Type.Monthly, 3, true);
            case 'p': // Continuously updated
            case 'u': // Unknown
            case 'y': // No frequency
            case 'z': // Other
            case '|': // No attempt to code
            default:
                return new PublicationFrequency(Type.Unknown, 0, false);
        }
    }
    /**
     * Resolves the current frequency instance into its localized Android display string.
     *
     * @param context Android context to access localized assets and plural rules.
     *
     * @return A fully localized, clean human-readable frequency string.
     */
    @NonNull
    public String toDisplayString(@NonNull final Context context) {
        final Resources res = context.getResources();

        if (type == Type.Unknown) {
            return res.getString(R.string.freq_unknown);
        }

        if (isOrdinal) {
            switch (type) {
                case Daily:
                    return res.getQuantityString(R.plurals.freq_ordinal_daily, cadence, cadence);
                case Weekly:
                    return res.getQuantityString(R.plurals.freq_ordinal_weekly, cadence, cadence);
                case Monthly:
                    return res.getQuantityString(R.plurals.freq_ordinal_monthly, cadence, cadence);
                case Yearly:
                    return res.getQuantityString(R.plurals.freq_ordinal_yearly, cadence, cadence);
                default:
                    return res.getString(R.string.freq_unknown);
            }
        }

        switch (type) {
            case Daily:
                return res.getQuantityString(R.plurals.freq_interval_days, cadence, cadence);
            case Weekly:
                return res.getQuantityString(R.plurals.freq_interval_weeks, cadence, cadence);
            case Monthly:
                return res.getQuantityString(R.plurals.freq_interval_months, cadence, cadence);
            case Yearly:
                return res.getQuantityString(R.plurals.freq_interval_years, cadence, cadence);
            default:
                return res.getString(R.string.freq_unknown);
        }
    }

    @NonNull
    public Type getType() {
        return type;
    }

    public int getCadence() {
        return cadence;
    }

    public boolean isOrdinal() {
        return isOrdinal;
    }

    @Override
    public boolean equals(@Nullable final Object o) {
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final PublicationFrequency that = (PublicationFrequency) o;
        return type == that.type
               && cadence == that.cadence
               && isOrdinal == that.isOrdinal;
    }

    @Override
    public int hashCode() {
        return Objects.hash(type, cadence, isOrdinal);
    }

    @Override
    public void writeToParcel(@NonNull final Parcel dest,
                              final int flags) {
        dest.writeParcelable(type, flags);
        dest.writeInt(cadence);
        dest.writeByte((byte) (isOrdinal ? 1 : 0));
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    @NonNull
    public String toString() {
        return "PublicationFrequency{"
               + "type=" + type
               + ", cadence=" + cadence
               + ", isOrdinal=" + isOrdinal
               + '}';
    }

    public enum Type
            implements Parcelable {
        Unknown(0),
        Daily(1),
        Weekly(2),
        Monthly(3),
        Yearly(4);

        /** {@link Parcelable}. */
        public static final Creator<Type> CREATOR = new Creator<>() {
            @Override
            @NonNull
            public Type createFromParcel(@NonNull final Parcel in) {
                return values()[in.readInt()];
            }

            @Override
            @NonNull
            public Type[] newArray(final int size) {
                return new Type[size];
            }
        };

        private final int id;

        Type(final int id) {
            this.id = id;
        }

        /**
         * Lookup by id.
         * <p>
         * Import/Export and database usage only.
         *
         * @param id to lookup
         *
         * @return type; or {@link #Unknown} for any invalid id.
         */
        @NonNull
        public static Type byId(final int id) {
            return Arrays.stream(values())
                         .filter(v -> v.id == id)
                         .findFirst()
                         .orElse(Unknown);
        }

        /**
         * Get the internal id.
         * <p>
         * Import/Export and database usage only.
         *
         * @return id
         */
        public int getId() {
            return id;
        }


        @Override
        public void writeToParcel(@NonNull final Parcel dest,
                                  final int flags) {
            dest.writeInt(ordinal());
        }

        @Override
        public int describeContents() {
            return 0;
        }
    }
}
