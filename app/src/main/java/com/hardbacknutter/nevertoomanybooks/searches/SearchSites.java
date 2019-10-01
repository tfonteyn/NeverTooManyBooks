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
import com.hardbacknutter.nevertoomanybooks.searches.amazon.AmazonManager;
import com.hardbacknutter.nevertoomanybooks.searches.goodreads.GoodreadsManager;
import com.hardbacknutter.nevertoomanybooks.searches.googlebooks.GoogleBooksManager;
import com.hardbacknutter.nevertoomanybooks.searches.isfdb.IsfdbManager;
import com.hardbacknutter.nevertoomanybooks.searches.kbnl.KbNlManager;
import com.hardbacknutter.nevertoomanybooks.searches.librarything.LibraryThingManager;
import com.hardbacknutter.nevertoomanybooks.searches.openlibrary.OpenLibraryManager;
import com.hardbacknutter.nevertoomanybooks.utils.UnexpectedValueException;

/**
 * Manages the setup of search engines/sites.
 * <p>
 * <br>NEWTHINGS: adding a new search engine:
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


    /** tag. */
    private static final String TAG = "SearchSites";
    public static final String BKEY_SEARCH_SITES = TAG + ":searchSitesList";
    /** search source to use. */
    private static final int GOOGLE_BOOKS = 1;
    /** search source to use. */
    static final int AMAZON = 1 << 1;
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

    /**
     * Dutch sites are by *default* only enabled if either the device or this app is running
     * in Dutch.
     *
     * @return {@code true} if Dutch sites should be enabled by default.
     */
    private static boolean isDutch() {
        return "nld".equals(App.getSystemLocale().getISO3Language())
               || "nld".equals(Locale.getDefault().getISO3Language());
    }

    /** the default search site order for standard data/covers. */
    private static final ArrayList<Site> SEARCH_ORDER_DEFAULTS = new ArrayList<>();
    /** the default search site order for _dedicated_ cover searches. */
    private static final ArrayList<Site> COVER_SEARCH_ORDER_DEFAULTS = new ArrayList<>();

    /** ENHANCE: reliability order is not user configurable for now, but plumbing installed. */
    private static final List<Site> PREFERRED_RELIABILITY_ORDER;

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
        SEARCH_ORDER_DEFAULTS.add(Site.newSite(GOODREADS, true, priority++, 0));
        SEARCH_ORDER_DEFAULTS.add(Site.newSite(GOOGLE_BOOKS, true, priority++, 2));
        SEARCH_ORDER_DEFAULTS.add(Site.newSite(LIBRARY_THING, true, priority++, 3));
        SEARCH_ORDER_DEFAULTS.add(Site.newSite(ISFDB, true, priority++, 4));

        // Dutch.
        SEARCH_ORDER_DEFAULTS.add(Site.newSite(KBNL, isDutch(), priority++, 5));

        // The proxy site has been broken since around April 2019.
        // 2019-08-11: still broken, disabled by default.
        SEARCH_ORDER_DEFAULTS.add(Site.newSite(AMAZON, false, priority++, 1));

        // bottom of the list as the data from this site is not very complete.
        // Disabled by default.
        //noinspection UnusedAssignment
        SEARCH_ORDER_DEFAULTS.add(Site.newSite(OPEN_LIBRARY, false, priority++, 6));
    }

    /*
     * Default search order for dedicated cover lookup; does not use a reliability index.
     */
    static {
        int priority = 0;
        COVER_SEARCH_ORDER_DEFAULTS.add(Site.newCoverSite(GOOGLE_BOOKS, true, priority++));
        COVER_SEARCH_ORDER_DEFAULTS.add(Site.newCoverSite(LIBRARY_THING, true, priority++));
        COVER_SEARCH_ORDER_DEFAULTS.add(Site.newCoverSite(ISFDB, true, priority++));
        COVER_SEARCH_ORDER_DEFAULTS.add(Site.newCoverSite(GOODREADS, true, priority++));

        // Dutch.
        COVER_SEARCH_ORDER_DEFAULTS.add(Site.newCoverSite(KBNL, isDutch(), priority++));

        // The proxy site has been broken since around April 2019.
        COVER_SEARCH_ORDER_DEFAULTS.add(Site.newCoverSite(AMAZON, false, priority++));

        // bottom of the list as the data from this site is not up to scratch. Disabled by default.
        //noinspection UnusedAssignment
        COVER_SEARCH_ORDER_DEFAULTS.add(Site.newCoverSite(OPEN_LIBRARY, false, priority++));
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
        // To be redone some day...
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
            case KBNL:
                return "KBNL";

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

            case KBNL:
                return new KbNlManager();

            default:
                throw new UnexpectedValueException(id);
        }
    }

    /**
     * Reset all values back to the hardcoded defaults.
     *
     * @param context Current context
     */
    public static void reset(@NonNull final Context context) {
        setSearchOrder(context, SEARCH_ORDER_DEFAULTS);
        setCoverSearchOrder(context, COVER_SEARCH_ORDER_DEFAULTS);
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
        setSearchOrder(context, SEARCH_ORDER_DEFAULTS);
    }

    /**
     * Reset cover search order values back to the hardcoded defaults.
     *
     * @param context Current context
     */
    public static void resetCoverSearchOrder(@NonNull final Context context) {
        setCoverSearchOrder(context, COVER_SEARCH_ORDER_DEFAULTS);
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

    @IntDef(flag = true, value = {
            GOOGLE_BOOKS, AMAZON, LIBRARY_THING, GOODREADS, ISFDB, OPEN_LIBRARY, KBNL})
    @Retention(RetentionPolicy.SOURCE)
    public @interface Id {
    }
}
