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
package com.hardbacknutter.nevertoomanybooks.booklist.filters;

import android.content.Context;
import android.os.Parcel;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

public interface Filter<T> {

    /**
     * A Filter must implement this method. All others are optional.
     *
     * @return filter SQL expression, or {@code null} if not active.
     */
    @Nullable
    String getExpression();

    /**
     * Get a human readable label/name for this filter.
     *
     * @param context Current context
     *
     * @return a human readable label/name for this filter, or {@code null} if none.
     */
    @Nullable
    default String getLabel(@NonNull final Context context) {
        return null;
    }

    /**
     * Allow an implementation to override the definition of being active.
     *
     * @return {@code true} if this filter is active.
     */
    default boolean isActive() {
        return getExpression() != null;
    }

    default String getKey() {
        throw new UnsupportedOperationException();
    }

    default T get() {
        throw new UnsupportedOperationException();
    }

    /**
     * Set the <strong>value</strong> from the Parcel.
     *
     * @param in parcel to read from
     */
    default void set(@NonNull final Parcel in) {
        // do nothing
    }

    /**
     * Write the <strong>value</strong> to the Parcel.
     *
     * @param dest parcel to write to
     */
    default void writeToParcel(@NonNull final Parcel dest) {
        // do nothing
    }
}
