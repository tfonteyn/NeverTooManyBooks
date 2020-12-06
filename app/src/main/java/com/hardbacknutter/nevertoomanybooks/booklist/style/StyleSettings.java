/*
 * @Copyright 2020 HardBackNutter
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
import com.hardbacknutter.nevertoomanybooks.booklist.prefs.PCsvString;

public class StyleSettings {

    /**
     * Obsolete preference for this style being a preferred style. Now handled in the db.
     *
     * @deprecated will be removed soon.
     */
    @SuppressWarnings("DeprecatedIsStillUsed")
    @Deprecated
    private static final String OBSOLETE_PK_STYLE_BOOKLIST_PREFERRED = "style.booklist.preferred";

    private static final String TAG = "StyleSettings";

    @NonNull
    private final SharedPreferences mStylePrefs;

    StyleSettings(@NonNull final Context context,
                  @NonNull final String uuid) {
        if (!uuid.isEmpty()) {
            mStylePrefs = context.getSharedPreferences(uuid, Context.MODE_PRIVATE);
        } else {
            // Doing this here is much easier then doing it each time access is needed.
            // The downside is that when the global settings are accessed,
            // and the desired setting is not preset, a *second* and useless read is done.
            mStylePrefs = PreferenceManager.getDefaultSharedPreferences(context);
        }

        // remove obsolete entries; no attempt is made to preserve the setting
        if (mStylePrefs.contains(OBSOLETE_PK_STYLE_BOOKLIST_PREFERRED)) {
            mStylePrefs.edit().remove(OBSOLETE_PK_STYLE_BOOKLIST_PREFERRED).apply();
        }
    }


    /**
     * Remove the value for the given key.
     *
     * @param key preference key
     */
    public void remove(@NonNull final String key) {
        mStylePrefs.edit().remove(key).apply();
    }

    /**
     * Set or remove a String value.
     *
     * @param key   preference key
     * @param value to set
     */
    public void setString(@NonNull final String key,
                          @Nullable final String value) {
        if (value == null) {
            mStylePrefs.edit().remove(key).apply();
        } else {
            mStylePrefs.edit().putString(key, value).apply();
        }
    }

    /**
     * Get a String value for the given key.
     *
     * @param key preference key
     *
     * @return <ol>
     * <li>The user preference if set</li>
     * <li>{@code null}</li>
     * </ol>
     */
    @Nullable
    public String getString(@NonNull final String key) {
        return mStylePrefs.getString(key, null);
    }

    /**
     * Get a String value for the given key.
     *
     * @param context Current context
     * @param key     preference key
     *
     * @return <ol>
     * <li>The user preference if set</li>
     * <li>The global preference if set</li>
     * <li>{@code null}</li>
     * </ol>
     */
    @Nullable
    public String getString(@NonNull final Context context,
                            @NonNull final String key) {

        final String value = mStylePrefs.getString(key, null);
        if (value != null && !value.isEmpty()) {
            return value;
        }

        return PreferenceManager.getDefaultSharedPreferences(context)
                                .getString(key, null);
    }


    /**
     * Set or remove the boolean value for the given key.
     *
     * @param key   preference key
     * @param value to set
     */
    public void setBoolean(@NonNull final String key,
                           @Nullable final Boolean value) {
        if (value == null) {
            mStylePrefs.edit().remove(key).apply();
        } else {
            mStylePrefs.edit().putBoolean(key, value).apply();
        }
    }

    /**
     * Get a Boolean value for the given key.
     *
     * @param key preference key
     *
     * @return <ol>
     * <li>The user preference if set</li>
     * <li>{@code false}</li>
     * </ol>
     */
    public boolean getBoolean(@NonNull final String key) {
        return mStylePrefs.getBoolean(key, false);
    }

    /**
     * Get a Boolean value for the given key.
     *
     * @param context Current context
     * @param key     preference key
     *
     * @return <ol>
     * <li>The user preference if set</li>
     * <li>The global preference if set</li>
     * <li>{@code null}</li>
     * </ol>
     */
    @Nullable
    public Boolean getBoolean(@NonNull final Context context,
                              @NonNull final String key) {
        // reminder: it's a primitive so we must test on contains first

        if (mStylePrefs.contains(key)) {
            return mStylePrefs.getBoolean(key, false);
        }

        final SharedPreferences global = PreferenceManager
                .getDefaultSharedPreferences(context);
        if (global.contains(key)) {
            return global.getBoolean(key, false);
        }

        return null;
    }


    /**
     * Set or remove the int value for the given key.
     *
     * @param key   preference key
     * @param value to set
     */
    public void setInt(@NonNull final String key,
                       @Nullable final Integer value) {
        if (value == null) {
            mStylePrefs.edit().remove(key).apply();
        } else {
            mStylePrefs.edit().putInt(key, value).apply();
        }
    }

    /**
     * Get an Integer value for the given key.
     *
     * @param context Current context
     * @param key     preference key
     *
     * @return <ol>
     * <li>The user preference if set</li>
     * <li>The global preference if set</li>
     * <li>{@code null}</li>
     * </ol>
     */
    @Nullable
    public Integer getInteger(@NonNull final Context context,
                              @NonNull final String key) {
        // reminder: it's a primitive so we must test on contains first

        if (mStylePrefs.contains(key)) {
            return mStylePrefs.getInt(key, 0);
        }

        final SharedPreferences global = PreferenceManager
                .getDefaultSharedPreferences(context);
        if (global.contains(key)) {
            return global.getInt(key, 0);
        }

        return null;
    }


    /**
     * Set or remove the int value, <strong>which is stored as a String</strong>,
     * for the given key.
     *
     * @param key   preference key
     * @param value to set
     */
    public void setStringedInt(@NonNull final String key,
                               @Nullable final Integer value) {
        if (value == null) {
            mStylePrefs.edit().remove(key).apply();
        } else {
            mStylePrefs.edit().putString(key, String.valueOf(value)).apply();
        }
    }

    /**
     * Get an int value, <strong>which is stored as a String</strong>,
     * for the given key.
     *
     * @param context Current context
     * @param key     preference key
     *
     * @return <ol>
     * <li>The user preference if set</li>
     * <li>The global preference if set</li>
     * <li>{@code null}</li>
     * </ol>
     */
    @Nullable
    public Integer getStringedInt(@NonNull final Context context,
                                  @NonNull final String key) {
        // reminder: {@link androidx.preference.ListPreference} is stored as a String
        String value = mStylePrefs.getString(key, null);
        if (value != null && !value.isEmpty()) {
            return Integer.parseInt(value);
        }

        value = PreferenceManager.getDefaultSharedPreferences(context)
                                 .getString(key, null);
        if (value != null && !value.isEmpty()) {
            return Integer.parseInt(value);
        }
        return null;
    }


    /**
     * Set or remove a list of int values.
     *
     * @param key   preference key
     * @param value to set
     */
    public void setIntList(@NonNull final String key,
                           @Nullable final List<Integer> value) {
        if (value == null || value.isEmpty()) {
            mStylePrefs.edit().remove(key).apply();
        } else {
            mStylePrefs.edit().putString(key, TextUtils.join(PCsvString.DELIM, value)).apply();
        }
    }

    /**
     * Get a list of int values for the given key.
     *
     * @param context Current context
     * @param key     preference key
     *
     * @return <ol>
     * <li>The user preference if set</li>
     * <li>The global preference if set</li>
     * <li>{@code null}</li>
     * </ol>
     */
    @Nullable
    public List<Integer> getIntList(@NonNull final Context context,
                                    @NonNull final String key) {
        String csvString = mStylePrefs.getString(key, null);
        if (csvString != null && !csvString.isEmpty()) {
            return getIntListFromCsv(csvString);
        }

        csvString = PreferenceManager.getDefaultSharedPreferences(context)
                                     .getString(key, null);
        if (csvString != null && !csvString.isEmpty()) {
            return getIntListFromCsv(csvString);
        }
        return null;
    }

    /**
     * Split a CSV String into a List.
     *
     * @param csvString CSV string
     *
     * @return list
     */
    @NonNull
    private List<Integer> getIntListFromCsv(@NonNull final String csvString) {
        try {
            return Arrays.stream(csvString.split(PCsvString.DELIM))
                         .map(Integer::parseInt)
                         .collect(Collectors.toList());

        } catch (@NonNull final NumberFormatException e) {
            // should not happen unless we had a bug while previously writing the pref.
            if (BuildConfig.DEBUG /* always */) {
                Log.d(TAG, "values=`" + csvString + '`', e);
            }
            // in which case we bail out gracefully
            return new ArrayList<>();
        }
    }


    /**
     * Set or remove a bitmask (int) value.
     *
     * @param key   preference key
     * @param mask  valid values bitmask
     * @param value to set
     */
    public void setBitmask(@NonNull final String key,
                           @IntRange(from = 0, to = 0xFFFF) final int mask,
                           @Nullable final Integer value) {
        if (value == null) {
            mStylePrefs.edit().remove(key).apply();
        } else {
            mStylePrefs.edit().putStringSet(key, getBitmaskAsStringSet(mask & value)).apply();
        }
    }

    /**
     * Get a bitmask value for the given key.
     *
     * @param context Current context
     * @param key     preference key
     * @param mask    valid values bitmask
     *
     * @return <ol>
     * <li>The user preference if set</li>
     * <li>The global preference if set</li>
     * <li>{@code null}</li>
     * </ol>
     */
    @Nullable
    public Integer getBitmask(@NonNull final Context context,
                              @NonNull final String key,
                              @IntRange(from = 0, to = 0xFFFF) final int mask) {
        // an empty set is a valid result

        Set<String> stringSet = mStylePrefs.getStringSet(key, null);
        if (stringSet != null) {
            return getStringSetAsBitmask(mask, stringSet);
        }

        stringSet = PreferenceManager.getDefaultSharedPreferences(context)
                                     .getStringSet(key, null);
        if (stringSet != null) {
            return getStringSetAsBitmask(mask, stringSet);
        }

        return null;
    }


    @IntRange(from = 0, to = 0xFFFF)
    private int getStringSetAsBitmask(@IntRange(from = 0, to = 0xFFFF) final int mask,
                                      @NonNull final Set<String> stringSet) {
        int bitmask = 0;
        for (final String s : stringSet) {
            bitmask |= Integer.parseInt(s);
        }
        return bitmask & mask;
    }

    /**
     * Convert a bitmask to set.
     *
     * @param bitmask the value
     *
     * @return the set
     */
    @NonNull
    private Set<String> getBitmaskAsStringSet(
            @IntRange(from = 0, to = 0xFFFF) final int bitmask) {
        if (bitmask < 0) {
            throw new IllegalArgumentException(Integer.toBinaryString(bitmask));
        }

        final Set<String> stringSet = new HashSet<>();
        int tmp = bitmask;
        int bit = 1;
        while (tmp != 0) {
            if ((tmp & 1) == 1) {
                stringSet.add(String.valueOf(bit));
            }
            bit *= 2;
            // unsigned shift
            tmp = tmp >>> 1;
        }
        return stringSet;
    }
}
