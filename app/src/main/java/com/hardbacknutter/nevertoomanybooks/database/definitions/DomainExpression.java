/*
 * @Copyright 2018-2021 HardBackNutter
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

import androidx.annotation.IntDef;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
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

    public static final int SORT_UNSORTED = 0;
    public static final int SORT_ASC = 1;
    public static final int SORT_DESC = 2;

    /** Domain. */
    @NonNull
    private final Domain mDomain;
    /** Expression to use to fetch the domain value. */
    @Nullable
    private final String mExpression;
    @Sorting
    private final int mSorted;

    /**
     * Constructor.
     * By default unsorted.
     *
     * @param domain     underlying domain
     * @param expression to use for fetching the data
     */
    public DomainExpression(@NonNull final Domain domain,
                            @Nullable final String expression) {
        this(domain, expression, SORT_UNSORTED);
    }

    /**
     * Constructor.
     *
     * @param domain underlying domain
     * @param sorted flag
     */
    public DomainExpression(@NonNull final Domain domain,
                            @Sorting final int sorted) {
        this(domain, null, sorted);
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
                            @Sorting final int sorted) {
        mDomain = domain;
        mExpression = expression;
        mSorted = sorted;
    }

    /**
     * Copy constructor.
     *
     * @param that to copy from
     */
    public DomainExpression(@NonNull final DomainExpression that) {
        this(that.mDomain, that.mExpression, that.mSorted);
    }

    /**
     * {@link Parcelable} Constructor.
     *
     * @param in Parcel to construct the object from
     */
    private DomainExpression(@NonNull final Parcel in) {
        //noinspection ConstantConditions
        mDomain = in.readParcelable(Domain.class.getClassLoader());
        mExpression = in.readString();
        mSorted = in.readInt();
    }

    @Override
    public void writeToParcel(@NonNull final Parcel dest,
                              final int flags) {
        dest.writeParcelable(mDomain, flags);
        dest.writeString(mExpression);
        dest.writeInt(mSorted);
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
        return mSorted != SORT_UNSORTED;
    }

    /**
     * Get the underlying domain.
     *
     * @return Domain
     */
    @NonNull
    public Domain getDomain() {
        return mDomain;
    }

    @Nullable
    public String getExpression() {
        return mExpression;
    }

    /**
     * Get the name of this domain.
     *
     * @return name
     */
    @NonNull
    public String getName() {
        return mDomain.getName();
    }

    @NonNull
    public String getSortedExpression() {
        if (mSorted == SORT_DESC) {
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
        return mSorted == that.mSorted
               && mDomain.equals(that.mDomain)
               && Objects.equals(mExpression, that.mExpression);
    }

    @Override
    public int hashCode() {
        return Objects.hash(mDomain, mExpression, mSorted);
    }

    @Override
    @NonNull
    public String toString() {
        return "DomainExpression{"
               + "mDomain=" + mDomain
               + ", mExpression='" + mExpression + '\''
               + ", mSorted=" + mSorted
               + '}';
    }

    @IntDef({SORT_UNSORTED, SORT_ASC, SORT_DESC})
    @Retention(RetentionPolicy.SOURCE)
    public @interface Sorting {

    }
}
