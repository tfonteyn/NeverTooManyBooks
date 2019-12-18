/*
 * @Copyright 2019 HardBackNutter
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

import android.content.Context;
import android.view.View;

import androidx.annotation.IdRes;
import androidx.annotation.IntDef;
import androidx.annotation.NonNull;
import androidx.preference.PreferenceManager;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.util.Locale;

import com.hardbacknutter.nevertoomanybooks.App;
import com.hardbacknutter.nevertoomanybooks.R;
import com.hardbacknutter.nevertoomanybooks.searches.amazon.AmazonManager;
import com.hardbacknutter.nevertoomanybooks.searches.goodreads.GoodreadsManager;
import com.hardbacknutter.nevertoomanybooks.searches.googlebooks.GoogleBooksManager;
import com.hardbacknutter.nevertoomanybooks.searches.isfdb.IsfdbManager;
import com.hardbacknutter.nevertoomanybooks.searches.kbnl.KbNlManager;
import com.hardbacknutter.nevertoomanybooks.searches.librarything.LibraryThingManager;
import com.hardbacknutter.nevertoomanybooks.searches.openlibrary.OpenLibraryManager;
import com.hardbacknutter.nevertoomanybooks.searches.stripinfo.StripInfoManager;
import com.hardbacknutter.nevertoomanybooks.settings.sites.IsfdbPreferencesFragment;
import com.hardbacknutter.nevertoomanybooks.utils.UnexpectedValueException;

/**
 * Manages the setup of {@link SearchEngine} and {@link Site} instances.
 * <p>
 * <br>NEWTHINGS: adding a new search engine:
 * To make it available, follow these steps:
 * <ol>
 * <li>Implement {@link SearchEngine} to create the new engine (class).</li>
 * <li>Add an identifier (bit) in this class and
 * add it to {@link #SEARCH_FLAG_MASK} and {@link Id}</li>
 * <li>Add a name for it to {@link #getName}.<br>
 * This should be a hardcoded, single word, no spaces, and will be user visible.<br>
 * It will be used in SharedPreferences so should never be changed.</li>
 * <li>Add the new SearchEngine to {@link #getSearchEngine}</li>
 * <li>Add a new {@link Site} instance to the list(s) in {@link #createSiteList}
 * and to {@link #DATA_RELIABILITY_ORDER}</li>
 *
 * <li>Add the new SearchEngine to {@link #getSiteUrls}</li>
 * <li>Optional: add a preference fragment if needed.<br>
 * See the {@link IsfdbPreferencesFragment} example: a class, an xml file, and an entry
 * in res/xml/preferences.xml.</li>
 * </ol>
 *
 * <strong>Note:</strong> NEVER change the identifiers (bit flag) of the sites,
 * as they are stored in user preferences.
 */
public final class SearchSites {

    /**
     * The Amazon handler uses the BookCatalogue proxy.
     * DO NOT USE UNTIL WE REMOVE THAT DEPENDENCY.
     */
    public static final boolean ENABLE_AMAZON_AWS = false;


    static final String DATA_RELIABILITY_ORDER;

    /** Site. */
    private static final int GOOGLE_BOOKS = 1;
    /** Site. */
    private static final int AMAZON = 1 << 1;
    /** Site. */
    private static final int LIBRARY_THING = 1 << 2;
    /** Site. */
    private static final int GOODREADS = 1 << 3;
    /** Site: Speculative Fiction only. i.e. Science-Fiction/Fantasy etc... */
    private static final int ISFDB = 1 << 4;
    /** Site. */
    private static final int OPEN_LIBRARY = 1 << 5;
    /** Site: Dutch language books & comics. */
    private static final int KB_NL = 1 << 6;
    /** Site: Dutch language (and to an extend French) comics. */
    private static final int STRIP_INFO_BE = 1 << 7;


    /** Mask including all search sources. */
    public static final int SEARCH_FLAG_MASK = GOOGLE_BOOKS | AMAZON | LIBRARY_THING | GOODREADS
                                               | ISFDB | OPEN_LIBRARY
                                               | KB_NL | STRIP_INFO_BE;
    /** Dutch language site. */
    private static final String NLD = "nld";

    static {
        // order is hardcoded based on experience.
        DATA_RELIABILITY_ORDER =
                "" + ISFDB
                + ',' + STRIP_INFO_BE
                + ',' + GOODREADS
                + (ENABLE_AMAZON_AWS ? ',' + AMAZON : "")
                + ',' + GOOGLE_BOOKS
                + ',' + LIBRARY_THING
                + ',' + KB_NL
                + ',' + OPEN_LIBRARY;
    }

    private SearchSites() {
    }

    /**
     * Return the name for the site. This should/is a hardcoded single word.
     * It is used for:
     * <ol>
     * <li>As the key into the actual preferences.</li>
     * <li>User-visible name in the app settings.</li>
     * </ol>
     * <p>
     * As it's used as a prefs key, it should never be changed.
     * <p>
     * <strong>Note:</strong> the name is also required in the actual {@link SearchEngine}
     * as a {@code StringRes}. The latter can be localized and is purely for display purposes.
     *
     * @param id for the site
     *
     * @return the name
     */
    static String getName(@Id final int id) {
        switch (id) {
            case GOOGLE_BOOKS:
                return "GoogleBooks";
            case AMAZON:
                return "Amazon";
            case GOODREADS:
                return "Goodreads";
            case ISFDB:
                return "ISFDB";
            case LIBRARY_THING:
                return "LibraryThing";
            case OPEN_LIBRARY:
                return "OpenLibrary";
            case KB_NL:
                return "KBNL";
            case STRIP_INFO_BE:
                return "StripInfo";
            default:
                throw new UnexpectedValueException(id);
        }
    }

    /**
     * Get a new SearchEngine class instance for the given ID.
     *
     * @param id site id
     *
     * @return instance
     */
    static SearchEngine getSearchEngine(@Id final int id) {
        switch (id) {
            case GOOGLE_BOOKS:
                return new GoogleBooksManager();
            case AMAZON:
                return new AmazonManager();
            case GOODREADS:
                return new GoodreadsManager();
            case ISFDB:
                return new IsfdbManager();
            case LIBRARY_THING:
                return new LibraryThingManager();
            case OPEN_LIBRARY:
                return new OpenLibraryManager();
            case KB_NL:
                return new KbNlManager();
            case STRIP_INFO_BE:
                return new StripInfoManager();
            default:
                throw new UnexpectedValueException(id);
        }
    }

    /**
     * Hardcoded to ISFDB for now, as that's the only site supporting this flag.
     *
     * @param context Current context
     */
    public static boolean usePublisher(@NonNull final Context context) {
        return PreferenceManager.getDefaultSharedPreferences(context)
                                .getBoolean(IsfdbManager.PREFS_USE_PUBLISHER, false);
    }

    /**
     * Get the global search site list. Optionally initialise sites and order.
     *
     * @param context   Current context
     * @param type      list to get
     * @param loadPrefs set to {@code true} to initialise individual sites and the order
     *
     * @return list
     */
    static SiteList createSiteList(@NonNull final Context context,
                                   @NonNull final SiteList.Type type,
                                   final boolean loadPrefs) {
        SiteList list = new SiteList(type);

        switch (type) {
            case Data: {
                if (ENABLE_AMAZON_AWS) {
                    list.add(Site.createSite(AMAZON, type, false));
                }
                list.add(Site.createSite(GOODREADS, type, true));
                list.add(Site.createSite(GOOGLE_BOOKS, type, true));
                list.add(Site.createSite(LIBRARY_THING, type, true));
                list.add(Site.createSite(ISFDB, type, true));
                // Dutch.
                list.add(Site.createSite(STRIP_INFO_BE, type, isLang(NLD)));
                // Dutch.
                list.add(Site.createSite(KB_NL, type, isLang(NLD)));
                // Disabled by default as data from this site is not very complete.
                list.add(Site.createSite(OPEN_LIBRARY, type, false));
                break;
            }
            case Covers: {
                /*
                 * Default search order for dedicated cover lookup.
                 * These are only used by the {@link CoverBrowserViewModel}.
                 */
                list.add(Site.createSite(ISFDB, type, true));
                list.add(Site.createSite(STRIP_INFO_BE, type, isLang(NLD)));
                list.add(Site.createSite(GOOGLE_BOOKS, type, true));
                list.add(Site.createSite(GOODREADS, type, true));
                if (ENABLE_AMAZON_AWS) {
                    list.add(Site.createSite(AMAZON, type, false));
                }
                list.add(Site.createSite(KB_NL, type, isLang(NLD)));
                list.add(Site.createSite(LIBRARY_THING, type, false));
                list.add(Site.createSite(OPEN_LIBRARY, type, false));
                break;
            }

            case AltEditions: {
                list.add(Site.createSite(LIBRARY_THING, type, true));
                list.add(Site.createSite(ISFDB, type, true));
                break;
            }

            default:
                throw new IllegalStateException();
        }

        if (loadPrefs) {
            list.loadPrefs(context);
        }
        return list;
    }

    /**
     * Lookup a site id by the given resource id.
     *
     * @param resId to find
     *
     * @return site id.
     *
     * @throws UnexpectedValueException if a resId is unknown
     */
    @Id
    public static int getSiteIdFromResId(@IdRes final int resId) {
        //NEWTHINGS: add new site specific ID:
        // not all sites actually have/need a resource id.
        switch (resId) {
            case R.id.site_amazon:
                return AMAZON;

            case R.id.site_goodreads:
                return GOODREADS;

            case R.id.site_isfdb:
                return ISFDB;

            case R.id.site_library_thing:
                return LIBRARY_THING;

            case R.id.site_open_library:
                return OPEN_LIBRARY;

            case R.id.site_strip_info_be:
                return STRIP_INFO_BE;

            default:
                throw new UnexpectedValueException(resId);
        }
    }

    /**
     * Lookup a resource id by the given site id.
     *
     * @param siteId to find
     *
     * @return resource id, or View.NO_ID if the site has none.
     */
    @IdRes
    public static int getResIdFromSiteId(@Id final int siteId) {
        //NEWTHINGS: add new site specific ID:
        switch (siteId) {
            case AMAZON:
                return R.id.site_amazon;

            case GOODREADS:
                return R.id.site_goodreads;

            case ISFDB:
                return R.id.site_isfdb;

            case LIBRARY_THING:
                return R.id.site_library_thing;

            case OPEN_LIBRARY:
                return R.id.site_open_library;

            case STRIP_INFO_BE:
                return R.id.site_strip_info_be;

            case GOOGLE_BOOKS:
            case KB_NL:
            default:
                return View.NO_ID;
        }
    }

    /**
     * DEBUG method. Returns a text block containing potentially changed urls.
     *
     * @param context Current context
     *
     * @return url list
     */
    public static String getSiteUrls(@NonNull final Context context) {
        return AmazonManager.getBaseURL(context) + '\n'
//               + GoodreadsManager.getBaseURL(context) + '\n'
//               + GoogleBooksManager.getBaseURL(context) + '\n'
               + IsfdbManager.getBaseURL(context) + '\n'
               + KbNlManager.getBaseURL(context) + '\n'
//               + LibraryThingManager.getBaseURL(context) + '\n'
//               + OpenLibraryManager.getBaseURL(context) + '\n'
//               + StripInfoManager.getBaseURL(context) + '\n'
                ;
        //NEWTHINGS: add new search engine if it supports a changeable url.
    }

    /**
     * Check if the device or user locale matches the given language.
     * <p>
     * Non-english sites are by default only enabled if either the device or
     * this app is running in the specified language.
     * The user can still enable/disable them at will of course.
     *
     * @param iso language code to check
     *
     * @return {@code true} if sites should be enabled by default.
     */
    private static boolean isLang(@SuppressWarnings("SameParameterValue")
                                  @NonNull final String iso) {
        return iso.equals(App.getSystemLocale().getISO3Language())
               || iso.equals(Locale.getDefault().getISO3Language());
    }

    @IntDef(flag = true, value = {
            GOOGLE_BOOKS, AMAZON, LIBRARY_THING, GOODREADS, ISFDB, OPEN_LIBRARY,
            KB_NL, STRIP_INFO_BE})
    @Retention(RetentionPolicy.SOURCE)
    public @interface Id {

    }
}
