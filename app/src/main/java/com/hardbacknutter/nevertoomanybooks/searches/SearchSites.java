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
import android.content.SharedPreferences;

import androidx.annotation.IntDef;
import androidx.annotation.NonNull;
import androidx.preference.PreferenceManager;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.util.ArrayList;
import java.util.Locale;

import com.hardbacknutter.nevertoomanybooks.App;
import com.hardbacknutter.nevertoomanybooks.debug.DebugReport;
import com.hardbacknutter.nevertoomanybooks.searches.amazon.AmazonManager;
import com.hardbacknutter.nevertoomanybooks.searches.goodreads.GoodreadsManager;
import com.hardbacknutter.nevertoomanybooks.searches.googlebooks.GoogleBooksManager;
import com.hardbacknutter.nevertoomanybooks.searches.isfdb.IsfdbManager;
import com.hardbacknutter.nevertoomanybooks.searches.kbnl.KbNlManager;
import com.hardbacknutter.nevertoomanybooks.searches.librarything.LibraryThingManager;
import com.hardbacknutter.nevertoomanybooks.searches.openlibrary.OpenLibraryManager;
import com.hardbacknutter.nevertoomanybooks.searches.stripinfo.StripInfoManager;
import com.hardbacknutter.nevertoomanybooks.utils.Csv;
import com.hardbacknutter.nevertoomanybooks.utils.UnexpectedValueException;

/**
 * Manages the setup of {@link SearchEngine} and {@link Site} instances.
 * <p>
 * <br>NEWTHINGS: adding a new search engine:
 * To make it available, follow these steps:
 * <ol>
 * <li>Add an identifier (bit) in this class + add it to {@link #SEARCH_FLAG_MASK}.</li>
 * <li>Add the identifier to {@link Id}</li>
 * <li>Add a name for it to {@link #getName}.<br>
 * This should be a hardcoded, single word, no spaces, and will be user visible.<br>
 * It will be used in SharedPreferences so should never be changed.
 * </li>
 * <li>Implement {@link SearchEngine} to create the new engine (class).</li>
 * <li>Add your new engine to {@link #getSearchEngine}</li>
 *
 * <li>Create+add a new {@link Site} instance to the lists in {@link #getSites}
 * and to {@link #DATA_RELIABILITY_ORDER}</li>
 *
 * <li>Add your new engine to {@link DebugReport#getSiteUrls}</li>
 * <li>Optional: add to res/xml/preferences.xml if the url should be editable.<br>
 * See the Amazon example in that xml file.</li>
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

    /** Site. */
    public static final int AMAZON = 1 << 1;
    /** Site. */
    public static final int GOOGLE_BOOKS = 1;
    /** Site. */
    public static final int LIBRARY_THING = 1 << 2;
    /** Site. */
    public static final int GOODREADS = 1 << 3;
    /** Site: Speculative Fiction only. i.e. Science-Fiction/Fantasy etc... */
    public static final int ISFDB = 1 << 4;
    /** Site. */
    public static final int OPEN_LIBRARY = 1 << 5;
    /** Site: Dutch language books & comics. */
    public static final int KB_NL = 1 << 6;
    /** Site: Dutch language (and to an extend French) comics. */
    public static final int STRIP_INFO_BE = 1 << 7;
    /** Mask including all search sources. */
    static final int SEARCH_FLAG_MASK = GOOGLE_BOOKS | AMAZON
                                        | LIBRARY_THING | GOODREADS | ISFDB | OPEN_LIBRARY
                                        | KB_NL | STRIP_INFO_BE;
    /** tag. */
    private static final String TAG = "SearchSites";
    /** Used to pass a list of sites around. */
    public static final String BKEY_SEARCH_SITES_BOOKS = TAG + ":books";
    /** Used to pass a list of sites around. */
    public static final String BKEY_SEARCH_SITES_COVERS = TAG + ":covers";
    private static final String PREFS_ORDER_PREFIX = "search.siteOrder.";
    private static final String PREFS_SEARCH_SITE_ORDER_DATA = PREFS_ORDER_PREFIX + "data";
    private static final String PREFS_SEARCH_SITE_ORDER_COVERS = PREFS_ORDER_PREFIX + "covers";
    private static final String SEP = ",";
    /** Dutch language site. */
    private static final String NLD = "nld";

    // use a map...
    //private static EnumMap<ListType,ArrayList<Site>> sLists = new EnumMap<>(ListType.class);
    private static final String DATA_RELIABILITY_ORDER;
    /** Cached copy of the users preferred search site order. */
    private static ArrayList<Site> sDataSearchOrder;
    /** Cached copy of the users preferred search site order specific for covers. */
    private static ArrayList<Site> sCoverSearchOrder;

    static {
        DATA_RELIABILITY_ORDER =
                "" + ISFDB
                + ',' + STRIP_INFO_BE
                + ',' + GOODREADS
                + (ENABLE_AMAZON_AWS ? ',' + AMAZON : "")
                + ',' + GOOGLE_BOOKS
                + ',' + LIBRARY_THING
                + ',' + KB_NL
                + ',' + OPEN_LIBRARY
        ;
    }

    private SearchSites() {
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

    /**
     * Return the name for the site. This should/is a hardcoded single word.
     * It is used for:
     * <ol>
     * <li>As the key into the actual preferences.</li>
     * <li>User-visible name in the app settings.</li>
     * <li>Internal task(thread) name which in some circumstances will be user-visible.</li>
     * </ol>
     * <p>
     * As it's used as a prefs key, it should never be changed.
     * <p>
     * <strong>Note:</strong> the name is also required in the actual {@link SearchEngine}
     * as a {@code StringRes} but the method here can not use that one without
     * instantiating which we don't want to do here.
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
     * @return instance
     */
    static SearchEngine getSearchEngine(@SearchSites.Id final int id) {
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

    /** Hardcoded to ISFDB for now, as that's the only site supporting this flag. */
    public static boolean usePublisher(@NonNull final Context context) {
        return PreferenceManager.getDefaultSharedPreferences(context)
                                .getBoolean(IsfdbManager.PREFS_USE_PUBLISHER, false);
    }

    /**
     * Bring up an Alert to the user if the searchSites include a site where registration
     * is beneficial/required.
     *
     * @param context     Current context
     * @param prefSuffix  Tip preference marker
     * @param searchSites sites
     *
     * @return {@code true} if an alert is currently shown
     */
    @SuppressWarnings("UnusedReturnValue")
    public static boolean promptToRegister(@NonNull final Context context,
                                           @NonNull final String prefSuffix,
                                           @SearchSites.Id final int searchSites) {
        boolean showingAlert = false;

        if ((searchSites & GOODREADS) != 0) {
            showingAlert = GoodreadsManager.promptToRegister(context, false, prefSuffix);
        }

        if ((searchSites & LIBRARY_THING) != 0) {
            showingAlert = showingAlert
                           || LibraryThingManager.promptToRegister(context, false, prefSuffix);
        }

        return showingAlert;
    }

    /**
     * Get the global search site list. Optionally initialise sites and order.
     *
     * @param context   Current context
     * @param listType  list to get
     * @param loadPrefs set to {@code true} to initialise individual sites and the order
     *
     * @return list
     */
    private static ArrayList<Site> getSites(@NonNull final Context context,
                                            @NonNull final ListType listType,
                                            final boolean loadPrefs) {
        ArrayList<Site> list = new ArrayList<>();

        switch (listType) {
            case Data:
                if (ENABLE_AMAZON_AWS) {
                    list.add(Site.newSite(context, AMAZON, loadPrefs, false));
                }
                list.add(Site.newSite(context, GOODREADS, loadPrefs, true));
                list.add(Site.newSite(context, GOOGLE_BOOKS, loadPrefs, true));
                list.add(Site.newSite(context, LIBRARY_THING, loadPrefs, true));
                list.add(Site.newSite(context, ISFDB, loadPrefs, true));
                // Dutch.
                list.add(Site.newSite(context, STRIP_INFO_BE, loadPrefs, isLang(NLD)));
                // Dutch.
                list.add(Site.newSite(context, KB_NL, loadPrefs, isLang(NLD)));
                // Disabled by default as data from this site is not very complete.
                list.add(Site.newSite(context, OPEN_LIBRARY, loadPrefs, false));
                break;

            case Covers:
                /*
                 * Default search order for dedicated cover lookup.
                 * These are only used by the {@link CoverBrowserViewModel}.
                 */
                list.add(Site.newCoverSite(context, ISFDB, loadPrefs, true));
                list.add(Site.newCoverSite(context, STRIP_INFO_BE, loadPrefs, isLang(NLD)));
                list.add(Site.newCoverSite(context, GOOGLE_BOOKS, loadPrefs, true));
                list.add(Site.newCoverSite(context, GOODREADS, loadPrefs, true));
                if (ENABLE_AMAZON_AWS) {
                    list.add(Site.newCoverSite(context, AMAZON, loadPrefs, false));
                }
                list.add(Site.newCoverSite(context, KB_NL, loadPrefs, isLang(NLD)));
                list.add(Site.newCoverSite(context, LIBRARY_THING, loadPrefs, false));
                list.add(Site.newCoverSite(context, OPEN_LIBRARY, loadPrefs, false));
                break;

            default:
                throw new IllegalStateException();
        }

        if (loadPrefs) {
            String order = PreferenceManager.getDefaultSharedPreferences(context)
                                            .getString(listType.getKey(), null);
            if (order != null) {
                return reorder(order, list);
            }
        }
        return list;
    }

    static ArrayList<Site> getReliabilityOrder() {
        // get the (cached) list with user preferences for the sites.
        // Now reorder for reliability.
        return reorder(DATA_RELIABILITY_ORDER, getSites(App.getAppContext(), ListType.Data));
    }

    /**
     * Get the global search site list in the preferred order.
     * Includes enabled <strong>AND</strong> disabled sites.
     *
     * @param context  Current context
     * @param listType type
     *
     * @return the list
     */
    @NonNull
    public static ArrayList<Site> getSites(@NonNull final Context context,
                                           @NonNull final ListType listType) {

        // already loaded ?
        switch (listType) {
            case Data:
                if (sDataSearchOrder != null) {
                    //noinspection unchecked
                    return (ArrayList<Site>) sDataSearchOrder.clone();
                }
                break;

            case Covers:
                if (sCoverSearchOrder != null) {
                    //noinspection unchecked
                    return (ArrayList<Site>) sCoverSearchOrder.clone();
                }
                break;

            default:
                throw new IllegalStateException();
        }

        // get the list according to user preferences.
        ArrayList<Site> sites = getSites(context, listType, true);

        // cache the list for reuse
        switch (listType) {
            case Data:
                sDataSearchOrder = sites;
                break;

            case Covers:
                sCoverSearchOrder = sites;
                break;

            default:
                throw new IllegalStateException();
        }


        //noinspection unchecked
        return (ArrayList<Site>) sites.clone();
    }

    /**
     * Reorder the given list based on user preferences.
     *
     * @param order CSV string with site ids
     * @param sites list to reorder
     *
     * @return ordered list
     */
    private static ArrayList<Site> reorder(@NonNull final String order,
                                           @NonNull final ArrayList<Site> sites) {
        ArrayList<Site> orderedList = new ArrayList<>();
        for (String idStr : order.split(SEP)) {
            int id = Integer.parseInt(idStr);
            for (Site site : sites) {
                if (site.id == id) {
                    orderedList.add(site);
                    break;
                }
            }
        }
        return orderedList;
    }

    /**
     * Get a bitmask with the enabled sites for the given list.
     *
     * @param list to filter
     *
     * @return bitmask containing only the enables sites
     */
    public static int getEnabledSites(@NonNull final ArrayList<Site> list) {
        int sites = 0;
        for (Site site : list) {
            if (site.isEnabled()) {
                // add the site
                sites = sites | site.id;
            }
        }
        return sites;
    }

    /**
     * Reset a list back to the hardcoded defaults.
     *
     * @param context  Current context
     * @param listType type
     */
    public static void resetList(@NonNull final Context context,
                                 @NonNull final ListType listType) {

        setList(context, listType, getSites(context, listType, false));
    }

    /**
     * Update the given list.
     *
     * @param context  Current context
     * @param listType type
     * @param newList  to use
     */
    public static void setList(@NonNull final Context context,
                               @NonNull final ListType listType,
                               @NonNull final ArrayList<Site> newList) {

        // replace the cached copy.
        switch (listType) {
            case Data:
                sDataSearchOrder = newList;
                break;

            case Covers:
                sCoverSearchOrder = newList;
                break;

            default:
                throw new IllegalStateException();
        }

        // Save the order of the given list (ids) and the individual site settings to preferences.
        SharedPreferences.Editor ed = PreferenceManager.getDefaultSharedPreferences(context).edit();
        String order = Csv.join(SEP, newList, site -> {
            // store individual site settings
            site.saveToPrefs(ed);
            // and collect the id for the order string
            return String.valueOf(site.id);
        });
        ed.putString(listType.getKey(), order);
        ed.apply();
    }

    public enum ListType {
        Data, Covers;

        String getKey() {
            switch (this) {
                case Covers:
                    return PREFS_SEARCH_SITE_ORDER_COVERS;

                case Data:
                default:
                    return PREFS_SEARCH_SITE_ORDER_DATA;
            }
        }
    }

    @IntDef(flag = true, value = {
            GOOGLE_BOOKS, AMAZON, LIBRARY_THING, GOODREADS, ISFDB, OPEN_LIBRARY,
            KB_NL, STRIP_INFO_BE})
    @Retention(RetentionPolicy.SOURCE)
    public @interface Id {

    }
}
