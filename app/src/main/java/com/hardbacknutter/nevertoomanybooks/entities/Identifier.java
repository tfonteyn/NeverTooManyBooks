/*
 * @Copyright 2018-2026 HardBackNutter
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
import androidx.annotation.StringRes;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

import com.hardbacknutter.nevertoomanybooks.R;
import com.hardbacknutter.nevertoomanybooks.booklist.style.Style;
import com.hardbacknutter.nevertoomanybooks.database.DBKey;
import com.hardbacknutter.nevertoomanybooks.searchengines.EngineId;
import com.hardbacknutter.nevertoomanybooks.searchengines.amazon.AmazonSearchEngine;
import com.hardbacknutter.nevertoomanybooks.searchengines.bedetheque.BedethequeSearchEngine;
import com.hardbacknutter.nevertoomanybooks.searchengines.bibliotecepl.BibliotecePlSearchEngine;
import com.hardbacknutter.nevertoomanybooks.searchengines.bnf.BnfSearchEngine;
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
import com.hardbacknutter.nevertoomanybooks.searchengines.wikidata.WikidataSearchEngine;
import com.hardbacknutter.nevertoomanybooks.searchengines.zzz.Audible;
import com.hardbacknutter.nevertoomanybooks.searchengines.zzz.BL;
import com.hardbacknutter.nevertoomanybooks.searchengines.zzz.BarnesAndNoble;
import com.hardbacknutter.nevertoomanybooks.searchengines.zzz.DOI;
import com.hardbacknutter.nevertoomanybooks.searchengines.zzz.FantLab;
import com.hardbacknutter.nevertoomanybooks.searchengines.zzz.FantaScienza;
import com.hardbacknutter.nevertoomanybooks.searchengines.zzz.ISNI;
import com.hardbacknutter.nevertoomanybooks.searchengines.zzz.ISSN;
import com.hardbacknutter.nevertoomanybooks.searchengines.zzz.KBR;
import com.hardbacknutter.nevertoomanybooks.searchengines.zzz.Lccn;
import com.hardbacknutter.nevertoomanybooks.searchengines.zzz.LibrisSE;
import com.hardbacknutter.nevertoomanybooks.searchengines.zzz.NooSFere;
import com.hardbacknutter.nevertoomanybooks.searchengines.zzz.Porbase;
import com.hardbacknutter.nevertoomanybooks.searchengines.zzz.StoryGraph;
import com.hardbacknutter.nevertoomanybooks.searchengines.zzz.TerceraFundacion;
import com.hardbacknutter.nevertoomanybooks.searchengines.zzz.URI;
import com.hardbacknutter.nevertoomanybooks.searchengines.zzz.URN;
import com.hardbacknutter.nevertoomanybooks.searchengines.zzz.VIAF;
import com.hardbacknutter.nevertoomanybooks.searchengines.zzz.WorldCat;

/**
 * External website id's (site-id, sid).
 * <ul>
 * <li>key: a unique keyword; never to be changed; used as bundle keys and import/export</li>
 * <li>entityType: which entity this identifier is used for.
 *     The combination of key+entityType defines the Identifier.</li>
 * <li>type: {@code 'L'} or {@code 'S'}, see {@link Type}.</li>
 * <li>name: a non-localised short name to show to the user.
 *           Can be empty for user created key.</li>
 * <li>sid: the actual value of the identifier field</li>
 * </ul>
 * The type is used in two situations only.
 * <ol>
 *     <li>When storing a book, {@link Type#Number} identifiers are checked for
 *         being a valid {@code long}. If they fail, they are removed.
 *         {@link Type#Text} is always stored unless it's empty.
 *     </li>
 *     <li>The screen where the user can directly edit identifier values will
 *         show a numeric or full keyboard depending on the type just for convenience.
 *     </li>
 * </ol>
 * i.o.w. the type {@link Type#Number} is only used in the predefined Identifiers,
 * and an unknown identifier is always assumed to be a {@link Type#Text}.
 * <p>
 * There are an endless amount of Identifiers, we cannot predefine them all.
 * For reference, here are some more wikidata claim numbers:
 * <ul>
 *     <li>P12435   Shamela author ID</li>
 *     <li>P2687    JPNO=Japanese National Bibliography Number of the National Diet Library</li>
 *     <li>P3184    Czech National Bibliography ID  identifier for a book or periodical
 *                  at the Czech National Library</li>
 *     <li>P7865    CoBiS author ID</li>
 *     <li>P8287    Worlds Without End author ID</li>
 * </ul>
 *
 * @see <a href="https://www.wikidata.org/wiki/Wikidata:Database_reports/List_of_properties/all">
 *     All Wikidata claim number - WARNING: LONG LIST</a>
 */
public class Identifier
        implements Parcelable, Entity, Mergeable {

    public static final String SID_ASIN = "asin";
    public static final String SID_AUDIBLE = "audible-asin";
    public static final String SID_BARNES_AND_NOBLE = "bn";
    public static final String SID_BEDETHEQUE = "bedetheque";
    public static final String SID_BIBLIOTECE_PL = "bibliotece";
    public static final String SID_BNF = "bnf";
    public static final String SID_BRITISH_LIBRARY = "bl";
    public static final String SID_DATABAZE_KNIH = "databazeknih";
    /** DE-101. */
    public static final String SID_DNB = "dnb";
    public static final String SID_DOI = "doi";
    public static final String SID_DOUBAN = "douban";
    public static final String SID_FANTLAB = "fantlab";
    /** The original Goodreads "legacyId" which is in widespread use. */
    public static final String SID_GOODREADS = "goodreads";
    public static final String SID_GOOGLE = "google";
    public static final String SID_ISFDB = "isfdb";
    public static final String SID_ISFDB_PUB_SERIES = "isfdb-pubseries";
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
    public static final String SID_STRIP_INFO_COLLECTION = "stripinfo-collection";
    public static final String SID_TERCERA_FUNDACION = "ltf";
    public static final String SID_URI = "uri";
    public static final String SID_URN = "urn";
    public static final String SID_WIKIDATA = "wikidata";


    /**
     * Used for Authors only.
     * {@link com.hardbacknutter.nevertoomanybooks.entities.codes.ISNI}.
     */
    public static final String SID_ISNI = "isni";

    /** Used for Series only. */
    public static final String SID_ISSN = "issn";

    /** <a href="https://viaf.org">viaf</a>. */
    public static final String SID_VIAF = "viaf";

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
    /** Wikidata claims prefix. */
    private static final String P = "P";

    @NonNull
    private String key;
    @NonNull
    private Type type;
    @NonNull
    private String name;
    @NonNull
    private EntityType entityType;

    @Nullable
    private String siteUrl;
    @Nullable
    private String uri;

    private long id;

    /**
     * For now, the wiki claim is only supported for an author.
     * WikiData does have claim numbers for books and series,
     * but that data is minimal to unusable.
     */
    @Nullable
    private String wikidataClaim;

    /**
     * Constructor to add unknown Identifiers as found in searches.
     *
     * @param key        a key(word) for this Identifier
     * @param entityType to set
     */
    public Identifier(@NonNull final String key,
                      @NonNull final EntityType entityType) {
        this.key = key;
        this.entityType = entityType;

        // adding unknown identifiers is always done as a string
        this.type = Type.Text;
        this.name = key;

        this.wikidataClaim = null;
        this.siteUrl = null;
        this.uri = null;
    }

    /**
     * Copy constructor.
     *
     * @param identifier to copy
     */
    public Identifier(@NonNull final Identifier identifier) {
        copyFrom(identifier);
    }

    /**
     * Constructor for the predefined Identifiers.
     * Will be used when updated app versions bring new and TESTED urls.
     *
     * @param entityType    to set
     * @param type          Text/Number
     * @param key           a key(word) for this Identifier. e.g. "oclc"
     *                      Should be lowercase.
     *                      The UI editor does enforce lowercase.
     * @param name          a short name
     * @param siteUrl       url to the main website page
     * @param uri           a url with a {@code %s%} placeholder for the sid,
     *                      to view a {@code Book} on the site
     * @param wikidataClaim (optional) "Pxxx" Wikidata claim number
     */
    public Identifier(@NonNull final EntityType entityType,
                      @NonNull final Type type,
                      @NonNull final String key,
                      @NonNull final String name,
                      @Nullable final String siteUrl,
                      @Nullable final String uri,
                      @Nullable final String wikidataClaim) {
        this.entityType = entityType;
        this.type = type;
        this.key = key;
        this.name = name;

        this.siteUrl = siteUrl;
        this.uri = uri;
        this.wikidataClaim = wikidataClaim;
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

        entityType = EntityType.byId(rowData.getInt(DBKey.IDENTIFIERS.ENTITY));
        type = Type.byId(rowData.getString(DBKey.IDENTIFIERS.TYPE).charAt(0));
        key = rowData.getString(DBKey.IDENTIFIERS.KEY);
        name = rowData.getString(DBKey.IDENTIFIERS.NAME);

        siteUrl = rowData.getString(DBKey.IDENTIFIERS.SITE_URL, null);
        uri = rowData.getString(DBKey.IDENTIFIERS.URI, null);
        wikidataClaim = rowData.getString(DBKey.IDENTIFIERS.WIKIDATA_CLAIM);
    }

    private Identifier(@NonNull final Parcel in) {
        id = in.readLong();
        //noinspection DataFlowIssue
        entityType = in.readParcelable(EntityStage.Stage.class.getClassLoader());
        //noinspection DataFlowIssue
        type = in.readParcelable(EntityStage.Stage.class.getClassLoader());
        //noinspection DataFlowIssue
        key = in.readString();
        //noinspection DataFlowIssue
        name = in.readString();

        siteUrl = in.readString();
        uri = in.readString();
        wikidataClaim = in.readString();
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
    public static Collection<Identifier> createInitialList(@NonNull final Context context) {
        final Collection<Identifier> all = new ArrayList<>();

        // TODO: automate this
        all.addAll(AmazonSearchEngine.createIdentifiers(context));
        all.addAll(Audible.createIdentifiers(context));
        all.addAll(BarnesAndNoble.createIdentifiers(context));
        all.addAll(BedethequeSearchEngine.createIdentifiers(context));
        all.addAll(BibliotecePlSearchEngine.createIdentifiers(context));
        all.addAll(BnfSearchEngine.createIdentifiers(context));
        all.addAll(BL.createIdentifiers(context));
        all.addAll(DatabazeKnihSearchEngine.createIdentifiers(context));
        all.addAll(DnbSearchEngine.createIdentifiers(context));
        all.addAll(DOI.createIdentifiers(context));
        all.addAll(DoubanSearchEngine.createIdentifiers(context));
        all.addAll(FantLab.createIdentifiers(context));
        all.addAll(GoodreadsSearchEngine.createIdentifiers(context));
        all.addAll(GoogleBooksSearchEngine.createIdentifiers(context));
        all.addAll(IsfdbSearchEngine.createIdentifiers(context));
        all.addAll(ISNI.createIdentifiers(context));
        all.addAll(ISSN.createIdentifiers(context));
        all.addAll(KbNlSearchEngine.createIdentifiers(context));
        all.addAll(KBR.createIdentifiers(context));
        all.addAll(LastDodoSearchEngine.createIdentifiers(context));
        all.addAll(Lccn.createIdentifiers(context));
        all.addAll(LibraryThingSearchEngine.createIdentifiers(context));
        all.addAll(LibrisSE.createIdentifiers(context));
        all.addAll(FantaScienza.createIdentifiers(context));
        all.addAll(NooSFere.createIdentifiers(context));
        all.addAll(WorldCat.createIdentifiers(context));
        all.addAll(OpenLibrarySearchEngine.createIdentifiers(context));
        all.addAll(Porbase.createIdentifiers(context));
        all.addAll(StoryGraph.createIdentifiers(context));
        all.addAll(StripInfoSearchEngine.createIdentifiers(context));
        all.addAll(StripWebSearchEngine.createIdentifiers(context));
        all.addAll(TerceraFundacion.createIdentifiers(context));
        all.addAll(URI.createIdentifiers(context));
        all.addAll(URN.createIdentifiers(context));
        all.addAll(VIAF.createIdentifiers(context));
        all.addAll(WikidataSearchEngine.createIdentifiers(context));

        return all;
    }

    @Override
    public void writeToParcel(@NonNull final Parcel dest,
                              final int flags) {
        dest.writeLong(id);
        dest.writeParcelable(entityType, flags);
        dest.writeParcelable(type, flags);
        dest.writeString(key);
        dest.writeString(name);

        dest.writeString(siteUrl);
        dest.writeString(uri);
        dest.writeString(wikidataClaim);
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

    @NonNull
    public EntityType getEntityType() {
        return entityType;
    }

    /**
     * Get the type.
     *
     * @return {@link Type}.
     */
    @NonNull
    public Type getType() {
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
     * Set the Identifier key.
     *
     * @param key to set
     */
    public void setKey(@NonNull final String key) {
        this.key = key;
    }

    @NonNull
    @Override
    public List<String> getNameFields() {
        // The 'name' fields are the key and entity type,
        // not the actual name which is a description only.
        return List.of(key, String.valueOf(entityType.getId()));
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

    /**
     * Set the user displayable name.
     *
     * @param name name
     */
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
     * Get the WikiData claim number. Includes the prefix {@code P}.
     *
     * @return claim
     */
    @NonNull
    public Optional<String> getWikidataClaim() {
        if (wikidataClaim != null && !wikidataClaim.isEmpty()) {
            return Optional.of(wikidataClaim);
        }
        return Optional.empty();
    }

    /**
     * Set the WikiData claim number.
     * If the prefix {@code P} is missing, it will be added.
     *
     * @param claim number
     */
    public void setWikidataClaim(@Nullable final String claim) {
        if (claim == null || claim.isBlank()) {
            this.wikidataClaim = null;
        } else {
            String wdp = claim.strip();
            // add prefix if missing
            if (!wdp.startsWith(P)) {
                wdp = P + wdp;
            }
            this.wikidataClaim = wdp;
        }
    }

    /**
     * Get the main/home page for this Identifier.
     * This it not necessarily the home page.
     *
     * @return url
     */
    @Nullable
    public String getSiteUrl() {
        // Always overrule the db stored url for amazon
        if (SID_ASIN.equals(key)) {
            //noinspection DataFlowIssue
            return EngineId.Amazon.getConfig().getHostUrl();
        }

        return siteUrl;
    }

    /**
     * Set the <strong>uri</strong> for viewing a reference on the site.
     * The uri will have a single {@code %s} placeholder where the Identifier value needs to go.
     *
     * @param siteUrl uri to set
     */
    public void setSiteUrl(@Nullable final String siteUrl) {
        this.siteUrl = siteUrl;
    }

    /**
     * Get the <strong>uri</strong> for viewing a reference on the site.
     * The uri will have a single {@code %s} placeholder where the Identifier value needs to go.
     *
     * @return uri
     */
    @NonNull
    public Optional<String> getUri() {
        // Always overrule the db stored url for amazon
        if (SID_ASIN.equals(key)) {
            return AmazonSearchEngine.getIdentifierUri(entityType);
        }

        if (uri == null) {
            return Optional.empty();
        }
        return Optional.of(uri);
    }

    public void setUri(@Nullable final String uri) {
        this.uri = uri;
    }

    /**
     * Replace local details from another Identifier.
     *
     * @param source to copy from
     */
    public void copyFrom(@NonNull final Identifier source) {
        key = source.key;
        entityType = source.entityType;

        type = source.type;
        name = source.name;

        wikidataClaim = source.wikidataClaim;
        siteUrl = source.siteUrl;
        uri = source.uri;
    }

    @Override
    @NonNull
    public String toString() {
        return "Identifier{"
               + "id=" + id
               + ", key=`" + key + '`'
               + ", entityType=`" + entityType + '`'
               + ", type=`" + type + '`'
               + ", name=`" + name + '`'
               + ", wikidataClaim=`" + wikidataClaim + '`'
               + ", siteUrl=`" + siteUrl + '`'
               + ", uri=`" + uri + '`'
               + '}';
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, key, entityType, type, name, wikidataClaim, siteUrl, uri);
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
               && entityType == that.entityType
               && type == that.type
               && Objects.equals(name, that.name)
               && Objects.equals(wikidataClaim, that.wikidataClaim)
               && Objects.equals(siteUrl, that.siteUrl)
               && Objects.equals(uri, that.uri);
    }


    /**
     * Defines for which entity this identifier is valid.
     */
    public enum EntityType
            implements Parcelable {
        Book(0, R.string.lbl_books),
        Author(1, R.string.lbl_authors),
        Series(2, R.string.lbl_series_multiple);

        /** {@link Parcelable}. */
        public static final Creator<EntityType> CREATOR = new Creator<>() {
            @Override
            @NonNull
            public EntityType createFromParcel(@NonNull final Parcel in) {
                return values()[in.readInt()];
            }

            @Override
            @NonNull
            public EntityType[] newArray(final int size) {
                return new EntityType[size];
            }
        };

        private final int id;

        /** User displayable short name; used as the text for a Tab. */
        @StringRes
        private final int labelResId;

        EntityType(final int id,
                   @StringRes final int labelResId) {
            this.id = id;
            this.labelResId = labelResId;
        }

        /**
         * Lookup by id.
         * <p>
         * Import/Export and database usage only.
         *
         * @param id to lookup
         *
         * @return type; or {@link #Book} if not found to facilitate legacy imports
         */
        @NonNull
        public static EntityType byId(final int id) {
            return Arrays.stream(values())
                         .filter(v -> v.id == id)
                         .findFirst()
                         .orElse(Book);
        }

        /**
         * Get the internal id.
         * <p>
         * Import/Export and database usage only.
         *
         * @return id
         */
        public int getId() {
            return id;
        }

        @StringRes
        public int getLabelResId() {
            return labelResId;
        }


        @Override
        public int describeContents() {
            return 0;
        }

        @Override
        public void writeToParcel(@NonNull final Parcel dest,
                                  final int flags) {
            dest.writeInt(ordinal());
        }
    }

    /**
     * The user will get an alphanumeric or numeric-only keyboard.
     * The storage of the {@link Identifier.Value} is always a {@code String}!
     */
    public enum Type
            implements Parcelable {
        /** Site defined string id. */
        Text('S'),
        /** A pure number. */
        Number('L');

        /** {@link Parcelable}. */
        public static final Creator<Type> CREATOR = new Creator<>() {
            @Override
            @NonNull
            public Type createFromParcel(@NonNull final Parcel in) {
                return values()[in.readInt()];
            }

            @Override
            @NonNull
            public Type[] newArray(final int size) {
                return new Type[size];
            }
        };

        private final char id;

        Type(final char id) {
            this.id = id;
        }

        /**
         * Lookup by id.
         * <p>
         * Import/Export and database usage only.
         *
         * @param id to lookup
         *
         * @return type; or {@link #Text} for any invalid id.
         */
        @NonNull
        public static Type byId(final char id) {
            return Arrays.stream(values())
                         .filter(v -> v.id == id)
                         .findFirst()
                         .orElse(Type.Text);
        }

        /**
         * Get the internal id.
         * <p>
         * Import/Export and database usage only.
         *
         * @return id
         */
        public char getId() {
            return id;
        }

        @Override
        public int describeContents() {
            return 0;
        }

        @Override
        public void writeToParcel(@NonNull final Parcel dest,
                                  final int flags) {
            dest.writeInt(ordinal());
        }
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

        /**
         * Bundle key for {@code ArrayList<Identifier.Value>}.
         * <strong>Used in export/import, NEVER change the string</strong>
         */
        public static final String BKEY_LIST = "identifier_list";

        @NonNull
        private final String key;
        @NonNull
        private String sid;

        /**
         * Constructor.
         *
         * @param key Identifier
         * @param sid value
         */
        public Value(@NonNull final String key,
                     @NonNull final String sid) {
            this.key = key;
            this.sid = sid;
        }

        /**
         * Convenience constructor which takes the sid as a number.
         *
         * @param key Identifier
         * @param sid value
         */
        public Value(@NonNull final String key,
                     final long sid) {
            this(key, String.valueOf(sid));
        }

        public Value(@NonNull final Value source) {
            this(source.key, source.sid);
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
         * Set the external website id (site-id, sid).
         *
         * @param sid value
         */
        public void setSid(@NonNull final String sid) {
            this.sid = sid;
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
