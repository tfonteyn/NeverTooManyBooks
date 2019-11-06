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
import android.os.Parcel;
import android.os.Parcelable;

import androidx.annotation.NonNull;
import androidx.annotation.VisibleForTesting;
import androidx.preference.PreferenceManager;

import java.util.Locale;

/**
 * Represents a site we will search.
 * Acts as a container for the site's {@link SearchEngine}.
 * <p>
 * The class {@link SearchSites} defines and links up all actual sites and their search engines.
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
    @VisibleForTesting
    public static final String PREF_PREFIX = "search.site.";

    /** Internal ID, bitmask based, not stored in prefs. */
    @SearchSites.Id
    public final int id;

    /** Internal task(thread) name AND user-visible name AND key into prefs. */
    @NonNull
    private final String mName;

    /** user preference: enable/disable this site. */
    private boolean mEnabled = true;

    /** the class which implements the search engine for a specific site. */
    private SearchEngine mSearchEngine;


    /**
     * Constructor. Use static method instead.
     *
     * @param id   Internal ID, bitmask based
     * @param name user visible name
     */
    private Site(@SearchSites.Id final int id,
                 @NonNull final String name) {

        this.id = id;
        mName = name;
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
    }

    /**
     * Create the Site with whatever suitable default values.
     * If previously stored to SharedPreferences, the stored values will be used instead.
     *
     * @param context          Current context
     * @param id               Internal ID, bitmask based
     * @param enabledByDefault flag
     */
    static Site newSite(@NonNull final Context context,
                        @SearchSites.Id final int id,
                        final boolean loadPrefs,
                        final boolean enabledByDefault) {
        Site site = new Site(id, SearchSites.getName(id));
        if (loadPrefs) {
            site.loadFromPrefs(context);
        } else {
            site.setEnabled(enabledByDefault);
        }
        return site;
    }

    static Site newSite(@SearchSites.Id final int id) {
        return new Site(id, SearchSites.getName(id));
    }

    /**
     * Create the Site with whatever suitable default values.
     * If previously stored to SharedPreferences, the stored values will be used instead.
     *
     * @param context          Current context
     * @param id               Internal ID, bitmask based
     * @param enabledByDefault flag
     */
    static Site newCoverSite(@NonNull final Context context,
                             @SearchSites.Id final int id,
                             final boolean loadPrefs,
                             final boolean enabledByDefault) {

        // Reminder: the name is used for preferences... so the suffix here must be hardcoded.
        String name = SearchSites.getName(id) + "-Covers";
        Site site = new Site(id, name);
        if (loadPrefs) {
            site.loadFromPrefs(context);
        } else {
            site.setEnabled(enabledByDefault);
        }
        return site;
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

    private void loadFromPrefs(@NonNull final Context context) {
        String lcName = PREF_PREFIX + mName.toLowerCase(Locale.getDefault()) + '.';
        mEnabled = PreferenceManager.getDefaultSharedPreferences(context)
                                    .getBoolean(lcName + "enabled", mEnabled);
    }

    void saveToPrefs(@NonNull final SharedPreferences.Editor editor) {
        String lcName = PREF_PREFIX + mName.toLowerCase(Locale.getDefault()) + '.';
        editor.putBoolean(lcName + "enabled", mEnabled);
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
    }

    @NonNull
    Site getClone() {
        Parcel parcel = Parcel.obtain();
        writeToParcel(parcel, 0);
        parcel.setDataPosition(0);
        Site site = CREATOR.createFromParcel(parcel);
        parcel.recycle();
        return site;
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

    @Override
    @NonNull
    public String toString() {
        return "Site{"
               + "id=" + id
               + ", mName=`" + mName + '`'
               + ", mEnabled=" + mEnabled
               + ", mSearchEngine=" + mSearchEngine
               + '}';
    }
}
