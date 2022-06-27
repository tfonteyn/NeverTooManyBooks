/*
 * @Copyright 2018-2022 HardBackNutter
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
package com.hardbacknutter.nevertoomanybooks.database.definitions;

import android.os.Parcel;
import android.os.Parcelable;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.Objects;

/**
 * A data class representing a domain + the sql column expression and optional sorting flag.
 * <p>
 * Immutable.
 */
public class DomainExpression
        implements Parcelable {

    /** {@link Parcelable}. */
    public static final Creator<DomainExpression> CREATOR = new Creator<>() {
        @Override
        public DomainExpression createFromParcel(@NonNull final Parcel in) {
            return new DomainExpression(in);
        }

        @Override
        public DomainExpression[] newArray(final int size) {
            return new DomainExpression[size];
        }
    };

    /** Domain. */
    @NonNull
    private final Domain domain;
    /** Expression to use to fetch the domain value. */
    @Nullable
    private final String expression;
    @NonNull
    private final Sort sorted;

    /**
     * Constructor.
     * By default unsorted.
     *
     * @param domain     underlying domain
     * @param expression to use for fetching the data
     */
    public DomainExpression(@NonNull final Domain domain,
                            @Nullable final String expression) {
        this(domain, expression, Sort.Unsorted);
    }

    /**
     * Constructor.
     *
     * @param domain     underlying domain
     * @param expression to use for fetching the data
     * @param sorted     flag
     */
    public DomainExpression(@NonNull final Domain domain,
                            @Nullable final String expression,
                            @NonNull final Sort sorted) {
        this.domain = domain;
        this.expression = expression;
        this.sorted = sorted;
    }

    /**
     * Copy constructor.
     *
     * @param that to copy from
     */
    public DomainExpression(@NonNull final DomainExpression that) {
        this(that.domain, that.expression, that.sorted);
    }

    /**
     * {@link Parcelable} Constructor.
     *
     * @param in Parcel to construct the object from
     */
    private DomainExpression(@NonNull final Parcel in) {
        //noinspection ConstantConditions
        domain = in.readParcelable(Domain.class.getClassLoader());
        expression = in.readString();
        //noinspection ConstantConditions
        sorted = in.readParcelable(Domain.class.getClassLoader());
    }

    @Override
    public void writeToParcel(@NonNull final Parcel dest,
                              final int flags) {
        dest.writeParcelable(domain, flags);
        dest.writeString(expression);
        dest.writeParcelable(sorted, flags);
    }

    @Override
    public int describeContents() {
        return 0;
    }

    /**
     * Check if this domain is sorted (asc/desc) or unsorted.
     *
     * @return {@code true} for sorted
     */
    public boolean isSorted() {
        return sorted != Sort.Unsorted;
    }

    /**
     * Get the underlying domain.
     *
     * @return Domain
     */
    @NonNull
    public Domain getDomain() {
        return domain;
    }

    @Nullable
    public String getExpression() {
        return expression;
    }

    @NonNull
    public String getSortedExpression() {
        if (sorted == Sort.Desc) {
            return " DESC";
        } else {
            return "";
        }
    }

    @Override
    public boolean equals(@Nullable final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final DomainExpression that = (DomainExpression) o;
        return sorted == that.sorted
               && domain.equals(that.domain)
               && Objects.equals(expression, that.expression);
    }

    @Override
    public int hashCode() {
        return Objects.hash(domain, expression, sorted);
    }

    @Override
    @NonNull
    public String toString() {
        return "DomainExpression{"
               + "domain=" + domain
               + ", expression=`" + expression + '`'
               + ", sorted=" + sorted
               + '}';
    }

    public enum Sort
            implements Parcelable {
        Unsorted,
        Asc,
        Desc;


        @SuppressWarnings("InnerClassFieldHidesOuterClassField")
        public static final Creator<Sort> CREATOR = new Creator<>() {
            @Override
            @NonNull
            public Sort createFromParcel(@NonNull final Parcel in) {
                return values()[in.readInt()];
            }

            @Override
            public Sort[] newArray(final int size) {
                return new Sort[size];
            }
        };

        @Override
        public int describeContents() {
            return 0;
        }

        @Override
        public void writeToParcel(@NonNull final Parcel dest,
                                  final int flags) {
            dest.writeInt(ordinal());
        }
    }
}
