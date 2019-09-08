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

import androidx.annotation.NonNull;
import androidx.preference.PreferenceManager;

import java.util.ArrayList;
import java.util.List;

import com.hardbacknutter.nevertoomanybooks.App;
import com.hardbacknutter.nevertoomanybooks.searches.amazon.AmazonManager;
import com.hardbacknutter.nevertoomanybooks.searches.goodreads.GoodreadsManager;
import com.hardbacknutter.nevertoomanybooks.searches.googlebooks.GoogleBooksManager;
import com.hardbacknutter.nevertoomanybooks.searches.isfdb.IsfdbManager;
import com.hardbacknutter.nevertoomanybooks.searches.kbnl.KbNlManager;
import com.hardbacknutter.nevertoomanybooks.searches.librarything.LibraryThingManager;
import com.hardbacknutter.nevertoomanybooks.searches.openlibrary.OpenLibraryManager;
import com.hardbacknutter.nevertoomanybooks.utils.LocaleUtils;

/**
 * Manages the setup of search engines/sites.
 * <p>
 * <br>NEWKIND: adding a new search engine:
 * To make it available, follow these steps:
 * <ol>
 * <li>Implement {@link SearchEngine} to create the new engine.</li>
 * <li>Add an identifier (bit) in this class + add it to {@link #SEARCH_ALL}.</li>
 * <li>Add a name for it to {@link #getName}.<br>
 * This should be a hardcoded, single word, no spaces, and will be user visible.</li>
 * <li>Add your new engine to {@link #getSearchEngine};</li>
 * <li>Create+add a new instance to {@link #SEARCH_ORDER_DEFAULTS}<br>
 * and {@link #COVER_SEARCH_ORDER_DEFAULTS}</li>
 * <li>Optional: add to res/xml/preferences.xml if the url should be editable.<br>
 * See the Amazon example in that xml file.</li>
 * </ol>
 *
 * <strong>Note:</strong> never change the identifiers (bit flag) of the sites, as they are stored
 * in user preferences.
 */
public final class SearchSites {

    /** search source to use. */
    static final int AMAZON = 1 << 1;
    /** tag. */
    private static final String TAG = "SearchSites";
    public static final String BKEY_SEARCH_SITES = TAG + ":searchSitesList";
    /** search source to use. */
    private static final int GOOGLE_BOOKS = 1;
    /** search source to use. */
    private static final int LIBRARY_THING = 1 << 2;
    /** search source to use. */
    private static final int GOODREADS = 1 << 3;
    /**
     * search source to use.
     * Speculative Fiction only. i.e. Science-Fiction/Fantasy etc...
     */
    private static final int ISFDB = 1 << 4;
    /** search source to use. */
    private static final int OPEN_LIBRARY = 1 << 5;
    /**
     * search source to use.
     * Dutch books & comics.
     */
    private static final int KBNL = 1 << 6;

    /** Mask including all search sources. */
    public static final int SEARCH_ALL = GOOGLE_BOOKS | AMAZON
                                         | LIBRARY_THING | GOODREADS | ISFDB | OPEN_LIBRARY
                                         | KBNL;

    /** the default search site order for standard data/covers. */
    private static final ArrayList<Site> SEARCH_ORDER_DEFAULTS = new ArrayList<>();
    /** the default search site order for _dedicated_ cover searches. */
    private static final ArrayList<Site> COVER_SEARCH_ORDER_DEFAULTS = new ArrayList<>();

    /** ENHANCE: reliability order is not user configurable for now, but plumbing installed. */
    private static final List<Site> PREFERRED_RELIABILITY_ORDER;
    private static final String UNEXPECTED_SEARCH_SOURCE_ERROR = "Unexpected search source: ";

    /** the users preferred search site order. */
    private static ArrayList<Site> sPreferredSearchOrder;
    /** the users preferred search site order specific for covers. */
    private static ArrayList<Site> sPreferredCoverSearchOrder;

    /*
     * Default search order for full details.
     *
     * Original app reliability order was: {GOODREADS, AMAZON, GOOGLE_BOOKS, LIBRARY_THING}
     */
    static {
        int priority = 0;
        SEARCH_ORDER_DEFAULTS.add(Site.newSite(GOODREADS, priority++, 0));
        SEARCH_ORDER_DEFAULTS.add(Site.newSite(GOOGLE_BOOKS, priority++, 2));
        SEARCH_ORDER_DEFAULTS.add(Site.newSite(LIBRARY_THING, priority++, 3));
        SEARCH_ORDER_DEFAULTS.add(Site.newSite(ISFDB, priority++, 4));

        // Dutch.
        Site kbnl = Site.newSite(KBNL, priority++, 5);
        // Disabled by default if neither the device or the app is running in Dutch.
        if (!"nld".equals(App.getSystemLocale().getISO3Language())
            && !"nld".equals(
                LocaleUtils.getLocale(App.getLocalizedAppContext()).getISO3Language())) {
            kbnl.setEnabled(false);
        }
        SEARCH_ORDER_DEFAULTS.add(kbnl);

        // The proxy site has been broken since around April 2019.
        // 2019-08-11: still broken, disabling for now.
        Site amazon = Site.newSite(AMAZON, priority++, 1);
        amazon.setEnabled(false);
        SEARCH_ORDER_DEFAULTS.add(amazon);

        // bottom of the list as the data from this site is not up to scratch. Disabled by default.
        @SuppressWarnings("UnusedAssignment")
        Site openLibrary = Site.newSite(OPEN_LIBRARY, priority++, 6);
        openLibrary.setEnabled(false);
        SEARCH_ORDER_DEFAULTS.add(openLibrary);
    }

    /*
     * Default search order for dedicated cover lookup; does not use a reliability index.
     */
    static {
        int priority = 0;
        COVER_SEARCH_ORDER_DEFAULTS.add(Site.newCoverSite(GOOGLE_BOOKS, priority++));
        COVER_SEARCH_ORDER_DEFAULTS.add(Site.newCoverSite(LIBRARY_THING, priority++));
        COVER_SEARCH_ORDER_DEFAULTS.add(Site.newCoverSite(ISFDB, priority++));
        COVER_SEARCH_ORDER_DEFAULTS.add(Site.newCoverSite(GOODREADS, priority++));

        // Dutch. Disabled by default.
        Site kbnl = Site.newCoverSite(KBNL, priority++);
        kbnl.setEnabled(false);
        COVER_SEARCH_ORDER_DEFAULTS.add(kbnl);

        // The proxy site has been broken since around April 2019.
        Site amazon = Site.newCoverSite(AMAZON, priority++);
        amazon.setEnabled(false);
        COVER_SEARCH_ORDER_DEFAULTS.add(amazon);

        // bottom of the list as the data from this site is not up to scratch. Disabled by default.
        @SuppressWarnings("UnusedAssignment")
        Site openLibrary = Site.newCoverSite(OPEN_LIBRARY, priority++);
        openLibrary.setEnabled(false);
        COVER_SEARCH_ORDER_DEFAULTS.add(openLibrary);
    }

    /*
     * Create the user configurable lists.
     */
    static {
        // same length as the defaults.
        PREFERRED_RELIABILITY_ORDER = new ArrayList<>(SEARCH_ORDER_DEFAULTS);

        // we're going to use set(index,...), so make them big enough
        sPreferredSearchOrder = new ArrayList<>(SEARCH_ORDER_DEFAULTS);
        sPreferredCoverSearchOrder = new ArrayList<>(COVER_SEARCH_ORDER_DEFAULTS);
        // yes, this shows that sPreferredSearchOrder should be Map's but for now
        // the code was done with List so this was the easiest to make them configurable.
        // To be redone.
        for (Site searchSite : SEARCH_ORDER_DEFAULTS) {
            sPreferredSearchOrder.set(searchSite.getPriority(), searchSite);
            PREFERRED_RELIABILITY_ORDER.set(searchSite.getReliability(), searchSite);
        }
        for (Site searchSite : COVER_SEARCH_ORDER_DEFAULTS) {
            sPreferredCoverSearchOrder.set(searchSite.getPriority(), searchSite);
        }
    }

    private SearchSites() {
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
    static String getName(final int id) {
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
            case KBNL:
                return "KBNL";

            default:
                throw new IllegalStateException(UNEXPECTED_SEARCH_SOURCE_ERROR + id);
        }
    }

    /**
     * Get a new SearchEngine class instance for the given ID.
     *
     * @return instance
     */
    static SearchEngine getSearchEngine(final int id) {

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

            case KBNL:
                return new KbNlManager();

            default:
                throw new IllegalStateException(UNEXPECTED_SEARCH_SOURCE_ERROR + id);
        }
    }


    public static boolean usePublisher(@NonNull final Context context) {
        return PreferenceManager.getDefaultSharedPreferences(context)
                                .getBoolean(IsfdbManager.PREFS_USE_PUBLISHER, false);
    }

    /**
     * Reset all values back to the hardcoded defaults.
     */
    public static void reset(@NonNull final Context context) {
        setSearchOrder(context, SEARCH_ORDER_DEFAULTS);
        setCoverSearchOrder(context, COVER_SEARCH_ORDER_DEFAULTS);
    }

    public static void resetSearchOrder(@NonNull final Context context) {
        setSearchOrder(context, SEARCH_ORDER_DEFAULTS);
    }

    public static void resetCoverSearchOrder(@NonNull final Context context) {
        setCoverSearchOrder(context, COVER_SEARCH_ORDER_DEFAULTS);
    }

    @NonNull
    static List<Site> getSitesByReliability() {
        return PREFERRED_RELIABILITY_ORDER;
    }

    @NonNull
    public static ArrayList<Site> getSites() {
        return sPreferredSearchOrder;
    }

    /**
     * Update the standard search order/enabled list.
     *
     * @param context Current context
     * @param newList to use
     */
    public static void setSearchOrder(@NonNull final Context context,
                                      @NonNull final ArrayList<Site> newList) {
        sPreferredSearchOrder = newList;
        SharedPreferences.Editor ed = PreferenceManager.getDefaultSharedPreferences(context).edit();
        for (Site site : newList) {
            site.saveToPrefs(ed);
        }
        ed.apply();
    }

    @NonNull
    public static ArrayList<Site> getSitesForCoverSearches() {
        return sPreferredCoverSearchOrder;
    }

    /**
     * Update the dedicated cover search order/enabled list.
     *
     * @param context Current context
     * @param newList to use
     */
    public static void setCoverSearchOrder(@NonNull final Context context,
                                           @NonNull final ArrayList<Site> newList) {
        sPreferredCoverSearchOrder = newList;
        SharedPreferences.Editor ed = PreferenceManager.getDefaultSharedPreferences(context).edit();
        for (Site site : newList) {
            site.saveToPrefs(ed);
        }
        ed.apply();
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
    public static boolean alertRegistrationBeneficial(@NonNull final Context context,
                                                      @NonNull final String prefSuffix,
                                                      final int searchSites) {
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
}
