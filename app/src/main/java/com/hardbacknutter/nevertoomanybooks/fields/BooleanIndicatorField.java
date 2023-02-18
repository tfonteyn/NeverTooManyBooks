/*
 * @Copyright 2018-2022 HardBackNutter
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
import android.widget.Checkable;

import androidx.annotation.IdRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.hardbacknutter.nevertoomanybooks.datamanager.DataManager;

/**
 * Ties a boolean value to visible/gone for a <strong>generic</strong> View.
 * If the View is {@link Checkable}, then it's kept visible and the value (un)checked.
 * <p>
 * A {@code null} value is always handled as {@code false}.
 */
public class BooleanIndicatorField
        extends BaseField<Boolean, View> {

    /**
     * Constructor.
     *
     * @param fragmentId  the hosting {@link FragmentId} for this {@link Field}
     * @param fieldViewId the view id for this {@link Field}
     * @param fieldKey    Key used to access a {@link DataManager}
     *                    Set to {@code ""} to suppress all access.
     */
    public BooleanIndicatorField(@NonNull final FragmentId fragmentId,
                                 @IdRes final int fieldViewId,
                                 @NonNull final String fieldKey) {
        super(fragmentId, fieldViewId, fieldKey, fieldKey);
    }

    @Override
    public void setVisibility(@NonNull final View parent,
                              final boolean hideEmptyFields,
                              final boolean keepHiddenFieldsHidden) {
        // The field view is handled when the value is set.
        setRelatedViewsVisibility(parent);
    }

    @Override
    @NonNull
    public Boolean getValue() {
        return rawValue != null ? rawValue : false;
    }

    @Override
    public void setValue(@Nullable final Boolean value) {
        super.setValue(value != null ? value : false);

        final View view = getView();
        if (view != null) {
            if (view instanceof Checkable) {
                view.setVisibility(View.VISIBLE);
                ((Checkable) view).setChecked(getValue());
            } else {
                view.setVisibility(getValue() ? View.VISIBLE : View.GONE);
            }
        }
    }

    @Override
    public void setInitialValue(@NonNull final Context context,
                                @NonNull final DataManager source) {
        initialValue = source.getBoolean(fieldKey);
        setValue(initialValue);
    }

    @Override
    void internalPutValue(@NonNull final DataManager target) {
        target.putBoolean(fieldKey, getValue());
    }

    @Override
    boolean isEmpty(@Nullable final Boolean value) {
        return value == null || !value;
    }
}
