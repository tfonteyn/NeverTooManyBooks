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

import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.hardbacknutter.nevertoomanybooks.datamanager.DataManager;

/**
 * BooleanIndicatorAccessor accessor. Ties a boolean value to visible/gone for a generic View.
 * <p>
 * A {@code null} value is always handled as {@code false}.
 */
public class BooleanIndicatorAccessor
        extends BaseDataAccessor<Boolean, View> {

    @NonNull
    @Override
    public Boolean getValue() {
        return mRawValue != null ? mRawValue : false;
    }

    @Override
    public void setValue(@Nullable final Boolean value) {
        mRawValue = value != null ? value : false;

        final View view = getView();
        if (view != null) {
            view.setVisibility(mRawValue ? View.VISIBLE : View.GONE);
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
