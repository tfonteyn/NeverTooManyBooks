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
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;

import androidx.annotation.ArrayRes;
import androidx.annotation.NonNull;

import com.hardbacknutter.nevertoomanybooks.R;
import com.hardbacknutter.nevertoomanybooks.datamanager.DataManager;

/**
 * The value is expected to be the list position.
 *
 * <pre>
 *     {@code
 *             <com.google.android.material.textfield.TextInputLayout
 *             android:id="@+id/lbl_condition"
 *             style="@style/Envelope.AutoCompleteTextView"
 *             android:hint="@string/lbl_condition"
 *             app:layout_constraintStart_toStartOf="parent"
 *             app:layout_constraintEnd_toStartOf="@id/lbl_condition_cover"
 *             app:layout_constraintTop_toBottomOf="@id/lbl_price_paid_currency"
 *             >
 *
 *             <AutoCompleteTextView
 *                 android:id="@+id/condition"
 *                 style="@style/EditText.Spinner"
 *                 android:layout_width="match_parent"
 *                 tools:ignore="LabelFor"
 *                 />
 *
 *         </com.google.android.material.textfield.TextInputLayout>}
 * </pre>
 */
public class MaterialSpinnerAccessor
        extends BaseDataAccessor<Integer> {

    @NonNull
    private final ArrayAdapter<CharSequence> mAdapter;

    /**
     * Constructor.
     *
     * @param context    Current context
     * @param arrayResId to use
     */
    public MaterialSpinnerAccessor(@NonNull final Context context,
                                   @ArrayRes final int arrayResId) {
        mAdapter = ArrayAdapter.createFromResource(context, arrayResId,
                                                   R.layout.dropdown_menu_popup_item);
    }

    @Override
    public void setView(@NonNull final View view) {
        super.setView(view);
        final AutoCompleteTextView ac = (AutoCompleteTextView) view;
        ac.setAdapter(mAdapter);
        // FIXME: opening works fine, but a second click closes AND re-opens the MaterialSpinner
        //ac.setOnClickListener(v -> ac.showDropDown());
        addTouchSignalsDirty(view);
    }

    @Override
    @NonNull
    public Integer getValue() {
        AutoCompleteTextView spinner = (AutoCompleteTextView) getView();
        String current = spinner.getText().toString();
        return mAdapter.getPosition(current);
    }

    @Override
    public void setValue(@NonNull final Integer value) {
        mRawValue = value;

        AutoCompleteTextView spinner = (AutoCompleteTextView) getView();
        if (value >= 0 && value < mAdapter.getCount()) {
            spinner.setText(mAdapter.getItem(value), false);
        } else {
            spinner.setText(mAdapter.getItem(0), false);
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
}
