/*
 * @Copyright 2020 HardBackNutter
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

import java.util.Locale;

import com.hardbacknutter.nevertoomanybooks.utils.exceptions.UnexpectedValueException;

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
    private static final String PREF_PREFIX = "search.site.";

    /** Name suffix for Cover websites. */
    private static final String PREF_SUFFIX_COVERS = "Covers";
    /** Name suffix for Alternative Editions websites. */
    private static final String PREF_SUFFIX_ALT_ED = "AltEd";
    private static final String ENABLED = "enabled";

    /** Internal ID, bitmask based, not stored in prefs. */
    @SearchSites.Id
    public final int id;

    /** User-visible name. */
    @NonNull
    private final String mName;
    /** key into prefs. */
    @NonNull
    private final String mPreferenceKey;

    /** user preference: enable/disable this site. */
    private boolean mEnabled;

    /** the class which implements the search engine for a specific site. */
    private SearchEngine mSearchEngine;

    /**
     * Constructor. Use {@link #createDataSite(int)}.
     *
     * @param id Internal ID, bitmask based
     */
    private Site(@SearchSites.Id final int id) {
        this(id, SiteList.Type.Data, true);
    }

    /**
     * Constructor. Use {@link #createSite(int, SiteList.Type, boolean)}.
     *
     * @param id      Internal ID, bitmask based
     * @param type    the list type this site will belong to
     * @param enabled flag
     */
    private Site(@SearchSites.Id final int id,
                 @NonNull final SiteList.Type type,
                 final boolean enabled) {
        this.id = id;
        mEnabled = enabled;
        mName = SearchSites.getName(id);

        switch (type) {
            case Data:
                mPreferenceKey = mName;
                break;
            case Covers:
                mPreferenceKey = mName + "-" + PREF_SUFFIX_COVERS;
                break;
            case AltEditions:
                mPreferenceKey = mName + "-" + PREF_SUFFIX_ALT_ED;
                break;

            default:
                throw new UnexpectedValueException(type);
        }
    }

    /**
     * Copy constructor.
     *
     * @param from object to copy
     */
    Site(@NonNull final Site from) {
        id = from.id;
        mEnabled = from.mEnabled;
        mName = from.mName;
        mPreferenceKey = from.mPreferenceKey;
        // just copy the reference, it's a singleton.
        mSearchEngine = from.mSearchEngine;
    }

    /**
     * {@link Parcelable} Constructor.
     *
     * @param in Parcel to construct the object from
     */
    private Site(@NonNull final Parcel in) {
        id = in.readInt();
        mEnabled = in.readInt() != 0;
        //noinspection ConstantConditions
        mName = in.readString();
        //noinspection ConstantConditions
        mPreferenceKey = in.readString();
        // Reminder: this is IPC.. so don't load prefs!
    }

    /**
     * Create an enabled Data site for temporary usage.
     *
     * @param id Internal ID, bitmask based
     *
     * @return instance
     */
    public static Site createDataSite(@SearchSites.Id final int id) {
        return new Site(id);
    }

    /**
     * Create a persistent Site.
     *
     * @param id      Internal ID, bitmask based
     * @param type    the list type this site will belong to
     * @param enabled flag
     *
     * @return instance
     */
    static Site createSite(@SearchSites.Id final int id,
                           @NonNull final SiteList.Type type,
                           final boolean enabled) {
        return new Site(id, type, enabled);
    }

    /**
     * Get the {@link SearchEngine} instance for this site.
     *
     * @return (cached) instance
     */
    public SearchEngine getSearchEngine() {
        if (mSearchEngine == null) {
            mSearchEngine = SearchSites.getSearchEngine(id);
        }

        return mSearchEngine;
    }

    /**
     * Get the current/standard Locale for this Site.
     *
     * @param context Current context
     *
     * @return site locale
     */
    @NonNull
    public Locale getLocale(@NonNull final Context context) {
        return getSearchEngine().getLocale(context);
    }

    void loadFromPrefs(@NonNull final SharedPreferences preferences,
                       @NonNull final Locale systemLocale) {
        String lcName = PREF_PREFIX + mPreferenceKey.toLowerCase(systemLocale) + '.';
        mEnabled = preferences.getBoolean(lcName + ENABLED, mEnabled);
    }

    void saveToPrefs(@NonNull final SharedPreferences.Editor editor,
                     @NonNull final Locale systemLocale) {
        String lcName = PREF_PREFIX + mPreferenceKey.toLowerCase(systemLocale) + '.';
        editor.putBoolean(lcName + ENABLED, mEnabled);
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
        dest.writeString(mName);
        dest.writeString(mPreferenceKey);
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
               + ", mPreferenceKey=`" + mPreferenceKey + '`'
               + ", mEnabled=" + mEnabled
               + ", mSearchEngine=" + mSearchEngine
               + '}';
    }
}
