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
package com.hardbacknutter.nevertoomanybooks.searchengines;

import android.content.Context;
import android.os.Parcel;
import android.os.Parcelable;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import androidx.fragment.app.Fragment;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.stream.Collectors;

import com.hardbacknutter.nevertoomanybooks.BuildConfig;
import com.hardbacknutter.nevertoomanybooks.R;
import com.hardbacknutter.nevertoomanybooks.core.tasks.Cancellable;
import com.hardbacknutter.nevertoomanybooks.entities.Identifier;
import com.hardbacknutter.nevertoomanybooks.searchengines.amazon.AmazonSearchEngine;
import com.hardbacknutter.nevertoomanybooks.searchengines.bedetheque.BedethequeSearchEngine;
import com.hardbacknutter.nevertoomanybooks.searchengines.bertrandpt.BertrandPtSearchEngine;
import com.hardbacknutter.nevertoomanybooks.searchengines.biblionetgr.BiblionetGrSearchEngine;
import com.hardbacknutter.nevertoomanybooks.searchengines.bibliotecepl.BibliotecePlSearchEngine;
import com.hardbacknutter.nevertoomanybooks.searchengines.bnf.BnfSearchEngine;
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
import com.hardbacknutter.nevertoomanybooks.searchengines.wikidata.WikidataSearchEngine;
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
 *         {@code "src/main/res/values/strings-donottranslate.xml"}
 *         Look for existing entries named {@code site_*}.
 *     </li>
 *
 *     <li>Implement {@link SearchEngine} to create the new engine class
 *         extending {@link SearchEngineBase} or {@link JsoupSearchEngineBase}
 *         or a similar setup.
 *         <ul>
 *             <li>There MUST be a public constructor annotated with {@code @Keep}
 *                 and with arguments ({@link Context},{@link SearchEngineConfig})
 *                 The context received is the <strong>application</strong> context;
 *                 i.e. a NON-localised context which cannot be used to lookup
 *                 string resources but can be used for preference-value lookups.
 *             </li>
 *             <li>Add a public static method {@code
 *                     @Keep
 *                     @NonNull
 *                     public static Builder init()
 *                 }
 *                 Create the {@link Builder} using a unique string-id:
 *                 must be all lowercase, no-spaces; this becomes the {@link #key} field.
 *                 This key will be used in preferences, database settings,...
 *                 <br>See existing engines for examples of the other parameters.
 *                 For an example of using multiple Author-Resolvers,
 *                 look at the Goodreads engine.
 *              </li>
 *              <li>If needed, add a preference fragment for the user to configure the engine.
 *                  The class MUST be annotated with {@code @Keep}.
 *                  See existing engines for examples: add a class, an XML file,
 *                  and add it in the above {@code init()}.
 *              </li>
 *          </ul>
 *      </li>
 *
 *     <li>Add an enum identifier in this class and add the implementation class.
 *     </li>
 *
 *      <li>Add a new {@link Site} instance to the one or more list(s) in {@link #registerSites}
 *      </li>
 *
 *      <li>Configure {@link AuthorResolverFactory#getEngines(Context, List)} as needed</li>
 * </ol>
 *
 * <strong>Note: NEVER change the {@link #key} of the sites</strong>.
 *
 * @see SearchEngine
 * @see SearchEngineConfig
 * @see Site
 */
public enum EngineId
        implements Parcelable {

    // NEWTHINGS: adding a new search engine: add an engine class

    Amazon(AmazonSearchEngine.class, true),
    Bedetheque(BedethequeSearchEngine.class, true),
    BertrandPt(BertrandPtSearchEngine.class, true),
    BiblionetGr(BiblionetGrSearchEngine.class, true),
    BibliotecePl(BibliotecePlSearchEngine.class, true),
    Bnf(BnfSearchEngine.class, true),
    Bol(BolSearchEngine.class, true),
    BookFinder(BookFinderSearchEngine.class, BuildConfig.ENABLE_BOOKFINDER),
    DatabazeKnih(DatabazeKnihSearchEngine.class, true),
    Dnb(DnbSearchEngine.class, true),
    Douban(DoubanSearchEngine.class, true),
    Goodreads(GoodreadsSearchEngine.class, true),
    GoogleBooks(GoogleBooksSearchEngine.class, true),
    Isfdb(IsfdbSearchEngine.class, true),
    KbNl(KbNlSearchEngine.class, true),
    LastDodoNl(LastDodoSearchEngine.class, true),
    LibraryThing(LibraryThingSearchEngine.class, BuildConfig.ENABLE_LIBRARYTHING),
    OpenLibrary(OpenLibrarySearchEngine.class, true),
    StripInfoBe(StripInfoSearchEngine.class, true),
    StripWebBe(StripWebSearchEngine.class, true),
    Wikidata(WikidataSearchEngine.class, true);

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
    /** Set at compile time from the Gradle script. */
    private final boolean enabled;
    /** {@link SearchEngine.CoverByEdition} only. */
    private final boolean multipleCoverSizes;
    @Nullable
    private final String bookIdentifierKey;
    @Nullable
    private final String authorIdentifierKey;
    @SuppressWarnings("FieldNotUsedInToString")
    @Nullable
    private final Function<SearchEngineConfig.Builder, SearchEngineConfig> configSupplier;
    @Nullable
    private final Class<? extends Fragment> preferenceFragmentClazz;
    @SuppressWarnings("FieldNotUsedInToString")
    @Nullable
    private final BiFunction<Context, SearchEngine, List<AuthorResolver>> authorResolverSupplier;
    // Don't add to toString(), it would recurse
    @SuppressWarnings("FieldNotUsedInToString")
    @Nullable
    private SearchEngineConfig config;

    /**
     * Constructor.
     *
     * @param clazz   implementation class for this engine.
     * @param enabled {@code true} or a BuildConfig.ENABLE_ variable - see app/build.gradle
     *
     * @throws IllegalStateException (debug) if the implementation class does not
     *                               have a correct {@code init()} method.
     */
    EngineId(@NonNull final Class<? extends SearchEngine> clazz,
             final boolean enabled) {
        this.clazz = clazz;
        this.enabled = enabled;

        final Builder builder;
        try {
            final Method method = clazz.getMethod("init");
            builder = Objects.requireNonNull(
                    (Builder) method.invoke(null));

        } catch (@NonNull final NoSuchMethodException
                                | InvocationTargetException
                                | IllegalAccessException e) {
            throw new IllegalStateException(e);
        }
        this.key = builder.key;
        this.labelResId = builder.labelResId;
        this.infoResIdList = builder.infoResIdList;
        this.defaultUrl = builder.defaultSearchUrl;
        this.defaultLocale = builder.defaultLocale;

        this.bookIdentifierKey = builder.bookIdentifierKey;
        this.authorIdentifierKey = builder.authorIdentifierKey;
        this.multipleCoverSizes = builder.multipleCoverSizes;

        this.authorResolverSupplier = builder.authorResolverSupplier;

        this.configSupplier = builder.configConsumer;
        this.preferenceFragmentClazz = builder.preferenceFragmentClazz;
    }

    /**
     * Register all {@link Site} instances; called during startup.
     *
     * @param context   Current context
     * @param type      the type of Site list
     * @param languages the language cache container
     *
     * @throws IllegalArgumentException (debug)
     */
    static void registerSites(@NonNull final Context context,
                              @NonNull final Site.Type type,
                              @NonNull final Languages languages) {

        // Site activation is partially done depending on the device or user set language
        // matching the site language.

        final boolean isChinese = languages.isUserLanguage(context, "zho");
        final boolean isCzech = languages.isUserLanguage(context, "cze");
        final boolean isDutch = languages.isUserLanguage(context, "nld");
        final boolean isFrench = languages.isUserLanguage(context, "fra");
        final boolean isGerman = languages.isUserLanguage(context, "deu");
        final boolean isGreek = languages.isUserLanguage(context, "ell");
        final boolean isPolish = languages.isUserLanguage(context, "pol");
        final boolean isPortuguese = languages.isUserLanguage(context, "por");
        final boolean isSlovak = languages.isUserLanguage(context, "slo");

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

                // Try to optimise by putting the most-likely-wanted at the top
                if (isPolish) {
                    type.addSite(BibliotecePl, true);
                }
                if (isPortuguese) {
                    type.addSite(BertrandPt, true);
                }
                if (isCzech || isSlovak) {
                    type.addSite(DatabazeKnih, true);
                }
                if (isGerman) {
                    type.addSite(Dnb, true);
                }
                if (isGreek) {
                    type.addSite(BiblionetGr, true);
                }
                if (isChinese) {
                    type.addSite(Douban, true);
                }
                if (isDutch) {
                    type.addSite(KbNl, true);
                }
                if (isFrench) {
                    type.addSite(Bnf, true);
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

                if (!isPolish) {
                    type.addSite(BibliotecePl, false);
                }
                if (!isPortuguese) {
                    type.addSite(BertrandPt, false);
                }
                if (!isCzech && !isSlovak) {
                    type.addSite(DatabazeKnih, false);
                }
                if (!isGerman) {
                    type.addSite(Dnb, false);
                }
                if (!isGreek) {
                    type.addSite(BiblionetGr, false);
                }
                if (!isChinese) {
                    type.addSite(Douban, false);
                }
                if (!isDutch) {
                    type.addSite(KbNl, false);
                }
                if (!isFrench) {
                    type.addSite(Bnf, false);
                }
                break;
            }
            case Covers: {
                // Only add sites here that implement {@link SearchEngine.CoverByEdition}.

                // Try to optimise by putting the most-likely-wanted at the top
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

                // Try to optimise by putting the most-likely-wanted at the top
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
    @NonNull
    public static List<EngineId> getSearchOnSite() {
        return Arrays.stream(values())
                     .filter(EngineId::isEnabled)
                     .filter(engineId -> engineId.supports(
                             SearchEngine.SearchOnSite.class))
                     .sorted(Comparator.comparing(Enum::name))
                     .collect(Collectors.toList());
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
     * Build the configuration.
     */
    void config() {
        final SearchEngineConfig.Builder configBuilder =
                new SearchEngineConfig.Builder(this);
        if (configSupplier != null) {
            config = configSupplier.apply(configBuilder);
        } else {
            config = configBuilder.build(SearchEngineConfig::new);
        }
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

    @Nullable
    public Class<? extends Fragment> getPreferenceFragmentClazz() {
        return preferenceFragmentClazz;
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
     * Use {@link SearchEngineConfig#getHostUrl()} instead for all normal usage!
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
     * Get the book {@link Identifier} key.
     *
     * @return key
     */
    @NonNull
    public Optional<String> getBookIdentifierKey() {
        return bookIdentifierKey == null ? Optional.empty() : Optional.of(bookIdentifierKey);
    }

    /**
     * Get the author {@link Identifier} key.
     *
     * @return key
     */
    @NonNull
    public Optional<String> getAuthorIdentifierKey() {
        return authorIdentifierKey == null ? Optional.empty() : Optional.of(authorIdentifierKey);
    }

    @NonNull
    List<AuthorResolver> getAuthorResolver(@NonNull final Context context,
                                           @NonNull final SearchEngine searchEngine) {
        if (authorResolverSupplier != null) {
            return authorResolverSupplier.apply(context, searchEngine);
        }
        return List.of();
    }

    /**
     * {@link SearchEngine.CoverByEdition} only.
     * <p>
     * A site can support a single (default) or multiple sizes.
     *
     * @return {@code true} if multiple sizes are supported.
     */
    public boolean hasMultipleCoverSizes() {
        return multipleCoverSizes;
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
     * <p>
     * <strong>REMINDER: you usually (but not always) need to call
     * {@link SearchEngine#setCaller(Cancellable)}.
     * Check other places in the code to know for sure.</strong>
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
               + ", enabled=" + enabled
               + ", defaultUrl=`" + defaultUrl + '`'
               + ", locale=" + defaultLocale
               + ", multipleCoverSizes=" + multipleCoverSizes
               + ", bookIdentifierKey=" + bookIdentifierKey
               + ", authorIdentifierKey=" + authorIdentifierKey
               + ", clazz=" + clazz.getName()
               + ", preferenceFragmentClazz=" + preferenceFragmentClazz
               + '}';
    }

    public static class Builder {

        /** The preference key / generic string identifier for this engine. */
        @NonNull
        private final String key;

        /** The user displayable name for this engine. */
        @StringRes
        private final int labelResId;

        @NonNull
        private final List<Integer> infoResIdList;

        /** Default url. */
        @NonNull
        private final String defaultSearchUrl;

        @NonNull
        private final Locale defaultLocale;

        private boolean multipleCoverSizes;

        @Nullable
        private String bookIdentifierKey;
        @Nullable
        private String authorIdentifierKey;
        @Nullable
        private Class<? extends Fragment> preferenceFragmentClazz;
        @Nullable
        private Function<SearchEngineConfig.Builder, SearchEngineConfig> configConsumer;
        @Nullable
        private BiFunction<Context, SearchEngine, List<AuthorResolver>> authorResolverSupplier;

        /**
         * Constructor.
         *
         * @param key              The preference key / generic string identifier for this engine.
         * @param labelResId       The user displayable name for this engine.
         * @param infoResIdList    A list of informational string resources about this site
         * @param defaultSearchUrl for the site
         * @param defaultLocale    for the site
         */
        public Builder(@NonNull final String key,
                       @StringRes final int labelResId,
                       @NonNull final List<Integer> infoResIdList,
                       @NonNull final String defaultSearchUrl,
                       @NonNull final Locale defaultLocale) {
            this.key = key;
            this.labelResId = labelResId;
            this.infoResIdList = infoResIdList;
            this.defaultSearchUrl = defaultSearchUrl;
            this.defaultLocale = defaultLocale;
        }

        /**
         * Set the class which will allow the user to see/edit the Settings.
         *
         * @param clazz a Fragment
         *
         * @return {@code this} (for chaining)
         */
        public Builder setPreferenceFragmentClazz(@NonNull final Class<? extends Fragment> clazz) {
            preferenceFragmentClazz = clazz;
            return this;
        }


        @NonNull
        public Builder setIdentifierKeys(@NonNull final String identifierKey) {
            return setIdentifierKeys(identifierKey, identifierKey);
        }

        /**
         * Set the {@link Identifier} for the website specific identifier for a book and an author.
         * <p>
         * FIXME: remove the need for authorIdentifierKey. We should that from the resolver supplier
         *
         * @param bookIdentifierKey   key
         * @param authorIdentifierKey key
         *
         * @return {@code this} (for chaining)
         */
        @NonNull
        public Builder setIdentifierKeys(@NonNull final String bookIdentifierKey,
                                         @Nullable final String authorIdentifierKey) {
            this.bookIdentifierKey = bookIdentifierKey;
            this.authorIdentifierKey = authorIdentifierKey;
            return this;
        }

        /**
         * Does the site support multiple cover sizes.
         *
         * @param supports flag
         *
         * @return {@code this} (for chaining)
         */
        @NonNull
        public Builder setMultipleCoverSizes(final boolean supports) {
            this.multipleCoverSizes = supports;
            return this;
        }

        /**
         * If the engine is going to do author resolving, configure the
         * resolvers to use by setting a suitable list.
         *
         * @param supplier for the list
         *
         * @return {@code this} (for chaining)
         */
        @NonNull
        public Builder setAuthorResolverSupplier(
                @NonNull final BiFunction<Context, SearchEngine, List<AuthorResolver>> supplier) {
            this.authorResolverSupplier = supplier;
            return this;
        }

        /**
         * Finish building. The consumer can be used to add configuration tuning,
         * and must end with calling {@link SearchEngineConfig.Builder#build}
         *
         * @param configConsumer to finish off
         *
         * @return {@code this} (for chaining)
         */
        @NonNull
        public Builder setConfig(
                @NonNull final Function<SearchEngineConfig.Builder, SearchEngineConfig>
                        configConsumer) {
            this.configConsumer = configConsumer;
            return this;
        }
    }
}
