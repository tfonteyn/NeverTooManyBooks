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
import java.util.Optional;

import com.hardbacknutter.nevertoomanybooks.R;
import com.hardbacknutter.nevertoomanybooks.booklist.style.Style;
import com.hardbacknutter.nevertoomanybooks.database.DBKey;
import com.hardbacknutter.nevertoomanybooks.searchengines.EngineId;

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
        implements Parcelable, Entity {

    public static final String SID_ASIN = "asin";
    public static final String SID_AUDIBLE = "audible-asin";
    public static final String SID_BARNES_AND_NOBLE = "bn";
    public static final String SID_BEDETHEQUE = "bedetheque";
    public static final String SID_BNF = "bnf";
    public static final String SID_BRITISH_LIBRARY = "bl";
    public static final String SID_DNB = "dnb";
    public static final String SID_DOI = "doi";
    public static final String SID_DOUBAN = "douban";
    public static final String SID_FANTLAB = "fantlab";
    /** The original Goodreads "legacyId" which is in widespread use. */
    public static final String SID_GOODREADS = "goodreads";
    public static final String SID_GOOGLE = "google";
    public static final String SID_ISFDB = "isfdb";
    public static final String SID_KBNL = "ppn";
    public static final String SID_KBR = "kbr";
    public static final String SID_LAST_DODO_NL = "lastdodo";
    public static final String SID_LCCN = "lccn";
    public static final String SID_LIBRARY_THING = "librarything";
    public static final String SID_LIBRIS = "onr";
    public static final String SID_LIBRIS_XL = "libris";
    public static final String SID_NILF = "nilf";
    public static final String SID_NOOSFERE = "noosfere";
    public static final String SID_OCLC = "oclc";
    public static final String SID_OPEN_LIBRARY = "openlibrary";
    public static final String SID_PORBASE = "porbase";
    public static final String SID_STRIPWEB = "stripweb";
    public static final String SID_STRIP_INFO = "stripinfo";
    public static final String SID_TERCERA_FUNDACION = "ltf";
    public static final String SID_URI = "uri";
    public static final String SID_WIKIDATA = "wikidata";

    public static final char TYPE_LONG = 'L';
    public static final char TYPE_STRING = 'S';

    public static final int MAX_KEY_LEN = 15;

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

    @NonNull
    private final String key;
    @NonNull
    private final String name;
    @Nullable
    private final String siteUrl;
    @Nullable
    private final String bookUri;
    @Nullable
    private final String authorUri;
    private final char type;
    private long id;

    /**
     * Constructor to add unknown Identifiers as found in book searches.
     *
     * @param key a key(word) for this Identifier
     */
    public Identifier(@Size(max = MAX_KEY_LEN) @NonNull final String key) {
        this.key = key;
        this.type = TYPE_STRING;
        this.name = key;
        this.siteUrl = null;
        this.bookUri = null;
        this.authorUri = null;
    }

    /**
     * Constructor for the predefined Identifiers.
     * Will be used when updated app versions bring new and TESTED urls.
     *
     * @param key       a key(word) for this Identifier. e.g. "oclc"
     *                  The size is not enforced, but should be {@link #MAX_KEY_LEN}
     *                  characters max, preferably less.
     * @param type      {@link #TYPE_STRING} or {@link #TYPE_LONG}
     * @param name      a short name
     * @param siteUrl   url to the main website page
     * @param bookUri   a url with a {@code %s%} placeholder for the sid,
     *                  to view a {@code Book} on the site
     * @param authorUri a url with a {@code %s%} placeholder for the sid,
     *                  to view an {@code Author} on the site
     */
    public Identifier(@Size(max = MAX_KEY_LEN) @NonNull final String key,
                      final char type,
                      @NonNull final String name,
                      @Nullable final String siteUrl,
                      @Nullable final String bookUri,
                      @Nullable final String authorUri) {
        this.key = key;
        this.type = type;
        this.name = name;
        this.siteUrl = siteUrl;
        this.bookUri = bookUri;
        this.authorUri = authorUri;
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
        key = rowData.getString(DBKey.IDENTIFIERS.KEY);
        type = rowData.getString(DBKey.IDENTIFIERS.TYPE).charAt(0);
        name = rowData.getString(DBKey.IDENTIFIERS.NAME);
        siteUrl = rowData.getString(DBKey.IDENTIFIERS.SITE_URL, null);
        bookUri = rowData.getString(DBKey.IDENTIFIERS.BOOK_URI, null);
        authorUri = rowData.getString(DBKey.IDENTIFIERS.AUTHOR_URI, null);
    }

    protected Identifier(@NonNull final Parcel in) {
        id = in.readLong();
        //noinspection DataFlowIssue
        key = in.readString();
        type = (char) in.readInt();
        //noinspection DataFlowIssue
        name = in.readString();
        siteUrl = in.readString();
        bookUri = in.readString();
        authorUri = in.readString();
    }

    /**
     * Used only at <strong>installation/upgrade</strong> time to create the initial set
     * in the database.
     * <p>
     * TODO: stick these in an sql batch file
     *
     * @param context Current context
     *
     * @return list
     */
    @SuppressWarnings("StaticMethodOnlyUsedInOneClass")
    @NonNull
    public static List<Identifier> createInitialList(@NonNull final Context context) {
        // links have been verified at the date listed.
        return List.of(
                // links empty on purpose
                new Identifier(SID_ASIN, TYPE_STRING,
                               context.getString(R.string.identifier_amazon),
                               null,
                               null,
                               null),
                // 2025-01-29
                new Identifier(SID_AUDIBLE, TYPE_STRING,
                               context.getString(R.string.identifier_audible),
                               "https://www.audible.com",
                               "https://www.audible.com/pd/%s",
                               null),
                // 2025-01-29
                new Identifier(SID_BARNES_AND_NOBLE, TYPE_LONG,
                               context.getString(R.string.identifier_barnesandnoble),
                               "https://www.barnesandnoble.com",
                               "https://www.barnesandnoble.com/w/%s",
                               null),
                // 2025-01-29
                new Identifier(SID_BEDETHEQUE, TYPE_LONG,
                               context.getString(R.string.identifier_bedetheque),
                               "https://www.bedetheque.com",
                               "https://www.bedetheque.com/BD-x-%s.html",
                               "https://www.bedetheque.com/auteur-%s-BD-x.html"),
                // 2025-01-29
                new Identifier(SID_BNF, TYPE_STRING,
                               context.getString(R.string.identifier_bnf),
                               "https://www.bnf.fr",
                               // not entirely sure if we should use the host "ark"
                               // or "catalogue"
                               "http://ark.bnf.fr/ark:/12148/%s",
                               "https://catalogue.bnf.fr/ark:/12148/%s"),
                // FIXME: BL link disabled for now due to https://www.bl.uk/cyber-incident/
                // The British National Bibliography ??
                new Identifier(SID_BRITISH_LIBRARY, TYPE_STRING,
                               context.getString(R.string.identifier_british_library),
                               "https://www.bl.uk",
                               null,
                               null),
                // 2025-01-29
                new Identifier(SID_DNB, TYPE_STRING,
                               context.getString(R.string.identifier_dnb),
                               "https://www.dnb.de",
                               "https://d-nb.info/%s",
                               "https://d-nb.info/gnd/%s"),
                // FIXME: openlibrary  https://www.doi.org/%s
                // but none of the openlibrary provided doi numbers
                // we tried are resolving, so leaving bookUrl/authorUrl empty on purpose.
                new Identifier(SID_DOI, TYPE_STRING,
                               context.getString(R.string.identifier_doi),
                               "https://www.doi.org",
                               null,
                               null),
                // 2025-01-29
                new Identifier(SID_DOUBAN, TYPE_LONG,
                               context.getString(R.string.identifier_douban),
                               "https://book.douban.com",
                               "https://book.douban.com/subject/%s",
                               "https://www.douban.com/personage/%s"),
                // 2025-01-29
                new Identifier(SID_FANTLAB, TYPE_LONG,
                               context.getString(R.string.identifier_fantlab),
                               "https://fantlab.ru",
                               "https://fantlab.ru/edition%s",
                               "https://fantlab.ru/autor%s"),
                // 2025-01-29
                new Identifier(SID_GOODREADS, TYPE_LONG,
                               context.getString(R.string.identifier_goodreads),
                               "https://www.goodreads.com",
                               "https://www.goodreads.com/book/show/%s",
                               "https://www.goodreads.com/author/show/%s"),
                // 2025-01-29
                new Identifier(SID_GOOGLE, TYPE_STRING,
                               context.getString(R.string.identifier_google_books),
                               "https://books.google.com",
                               "https://books.google.co.uk/books?id=%s",
                               null),
                // 2025-01-29
                new Identifier(SID_ISFDB, TYPE_LONG,
                               context.getString(R.string.identifier_isfdb),
                               "https://www.isfdb.org",
                               "https://www.isfdb.org/cgi-bin/pl.cgi?%s",
                               "https://www.isfdb.org/cgi-bin/ea.cgi?%s"),
                // 2025-01-29
                new Identifier(SID_KBNL, TYPE_LONG,
                               context.getString(R.string.identifier_kb_nl),
                               "https://www.kb.nl",
                               "https://webggc.oclc.org/cbs/DB=2.37/XMLPRS=Y/PPN?PPN=%s",
                               "https://webggc.oclc.org/cbs/DB=2.37/REL?PPN=%s"),
                // 2025-01-29
                new Identifier(SID_KBR, TYPE_LONG,
                               context.getString(R.string.identifier_kbr),
                               "https://opac.kbr.be",
                               "https://opac.kbr.be/Library/doc/SYRACUSE/%s",
                               null),
                // 2025-01-29
                new Identifier(SID_LAST_DODO_NL, TYPE_LONG,
                               context.getString(R.string.identifier_lastdodo_nl),
                               "https://www.lastdodo.nl",
                               "https://www.lastdodo.nl/nl/items/%s",
                               "https://www.lastdodo.nl/nl/areas/%s"),
                // 2025-01-29
                new Identifier(SID_LCCN, TYPE_STRING,
                               context.getString(R.string.identifier_lccn),
                               "https://catalog.loc.gov",
                               "https://lccn.loc.gov/%s",
                               null),
                // 2025-01-29
                new Identifier(SID_LIBRARY_THING, TYPE_LONG,
                               context.getString(R.string.identifier_library_thing),
                               "https://www.librarything.com",
                               "https://www.librarything.com/work/%s",
                               null),
                // 2025-01-29
                new Identifier(SID_LIBRIS, TYPE_LONG,
                               context.getString(R.string.identifier_libris),
                               "https://libris.kb.se",
                               "https://libris.kb.se/bib/%s",
                               null),
                // 2025-01-29
                new Identifier(SID_LIBRIS_XL, TYPE_STRING,
                               context.getString(R.string.identifier_libris),
                               "https://libris.kb.se/katalogisering",
                               "https://libris.kb.se/%s",
                               null),
                // 2025-01-29
                new Identifier(SID_NILF, TYPE_LONG,
                               context.getString(R.string.identifier_nilf),
                               "https://www.fantascienza.com/catalogo/",
                               "https://www.fantascienza.com/catalogo/volumi/NILF%s",
                               "https://www.fantascienza.com/catalogo/autori/NILF%s"),
                // 2025-01-29
                new Identifier(SID_NOOSFERE, TYPE_LONG,
                               context.getString(R.string.identifier_noosfere),
                               "https://www.noosfere.org",
                               "https://www.noosfere.org/livres/niourf.asp?numlivre=%s",
                               "https://www.noosfere.org/livres/auteur.asp?NumAuteur=%s"),
                // 2025-01-29
                new Identifier(SID_OCLC, TYPE_LONG,
                               context.getString(R.string.identifier_worldcat),
                               "https://search.worldcat.org",
                               "https://www.worldcat.org/oclc/%s",
                               "https://id.oclc.org/worldcat/entity/%s"),
                // 2025-01-29
                new Identifier(SID_OPEN_LIBRARY, TYPE_STRING,
                               context.getString(R.string.identifier_open_library),
                               "https://openlibrary.org",
                               "https://openlibrary.org/books/%s",
                               "https://openlibrary.org/authors/%s"),
                // 2025-01-29
                new Identifier(SID_PORBASE, TYPE_LONG,
                               context.getString(R.string.identifier_porbase),
                               "https://porbase.bnportugal.gov.pt",
                               "http://id.bnportugal.gov.pt/bib/porbase/%s",
                               null),
                // 2025-01-29
                new Identifier(SID_STRIP_INFO, TYPE_LONG,
                               context.getString(R.string.identifier_stripinfo_be),
                               "https://stripinfo.be",
                               "https://stripinfo.be/reeks/strip/%s",
                               "https://stripinfo.be/auteur/index/%s"),
                // 2025-01-29  a permalink to the product nr is not possible
                new Identifier(SID_STRIPWEB, TYPE_LONG,
                               context.getString(R.string.identifier_stripweb_be),
                               "https://www.stripweb.be",
                               null,
                               null),
                // 2025-01-29
                new Identifier(SID_TERCERA_FUNDACION, TYPE_LONG,
                               context.getString(R.string.identifier_tercerafundacion),
                               "https://tercerafundacion.net",
                               "https://tercerafundacion.net/biblioteca/ver/libro/%s",
                               "https://tercerafundacion.net/biblioteca/ver/persona/%s"),
                // the bookUrl/authorUrl IS the sid
                new Identifier(SID_URI, TYPE_STRING,
                               context.getString(R.string.identifier_uri),
                               null,
                               "%s",
                               "%s"),
                // 2025-01-29
                new Identifier(SID_WIKIDATA, TYPE_STRING,
                               context.getString(R.string.identifier_wikidata),
                               "https://www.wikidata.org",
                               "https://www.wikidata.org/wiki/%s",
                               "https://www.wikidata.org/wiki/%s")
        );
    }

    @Override
    public void writeToParcel(@NonNull final Parcel dest,
                              final int flags) {
        dest.writeLong(id);
        dest.writeString(key);
        dest.writeInt(type);
        dest.writeString(name);
        dest.writeString(siteUrl);
        dest.writeString(bookUri);
        dest.writeString(authorUri);
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

    @NonNull
    @Override
    public String getLabel(@NonNull final Context context,
                           @NonNull final Details details,
                           @NonNull final Style style) {
        return name;
    }

    /**
     * Get the main/home page for this Identifier.
     * This it not necessarily the home page.
     *
     * @param context Current context
     *
     * @return url
     */
    @Nullable
    public String getSiteUrl(@NonNull final Context context) {
        // Always overrule the db stored url for amazon
        if (SID_ASIN.equals(key)) {
            //noinspection DataFlowIssue
            return EngineId.Amazon.getConfig().getHostUrl(context);
        }

        return siteUrl;
    }

    /**
     * Get the <strong>uri</strong> for viewing a book on the site.
     * The uri will have a single {@code %s} placeholder where the Identifier value needs to go.
     *
     * @param context Current context
     *
     * @return uri
     */
    @NonNull
    public Optional<String> getBookUri(@NonNull final Context context) {
        // Always overrule the db stored url for amazon
        if (SID_ASIN.equals(key)) {
            //noinspection DataFlowIssue
            return Optional.of(EngineId.Amazon.getConfig().getHostUrl(context) + "/dp/%s");
        }

        return bookUri != null ? Optional.of(bookUri) : Optional.empty();
    }

    /**
     * Get the <strong>uri</strong> for viewing an author on the site.
     * The uri will have a single {@code %s} placeholder where the Identifier value needs to go.
     *
     * @param context Current context
     *
     * @return uri
     */
    @NonNull
    public Optional<String> getAuthorUri(@NonNull final Context context) {
        // Always overrule the db stored url for amazon
        if (SID_ASIN.equals(key)) {
            //noinspection DataFlowIssue
            return Optional.of(EngineId.Amazon.getConfig().getHostUrl(context)
                               + "/stores/author/%s");
        }

        return authorUri != null ? Optional.of(authorUri) : Optional.empty();
    }

    @Override
    @NonNull
    public String toString() {
        return "Identifier{"
               + "id=" + id
               + ", key=`" + key + '`'
               + ", type=`" + type + '`'
               + ", name=`" + name + '`'
               + ", siteUrl=`" + siteUrl + '`'
               + ", bookUri=`" + bookUri + '`'
               + ", authorUri=`" + authorUri + '`'
               + '}';
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, key, type, name, siteUrl, bookUri, authorUri);
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
               && Objects.equals(name, that.name)
               && Objects.equals(siteUrl, that.siteUrl)
               && Objects.equals(bookUri, that.bookUri)
               && Objects.equals(authorUri, that.authorUri);
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
