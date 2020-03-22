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
package com.hardbacknutter.nevertoomanybooks.datamanager.fieldaccessors;

import android.view.View;
import android.widget.RatingBar;

import androidx.annotation.NonNull;

import com.hardbacknutter.nevertoomanybooks.datamanager.DataManager;

/**
 * RatingBar accessor.
 *
 * <pre>
 *     {@code
 *             <RatingBar
 *             android:id="@+id/rating"
 *             style="?android:attr/ratingBarStyle"
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
        extends BaseDataAccessor<Float> {

    @Override
    public void setView(@NonNull final View view) {
        super.setView(view);
        addTouchSignalsDirty(view);
    }

    @NonNull
    @Override
    public Float getValue() {
        RatingBar bar = (RatingBar) getView();
        return bar.getRating();
    }

    @Override
    public void setValue(@NonNull final Float value) {
        mRawValue = value;

        RatingBar bar = (RatingBar) getView();
        bar.setRating(mRawValue);
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
