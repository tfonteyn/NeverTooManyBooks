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

import android.os.Parcel;
import android.os.Parcelable;

import androidx.annotation.NonNull;

import java.util.List;
import java.util.Locale;

import com.hardbacknutter.nevertoomanybooks.BuildConfig;
import com.hardbacknutter.nevertoomanybooks.ServiceLocator;
import com.hardbacknutter.nevertoomanybooks.searchengines.amazon.AmazonSearchEngine;
import com.hardbacknutter.nevertoomanybooks.searchengines.goodreads.GoodreadsSearchEngine;
import com.hardbacknutter.nevertoomanybooks.searchengines.googlebooks.GoogleBooksSearchEngine;
import com.hardbacknutter.nevertoomanybooks.searchengines.isfdb.IsfdbSearchEngine;
import com.hardbacknutter.nevertoomanybooks.searchengines.kbnl.KbNlSearchEngine;
import com.hardbacknutter.nevertoomanybooks.searchengines.lastdodo.LastDodoSearchEngine;
import com.hardbacknutter.nevertoomanybooks.searchengines.librarything.LibraryThingSearchEngine;
import com.hardbacknutter.nevertoomanybooks.searchengines.openlibrary.OpenLibrarySearchEngine;
import com.hardbacknutter.nevertoomanybooks.searchengines.stripinfo.StripInfoSearchEngine;
import com.hardbacknutter.nevertoomanybooks.settings.sites.IsfdbPreferencesFragment;

/**
 * Manages the setup of {@link SearchEngine}'s.
 * <p>
 * To add a new site to search, follow these steps:
 * <ol>
 *     <li>Add a string resource with the name of the site engine in:
 *          "src/main/res/values/donottranslate.xml" (look for existing entries named 'site_*')
 *     </li>
 *
 *     <li>Implement {@link SearchEngine} to create the new engine class
 *          extending {@link SearchEngineBase} or {@link JsoupSearchEngineBase}
 *          or a similar setup.<br>
 *          Don't forget the "@Keep" annotation on the required constructor.<br>
 *          Configure the engine using {@link SearchEngineConfig}.
 *      </li>
 *
 *     <li>Add an identifier in this class and give it a unique string-id
 *         and the implementation class.
 *         <br>The string-id must be all lowercase, no-spaces.
 *         It will be used in preferences, database settings,...
 *     </li>
 *
 *     <li>Add this identifier to {@link #DATA_RELIABILITY_ORDER}.</li>
 *
 *     <li>Add the {@link SearchEngine} class to
 *         {@link #registerSearchEngineClasses(SearchEngineRegistry)}
 *     </li>
 *
 *      <li>Add a new {@link Site} instance to the one or more list(s)
 *          in {@link #createSiteList}
 *      </li>
 *
 *      <li>Optional: if the engine/site will store a external book id (or any other specific
 *          fields) in the local database, extra steps will need to be taken.
 *          TODO: document steps: search the code for "NEWTHINGS: adding a new search engine"
 *      </li>
 *
 *      <li>Optional: Add a preference fragment for the user to configure the engine.
 *          See the {@link IsfdbPreferencesFragment} for an example:
 *          a class, an xml file, and an entry in "src/main/res/xml/preferences.xml"
 *      </li>
 * </ol>
 * <p>
 *
 * <strong>Note: NEVER change the "key" of the sites</strong>.
 *
 * @see SearchEngine
 * @see SearchEngineConfig
 * @see SearchEngineRegistry
 * @see Site
 */
public enum EngineId
        implements Parcelable {

    /** All genres. */
    GoogleBooks("googlebooks", GoogleBooksSearchEngine.class),

    /** All genres. */
    Amazon("amazon", AmazonSearchEngine.class),

    /** All genres. */
    LibraryThing("librarything", LibraryThingSearchEngine.class),

    /** All genres. */
    Goodreads("goodreads", GoodreadsSearchEngine.class),

    /** Speculative Fiction only. e.g. Science-Fiction/Fantasy etc... */
    IsfDb("isfdb", IsfdbSearchEngine.class),

    /** All genres. */
    OpenLibrary("openlibrary", OpenLibrarySearchEngine.class),

    /** Dutch language books & comics. */
    KbNl("kbnl", KbNlSearchEngine.class),

    /** Dutch language (and to some extend other languages) comics. */
    StripInfoBe("stripinfo", StripInfoSearchEngine.class),

    /** Dutch language (and to some extend other languages) comics. */
    LastDodoNl("lastdodo", LastDodoSearchEngine.class);

    // NEWTHINGS: adding a new search engine: add the engine id as a new bit

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

    /**
     * The search engine ids in reliability of data order.
     * Order is hardcoded based on experience. ENHANCE: make this user configurable
     * NEWTHINGS: adding a new search engine: add the engine id
     */
    static final List<EngineId> DATA_RELIABILITY_ORDER =
            List.of(IsfDb,
                    StripInfoBe,
                    Amazon,
                    GoogleBooks,
                    LastDodoNl,
                    /* KB_NL, */
                    OpenLibrary);

    @NonNull
    private final String key;
    @NonNull
    private final Class<? extends SearchEngine> clazz;

    EngineId(@NonNull final String key,
             @NonNull final Class<? extends SearchEngine> clazz) {
        this.key = key;
        this.clazz = clazz;
    }

    /**
     * Register all {@link SearchEngine} classes.
     * <p>
     * NEWTHINGS: adding a new search engine: add the search engine class
     *
     * @param registry singleton
     */
    static void registerSearchEngineClasses(@NonNull final SearchEngineRegistry registry) {
        //dev note: we could scan for annotations/interfaces on the engine classes...
        // Not really worth the hassle.
        // For the BuildConfig.ENABLE_ usage: see app/build.gradle
        // The order added is not relevant

        // full/english functionality
        registry.add(AmazonSearchEngine.createConfig())
                .add(GoodreadsSearchEngine.createConfig())
                .add(IsfdbSearchEngine.createConfig())
                .add(OpenLibrarySearchEngine.createConfig());

        if (BuildConfig.ENABLE_GOOGLE_BOOKS) {
            registry.add(GoogleBooksSearchEngine.createConfig());
        }

        // Alternative Edition search only!
        if (BuildConfig.ENABLE_LIBRARY_THING_ALT_ED) {
            registry.add(LibraryThingSearchEngine.createConfig());
        }


        // Dutch.
        if (BuildConfig.ENABLE_KB_NL) {
            registry.add(KbNlSearchEngine.createConfig());
        }
        // Dutch.
        if (BuildConfig.ENABLE_LAST_DODO) {
            registry.add(LastDodoSearchEngine.createConfig());
        }
        // Dutch.
        if (BuildConfig.ENABLE_STRIP_INFO) {
            registry.add(StripInfoSearchEngine.createConfig());
        }
    }

    /**
     * Register all {@link Site} instances; called during startup.
     *
     * @param systemLocale device Locale <em>(passed in to allow mocking)</em>
     * @param userLocale   user locale <em>(passed in to allow mocking)</em>
     * @param type         the type of Site list
     */
    static void createSiteList(@NonNull final Locale systemLocale,
                               @NonNull final Locale userLocale,
                               @NonNull final Site.Type type) {

        // Certain sites are only enabled by default if the device or user set language
        // matches the site language.
        // Dutch websites:
        final boolean enableIfDutch = ServiceLocator.getInstance().getLanguages()
                                                    .isLang(systemLocale, userLocale, "nld");

        //NEWTHINGS: add new search engine: add to the 3 lists as needed.

        // yes, we could loop over the SearchEngine's, and detect their interfaces.
        // but this gives more control.
        // For the BuildConfig.ENABLE_ usage: see app/build.gradle
        //
        // The order added here is the default order they will be used, but the user
        // can reorder the lists in preferences.

        switch (type) {
            case Data: {
                type.addSite(Amazon);

                if (BuildConfig.ENABLE_GOOGLE_BOOKS) {
                    type.addSite(GoogleBooks);
                }

                type.addSite(IsfDb);

                if (BuildConfig.ENABLE_STRIP_INFO) {
                    type.addSite(StripInfoBe, enableIfDutch);
                }
                if (BuildConfig.ENABLE_LAST_DODO) {
                    type.addSite(LastDodoNl, enableIfDutch);
                }
                if (BuildConfig.ENABLE_KB_NL) {
                    type.addSite(KbNl, enableIfDutch);
                }

                // Disabled by default as data from this site is not very complete.
                type.addSite(OpenLibrary, false);
                break;
            }
            case Covers: {
                // Only add sites here that implement {@link SearchEngine.CoverByIsbn}.

                type.addSite(Amazon);

                type.addSite(IsfDb);

                if (BuildConfig.ENABLE_KB_NL) {
                    type.addSite(KbNl, enableIfDutch);
                }

                // Disabled by default as this site lacks many covers.
                type.addSite(OpenLibrary, false);
                break;
            }
            case AltEditions: {
                //Only add sites here that implement {@link SearchEngine.AlternativeEditions}.

                if (BuildConfig.ENABLE_LIBRARY_THING_ALT_ED) {
                    type.addSite(LibraryThing);
                }

                type.addSite(IsfDb);
                break;
            }

            case ViewOnSite: {
                // only add sites here that implement {@link SearchEngine.ViewBookByExternalId}.

                type.addSite(Goodreads);
                type.addSite(IsfDb);

                if (BuildConfig.ENABLE_LIBRARY_THING_ALT_ED) {
                    type.addSite(LibraryThing);
                }

                type.addSite(OpenLibrary);

                if (BuildConfig.ENABLE_STRIP_INFO) {
                    type.addSite(StripInfoBe, enableIfDutch);
                }
                if (BuildConfig.ENABLE_LAST_DODO) {
                    type.addSite(LastDodoNl, enableIfDutch);
                }
                break;
            }

            default:
                throw new IllegalArgumentException(String.valueOf(type));
        }
    }

    @NonNull
    public String getPreferenceKey() {
        return key;
    }

    @NonNull
    public Class<? extends SearchEngine> getClazz() {
        return clazz;
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
}
