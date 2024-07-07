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
package com.hardbacknutter.nevertoomanybooks.searchengines;

import android.content.Context;
import android.os.Parcel;
import android.os.Parcelable;
import android.text.method.LinkMovementMethod;
import android.text.util.Linkify;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import androidx.annotation.UiThread;
import androidx.appcompat.app.AlertDialog;
import androidx.preference.PreferenceManager;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.util.ArrayDeque;
import java.util.Collection;
import java.util.Deque;
import java.util.Locale;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.stream.Collectors;

import com.hardbacknutter.nevertoomanybooks.BuildConfig;
import com.hardbacknutter.nevertoomanybooks.R;
import com.hardbacknutter.nevertoomanybooks.database.DBKey;
import com.hardbacknutter.nevertoomanybooks.searchengines.amazon.AmazonSearchEngine;
import com.hardbacknutter.nevertoomanybooks.searchengines.bedetheque.BedethequeSearchEngine;
import com.hardbacknutter.nevertoomanybooks.searchengines.bol.BolSearchEngine;
import com.hardbacknutter.nevertoomanybooks.searchengines.bookfinder.BookFinderSearchEngine;
import com.hardbacknutter.nevertoomanybooks.searchengines.dnb.DnbSearchEngine;
import com.hardbacknutter.nevertoomanybooks.searchengines.douban.DoubanSearchEngine;
import com.hardbacknutter.nevertoomanybooks.searchengines.goodreads.GoodreadsSearchEngine;
import com.hardbacknutter.nevertoomanybooks.searchengines.googlebooks.GoogleBooksSearchEngine;
import com.hardbacknutter.nevertoomanybooks.searchengines.isfdb.IsfdbSearchEngine;
import com.hardbacknutter.nevertoomanybooks.searchengines.kbnl.KbNlSearchEngine;
import com.hardbacknutter.nevertoomanybooks.searchengines.lastdodo.LastDodoSearchEngine;
import com.hardbacknutter.nevertoomanybooks.searchengines.librarything.LibraryThingSearchEngine;
import com.hardbacknutter.nevertoomanybooks.searchengines.openlibrary.OpenLibrary2SearchEngine;
import com.hardbacknutter.nevertoomanybooks.searchengines.stripinfo.StripInfoSearchEngine;
import com.hardbacknutter.nevertoomanybooks.searchengines.stripweb.StripWebSearchEngine;
import com.hardbacknutter.nevertoomanybooks.utils.Languages;

/**
 * Manages the setup of {@link SearchEngine}'s.
 * <p>
 * To add a new site to search, follow these steps:
 * <ol>
 *     <li>Add a buildConfigField to build.gradle in th defaultConfig section
 *         and if needed in buildTypes/release</li>
 *
 *     <li>Add a string resource with the name of the site engine in:
 *         "src/main/res/values/donottranslate.xml" (look for existing entries named 'site_*')
 *     </li>
 *
 *     <li>Implement {@link SearchEngine} to create the new engine class
 *         extending {@link SearchEngineBase} or {@link JsoupSearchEngineBase}
 *         or a similar setup.<br>
 *         There MUST be a public constructor annotated with "@Keep" and with arguments
 *         ({@link Context},{@link SearchEngineConfig})
 *         The context received is the <strong>application</strong> context;
 *         i.e. a NON-localized context which cannot be used to lookup string resources but is
 *         only meant to be used for preference-value lookups.
 *      </li>
 *
 *     <li>Add an enum identifier in this class and give it a unique string-id,
 *         the string resource id for the name as displayed to the user and
 *         the implementation class. The string-id must be all lowercase, no-spaces.
 *         It will be used in preferences, database settings,...
 *     </li>
 *
 *     <li>Configure the engine in the method {@link #createEngineConfigurations()},
 *         using {@link #createConfiguration()}
 *         and {@link SearchEngineConfig.Builder} methods.
 *     </li>
 *
 *      <li>Add a new {@link Site} instance to the one or more list(s) in {@link #registerSites}
 *      </li>
 *
 *      <li>Add a preference fragment for the user to configure the engine.
 *          The class MUST be annotated with "@Keep".
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
           R.string.site_info_amazon,
           // amazon.com, amazon.ca : blocked by captcha
           "https://www.amazon.co.uk",
           // The Locale will be dynamically set depending on the country site
           Locale.US,
           AmazonSearchEngine.class,
           BuildConfig.ENABLE_AMAZON),

    /** French language (and to some extend other languages) comics. */
    Bedetheque("bedetheque",
               R.string.site_bedetheque,
               R.string.site_info_bedetheque,
               "https://www.bedetheque.com",
               Locale.FRANCE,
               BedethequeSearchEngine.class,
               BuildConfig.ENABLE_BEDETHEQUE),

    /**
     * All genres; dutch and many other languages.
     * Shopping site from The Netherlands / Belgium.
     */
    Bol("bol",
        R.string.site_bol_com,
        R.string.site_info_bol_com,
        "https://www.bol.com",
        new Locale("nl", "NL"),
        BolSearchEngine.class,
        BuildConfig.ENABLE_BOL),

    /**
     * All genres. This is a portal site to other shopping sites.
     * Can find books which are harder to find on other sites,
     * but will only show minimal information.
     */
    BookFinder("bookfinder",
               R.string.site_bookfinder,
               R.string.site_info_bookfinder,
               "https://www.bookfinder.com",
               Locale.US,
               BookFinderSearchEngine.class,
               BuildConfig.ENABLE_BOOKFINDER),

    /** German language books & comics. */
    Dnb("dnb",
        R.string.site_dnb_de,
        R.string.site_info_dnb_de,
        "https://katalog.dnb.de",
        new Locale("de", "DE"),
        DnbSearchEngine.class,
        BuildConfig.ENABLE_DNB_DE),

    /** Chinese language books & comics. */
    Douban("douban",
           R.string.site_douban,
           R.string.site_info_douban,
           "https://search.douban.com",
           Locale.CHINA,
           DoubanSearchEngine.class,
           BuildConfig.ENABLE_DOUBAN),

    /** Only used for {@link SearchEngine.ViewBookByExternalId}. */
    Goodreads("goodreads",
              R.string.site_goodreads,
              R.string.site_info_goodreads,
              "https://www.goodreads.com",
              Locale.US,
              GoodreadsSearchEngine.class,
              BuildConfig.ENABLE_GOODREADS_BY_EXT_ID),

    /**
     * All genres.
     * Uses old google api which theoretically can be disabled at any time by google.
     */
    GoogleBooks("googlebooks",
                R.string.site_google_books,
                R.string.site_info_google_books,
                "https://books.google.com",
                Locale.US,
                GoogleBooksSearchEngine.class,
                BuildConfig.ENABLE_GOOGLE_BOOKS),

    /** Speculative Fiction only. e.g. Science-Fiction/Fantasy etc... */
    Isfdb("isfdb",
          R.string.site_isfdb,
          R.string.site_info_isfdb,
          "https://www.isfdb.org",
          Locale.US,
          IsfdbSearchEngine.class,
          BuildConfig.ENABLE_ISFDB),

    /** Dutch language books & comics. */
    KbNl("kbnl",
         R.string.site_kb_nl,
         R.string.site_info_kb_nl,
         "https://webggc.oclc.org",
         new Locale("nl", "NL"),
         KbNlSearchEngine.class,
         BuildConfig.ENABLE_KB_NL),

    /** Dutch language (and to some extend other languages) comics. */
    LastDodoNl("lastdodo",
               R.string.site_lastdodo_nl,
               R.string.site_info_lastdodo_nl,
               "https://www.lastdodo.nl",
               new Locale("nl", "NL"),
               LastDodoSearchEngine.class,
               BuildConfig.ENABLE_LAST_DODO),

    /** Only used for {@link SearchEngine.ViewBookByExternalId}. */
    LibraryThing("librarything",
                 R.string.site_library_thing,
                 R.string.site_info_library_thing,
                 "https://www.librarything.com",
                 Locale.US,
                 LibraryThingSearchEngine.class,
                 BuildConfig.ENABLE_LIBRARY_THING_BY_EXT_ID),

    /** All genres. */
    OpenLibrary("openlibrary",
                R.string.site_open_library,
                R.string.site_info_open_library,
                "https://openlibrary.org",
                Locale.US,
                OpenLibrary2SearchEngine.class,
                BuildConfig.ENABLE_OPEN_LIBRARY),

    /** Dutch language (and to some extend other languages) comics. */
    StripInfoBe("stripinfo",
                R.string.site_stripinfo_be,
                R.string.site_info_stripinfo_be,
                "https://www.stripinfo.be",
                new Locale("nl", "BE"),
                StripInfoSearchEngine.class,
                BuildConfig.ENABLE_STRIP_INFO),

    /**
     * Dutch language (and to some extend other languages) comics.
     * The site can be accessed in Dutch,French,English. We use the Dutch site for access.
     * The main reason for this one is having access to current list-prices;
     * otherwise the recommendation is to use {@link #StripInfoBe} and {@link #LastDodoNl}.
     */
    StripWebBe("stripweb",
               R.string.site_stripweb_be,
               R.string.site_info_stripweb_be,
               "https://www.stripweb.be",
               new Locale("nl", "BE"),
               StripWebSearchEngine.class,
               BuildConfig.ENABLE_STRIP_WEB);

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
    @SuppressWarnings("FieldNotUsedInToString")
    @StringRes
    private final int labelResId;

    @SuppressWarnings("FieldNotUsedInToString")
    @StringRes
    private final int infoResId;

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
     * @param infoResId     A short information text about this site
     * @param defaultUrl    for the site
     * @param defaultLocale for the site
     * @param clazz         implementation class for this engine.
     * @param enabled       {@code true} or a BuildConfig.ENABLE_ variable - see app/build.gradle
     */
    EngineId(@NonNull final String key,
             @StringRes final int labelResId,
             @StringRes final int infoResId,
             @NonNull final String defaultUrl,
             @NonNull final Locale defaultLocale,
             @NonNull final Class<? extends SearchEngine> clazz,
             final boolean enabled) {
        this.key = key;
        this.labelResId = labelResId;
        this.infoResId = infoResId;
        this.defaultUrl = defaultUrl;
        this.defaultLocale = defaultLocale;
        this.clazz = clazz;
        this.enabled = enabled;
    }

    /**
     * Create all {@link SearchEngine} configurations; called during startup.
     */
    static void createEngineConfigurations() {
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
                      .build();
        }
        if (Bol.isEnabled()) {
            Bol.createConfiguration()
               .build();
        }
        if (BookFinder.isEnabled()) {
            BookFinder.createConfiguration()
                      .build();
        }
        if (Dnb.isEnabled()) {
            Dnb.createConfiguration()
               .build();
        }
        if (Douban.isEnabled()) {
            Douban.createConfiguration()
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
                       .build();
        }
        if (StripWebBe.isEnabled()) {
            StripWebBe.createConfiguration()
                      .build();
        }

        // NEWTHINGS: adding a new search engine: add the search engine configuration
    }

    /**
     * Register all {@link Site} instances; called during startup.
     *
     * @param context   Current context
     * @param type      the type of Site list
     * @param languages the language cache container
     */
    static void registerSites(@NonNull final Context context,
                              @NonNull final Site.Type type,
                              @NonNull final Languages languages) {

        // Certain sites should only be enabled by default if the device or user set language
        // matches the site language.
        final boolean activateIfChinese = languages.isUserLanguage(context, "zho");
        final boolean activateIfDutch = languages.isUserLanguage(context, "nld");
        final boolean activateIfFrench = languages.isUserLanguage(context, "fra");
        final boolean activateIfGerman = languages.isUserLanguage(context, "deu");

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
                type.addSite(BookFinder, true);
                type.addSite(OpenLibrary, true);

                type.addSite(StripInfoBe, activateIfDutch);
                type.addSite(LastDodoNl, activateIfDutch);
                type.addSite(StripWebBe, activateIfDutch);
                type.addSite(Bedetheque, activateIfFrench);

                type.addSite(KbNl, activateIfDutch);
                type.addSite(Bol, activateIfDutch);

                type.addSite(Dnb, activateIfGerman);
                type.addSite(Douban, activateIfChinese);
                break;
            }
            case Covers: {
                // Only add sites here that implement {@link SearchEngine.CoverByIsbn}.
                type.addSite(Amazon, true);
                type.addSite(Isfdb, true);
                type.addSite(OpenLibrary, true);
                type.addSite(KbNl, activateIfDutch);
                break;
            }
            case AltEditions: {
                //Only add sites here that implement {@link SearchEngine.AlternativeEditions}.
                type.addSite(OpenLibrary, true);
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

    /**
     * Bring up an Alert to the user if the given list includes a site where registration
     * is beneficial (but not required... it's just one of many engines here).
     *
     * @param context        Current context
     * @param sites          the list to check
     * @param callerIdString String used to flag in preferences if we showed the alert from
     *                       that caller already or not.
     * @param onFinished     (optional) Runnable to call when all sites have been processed.
     */
    public static void promptToRegister(@NonNull final Context context,
                                        @NonNull final Collection<Site> sites,
                                        @NonNull final String callerIdString,
                                        @Nullable final Runnable onFinished) {

        final Deque<EngineId> stack = sites.stream()
                                           .filter(Site::isActive)
                                           .map(Site::getEngineId)
                                           .collect(Collectors.toCollection(ArrayDeque::new));
        promptToRegister(context, stack, callerIdString, onFinished);
    }

    /**
     * Recursive stack-based prompt for registration.
     *
     * @param context        Current context
     * @param engineIds      the stack of active engines to check
     * @param callerIdString String used to flag in preferences if we showed the alert from
     *                       that caller already or not.
     * @param onFinished     (optional) Runnable to call when all sites have been processed.
     */
    private static void promptToRegister(@NonNull final Context context,
                                         @NonNull final Deque<EngineId> engineIds,
                                         @NonNull final String callerIdString,
                                         @Nullable final Runnable onFinished) {
        while (!engineIds.isEmpty()) {
            final EngineId engineId = engineIds.poll();
            //noinspection DataFlowIssue
            if (engineId.promptToRegister(context, false, callerIdString, action -> {
                switch (action) {
                    case Register:
                        throw new IllegalStateException("Engine must handle Register");

                    case NotNow:
                    case NotEver:
                        // restart the loop with the remaining sites to check.
                        promptToRegister(context, engineIds, callerIdString, onFinished);
                        return;

                    case Cancelled:
                        // user explicitly cancelled, we're done here
                        if (onFinished != null) {
                            onFinished.run();
                        }
                        break;
                }
            })) {
                // we are showing a registration dialog, quit the loop
                return;
            }
        }

        // all engines have registration, or were dismissed.
        if (onFinished != null) {
            onFinished.run();
        }
    }

    /**
     * Is this engine enabled <strong>AT ALL</strong>.
     * <p>
     * Dev. note: {@code false} in release-builds for engines still under development;
     *
     * @return flag
     */
    public boolean isEnabled() {
        return enabled;
    }

    @NonNull
    private SearchEngineConfig.Builder createConfiguration() {
        return new SearchEngineConfig.Builder(this);
    }

    /**
     * Get the preference key / generic string identifier for this engine.
     *
     * @return key
     */
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
     * Get the resource id for a piece of information text describing the website.
     *
     * @return resource id
     */
    @StringRes
    public int getInfoResId() {
        return infoResId;
    }

    /**
     * The <strong>DEFAULT</strong> url.
     * Use {@link SearchEngineConfig#getHostUrl(Context)} instead for all normal usage!
     *
     * @return default/hardcoded url for the site.
     */
    @NonNull
    String getDefaultUrl() {
        return defaultUrl;
    }

    @NonNull
    public Locale getDefaultLocale() {
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

    /**
     * Get the configuration.
     *
     * @return {@link SearchEngineConfig}
     *
     * @throws NullPointerException if there is none (which would be a bug)
     */
    @NonNull
    public SearchEngineConfig requireConfig() {
        return Objects.requireNonNull(config);
    }

    /**
     * Check if the given interface is supported by this engine.
     *
     * @param by to check
     *
     * @return {@code true} if it is
     */
    public boolean supports(@NonNull final SearchEngine.SearchBy by) {
        return by.getSearchEngineClass().isAssignableFrom(clazz);
    }

    /**
     * Create a SearchEngine instance based on the registered configuration for the given id.
     *
     * @param context Current context
     *
     * @return a new instance
     */
    @NonNull
    public SearchEngine createSearchEngine(@NonNull final Context context) {
        try {
            final Constructor<? extends SearchEngine> c =
                    clazz.getConstructor(Context.class, SearchEngineConfig.class);
            return c.newInstance(context.getApplicationContext(), config);

        } catch (@NonNull final NoSuchMethodException | IllegalAccessException
                                | InstantiationException | InvocationTargetException e) {
            throw new IllegalStateException(
                    clazz + " must implement SearchEngine(Context, SearchEngineConfig)", e);
        }
    }


    /**
     * ENHANCE: when needed... this method parked here for future thoughts.
     *  sites which need a registration of some sorts.
     * <p>
     * Check if we have a key/account; if not alert the user.
     *
     * @param context        Current context; <strong>MUST</strong> be passed in
     *                       as this call might do UI interaction.
     * @param required       {@code true} if we <strong>must</strong> have access to the site.
     *                       {@code false} if it would be beneficial but not mandatory.
     * @param callerIdString String used to flag in preferences if we showed the alert from
     *                       that caller already or not.
     * @param onResult       called after user selects an outcome
     *
     * @return {@code true} if an alert is currently shown
     */
    @UiThread
    private boolean promptToRegister(@NonNull final Context context,
                                     final boolean required,
                                     @Nullable final String callerIdString,
                                     @Nullable final Consumer<RegistrationAction> onResult) {

        return false;
    }

    /**
     * Show a registration request dialog.
     *
     * @param context        Current context
     * @param required       {@code true} if we <strong>must</strong> have access.
     *                       {@code false} if it would be beneficial.
     * @param callerIdString String used to flag in preferences if we showed the alert from
     *                       that caller already or not.
     * @param onResult       called after user selects an outcome
     *
     * @return {@code true} if an alert is currently shown
     */
    @UiThread
    boolean showRegistrationDialog(@NonNull final Context context,
                                   final boolean required,
                                   @Nullable final String callerIdString,
                                   @NonNull final Consumer<RegistrationAction> onResult) {

        @Nullable
        final String showAlertPrefKey;
        if (callerIdString != null) {
            showAlertPrefKey = getPreferenceKey() + ".hide_alert." + callerIdString;
        } else {
            showAlertPrefKey = null;
        }

        final boolean showAlert;
        if (required || showAlertPrefKey == null) {
            showAlert = true;
        } else {
            showAlert = !PreferenceManager.getDefaultSharedPreferences(context)
                                          .getBoolean(showAlertPrefKey, false);
        }

        if (showAlert) {
            final String siteName = createSearchEngine(context).getHostUrl(context);

            final AlertDialog.Builder dialogBuilder = new MaterialAlertDialogBuilder(context)
                    .setIcon(R.drawable.warning_24px)
                    .setTitle(siteName)
                    .setNegativeButton(R.string.action_not_now, (d, w) ->
                            onResult.accept(RegistrationAction.NotNow))
                    .setPositiveButton(R.string.action_learn_more, (d, w) ->
                            onResult.accept(RegistrationAction.Register))
                    .setOnCancelListener(
                            d -> onResult.accept(RegistrationAction.Cancelled));

            // Use the Dialog's themed context!
            final TextView messageView = new TextView(dialogBuilder.getContext());

            if (required) {
                messageView.setText(context.getString(
                        R.string.confirm_registration_required, siteName));

            } else {
                messageView.setText(context.getString(
                        R.string.confirm_registration_benefits, siteName,
                        context.getString(R.string.lbl_credentials)));

                // If it's not required, allow the user to permanently hide this alert
                // for the given caller.
                if (showAlertPrefKey != null) {
                    dialogBuilder.setPositiveButton(context.getString(
                            R.string.action_disable_message), (d, w) -> {
                        PreferenceManager.getDefaultSharedPreferences(context)
                                         .edit().putBoolean(showAlertPrefKey, true).apply();
                        onResult.accept(RegistrationAction.NotEver);
                    });
                }
            }

            messageView.setAutoLinkMask(Linkify.WEB_URLS);
            messageView.setMovementMethod(LinkMovementMethod.getInstance());

            dialogBuilder.setView(messageView)
                         .create()
                         .show();
        }

        return showAlert;
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
               + ", defaultUrl='" + defaultUrl + '\''
               + ", locale=" + defaultLocale
               + ", clazz=" + clazz.getName()
               + ", enabled=" + enabled
               + '}';
    }

    private enum RegistrationAction {
        /** User selected to 'learn more' and register on the given site. */
        Register,
        /** User does not want to bother now, but wants to be reminded later. */
        NotNow,
        /** Not interested, don't bother the user again. */
        NotEver,
        /** Cancelled without selecting any option. */
        Cancelled
    }
}
