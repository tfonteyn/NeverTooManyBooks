/*
 * @Copyright 2018-2024 HardBackNutter
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

import android.content.Context;
import android.view.View;
import android.widget.CompoundButton;

import androidx.annotation.IdRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.hardbacknutter.nevertoomanybooks.core.parsers.RealNumberParser;
import com.hardbacknutter.nevertoomanybooks.datamanager.DataManager;

/**
 * CompoundButton accessor.
 * <p>
 * A {@code null} value is always handled as {@code false}.
 * <p>
 * {@link CompoundButton} covers more than just a Checkbox:
 * <ul>
 *      <li>CheckBox</li>
 *      <li>RadioButton</li>
 *      <li>Switch</li>
 *      <li>ToggleButton</li>
 * </ul>
 * <p>
 * NOT covered is {@code CheckedTextView extends TextView}'
 */
public class CompoundButtonField
        extends BaseField<Boolean, CompoundButton> {

    /**
     * Constructor.
     *
     * @param fragmentId  the hosting {@link FragmentId} for this {@link Field}
     * @param fieldViewId the view id for this {@link Field}
     * @param fieldKey    Key used to access a {@link DataManager}
     *                    Set to {@code ""} to suppress all access.
     */
    public CompoundButtonField(@NonNull final FragmentId fragmentId,
                               @IdRes final int fieldViewId,
                               @NonNull final String fieldKey) {
        super(fragmentId, fieldViewId, fieldKey, fieldKey);
    }

    @Override
    public void setParentView(@NonNull final View parent) {
        super.setParentView(parent);
        requireView().setOnCheckedChangeListener((buttonView, isChecked) -> {
            final Boolean previous = rawValue;
            rawValue = isChecked;
            notifyIfChanged(previous);
        });
    }

    @Override
    @NonNull
    public Boolean getValue() {
        return rawValue != null ? rawValue : false;
    }

    @Override
    public void setValue(@Nullable final Boolean value) {
        super.setValue(value != null ? value : false);

        final CompoundButton view = getView();
        if (view != null) {
            view.setChecked(getValue());
        }
    }

    @Override
    public void setInitialValue(@NonNull final Context context,
                                @NonNull final DataManager source,
                                @NonNull final RealNumberParser realNumberParser) {
        initialValue = source.getBoolean(getFieldKey());
        setValue(initialValue);
    }

    @Override
    void internalPutValue(@NonNull final DataManager target) {
        target.putBoolean(getFieldKey(), getValue());
    }

    @Override
    boolean isEmpty(@Nullable final Boolean value) {
        return value == null || !value;
    }
}
