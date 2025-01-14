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

package com.hardbacknutter.nevertoomanybooks.entities;

import android.content.Context;
import android.os.Parcel;
import android.os.Parcelable;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.List;
import java.util.Objects;

import com.hardbacknutter.nevertoomanybooks.booklist.style.Style;
import com.hardbacknutter.nevertoomanybooks.database.DBKey;

public class Tag
        implements Parcelable, Entity, Mergeable, Comparable<Tag> {

    /** {@link Parcelable}. */
    public static final Creator<Tag> CREATOR = new Creator<Tag>() {
        @Override
        @NonNull
        public Tag createFromParcel(@NonNull final Parcel in) {
            return new Tag(in);
        }

        @Override
        @NonNull
        public Tag[] newArray(final int size) {
            return new Tag[size];
        }
    };

    private long id;
    @NonNull
    private String name;

    /**
     * Constructor without ID.
     *
     * @param name for the Tag
     */
    public Tag(@NonNull final String name) {
        this.name = name;
    }

    /**
     * Full constructor.
     *
     * @param id      ID of the Tag in the database.
     * @param rowData with data
     */
    public Tag(final long id,
               @NonNull final DataHolder rowData) {
        this.id = id;
        name = rowData.getString(DBKey.TAG);
    }

    private Tag(@NonNull final Parcel in) {
        id = in.readLong();
        //noinspection DataFlowIssue
        name = in.readString();
    }

    public long getId() {
        return id;
    }

    @NonNull
    @Override
    public String getLabel(@NonNull final Context context,
                           @NonNull final Details details,
                           @NonNull final Style style) {
        return name;
    }

    public void setId(final long id) {
        this.id = id;
    }

    @NonNull
    @Override
    public List<String> getNameFields() {
        return List.of(name);
    }

    @NonNull
    public String getName() {
        return name;
    }

    public void setName(@NonNull final String name) {
        this.name = name;
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(@NonNull final Parcel dest,
                              final int flags) {
        dest.writeLong(id);
        dest.writeString(name);
    }

    @Override
    @NonNull
    public String toString() {
        return "Tag{"
               + "id=" + id
               + ", tag='" + name + '\''
               + '}';
    }

    @Override
    public boolean equals(@Nullable final Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj == null || getClass() != obj.getClass()) {
            return false;
        }
        final Tag that = (Tag) obj;
        // if both 'exist' but have different ID's -> different.
        if (id != 0 && that.id != 0 && id != that.id) {
            return false;
        }
        return Objects.equals(name, that.name);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, name);
    }

    @Override
    public int compareTo(@NonNull final Tag o) {
        return name.compareTo(o.name);
    }
}
