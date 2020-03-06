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
package com.hardbacknutter.nevertoomanybooks.booklist.prefs;

import android.content.Context;
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
     * For single updates. Not usable for globals.
     * <ul>
     *     <li>non-persistent preferences</li>
     *     <li>user preferences: implementations should redirect
     *     to {@link #set(SharedPreferences.Editor, Object)}</li>
     * </ul>
     */
    void set(@Nullable T value);

    /**
     * for batch updates. Not usable for non-persistent.
     * <ul>
     *     <li>user preferences</li>
     *     <li>global preferences</li>
     * </ul>
     */
    void set(@NonNull SharedPreferences.Editor ed,
             @NonNull T value);

    /**
     * Implementations should return in order below.
     * <ol>
     * <li>The user preference if set</li>
     * <li>The global preference if set</li>
     * <li>The default value as set at creation time of the preference Object.</li>
     * </ol>
     *
     * @param context Current context
     *
     * @return the value of the preference
     */
    @NonNull
    T getValue(@NonNull final Context context);

    /**
     * Implementations should return in order below.
     * <ol>
     * <li>The global preference if set</li>
     * <li>The default value as set at creation time of the preference Object.</li>
     * </ol>
     *
     * @param context Current context
     *
     * @return the value of the preference
     */
    @NonNull
    T getGlobalValue(@NonNull final Context context);
}
