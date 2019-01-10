package com.eleybourn.bookcatalogue.searches;

import android.content.SharedPreferences;
import android.os.Parcel;
import android.os.Parcelable;

import androidx.annotation.NonNull;

import com.eleybourn.bookcatalogue.searches.amazon.SearchAmazonTask;
import com.eleybourn.bookcatalogue.searches.goodreads.SearchGoodreadsTask;
import com.eleybourn.bookcatalogue.searches.googlebooks.SearchGoogleBooksTask;
import com.eleybourn.bookcatalogue.searches.isfdb.SearchISFDBTask;
import com.eleybourn.bookcatalogue.searches.librarything.SearchLibraryThingTask;
import com.eleybourn.bookcatalogue.tasks.managedtasks.TaskManager;
import com.eleybourn.bookcatalogue.utils.Prefs;
import com.eleybourn.bookcatalogue.utils.RTE;

import java.util.ArrayList;
import java.util.List;

/**
 * Split of from {@link SearchManager} to avoid the static init of "SearchManager#TaskSwitch"
 * <p>
 * Static class; Holds various lists with {@link Site} 's for use by the {@link SearchManager}
 */
public final class SearchSites {

    /** */
    static final String BKEY_SEARCH_SITES = "searchSitesList";
    /** */
    private static final String TAG = "SearchManager";
    /** the default search site order for standard data/covers */
    private static final ArrayList<Site> mSearchOrderDefaults = new ArrayList<>();
    /** the default search site order for _dedicated_ cover searches */
    private static final ArrayList<Site> mCoverSearchOrderDefaults = new ArrayList<>();
    /** TODO: not user configurable for now, but plumbing installed */
    private static final List<Site> mPreferredReliabilityOrder;
    /** the users preferred search site order */
    private static ArrayList<Site> mPreferredSearchOrder;
    /** the users preferred search site order */
    private static ArrayList<Site> mPreferredCoverSearchOrder;

    /*
     * default search order
     *
     * NEWKIND: search web site configuration
     *
     *  Original app reliability order was:
     *  {SEARCH_GOODREADS, SEARCH_AMAZON, SEARCH_GOOGLE, SEARCH_LIBRARY_THING}
     */
    static {

        Site site;

        // standard searches; includes cover lookup while searching for all data
        mSearchOrderDefaults.add(new Site(Site.SEARCH_AMAZON, "Amazon", 0, 1));
        mSearchOrderDefaults.add(new Site(Site.SEARCH_GOODREADS, "Goodreads", 1, 0));
        mSearchOrderDefaults.add(new Site(Site.SEARCH_GOOGLE, "Google", 2, 2));

        site = new Site(Site.SEARCH_LIBRARY_THING, "LibraryThing", 3, 3);
        site.isbnOnly = true;
        mSearchOrderDefaults.add(site);

        // pretty reliable site, but only serving one group of readers. Those readers can up the priority in the preferences.
        mSearchOrderDefaults.add(new Site(Site.SEARCH_ISFDB, "ISFDB", 4, 4));


        // dedicated cover lookup; does not use a reliability index.
        mCoverSearchOrderDefaults.add(new Site(Site.SEARCH_GOOGLE, "Google-cover", 0));

        site = new Site(Site.SEARCH_LIBRARY_THING, "LibraryThing-cover", 1);
        site.isbnOnly = true;

        mCoverSearchOrderDefaults.add(site);
        mCoverSearchOrderDefaults.add(new Site(Site.SEARCH_ISFDB, "ISFDB-cover", 2));


        // we're going to use set(index,...), so make them big enough
        mPreferredSearchOrder = new ArrayList<>(mSearchOrderDefaults);
        mPreferredCoverSearchOrder = new ArrayList<>(mCoverSearchOrderDefaults);
        // not user configurable yet
        mPreferredReliabilityOrder = new ArrayList<>(mSearchOrderDefaults);

        // yes, this shows that mPreferredSearchOrder should be Map's but for now the code was done with List
        // so this was the easiest to make them configurable. To be redone.
        for (Site searchSite : mSearchOrderDefaults) {
            mPreferredSearchOrder.set(searchSite.priority, searchSite);
            mPreferredReliabilityOrder.set(searchSite.reliability, searchSite);
        }

        for (Site searchSite : mCoverSearchOrderDefaults) {
            mPreferredCoverSearchOrder.set(searchSite.priority, searchSite);
        }
    }

    private SearchSites() {
    }

    @NonNull
    static List<Site> getSitesByReliability() {
        return mPreferredReliabilityOrder;
    }

    @NonNull
    static ArrayList<Site> getSites() {
        return mPreferredSearchOrder;
    }

    static void setSearchOrder(@NonNull final ArrayList<Site> newList) {
        mPreferredSearchOrder = newList;
        SharedPreferences.Editor e = Prefs.getPrefs().edit();
        for (Site site : newList) {
            site.saveToPrefs(e);
        }
        e.apply();
    }

    @NonNull
    public static ArrayList<Site> getSitesForCoverSearches() {
        return mPreferredCoverSearchOrder;
    }

    static void setCoverSearchOrder(@NonNull final ArrayList<Site> newList) {
        mPreferredCoverSearchOrder = newList;
        SharedPreferences.Editor e = Prefs.getPrefs().edit();
        for (Site site : newList) {
            site.saveToPrefs(e);
        }
        e.apply();
    }

    /**
     * NEWKIND: search web site configuration.
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
        /** search source to use */
        public static final int SEARCH_GOOGLE = 1;
        /** search source to use */
        public static final int SEARCH_AMAZON = 1 << 1;
        /** search source to use */
        public static final int SEARCH_LIBRARY_THING = 1 << 2;
        /** search source to use */
        public static final int SEARCH_GOODREADS = 1 << 3;
        /** search source to use */
        public static final int SEARCH_ISFDB = 1 << 4;
        /** Mask including all search sources */
        public static final int SEARCH_ALL = SEARCH_GOOGLE | SEARCH_AMAZON
                | SEARCH_LIBRARY_THING | SEARCH_GOODREADS | SEARCH_ISFDB;

        /** Internal id, bitmask based, not stored in prefs */
        public final int id;
        /** Internal AND user-visible name, key into prefs. */
        @NonNull
        final String name;
        public boolean enabled = true;
        int priority;
        int reliability;
        boolean isbnOnly = false;

        /**
         * Create the Site with whatever suitable default values.
         * If previously stored to SharedPreferences, the stored values will be used instead.
         *
         * @param id       Internal id, bitmask based
         * @param name     user-visible name.
         * @param priority the search priority order
         */
        @SuppressWarnings("SameParameterValue")
        Site(final int id,
             @NonNull final String name,
             final int priority) {
            this.id = id;
            this.name = name;
            this.priority = priority;
            // by default, reliability == order.
            this.reliability = priority;

            loadFromPrefs();
        }

        /**
         * Create the Site with whatever suitable default values.
         * If previously stored to SharedPreferences, the stored values will be used instead.
         *
         * @param id          Internal id, bitmask based
         * @param name        user-visible name.
         * @param priority    the search priority order
         * @param reliability the search reliability order
         */
        @SuppressWarnings("SameParameterValue")
        Site(final int id,
             @NonNull final String name,
             final int priority,
             final int reliability) {
            this.id = id;
            this.name = name;
            this.priority = priority;
            this.reliability = reliability;

            loadFromPrefs();
        }

        /**
         * Reminder: this is IPC.. so don't load prefs!
         */
        Site(@NonNull final Parcel in) {
            id = in.readInt();
            name = in.readString();
            enabled = in.readByte() != 0;
            priority = in.readInt();
            reliability = in.readInt();
            isbnOnly = in.readByte() != 0;
        }

        /**
         * Reminder: this is IPC.. so don't save prefs.
         */
        @Override
        public void writeToParcel(@NonNull final Parcel dest,
                                  final int flags) {
            dest.writeInt(id);
            dest.writeString(name);
            dest.writeByte((byte) (enabled ? 1 : 0));
            dest.writeInt(priority);
            dest.writeInt(reliability);
            dest.writeByte((byte) (isbnOnly ? 1 : 0));
        }

        private void loadFromPrefs() {
            enabled = Prefs.getBoolean(TAG + '.' + name + ".enabled", enabled);
            priority = Prefs.getInt(TAG + '.' + name + ".order", priority);
            reliability = Prefs.getInt(TAG + '.' + name + ".reliability", reliability);
            // leaving commented as a reminder; this is NOT a preference but a site rule
            //isbnOnly = BookCatalogueApp.getBoolean(TAG + "." + name + ".isbnOnly", isbnOnly);
        }

        void saveToPrefs(@NonNull final SharedPreferences.Editor editor) {
            editor.putBoolean(TAG + '.' + name + ".enabled", enabled);
            editor.putInt(TAG + '.' + name + ".order", priority);
            editor.putInt(TAG + '.' + name + ".reliability", reliability);
        }

        /** {@link Parcelable}. */
        @SuppressWarnings("SameReturnValue")
        @Override
        public int describeContents() {
            return 0;
        }

        /**
         * NEWKIND: search web site configuration.
         */
        ManagedSearchTask getTask(@NonNull final TaskManager manager) {
            switch (id) {
                case SEARCH_GOOGLE:
                    return new SearchGoogleBooksTask(name, manager);

                case SEARCH_AMAZON:
                    return new SearchAmazonTask(name, manager);

                case SEARCH_GOODREADS:
                    return new SearchGoodreadsTask(name, manager);

                case SEARCH_ISFDB:
                    return new SearchISFDBTask(name, manager);

                case SEARCH_LIBRARY_THING:
                    return new SearchLibraryThingTask(name, manager);

                default:
                    throw new RTE.IllegalTypeException("Unexpected search source: " + name);
            }
        }

//        @Override
//        public String toString() {
//            return "SearchSite{" +
//                    "id=" + id +
//                    ", name='" + name + '\'' +
//                    ", enabled=" + enabled +
//                    ", order=" + order +
//                    ", reliability=" + reliability +
//                    '}';
//        }
    }
}
