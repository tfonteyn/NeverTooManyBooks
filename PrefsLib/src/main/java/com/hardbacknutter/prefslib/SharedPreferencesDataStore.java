/*
 * @Copyright 2018-2026 HardBackNutter
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

package com.hardbacknutter.prefslib;

import android.content.SharedPreferences;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.Set;

/**
 * A simple/default {@link SettingsDataStore} which wraps {@link SharedPreferences}.
 */
public class SharedPreferencesDataStore
        implements SettingsDataStore {

    @NonNull
    private final SharedPreferences p;

    /**
     * Constructor.
     *
     * @param p SharedPreferences to be wrapped
     */
    public SharedPreferencesDataStore(@NonNull final SharedPreferences p) {
        this.p = p;
    }

    @Nullable
    @Override
    public String getString(@NonNull final String key,
                            @Nullable final String defValue) {
        return p.getString(key, defValue);
    }

    @Override
    public void putString(@NonNull final String key,
                          @Nullable final String value) {
        if (value == null) {
            p.edit().remove(key).apply();
        } else {
            p.edit().putString(key, value).apply();
        }
    }

    @Nullable
    @Override
    public Set<String> getStringSet(@NonNull final String key,
                                    @Nullable final Set<String> defValues) {
        return p.getStringSet(key, defValues);
    }

    @Override
    public void putStringSet(@NonNull final String key,
                             @Nullable final Set<String> values) {
        if (values == null) {
            p.edit().remove(key).apply();
        } else {
            p.edit().putStringSet(key, values).apply();
        }
    }

    @Override
    public int getInt(@NonNull final String key,
                      @Nullable final Integer defValue) {
        return p.getInt(key, defValue != null ? defValue : 0);
    }

    @Override
    public void putInt(@NonNull final String key,
                       @Nullable final Integer value) {
        if (value == null) {
            p.edit().remove(key).apply();
        } else {
            p.edit().putInt(key, value).apply();
        }
    }

    @Override
    public long getLong(@NonNull final String key,
                        @Nullable final Long defValue) {
        return p.getLong(key, defValue != null ? defValue : 0);
    }

    @Override
    public void putLong(@NonNull final String key,
                        @Nullable final Long value) {
        if (value == null) {
            p.edit().remove(key).apply();
        } else {
            p.edit().putLong(key, value).apply();
        }
    }

    @Override
    public float getFloat(@NonNull final String key,
                          @Nullable final Float defValue) {
        return p.getFloat(key, defValue != null ? defValue : 0);
    }

    @Override
    public void putFloat(@NonNull final String key,
                         @Nullable final Float value) {
        if (value == null) {
            p.edit().remove(key).apply();
        } else {
            p.edit().putFloat(key, value).apply();
        }
    }

    @Override
    public boolean getBoolean(@NonNull final String key,
                              @Nullable final Boolean defValue) {
        return p.getBoolean(key, defValue != null && defValue);
    }

    @Override
    public void putBoolean(@NonNull final String key,
                           @Nullable final Boolean value) {
        if (value == null) {
            p.edit().remove(key).apply();
        } else {
            p.edit().putBoolean(key, value).apply();
        }
    }
}
