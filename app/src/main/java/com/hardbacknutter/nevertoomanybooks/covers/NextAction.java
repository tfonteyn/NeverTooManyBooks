/*
 * @Copyright 2018-2025 HardBackNutter
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

package com.hardbacknutter.nevertoomanybooks.covers;

import android.content.Context;

import androidx.annotation.NonNull;

import com.hardbacknutter.nevertoomanybooks.core.utils.IntListPref;

/**
 * The next action after taking a picture.
 */
enum NextAction {
    /** Do nothing. */
    Done(0),
    /** Start the cropper fragment. */
    Crop(1),
    /** Start an editor. */
    Edit(2);

    private static final String PK_CAMERA_IMAGE_ACTION = "camera.image.action";

    private final int value;

    NextAction(final int value) {
        this.value = value;
    }

    /**
     * Get the user default action to take after taking a picture.
     *
     * @param context Current context
     *
     * @return next action
     */
    @NonNull
    static NextAction getAction(@NonNull final Context context) {

        final int value = IntListPref.getInt(context, PK_CAMERA_IMAGE_ACTION, Done.value);
        switch (value) {
            case 2:
                return Edit;
            case 1:
                return Crop;
            case 0:
            default:
                return Done;
        }
    }
}
