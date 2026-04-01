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

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.Set;

/**
 * There is NO {@code remove} method. This is by design.
 * The equivalent is passing in {@code null} as the value.
 * Implementations should typically then remove such key/value pairs.
 */
public interface SettingsDataStore {

    @Nullable
    String getString(@NonNull String key,
                     @Nullable String defValue);

    void putString(@NonNull String key,
                   @Nullable String value);

    @Nullable
    Set<String> getStringSet(@NonNull String key,
                             @Nullable Set<String> defValues);

    void putStringSet(@NonNull String key,
                      @Nullable Set<String> values);

    int getInt(@NonNull String key,
               @Nullable Integer defValue);

    void putInt(@NonNull String key,
                @Nullable Integer value);

    long getLong(@NonNull String key,
                 @Nullable Long defValue);

    void putLong(@NonNull String key,
                 @Nullable Long value);

    float getFloat(@NonNull String key,
                   @Nullable Float defValue);

    void putFloat(@NonNull String key,
                  @Nullable Float value);

    boolean getBoolean(@NonNull String key,
                       @Nullable Boolean defValue);

    void putBoolean(@NonNull String key,
                    @Nullable Boolean value);
}
