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
package com.hardbacknutter.nevertoomanybooks.settings;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;

import androidx.annotation.NonNull;
import androidx.preference.PreferenceManager;

/**
 * Created to make testing a little easier.
 */
public class SettingsHelper {

    @NonNull
    private final Context mContext;

    /**
     * Constructor.
     *
     * @param context Current context. Will be cached in this object.
     */
    public SettingsHelper(@NonNull final Context context) {
        mContext = context;
    }

    @NonNull
    public Context getContext() {
        return mContext;
    }

    @SuppressWarnings("unused")
    @NonNull
    public SharedPreferences getSharedPreferences() {
        return PreferenceManager.getDefaultSharedPreferences(mContext);
    }

    /**
     * Read a string from the META tags in the Manifest.
     *
     * @param key The name of the meta data string to retrieve.
     *
     * @return the key, or the empty string if no key found.
     */
    @NonNull
    public String getManifestString(@NonNull final String key) {
        ApplicationInfo ai;
        try {
            ai = mContext.getPackageManager().getApplicationInfo(mContext.getPackageName(),
                                                                 PackageManager.GET_META_DATA);
        } catch (@NonNull final PackageManager.NameNotFoundException e) {
            throw new IllegalStateException(e);
        }

        String result = ai.metaData.getString(key);
        if (result == null) {
            return "";
        }
        return result.trim();
    }
}
