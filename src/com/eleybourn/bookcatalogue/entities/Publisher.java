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

import com.eleybourn.bookcatalogue.utils.StringList;

import java.util.Objects;

/**
 * Class to hold Publisher data. Used in lists.
 *
 * ENHANCE Could just have used a String, but this way we're prepared for a dedicated table with the publishers
 */
public class Publisher implements Parcelable {
    private static final char SEPARATOR = ',';
    public String name;

    public Publisher(final @NonNull String name) {
        this.name = name.trim();
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
    public void copyFrom(final @NonNull Publisher source) {
        name = source.name;
    }

    /**
     * Two Publishers are equal if:
     * - it's the same Object duh..
     * - one or both of them is 'new' (e.g. id == 0) but their names are equal
     * - ids are equal
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
        Publisher publisher = (Publisher) o;
        //ENHANCE uncomment the 3 lines once(if) we start using ids
//        if (id == 0 || publisher.id == 0) {
        return Objects.equals(name, publisher.name);
//        }
//        return (id == publisher.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(name);
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(final @NonNull Parcel dest, int flags) {
        //dest.writeLong(id);
        dest.writeString(name);
    }

    protected Publisher(final @NonNull Parcel in) {
        //id = in.readLong();
        name = in.readString();
    }

    public static final Creator<Publisher> CREATOR = new Creator<Publisher>() {
        @Override
        public Publisher createFromParcel(final @NonNull Parcel in) {
            return new Publisher(in);
        }

        @Override
        public Publisher[] newArray(final int size) {
            return new Publisher[size];
        }
    };
}
