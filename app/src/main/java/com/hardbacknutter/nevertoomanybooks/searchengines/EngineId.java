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
package com.hardbacknutter.nevertoomanybooks.searchengines;

import android.content.Context;
import android.os.Parcel;
import android.os.Parcelable;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import com.hardbacknutter.nevertoomanybooks.BuildConfig;
import com.hardbacknutter.nevertoomanybooks.R;
import com.hardbacknutter.nevertoomanybooks.ServiceLocator;
import com.hardbacknutter.nevertoomanybooks.entities.Identifier;
import com.hardbacknutter.nevertoomanybooks.searchengines.amazon.AmazonSearchEngine;
import com.hardbacknutter.nevertoomanybooks.searchengines.bedetheque.BedethequeSearchEngine;
import com.hardbacknutter.nevertoomanybooks.searchengines.bertrandpt.BertrandPtSearchEngine;
import com.hardbacknutter.nevertoomanybooks.searchengines.bol.BolSearchEngine;
import com.hardbacknutter.nevertoomanybooks.searchengines.bookfinder.BookFinderSearchEngine;
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
import com.hardbacknutter.nevertoomanybooks.utils.Languages;

/**
 * This class contains the <strong>immutable</strong> configuration
 * data for a {@link SearchEngine}.
 * <p>
 * It also provides the bootstrapping logic to register {@link SearchEngine}'s.
 * <p>
 * To add a new site to search, follow these steps:
 * <ol>
 *     <li>Add a string resource with the name of the site engine in:
 *         "src/main/res/values/strings-donottranslate.xml"
 *         (look for existing entries named 'site_*')
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
 *         This is the {@link #key} field.
 *     </li>
 *
 *     <li>Configure the engine in the method {@link #createEngineConfigurations(Context)},
 *         using {@link SearchEngineConfig.Builder} methods.
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
 *      <li>Optional: if the engine/site will store a external book id (or any other specific
 *          fields) in the local database, extra steps will need to be taken.
 *          TODO: document steps for adding a SID to a new engine
 *      </li>
 *
 * </ol>
 * <p>
 *
 * <strong>Note: NEVER change the {@link #key} of the sites</strong>.
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
           List.of(R.string.site_description_various_languages,
                   R.string.site_description_shop),
           // amazon.com, amazon.ca : blocked by captcha
           "https://www.amazon.co.uk",
           // The Locale will be dynamically set depending on the country site
           Locale.US,
           AmazonSearchEngine.class,
           true),

    /** French language (and to some extend other languages) comics. */
    Bedetheque("bedetheque",
               R.string.site_bedetheque,
               List.of(R.string.site_description_french,
                       R.string.site_description_catalog,
                       R.string.site_description_eu_comics),
               "https://www.bedetheque.com",
               Locale.FRANCE,
               BedethequeSearchEngine.class,
               true),

    /** All genres; portuguese and some other languages. */
    BertrandPt(BertrandPtSearchEngine.class, true),

    /** All genres; dutch and many other languages. */
    Bol(BolSearchEngine.class, true),

    /** All genres. English only? */
    BookFinder(BookFinderSearchEngine.class, BuildConfig.ENABLE_BOOKFINDER),

    /** Czech language books. */
    DatabazeKnih(DatabazeKnihSearchEngine.class, true),

    /** German language books & comics. */
    Dnb("dnb",
        R.string.site_dnb_de,
        List.of(R.string.site_description_german,
                R.string.site_description_catalog),
        "https://katalog.dnb.de",
        new Locale("de", "DE"),
        DnbSearchEngine.class,
        true),

    /** Chinese language books & comics. */
    Douban(DoubanSearchEngine.class, true),

    /** All genres. */
    Goodreads(GoodreadsSearchEngine.class, true),

    /** All genres. */
    GoogleBooks(GoogleBooksSearchEngine.class, true),

    /** Speculative Fiction only. e.g. Science-Fiction/Fantasy etc... */
    Isfdb("isfdb",
          R.string.site_isfdb,
          List.of(R.string.site_description_english_and_more,
                  R.string.site_description_catalog,
                  R.string.site_description_fsf),
          "https://www.isfdb.org",
          Locale.US,
          IsfdbSearchEngine.class,
          true),

    /** Dutch language books & comics. */
    KbNl("kbnl",
         R.string.site_kb_nl,
         List.of(R.string.site_description_dutch_and_more,
                 R.string.site_description_catalog),
         "https://webggc.oclc.org",
         new Locale("nl", "NL"),
         KbNlSearchEngine.class,
         true),

    /** Dutch language (and to some extend other languages) comics. */
    LastDodoNl("lastdodo",
               R.string.site_lastdodo_nl,
               List.of(R.string.site_description_dutch_and_more,
                       R.string.site_description_catalog,
                       R.string.site_description_eu_comics),
               "https://www.lastdodo.nl",
               new Locale("nl", "NL"),
               LastDodoSearchEngine.class,
               true),

    LibraryThing(LibraryThingSearchEngine.class, BuildConfig.ENABLE_LIBRARYTHING),

    /** All genres. */
    OpenLibrary("openlibrary",
                R.string.site_open_library,
                List.of(R.string.site_description_english_and_more,
                        R.string.site_description_catalog),
                "https://openlibrary.org",
                Locale.US,
                OpenLibrarySearchEngine.class,
                true),

    /** Dutch language (and to some extend other languages) comics. */
    StripInfoBe("stripinfo",
                R.string.site_stripinfo_be,
                List.of(R.string.site_description_dutch_and_more,
                        R.string.site_description_catalog,
                        R.string.site_description_eu_comics),
                "https://www.stripinfo.be",
                new Locale("nl", "BE"),
                StripInfoSearchEngine.class,
                true),

    /**
     * Dutch language (and to some extend other languages) comics.
     * The site can be accessed in Dutch,French,English. We use the Dutch site for access.
     * The main reason for this one is having access to current list-prices;
     * otherwise the recommendation is to use {@link #StripInfoBe} and {@link #LastDodoNl}.
     */
    StripWebBe("stripweb",
               R.string.site_stripweb_be,
               List.of(R.string.site_description_dutch_and_more,
                       R.string.site_description_shop,
                       R.string.site_description_eu_comics),
               "https://www.stripweb.be",
               new Locale("nl", "BE"),
               StripWebSearchEngine.class,
               true);

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
    @NonNull
    private final List<Integer> infoResIdList;

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


    /** {@link SearchEngine.CoverByEdition} only. */
    @SuppressWarnings("FieldNotUsedInToString")
    private boolean supportsMultipleCoverSizes;

    @Nullable
    private String identifierKey;

    /**
     * Constructor.
     *
     * @param key           The preference key / generic string identifier for this engine.
     * @param labelResId    The user displayable name for this engine.
     * @param infoResIdList A list of informational string resources about this site
     * @param defaultUrl    for the site
     * @param defaultLocale for the site
     * @param clazz         implementation class for this engine.
     * @param enabled       {@code true} or a BuildConfig.ENABLE_ variable - see app/build.gradle
     */
    EngineId(@NonNull final String key,
             @StringRes final int labelResId,
             @NonNull final List<Integer> infoResIdList,
             @NonNull final String defaultUrl,
             @NonNull final Locale defaultLocale,
             @NonNull final Class<? extends SearchEngine> clazz,
             final boolean enabled) {
        this.key = key;
        this.labelResId = labelResId;
        this.infoResIdList = infoResIdList;
        this.defaultUrl = defaultUrl;
        this.defaultLocale = defaultLocale;
        this.clazz = clazz;
        this.enabled = enabled;
    }

    EngineId(@NonNull final Class<? extends SearchEngine> clazz,
             final boolean enabled) {
        this.clazz = clazz;
        this.enabled = enabled;

        final EngineData engineData;
        try {
            final Method method = clazz.getMethod("getEngineData");
            engineData = Objects.requireNonNull(
                    (EngineData) method.invoke(null));

        } catch (@NonNull final NoSuchMethodException
                                | InvocationTargetException
                                | IllegalAccessException e) {
            throw new RuntimeException(e);
        }
        this.key = engineData.key;
        this.labelResId = engineData.labelResId;
        this.infoResIdList = engineData.infoResIdList;
        this.defaultUrl = engineData.defaultUrl;
        this.defaultLocale = engineData.defaultLocale;
    }

    /**
     * Create all {@link SearchEngine} configurations; called during startup.
     *
     * @param context <strong>Application</strong> or <strong>test</strong> context.
     */
    static void createEngineConfigurations(@NonNull final Context context) {
        // The engine order here is not important; just keep them alphabetical

        //FIXME: cleanup the mix of the config builder and the post create config

        if (Amazon.isEnabled()) {
            Amazon.setIdentifierKey(Identifier.SID_ASIN)
                  .createConfig()
                  .build(SearchEngineConfig::new);
        }
        if (Bedetheque.isEnabled()) {
            Bedetheque.setIdentifierKey(Identifier.SID_BEDETHEQUE)
                      .createConfig()
                      // default timeouts based on limited testing
                      .setConnectTimeoutMs(15_000)
                      .setReadTimeoutMs(60_000)
                      .build(SearchEngineConfig::new);
        }
        if (Bol.isEnabled()) {
            Bol.createConfig()
               .setTagsToIgnore(Set.of("Boeken", "Livres"))
               .build(SearchEngineConfig::new);
        }
        if (BertrandPt.isEnabled()) {
            BertrandPt.createConfig()
                      .setTagsToIgnore(Set.of("Livros", "Livros em Português"))
                      .build(SearchEngineConfig::new);
        }
        if (BookFinder.isEnabled()) {
            BookFinder.createConfig()
                      .build(SearchEngineConfig::new);
        }
        if (DatabazeKnih.isEnabled()) {
            DatabazeKnih.setIdentifierKey(Identifier.SID_DATABAZE_KNIH)
                        .createConfig()
                        .build(SearchEngineConfig::new);
        }
        if (Dnb.isEnabled()) {
            Dnb.setIdentifierKey(Identifier.SID_DNB)
               .createConfig()
               .build(SearchEngineConfig::new);
        }
        if (Douban.isEnabled()) {
            Douban.setIdentifierKey(Identifier.SID_DOUBAN)
                  .createConfig()
                  .build(SearchEngineConfig::new);
        }
        if (Goodreads.isEnabled()) {
            Goodreads.setIdentifierKey(Identifier.SID_GOODREADS)
                     .createConfig()
                     .build(SearchEngineConfig::new);
        }
        if (GoogleBooks.isEnabled()) {
            GoogleBooks.setSupportsMultipleCoverSizes(true)
                       .setIdentifierKey(Identifier.SID_GOOGLE)
                       .createConfig()
                       .build(SearchEngineConfig::new);
        }
        if (Isfdb.isEnabled()) {
            Isfdb.setIdentifierKey(Identifier.SID_ISFDB)
                 .createConfig()
                 // default timeouts based on limited testing
                 .setConnectTimeoutMs(20_000)
                 .setReadTimeoutMs(60_000)
                 .build(SearchEngineConfig::new);
        }
        if (KbNl.isEnabled()) {
            KbNl.setSupportsMultipleCoverSizes(true)
                .setIdentifierKey(Identifier.SID_KBNL)
                .createConfig()
                .build(SearchEngineConfig::new);
        }
        if (LastDodoNl.isEnabled()) {
            LastDodoNl.setIdentifierKey(Identifier.SID_LAST_DODO_NL)
                      .createConfig()
                      .setPrefersIsbn10(true)
                      .build(SearchEngineConfig::new);
        }
        if (LibraryThing.isEnabled()) {
            LibraryThing.setIdentifierKey(Identifier.SID_LIBRARY_THING)
                        .createConfig()
                        .build(SearchEngineConfig::new);
        }
        if (OpenLibrary.isEnabled()) {
            OpenLibrary.setIdentifierKey(Identifier.SID_OPEN_LIBRARY)
                       .setSupportsMultipleCoverSizes(true)
                       .createConfig()
                       .build(SearchEngineConfig::new);
        }
        if (StripInfoBe.isEnabled()) {
            StripInfoBe.setIdentifierKey(Identifier.SID_STRIP_INFO)
                       .createConfig()
                       // default timeouts based on limited testing
                       .setConnectTimeoutMs(7_000)
                       .setReadTimeoutMs(60_000)
                       .build(SearchEngineConfig::new);
        }
        if (StripWebBe.isEnabled()) {
            StripWebBe.createConfig()
                      .build(SearchEngineConfig::new);
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

        // Site activation is partially done depending on the device or user set language
        // matching the site language.
        final boolean isChinese = languages.isUserLanguage(context, "zho");
        final boolean isCzech = languages.isUserLanguage(context, "cze");
        final boolean isSlovak = languages.isUserLanguage(context, "slo");

        final boolean isDutch = languages.isUserLanguage(context, "nld");
        final boolean isFrench = languages.isUserLanguage(context, "fra");
        final boolean isGerman = languages.isUserLanguage(context, "deu");
        final boolean isPortuguese = languages.isUserLanguage(context, "por");

        //NEWTHINGS: adding a new search engine: add to the list type as needed.

        // The order added here is the default order they will be used, but the user
        // can reorder the lists in preferences.
        // TODO: optimize the lists depending on device locale/language
        switch (type) {
            case Data: {
                // Only add sites here that implement one or more of
                // {@link SearchEngine.ByExternalId}
                // {@link SearchEngine.ByIsbn}
                // {@link SearchEngine.ByBarcode}
                // {@link SearchEngine.ByText}

                // Try to optimize by putting the most-likely-wanted at the top
                if (isPortuguese) {
                    type.addSite(BertrandPt, true);
                }
                if (isCzech || isSlovak) {
                    type.addSite(DatabazeKnih, true);
                }
                if (isGerman) {
                    type.addSite(Dnb, true);
                }
                if (isChinese) {
                    type.addSite(Douban, true);
                }
                if (isDutch) {
                    type.addSite(KbNl, true);
                }

                // All sites unless added above
                type.addSite(Amazon, true);
                type.addSite(Goodreads, true);
                type.addSite(GoogleBooks, true);
                type.addSite(Isfdb, true);
                type.addSite(BookFinder, true);
                type.addSite(OpenLibrary, true);

                type.addSite(StripInfoBe, isDutch);
                type.addSite(LastDodoNl, isDutch);
                type.addSite(StripWebBe, isDutch || isFrench);
                type.addSite(Bedetheque, isFrench);

                type.addSite(Bol, isDutch || isFrench);

                if (!isPortuguese) {
                    type.addSite(BertrandPt, false);
                }
                if (!isCzech && !isSlovak) {
                    type.addSite(DatabazeKnih, false);
                }
                if (!isGerman) {
                    type.addSite(Dnb, false);
                }
                if (!isChinese) {
                    type.addSite(Douban, false);
                }
                if (!isDutch) {
                    type.addSite(KbNl, false);
                }
                break;
            }
            case Covers: {
                // Only add sites here that implement {@link SearchEngine.CoverByEdition}.

                // Try to optimize by putting the most-likely-wanted at the top
                if (isChinese) {
                    type.addSite(Douban, true);
                }
                if (isDutch) {
                    type.addSite(KbNl, true);
                }

                // All sites unless added above
                type.addSite(Amazon, true);
                type.addSite(Isfdb, true);
                type.addSite(OpenLibrary, true);
                type.addSite(GoogleBooks, true);

                if (!isDutch) {
                    type.addSite(KbNl, false);
                }
                if (!isChinese) {
                    type.addSite(Douban, false);
                }
                break;
            }
            case AltEditions: {
                //Only add sites here that implement {@link SearchEngine.AlternativeEditions}.

                // Try to optimize by putting the most-likely-wanted at the top
                if (isChinese) {
                    type.addSite(Douban, true);
                }

                // All sites unless added above
                if (BuildConfig.ENABLE_LIBRARYTHING) {
                    type.addSite(LibraryThing, true);
                }
                type.addSite(OpenLibrary, true);
                type.addSite(Isfdb, true);

                if (!isChinese) {
                    type.addSite(Douban, false);
                }
                break;
            }

            default:
                throw new IllegalArgumentException(String.valueOf(type));
        }
    }

    /**
     * Collect the website engines for which we support searching via url.
     * Sorted by name.
     *
     * @return list
     */
    public static List<EngineId> getSearchOnSite() {
        return Arrays.stream(values())
                     .filter(EngineId::isEnabled)
                     .filter(engineId -> engineId.supports(
                             SearchEngine.SearchOnSite.class))
                     .sorted(Comparator.comparing(Enum::name))
                     .collect(Collectors.toList());
    }

    @NonNull
    private EngineId setSupportsMultipleCoverSizes(final boolean supportsMultipleCoverSizes) {
        this.supportsMultipleCoverSizes = supportsMultipleCoverSizes;
        return this;
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
     * Get an informational text describing the website.
     *
     * @param context Current context
     *
     * @return info
     */
    @NonNull
    public String getInfo(@NonNull final Context context) {
        return infoResIdList.stream()
                            .map(context::getString)
                            .collect(Collectors.joining(
                                    context.getString(R.string.list_semicolon)));
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

    @Nullable
    public String getIdentifierKey() {
        return identifierKey;
    }

    /**
     * Set the {@link Identifier} for the website specific identifier for a book.
     *
     * @param identifierKey key
     *
     * @return {@code this} (for chaining)
     */
    @NonNull
    private EngineId setIdentifierKey(@NonNull final String identifierKey) {
        this.identifierKey = identifierKey;
        return this;
    }

    @NonNull
    public Optional<Identifier> getIdentifier() {
        if (identifierKey != null) {
            return ServiceLocator.getInstance().getIdentifierDao().findByKey(identifierKey);
        } else {
            return Optional.empty();
        }
    }

    /**
     * {@link SearchEngine.CoverByEdition} only.
     * <p>
     * A site can support a single (default) or multiple sizes.
     *
     * @return {@code true} if multiple sizes are supported.
     */
    public boolean supportsMultipleCoverSizes() {
        return supportsMultipleCoverSizes;
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

    void setConfig(@NonNull final SearchEngineConfig config) {
        this.config = config;
    }

    @NonNull
    private SearchEngineConfig.Builder createConfig() {
        return new SearchEngineConfig.Builder(this);
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
     * Check if the given search interface is supported by this engine.
     *
     * @param by interface to test
     *
     * @return flag
     */
    public boolean supports(@NonNull final Class<? extends SearchEngine> by) {
        return by.isAssignableFrom(clazz);
    }

    /**
     * Create a SearchEngine instance based on the registered configuration for the given id.
     *
     * @param context Application context
     *
     * @return a new instance
     *
     * @throws IllegalStateException on any error
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
               + "key=`" + key + '`'
               + ", defaultUrl=`" + defaultUrl + '`'
               + ", locale=" + defaultLocale
               + ", clazz=" + clazz.getName()
               + ", enabled=" + enabled

               + ", identifierKey=" + identifierKey
               + '}';
    }
}
