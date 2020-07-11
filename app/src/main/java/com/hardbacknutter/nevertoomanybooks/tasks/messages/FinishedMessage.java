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
package com.hardbacknutter.nevertoomanybooks.tasks.messages;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.hardbacknutter.nevertoomanybooks.viewmodels.LiveDataEvent;

/**
 * Value class holding Result data.
 */
public class FinishedMessage<Result>
        implements LiveDataEvent {

    /** {@link LiveDataEvent}. */
    private boolean mHasBeenHandled;

    public final int taskId;

    /**
     * The result object from the task.
     * It can be {@code null} regardless of the task implementation.
     */
    @Nullable
    public final Result result;

    public FinishedMessage(final int taskId,
                           @Nullable final Result result) {
        this.taskId = taskId;
        this.result = result;
    }

    @Override
    public boolean isNewEvent() {
        boolean isNew = !mHasBeenHandled;
        mHasBeenHandled = true;
        return isNew;
    }

    @Override
    @NonNull
    public String toString() {
        return "FinishedMessage{"
               + "mHasBeenHandled=" + mHasBeenHandled
               + ", taskId=" + taskId
               + ", result=" + result
               + '}';
    }
}
