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
 * <li>type: {@code 'L'} or {@code 'S'}, see below.</li>
 * <li>name: a non-localized short name to show to the user.
 *           Can be empty for user created key.</li>
 * <li>sid: the actual value of the identifier field</li>
 * </ul>
 * The type is used in two situations only.
 * <ol>
 *     <li>When storing a book, {@code TYPE_LONG} identifiers are checked for
 *         being a valid {@code long}. If they fail, they are removed.
 *         {@code TYPE_STRING} is always stored unless it's empty.
 *     </li>
 *     <li>The screen where the user can directly edit identifier values will
 *         show a numeric or full keyboard depending on the type just for convenience.
 *     </li>
 * </ol>
 * i.o.w. the type {@code TYPE_LONG} is only used in the predefined Identifiers,
 * and an unknown identifier is always assumed to be a {@code TYPE_STRING}.
 */
public class Identifier
        implements Parcelable {

    public static final String SID_ASIN = "asin";
    public static final String SID_BEDETHEQUE = "bedetheque";
    /** <a href="https://www.bnf.fr">www.bnf.fr</a>. */
    public static final String SID_BNF = "bnf";
    /** <a href="https://www.bl.uk/">www.bl.uk</a>. */
    public static final String SID_BRITISH_LIBRARY = "bl";
    /** <a href="https://www.dnb.de">www.dnb.de</a>. */
    public static final String SID_DNB = "dnb";
    /** <a href="https://www.doi.org">www.doi.org</a>. */
    public static final String SID_DOI = "doi";
    public static final String SID_DOUBAN = "douban";
    public static final String SID_GOODREADS_BOOK = "goodreads";
    public static final String SID_GOOGLE = "google";
    public static final String SID_ISFDB = "isfdb";
    /** <a href="https://www.kb.nl">www.kb.nl</a>. */
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
    public static final String SID_URL = "url";
    /** <a href="https://www.wikidata.org">www.wikidata.org</a>. */
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

    public static final int MAX_KEY_LEN = 15;
    @NonNull
    private final String key;
    @NonNull
    private final String name;
    private final char type;
    private long id;

    /**
     * Constructor.
     *
     * @param key  a key(word) for this Identifier. e.g. "oclc"
     *             The size is not enforced, but should be {@link #MAX_KEY_LEN}
     *             characters max, preferably less.
     * @param type {@link #TYPE_STRING} or {@link #TYPE_LONG}
     * @param name the NOT-LOCALIZED short name
     */
    public Identifier(@Size(max = MAX_KEY_LEN) @NonNull final String key,
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
                               context.getString(R.string.identifier_amazon_asin)),
                new Identifier(SID_BEDETHEQUE, TYPE_LONG,
                               context.getString(R.string.identifier_bedetheque)),
                new Identifier(SID_BNF, TYPE_STRING,
                               context.getString(R.string.identifier_bnf)),
                new Identifier(SID_BRITISH_LIBRARY, TYPE_LONG,
                               context.getString(R.string.identifier_british_library)),
                new Identifier(SID_DNB, TYPE_LONG,
                               context.getString(R.string.identifier_dnb)),
                new Identifier(SID_DOI, TYPE_STRING,
                               context.getString(R.string.identifier_doi)),
                new Identifier(SID_DOUBAN, TYPE_LONG,
                               context.getString(R.string.identifier_douban)),
                new Identifier(SID_GOODREADS_BOOK, TYPE_LONG,
                               context.getString(R.string.identifier_goodreads)),
                new Identifier(SID_GOOGLE, TYPE_STRING,
                               context.getString(R.string.identifier_google_books)),
                new Identifier(SID_ISFDB, TYPE_LONG,
                               context.getString(R.string.identifier_isfdb)),
                new Identifier(SID_KBNL, TYPE_LONG,
                               context.getString(R.string.identifier_kb_nl)),
                new Identifier(SID_LAST_DODO_NL, TYPE_LONG,
                               context.getString(R.string.identifier_lastdodo_nl)),
                new Identifier(SID_LCCN, TYPE_STRING,
                               context.getString(R.string.identifier_lccn)),
                new Identifier(SID_LIBRARY_THING, TYPE_LONG,
                               context.getString(R.string.identifier_library_thing)),
                new Identifier(SID_MOBI_ASIN, TYPE_STRING,
                               context.getString(R.string.identifier_amazon_mobi_asin)),
                new Identifier(SID_OCLC, TYPE_STRING,
                               context.getString(R.string.identifier_worldcat)),
                new Identifier(SID_OPEN_LIBRARY, TYPE_STRING,
                               context.getString(R.string.identifier_open_library)),
                new Identifier(SID_STRIP_INFO, TYPE_LONG,
                               context.getString(R.string.identifier_stripinfo_be)),
                new Identifier(SID_STRIPWEB, TYPE_LONG,
                               context.getString(R.string.identifier_stripweb_be)),
                new Identifier(SID_URI, TYPE_STRING,
                               context.getString(R.string.identifier_uri)),
                new Identifier(SID_URL, TYPE_STRING,
                               context.getString(R.string.identifier_url)),
                new Identifier(SID_WIKIDATA, TYPE_STRING,
                               context.getString(R.string.identifier_wikidata))
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

    /**
     * <strong>DAO use only.</strong>
     *
     * @return id
     */
    public long getId() {
        return id;
    }

    /**
     * <strong>DAO use only.</strong>
     *
     * @param id to set
     */
    public void setId(final long id) {
        this.id = id;
    }

    /**
     * Get the type.
     *
     * @return {@code L} for a {@code long}, {@code S} for a {@code String}
     */
    public char getType() {
        return type;
    }

    /**
     * Get the Identifier key.
     *
     * @return key
     */
    @NonNull
    public String getKey() {
        return key;
    }

    /**
     * Get the user displayable name.
     *
     * @return name
     */
    @NonNull
    public String getName() {
        return name;
    }

    @Override
    @NonNull
    public String toString() {
        return "Identifier{"
               + "id=" + id
               + ", key=`" + key + '`'
               + ", type=`" + type + '`'
               + ", name=`" + name + '`'
               + '}';
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, key, type, name);
    }

    @Override
    public boolean equals(@Nullable final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final Identifier that = (Identifier) o;
        // if both 'exist' but have different ID's -> different.
        if (id != 0 && that.id != 0 && id != that.id) {
            return false;
        }

        // The ids MAY be different, but at least one is != 0
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
        private final String key;
        @NonNull
        private final String sid;

        public Value(@NonNull final String key,
                     @NonNull final String sid) {
            this.key = key;
            this.sid = sid;
        }

        public Value(@NonNull final String key,
                     final long sid) {
            this.key = key;
            this.sid = String.valueOf(sid);
        }

        private Value(@NonNull final Parcel in) {
            //noinspection DataFlowIssue
            key = in.readString();
            //noinspection DataFlowIssue
            sid = in.readString();
        }

        @Override
        public void writeToParcel(@NonNull final Parcel dest,
                                  final int flags) {
            dest.writeString(key);
            dest.writeString(sid);
        }

        @Override
        public int describeContents() {
            return 0;
        }

        /**
         * Get the {@link Identifier} key.
         *
         * @return key
         */
        @NonNull
        public String getKey() {
            return key;
        }

        /**
         * Get the external website id (site-id, sid).
         *
         * @return sid
         */
        @NonNull
        public String getSid() {
            return sid;
        }

        @Override
        @NonNull
        public String toString() {
            return "Value{"
                   + "key=" + key
                   + ", sid=`" + sid + '`'
                   + '}';
        }

        @Override
        public int hashCode() {
            return Objects.hash(key, sid);
        }

        @Override
        public boolean equals(@Nullable final Object o) {
            if (this == o) {
                return true;
            }
            if (o == null || getClass() != o.getClass()) {
                return false;
            }
            final Value that = (Value) o;
            return Objects.equals(key, that.key)
                   && Objects.equals(sid, that.sid);
        }
    }
}
