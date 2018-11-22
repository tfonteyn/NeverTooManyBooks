package com.eleybourn.bookcatalogue.searches;

import android.content.SharedPreferences;
import android.os.Parcel;
import android.os.Parcelable;
import android.support.annotation.NonNull;

import com.eleybourn.bookcatalogue.BookCatalogueApp;
import com.eleybourn.bookcatalogue.searches.amazon.SearchAmazonTask;
import com.eleybourn.bookcatalogue.searches.goodreads.SearchGoodreadsTask;
import com.eleybourn.bookcatalogue.searches.googlebooks.SearchGoogleBooksTask;
import com.eleybourn.bookcatalogue.searches.isfdb.SearchISFDBTask;
import com.eleybourn.bookcatalogue.searches.librarything.SearchLibraryThingTask;
import com.eleybourn.bookcatalogue.tasks.TaskManager;
import com.eleybourn.bookcatalogue.utils.RTE;

import java.util.ArrayList;
import java.util.List;

/**
 * Split of from {@link SearchManager} to avoid the static init of "SearchManager#TaskSwitch"
 *
 * Singleton class
 *
 * Note: Most List's here must be ArrayList as they need to be serializable
 */
public class SearchSites {

    /** Options indicating a search source to use */
    public static final int SEARCH_GOOGLE = 1;
    /** Options indicating a search source to use */
    public static final int SEARCH_AMAZON = 1 << 1;
    /** Options indicating a search source to use */
    public static final int SEARCH_LIBRARY_THING = 1 << 2;
    /** Options indicating a search source to use */
    public static final int SEARCH_GOODREADS = 1 << 3;
    /** Options indicating a search source to use */
    public static final int SEARCH_ISFDB = 1 << 4;
    /** Mask including all search sources */
    public static final int SEARCH_ALL = SEARCH_GOOGLE | SEARCH_AMAZON | SEARCH_LIBRARY_THING | SEARCH_GOODREADS | SEARCH_ISFDB;
    /** */
    public static final String BKEY_SEARCH_SITES = "searchSitesList";
    private static final String TAG = "SearchManager";
    /** the default search site order */
    private static final ArrayList<Site> mSearchOrderDefaults = new ArrayList<>();
    /** the default search site order */
    private static final ArrayList<Site> mCoverSearchOrderDefaults = new ArrayList<>();
    /** TODO: not user configurable for now, but plumbing installed */
    private static List<Site> mReliabilityOrder;
    /** the users preferred search site order */
    private static ArrayList<Site> mPreferredSearchOrder;
    /** the users preferred search site order */
    private static ArrayList<Site> mPreferredCoverSearchOrder;

    static {
        /*ENHANCE: note to self... redo this mess. To complicated for what it is doing. Isolate ALL functionality in Site

         * default search order
         *
         * NEWKIND: search web site configuration
         *
         *  Original app reliability order was:
         *  {SEARCH_GOODREADS, SEARCH_AMAZON, SEARCH_GOOGLE, SEARCH_LIBRARY_THING}
         */
        Site site;

        mSearchOrderDefaults.add(new Site(SEARCH_AMAZON, "Amazon",
                0, true, 1));
        mSearchOrderDefaults.add(new Site(SEARCH_GOODREADS, "GoodReads",
                1, true, 0));
        mSearchOrderDefaults.add(new Site(SEARCH_GOOGLE, "Google",
                2, true, 2));
        site = new Site(SEARCH_LIBRARY_THING, "LibraryThing",
                3, true, 3);
        site.isbnOnly = true;
        mSearchOrderDefaults.add(site);
        mSearchOrderDefaults.add(new Site(SEARCH_ISFDB, "ISFDB",
                4, true, 4));


        mCoverSearchOrderDefaults.add(new Site(SEARCH_GOOGLE, "Google-cover",
                0, true));
        site = new Site(SEARCH_LIBRARY_THING, "LibraryThing-cover",
                1, true);
        site.isbnOnly = true;
        mCoverSearchOrderDefaults.add(site);
        mCoverSearchOrderDefaults.add(new Site(SEARCH_ISFDB, "ISFDB-cover",
                2, true));


        // we're going to use set(index,...), so make them big enough
        mPreferredSearchOrder = new ArrayList<>(mSearchOrderDefaults);
        mReliabilityOrder = new ArrayList<>(mSearchOrderDefaults);

        mPreferredCoverSearchOrder = new ArrayList<>(mCoverSearchOrderDefaults);

        for (Site searchSite : mSearchOrderDefaults) {
            searchSite.enabled = BookCatalogueApp.getBooleanPreference(TAG + "." + searchSite.name + ".enabled", searchSite.enabled);
            searchSite.order = BookCatalogueApp.getIntPreference(TAG + "." + searchSite.name + ".order", searchSite.order);
            searchSite.reliability = BookCatalogueApp.getIntPreference(TAG + "." + searchSite.name + ".reliability", searchSite.reliability);

            mReliabilityOrder.set(searchSite.reliability, searchSite);
            mPreferredSearchOrder.set(searchSite.order, searchSite);
        }

        for (Site searchSite : mCoverSearchOrderDefaults) {
            searchSite.enabled = BookCatalogueApp.getBooleanPreference(TAG + "." + searchSite.name + ".cover.enabled", searchSite.enabled);
            searchSite.order = BookCatalogueApp.getIntPreference(TAG + "." + searchSite.name + ".cover.order", searchSite.order);

            mPreferredCoverSearchOrder.set(searchSite.order, searchSite);
        }
    }

    @NonNull
    public static List<Site> getReliabilityOrder() {
        return mReliabilityOrder;
    }

    @NonNull
    public static ArrayList<Site> getSiteSearchOrder() {
        return mPreferredSearchOrder;
    }

    public static void setSearchOrder(final @NonNull ArrayList<Site> newList) {
        mPreferredSearchOrder = newList;
        SharedPreferences.Editor e = BookCatalogueApp.getSharedPreferences().edit();
        for (Site site : newList) {
            e.putInt(TAG + "." + site.name + ".reliability", site.reliability);
            e.putBoolean(TAG + "." + site.name + ".enabled", site.enabled);
            e.putInt(TAG + "." + site.name + ".order", site.order);
        }
        e.apply();
    }

    @NonNull
    public static ArrayList<Site> getSiteCoverSearchOrder() {
        return mPreferredCoverSearchOrder;
    }

    public static void setCoverSearchOrder(final @NonNull ArrayList<Site> newList) {
        mPreferredCoverSearchOrder = newList;
        SharedPreferences.Editor e = BookCatalogueApp.getSharedPreferences().edit();
        for (Site site : newList) {
            e.putBoolean(TAG + "." + site.name + ".cover.enabled", site.enabled);
            e.putInt(TAG + "." + site.name + ".cover.order", site.order);
        }
        e.apply();
    }

    public static class Site implements Parcelable {
        public final int id;
        final String name;

        public boolean enabled;
        int order;
        int reliability;
        boolean isbnOnly = false;

        @SuppressWarnings("SameParameterValue")
        Site(final int bit, final @NonNull String name, final int order, final boolean enabled) {
            this.id = bit;
            this.name = name;
            this.order = order;
            this.enabled = enabled;

            this.reliability = order;
        }

        @SuppressWarnings("SameParameterValue")
        Site(final int id, final @NonNull String name, final int order, final boolean enabled, final int reliability) {
            this.id = id;
            this.name = name;
            this.order = order;
            this.enabled = enabled;
            this.reliability = reliability;
        }

        Site(final @NonNull Parcel in) {
            id = in.readInt();
            name = in.readString();
            enabled = in.readByte() != 0;
            order = in.readInt();
            reliability = in.readInt();
            isbnOnly = in.readByte() != 0;
        }

        @Override
        public void writeToParcel(final @NonNull Parcel dest, final int flags) {
            dest.writeInt(id);
            dest.writeString(name);
            dest.writeByte((byte) (enabled ? 1 : 0));
            dest.writeInt(order);
            dest.writeInt(reliability);
            dest.writeByte((byte) (isbnOnly ? 1 : 0));
        }

        @Override
        public int describeContents() {
            return 0;
        }

        public static final Creator<Site> CREATOR = new Creator<Site>() {
            @Override
            public Site createFromParcel(final @NonNull Parcel in) {
                return new Site(in);
            }

            @Override
            public Site[] newArray(final int size) {
                return new Site[size];
            }
        };

        public SearchTask getTask(final @NonNull TaskManager manager) {
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
