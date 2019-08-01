package com.eleybourn.bookcatalogue.searches;

import android.content.SharedPreferences;

import androidx.annotation.NonNull;

import java.util.ArrayList;
import java.util.List;

import com.eleybourn.bookcatalogue.App;
import com.eleybourn.bookcatalogue.searches.amazon.AmazonManager;
import com.eleybourn.bookcatalogue.searches.goodreads.GoodreadsManager;
import com.eleybourn.bookcatalogue.searches.googlebooks.GoogleBooksManager;
import com.eleybourn.bookcatalogue.searches.isfdb.IsfdbManager;
import com.eleybourn.bookcatalogue.searches.librarything.LibraryThingManager;
import com.eleybourn.bookcatalogue.searches.openlibrary.OpenLibraryManager;
import com.eleybourn.bookcatalogue.utils.IllegalTypeException;

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
 */
public final class SearchSites {

    /** tag. */
    public static final String TAG = "SearchSites";
    public static final String BKEY_SEARCH_SITES = TAG + ":searchSitesList";

    /** search source to use. */
    @SuppressWarnings("WeakerAccess")
    public static final int GOOGLE_BOOKS = 1;

    /** search source to use. */
    @SuppressWarnings("WeakerAccess")
    public static final int AMAZON = 1 << 1;

    /** search source to use. */
    public static final int LIBRARY_THING = 1 << 2;

    /** search source to use. */
    @SuppressWarnings("WeakerAccess")
    public static final int GOODREADS = 1 << 3;

    /**
     * search source to use.
     * Speculative Fiction only. i.e. Science-Fiction/Fantasy etc...
     */
    public static final int ISFDB = 1 << 4;

    /** search source to use. */
    @SuppressWarnings("WeakerAccess")
    public static final int OPEN_LIBRARY = 1 << 5;

    /** Mask including all search sources. */
    public static final int SEARCH_ALL = GOOGLE_BOOKS | AMAZON
            | LIBRARY_THING | GOODREADS | ISFDB | OPEN_LIBRARY;

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
     * default search order.
     *
     *  Original app reliability order was:
     *  {GOODREADS, AMAZON, GOOGLE_BOOKS, LIBRARY_THING}
     */
    static {
        /*
         * standard searches for full details.
         */
        SEARCH_ORDER_DEFAULTS.add(Site.newSite(GOODREADS, 0, 0));
        SEARCH_ORDER_DEFAULTS.add(Site.newSite(GOOGLE_BOOKS, 1, 2));
        SEARCH_ORDER_DEFAULTS.add(Site.newSite(LIBRARY_THING, 2, 3));
        SEARCH_ORDER_DEFAULTS.add(Site.newSite(ISFDB, 3, 4));

        // The proxy site has been broken since around April 2019.
        // 2019-05-26: still broken, disabling Amazon here for now.
        Site amazon = Site.newSite(AMAZON, 4, 1);
        amazon.setEnabled(false);
        SEARCH_ORDER_DEFAULTS.add(amazon);

        // bottom of the list as the data from this site is not up to scratch. Disabled by default.
        Site openLibrary = Site.newSite(OPEN_LIBRARY, 5, 5);
        openLibrary.setEnabled(false);
        SEARCH_ORDER_DEFAULTS.add(openLibrary);

        /*
         * dedicated cover lookup; does not use a reliability index.
         */
        COVER_SEARCH_ORDER_DEFAULTS.add(Site.newCoverSite(GOOGLE_BOOKS, 0));
        COVER_SEARCH_ORDER_DEFAULTS.add(Site.newCoverSite(LIBRARY_THING, 1));
        COVER_SEARCH_ORDER_DEFAULTS.add(Site.newCoverSite(ISFDB, 2));
        COVER_SEARCH_ORDER_DEFAULTS.add(Site.newCoverSite(GOODREADS, 3));

        // The proxy site has been broken since around April 2019.
        amazon = Site.newCoverSite(AMAZON, 4);
        amazon.setEnabled(false);
        COVER_SEARCH_ORDER_DEFAULTS.add(amazon);

        // bottom of the list as the data from this site is not up to scratch. Disabled by default.
        openLibrary = Site.newCoverSite(OPEN_LIBRARY, 5);
        openLibrary.setEnabled(false);
        COVER_SEARCH_ORDER_DEFAULTS.add(openLibrary);
    }

    /* ************************************************************************************** */
    static {

        PREFERRED_RELIABILITY_ORDER = new ArrayList<>(SEARCH_ORDER_DEFAULTS);

        /*
         * Create the user configurable lists.
         */
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
     * <li>User-visible name in the app settings.</li>
     * <li>As the key into the actual preferences.</li>
     * <li>Internal task(thread) name which in some circumstances will be user-visible.</li>
     * </ol>
     * <p>
     * As it's used as a prefs key, it should never be changed.
     * <p>
     * <b>Note:</b> the name is also required in the actual {@link SearchEngine}
     * as a {@code StringRes} but the method here can not use that one without
     * instantiating which we don't want here.
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

            default:
                throw new IllegalTypeException(UNEXPECTED_SEARCH_SOURCE_ERROR + id);
        }
    }

    /**
     * Get a new SearchEngine class instance for the given id.
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

            default:
                throw new IllegalTypeException(UNEXPECTED_SEARCH_SOURCE_ERROR + id);
        }
    }


    public static boolean usePublisher() {
        return App.getPrefs().getBoolean(IsfdbManager.PREFS_USE_PUBLISHER, false);
    }

    /**
     * Reset all values back to the hardcoded defaults.
     */
    public static void reset() {
        setSearchOrder(SEARCH_ORDER_DEFAULTS);
        setCoverSearchOrder(COVER_SEARCH_ORDER_DEFAULTS);
    }

    public static void resetSearchOrder() {
        setSearchOrder(SEARCH_ORDER_DEFAULTS);
    }

    public static void resetCoverSearchOrder() {
        setCoverSearchOrder(COVER_SEARCH_ORDER_DEFAULTS);
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
     * @param newList to use
     */
    public static void setSearchOrder(@NonNull final ArrayList<Site> newList) {
        sPreferredSearchOrder = newList;
        SharedPreferences.Editor ed = App.getPrefs().edit();
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
     * @param newList to use
     */
    public static void setCoverSearchOrder(@NonNull final ArrayList<Site> newList) {
        sPreferredCoverSearchOrder = newList;
        SharedPreferences.Editor ed = App.getPrefs().edit();
        for (Site site : newList) {
            site.saveToPrefs(ed);
        }
        ed.apply();
    }
}
