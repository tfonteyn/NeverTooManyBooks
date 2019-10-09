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

import android.content.SharedPreferences;
import android.os.Parcel;
import android.os.Parcelable;

import androidx.annotation.NonNull;
import androidx.preference.PreferenceManager;

import java.util.Locale;

import com.hardbacknutter.nevertoomanybooks.App;

/**
 * All search engines are added here.
 */
public final class Site
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

    /** Internal ID, bitmask based, not stored in prefs. */
    @SearchSites.Id
    public final int id;

    /** Internal task(thread) name AND user-visible name AND key into prefs. */
    @NonNull
    private final String mName;

    /** user preference: enable/disable this site. */
    private boolean mEnabled;
    /** user preference: the priority/order the list of sites will be searched. */
    private int mPriority;
    /** for now hard-coded, but plumbing to have this as a user preference is done. */
    private int mReliability;

    /** the class which implements the search engine for a specific site. */
    private SearchEngine mSearchEngine;


    /**
     * Constructor. Use static method instead.
     *
     * @param id          Internal ID, bitmask based
     * @param name        user visible name
     * @param enabled     flag
     * @param priority    the search priority order
     * @param reliability the search reliability order
     */
    private Site(@SearchSites.Id final int id,
                 @NonNull final String name,
                 final boolean enabled,
                 final int priority,
                 final int reliability) {

        this.id = id;
        mName = name;
        mEnabled = enabled;
        mPriority = priority;
        mReliability = reliability;
        loadFromPrefs(PreferenceManager.getDefaultSharedPreferences(App.getAppContext()));
    }

    /**
     * {@link Parcelable} Constructor.
     * <p>
     * Reminder: this is IPC.. so don't load prefs!
     *
     * @param in Parcel to construct the object from
     */
    private Site(@NonNull final Parcel in) {
        id = in.readInt();
        mName = SearchSites.getName(id);
        mEnabled = in.readInt() != 0;
        mPriority = in.readInt();
        mReliability = in.readInt();
    }

    /**
     * Create the Site with whatever suitable default values.
     * If previously stored to SharedPreferences, the stored values will be used instead.
     *
     * @param id          Internal ID, bitmask based
     * @param enabled     flag
     * @param priority    the search priority order
     * @param reliability the search reliability order
     */
    static Site newSite(@SearchSites.Id final int id,
                        final boolean enabled,
                        final int priority,
                        final int reliability) {
        return new Site(id, SearchSites.getName(id), enabled, priority, reliability);
    }

    /**
     * Create the Site with whatever suitable default values.
     * If previously stored to SharedPreferences, the stored values will be used instead.
     *
     * @param id       Internal ID, bitmask based
     * @param enabled  flag
     * @param priority the search priority order
     */
    static Site newCoverSite(@SearchSites.Id final int id,
                             final boolean enabled,
                             final int priority) {

        // Reminder: the name is used for preferences... so the suffix here must be hardcoded.
        String name = SearchSites.getName(id) + "-Covers";
        // by default, reliability == order.
        return new Site(id, name, enabled, priority, priority);
    }

    /**
     * Get the {@link SearchEngine} instance for this site.
     *
     * @return {@link SearchEngine}
     */
    public SearchEngine getSearchEngine() {
        if (mSearchEngine == null) {
            mSearchEngine = SearchSites.getSearchEngine(id);
        }

        return mSearchEngine;
    }

    private void loadFromPrefs(@NonNull final SharedPreferences prefs) {
        String lcName = PREF_PREFIX + mName.toLowerCase(Locale.getDefault()) + '.';
        mEnabled = prefs.getBoolean(lcName + "enabled", mEnabled);
        mPriority = prefs.getInt(lcName + "order", mPriority);
        mReliability = prefs.getInt(lcName + "reliability", mReliability);
    }

    void saveToPrefs(@NonNull final SharedPreferences.Editor editor) {
        String lcName = PREF_PREFIX + mName.toLowerCase(Locale.getDefault()) + '.';
        editor.putBoolean(lcName + "enabled", mEnabled);
        editor.putInt(lcName + "order", mPriority);
        editor.putInt(lcName + "reliability", mReliability);
    }

    @SuppressWarnings("SameReturnValue")
    @Override
    public int describeContents() {
        return 0;
    }

    /**
     * Reminder: this is IPC.. so don't save prefs.
     */
    @Override
    public void writeToParcel(@NonNull final Parcel dest,
                              final int flags) {
        dest.writeInt(id);
        dest.writeInt(mEnabled ? 1 : 0);
        dest.writeInt(mPriority);
        dest.writeInt(mReliability);
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
