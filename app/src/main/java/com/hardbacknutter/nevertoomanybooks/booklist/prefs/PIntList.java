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
package com.hardbacknutter.nevertoomanybooks.booklist.prefs;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Parcel;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.preference.PreferenceManager;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import com.hardbacknutter.nevertoomanybooks.BuildConfig;

/**
 * A {@code List<Integer>} is stored as a CSV String.
 * <p>
 * No equivalent Preference widget
 */
public class PIntList
        extends PCollectionBase<Integer, List<Integer>> {

    /** Log tag. */
    private static final String TAG = "PIntList";

    /**
     * Constructor.
     *
     * @param sp           Style preferences reference.
     * @param isPersistent {@code true} to persist the value, {@code false} for in-memory only.
     * @param key          key of preference
     */
    public PIntList(final SharedPreferences sp,
                    final boolean isPersistent,
                    @NonNull final String key) {
        super(sp, isPersistent, key, new ArrayList<>());
        mNonPersistedValue = new ArrayList<>();
    }

    @NonNull
    public List<Integer> getGlobalValue(@NonNull final Context context) {
        final String value = PreferenceManager.getDefaultSharedPreferences(context)
                                              .getString(getKey(), null);
        if (value == null || value.isEmpty()) {
            return mDefaultValue;
        }
        return getAsList(value);
    }

    /**
     * Set the <strong>value</strong> from the Parcel.
     *
     * @param in parcel to read from
     */
    @Override
    public void set(@NonNull final Parcel in) {
        final List<Integer> list = new ArrayList<>();
        in.readList(list, getClass().getClassLoader());
        this.set(list);
    }

    @NonNull
    @Override
    public List<Integer> getValue(@NonNull final Context context) {
        if (mIsPersistent) {
            // reminder: it's a CSV string
            final String value = mStylePrefs.getString(getKey(), null);
            if (value != null && !value.isEmpty()) {
                return getAsList(value);
            }
            return getGlobalValue(context);
        } else {
            return mNonPersistedValue != null ? mNonPersistedValue : mDefaultValue;
        }
    }

    /**
     * Split a CSV String into a List.
     *
     * @param values CSV string
     *
     * @return list
     */
    @NonNull
    private List<Integer> getAsList(@NonNull final String values) {
        try {
            return Arrays.stream(values.split(DELIM))
                         .map(Integer::parseInt)
                         .collect(Collectors.toList());

        } catch (@NonNull final NumberFormatException e) {
            // should not happen unless we had a bug while previously writing the pref.
            if (BuildConfig.DEBUG /* always */) {
                Log.d(TAG, "key=" + getKey() + "|values=`" + values + '`', e);
            }
            // in which case we bail out gracefully
            return new ArrayList<>();
        }
    }
}
