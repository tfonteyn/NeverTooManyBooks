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
import java.util.List;
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
 * <li>Implement {@link SearchEngine} to create the new engine.</li>
 * <li>Add an identifier (bit) in this class + add it to {@link #SEARCH_FLAG_MASK}.</li>
 * <li>Add the identifier to {@link interface Id}</li>
 * <li>Add a name for it to {@link #getName}.<br>
 * This should be a hardcoded, single word, no spaces, and will be user visible.<br>
 * It will be used in SharedPreferences so should never be changed.
 * </li>
 * <li>Add your new engine to {@link #getSearchEngine};</li>
 * <li>Create+add a new instance to {@link #SEARCH_ORDER}<br>
 * and {@link #COVER_SEARCH_ORDER}</li>
 * <li>Add your new engine to {@link DebugReport#getSiteUrls}</li>
 * <li>Optional: add to res/xml/preferences.xml if the url should be editable.<br>
 * See the Amazon example in that xml file.</li>
 * </ol>
 *
 * <strong>Note:</strong> NEVER change the identifiers (bit flag) of the sites,
 * as they are stored in user preferences.
 */
public final class SearchSites {

    /** Site. */
    public static final int AMAZON = 1 << 1;
    /**
     * The Amazon handler uses the BookCatalogue proxy.
     * DO NOT USE UNTIL WE REMOVE THAT DEPENDENCY.
     */
    public static final boolean ENABLE_AMAZON_AWS = false;

    /** tag. */
    private static final String TAG = "SearchSites";
    public static final String BKEY_SEARCH_SITES = TAG + ":searchSitesList";
    /** Site. */
    private static final int GOOGLE_BOOKS = 1;
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
    static final int SEARCH_FLAG_MASK = GOOGLE_BOOKS | AMAZON
                                        | LIBRARY_THING | GOODREADS | ISFDB | OPEN_LIBRARY
                                        | KB_NL | STRIP_INFO_BE;

    /** the default search site order for standard data/covers. */
    private static final ArrayList<Site> SEARCH_ORDER = new ArrayList<>();
    /** the default search site order for _dedicated_ cover searches. */
    private static final ArrayList<Site> COVER_SEARCH_ORDER = new ArrayList<>();
    /** ENHANCE: reliability order is not user configurable for now, but plumbing installed. */
    private static final ArrayList<Site> DATA_RELIABILITY_ORDER = new ArrayList<>();

    private static final String PREFS_PREFIX = "search.siteOrder.";
    private static final String PREFS_SEARCH_SITE_ORDER_DATA = PREFS_PREFIX + "data";
    private static final String PREFS_SEARCH_SITE_RELIABILITY = PREFS_PREFIX + "reliability";
    private static final String PREFS_SEARCH_SITE_ORDER_COVERS = PREFS_PREFIX + "covers";

    /** the users preferred search site order. */
    private static final ArrayList<Site> sPreferredSearchOrder = new ArrayList<>();
    /** the users preferred search site order specific for covers. */
    private static final ArrayList<Site> sPreferredCoverSearchOrder = new ArrayList<>();
    private static final String SEP = ",";

    static {
        Site sAmazon = Site.newSite(AMAZON, false);
        Site sGoodreads = Site.newSite(GOODREADS, true);
        Site sGoogle = Site.newSite(GOOGLE_BOOKS, true);
        Site sLibraryThing = Site.newSite(LIBRARY_THING, true);
        Site sIsfdb = Site.newSite(ISFDB, true);
        // Dutch.
        Site sStripinfoBe = Site.newSite(STRIP_INFO_BE, isDutch());
        // Dutch.
        Site sKbNl = Site.newSite(KB_NL, isDutch());
        // Disabled by default as data from this site is not very complete.
        Site sOpenLibrary = Site.newSite(OPEN_LIBRARY, false);

        if (ENABLE_AMAZON_AWS) {
            SEARCH_ORDER.add(sAmazon);
        }
        SEARCH_ORDER.add(sGoodreads);
        SEARCH_ORDER.add(sGoogle);
        SEARCH_ORDER.add(sLibraryThing);
        SEARCH_ORDER.add(sIsfdb);
        SEARCH_ORDER.add(sStripinfoBe);
        SEARCH_ORDER.add(sKbNl);
        SEARCH_ORDER.add(sOpenLibrary);

        DATA_RELIABILITY_ORDER.add(sIsfdb);
        DATA_RELIABILITY_ORDER.add(sStripinfoBe);
        DATA_RELIABILITY_ORDER.add(sGoodreads);
        if (ENABLE_AMAZON_AWS) {
            DATA_RELIABILITY_ORDER.add(sAmazon);
        }
        DATA_RELIABILITY_ORDER.add(sGoogle);
        DATA_RELIABILITY_ORDER.add(sLibraryThing);
        DATA_RELIABILITY_ORDER.add(sKbNl);
        DATA_RELIABILITY_ORDER.add(sOpenLibrary);

        /*
         * Default search order for dedicated cover lookup.
         * These are only used by the {@link CoverBrowserViewModel}.
         */
        Site csIsfdb = Site.newCoverSite(ISFDB, true);
        Site csStripinfoBe = Site.newCoverSite(STRIP_INFO_BE, isDutch());
        Site csGoogle = Site.newCoverSite(GOOGLE_BOOKS, true);
        Site csGoodreads = Site.newCoverSite(GOODREADS, true);
        Site csAmazon = Site.newCoverSite(AMAZON, false);
        Site csKbNl = Site.newCoverSite(KB_NL, isDutch());
        Site csLibraryThing = Site.newCoverSite(LIBRARY_THING, false);
        Site csOpenLibrary = Site.newCoverSite(OPEN_LIBRARY, false);

        // Create the user configurable list.
        COVER_SEARCH_ORDER.add(csIsfdb);
        COVER_SEARCH_ORDER.add(csStripinfoBe);
        COVER_SEARCH_ORDER.add(csGoogle);
        COVER_SEARCH_ORDER.add(csGoodreads);
        if (ENABLE_AMAZON_AWS) {
            COVER_SEARCH_ORDER.add(csAmazon);
        }
        COVER_SEARCH_ORDER.add(csKbNl);
        COVER_SEARCH_ORDER.add(csLibraryThing);
        COVER_SEARCH_ORDER.add(csOpenLibrary);
    }

    private SearchSites() {
    }

    /**
     * Dutch sites are by *default* only enabled if either the device or this app is running
     * in Dutch. The user can enable/disable them at will of course.
     *
     * @return {@code true} if Dutch sites should be enabled by default.
     */
    private static boolean isDutch() {
        return "nld".equals(App.getSystemLocale().getISO3Language())
               || "nld".equals(Locale.getDefault().getISO3Language());
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

    public static boolean usePublisher(@NonNull final Context context) {
        return PreferenceManager.getDefaultSharedPreferences(context)
                                .getBoolean(IsfdbManager.PREFS_USE_PUBLISHER, false);
    }

    /**
     * Reset search order values back to the hardcoded defaults.
     *
     * @param context Current context
     */
    public static void resetSearchOrder(@NonNull final Context context) {
        setOrder(context, SEARCH_ORDER);
    }

    /**
     * Reset cover search order values back to the hardcoded defaults.
     *
     * @param context Current context
     */
    public static void resetCoverSearchOrder(@NonNull final Context context) {
        setCoverOrder(context, COVER_SEARCH_ORDER);
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
    public static boolean alertRegistrationBeneficial(@NonNull final Context context,
                                                      @NonNull final String prefSuffix,
                                                      @SearchSites.Id final int searchSites) {
        boolean showingAlert = false;

        if ((searchSites & GOODREADS) != 0) {
            showingAlert = GoodreadsManager.alertRegistrationBeneficial(context,
                                                                        false, prefSuffix);
        }

        if ((searchSites & LIBRARY_THING) != 0) {
            showingAlert = showingAlert
                           || LibraryThingManager.alertRegistrationBeneficial(context,
                                                                              false, prefSuffix);
        }

        return showingAlert;
    }


    /**
     * Get the global search site list in the preferred order.
     * Includes enabled <strong>AND</strong> disabled sites, in the preferred order.
     *
     * @return the list
     */
    @NonNull
    public static ArrayList<Site> getSites() {
        if (sPreferredSearchOrder.isEmpty()) {
            sPreferredSearchOrder.addAll(readOrder(ListType.Data));
        }
        //noinspection unchecked
        return (ArrayList<Site>) sPreferredSearchOrder.clone();
    }

    /**
     * Get the global cover-search site list in the preferred order.
     * Includes enabled <strong>AND</strong> disabled sites, in the preferred order.
     *
     * @return the list
     */
    @NonNull
    public static ArrayList<Site> getSitesForCoverSearches() {
        if (sPreferredCoverSearchOrder.isEmpty()) {
            sPreferredCoverSearchOrder.addAll(readOrder(ListType.Covers));
        }
        //noinspection unchecked
        return (ArrayList<Site>) sPreferredCoverSearchOrder.clone();
    }

    @NonNull
    static List<Site> getSitesByDataReliability() {
        //noinspection unchecked
        return (List<Site>) DATA_RELIABILITY_ORDER.clone();
    }

    /**
     * Get the global search site list as a bitmask.
     * Includes <strong>ONLY</strong> the enabled sites.
     *
     * @return bitmask
     */
    public static int getEnabledSitesAsBitmask() {
        return getEnabledSitesAsBitmask(getSites());
    }

    /**
     * Get the global cover search site list as a bitmask.
     * Includes <strong>ONLY</strong> the enabled sites.
     *
     * @return bitmask
     */
    public static int getEnabledSitesForCoverSearchesAsBitmask() {
        return getEnabledSitesAsBitmask(getSitesForCoverSearches());
    }

    /**
     * Filter the incoming list, returning a new list with the enabled sites.
     *
     * @param list to filter
     *
     * @return list containing only the enables sites
     */
    public static int getEnabledSitesAsBitmask(@NonNull final ArrayList<Site> list) {
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
     * Update the standard search order/enabled list.
     *
     * @param context Current context
     * @param newList to use
     */
    public static void setOrder(@NonNull final Context context,
                                @NonNull final ArrayList<Site> newList) {
        sPreferredSearchOrder.clear();
        sPreferredSearchOrder.addAll(newList);
        saveOrder(context, ListType.Data, newList);
    }

    /**
     * Update the dedicated cover search order/enabled list.
     *
     * @param context Current context
     * @param newList to use
     */
    public static void setCoverOrder(@NonNull final Context context,
                                     @NonNull final ArrayList<Site> newList) {
        sPreferredCoverSearchOrder.clear();
        sPreferredCoverSearchOrder.addAll(newList);
        saveOrder(context, ListType.Covers, newList);
    }

    /**
     * Save the order of the given list (ids) to preferences.
     *
     * @param context  Current context
     * @param listType to save
     * @param newList  to save
     */
    private static void saveOrder(@NonNull final Context context,
                                  final ListType listType,
                                  @NonNull final ArrayList<Site> newList) {

        SharedPreferences.Editor ed = PreferenceManager.getDefaultSharedPreferences(context).edit();
        String order = Csv.join(SEP, newList, site -> {
            // store individual site settings
            site.saveToPrefs(ed);
            return String.valueOf(site.id);
        });
        ed.putString(listType.getKey(), order);
        ed.apply();
    }

    /**
     * Read the required list from preferences (or return the default).
     *
     * @param listType to get
     *
     * @return list
     */
    @NonNull
    private static ArrayList<Site> readOrder(@NonNull final ListType listType) {

        ArrayList<Site> orderDefaults;
        switch (listType) {
            case DataReliability:
                orderDefaults = DATA_RELIABILITY_ORDER;
                break;
            case Covers:
                orderDefaults = COVER_SEARCH_ORDER;
                break;
            case Data:
            default:
                orderDefaults = SEARCH_ORDER;
                break;
        }

        String order = PreferenceManager.getDefaultSharedPreferences(App.getAppContext())
                                        .getString(listType.getKey(), null);

        if (order == null) {
            return orderDefaults;
        }
        String[] ids = order.split(SEP);

        ArrayList<Site> newList = new ArrayList<>();
        for (String idStr : ids) {
            int id = Integer.parseInt(idStr);
            for (Site site : orderDefaults) {
                if (site.id == id) {
                    newList.add(site);
                    break;
                }
            }
        }
        return newList;
    }

    public enum ListType {
        Data, DataReliability, Covers;

        String getKey() {
            switch (this) {
                case DataReliability:
                    return PREFS_SEARCH_SITE_RELIABILITY;

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
