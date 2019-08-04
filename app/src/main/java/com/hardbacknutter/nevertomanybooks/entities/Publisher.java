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

package com.hardbacknutter.nevertomanybooks.entities;

import android.os.Parcel;
import android.os.Parcelable;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.Objects;

/**
 * Class to hold Publisher data. Used in lists.
 * <p>
 * ENHANCE: use a dedicated table with the publishers
 * Any reference to 'id' in this class is meant for future implementation.
 */
public class Publisher
        implements Parcelable {

    /** {@link Parcelable}. */
    public static final Creator<Publisher> CREATOR =
            new Creator<Publisher>() {
                @Override
                public Publisher createFromParcel(@NonNull final Parcel source) {
                    return new Publisher(source);
                }

                @Override
                public Publisher[] newArray(final int size) {
                    return new Publisher[size];
                }
            };

    /** Publisher name. */
    private String mName;

    /**
     * Constructor.
     *
     * @param name of publisher.
     */
    public Publisher(@NonNull final String name) {
        mName = name.trim();
    }

    /**
     * {@link Parcelable} Constructor.
     *
     * @param in Parcel to construct the object from
     */
    private Publisher(@NonNull final Parcel in) {
        mName = in.readString();
    }

    @Override
    public void writeToParcel(@NonNull final Parcel dest,
                              final int flags) {
        dest.writeString(mName);
    }

    @SuppressWarnings("SameReturnValue")
    @Override
    public int describeContents() {
        return 0;
    }

    /**
     * Get the name of the publisher.
     *
     * @return the name
     */
    @NonNull
    public String getName() {
        return mName;
    }

    /**
     * Replace local details from another publisher.
     *
     * @param source publisher to copy from
     */
    public void copyFrom(@NonNull final Publisher source) {
        mName = source.mName;
    }

    @Override
    @NonNull
    public String toString() {
        return "Publisher{"
                + "mName=`" + mName + '`'
                + '}';
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
        Publisher that = (Publisher) obj;
        return Objects.equals(mName, that.mName);
    }

    @Override
    public int hashCode() {
        return Objects.hash(mName);
    }
}
