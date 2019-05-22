package com.eleybourn.bookcatalogue.searches;

import android.content.SharedPreferences;
import android.os.Parcel;
import android.os.Parcelable;

import androidx.annotation.NonNull;

import com.eleybourn.bookcatalogue.App;

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
    private SearchEngine mSearchEngine;


    /** Constructor. Use static method instead. */
    private Site(final int id,
                 @NonNull final String name,
                 final int priority,
                 final int reliability) {

        this.id = id;
        mName = name;
        mPriority = priority;
        mReliability = reliability;

        loadFromPrefs();
    }

    /**
     * Reminder: this is IPC.. so don't load prefs!
     */
    Site(@NonNull final Parcel in) {
        id = in.readInt();
        mName = SearchSites.getName(id);
        mEnabled = in.readByte() != 0;
        mPriority = in.readInt();
        mReliability = in.readInt();
    }

    /**
     * Create the Site with whatever suitable default values.
     * If previously stored to SharedPreferences, the stored values will be used instead.
     *
     * @param id          Internal id, bitmask based
     * @param priority    the search priority order
     * @param reliability the search reliability order
     */
    static Site newSite(final int id,
                        final int priority,
                        final int reliability) {
        return new Site(id, SearchSites.getName(id), priority, reliability);
    }

    /**
     * Create the Site with whatever suitable default values.
     * If previously stored to SharedPreferences, the stored values will be used instead.
     *
     * @param id       Internal id, bitmask based
     * @param priority the search priority order
     */
    static Site newCoverSite(final int id,
                             final int priority) {
        // by default, reliability == order.
        return new Site(id, SearchSites.getName(id) + "-covers", priority, priority);
    }

    /**
     * @return the manager class instance.
     */
    public SearchEngine getSearchEngine() {
        if (mSearchEngine == null) {
            mSearchEngine = SearchSites.getSearchEngine(id);
        }

        return mSearchEngine;
    }

    /**
     * Reminder: this is IPC.. so don't save prefs.
     */
    @Override
    public void writeToParcel(@NonNull final Parcel dest,
                              final int flags) {
        dest.writeInt(id);
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

    public void setEnabled(final boolean enabled) {
        mEnabled = enabled;
    }

    int getPriority() {
        return mPriority;
    }

    public void setPriority(final int priority) {
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
                + ", mSearchEngine=" + mSearchEngine
                + '}';
    }
}
