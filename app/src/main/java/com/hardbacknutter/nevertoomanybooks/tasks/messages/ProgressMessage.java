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

/**
 * Value class holding progress data.
 */
public class ProgressMessage {

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
    @NonNull
    public String toString() {
        return "ProgressMessage{"
               + "taskId=" + taskId
               + ", indeterminate=" + indeterminate
               + ", maxPosition=" + maxPosition
               + ", position=" + position
               + ", text=" + text
               + '}';
    }
}
