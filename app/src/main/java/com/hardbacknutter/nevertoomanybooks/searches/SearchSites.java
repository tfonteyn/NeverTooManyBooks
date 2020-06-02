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

import android.content.Context;

import androidx.annotation.IdRes;
import androidx.annotation.IntDef;
import androidx.annotation.NonNull;
import androidx.preference.PreferenceManager;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.util.Locale;

import com.hardbacknutter.nevertoomanybooks.BuildConfig;
import com.hardbacknutter.nevertoomanybooks.R;
import com.hardbacknutter.nevertoomanybooks.database.DBDefinitions;
import com.hardbacknutter.nevertoomanybooks.debug.ErrorMsg;
import com.hardbacknutter.nevertoomanybooks.searches.amazon.AmazonSearchEngine;
import com.hardbacknutter.nevertoomanybooks.searches.goodreads.GoodreadsSearchEngine;
import com.hardbacknutter.nevertoomanybooks.searches.googlebooks.GoogleBooksSearchEngine;
import com.hardbacknutter.nevertoomanybooks.searches.isfdb.IsfdbSearchEngine;
import com.hardbacknutter.nevertoomanybooks.searches.kbnl.KbNlSearchEngine;
import com.hardbacknutter.nevertoomanybooks.searches.librarything.LibraryThingSearchEngine;
import com.hardbacknutter.nevertoomanybooks.searches.openlibrary.OpenLibrarySearchEngine;
import com.hardbacknutter.nevertoomanybooks.searches.stripinfo.StripInfoSearchEngine;
import com.hardbacknutter.nevertoomanybooks.settings.sites.IsfdbPreferencesFragment;

/**
 * Manages the setup of {@link SearchEngine} and {@link Site} instances.
 * <p>
 * <br>NEWTHINGS: adding a new search engine:
 * To make it available, follow these steps:
 * <ol>
 *      <li>Implement {@link SearchEngine} to create the new engine (class).<br>
 *          It needs a no-arguments constructor.</li>
 *      <li>Add an identifier (bit) in this class and add it to {@link #SEARCH_FLAG_MASK}
 *          and {@link Id}</li>
 *      <li>Add a name for it to {@link #getName}.<br>
 *          This should be a hardcoded, single word, no spaces, and will be user visible.<br>
 *          It will be used in SharedPreferences so should never be changed.</li>
 *      <li>Add the new SearchEngine to {@link #getSearchEngine}</li>
 *      <li>Add a new {@link Site} instance to the list(s) in {@link #createSiteList}
 *          and to {@link #DATA_RELIABILITY_ORDER}</li>
 *      <li>Add the new SearchEngine to {@link #getSiteUrls}</li>
 *      <li>Optional: add a preference fragment if needed.<br>
 *          See the {@link IsfdbPreferencesFragment} example: a class, an xml file, and an entry
 *          in res/xml/preferences.xml.</li>
 * </ol>
 *
 * <strong>Note:</strong> NEVER change the identifiers (bit flag) of the sites,
 * as they are stored in user preferences.
 */
public final class SearchSites {

    /** Site. */
    @SuppressWarnings("WeakerAccess")
    public static final int GOOGLE_BOOKS = 1;
    /** Site. */
    public static final int AMAZON = 1 << 1;
    /** Site. */
    @SuppressWarnings("WeakerAccess")
    public static final int LIBRARY_THING = 1 << 2;
    /** Site. */
    public static final int GOODREADS = 1 << 3;
    /** Site: Speculative Fiction only. i.e. Science-Fiction/Fantasy etc... */
    public static final int ISFDB = 1 << 4;
    /** Site. */
    @SuppressWarnings("WeakerAccess")
    public static final int OPEN_LIBRARY = 1 << 5;
    /** Site: Dutch language books & comics. */
    @SuppressWarnings("WeakerAccess")
    public static final int KB_NL = 1 << 6;
    /** Site: Dutch language (and to an extend French) comics. */
    @SuppressWarnings("WeakerAccess")
    public static final int STRIP_INFO_BE = 1 << 7;
    /** Mask including all search sources. */
    @SuppressWarnings("WeakerAccess")
    public static final int SEARCH_FLAG_MASK = GOOGLE_BOOKS | AMAZON | LIBRARY_THING | GOODREADS
                                               | ISFDB | OPEN_LIBRARY
                                               | KB_NL | STRIP_INFO_BE;
    static final String DATA_RELIABILITY_ORDER;
    /** Dutch language site. */
    private static final String NLD = "nld";

    static {
        // order is hardcoded based on experience.
        DATA_RELIABILITY_ORDER =
                ISFDB
                + "," + STRIP_INFO_BE
                + "," + GOODREADS
                + "," + AMAZON
                + "," + GOOGLE_BOOKS
                + "," + LIBRARY_THING
                + "," + KB_NL
                + "," + OPEN_LIBRARY;
    }

    private SearchSites() {
    }

    /**
     * Return the name for the site. This should/is a hardcoded single word.
     * It is used for:
     * <ol>
     *      <li>As the key into the actual preferences.</li>
     *      <li>User-visible name in the app settings.</li>
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
    public static String getName(@Id final int id) {
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
                throw new IllegalArgumentException(ErrorMsg.UNEXPECTED_VALUE + id);
        }
    }

    /**
     * Get a new SearchEngine class instance for the given ID.
     *
     * <strong>Should only be called by {@link Site#getSearchEngine()}</strong>.
     *
     * @param id site id
     *
     * @return instance
     */
    static SearchEngine getSearchEngine(@Id final int id) {
        switch (id) {
            case GOOGLE_BOOKS:
                return new GoogleBooksSearchEngine();
            case AMAZON:
                return new AmazonSearchEngine();
            case GOODREADS:
                return new GoodreadsSearchEngine();
            case ISFDB:
                return new IsfdbSearchEngine();
            case LIBRARY_THING:
                return new LibraryThingSearchEngine();
            case OPEN_LIBRARY:
                return new OpenLibrarySearchEngine();
            case KB_NL:
                return new KbNlSearchEngine();
            case STRIP_INFO_BE:
                return new StripInfoSearchEngine();
            default:
                throw new IllegalArgumentException(ErrorMsg.UNEXPECTED_VALUE + id);
        }
    }

    /**
     * Hardcoded to ISFDB for now, as that's the only site supporting this flag.
     *
     * @param context Current context
     *
     * @return flag
     */
    public static boolean usePublisher(@NonNull final Context context) {
        return PreferenceManager.getDefaultSharedPreferences(context)
                                .getBoolean(IsfdbSearchEngine.PREFS_USE_PUBLISHER, false);
    }

    /**
     * Get the global search site list. Optionally initialise sites and order.
     *
     * @param systemLocale device Locale
     * @param userLocale   user locale
     * @param type         list to get
     *
     * @return list
     */
    static SiteList createSiteList(@NonNull final Locale systemLocale,
                                   @NonNull final Locale userLocale,
                                   @NonNull final SiteList.Type type) {

        SiteList list = new SiteList(type);

        // yes, we could loop over the sites, and detect their interfaces.
        // but this gives more control (e.g. language and other defaults).
        switch (type) {
            case Data: {
                list.add(Site.createSite(AMAZON, type, false));

                list.add(Site.createSite(GOODREADS, type, true));

                if (BuildConfig.ENABLE_GOOGLE_BOOKS) {
                    list.add(Site.createSite(GOOGLE_BOOKS, type, true));
                }

                if (BuildConfig.ENABLE_LIBRARY_THING) {
                    list.add(Site.createSite(LIBRARY_THING, type, true));
                }

                list.add(Site.createSite(ISFDB, type, true));

                // Dutch.
                list.add(Site.createSite(STRIP_INFO_BE, type,
                                         isLang(systemLocale, userLocale, NLD)));
                // Dutch.
                list.add(Site.createSite(KB_NL, type,
                                         isLang(systemLocale, userLocale, NLD)));

                // Disabled by default as data from this site is not very complete.
                list.add(Site.createSite(OPEN_LIBRARY, type, false));
                break;
            }
            case Covers: {
                // Sites that implement {@link SearchEngine.CoverByIsbn}.
                // These are only used by the {@link CoverBrowserViewModel}.

                list.add(Site.createSite(ISFDB, type, true));

                //list.add(Site.createSite(STRIP_INFO_BE, type,
                //                         isLang(systemLocale, userLocale, NLD)));

//                if (BuildConfig.ENABLE_GOOGLE_BOOKS) {
                //list.add(Site.createSite(GOOGLE_BOOKS, type, true));
//                }

                list.add(Site.createSite(GOODREADS, type, true));

                //list.add(Site.createSite(AMAZON, type, false));

                list.add(Site.createSite(KB_NL, type, isLang(systemLocale, userLocale, NLD)));

                if (BuildConfig.ENABLE_LIBRARY_THING) {
                    // Disabled by default as this site lacks many covers.
                    list.add(Site.createSite(LIBRARY_THING, type, false));
                }
                // Disabled by default as this site lacks many covers.
                list.add(Site.createSite(OPEN_LIBRARY, type, false));
                break;
            }

            case AltEditions: {
                // Sites that implement {@link SearchEngine.AlternativeEditions}.

                if (BuildConfig.ENABLE_LIBRARY_THING_ALT_ED) {
                    list.add(Site.createSite(LIBRARY_THING, type, true));
                }
                list.add(Site.createSite(ISFDB, type, true));
                break;
            }

            default:
                throw new IllegalStateException();
        }

        return list;
    }

    /**
     * Lookup a site id by the given resource id.
     *
     * @param resId to find
     *
     * @return site id.
     */
    @Id
    public static int getSiteIdFromResId(@IdRes final int resId) {
        //NEWTHINGS: add new site specific ID: not all sites have/need a resource/menu id.
        switch (resId) {
            case R.id.MENU_VIEW_BOOK_AT_AMAZON:
            case R.id.site_amazon:
                return AMAZON;

            case R.id.MENU_VIEW_BOOK_AT_GOODREADS:
            case R.id.site_goodreads:
                return GOODREADS;

            case R.id.MENU_VIEW_BOOK_AT_ISFDB:
            case R.id.site_isfdb:
                return ISFDB;

            case R.id.MENU_VIEW_BOOK_AT_LIBRARY_THING:
            case R.id.site_library_thing:
                return LIBRARY_THING;

            case R.id.MENU_VIEW_BOOK_AT_OPEN_LIBRARY:
            case R.id.site_open_library:
                return OPEN_LIBRARY;

            case R.id.MENU_VIEW_BOOK_AT_STRIP_INFO_BE:
            case R.id.site_strip_info_be:
                return STRIP_INFO_BE;

            default:
                throw new IllegalArgumentException(ErrorMsg.UNEXPECTED_VALUE + resId);
        }
    }

    /**
     * Lookup a site id by the given {@link DBDefinitions} key.
     *
     * @param key to find
     *
     * @return site id.
     */
    @Id
    public static int getSiteIdFromDBDefinitions(@NonNull final String key) {
        //NEWTHINGS: add new site specific ID: all native keys should be listed
        switch (key) {
            case DBDefinitions.KEY_EID_GOODREADS_BOOK:
                return GOODREADS;

            case DBDefinitions.KEY_EID_ISFDB:
                return ISFDB;

            case DBDefinitions.KEY_EID_LIBRARY_THING:
                return LIBRARY_THING;

            case DBDefinitions.KEY_EID_OPEN_LIBRARY:
                return OPEN_LIBRARY;

            case DBDefinitions.KEY_EID_STRIP_INFO_BE:
                return STRIP_INFO_BE;

            default:
                throw new IllegalArgumentException(ErrorMsg.UNEXPECTED_VALUE + key);
        }
    }

    /**
     * Returns a text block containing potentially changed urls.
     *
     * @param context Current context
     *
     * @return url list
     */
    public static String getSiteUrls(@NonNull final Context context) {
        return AmazonSearchEngine.getBaseURL(context) + '\n'
               + IsfdbSearchEngine.getBaseURL(context) + '\n'
               + KbNlSearchEngine.getBaseURL(context) + '\n';
        //NEWTHINGS: add new search engine if it supports a user changeable url.
    }

    /**
     * Check if the device or user locale matches the given language.
     * <p>
     * Non-english sites are by default only enabled if either the device or
     * this app is running in the specified language.
     * The user can still enable/disable them at will of course.
     *
     * @param systemLocale device Locale
     * @param userLocale   user Locale
     * @param iso          language code to check
     *
     * @return {@code true} if sites should be enabled by default.
     */
    private static boolean isLang(@NonNull final Locale systemLocale,
                                  @NonNull final Locale userLocale,
                                  @SuppressWarnings("SameParameterValue")
                                  @NonNull final String iso) {
        return iso.equals(systemLocale.getISO3Language())
               || iso.equals(userLocale.getISO3Language());
    }


    @IntDef(flag = true, value = {
            GOOGLE_BOOKS, AMAZON, LIBRARY_THING, GOODREADS, ISFDB, OPEN_LIBRARY,
            KB_NL, STRIP_INFO_BE})
    @Retention(RetentionPolicy.SOURCE)
    public @interface Id {

    }
}
