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
import android.widget.Spinner;

import androidx.annotation.NonNull;

import com.hardbacknutter.nevertoomanybooks.datamanager.DataManager;

/**
 * Spinner accessor. Assumes the Spinner contains a list of Strings and
 * sets the spinner to the matching item.
 *
 * <strong>Does NOT support a formatter.</strong>
 * <p>
 * 2020-02-07: currently not in use.
 */
public class SpinnerAccessor
        extends BaseDataAccessor<String> {

    @Override
    public void setView(@NonNull final View view) {
        super.setView(view);
        addTouchSignalsDirty(view);
    }

    @Override
    @NonNull
    public String getValue() {
        Spinner spinner = (Spinner) getView();
        Object selItem = spinner.getSelectedItem();
        if (selItem != null) {
            return selItem.toString().trim();
        } else {
            return "";
        }

    }

    @Override
    public void setValue(@NonNull final String value) {
        mRawValue = value;

        Spinner spinner = (Spinner) getView();
        for (int i = 0; i < spinner.getCount(); i++) {
            if (spinner.getItemAtPosition(i).equals(mRawValue)) {
                spinner.setSelection(i);
                return;
            }
        }
    }

    @Override
    public void setValue(@NonNull final DataManager source) {
        setValue(source.getString(mField.getKey()));
    }

    @Override
    public void getValue(@NonNull final DataManager target) {
        target.putString(mField.getKey(), getValue());
    }

    @Override
    public boolean isEmpty() {
        return getValue().isEmpty();
    }
}
