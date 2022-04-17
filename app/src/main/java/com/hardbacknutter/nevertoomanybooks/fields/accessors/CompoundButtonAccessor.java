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

import android.widget.CompoundButton;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.hardbacknutter.nevertoomanybooks.datamanager.DataManager;

/**
 * CompoundButton accessor.
 * <p>
 * A {@code null} value is always handled as {@code false}.
 *
 * <ul>{@link CompoundButton} covers more than just a Checkbox:
 *      <li>CheckBox</li>
 *      <li>RadioButton</li>
 *      <li>Switch</li>
 *      <li>ToggleButton</li>
 * </ul>
 * <p>
 * NOT covered is {@code CheckedTextView extends TextView}'
 */
public class CompoundButtonAccessor
        extends BaseFieldViewAccessor<Boolean, CompoundButton> {

    /** Are we viewing {@code false} or editing {@code true} a Field. */
    private final boolean mIsEditable;

    public CompoundButtonAccessor(final boolean isEditable) {
        mIsEditable = isEditable;
    }

    @Override
    public void setView(@NonNull final CompoundButton view) {
        super.setView(view);
        if (mIsEditable) {
            view.setOnCheckedChangeListener((buttonView, isChecked) -> {
                final Boolean previous = mRawValue;
                mRawValue = isChecked;
                notifyIfChanged(previous);
            });
        }
    }

    @NonNull
    @Override
    public Boolean getValue() {
        return mRawValue != null ? mRawValue : false;
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
    public void setInitialValue(@NonNull final DataManager source) {
        mInitialValue = source.getBoolean(mField.getKey());
        setValue(mInitialValue);
    }

    @Override
    public void getValue(@NonNull final DataManager target) {
        target.putBoolean(mField.getKey(), getValue());
    }

    public boolean isEmpty(@Nullable final Boolean value) {
        return value == null || !value;
    }
}
