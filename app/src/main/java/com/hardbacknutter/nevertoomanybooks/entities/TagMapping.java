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

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;

import com.hardbacknutter.nevertoomanybooks.booklist.style.Style;
import com.hardbacknutter.nevertoomanybooks.database.DBKey;

public class TagMapping
        implements Parcelable, Entity, Comparable<TagMapping> {

    /** {@link Parcelable}. */
    public static final Creator<TagMapping> CREATOR = new Creator<>() {
        @Override
        @NonNull
        public TagMapping createFromParcel(@NonNull final Parcel in) {
            return new TagMapping(in);
        }

        @Override
        @NonNull
        public TagMapping[] newArray(final int size) {
            return new TagMapping[size];
        }
    };

    private static final String JOIN = "\\,";
    private static final Pattern SPLIT = Pattern.compile("\\\\,");

    private long id;
    @NonNull
    private String name;
    @NonNull
    private Set<String> mappings;

    /**
     * Constructor without ID.
     *
     * @param name     for the external site tag
     * @param mappings internal tag names
     */
    public TagMapping(@NonNull final String name,
                      @NonNull final Set<String> mappings) {
        this.name = name;
        this.mappings = mappings;
    }

    public TagMapping(@NonNull final TagMapping source) {
        copyFrom(source);
    }

    /**
     * Full constructor.
     *
     * @param id      ID of the Tag in the database.
     * @param rowData with data
     */
    public TagMapping(final long id,
                      @NonNull final DataHolder rowData) {
        this.id = id;
        name = rowData.getString(DBKey.TAGS.TAG);
        final String tmp = rowData.getString(DBKey.TAGS.TAG_MAPPING);
        mappings = decodeMappingString(tmp);
    }

    private TagMapping(@NonNull final Parcel in) {
        id = in.readLong();
        //noinspection DataFlowIssue
        name = in.readString();
        final List<String> list = new ArrayList<>();
        in.readStringList(list);
        mappings = new HashSet<>(list);
    }

    /**
     * Decode a single string to a set
     * of mappings.
     *
     * @param s to process
     *
     * @return set
     *
     * @see #encodeMappingString(Set)
     */
    @NonNull
    private static Set<String> decodeMappingString(@NonNull final CharSequence s) {
        return Set.of(SPLIT.split(s));
    }

    /**
     * Encode the mappings to a single string.
     *
     * @param mappings to process
     *
     * @return single string
     *
     * @see #decodeMappingString(CharSequence)
     */
    @NonNull
    public static String encodeMappingString(@NonNull final Set<String> mappings) {
        return String.join(JOIN, mappings);
    }

    @Override
    public void writeToParcel(@NonNull final Parcel dest,
                              final int flags) {
        dest.writeLong(id);
        dest.writeString(name);
        dest.writeStringList(new ArrayList<>(mappings));
    }

    @Override
    public int describeContents() {
        return 0;
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
                           @Nullable final Details details,
                           @Nullable final Style style) {
        return name;
    }

    public void copyFrom(@NonNull final TagMapping source) {
        name = source.name;
        // new Set, contents are immutable Strings
        mappings = new HashSet<>(source.mappings);
    }

    @NonNull
    public String getName() {
        return name;
    }

    public void setName(@NonNull final String name) {
        this.name = name;
    }

    @NonNull
    public Set<String> getMappings() {
        return mappings;
    }

    public void setMappings(@NonNull final Set<String> mappings) {
        this.mappings = mappings;
    }

    @Override
    @NonNull
    public String toString() {
        return "TagMapping{"
               + "id=" + id
               + ", name=`" + name + '`'
               + ", mappings=" + mappings
               + '}';
    }

    @Override
    public boolean equals(@Nullable final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final TagMapping that = (TagMapping) o;
        // if both 'exist' but have different ID's -> different.
        if (id != 0 && that.id != 0 && id != that.id) {
            return false;
        }

        // The ids MAY be different, but at least one is != 0
        return Objects.equals(name, that.name)
               && Objects.equals(mappings, that.mappings);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, name, mappings);
    }

    @Override
    public int compareTo(@NonNull final TagMapping o) {
        return name.compareTo(o.name);
    }


}
