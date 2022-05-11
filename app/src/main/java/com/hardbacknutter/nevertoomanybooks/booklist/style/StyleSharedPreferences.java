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
import java.util.Optional;
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

    /** Log tag. */
    private static final String TAG = "StyleSharedPreferences";

    /** Style unique name. */
    private static final String PK_STYLE_UUID = "style.booklist.uuid";

    @NonNull
    private final SharedPreferences globalPrefs;
    @NonNull
    private final SharedPreferences stylePrefs;

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

        globalPrefs = PreferenceManager.getDefaultSharedPreferences(context);

        if (uuid.isEmpty()) {
            // Doing this here is much easier then doing it each time access is needed.
            // The downside is that when the global settings are accessed,
            // and the desired setting is not preset, a *second* and useless read is done.
            stylePrefs = globalPrefs;

        } else {
            stylePrefs = context.getSharedPreferences(uuid, Context.MODE_PRIVATE);
            if (isPersistent) {
                final SharedPreferences.Editor editor = stylePrefs.edit();
                if (!stylePrefs.contains(PK_STYLE_UUID)) {
                    // Storing the uuid is not actually needed but handy to have for debug
                    editor.putString(PK_STYLE_UUID, uuid);
                }

                // remove obsolete entries; no attempt is made to preserve the setting
                editor.remove("style.booklist.preferred")
                      .remove("style.booklist.filter.read")
                      .remove("style.booklist.filter.signed")
                      .remove("style.booklist.filter.anthology")
                      .remove("style.booklist.filter.lending")
                      .remove("style.booklist.filter.isbn")
                      .remove("style.booklist.filter.editions")
                      .remove("style.booklist.filter.bookshelves")

                      .apply();
            }
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
            return convert(stringSet);
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
    public static int convert(@NonNull final Set<String> stringSet) {
        int bitmask = 0;
        for (final String s : stringSet) {
            bitmask |= Integer.parseInt(s);
        }
        return bitmask;
    }

    @NonNull
    public static Set<String> convert(final int value) {
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
        return stringSet;
    }

    @Override
    public void remove(@NonNull final String key) {
        stylePrefs.edit().remove(key).apply();
    }

    @Override
    public void setString(@NonNull final String key,
                          @Nullable final String value) {
        if (value == null) {
            stylePrefs.edit().remove(key).apply();
        } else {
            stylePrefs.edit().putString(key, value).apply();
        }
    }

    @Override
    @Nullable
    public String getNonGlobalString(@NonNull final String key) {
        return stylePrefs.getString(key, null);
    }

    @Override
    @Nullable
    public String getString(@NonNull final String key) {

        final String value = stylePrefs.getString(key, null);
        if (value != null && !value.isEmpty()) {
            return value;
        }

        return globalPrefs.getString(key, null);
    }

    @Override
    public void setBoolean(@NonNull final String key,
                           @Nullable final Boolean value) {
        if (value == null) {
            stylePrefs.edit().remove(key).apply();
        } else {
            stylePrefs.edit().putBoolean(key, value).apply();
        }
    }

    @Override
    @Nullable
    public Boolean getBoolean(@NonNull final String key) {
        // reminder: it's a primitive so we must test on contains first

        if (stylePrefs.contains(key)) {
            return stylePrefs.getBoolean(key, false);
        }

        if (globalPrefs.contains(key)) {
            return globalPrefs.getBoolean(key, false);
        }

        return null;
    }

    @Override
    public void setInt(@NonNull final String key,
                       @Nullable final Integer value) {
        if (value == null) {
            stylePrefs.edit().remove(key).apply();
        } else {
            stylePrefs.edit().putInt(key, value).apply();
        }
    }

    @Override
    @Nullable
    public Integer getInteger(@NonNull final String key) {
        // reminder: it's a primitive so we must test on contains first

        if (stylePrefs.contains(key)) {
            return stylePrefs.getInt(key, 0);
        }

        if (globalPrefs.contains(key)) {
            return globalPrefs.getInt(key, 0);
        }

        return null;
    }

    @Override
    public void setBitmask(@NonNull final String key,
                           @Nullable final Integer value) {
        if (value == null) {
            stylePrefs.edit().remove(key).apply();
        } else {
            if (value < 0) {
                throw new IllegalArgumentException(Integer.toBinaryString(value));
            }

            final Set<String> stringSet = convert(value);
            stylePrefs.edit().putStringSet(key, stringSet).apply();
        }
    }

    @Override
    @Nullable
    public Integer getBitmask(@NonNull final String key) {
        // an empty set is a valid result

        Set<String> stringSet = stylePrefs.getStringSet(key, null);
        if (stringSet != null) {
            return convert(stringSet);
        }

        stringSet = globalPrefs.getStringSet(key, null);
        if (stringSet != null) {
            return convert(stringSet);
        }

        return null;
    }

    @Override
    public void setStringedIntList(@NonNull final String key,
                                   @Nullable final List<Integer> value) {
        if (value == null || value.isEmpty()) {
            stylePrefs.edit().remove(key).apply();
        } else {
            stylePrefs.edit()
                      .putString(key, TextUtils.join(PIntList.DELIM, value))
                      .apply();
        }
    }

    @Override
    @NonNull
    public Optional<ArrayList<Integer>> getStringedIntList(@NonNull final String key) {
        // an empty list is a valid result

        String csvString = stylePrefs.getString(key, null);
        if (csvString != null && !csvString.isEmpty()) {
            return Optional.of(getIntListFromCsv(csvString));
        }

        csvString = globalPrefs.getString(key, null);
        if (csvString != null && !csvString.isEmpty()) {
            return Optional.of(getIntListFromCsv(csvString));
        }
        return Optional.empty();
    }
}
