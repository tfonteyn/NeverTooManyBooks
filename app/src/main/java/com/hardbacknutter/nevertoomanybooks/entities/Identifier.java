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
import androidx.annotation.Size;

import java.util.List;
import java.util.Objects;

import com.hardbacknutter.nevertoomanybooks.R;
import com.hardbacknutter.nevertoomanybooks.database.DBKey;

/**
 * External website id's (site-id, sid).
 * <ul>
 * <li>key: a unique keyword; never to be changed; used as bundle keys and import/export</li>
 * <li>name: a non-localized short name to show to the user.</li>
 * <li>sid: the actual value</li>
 * </ul>
 */
public class Identifier
        implements Parcelable {

    public static final String SID_ASIN = "asin";
    public static final String SID_BEDETHEQUE = "bedetheque";
    public static final String SID_BNF = "bnf";
    public static final String SID_BRITISH_LIBRARY = "bl";
    public static final String SID_DNB = "dnb";
    public static final String SID_DOI = "doi";
    public static final String SID_DOUBAN = "douban";
    public static final String SID_GOODREADS_BOOK = "goodreads";
    public static final String SID_GOOGLE = "google";
    public static final String SID_ISFDB = "isfdb";
    public static final String SID_KBNL = "ppn";
    public static final String SID_LAST_DODO_NL = "lastdodo";
    public static final String SID_LCCN = "lccn";
    public static final String SID_LIBRARY_THING = "librarything";
    public static final String SID_MOBI_ASIN = "mobi-asin";
    public static final String SID_OCLC = "oclc";
    public static final String SID_OPEN_LIBRARY = "openlibrary";
    public static final String SID_STRIP_INFO = "stripinfo";
    public static final String SID_STRIPWEB = "stripweb";
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

    private long id;
    @NonNull
    private String key;
    @NonNull
    private String name;

    private char type;

    /**
     * Constructor.
     *
     * @param key  a key(word) for this Identifier. e.g. "oclc"
     *             The size is not enforced, but should be 15 characters max,
     *             preferably less.
     * @param type {@link #TYPE_STRING} or {@link #TYPE_LONG}
     * @param name the NOT-LOCALIZED short name
     */
    public Identifier(@Size(max = 15) @NonNull final String key,
                      final char type,
                      @NonNull final String name) {
        this.key = key;
        this.type = type;
        this.name = name;
    }

    /**
     * Constructor.
     *
     * @param id      ID of the Identifier in the database.
     * @param rowData with data
     */
    public Identifier(final long id,
                      @NonNull final DataHolder rowData) {
        this.id = id;
        key = rowData.getString(DBKey.IDENT_KEY);
        type = rowData.getString(DBKey.IDENT_TYPE).charAt(0);
        name = rowData.getString(DBKey.IDENT_NAME);
    }

    protected Identifier(@NonNull final Parcel in) {
        id = in.readLong();
        //noinspection DataFlowIssue
        key = in.readString();
        type = (char) in.readInt();
        //noinspection DataFlowIssue
        name = in.readString();
    }

    /**
     * Used only at <strong>installation/upgrade</strong> time to create the initial set
     * in the database.
     *
     * @param context Current context
     *
     * @return list
     */
    @SuppressWarnings("StaticMethodOnlyUsedInOneClass")
    @NonNull
    public static List<Identifier> createInitialList(@NonNull final Context context) {
        return List.of(
                new Identifier(SID_ASIN, TYPE_STRING,
                               context.getString(R.string.site_amazon)),
                new Identifier(SID_BEDETHEQUE, TYPE_LONG,
                               context.getString(R.string.site_bedetheque)),
                new Identifier(SID_BNF, TYPE_STRING,
                               context.getString(R.string.site_bnf)),
                new Identifier(SID_BRITISH_LIBRARY, TYPE_LONG,
                               context.getString(R.string.site_british_library)),
                new Identifier(SID_DNB, TYPE_LONG,
                               context.getString(R.string.site_dnb_de)),
                new Identifier(SID_DOI, TYPE_STRING,
                               context.getString(R.string.site_doi)),
                new Identifier(SID_DOUBAN, TYPE_LONG,
                               context.getString(R.string.site_douban)),
                new Identifier(SID_GOODREADS_BOOK, TYPE_LONG,
                               context.getString(R.string.site_goodreads)),
                new Identifier(SID_GOOGLE, TYPE_STRING,
                               context.getString(R.string.site_google_books)),
                new Identifier(SID_ISFDB, TYPE_LONG,
                               context.getString(R.string.site_isfdb)),
                new Identifier(SID_KBNL, TYPE_LONG,
                               context.getString(R.string.site_kb_nl)),
                new Identifier(SID_LAST_DODO_NL, TYPE_LONG,
                               context.getString(R.string.site_lastdodo_nl)),
                new Identifier(SID_LCCN, TYPE_STRING,
                               context.getString(R.string.site_lccn)),
                new Identifier(SID_LIBRARY_THING, TYPE_LONG,
                               context.getString(R.string.site_library_thing)),
                new Identifier(SID_MOBI_ASIN, TYPE_STRING,
                               context.getString(R.string.site_amazon)),
                new Identifier(SID_OCLC, TYPE_STRING,
                               context.getString(R.string.site_worldcat)),
                new Identifier(SID_OPEN_LIBRARY, TYPE_STRING,
                               context.getString(R.string.site_open_library)),
                new Identifier(SID_STRIP_INFO, TYPE_LONG,
                               context.getString(R.string.site_stripinfo_be)),
                new Identifier(SID_STRIPWEB, TYPE_LONG,
                               context.getString(R.string.site_stripweb_be)),
                new Identifier(SID_URI, TYPE_STRING,
                               "URI/URL")
        );
    }

    @Override
    public void writeToParcel(@NonNull final Parcel dest,
                              final int flags) {
        dest.writeLong(id);
        dest.writeString(key);
        dest.writeInt(type);
        dest.writeString(name);
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
    public String getKey() {
        return key;
    }

    public void setKey(@NonNull final String key) {
        this.key = key;
    }

    @NonNull
    public String getName() {
        return name;
    }

    public void setName(@NonNull final String name) {
        this.name = name;
    }

    @Override
    @NonNull
    public String toString() {
        return "Identifier{"
               + "id=" + id
               + ", key='" + key + '\''
               + ", type='" + type + '\''
               + ", name='" + name + '\''
               + '}';
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, key, type, name);
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
        return Objects.equals(key, that.key)
               && type == that.type
               && Objects.equals(name, that.name);
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
            //noinspection DataFlowIssue
            identifier = in.readParcelable(getClass().getClassLoader());
            //noinspection DataFlowIssue
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
