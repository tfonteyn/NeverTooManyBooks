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
package com.hardbacknutter.nevertoomanybooks.tasks;

import androidx.lifecycle.LiveData;

/**
 * Prevent acting twice on a delivered {@link LiveData} event.
 * <p>
 * See <a href="https://medium.com/androiddevelopers/livedata-with-snackbar-navigation-and-other-events-the-singleliveevent-case-ac2622673150">
 * this Medium post</a>
 * <p>
 * Modified from the article: the client must call {@link #isNewEvent()},
 * so we can pass {@code null} as valid data.
 * <p>
 * Example implementation:
 * <pre>
 *     {@code
 *          private boolean mHasBeenHandled;
 *
 *          public boolean isNewEvent() {
 *              boolean isNew = !mHasBeenHandled;
 *              mHasBeenHandled = true;
 *              return isNew;
 *          }
 *     }
 * </pre>
 */
public interface LiveDataEvent {

    /**
     * Check if the data needs handling, or can be ignored.
     *
     * @return {@code true} if this is a new event that needs handling.
     * {@code false} if the client can choose not to handle it.
     */
    boolean isNewEvent();
}
