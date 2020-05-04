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
package com.hardbacknutter.nevertoomanybooks.fields.accessors;

import android.widget.CompoundButton;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.hardbacknutter.nevertoomanybooks.datamanager.DataManager;

/**
 * CompoundButton accessor.
 * <p>
 * A {@code null} value is always handled as {@code false}.
 *
 * <ul>{@link CompoundButton} covers more then just a Checkbox:
 *      <li>CheckBox</li>
 *      <li>RadioButton</li>
 *      <li>Switch</li>
 *      <li>ToggleButton</li>
 * </ul>
 * <p>
 * NOT covered is {@code CheckedTextView extends TextView}'
 *
 * <pre>
 *     {@code
 *             <CheckBox
 *             android:id="@+id/cbx_read"
 *             style="@style/CheckboxEntry"
 *             android:text="@string/lbl_read"
 *             app:layout_constraintStart_toStartOf="parent"
 *             app:layout_constraintEnd_toEndOf="parent"
 *             app:layout_constraintTop_toBottomOf="@id/rating"
 *             tools:checked="true"
 *             />}
 * </pre>
 */
public class CompoundButtonAccessor
        extends BaseDataAccessor<Boolean, CompoundButton> {

    @Override
    public void setView(@NonNull final CompoundButton view) {
        super.setView(view);
        addTouchSignalsDirty(view);
    }

    @NonNull
    @Override
    public Boolean getValue() {
        final CompoundButton view = getView();
        if (view != null) {
            return view.isChecked();
        } else {
            return mRawValue != null ? mRawValue : false;
        }
    }

    @Override
    public void setValue(@Nullable final Boolean value) {
        mRawValue = value != null ? value : false;

        final CompoundButton view = getView();
        if (view != null) {
            view.setChecked(mRawValue);
        }
    }

    @Override
    public void setValue(@NonNull final DataManager source) {
        setValue(source.getBoolean(mField.getKey()));
    }

    @Override
    public void getValue(@NonNull final DataManager target) {
        target.putBoolean(mField.getKey(), getValue());
    }

    @Override
    public boolean isEmpty() {
        return !getValue();
    }
}
