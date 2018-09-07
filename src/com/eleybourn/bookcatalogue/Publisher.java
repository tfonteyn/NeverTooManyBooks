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

package com.eleybourn.bookcatalogue;

import android.os.Parcel;
import android.os.Parcelable;

import com.eleybourn.bookcatalogue.utils.ArrayUtils;

import java.io.Serializable;
import java.util.Objects;

/**
 * Class to hold Publisher data. Used in lists.
 *
 */
public class Publisher implements Serializable {
	private static final long serialVersionUID = 1L;

	public String name;

	public Publisher(String name) {
		this.name = name.trim();
	}

	/**
	 * Return the 'human readable' version of the name
	 *
	 * @return	name
	 */
	public String getDisplayName() {
			return name;
	}

	// Support for encoding to a text file
	@Override
	public String toString() {
		return ArrayUtils.encodeListItem(name, ',');
	}

	/**
	 * Support for creation via Parcelable. This is primarily useful for passing
	 * ArrayList<Publisher> in Bundles to activities.
	 */
    public static final Parcelable.Creator<Publisher> CREATOR
            = new Parcelable.Creator<Publisher>() {
        public Publisher createFromParcel(Parcel in) {
            return new Publisher(in);
        }

        public Publisher[] newArray(int size) {
            return new Publisher[size];
        }
    };

    /**
     * Replace local details from another publisher
     *
     * @param source	publisher to copy
     */
    void copyFrom(Publisher source) {
		name = source.name;
    }

    /**
     * Constructor using a Parcel.
     */
    private Publisher(Parcel in) {
    	name = in.readString();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Publisher publisher = (Publisher) o;
        return Objects.equals(name, publisher.name);
    }

    @Override
    public int hashCode() {
        return Objects.hash(name);
    }
}
