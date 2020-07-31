/*
 * @Copyright 2020 HardBackNutter
 * @License GNU General Public License
 *
 * This file is part of NeverTooManyBooks.
 *
 * In August 2018, this project was forked from:
 * Book Catalogue 5.2.2 @2016 Philip Warner & Evan Leybourn
 *
 * Without their original creation, this project would not exist in its
 * current form. It was however largely rewritten/refactored and any
 * comments on this fork should be directed at HardBackNutter and not
 * at the original creators.
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
package com.hardbacknutter.nevertoomanybooks.searches;

import android.os.Parcelable;

import androidx.annotation.IntDef;
import androidx.annotation.NonNull;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.util.Locale;

import com.hardbacknutter.nevertoomanybooks.BuildConfig;
import com.hardbacknutter.nevertoomanybooks.debug.ErrorMsg;
import com.hardbacknutter.nevertoomanybooks.searches.amazon.AmazonSearchEngine;
import com.hardbacknutter.nevertoomanybooks.searches.goodreads.GoodreadsSearchEngine;
import com.hardbacknutter.nevertoomanybooks.searches.googlebooks.GoogleBooksSearchEngine;
import com.hardbacknutter.nevertoomanybooks.searches.isfdb.IsfdbSearchEngine;
import com.hardbacknutter.nevertoomanybooks.searches.kbnl.KbNlSearchEngine;
import com.hardbacknutter.nevertoomanybooks.searches.lastdodo.LastDodoSearchEngine;
import com.hardbacknutter.nevertoomanybooks.searches.librarything.LibraryThingSearchEngine;
import com.hardbacknutter.nevertoomanybooks.searches.openlibrary.OpenLibrarySearchEngine;
import com.hardbacknutter.nevertoomanybooks.searches.stripinfo.StripInfoSearchEngine;
import com.hardbacknutter.nevertoomanybooks.settings.sites.IsfdbPreferencesFragment;
import com.hardbacknutter.nevertoomanybooks.utils.LanguageUtils;

/**
 * Manages the setup of {@link SearchEngine}'s.
 * <br><br>
 * Classes involved:
 * <ul>
 *      <li>{@link SearchEngine} :
 *          The interface that the engine class implements and responsible for the actual searches.
 *      </li>
 *      <li>{@link SearchEngine.Configuration} :
 *          The annotation that configures the engine class.
 *      </li>
 *
 *      <li>{@link Site.Config} :
 *          Encapsulates the engine class + {@link SearchEngine.Configuration}
 *          and resolves some complex objects ready to use.
 *          Singletons Stored in a STATIC map, and retrieved by engine id.
 *      </li>
 *
 *      <li>{@link Site} :
 *          Encapsulates a {@link SearchEngine} instance + the current enabled/disabled state.
 *          Implements {@link Parcelable} as it will be passed around as a member
 *          of a {@link SiteList}.
 *      </li>
 *
 *      <li>{@link SiteList} :
 *          A list of {@link Site}'s all of the same {@link SiteList.Type}.
 *          A search will use such a list to perform searches on the desired sites.
 *          Membership is hardcoded at design time (it depends on capabilities of the engine),
 *          but the order and enabling/disabling of the individual sites is user configurable.
 *          Implements {@link Parcelable} as it will be passed around.
 *      </li>
 *
 *      <li>{@link SearchCoordinator} and {@link SearchTask} will use all of the above to perform
 *          the searches.
 *      </li>
 * </ul>
 * <p>
 * There are 3 types of {@link SiteList}.
 * <ol>
 *      <li>{@link SiteList.Type#Data} : search for book data (and cover)</li>
 *      <li>{@link SiteList.Type#Covers} : search for book covers only</li>
 *      <li>{@link SiteList.Type#AltEditions} : search for alternative editions of a book.</li>
 * </ol>
 * Site/SearchEngine pairs are unique PER LIST:
 * <ul>
 *     <li>there is only one copy of a Site inside a single SiteList</li>
 *     <li>a SearchEngine is cached by a Site,
 *          but re-created each time the Site/SiteList is Parceled.</li>
 *     <li>two Site for the SAME website (i.e. same engine id),
 *          but in a different SiteList will have the same SearchEngine class,
 *          but a different instance of that SearchEngine </li>
 * </ul>
 * Example:
 * <ul>
 * <li>Site A1 with id=AAA, and type Data, will have a SearchEngine_A1</li>
 * <li>Site B1 with id=BBB, and type Data, will have a SearchEngine_B1</li>
 * <li>...</li>
 * <li>Site C1 with id=AAA, and type Covers, will have a SearchEngine_C1</li>
 * </ul>
 * <p>
 * <p>
 * To add a new site to search, follow these steps:
 * <ol>
 *     <li>Add an identifier (bit) in this class.</li>
 *     <li>Add this identifier to {@link EngineId} and {@link #DATA_RELIABILITY_ORDER}.</li>
 *     <li>Add a string resource with the name of the site engine in:
 *          "src/main/res/values/donottranslate.xml" (look for existing entries named 'site_*')
 *     </li>
 *     <li>Implement {@link SearchEngine} to create the new engine class
 *          extending {@link SearchEngineBase} or {@link JsoupSearchEngineBase}
 *          or a similar setup.<br>
 *          Configure the engine using the {@link SearchEngine.Configuration} annotation.
 *      </li>
 *
 *     <li>Add the {@link SearchEngine} class to the static block in this class
 *         by calling {@link Site#add}</li>
 *
 *      <li>Add a new {@link Site} instance to the one or more list(s)
 *          in {@link #createSiteList}</li>
 *
 *      <li>Optional: if the engine/site will store a external book id (or any other specific
 *          fields) in the local database, extra steps will need to be taken.
 *          TODO: document steps: search the code for "NEWTHINGS: adding a new search engine"
 *      </li>
 *
 *      <li>Optional: Add a preference fragment for the user to configure the engine.
 *          See the {@link IsfdbPreferencesFragment} for an example:
 *          a class, an xml file, and an entry in "src/main/res/xml/preferences.xml"."</li>
 * </ol>
 * <p>
 *
 * <strong>Note:</strong> NEVER change the identifiers (bit flag) of the sites,
 * as they are stored in user preferences.
 * Dev note: there is really only one place where the code relies on this being bit numbers...
 * URGENT: remove the need for engine id's to be BIT numbers
 */
public final class SearchSites {

    /** Site: all genres. */
    public static final int GOOGLE_BOOKS = 1;
    /** Site: all genres. */
    public static final int AMAZON = 1 << 1;
    /** Site: all genres. */
    public static final int LIBRARY_THING = 1 << 2;
    /** Site: all genres. */
    public static final int GOODREADS = 1 << 3;


    /** Site: Speculative Fiction only. e.g. Science-Fiction/Fantasy etc... */
    public static final int ISFDB = 1 << 4;
    /** Site: all genres. */
    public static final int OPEN_LIBRARY = 1 << 5;


    /** Site: Dutch language books & comics. */
    public static final int KB_NL = 1 << 6;
    /** Site: Dutch language (and to an extend French) comics. */
    public static final int STRIP_INFO_BE = 1 << 7;
    /** Site: Dutch language (and to an extend French) comics. */
    public static final int LAST_DODO = 1 << 8;

    // NEWTHINGS: adding a new search engine: add the engine id as a new bit

    /**
     * Simple CSV string with the search engine ids in reliability of data order.
     * Order is hardcoded based on experience. ENHANCE: make this user configurable
     * (Dev.note: it's a CSV because we store these kind of lists as strings in SharedPreferences)
     * NEWTHINGS: adding a new search engine: add the engine id
     */
    static final String DATA_RELIABILITY_ORDER =
            ISFDB
            + "," + STRIP_INFO_BE
            + "," + GOODREADS
            + "," + AMAZON
            + "," + GOOGLE_BOOKS
            + "," + LIBRARY_THING
            + "," + LAST_DODO
            + "," + KB_NL
            + "," + OPEN_LIBRARY;

    private SearchSites() {
    }

    /**
     * create all engine basic configurations; called during startup.
     */
    public static void createConfigs() {
        //dev note: we could scan for the annotation or for classes implementing the interface...
        // ... but that means traversing the class path. Not really worth the hassle.

        // NEWTHINGS: adding a new search engine: add the search engine class
        Site.add(AmazonSearchEngine.class);
        Site.add(GoodreadsSearchEngine.class);
        Site.add(IsfdbSearchEngine.class);
        Site.add(KbNlSearchEngine.class);
        Site.add(OpenLibrarySearchEngine.class);
        Site.add(StripInfoSearchEngine.class);

        if (BuildConfig.ENABLE_GOOGLE_BOOKS) {
            Site.add(GoogleBooksSearchEngine.class);
        }
        if (BuildConfig.ENABLE_LAST_DODO) {
            Site.add(LastDodoSearchEngine.class);
        }
        if (BuildConfig.ENABLE_LIBRARY_THING || BuildConfig.ENABLE_LIBRARY_THING_ALT_ED) {
            Site.add(LibraryThingSearchEngine.class);
        }
    }

    /**
     * Create the global search site list for the given type.
     *
     * @param systemLocale device Locale <em>(passed in to allow mocking)</em>
     * @param userLocale   user locale <em>(passed in to allow mocking)</em>
     * @param type         list to get
     *
     * @return list
     */
    static SiteList createSiteList(@NonNull final Locale systemLocale,
                                   @NonNull final Locale userLocale,
                                   @NonNull final SiteList.Type type) {

        // Certain sites are only enabled by default if the device or user set language
        // matches the site language.
        // Dutch websites:
        final boolean enableIfDutch = LanguageUtils.isLang(systemLocale, userLocale, "nld");

        //NEWTHINGS: add new search engine: add to the 3 lists as needed.

        // yes, we could loop over the SearchEngine's, and detect their interfaces.
        // but this gives more control.
        // For the BuildConfig.ENABLE_ usage: see app/build.gradle
        //
        // The order added here is the default order they will be used, but the user
        // can reorder the lists in preferences.
        final SiteList list = new SiteList(type);
        switch (type) {
            case Data: {
                list.add(AMAZON);

                list.add(GOODREADS);

                if (BuildConfig.ENABLE_GOOGLE_BOOKS) {
                    list.add(GOOGLE_BOOKS);
                }

                if (BuildConfig.ENABLE_LIBRARY_THING) {
                    list.add(LIBRARY_THING);
                }

                list.add(ISFDB);

                // Dutch.
                list.add(STRIP_INFO_BE, enableIfDutch);
                // Dutch.
                list.add(KB_NL, enableIfDutch);
                // Dutch.
                if (BuildConfig.ENABLE_LAST_DODO) {
                    list.add(LAST_DODO, enableIfDutch);
                }

                // Disabled by default as data from this site is not very complete.
                list.add(OPEN_LIBRARY, false);
                break;
            }
            case Covers: {
                // Only add sites here that implement {@link SearchEngine.CoverByIsbn}.

                list.add(AMAZON);

                list.add(GOODREADS);

                list.add(ISFDB);

                // Dutch.
                list.add(KB_NL, enableIfDutch);

                if (BuildConfig.ENABLE_LIBRARY_THING) {
                    // Disabled by default as this site lacks many covers.
                    list.add(LIBRARY_THING, false);
                }

                // Disabled by default as this site lacks many covers.
                list.add(OPEN_LIBRARY, false);
                break;
            }

            case AltEditions: {
                //Only add sites here that implement {@link SearchEngine.AlternativeEditions}.

                if (BuildConfig.ENABLE_LIBRARY_THING_ALT_ED) {
                    list.add(LIBRARY_THING);
                }

                list.add(ISFDB);
                break;
            }

            default:
                throw new IllegalArgumentException(ErrorMsg.UNEXPECTED_VALUE + type);
        }

        return list;
    }

    // NEWTHINGS: adding a new search engine: add the engine id
    @IntDef(flag = true, value = {
            GOOGLE_BOOKS, AMAZON, LIBRARY_THING, GOODREADS,
            ISFDB, OPEN_LIBRARY,
            KB_NL, STRIP_INFO_BE, LAST_DODO})
    @Retention(RetentionPolicy.SOURCE)
    public @interface EngineId {

    }
}
