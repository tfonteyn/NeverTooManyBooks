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

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/**
 * Value class holding progress data.
 */
public class ProgressMessage
        implements LiveDataEvent {

    public final int taskId;

    /**
     * The maximum position for the progressbar,
     * should be ignored if a mode change was requested with indeterminate.
     */
    public final int maxPosition;

    /**
     * Absolute position for the progressbar,
     * should be ignored if a mode change was requested with indeterminate.
     */
    public final int position;

    /** Optional text to display. */
    @Nullable
    public final String text;

    /** No-op if {@code null} otherwise change mode to the requested one. */
    @Nullable
    public final Boolean indeterminate;

    /** {@link LiveDataEvent}. */
    private boolean mHasBeenHandled;

    public ProgressMessage(final int taskId,
                           @Nullable final String text,
                           final int position,
                           final int maxPosition,
                           @Nullable final Boolean indeterminate) {
        this.taskId = taskId;
        this.text = text;

        this.indeterminate = indeterminate;
        this.maxPosition = maxPosition;
        this.position = position;
    }

    public ProgressMessage(final int taskId,
                           @Nullable final String text) {
        this.taskId = taskId;
        this.text = text;

        this.indeterminate = null;
        this.maxPosition = 0;
        this.position = 0;
    }

    @Override
    public boolean isNewEvent() {
        final boolean isNew = !mHasBeenHandled;
        mHasBeenHandled = true;
        return isNew;
    }

    @Override
    @NonNull
    public String toString() {
        return "ProgressMessage{"
               + "mHasBeenHandled=" + mHasBeenHandled
               + ", taskId=" + taskId
               + ", indeterminate=" + indeterminate
               + ", maxPosition=" + maxPosition
               + ", position=" + position
               + ", text=`" + text + '`'
               + '}';
    }
}
