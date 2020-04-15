/*
 * @Copyright 2020 HardBackNutter
 * @License GNU General Public License
 *
 * This file is part of NeverTooManyBooks.
 *
 * In August 2018, this project was forked from:
 * Book Catalogue 5.2.2 @2016 Philip Warner & Evan Leybourn
 *
 * Without their original creation, this project would not exist in its
 * current form. It was however largely rewritten/refactored and any
 * comments on this fork should be directed at HardBackNutter and not
 * at the original creators.
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

/**
 * A data class representing a domain + the sql column expression and optional sorting flag.
 */
public class VirtualDomain
        implements Parcelable {

    /** {@link Parcelable}. */
    public static final Creator<VirtualDomain> CREATOR = new Creator<VirtualDomain>() {
        @Override
        public VirtualDomain createFromParcel(@NonNull final Parcel in) {
            return new VirtualDomain(in);
        }

        @Override
        public VirtualDomain[] newArray(final int size) {
            return new VirtualDomain[size];
        }
    };
    /** Domain. */
    @NonNull
    private final Domain mDomain;
    /** Expression to use to fetch the domain value. */
    @Nullable
    private final String mExpression;
    @NonNull
    private final Sorted mSorted;

    public VirtualDomain(@NonNull final Domain domain,
                         @NonNull final String expression) {
        this.mDomain = domain;
        this.mExpression = expression;
        mSorted = Sorted.No;
    }

    public VirtualDomain(@NonNull final Domain domain,
                         @Nullable final String expression,
                         @NonNull final Sorted sorted) {
        this.mDomain = domain;
        this.mExpression = expression;
        this.mSorted = sorted;
    }

    /**
     * {@link Parcelable} Constructor.
     *
     * @param in Parcel to construct the object from
     */
    private VirtualDomain(@NonNull final Parcel in) {
        //noinspection ConstantConditions
        mDomain = in.readParcelable(Domain.class.getClassLoader());
        mExpression = in.readString();
        mSorted = Sorted.values()[in.readInt()];
    }

    @Override
    public void writeToParcel(@NonNull final Parcel dest,
                              final int flags) {
        dest.writeParcelable(mDomain, flags);
        dest.writeString(mExpression);
        dest.writeInt(mSorted.ordinal());
    }

    @Override
    public int describeContents() {
        return 0;
    }

    public boolean isSorted() {
        return mSorted != Sorted.No;
    }

    @NonNull
    public Domain getDomain() {
        return mDomain;
    }

    @Nullable
    public String getExpression() {
        return mExpression;
    }

    /** wrapper for better code readability. */
    public String getName() {
        return mDomain.getName();
    }

    public String getSortedExpression() {
        if (mSorted == Sorted.Desc) {
            return " DESC";
        } else {
            return "";
        }
    }

    public enum Sorted {
        No, Asc, Desc
    }
}
