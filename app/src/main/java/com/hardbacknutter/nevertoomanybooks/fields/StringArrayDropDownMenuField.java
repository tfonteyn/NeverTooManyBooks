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
package com.hardbacknutter.nevertoomanybooks.fields;

import android.content.Context;
import android.content.SharedPreferences;
import android.view.View;
import android.widget.AutoCompleteTextView;

import androidx.annotation.ArrayRes;
import androidx.annotation.IdRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.hardbacknutter.nevertoomanybooks.R;
import com.hardbacknutter.nevertoomanybooks.datamanager.DataManager;
import com.hardbacknutter.nevertoomanybooks.debug.SanityCheck;
import com.hardbacknutter.nevertoomanybooks.widgets.ExtArrayAdapter;

/**
 * The value is expected to be the list position.
 * <p>
 * A {@code null} value is always handled as {@code 0}.
 */
public class StringArrayDropDownMenuField
        extends BaseField<Integer, AutoCompleteTextView> {

    @NonNull
    private final ExtArrayAdapter<CharSequence> mAdapter;

    /**
     * Constructor.
     *
     * @param context    Current context
     * @param arrayResId to use; the array <strong>must not</strong> be empty
     */
    public StringArrayDropDownMenuField(@NonNull final FragmentId fragmentId,
                                        @IdRes final int fieldViewId,
                                        @NonNull final String fieldKey,
                                        @NonNull final Context context,
                                        @ArrayRes final int arrayResId) {
        super(fragmentId, fieldViewId, fieldKey, fieldKey);
        mAdapter = ExtArrayAdapter.createFromResource(
                context, R.layout.popup_dropdown_menu_item,
                ExtArrayAdapter.FilterType.Passthrough, arrayResId);

        SanityCheck.requirePositiveValue(mAdapter.getCount(), "mAdapter.getCount()");
    }

    @NonNull
    public StringArrayDropDownMenuField setTextInputLayoutId(@IdRes final int viewId) {
        addRelatedViews(viewId);
        return this;
    }

    @Override
    public void setParentView(@NonNull final View parent,
                              @NonNull final SharedPreferences global) {
        super.setParentView(parent, global);

        final AutoCompleteTextView view = requireView();
        view.setAdapter(mAdapter);
        view.setOnItemClickListener((p, v, position, id) -> {
            final Integer previous = mRawValue;
            mRawValue = position;
            notifyIfChanged(previous);
        });
    }

    @Override
    @NonNull
    public Integer getValue() {
        return mRawValue != null ? mRawValue : 0;
    }

    @Override
    public void setValue(@Nullable final Integer value) {
        super.setValue(value != null ? value : 0);

        final AutoCompleteTextView view = getView();
        if (view != null) {
            if (mRawValue >= 0 && mRawValue < mAdapter.getCount()) {
                view.setText(mAdapter.getItem(mRawValue), false);
            } else {
                view.setText(mAdapter.getItem(0), false);
            }
        }
    }

    @Override
    public void setInitialValue(@NonNull final DataManager source) {
        mInitialValue = source.getInt(mFieldKey);
        setValue(mInitialValue);
    }

    @Override
    void internalPutValue(@NonNull final DataManager target) {
        target.putInt(mFieldKey, getValue());
    }

    @Override
    boolean isEmpty(@Nullable final Integer value) {
        return value == null || value == 0;
    }
}