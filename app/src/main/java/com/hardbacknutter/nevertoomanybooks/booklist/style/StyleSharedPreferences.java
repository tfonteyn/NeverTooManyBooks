/*
 * @Copyright 2018-2021 HardBackNutter
 * @License GNU General Public License
 *
 * This file is part of NeverTooManyBooks.
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
package com.hardbacknutter.nevertoomanybooks.booklist.style;

import android.content.Context;
import android.content.SharedPreferences;
import android.text.TextUtils;
import android.util.Log;

import androidx.annotation.IntRange;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.preference.PreferenceManager;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import com.hardbacknutter.nevertoomanybooks.BuildConfig;
import com.hardbacknutter.nevertoomanybooks.ServiceLocator;
import com.hardbacknutter.nevertoomanybooks.booklist.style.prefs.PIntList;

/**
 * Encapsulate the SharedPreferences access for styles.
 */
public class StyleSharedPreferences
        implements StylePersistenceLayer {

    /**
     * Obsolete preference for this style being a preferred style. Now handled in the database.
     *
     * @deprecated will be removed soon.
     */
    @Deprecated
    private static final String OBSOLETE_PK_STYLE_BOOKLIST_PREFERRED = "style.booklist.preferred";

    /** Log tag. */
    private static final String TAG = "StyleSharedPreferences";

    /** Style unique name. */
    private static final String PK_STYLE_UUID = "style.booklist.uuid";

    @NonNull
    private final SharedPreferences mGlobalPrefs;
    @NonNull
    private final SharedPreferences mStylePrefs;

    /**
     * Constructor.
     *
     * @param context      Current context
     * @param uuid         of the style; use {@code ""} for the global style settings
     *                     (i.e. their default settings)
     * @param isPersistent flag
     */
    StyleSharedPreferences(@NonNull final Context context,
                           @NonNull final String uuid,
                           final boolean isPersistent) {

        mGlobalPrefs = PreferenceManager.getDefaultSharedPreferences(context);

        if (uuid.isEmpty()) {
            // Doing this here is much easier then doing it each time access is needed.
            // The downside is that when the global settings are accessed,
            // and the desired setting is not preset, a *second* and useless read is done.
            mStylePrefs = mGlobalPrefs;

        } else {
            mStylePrefs = context.getSharedPreferences(uuid, Context.MODE_PRIVATE);
            if (isPersistent && !mStylePrefs.contains(PK_STYLE_UUID)) {
                // Storing the uuid is not actually needed but handy to have for debug
                mStylePrefs.edit().putString(PK_STYLE_UUID, uuid).apply();
            }
        }

        // remove obsolete entries; no attempt is made to preserve the setting
        if (mStylePrefs.contains(OBSOLETE_PK_STYLE_BOOKLIST_PREFERRED)) {
            mStylePrefs.edit().remove(OBSOLETE_PK_STYLE_BOOKLIST_PREFERRED).apply();
        }
    }

    public static int getBitmaskPref(@NonNull final SharedPreferences preferences,
                                     @NonNull final String key,
                                     final int defValue) {
        // an empty set is a valid result
        final Set<String> stringSet = preferences.getStringSet(key, null);
        if (stringSet == null || stringSet.isEmpty()) {
            return defValue;
        }

        // we should never have an invalid setting in the prefs... flw
        try {
            return getStringSetAsBitmask(stringSet);
        } catch (@NonNull final NumberFormatException ignore) {
            return defValue;
        }
    }

    public static int getBitmaskPref(@NonNull final String key,
                                     final int defValue) {
        return getBitmaskPref(ServiceLocator.getGlobalPreferences(), key, defValue);
    }

    /**
     * Parse and combine the values in the set into a bitmask.
     *
     * @param stringSet to parse
     *
     * @return bitmask
     *
     * @throws NumberFormatException is for some reason the stored value cannot be parsed
     */
    @IntRange(from = 0, to = 0xFFFF)
    public static int getStringSetAsBitmask(@NonNull final Set<String> stringSet) {
        int bitmask = 0;
        for (final String s : stringSet) {
            bitmask |= Integer.parseInt(s);
        }
        return bitmask;
    }

    @NonNull
    private static ArrayList<Integer> getStringSetAsIntList(@NonNull final Set<String> stringSet) {
        try {
            return stringSet.stream()
                            .map(Integer::parseInt)
                            .collect(Collectors.toCollection(ArrayList::new));
        } catch (@NonNull final NumberFormatException e) {
            // should not happen unless we had a bug while previously writing the pref.
            if (BuildConfig.DEBUG /* always */) {
                Log.d(TAG, "values=`" + stringSet + '`', e);
            }
            // in which case we bail out gracefully
            return new ArrayList<>();
        }
    }

    /**
     * Split a CSV String into a List.
     *
     * @param csvString CSV string
     *
     * @return list
     */
    @NonNull
    private static ArrayList<Integer> getIntListFromCsv(@NonNull final String csvString) {
        try {
            return Arrays.stream(csvString.split(PIntList.DELIM))
                         .map(Integer::parseInt)
                         .collect(Collectors.toCollection(ArrayList::new));

        } catch (@NonNull final NumberFormatException e) {
            // should not happen unless we had a bug while previously writing the pref.
            if (BuildConfig.DEBUG /* always */) {
                Log.d(TAG, "values=`" + csvString + '`', e);
            }
            // in which case we bail out gracefully
            return new ArrayList<>();
        }
    }

    @Override
    public void remove(@NonNull final String key) {
        mStylePrefs.edit().remove(key).apply();
    }

    @Override
    public void setString(@NonNull final String key,
                          @Nullable final String value) {
        if (value == null) {
            mStylePrefs.edit().remove(key).apply();
        } else {
            mStylePrefs.edit().putString(key, value).apply();
        }
    }

    @Override
    @Nullable
    public String getNonGlobalString(@NonNull final String key) {
        return mStylePrefs.getString(key, null);
    }

    @Override
    @Nullable
    public String getString(@NonNull final String key) {

        final String value = mStylePrefs.getString(key, null);
        if (value != null && !value.isEmpty()) {
            return value;
        }

        return mGlobalPrefs.getString(key, null);
    }

    @Override
    public void setBoolean(@NonNull final String key,
                           @Nullable final Boolean value) {
        if (value == null) {
            mStylePrefs.edit().remove(key).apply();
        } else {
            mStylePrefs.edit().putBoolean(key, value).apply();
        }
    }

    @Override
    public boolean getNonGlobalBoolean(@NonNull final String key) {
        return mStylePrefs.getBoolean(key, false);
    }

    @Override
    @Nullable
    public Boolean getBoolean(@NonNull final String key) {
        // reminder: it's a primitive so we must test on contains first

        if (mStylePrefs.contains(key)) {
            return mStylePrefs.getBoolean(key, false);
        }

        if (mGlobalPrefs.contains(key)) {
            return mGlobalPrefs.getBoolean(key, false);
        }

        return null;
    }

    @Override
    public void setInt(@NonNull final String key,
                       @Nullable final Integer value) {
        if (value == null) {
            mStylePrefs.edit().remove(key).apply();
        } else {
            mStylePrefs.edit().putInt(key, value).apply();
        }
    }

    @Override
    @Nullable
    public Integer getInteger(@NonNull final String key) {
        // reminder: it's a primitive so we must test on contains first

        if (mStylePrefs.contains(key)) {
            return mStylePrefs.getInt(key, 0);
        }

        if (mGlobalPrefs.contains(key)) {
            return mGlobalPrefs.getInt(key, 0);
        }

        return null;
    }

    @Override
    public void setBitmask(@NonNull final String key,
                           @Nullable final Integer value) {
        if (value == null) {
            mStylePrefs.edit().remove(key).apply();
        } else {
            if (value < 0) {
                throw new IllegalArgumentException(Integer.toBinaryString(value));
            }

            final Set<String> stringSet = new HashSet<>();
            int tmp = value;
            int bit = 1;
            while (tmp != 0) {
                if ((tmp & 1) == 1) {
                    stringSet.add(String.valueOf(bit));
                }
                bit *= 2;
                // unsigned shift
                tmp = tmp >>> 1;
            }
            mStylePrefs.edit().putStringSet(key, stringSet).apply();
        }
    }

    @Override
    @Nullable
    public Integer getBitmask(@NonNull final String key) {
        // an empty set is a valid result

        Set<String> stringSet = mStylePrefs.getStringSet(key, null);
        if (stringSet != null) {
            return getStringSetAsBitmask(stringSet);
        }

        stringSet = mGlobalPrefs.getStringSet(key, null);
        if (stringSet != null) {
            return getStringSetAsBitmask(stringSet);
        }

        return null;
    }

    @Override
    public void setStringedInt(@NonNull final String key,
                               @Nullable final Integer value) {
        if (value == null) {
            mStylePrefs.edit().remove(key).apply();
        } else {
            mStylePrefs.edit().putString(key, String.valueOf(value)).apply();
        }
    }

    @Override
    @Nullable
    public Integer getStringedInt(@NonNull final String key) {
        // reminder: {@link androidx.preference.ListPreference} is stored as a String
        String value = mStylePrefs.getString(key, null);
        if (value != null && !value.isEmpty()) {
            return Integer.parseInt(value);
        }

        value = mGlobalPrefs.getString(key, null);
        if (value != null && !value.isEmpty()) {
            return Integer.parseInt(value);
        }
        return null;
    }

    @Override
    public void setStringedIntList(@NonNull final String key,
                                   @Nullable final List<Integer> value) {
        if (value == null || value.isEmpty()) {
            mStylePrefs.edit().remove(key).apply();
        } else {
            mStylePrefs.edit()
                       .putString(key, TextUtils.join(PIntList.DELIM, value))
                       .apply();
        }
    }

    @Override
    @Nullable
    public ArrayList<Integer> getStringedIntList(@NonNull final String key) {
        String csvString = mStylePrefs.getString(key, null);
        if (csvString != null && !csvString.isEmpty()) {
            return getIntListFromCsv(csvString);
        }

        csvString = mGlobalPrefs.getString(key, null);
        if (csvString != null && !csvString.isEmpty()) {
            return getIntListFromCsv(csvString);
        }
        return null;
    }

    @Override
    public void setIntList(@NonNull final String key,
                           @Nullable final List<Integer> value) {
        if (value == null) {
            mStylePrefs.edit().remove(key).apply();
        } else {
            mStylePrefs.edit()
                       .putStringSet(key, value.stream()
                                               .map(String::valueOf)
                                               .collect(Collectors.toSet()))
                       .apply();
        }
    }

    @Override
    @Nullable
    public ArrayList<Integer> getIntList(@NonNull final String key) {
        // an empty set is a valid result

        Set<String> stringSet = mStylePrefs.getStringSet(key, null);
        if (stringSet != null) {
            return getStringSetAsIntList(stringSet);
        }

        stringSet = mGlobalPrefs.getStringSet(key, null);
        if (stringSet != null) {
            return getStringSetAsIntList(stringSet);
        }

        return null;
    }
}
