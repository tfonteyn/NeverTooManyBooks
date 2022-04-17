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
package com.hardbacknutter.nevertoomanybooks.fields.accessors;

import android.widget.RatingBar;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.hardbacknutter.nevertoomanybooks.datamanager.DataManager;

/**
 * RatingBar accessor.
 * <p>
 * A {@code null} value is always handled as {@code 0}.
 */
public class RatingBarAccessor
        extends BaseFieldViewAccessor<Float, RatingBar> {

    /** Are we viewing {@code false} or editing {@code true} a Field. */
    private final boolean mIsEditable;

    public RatingBarAccessor(final boolean isEditable) {
        mIsEditable = isEditable;
    }

    @Override
    public void setView(@NonNull final RatingBar view) {
        super.setView(view);
        if (mIsEditable) {
            view.setOnRatingBarChangeListener((ratingBar, rating, fromUser) -> {
                if (fromUser) {
                    final Float previous = mRawValue;
                    mRawValue = rating;
                    notifyIfChanged(previous);
                }
            });
        }
    }

    @NonNull
    @Override
    public Float getValue() {
        return mRawValue != null ? mRawValue : 0f;
    }

    @Override
    public void setValue(@Nullable final Float value) {
        mRawValue = value != null ? value : 0f;

        final RatingBar view = getView();
        if (view != null) {
            view.setRating(mRawValue);
        }
    }

    @Override
    public void setInitialValue(@NonNull final DataManager source) {
        mInitialValue = source.getFloat(mField.getKey());
        setValue(mInitialValue);
    }

    @Override
    public void getValue(@NonNull final DataManager target) {
        target.putFloat(mField.getKey(), getValue());
    }

    public boolean isEmpty(@Nullable final Float value) {
        return value == null || value == 0f;
    }
}
