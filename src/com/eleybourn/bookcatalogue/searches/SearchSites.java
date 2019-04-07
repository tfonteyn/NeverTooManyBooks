package com.eleybourn.bookcatalogue.searches;

import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Parcel;
import android.os.Parcelable;

import androidx.annotation.AnyThread;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import androidx.annotation.WorkerThread;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import com.eleybourn.bookcatalogue.App;
import com.eleybourn.bookcatalogue.UniqueId;
import com.eleybourn.bookcatalogue.debug.Logger;
import com.eleybourn.bookcatalogue.searches.amazon.AmazonManager;
import com.eleybourn.bookcatalogue.searches.goodreads.GoodreadsManager;
import com.eleybourn.bookcatalogue.searches.googlebooks.GoogleBooksManager;
import com.eleybourn.bookcatalogue.searches.isfdb.ISFDBManager;
import com.eleybourn.bookcatalogue.searches.librarything.LibraryThingManager;
import com.eleybourn.bookcatalogue.searches.openlibrary.OpenLibraryManager;
import com.eleybourn.bookcatalogue.utils.AuthorizationException;
import com.eleybourn.bookcatalogue.utils.ISBN;
import com.eleybourn.bookcatalogue.utils.IllegalTypeException;
import com.eleybourn.bookcatalogue.utils.StorageUtils;

/**
 * Manages the setup of search engines/sites.
 * <p>
 * NEWKIND: adding a new search engine:
 * A search engine for a particular site should implement {@link SearchSiteManager}.
 * Configure in this class here below:
 * 1. Add an identifier (bit) + add it to {@link Site#SEARCH_ALL}.
 * 2. Add your new engine to {@link Site#getSearchSiteManager};
 * 3. create+add a new {@link Site} instance to {@link #SEARCH_ORDER_DEFAULTS}
 * and {@link #COVER_SEARCH_ORDER_DEFAULTS}
 * 4. Optional: add to res/xml/preferences.xml if the url should be editable.
 */
public final class SearchSites {

    /** tag. */
    public static final String TAG = SearchSites.class.getSimpleName();

    /** */
    static final String BKEY_SEARCH_SITES = TAG + ":searchSitesList";

    /** Preferences prefix. */
    private static final String PREF_PREFIX = "SearchManager.";

    /** the default search site order for standard data/covers. */
    private static final ArrayList<Site> SEARCH_ORDER_DEFAULTS = new ArrayList<>();
    /** the default search site order for _dedicated_ cover searches. */
    private static final ArrayList<Site> COVER_SEARCH_ORDER_DEFAULTS = new ArrayList<>();
    /** ENHANCE: reliability order is not user configurable for now, but plumbing installed. */
    private static final List<Site> PREFERRED_RELIABILITY_ORDER;

    /** the users preferred search site order. */
    private static ArrayList<Site> mPreferredSearchOrder;
    /** the users preferred search site order specific for covers. */
    private static ArrayList<Site> mPreferredCoverSearchOrder;

    /*
     * default search order.
     *
     *  Original app reliability order was:
     *  {SEARCH_GOODREADS, SEARCH_AMAZON, SEARCH_GOOGLE, SEARCH_LIBRARY_THING}
     */
    static {
        /*
         * standard searches for full details.
         */
        SEARCH_ORDER_DEFAULTS.add(new Site(Site.SEARCH_AMAZON, 0, 1));
        SEARCH_ORDER_DEFAULTS.add(new Site(Site.SEARCH_GOODREADS, 1, 0));
        SEARCH_ORDER_DEFAULTS.add(new Site(Site.SEARCH_GOOGLE, 2, 2));
        SEARCH_ORDER_DEFAULTS.add(new Site(Site.SEARCH_LIBRARY_THING, 3, 3));
        SEARCH_ORDER_DEFAULTS.add(new Site(Site.SEARCH_ISFDB, 4, 4));

        // bottom of the list, and disabled by default
        Site openlibrary_site = new Site(Site.SEARCH_OPEN_LIBRARY, 5, 5);
        openlibrary_site.setEnabled(false);
        SEARCH_ORDER_DEFAULTS.add(openlibrary_site);

        /* ************************************************************************************** */
        /*
         * dedicated cover lookup; does not use a reliability index.
         */
        COVER_SEARCH_ORDER_DEFAULTS.add(new Site(Site.SEARCH_GOOGLE, "cover", 0));
        COVER_SEARCH_ORDER_DEFAULTS.add(new Site(Site.SEARCH_LIBRARY_THING, "cover", 1));
        COVER_SEARCH_ORDER_DEFAULTS.add(new Site(Site.SEARCH_ISFDB, "cover", 2));
        COVER_SEARCH_ORDER_DEFAULTS.add(new Site(Site.SEARCH_GOODREADS, "cover", 3));
        COVER_SEARCH_ORDER_DEFAULTS.add(new Site(Site.SEARCH_AMAZON, "cover", 4));

        // bottom of the list, and disabled by default
        openlibrary_site = new Site(Site.SEARCH_OPEN_LIBRARY, "cover", 5);
        openlibrary_site.setEnabled(false);
        COVER_SEARCH_ORDER_DEFAULTS.add(openlibrary_site);

        /* ************************************************************************************** */

        PREFERRED_RELIABILITY_ORDER = new ArrayList<>(SEARCH_ORDER_DEFAULTS);
        /*
         * Create the user configurable lists.
         */
        // we're going to use set(index,...), so make them big enough
        mPreferredSearchOrder = new ArrayList<>(SEARCH_ORDER_DEFAULTS);
        mPreferredCoverSearchOrder = new ArrayList<>(COVER_SEARCH_ORDER_DEFAULTS);
        // yes, this shows that mPreferredSearchOrder should be Map's but for now
        // the code was done with List so this was the easiest to make them configurable.
        // To be redone.
        for (Site searchSite : SEARCH_ORDER_DEFAULTS) {
            mPreferredSearchOrder.set(searchSite.getPriority(), searchSite);
            PREFERRED_RELIABILITY_ORDER.set(searchSite.getReliability(), searchSite);
        }
        for (Site searchSite : COVER_SEARCH_ORDER_DEFAULTS) {
            mPreferredCoverSearchOrder.set(searchSite.getPriority(), searchSite);
        }
    }

    private SearchSites() {
    }

    @NonNull
    static List<Site> getSitesByReliability() {
        return PREFERRED_RELIABILITY_ORDER;
    }

    @NonNull
    static ArrayList<Site> getSites() {
        return mPreferredSearchOrder;
    }

    /**
     * Update the standard search order/enabled list.
     *
     * @param newList to use
     */
    static void setSearchOrder(@NonNull final ArrayList<Site> newList) {
        mPreferredSearchOrder = newList;
        SharedPreferences.Editor ed = App.getPrefs().edit();
        for (Site site : newList) {
            site.saveToPrefs(ed);
        }
        ed.apply();
    }

    @NonNull
    public static ArrayList<Site> getSitesForCoverSearches() {
        return mPreferredCoverSearchOrder;
    }

    /**
     * Update the dedicated cover search order/enabled list.
     *
     * @param newList to use
     */
    static void setCoverSearchOrder(@NonNull final ArrayList<Site> newList) {
        mPreferredCoverSearchOrder = newList;
        SharedPreferences.Editor ed = App.getPrefs().edit();
        for (Site site : newList) {
            site.saveToPrefs(ed);
        }
        ed.apply();
    }

    /**
     * If a {@link SearchSiteManager} does not support a specific (and faster) way/api
     * to fetch a cover image, then {@link SearchSiteManager#getCoverImage(String, ImageSizes)}
     * can call this fallback method.
     * Do NOT use if the site either does not support returning images during search,
     * or does not support isbn searches.
     * <p>
     * A search for the book is done, with the 'fetchThumbnail' flag set to true.
     * Any {@link IOException} or {@link AuthorizationException} thrown are ignored and
     * 'null' returned.
     *
     * @param isbn to search for
     *
     * @return found/saved File, or null when none found (or any other failure)
     */
    @Nullable
    @WorkerThread
    public static File getCoverImageFallback(@NonNull final SearchSiteManager site,
                                             @NonNull final String isbn) {
        // sanity check
        if (!ISBN.isValid(isbn)) {
            return null;
        }

        try {
            Bundle bookData = site.search(isbn, "", "", true);

            ArrayList<String> imageList =
                    bookData.getStringArrayList(UniqueId.BKEY_FILE_SPEC_ARRAY);
            if (imageList != null && !imageList.isEmpty()) {
                File found = new File(imageList.get(0));
                File coverFile = new File(found.getAbsolutePath() + '_' + isbn);
                StorageUtils.renameFile(found, coverFile);
                return coverFile;
            }
        } catch (IOException | AuthorizationException e) {
            Logger.error(e);
        }

        return null;
    }

    /**
     * Sizes of thumbnails.
     * These are open to interpretation (or not used) by individual {@link SearchSiteManager}.
     */
    public enum ImageSizes {
        SMALL,
        MEDIUM,
        LARGE
    }

    /**
     * API a search engine for a site needs to implement.
     */
    public interface SearchSiteManager {

        /**
         * Start a search using the passed criteria.
         *
         * @param fetchThumbnail Set to <tt>true</tt> if we want to get a thumbnail
         *
         * @return bundle with book data. Can be empty, but never null.
         *
         * @throws IOException            on failure
         * @throws AuthorizationException if the site rejects our credentials (if any)
         */
        @WorkerThread
        @NonNull
        Bundle search(@NonNull String isbn,
                      @NonNull String author,
                      @NonNull String title,
                      boolean fetchThumbnail)
                throws IOException, AuthorizationException;

        /**
         * Get a cover image.
         *
         * @param isbn to search for
         * @param size of image to get.
         *
         * @return found/saved File, or null when none found (or any other failure)
         */
        @Nullable
        @WorkerThread
        File getCoverImage(@NonNull String isbn,
                           @Nullable SearchSites.ImageSizes size);

        /**
         * Generic test to be implemented by individual site search managers to check if
         * this site is available for search.
         * e.g. check for developer keys, site is up/down, authorization, ...
         * <p>
         * Runs in a background task, so can run network code.
         *
         * @return <tt>true</tt> if we can use this site for searching.
         */
        @WorkerThread
        boolean isAvailable();


        /**
         * @return <tt>true</tt> if the site can only be searched with a valid ISBN
         */
        @AnyThread
        boolean isIsbnOnly();

        /**
         * Warning: the use of this method should be limited to places
         * where we search for multiple sizes!
         * <p>
         * Do NOT use this check if you just do a single search for some size.
         * <p>
         * TODO: the above warning could be phrased better...
         * try again:
         * CoverBrowser loops through all sizes
         * --> must use this method check to avoid calling sites to many times.
         * Other places, where just a single image is fetched, don't use this method.
         * <p>
         * Would it be better if this method was more "siteSupportsMultipleSizes()" ?
         *
         * @param size the image size we want to check support on.
         *
         * @return <tt>true</tt> if the site supports the passed image size
         */
        @AnyThread
        boolean supportsImageSize(@NonNull final ImageSizes size);

        /**
         * @return the resource id for the text "Searching {site}"
         */
        @AnyThread
        @StringRes
        int getSearchingResId();
    }

    /**
     * All search engines are added here.
     */
    public static class Site
            implements Parcelable {

        /** {@link Parcelable}. */
        public static final Creator<Site> CREATOR =
                new Creator<Site>() {
                    @Override
                    public Site createFromParcel(@NonNull final Parcel source) {
                        return new Site(source);
                    }

                    @Override
                    public Site[] newArray(final int size) {
                        return new Site[size];
                    }
                };
        /** search source to use. */
        public static final int SEARCH_LIBRARY_THING = 1 << 2;
        /** search source to use. */
        static final int SEARCH_GOOGLE = 1;
        /** search source to use. */
        static final int SEARCH_AMAZON = 1 << 1;
        /** search source to use. */
        static final int SEARCH_GOODREADS = 1 << 3;
        /**
         * search source to use.
         * Speculative Fiction only. i.e. Science-Fiction/Fantasy etc...
         */
        static final int SEARCH_ISFDB = 1 << 4;
        /*
         *  search source to use.
         */
        static final int SEARCH_OPEN_LIBRARY = 1 << 5;

        /** Mask including all search sources. */
        public static final int SEARCH_ALL = SEARCH_GOOGLE | SEARCH_AMAZON
                | SEARCH_LIBRARY_THING | SEARCH_GOODREADS | SEARCH_ISFDB | SEARCH_OPEN_LIBRARY;

        /** Internal id, bitmask based, not stored in prefs. */
        public final int id;

        /** Internal task(thread) name AND user-visible name AND key into prefs. */
        @NonNull
        private final String mName;

        /** user preference: enable/disable this site. */
        private boolean mEnabled = true;
        /** user preference: the priority/order the list of sites will be searched. */
        private int mPriority;
        /** for now hard-coded, but plumbing to have this as a user preference is done. */
        private int mReliability;

        /** the class which implements the search engine for a specific site. */
        private SearchSiteManager mSearchSiteManager;

        /**
         * Create the Site with whatever suitable default values.
         * If previously stored to SharedPreferences, the stored values will be used instead.
         *
         * @param id         Internal id, bitmask based
         * @param nameSuffix suffix to the add to the name
         * @param priority   the search priority order
         */
        Site(final int id,
             @NonNull final String nameSuffix,
             final int priority) {

            this.id = id;
            mName = getName(id) + '-' + nameSuffix;
            mPriority = priority;
            // by default, reliability == order.
            mReliability = priority;

            loadFromPrefs();
        }

        /**
         * Create the Site with whatever suitable default values.
         * If previously stored to SharedPreferences, the stored values will be used instead.
         *
         * @param id          Internal id, bitmask based
         * @param priority    the search priority order
         * @param reliability the search reliability order
         */
        Site(final int id,
             final int priority,
             final int reliability) {

            this.id = id;
            mName = getName(id);
            mPriority = priority;
            mReliability = reliability;

            loadFromPrefs();
        }

        /**
         * Reminder: this is IPC.. so don't load prefs!
         */
        Site(@NonNull final Parcel in) {
            id = in.readInt();
            //noinspection ConstantConditions
            mName = in.readString();
            mEnabled = in.readByte() != 0;
            mPriority = in.readInt();
            mReliability = in.readInt();
        }

        private static String getName(final int id) {
            switch (id) {
                case SEARCH_GOOGLE:
                    return "Google";
                case SEARCH_AMAZON:
                    return "Amazon";
                case SEARCH_GOODREADS:
                    return "Goodreads";
                case SEARCH_ISFDB:
                    return "ISFDB";
                case SEARCH_LIBRARY_THING:
                    return "LibraryThing";
                case SEARCH_OPEN_LIBRARY:
                    return "OpenLibrary";

                default:
                    throw new IllegalTypeException("Unexpected search source: " + id);
            }
        }

        /**
         * @return the manager class instance. Note that these are cached, so there is only one
         * instance for each site at all times.
         */
        public SearchSiteManager getSearchSiteManager() {
            if (mSearchSiteManager != null) {
                return mSearchSiteManager;
            }

            switch (id) {
                case SEARCH_GOOGLE:
                    mSearchSiteManager = new GoogleBooksManager();
                    break;

                case SEARCH_AMAZON:
                    mSearchSiteManager = new AmazonManager();
                    break;

                case SEARCH_GOODREADS:
                    mSearchSiteManager = new GoodreadsManager();
                    break;

                case SEARCH_ISFDB:
                    mSearchSiteManager = new ISFDBManager();
                    break;

                case SEARCH_LIBRARY_THING:
                    mSearchSiteManager = new LibraryThingManager();
                    break;

                case SEARCH_OPEN_LIBRARY:
                    mSearchSiteManager = new OpenLibraryManager();
                    break;

                default:
                    throw new IllegalTypeException("Unexpected search source: " + mName);
            }

            return mSearchSiteManager;
        }

        /**
         * Reminder: this is IPC.. so don't save prefs.
         */
        @Override
        public void writeToParcel(@NonNull final Parcel dest,
                                  final int flags) {
            dest.writeInt(id);
            dest.writeString(mName);
            dest.writeByte((byte) (mEnabled ? 1 : 0));
            dest.writeInt(mPriority);
            dest.writeInt(mReliability);
        }

        private void loadFromPrefs() {
            mEnabled = App.getPrefs().getBoolean(PREF_PREFIX + mName + ".enabled", mEnabled);
            mPriority = App.getPrefs().getInt(PREF_PREFIX + mName + ".order", mPriority);
            mReliability = App.getPrefs().getInt(PREF_PREFIX + mName + ".reliability",
                                                 mReliability);
        }

        void saveToPrefs(@NonNull final SharedPreferences.Editor editor) {
            editor.putBoolean(PREF_PREFIX + mName + ".enabled", mEnabled);
            editor.putInt(PREF_PREFIX + mName + ".order", mPriority);
            editor.putInt(PREF_PREFIX + mName + ".reliability", mReliability);
        }

        @SuppressWarnings("SameReturnValue")
        @Override
        public int describeContents() {
            return 0;
        }

        @NonNull
        public String getName() {
            return mName;
        }

        public boolean isEnabled() {
            return mEnabled;
        }

        void setEnabled(final boolean enabled) {
            mEnabled = enabled;
        }

        int getPriority() {
            return mPriority;
        }

        void setPriority(final int priority) {
            mPriority = priority;
        }

        int getReliability() {
            return mReliability;
        }

        public void setReliability(final int reliability) {
            mReliability = reliability;
        }

        @Override
        @NonNull
        public String toString() {
            return "Site{"
                    + "id=" + id
                    + ", mName=`" + mName + '`'
                    + ", mEnabled=" + mEnabled
                    + ", mPriority=" + mPriority
                    + ", mReliability=" + mReliability
                    + ", mSearchSiteManager=" + mSearchSiteManager
                    + '}';
        }
    }
}
