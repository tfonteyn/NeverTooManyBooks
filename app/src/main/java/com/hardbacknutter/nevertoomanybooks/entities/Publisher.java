/*
 * @Copyright 2018-2023 HardBackNutter
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

import com.hardbacknutter.nevertoomanybooks.ServiceLocator;
import com.hardbacknutter.nevertoomanybooks.booklist.style.Style;
import com.hardbacknutter.nevertoomanybooks.database.DBKey;
import com.hardbacknutter.nevertoomanybooks.utils.ReorderHelper;

/**
 * Represents a Publisher.
 * <p>
 * ENHANCE: The Publisher Locale should be based on the country where they are.
 */
public class Publisher
        implements Parcelable, Entity, Mergeable {

    /** {@link Parcelable}. */
    public static final Creator<Publisher> CREATOR = new Creator<>() {
        @Override
        @NonNull
        public Publisher createFromParcel(@NonNull final Parcel source) {
            return new Publisher(source);
        }

        @Override
        @NonNull
        public Publisher[] newArray(final int size) {
            return new Publisher[size];
        }
    };

    /** Row ID. */
    private long id;
    /** Publisher name. */
    @NonNull
    private String name;

    /**
     * Constructor.
     *
     * @param name of publisher.
     */
    public Publisher(@NonNull final String name) {
        this.name = name.trim();
    }

    /**
     * Full constructor.
     *
     * @param id      ID of the Publisher in the database.
     * @param rowData with data
     */
    public Publisher(final long id,
                     @NonNull final DataHolder rowData) {
        this.id = id;
        name = rowData.getString(DBKey.PUBLISHER_NAME);
    }

    /**
     * {@link Parcelable} Constructor.
     *
     * @param in Parcel to construct the object from
     */
    private Publisher(@NonNull final Parcel in) {
        id = in.readLong();
        //noinspection ConstantConditions
        name = in.readString();
    }

    /**
     * Constructor.
     *
     * @param name of publisher.
     *
     * @return Publisher
     */
    @NonNull
    public static Publisher from(@NonNull final String name) {
        return new Publisher(name);
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
    public long getId() {
        return id;
    }

    public void setId(final long id) {
        this.id = id;
    }

    @Override
    @NonNull
    public String getLabel(@NonNull final Context context,
                           @Nullable final Details details,
                           @Nullable final Style style) {
        final ReorderHelper reorderHelper = ServiceLocator.getInstance().getReorderHelper();
        if (reorderHelper.forDisplay(context)) {
            // Using the locale here is overkill;  see #getLocale(..)
            return reorderHelper.reorder(context, name);
        } else {
            return name;
        }
    }

    /**
     * Get the <strong>unformatted</strong> name.
     *
     * @return the name
     */
    @NonNull
    public String getName() {
        return name;
    }

    /**
     * Set the unformatted name; as entered manually by the user.
     *
     * @param name to use
     */
    public void setName(@NonNull final String name) {
        this.name = name;
    }

    /**
     * Replace local details from another publisher.
     *
     * @param source publisher to copy from
     */
    public void copyFrom(@NonNull final Publisher source) {
        name = source.name;
    }

    @NonNull
    @Override
    public List<String> getNameFields() {
        return List.of(name);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, name);
    }

    /**
     * Equality.
     * <ol>
     * <li>it's the same Object</li>
     * <li>one or both of them are 'new' (e.g. id == 0) or have the same id<br>
     *     AND the names are equal</li>
     * <li>if both are 'new' check if family/given-names are equal</li>
     * </ol>
     *
     * <strong>Comparing is DIACRITIC and CASE SENSITIVE</strong>:
     * This allows correcting case mistakes even with identical ID.
     */
    @Override
    public boolean equals(@Nullable final Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj == null || getClass() != obj.getClass()) {
            return false;
        }
        final Publisher that = (Publisher) obj;
        // if both 'exist' but have different ID's -> different.
        if (id != 0 && that.id != 0 && id != that.id) {
            return false;
        }
        return Objects.equals(name, that.name);
    }

    @Override
    @NonNull
    public String toString() {
        return "Publisher{"
               + "id=" + id
               + ", name=`" + name + '`'
               + '}';
    }
}
