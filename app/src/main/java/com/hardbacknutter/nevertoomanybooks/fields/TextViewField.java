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

import android.widget.TextView;

import androidx.annotation.IdRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.hardbacknutter.nevertoomanybooks.ServiceLocator;
import com.hardbacknutter.nevertoomanybooks.datamanager.DataManager;
import com.hardbacknutter.nevertoomanybooks.fields.endicon.ExtEndIconDelegate;
import com.hardbacknutter.nevertoomanybooks.fields.formatters.FieldFormatter;

/**
 * Handles a {@link TextView}.
 * <p>
 * The actual value is simply stored in a local variable.
 * No attempt to extract is done.
 *
 * @param <T> type of Field value.
 */
public class TextViewField<T>
        extends BaseTextField<T, TextView> {

    /** Log tag. */
    private static final String TAG = "TextViewField";

    /**
     * Constructor.
     *
     * @param fragmentId  the hosting {@link FragmentId} for this {@link Field}
     * @param fieldViewId the view id for this {@link Field}
     * @param fieldKey    Key used to access a {@link DataManager}
     */
    public TextViewField(@NonNull final FragmentId fragmentId,
                         @IdRes final int fieldViewId,
                         @NonNull final String fieldKey) {
        super(fragmentId, fieldViewId, fieldKey, fieldKey, null);
    }

    /**
     * Constructor.
     *
     * @param fragmentId  the hosting {@link FragmentId} for this {@link Field}
     * @param fieldViewId the view id for this {@link Field}
     * @param fieldKey    Key used to access a {@link DataManager}
     * @param formatter   (optional) formatter to use
     */
    public TextViewField(@NonNull final FragmentId fragmentId,
                         @IdRes final int fieldViewId,
                         @NonNull final String fieldKey,
                         @NonNull final FieldFormatter<T> formatter) {
        super(fragmentId, fieldViewId, fieldKey, fieldKey, formatter);
    }

    /**
     * Constructor.
     *
     * @param fragmentId  the hosting {@link FragmentId} for this {@link Field}
     * @param fieldViewId the view id for this {@link Field}
     * @param fieldKey    Key used to access a {@link DataManager}
     * @param prefKey     The preference key to check if this Field is used or not
     * @param formatter   (optional) formatter to use
     */
    public TextViewField(@NonNull final FragmentId fragmentId,
                         @IdRes final int fieldViewId,
                         @NonNull final String fieldKey,
                         @NonNull final String prefKey,
                         @NonNull final FieldFormatter<T> formatter) {
        super(fragmentId, fieldViewId, fieldKey, prefKey, formatter);
    }

    /**
     * Set the id for the surrounding TextInputLayout (if this field has one).
     *
     * @param viewId view id
     *
     * @return {@code this} (for chaining)
     */
    @NonNull
    public TextViewField<T> setTextInputLayoutId(@IdRes final int viewId) {
        textInputLayoutId = viewId;
        setErrorViewId(viewId);
        return this;
    }

    /**
     * Set the mode to use for the end-icon.
     *
     * @param endIconMode to use
     *
     * @return {@code this} (for chaining)
     */
    @NonNull
    public TextViewField<T> setEndIconMode(
            @ExtEndIconDelegate.EndIconMode final int endIconMode) {
        this.endIconMode = endIconMode;
        return this;
    }

    @Override
    public void setValue(@Nullable final T value) {
        super.setValue(value);

        final TextView view = getView();
        if (view != null) {
            try {
                formatter.apply(rawValue, view);

            } catch (@NonNull final ClassCastException e) {
                // Due to the way a Book loads data from the database,
                // it's possible that it gets the column type wrong.
                // See {@link TypedCursor} class docs.
                // Also see {@link SearchCoordinator#accumulateStringData}
                ServiceLocator.getInstance().getLogger().e(TAG, e, value);

                view.setText(rawValue != null ? String.valueOf(rawValue) : "");
            }
        }
    }
}
