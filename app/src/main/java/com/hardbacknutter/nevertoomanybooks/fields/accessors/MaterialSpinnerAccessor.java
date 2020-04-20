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
import android.widget.AutoCompleteTextView;

import androidx.annotation.ArrayRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.hardbacknutter.nevertoomanybooks.R;
import com.hardbacknutter.nevertoomanybooks.datamanager.DataManager;
import com.hardbacknutter.nevertoomanybooks.debug.ErrorMsg;

/**
 * The value is expected to be the list position.
 * <p>
 * A {@code null} value is always handled as {@code 0}.
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
        extends BaseDataAccessor<Integer, AutoCompleteTextView> {

    @NonNull
    private final ArrayAdapter<CharSequence> mAdapter;

    /**
     * Constructor.
     *
     * @param context    Current context
     * @param arrayResId to use; the array <strong>must</strong> not be empty
     */
    public MaterialSpinnerAccessor(@NonNull final Context context,
                                   @ArrayRes final int arrayResId) {
        mAdapter = ArrayAdapter.createFromResource(context, arrayResId,
                                                   R.layout.dropdown_menu_popup_item);
        if (mAdapter.getCount() == 0) {
            throw new IllegalStateException(ErrorMsg.EMPTY_ARRAY);
        }
    }

    @Override
    public void setView(@NonNull final AutoCompleteTextView view) {
        super.setView(view);
        view.setAdapter(mAdapter);
        // FIXME: opening works fine, but a second click closes AND re-opens the MaterialSpinner
        //view.setOnClickListener(v -> view.showDropDown());
        addTouchSignalsDirty(view);
    }

    @Override
    @NonNull
    public Integer getValue() {
        String current = getView().getText().toString();
        return mAdapter.getPosition(current);
    }

    @Override
    public void setValue(@Nullable final Integer value) {
        mRawValue = value != null ? value : 0;

        if (mRawValue >= 0 && mRawValue < mAdapter.getCount()) {
            getView().setText(mAdapter.getItem(mRawValue), false);
        } else {
            getView().setText(mAdapter.getItem(0), false);
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
