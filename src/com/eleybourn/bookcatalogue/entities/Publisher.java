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

import com.eleybourn.bookcatalogue.utils.StringList;

import java.util.Objects;

/**
 * Class to hold Publisher data. Used in lists.
 *
 * ENHANCE: Could just have used a String, but this way we're prepared for a dedicated table with the publishers
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
    private static final char SEPARATOR = ',';
    public String name;

    public Publisher(@NonNull final String name) {
        this.name = name.trim();
    }

    protected Publisher(Parcel in) {
        name = in.readString();
    }

    @Override
    public void writeToParcel(Parcel dest,
                              int flags) {
        dest.writeString(name);
    }

    /** {@link Parcelable}. */
    @SuppressWarnings("SameReturnValue")
    @Override
    public int describeContents() {
        return 0;
    }

    /**
     * Support for encoding to a text file
     *
     * @return the object encoded as a String.
     *
     * "name"
     */
    @Override
    @NonNull
    public String toString() {
        return StringList.encodeListItem(SEPARATOR, name);
    }

    /**
     * Replace local details from another publisher
     *
     * @param source publisher to copy
     */
    public void copyFrom(@NonNull final Publisher source) {
        name = source.name;
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
    public boolean equals(@Nullable final Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj == null || getClass() != obj.getClass()) {
            return false;
        }
        Publisher that = (Publisher) obj;
        //ENHANCE: uncomment the 3 lines once(if) we start using ids
//        if (this.id == 0 || that.id == 0 || this.id == that.id) {
        return Objects.equals(this.name, that.name);
//        }
//        return false;
    }

    @Override
    public int hashCode() {
        return Objects.hash(name);
    }


}
