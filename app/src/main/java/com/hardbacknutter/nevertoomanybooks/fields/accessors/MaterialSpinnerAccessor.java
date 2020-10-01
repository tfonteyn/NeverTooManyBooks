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

import android.content.Context;
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;

import androidx.annotation.ArrayRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.hardbacknutter.nevertoomanybooks.R;
import com.hardbacknutter.nevertoomanybooks.datamanager.DataManager;
import com.hardbacknutter.nevertoomanybooks.debug.SanityCheck;

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
     * @param arrayResId to use; the array <strong>must not</strong> be empty
     */
    public MaterialSpinnerAccessor(@NonNull final Context context,
                                   @ArrayRes final int arrayResId) {
        mAdapter = ArrayAdapter.createFromResource(context, arrayResId,
                                                   R.layout.dropdown_menu_popup_item);
        SanityCheck.requireValue(mAdapter.getCount(), "mAdapter.getCount()");
    }

    @Override
    public void setView(@NonNull final AutoCompleteTextView view) {
        super.setView(view);
        view.setAdapter(mAdapter);
        // FIXME: opening works fine, but a second click closes AND re-opens the MaterialSpinner
        //view.setOnClickListener(v -> view.showDropDown());
        if (mIsEditable) {
            addTouchSignalsDirty(view);
        }
    }

    @Override
    @NonNull
    public Integer getValue() {
        final AutoCompleteTextView view = getView();
        if (view != null) {
            String current = view.getText().toString();
            return mAdapter.getPosition(current);
        } else {
            return mRawValue != null ? mRawValue : 0;
        }
    }

    @Override
    public void setValue(@Nullable final Integer value) {
        mRawValue = value != null ? value : 0;

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
