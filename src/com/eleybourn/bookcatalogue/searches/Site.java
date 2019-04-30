package com.eleybourn.bookcatalogue.searches;

import android.content.SharedPreferences;
import android.os.Parcel;
import android.os.Parcelable;

import androidx.annotation.NonNull;

import com.eleybourn.bookcatalogue.App;
import com.eleybourn.bookcatalogue.searches.amazon.AmazonManager;
import com.eleybourn.bookcatalogue.searches.goodreads.GoodreadsManager;
import com.eleybourn.bookcatalogue.searches.googlebooks.GoogleBooksManager;
import com.eleybourn.bookcatalogue.searches.isfdb.ISFDBManager;
import com.eleybourn.bookcatalogue.searches.librarything.LibraryThingManager;
import com.eleybourn.bookcatalogue.searches.openlibrary.OpenLibraryManager;
import com.eleybourn.bookcatalogue.utils.IllegalTypeException;

/**
 * All search engines are added here.
 */
public class Site
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


    /** Preferences prefix. */
    private static final String PREF_PREFIX = "SearchSite.";

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
    @SuppressWarnings("SameParameterValue")
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
