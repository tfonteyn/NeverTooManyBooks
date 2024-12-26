/*
 * @Copyright 2018-2024 HardBackNutter
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

import android.os.Parcel;
import android.os.Parcelable;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.Size;

import java.util.Objects;

import com.hardbacknutter.nevertoomanybooks.database.DBKey;

public class Identifier
        implements Parcelable {

    public static final String SID_ASIN = "asin";
    public static final String SID_BEDETHEQUE = "bedetheque";
    public static final String SID_DNB = "dnb";
    public static final String SID_DOUBAN = "douban";
    public static final String SID_GOODREADS_BOOK = "goodreads";
    public static final String SID_GOOGLE = "google";
    public static final String SID_ISFDB = "isfdb";
    public static final String SID_KBNL = "kbnl";
    public static final String SID_LAST_DODO_NL = "lastdodo";
    public static final String SID_LCCN = "lccn";
    public static final String SID_LIBRARY_THING = "librarything";
    public static final String SID_MOBI_ASIN = "mobi-asin";
    public static final String SID_OCLC = "oclc";
    public static final String SID_OPEN_LIBRARY = "openlibrary";
    public static final String SID_STRIP_INFO = "stripinfo";
    public static final String SID_URI = "uri";
    public static final String SID_WIKIDATA = "wikidata";

    public static final char TYPE_LONG = 'L';
    public static final char TYPE_STRING = 'S';
    /** {@link Parcelable}. */
    public static final Creator<Identifier> CREATOR = new Creator<>() {
        @Override
        @NonNull
        public Identifier createFromParcel(@NonNull final Parcel in) {
            return new Identifier(in);
        }

        @Override
        @NonNull
        public Identifier[] newArray(final int size) {
            return new Identifier[size];
        }
    };
    private static final String TAG = "Identifier";
    private long id;
    @NonNull
    private String name;
    @Nullable
    private String description;
    @Nullable
    private String url;

    private char type;

    /**
     * Constructor.
     *
     * @param name        a key(word) for this Identifier. e.g. "oclc"
     *                    The size is not enforced, but should be 15 characters max,
     *                    preferably less.
     * @param type        {@link #TYPE_STRING} or {@link #TYPE_LONG}
     * @param description optional
     * @param url         optional - NOT USED/DEFINED YET
     */
    public Identifier(@Size(max = 15) @NonNull final String name,
                      final char type,
                      @Nullable final String description,
                      @Nullable final String url) {
        this.name = name;
        this.type = type;
        this.description = description;
        this.url = url;
    }

    /**
     * Constructor.
     *
     * @param id      ID of the Author in the database.
     * @param rowData with data
     */
    public Identifier(final long id,
                      @NonNull final DataHolder rowData) {
        this.id = id;
        name = rowData.getString(DBKey.IDENT_NAME);
        type = rowData.getString(DBKey.IDENT_TYPE).charAt(0);
        description = rowData.getString(DBKey.IDENT_DESC, null);
        url = rowData.getString(DBKey.IDENT_URL, null);
    }

    protected Identifier(@NonNull final Parcel in) {
        id = in.readLong();
        //noinspection DataFlowIssue
        name = in.readString();
        type = (char) in.readInt();
        description = in.readString();
        url = in.readString();
    }

    @Override
    public void writeToParcel(@NonNull final Parcel dest,
                              final int flags) {
        dest.writeLong(id);
        dest.writeString(name);
        dest.writeInt(type);
        dest.writeString(description);
        dest.writeString(url);
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

    /**
     * Check the type.
     *
     * @return {@code L} for a {@code long}, {@code S} for a {@code String}
     */
    public char getType() {
        return type;
    }

    public void setType(final char type) {
        this.type = type;
    }

    @NonNull
    public String getName() {
        return name;
    }

    public void setName(@NonNull final String name) {
        this.name = name;
    }

    @Nullable
    public String getDescription() {
        return description;
    }

    public void setDescription(@Nullable final String description) {
        this.description = description;
    }

    @Nullable
    public String getUrl() {
        return url;
    }

    public void setUrl(@Nullable final String url) {
        this.url = url;
    }

    @Override
    @NonNull
    public String toString() {
        return "Identifier{"
               + "id=" + id
               + ", name='" + name + '\''
               + ", type='" + type + '\''
               + ", description='" + description + '\''
               + ", url='" + url + '\''
               + '}';
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, name, type, description, url);
    }

    @Override
    public boolean equals(@Nullable final Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj == null || getClass() != obj.getClass()) {
            return false;
        }
        final Identifier that = (Identifier) obj;
        // if both 'exist' but have different ID's -> different.
        if (id != 0 && that.id != 0 && id != that.id) {
            return false;
        }
        return Objects.equals(name, that.name)
               && type == that.type
               && Objects.equals(description, that.description)
               && Objects.equals(url, that.url);
    }

    public static class Value
            implements Parcelable {

        /** {@link Parcelable}. */
        public static final Creator<Value> CREATOR = new Creator<>() {
            @Override
            @NonNull
            public Value createFromParcel(@NonNull final Parcel in) {
                return new Value(in);
            }

            @Override
            @NonNull
            public Value[] newArray(final int size) {
                return new Value[size];
            }
        };

        @NonNull
        private final Identifier identifier;
        @NonNull
        private final String sid;

        public Value(@NonNull final Identifier identifier,
                     @NonNull final String sid) {
            this.identifier = identifier;
            this.sid = sid;
        }

        protected Value(@NonNull final Parcel in) {
            identifier = in.readParcelable(getClass().getClassLoader());
            sid = in.readString();
        }

        @Override
        public void writeToParcel(@NonNull final Parcel dest,
                                  final int flags) {
            dest.writeParcelable(identifier, flags);
            dest.writeString(sid);
        }

        @Override
        public int describeContents() {
            return 0;
        }

        @NonNull
        public Identifier getIdentifier() {
            return identifier;
        }

        @NonNull
        public String getSid() {
            return sid;
        }

        @Override
        @NonNull
        public String toString() {
            return "Value{"
                   + "identifier=" + identifier
                   + ", value='" + sid + '\''
                   + '}';
        }

        @Override
        public int hashCode() {
            return Objects.hash(identifier, sid);
        }

        @Override
        public boolean equals(@Nullable final Object obj) {
            if (this == obj) {
                return true;
            }
            if (obj == null || getClass() != obj.getClass()) {
                return false;
            }
            final Value that = (Value) obj;
            return Objects.equals(identifier, that.identifier)
                   && Objects.equals(sid, that.sid);
        }
    }
}
