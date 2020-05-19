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
package com.hardbacknutter.nevertoomanybooks.viewmodels;

import androidx.annotation.Nullable;

/**
 * Used as a wrapper for data that is exposed via a LiveData that represents an event.
 * <p>
 * See <a href="https://medium.com/androiddevelopers/livedata-with-snackbar-navigation-and-other-events-the-singleliveevent-case-ac2622673150">
 * this Medium post</a>
 * <p>
 * Modified from the article: the client must call {@link #needsHandling()},
 * and we passing {@code null} as data is valid.
 */
public class LiveDataEvent<T> {

    @Nullable
    private final T mData;
    private boolean mHasBeenHandled;

    public LiveDataEvent(@Nullable final T data) {
        mData = data;
    }

    /**
     * Check if the data needs handling, or should be ignored.
     *
     * @return flag
     */
    public boolean needsHandling() {
        return !mHasBeenHandled;
    }

    /**
     * Return the encapsulated data.
     *
     * @return data
     */
    @Nullable
    public T getData() {
        mHasBeenHandled = true;
        return mData;
    }
}
