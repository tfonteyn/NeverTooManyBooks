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
package com.hardbacknutter.nevertoomanybooks.booklist.prefs;

import android.content.SharedPreferences;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/**
 * None of the Preferences implement Parcelable but we *do* parcel the 'value'.
 * This does mean the class must be properly constructed with all other fields initialised,
 * before un-parceling the actual value.
 *
 * @param <T> type of the actual value we store.
 */
public interface PPref<T> {

    @NonNull
    String getKey();

    /**
     * for single updates; user preferences only.
     */
    void set(@Nullable T value);

    /**
     * for batch updates.
     * Can also be used for setting globals.
     */
    void set(@NonNull SharedPreferences.Editor ed,
             @NonNull T value);

    /**
     * <ol>Implementations should return in order:
     * <li>The user preference if set</li>
     * <li>The global preference if set</li>
     * <li>The default value as set at creation time of the preference Object.</li>
     * </ol>
     *
     * @return the value of the preference
     */
    @NonNull
    T get();
}
