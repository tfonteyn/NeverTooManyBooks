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
import com.hardbacknutter.nevertoomanybooks.core.utils.ISNI;
import com.hardbacknutter.nevertoomanybooks.database.DBKey;
import com.hardbacknutter.nevertoomanybooks.searchengines.EngineId;
import com.hardbacknutter.nevertoomanybooks.searchengines.bedetheque.BedethequeSearchEngine;
import com.hardbacknutter.nevertoomanybooks.searchengines.databazeknih.DatabazeKnihSearchEngine;
import com.hardbacknutter.nevertoomanybooks.searchengines.dnb.DnbSearchEngine;
import com.hardbacknutter.nevertoomanybooks.searchengines.douban.DoubanSearchEngine;
import com.hardbacknutter.nevertoomanybooks.searchengines.goodreads.GoodreadsSearchEngine;
import com.hardbacknutter.nevertoomanybooks.searchengines.googlebooks.GoogleBooksSearchEngine;
import com.hardbacknutter.nevertoomanybooks.searchengines.isfdb.IsfdbSearchEngine;
import com.hardbacknutter.nevertoomanybooks.searchengines.kbnl.KbNlSearchEngine;
import com.hardbacknutter.nevertoomanybooks.searchengines.lastdodo.LastDodoSearchEngine;
import com.hardbacknutter.nevertoomanybooks.searchengines.librarything.LibraryThingSearchEngine;
import com.hardbacknutter.nevertoomanybooks.searchengines.openlibrary.OpenLibrarySearchEngine;
import com.hardbacknutter.nevertoomanybooks.searchengines.stripinfo.StripInfoSearchEngine;
import com.hardbacknutter.nevertoomanybooks.searchengines.stripweb.StripWebSearchEngine;
import com.hardbacknutter.nevertoomanybooks.searchengines.wikidata.WikiData;
import com.hardbacknutter.nevertoomanybooks.searchengines.zzz.Audible;
import com.hardbacknutter.nevertoomanybooks.searchengines.zzz.BL;
import com.hardbacknutter.nevertoomanybooks.searchengines.zzz.BNF;
import com.hardbacknutter.nevertoomanybooks.searchengines.zzz.BarnesAndNoble;
import com.hardbacknutter.nevertoomanybooks.searchengines.zzz.DOI;
import com.hardbacknutter.nevertoomanybooks.searchengines.zzz.FantLab;
import com.hardbacknutter.nevertoomanybooks.searchengines.zzz.FantaScienza;
import com.hardbacknutter.nevertoomanybooks.searchengines.zzz.KBR;
import com.hardbacknutter.nevertoomanybooks.searchengines.zzz.Lccn;
import com.hardbacknutter.nevertoomanybooks.searchengines.zzz.LibrisSE;
import com.hardbacknutter.nevertoomanybooks.searchengines.zzz.NooSFere;
import com.hardbacknutter.nevertoomanybooks.searchengines.zzz.Porbase;
import com.hardbacknutter.nevertoomanybooks.searchengines.zzz.StoryGraph;
import com.hardbacknutter.nevertoomanybooks.searchengines.zzz.TerceraFundacion;
import com.hardbacknutter.nevertoomanybooks.searchengines.zzz.VIAF;
import com.hardbacknutter.nevertoomanybooks.searchengines.zzz.WorldCat;

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
        implements Parcelable, Entity, Mergeable {

    public static final String SID_ASIN = "asin";
    public static final String SID_AUDIBLE = "audible-asin";
    public static final String SID_BARNES_AND_NOBLE = "bn";
    public static final String SID_BEDETHEQUE = "bedetheque";
    public static final String SID_BNF = "bnf";
    public static final String SID_BRITISH_LIBRARY = "bl";
    public static final String SID_DATABAZE_KNIH = "databazeknih";
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
    public static final String SID_STORYGRAPH = "storygraph";
    public static final String SID_STRIPWEB = "stripweb";
    public static final String SID_STRIP_INFO = "stripinfo";
    public static final String SID_TERCERA_FUNDACION = "ltf";
    public static final String SID_URI = "uri";
    public static final String SID_URN = "urn";
    public static final String SID_WIKIDATA = "wikidata";

    /** {@link com.hardbacknutter.nevertoomanybooks.core.utils.ISNI}. */
    public static final String SID_ISNI = "isni";
    /** <a href="https://viaf.org">viaf</a>. */
    public static final String SID_VIAF = "viaf";

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
    private String key;
    @NonNull
    private String name;
    @Nullable
    private String siteUrl;
    @Nullable
    private String bookUri;
    @Nullable
    private String authorUri;
    private char type;
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
     * Copy constructor.
     *
     * @param identifier to copy
     */
    public Identifier(final Identifier identifier) {
        copyFrom(identifier);
    }

    /**
     * Constructor for the predefined Identifiers.
     * Will be used when updated app versions bring new and TESTED urls.
     *
     * @param key       a key(word) for this Identifier. e.g. "oclc"
     *                  The size is not enforced when importing or coming from a website,
     *                  but should be {@link #MAX_KEY_LEN} characters max, preferably less.
     *                  The UI editor does enforce length and lowercase.
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
                // links empty on purpose; created dynamically
                new Identifier(SID_ASIN, TYPE_STRING,
                               context.getString(R.string.identifier_amazon),
                               null,
                               null,
                               null),
                new Identifier(SID_AUDIBLE, TYPE_STRING,
                               context.getString(R.string.identifier_audible),
                               Audible.SITE_URL,
                               Audible.BOOK_URL,
                               Audible.AUTHOR_URL),
                new Identifier(SID_BARNES_AND_NOBLE, TYPE_LONG,
                               context.getString(R.string.identifier_barnesandnoble),
                               BarnesAndNoble.SITE_URL,
                               BarnesAndNoble.BOOK_URL,
                               BarnesAndNoble.AUTHOR_URL),
                new Identifier(SID_BEDETHEQUE, TYPE_LONG,
                               context.getString(R.string.identifier_bedetheque),
                               BedethequeSearchEngine.SITE_URL,
                               BedethequeSearchEngine.BOOK_URL,
                               BedethequeSearchEngine.AUTHOR_URL),
                new Identifier(SID_BNF, TYPE_STRING,
                               context.getString(R.string.identifier_bnf),
                               BNF.SITE_URL,
                               BNF.BOOK_URL,
                               BNF.AUTHOR_URL),
                new Identifier(SID_BRITISH_LIBRARY, TYPE_STRING,
                               context.getString(R.string.identifier_british_library),
                               BL.SITE_URL,
                               BL.BOOK_URL,
                               BL.AUTHOR_URL),
                new Identifier(SID_DATABAZE_KNIH, TYPE_LONG,
                               context.getString(R.string.identifier_databaze_knih),
                               DatabazeKnihSearchEngine.SITE_URL,
                               DatabazeKnihSearchEngine.BOOK_URL,
                               DatabazeKnihSearchEngine.AUTHOR_URL),
                new Identifier(SID_DNB, TYPE_STRING,
                               context.getString(R.string.identifier_dnb),
                               DnbSearchEngine.SITE_URL,
                               DnbSearchEngine.BOOK_URL,
                               DnbSearchEngine.AUTHOR_URL),
                new Identifier(SID_DOI, TYPE_STRING,
                               context.getString(R.string.identifier_doi),
                               DOI.SITE_URL,
                               DOI.BOOK_URL,
                               DOI.AUTHOR_URL),
                new Identifier(SID_DOUBAN, TYPE_LONG,
                               context.getString(R.string.identifier_douban),
                               DoubanSearchEngine.SITE_URL,
                               DoubanSearchEngine.BOOK_URL,
                               DoubanSearchEngine.AUTHOR_URL),
                new Identifier(SID_FANTLAB, TYPE_LONG,
                               context.getString(R.string.identifier_fantlab),
                               FantLab.SITE_URL,
                               FantLab.BOOK_URL,
                               FantLab.AUTHOR_URL),
                new Identifier(SID_GOODREADS, TYPE_LONG,
                               context.getString(R.string.identifier_goodreads),
                               GoodreadsSearchEngine.SITE_URL,
                               GoodreadsSearchEngine.BOOK_URL,
                               GoodreadsSearchEngine.AUTHOR_URL),
                new Identifier(SID_GOOGLE, TYPE_STRING,
                               context.getString(R.string.identifier_google_books),
                               GoogleBooksSearchEngine.SITE_URL,
                               GoogleBooksSearchEngine.BOOK_URL,
                               GoogleBooksSearchEngine.AUTHOR_URL),
                new Identifier(SID_ISFDB, TYPE_LONG,
                               context.getString(R.string.identifier_isfdb),
                               IsfdbSearchEngine.SITE_URL,
                               IsfdbSearchEngine.BOOK_URL,
                               IsfdbSearchEngine.AUTHOR_URL),
                new Identifier(SID_ISNI, TYPE_STRING,
                               context.getString(R.string.identifier_isni),
                               ISNI.SITE_URL,
                               null,
                               ISNI.AUTHOR_URL),
                new Identifier(SID_KBNL, TYPE_LONG,
                               context.getString(R.string.identifier_kb_nl),
                               KbNlSearchEngine.SITE_URL,
                               KbNlSearchEngine.BOOK_URL,
                               KbNlSearchEngine.AUTHOR_URL),
                new Identifier(SID_KBR, TYPE_LONG,
                               context.getString(R.string.identifier_kbr),
                               KBR.SITE_URL,
                               KBR.BOOK_URL,
                               KBR.AUTHOR_URL),
                new Identifier(SID_LAST_DODO_NL, TYPE_LONG,
                               context.getString(R.string.identifier_lastdodo_nl),
                               LastDodoSearchEngine.SITE_URL,
                               LastDodoSearchEngine.BOOK_URL,
                               LastDodoSearchEngine.AUTHOR_URL),
                new Identifier(SID_LCCN, TYPE_STRING,
                               context.getString(R.string.identifier_lccn),
                               Lccn.SITE_URL,
                               Lccn.BOOK_URL,
                               Lccn.AUTHOR_URL),
                new Identifier(SID_LIBRARY_THING, TYPE_LONG,
                               context.getString(R.string.identifier_library_thing),
                               LibraryThingSearchEngine.SITE_URL,
                               LibraryThingSearchEngine.BOOK_URL,
                               LibraryThingSearchEngine.AUTHOR_URL),
                new Identifier(SID_LIBRIS, TYPE_LONG,
                               context.getString(R.string.identifier_libris),
                               LibrisSE.SITE_URL,
                               LibrisSE.BOOK_URL,
                               LibrisSE.AUTHOR_URL),
                new Identifier(SID_LIBRIS_XL, TYPE_STRING,
                               context.getString(R.string.identifier_libris),
                               LibrisSE.XL_SITE_URL,
                               LibrisSE.XL_BOOK_URL,
                               LibrisSE.XL_AUTHOR_URL),
                new Identifier(SID_NILF, TYPE_LONG,
                               context.getString(R.string.identifier_nilf),
                               FantaScienza.SITE_URL,
                               FantaScienza.BOOK_URL,
                               FantaScienza.AUTHOR_URL),
                new Identifier(SID_NOOSFERE, TYPE_LONG,
                               context.getString(R.string.identifier_noosfere),
                               NooSFere.SITE_URL,
                               NooSFere.BOOK_URL,
                               NooSFere.AUTHOR_URL),
                new Identifier(SID_OCLC, TYPE_LONG,
                               context.getString(R.string.identifier_worldcat),
                               WorldCat.SITE_URL,
                               WorldCat.BOOK_URL,
                               WorldCat.AUTHOR_URL),
                new Identifier(SID_OPEN_LIBRARY, TYPE_STRING,
                               context.getString(R.string.identifier_open_library),
                               OpenLibrarySearchEngine.SITE_URL,
                               OpenLibrarySearchEngine.BOOK_URL,
                               OpenLibrarySearchEngine.AUTHOR_URL),
                new Identifier(SID_PORBASE, TYPE_LONG,
                               context.getString(R.string.identifier_porbase),
                               Porbase.SITE_URL,
                               Porbase.BOOK_URL,
                               Porbase.AUTHOR_URL),
                new Identifier(SID_STORYGRAPH, TYPE_STRING,
                               context.getString(R.string.identifier_storygraph),
                               StoryGraph.SITE_URL,
                               StoryGraph.BOOK_URL,
                               StoryGraph.AUTHOR_URL),
                new Identifier(SID_STRIP_INFO, TYPE_LONG,
                               context.getString(R.string.identifier_stripinfo_be),
                               StripInfoSearchEngine.SITE_URL,
                               StripInfoSearchEngine.BOOK_URL,
                               StripInfoSearchEngine.AUTHOR_URL),
                new Identifier(SID_STRIPWEB, TYPE_LONG,
                               context.getString(R.string.identifier_stripweb_be),
                               StripWebSearchEngine.SITE_URL,
                               StripWebSearchEngine.BOOK_URL,
                               StripWebSearchEngine.AUTHOR_URL),
                new Identifier(SID_TERCERA_FUNDACION, TYPE_LONG,
                               context.getString(R.string.identifier_tercerafundacion),
                               TerceraFundacion.SITE_URL,
                               TerceraFundacion.BOOK_URL,
                               TerceraFundacion.AUTHOR_URL),
                // the bookUrl/authorUrl IS the sid
                new Identifier(SID_URI, TYPE_STRING,
                               context.getString(R.string.identifier_uri),
                               null,
                               "%s",
                               "%s"),
                //https://en.wikipedia.org/wiki/Uniform_Resource_Name
                new Identifier(SID_URN, TYPE_STRING,
                               context.getString(R.string.identifier_urn),
                               null,
                               null,
                               null),
                new Identifier(SID_VIAF, TYPE_LONG,
                               context.getString(R.string.identifier_viaf),
                               VIAF.SITE_URL,
                               null,
                               VIAF.AUTHOR_URL),
                new Identifier(SID_WIKIDATA, TYPE_STRING,
                               context.getString(R.string.identifier_wikidata),
                               WikiData.SITE_URL,
                               WikiData.BOOK_URL,
                               WikiData.AUTHOR_URL)
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

    public void setKey(@NonNull final String key) {
        this.key = key;
    }

    @NonNull
    @Override
    public List<String> getNameFields() {
        return List.of(key, name);
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

    public void setName(@NonNull final String name) {
        this.name = name;
    }

    @NonNull
    @Override
    public String getLabel(@NonNull final Context context,
                           @Nullable final Details details,
                           @Nullable final Style style) {
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

    public void setSiteUrl(@Nullable final String siteUrl) {
        this.siteUrl = siteUrl;
    }

    public void setBookUri(@Nullable final String bookUri) {
        this.bookUri = bookUri;
    }

    public void setAuthorUri(@Nullable final String authorUri) {
        this.authorUri = authorUri;
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

    /**
     * Replace local details from another Identifier.
     *
     * @param source to copy from
     */
    public void copyFrom(@NonNull final Identifier source) {
        key = source.key;
        type = source.type;
        name = source.name;
        siteUrl = source.siteUrl;
        bookUri = source.bookUri;
        authorUri = source.authorUri;
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
