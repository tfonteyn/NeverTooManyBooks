/*
 * @Copyright 2018-2022 HardBackNutter
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
package com.hardbacknutter.nevertoomanybooks.searchengines;

import android.content.Context;
import android.os.Parcel;
import android.os.Parcelable;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.util.Locale;
import java.util.Objects;

import com.hardbacknutter.nevertoomanybooks.BuildConfig;
import com.hardbacknutter.nevertoomanybooks.R;
import com.hardbacknutter.nevertoomanybooks.ServiceLocator;
import com.hardbacknutter.nevertoomanybooks.database.DBKey;
import com.hardbacknutter.nevertoomanybooks.searchengines.amazon.AmazonSearchEngine;
import com.hardbacknutter.nevertoomanybooks.searchengines.bedetheque.BedethequeSearchEngine;
import com.hardbacknutter.nevertoomanybooks.searchengines.goodreads.GoodreadsSearchEngine;
import com.hardbacknutter.nevertoomanybooks.searchengines.googlebooks.GoogleBooksSearchEngine;
import com.hardbacknutter.nevertoomanybooks.searchengines.isfdb.IsfdbSearchEngine;
import com.hardbacknutter.nevertoomanybooks.searchengines.kbnl.KbNlSearchEngine;
import com.hardbacknutter.nevertoomanybooks.searchengines.lastdodo.LastDodoSearchEngine;
import com.hardbacknutter.nevertoomanybooks.searchengines.librarything.LibraryThingSearchEngine;
import com.hardbacknutter.nevertoomanybooks.searchengines.openlibrary.OpenLibrarySearchEngine;
import com.hardbacknutter.nevertoomanybooks.searchengines.stripinfo.StripInfoSearchEngine;

/**
 * Manages the setup of {@link SearchEngine}'s.
 * <p>
 * To add a new site to search, follow these steps:
 * <ol>
 *     <li>Add a string resource with the name of the site engine in:
 *         "src/main/res/values/donottranslate.xml" (look for existing entries named 'site_*')
 *     </li>
 *
 *     <li>Implement {@link SearchEngine} to create the new engine class
 *         extending {@link SearchEngineBase} or {@link JsoupSearchEngineBase}
 *         or a similar setup.<br>
 *         There MUST be a public constructor which takes a {@link SearchEngineConfig}
 *         as its single argument. This constructor must be annotated with "@Keep"
 *      </li>
 *
 *     <li>Add an enum identifier in this class and give it a unique string-id,
 *         the string resource id for the name as displayed to the user and
 *         the implementation class. The string-id must be all lowercase, no-spaces.
 *         It will be used in preferences, database settings,...
 *     </li>
 *
 *     <li>Configure the engine in the method {@link #registerSearchEngines()},
 *         using {@link #createConfiguration()}
 *         and {@link SearchEngineConfig.Builder} methods.
 *     </li>
 *
 *      <li>Add a new {@link Site} instance to the one or more list(s) in {@link #registerSites}
 *      </li>
 *
 *      <li>Optional: Add a preference fragment for the user to configure the engine.
 *          See the OpenLibrary engine for an simple example:
 *          a class, an xml file, and an entry in "src/main/res/xml/preferences_site_searches.xml"
 *          Look at the other engines for more complex examples.
 *      </li>
 *
 *      <li>Optional: if the engine/site will store a external book id (or any other specific
 *          fields) in the local database, extra steps will need to be taken.
 *          TODO: document steps: search the code for "NEWTHINGS: adding a new search engine"
 *          and/or search for an existing one, e.g. {@link DBKey#SID_OPEN_LIBRARY}
 *      </li>
 *
 * </ol>
 * <p>
 *
 * <strong>Note: NEVER change the "key" of the sites</strong>.
 *
 * @see SearchEngine
 * @see SearchEngineConfig
 * @see Site
 */
public enum EngineId
        implements Parcelable {

    /** All genres. */
    Amazon("amazon",
           R.string.site_amazon,
           "https://www.amazon.com",
           Locale.US,
           AmazonSearchEngine.class,
           true),

    /** French language (and to some extend other languages) comics. */
    Bedetheque("bedetheque",
               R.string.site_bedetheque,
               "https://www.bedetheque.com",
               Locale.FRANCE,
               BedethequeSearchEngine.class,
               BuildConfig.ENABLE_BEDETHEQUE),

    /** All genres. */
    Goodreads("goodreads",
              R.string.site_goodreads,
              "https://www.goodreads.com",
              Locale.US,
              GoodreadsSearchEngine.class,
              true),

    /** All genres. */
    GoogleBooks("googlebooks",
                R.string.site_google_books,
                "https://books.google.com",
                Locale.US,
                GoogleBooksSearchEngine.class,
                BuildConfig.ENABLE_GOOGLE_BOOKS),

    /** Speculative Fiction only. e.g. Science-Fiction/Fantasy etc... */
    Isfdb("isfdb",
          R.string.site_isfdb,
          "https://www.isfdb.org",
          Locale.US,
          IsfdbSearchEngine.class,
          true),


    /** Dutch language books & comics. */
    KbNl("kbnl",
         R.string.site_kb_nl,
         "https://webggc.oclc.org",
         new Locale("nl", "NL"),
         KbNlSearchEngine.class,
         BuildConfig.ENABLE_KB_NL),

    /** Dutch language (and to some extend other languages) comics. */
    LastDodoNl("lastdodo",
               R.string.site_lastdodo_nl,
               "https://www.lastdodo.nl",
               new Locale("nl", "NL"),
               LastDodoSearchEngine.class,
               BuildConfig.ENABLE_LAST_DODO),

    /** All genres. */
    LibraryThing("librarything",
                 R.string.site_library_thing,
                 "https://www.librarything.com",
                 Locale.US,
                 LibraryThingSearchEngine.class,
                 BuildConfig.ENABLE_LIBRARY_THING_ALT_ED),

    /** All genres. */
    OpenLibrary("openlibrary",
                R.string.site_open_library,
                "https://openlibrary.org",
                Locale.US,
                OpenLibrarySearchEngine.class,
                true),

    /** Dutch language (and to some extend other languages) comics. */
    StripInfoBe("stripinfo",
                R.string.site_stripinfo_be,
                "https://www.stripinfo.be",
                new Locale("nl", "BE"),
                StripInfoSearchEngine.class,
                BuildConfig.ENABLE_STRIP_INFO),
    ;

    // NEWTHINGS: adding a new search engine: add an engine id definition

    /** {@link Parcelable}. */
    public static final Creator<EngineId> CREATOR = new Creator<>() {
        @Override
        @NonNull
        public EngineId createFromParcel(@NonNull final Parcel in) {
            return values()[in.readInt()];
        }

        @Override
        @NonNull
        public EngineId[] newArray(final int size) {
            return new EngineId[size];
        }
    };

    /** The preference key / generic string identifier for this engine. */
    @NonNull
    private final String key;

    /** The user displayable name for this engine. */
    @StringRes
    private final int labelResId;

    /** Default url. */
    @NonNull
    private final String defaultUrl;

    @NonNull
    private final Locale defaultLocale;

    /** The implementation class for this engine. */
    @NonNull
    private final Class<? extends SearchEngine> clazz;

    /** Set at compile time from the gradle script. */
    private final boolean enabled;

    // Don't add config... toPrint will recurse
    @SuppressWarnings("FieldNotUsedInToString")
    @Nullable
    private SearchEngineConfig config;

    /**
     * Constructor.
     *
     * @param key           The preference key / generic string identifier for this engine.
     * @param labelResId    The user displayable name for this engine.
     * @param defaultUrl    for the site
     * @param defaultLocale for the site
     * @param clazz         implementation class for this engine.
     * @param enabled       {@code true} or a BuildConfig.ENABLE_ variable - see app/build.gradle
     */
    EngineId(@NonNull final String key,
             @StringRes final int labelResId,
             @NonNull final String defaultUrl,
             @NonNull final Locale defaultLocale,
             @NonNull final Class<? extends SearchEngine> clazz,
             final boolean enabled) {
        this.key = key;
        this.labelResId = labelResId;
        this.defaultUrl = defaultUrl;
        this.defaultLocale = defaultLocale;
        this.clazz = clazz;
        this.enabled = enabled;
    }

    /**
     * Register all {@link SearchEngine} configurations; called during startup.
     */
    static void registerSearchEngines() {
        // The engine order here is not important

        // ENHANCE: support ASIN and the ViewBookByExternalId interface
        if (Amazon.isEnabled()) {
            Amazon.createConfiguration()
                  // .setDomainKey(DBKey.SID_ASIN)
                  // .setDomainViewId(R.id.site_amazon)
                  // .setDomainMenuId(R.id.MENU_VIEW_BOOK_AT_AMAZON)
                  .build();
        }
        if (Bedetheque.isEnabled()) {
            Bedetheque.createConfiguration()
                      .setDomainKey(DBKey.SID_BEDETHEQUE)

                      // default timeouts based on limited testing
                      .setConnectTimeoutMs(15_000)
                      .setReadTimeoutMs(60_000)
                      // There are no specific usage rules but as a courtesy/precaution,
                      // we're only going to send one request a second.
                      .setThrottlerTimeoutMs(1_000)
                      .build();
        }
        if (Goodreads.isEnabled()) {
            Goodreads.createConfiguration()
                     .setDomainKey(DBKey.SID_GOODREADS_BOOK)
                     .setDomainViewId(R.id.site_goodreads)
                     .setDomainMenuId(R.id.MENU_VIEW_BOOK_AT_GOODREADS,
                                      R.integer.MENU_ORDER_VIEW_BOOK_AT_GOODREADS)
                     .build();
        }

        if (GoogleBooks.isEnabled()) {
            GoogleBooks.createConfiguration()
                       .build();
        }
        if (Isfdb.isEnabled()) {
            Isfdb.createConfiguration()
                 .setDomainKey(DBKey.SID_ISFDB)
                 .setDomainViewId(R.id.site_isfdb)
                 .setDomainMenuId(R.id.MENU_VIEW_BOOK_AT_ISFDB,
                                  R.integer.MENU_ORDER_VIEW_BOOK_AT_ISFDB)
                 // default timeouts based on limited testing
                 .setConnectTimeoutMs(20_000)
                 .setReadTimeoutMs(60_000)
                 // As proposed by another user on the ISFDB wiki,
                 // we're only going to send one request a second.
                 //<a href="https://isfdb.org/wiki/index.php/ISFDB:Help_desk/archives/archive_34#Some_Downloading_Questions_and_a_Request">throttling</a>
                 .setThrottlerTimeoutMs(1_000)
                 .build();
        }
        if (KbNl.isEnabled()) {
            KbNl.createConfiguration()
                .setSupportsMultipleCoverSizes(true)
                .build();
        }
        if (LastDodoNl.isEnabled()) {
            LastDodoNl.createConfiguration()
                      .setPrefersIsbn10(true)

                      .setDomainKey(DBKey.SID_LAST_DODO_NL)
                      .setDomainViewId(R.id.site_last_dodo_nl)
                      .setDomainMenuId(R.id.MENU_VIEW_BOOK_AT_LAST_DODO_NL,
                                       R.integer.MENU_ORDER_VIEW_BOOK_AT_LAST_DODO_NL)
                      .build();
        }
        if (LibraryThing.isEnabled()) {
            LibraryThing.createConfiguration()
                        .setDomainKey(DBKey.SID_LIBRARY_THING)
                        .setDomainViewId(R.id.site_library_thing)
                        .setDomainMenuId(R.id.MENU_VIEW_BOOK_AT_LIBRARY_THING,
                                         R.integer.MENU_ORDER_VIEW_BOOK_AT_LIBRARY_THING)
                        .build();
        }
        if (OpenLibrary.isEnabled()) {
            OpenLibrary.createConfiguration()
                       .setSupportsMultipleCoverSizes(true)

                       .setDomainKey(DBKey.SID_OPEN_LIBRARY)
                       .setDomainViewId(R.id.site_open_library)
                       .setDomainMenuId(R.id.MENU_VIEW_BOOK_AT_OPEN_LIBRARY,
                                        R.integer.MENU_ORDER_VIEW_BOOK_AT_OPEN_LIBRARY)
                       .build();
        }
        if (StripInfoBe.isEnabled()) {
            StripInfoBe.createConfiguration()
                       .setDomainKey(DBKey.SID_STRIP_INFO)
                       .setDomainViewId(R.id.site_strip_info_be)
                       .setDomainMenuId(R.id.MENU_VIEW_BOOK_AT_STRIP_INFO_BE,
                                        R.integer.MENU_ORDER_VIEW_BOOK_AT_STRIPINFO_BE)

                       // default timeouts based on limited testing
                       .setConnectTimeoutMs(7_000)
                       .setReadTimeoutMs(60_000)
                       // There are no specific usage rules but as a courtesy/precaution,
                       // we're only going to send one request a second.
                       .setThrottlerTimeoutMs(1_000)
                       .build();
        }

        // NEWTHINGS: adding a new search engine: add the search engine configuration
    }

    /**
     * Register all {@link Site} instances; called during startup.
     *
     * @param context Current context
     * @param type    the type of Site list
     */
    static void registerSites(@NonNull final Context context,
                              @NonNull final Site.Type type) {

        // Certain sites should only be enabled by default if the device or user set language
        // matches the site language.
        final boolean activateIfDutch = ServiceLocator.getInstance().getLanguages()
                                                      .isUserLanguage(context, "nld");
        final boolean activateIfFrench = ServiceLocator.getInstance().getLanguages()
                                                       .isUserLanguage(context, "fra");

        //NEWTHINGS: adding a new search engine: add to the list type as needed.

        // The order added here is the default order they will be used, but the user
        // can reorder the lists in preferences.

        switch (type) {
            case Data: {
                // Only add sites here that implement one or more of
                // {@link SearchEngine.ByExternalId}
                // {@link SearchEngine.ByIsbn}
                // {@link SearchEngine.ByBarcode}
                // {@link SearchEngine.ByText}

                type.addSite(Amazon, true);
                type.addSite(GoogleBooks, true);
                type.addSite(Isfdb, true);
                type.addSite(StripInfoBe, activateIfDutch);
                type.addSite(LastDodoNl, activateIfDutch);
                type.addSite(Bedetheque, activateIfFrench);
                type.addSite(KbNl, activateIfDutch);
                // Deactivated by default as data from this site is not very complete.
                type.addSite(OpenLibrary, false);
                break;
            }
            case Covers: {
                // Only add sites here that implement {@link SearchEngine.CoverByIsbn}.
                type.addSite(Amazon, true);
                type.addSite(Isfdb, true);
                type.addSite(KbNl, activateIfDutch);
                // Deactivated by default as this site lacks many covers.
                type.addSite(OpenLibrary, false);
                break;
            }
            case AltEditions: {
                //Only add sites here that implement {@link SearchEngine.AlternativeEditions}.
                type.addSite(LibraryThing, true);
                type.addSite(Isfdb, true);
                break;
            }

            case ViewOnSite: {
                // The order is irrelevant; just add all compliant ones
                for (final EngineId engineId : values()) {
                    if (engineId.isEnabled() && SearchEngine.ViewBookByExternalId.class
                            .isAssignableFrom(engineId.clazz)) {
                        type.addSite(engineId, true);
                    }
                }
                break;
            }

            default:
                throw new IllegalArgumentException(String.valueOf(type));
        }
    }

    public boolean isEnabled() {
        return enabled;
    }

    @NonNull
    private SearchEngineConfig.Builder createConfiguration() {
        return new SearchEngineConfig.Builder(this);
    }

    @NonNull
    public String getPreferenceKey() {
        return key;
    }

    /**
     * Get the human-readable name resource id.
     *
     * @return the displayable name resource id
     */
    @StringRes
    public int getLabelResId() {
        return labelResId;
    }

    /**
     * Get the human-readable name.
     *
     * @param context Current context
     *
     * @return the displayable name
     */
    @NonNull
    public String getName(@NonNull final Context context) {
        return context.getString(labelResId);
    }

    /**
     * The <strong>DEFAULT</strong> url.
     * Use {@link SearchEngineConfig#getHostUrl()} instead for all normal usage!
     *
     * @return default/hardcoded url for the site.
     */
    @NonNull
    String getDefaultUrl() {
        return defaultUrl;
    }

    @NonNull
    Locale getDefaultLocale() {
        return defaultLocale;
    }

    /**
     * Get the configuration.
     *
     * @return {@link SearchEngineConfig}
     */
    @Nullable
    public SearchEngineConfig getConfig() {
        return config;
    }

    public void setConfig(@NonNull final SearchEngineConfig config) {
        this.config = config;
    }

    @NonNull
    public SearchEngineConfig requireConfig() {
        return Objects.requireNonNull(config);
    }

    /**
     * Create a SearchEngine instance based on the registered configuration for the given id.
     *
     * @return a new instance
     */
    @NonNull
    public SearchEngine createSearchEngine() {
        try {
            final Constructor<? extends SearchEngine> c =
                    clazz.getConstructor(SearchEngineConfig.class);
            return c.newInstance(config);

        } catch (@NonNull final NoSuchMethodException | IllegalAccessException
                                | InstantiationException | InvocationTargetException e) {
            throw new IllegalStateException(
                    clazz + " must implement SearchEngine(SearchEngineConfig)", e);
        }
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(@NonNull final Parcel dest,
                              final int flags) {
        dest.writeInt(this.ordinal());
    }

    @Override
    @NonNull
    public String toString() {
        return "EngineId{"
               + "key='" + key + '\''
               + ", labelResId=" + ServiceLocator.getAppContext().getString(labelResId)
               + ", defaultUrl='" + defaultUrl + '\''
               + ", locale=" + defaultLocale
               + ", clazz=" + clazz.getName()
               + ", enabled=" + enabled
               + '}';
    }
}
