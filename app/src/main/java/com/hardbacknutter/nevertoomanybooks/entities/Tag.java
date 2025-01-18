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
import androidx.annotation.VisibleForTesting;

import java.util.List;
import java.util.Locale;
import java.util.Objects;

import com.hardbacknutter.nevertoomanybooks.booklist.style.Style;
import com.hardbacknutter.nevertoomanybooks.database.DBKey;

public class Tag
        implements Parcelable, Entity, Mergeable, Comparable<Tag> {

    /** {@link Parcelable}. */
    public static final Creator<Tag> CREATOR = new Creator<>() {
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
     * Testing only; the caller must ensure correct upper/lowercase usage.
     *
     * @param name for the Tag
     */
    @VisibleForTesting
    public Tag(@NonNull final String name) {
        this.name = name;
    }

    /**
     * Copy constructor.
     *
     * @param tag to copy
     */
    public Tag(final Tag tag) {
        copyFrom(tag);
    }

    /**
     * Constructor without ID.
     *
     * @param name   for the Tag
     * @param locale for normalizing the name
     *
     * @see #normalize(String, Locale)
     */
    public Tag(@NonNull final String name,
               @NonNull final Locale locale) {
        this.name = normalize(name, locale);
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

    @NonNull
    public static String normalize(@NonNull final String sentence,
                                   @NonNull final Locale locale) {
        if (sentence.isBlank()) {
            return "";
        } else if (sentence.length() == 1) {
            return sentence.toUpperCase(locale);
        }

        return sentence.substring(0, 1).toUpperCase(locale) + sentence.substring(1);
    }

    public long getId() {
        return id;
    }

    public void setId(final long id) {
        this.id = id;
    }

    @NonNull
    @Override
    public String getLabel(@NonNull final Context context,
                           @NonNull final Details details,
                           @NonNull final Style style) {
        return name;
    }

    @NonNull
    @Override
    public List<String> getNameFields() {
        return List.of(name);
    }

    /**
     * Get the (normalized) name.
     *
     * @return "The Name"
     */
    @NonNull
    public String getName() {
        return name;
    }

    /**
     * Set the name.
     *
     * @param name   for the Tag
     * @param locale for normalizing the name
     *
     * @see #normalize(String, Locale)
     */
    public void setName(@NonNull final String name,
                        @NonNull final Locale locale) {
        this.name = normalize(name, locale);
    }

    /**
     * Replace local details from another Tag.
     *
     * @param source to copy from
     */
    public void copyFrom(@NonNull final Tag source) {
        name = source.name;
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

    /**
     * Equality: <strong>id, name</strong>.
     * <p>
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
