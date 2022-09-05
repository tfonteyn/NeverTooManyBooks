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
 * A data class representing a domain + the sql column expression + a sorting flag.
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
    private final Sort sort;

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
     * @param sort       flag
     */
    public DomainExpression(@NonNull final Domain domain,
                            @Nullable final String expression,
                            @NonNull final Sort sort) {
        this.domain = domain;
        this.expression = expression;
        this.sort = sort;
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
        sort = in.readParcelable(Domain.class.getClassLoader());
    }

    @NonNull
    public Domain getDomain() {
        return domain;
    }

    @Nullable
    public String getExpression() {
        return expression;
    }

    @NonNull
    public Sort getSort() {
        return sort;
    }

    @Override
    public void writeToParcel(@NonNull final Parcel dest,
                              final int flags) {
        dest.writeParcelable(domain, flags);
        dest.writeString(expression);
        dest.writeParcelable(sort, flags);
    }

    @Override
    public int describeContents() {
        return 0;
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
        return domain.equals(that.domain)
               && Objects.equals(expression, that.expression)
               && sort == that.sort;
    }

    @Override
    public int hashCode() {
        return Objects.hash(domain, expression, sort);
    }

    @Override
    @NonNull
    public String toString() {
        return "DomainExpression{"
               + "domain=" + domain
               + ", expression=`" + expression + '`'
               + ", sort=" + sort
               + '}';
    }
}
