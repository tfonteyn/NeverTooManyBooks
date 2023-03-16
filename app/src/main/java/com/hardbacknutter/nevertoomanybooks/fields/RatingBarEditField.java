/*
 * @Copyright 2018-2023 HardBackNutter
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
package com.hardbacknutter.nevertoomanybooks.fields;

import android.view.View;

import androidx.annotation.IdRes;
import androidx.annotation.NonNull;

import com.hardbacknutter.nevertoomanybooks.datamanager.DataManager;

/**
 * RatingBar.
 * <p>
 * A {@code null} value is always handled as {@code 0}.
 */
public class RatingBarEditField
        extends RatingBarField {

    /**
     * Constructor.
     *
     * @param fragmentId  the hosting {@link FragmentId} for this {@link Field}
     * @param fieldViewId the view id for this {@link Field}
     * @param fieldKey    Key used to access a {@link DataManager}
     *                    Set to {@code ""} to suppress all access.
     */
    public RatingBarEditField(@NonNull final FragmentId fragmentId,
                              @IdRes final int fieldViewId,
                              @NonNull final String fieldKey) {
        super(fragmentId, fieldViewId, fieldKey);
    }

    @Override
    public void setParentView(@NonNull final View parent) {
        super.setParentView(parent);
        requireView().setOnRatingBarChangeListener((ratingBar, rating, fromUser) -> {
            if (fromUser) {
                final Float previous = rawValue;
                rawValue = rating;
                notifyIfChanged(previous);
            }
        });
    }
}
