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

import android.content.Context;
import android.widget.ArrayAdapter;
import android.widget.Spinner;
import android.widget.SpinnerAdapter;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.ArrayList;
import java.util.List;

import com.hardbacknutter.nevertoomanybooks.R;
import com.hardbacknutter.nevertoomanybooks.datamanager.DataManager;
import com.hardbacknutter.nevertoomanybooks.debug.ErrorMsg;

/**
 * Very simple Spinner accessor.
 * The value is expected to be the list position.
 * <p>
 * A {@code null} value is always handled as {@code 0}.
 */
public class SpinnerAccessor
        extends BaseDataAccessor<Integer, Spinner> {

    @NonNull
    private final SpinnerAdapter mAdapter;

    /**
     * Constructor.
     *
     * @param adapter to use
     */
    public SpinnerAccessor(@NonNull final SpinnerAdapter adapter) {
        mAdapter = adapter;
    }

    /**
     * Constructor.
     *
     * @param context Current context
     * @param list    of strings to populate the spinner
     */
    public SpinnerAccessor(@NonNull final Context context,
                           @NonNull final List<String> list) {
        mAdapter = new ArrayAdapter<>(context, R.layout.dropdown_menu_popup_item, list);
    }

    /**
     * Constructor.
     *
     * @param context Current context
     * @param resIds  list of StringRes id to populate the spinner;
     *                the list <strong>must</strong> not be empty
     */
    public SpinnerAccessor(@NonNull final Context context,
                           @NonNull final Iterable<Integer> resIds) {
        List<String> list = new ArrayList<>();
        for (int id : resIds) {
            list.add(context.getString(id));
        }
        mAdapter = new ArrayAdapter<>(context, R.layout.dropdown_menu_popup_item, list);
        if (mAdapter.getCount() == 0) {
            throw new IllegalStateException(ErrorMsg.EMPTY_ARRAY);
        }
    }

    @Override
    public void setView(@NonNull final Spinner view) {
        super.setView(view);
        view.setAdapter(mAdapter);
        addTouchSignalsDirty(view);
    }

    @Override
    @NonNull
    public Integer getValue() {
        final Spinner view = getView();
        if (view != null) {
            return view.getSelectedItemPosition();
        } else {
            return mRawValue != null ? mRawValue : 0;
        }
    }

    @Override
    public void setValue(@Nullable final Integer value) {
        mRawValue = value != null ? value : 0;

        final Spinner view = getView();
        if (view != null) {
            if (mRawValue >= 0 && mRawValue < view.getCount()) {
                view.setSelection(mRawValue);
            } else {
                view.setSelection(0);
            }
        }
    }

    @Override
    public void setValue(@NonNull final DataManager source) {
        setValue(source.getInt(mField.getKey()));
    }

    @Override
    public void getValue(@NonNull final DataManager target) {
        target.putInt(mField.getKey(), getValue());
    }

    @Override
    public boolean isEmpty() {
        // Our spinner is never empty by design.
        return false;
    }
}
