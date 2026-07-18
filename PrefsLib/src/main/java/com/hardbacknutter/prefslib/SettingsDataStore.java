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
 * <p>
 * By default, <strong>all</strong> methods will throw.
 * Implement those you are going to use.
 */
public interface SettingsDataStore {

    @Nullable
    default String getString(@NonNull final String key,
                             @Nullable final String defValue) {
        throw new UnsupportedOperationException("getString");
    }

    default void putString(@NonNull final String key,
                           @Nullable final String value) {
        throw new UnsupportedOperationException("putString");
    }

    @Nullable
    default Set<String> getStringSet(@NonNull final String key,
                                     @Nullable final Set<String> defValues) {
        throw new UnsupportedOperationException("getStringSet");
    }

    default void putStringSet(@NonNull final String key,
                              @Nullable final Set<String> values) {
        throw new UnsupportedOperationException("putStringSet");
    }

    default int getInt(@NonNull final String key,
                       @Nullable final Integer defValue) {
        throw new UnsupportedOperationException("getInt");
    }

    default void putInt(@NonNull final String key,
                        @Nullable final Integer value) {
        throw new UnsupportedOperationException("putInt");
    }

    default long getLong(@NonNull final String key,
                         @Nullable final Long defValue) {
        throw new UnsupportedOperationException("getLong");
    }

    default void putLong(@NonNull final String key,
                         @Nullable final Long value) {
        throw new UnsupportedOperationException("putLong");
    }

    default float getFloat(@NonNull final String key,
                           @Nullable final Float defValue) {
        throw new UnsupportedOperationException("getFloat");
    }

    default void putFloat(@NonNull final String key,
                          @Nullable final Float value) {
        throw new UnsupportedOperationException("putFloat");
    }

    default boolean getBoolean(@NonNull final String key,
                               @Nullable final Boolean defValue) {
        throw new UnsupportedOperationException("getBoolean");
    }

    default void putBoolean(@NonNull final String key,
                            @Nullable final Boolean value) {
        throw new UnsupportedOperationException("putBoolean");
    }
}
