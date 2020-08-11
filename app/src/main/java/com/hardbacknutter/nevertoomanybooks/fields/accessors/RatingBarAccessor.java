/*
 * @Copyright 2020 HardBackNutter
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
 *
 * <pre>
 *     {@code
 *             <RatingBar
 *             android:id="@+id/rating"
 *             style="?attr/ratingBarStyle"
 *             android:layout_width="wrap_content"
 *             android:layout_height="wrap_content"
 *             android:numStars="5"
 *             app:layout_constraintStart_toStartOf="parent"
 *             app:layout_constraintTop_toBottomOf="@id/lbl_rating"
 *             tools:rating="5"
 *             />}
 * </pre>
 */
public class RatingBarAccessor
        extends BaseDataAccessor<Float, RatingBar> {

    @Override
    public void setView(@NonNull final RatingBar view) {
        super.setView(view);
        if (mIsEditable) {
            addTouchSignalsDirty(view);
        }
    }

    @NonNull
    @Override
    public Float getValue() {
        final RatingBar view = getView();
        if (view != null) {
            return view.getRating();
        } else {
            return mRawValue != null ? mRawValue : 0f;
        }
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
    public void setValue(@NonNull final DataManager source) {
        setValue(source.getFloat(mField.getKey()));
    }

    @Override
    public void getValue(@NonNull final DataManager target) {
        target.putFloat(mField.getKey(), getValue());
    }

    @Override
    public boolean isEmpty() {
        return getValue().equals(0.0f);
    }
}
