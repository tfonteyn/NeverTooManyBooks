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

import android.content.Context;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Checkable;

import androidx.annotation.NonNull;

import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;

import java.util.Map;

import com.hardbacknutter.nevertoomanybooks.R;
import com.hardbacknutter.nevertoomanybooks.datamanager.DataManager;

/**
 * <pre>
 *     {@code
 *             <com.google.android.material.chip.ChipGroup
 *             android:id="@+id/edition"
 *             android:layout_width="0dp"
 *             android:layout_height="wrap_content"
 *             app:layout_constraintEnd_toEndOf="parent"
 *             app:layout_constraintStart_toStartOf="parent"
 *             app:layout_constraintTop_toBottomOf="@id/lbl_edition"
 *             />}
 * </pre>
 */
public class BitmaskChipGroupAccessor
        extends BaseDataAccessor<Integer> {

    @NonNull
    private final Map<Integer, String> mAll;

    private final View.OnClickListener filterChipListener;

    public BitmaskChipGroupAccessor(@NonNull final Map<Integer, String> allValues,
                                    final boolean isEditable) {
        mAll = allValues;
        mIsEditable = isEditable;

        if (mIsEditable) {
            filterChipListener = v -> {
                Integer current = (Integer) v.getTag();
                if (((Checkable) v).isChecked()) {
                    // add
                    mRawValue |= current;
                } else {
                    // remove
                    mRawValue &= ~current;
                }
            };
        } else {
            filterChipListener = null;
        }
    }

    @Override
    public void setView(@NonNull final View view) {
        super.setView(view);
        addTouchSignalsDirty(view);
    }

    @NonNull
    @Override
    public Integer getValue() {
        return mRawValue != null ? mRawValue : 0;
    }

    @Override
    public void setValue(@NonNull final Integer value) {
        mRawValue = value;

        ViewGroup view = (ViewGroup) getView();
        view.removeAllViews();
        ChipGroup chipGroup = (ChipGroup) view;
        Context context = chipGroup.getContext();

        // *all* values
        for (Map.Entry<Integer, String> entry : mAll.entrySet()) {
            boolean isSet = (entry.getKey() & mRawValue) != 0;
            // if editable, all values; if not editable only the set values.
            if (isSet || mIsEditable) {
                chipGroup.addView(createChip(context, entry.getKey(), entry.getValue(), isSet));
            }
        }
    }

    @Override
    public void setValue(@NonNull final DataManager source) {
        setValue(source.getInt(mField.getKey()));
    }

    private Chip createChip(@NonNull final Context context,
                            @NonNull final Object tag,
                            @NonNull final CharSequence label,
                            final boolean initialState) {
        Chip chip;
        if (mIsEditable) {
            chip = new Chip(context, null, R.style.Widget_MaterialComponents_Chip_Filter);
        } else {
            chip = new Chip(context, null, R.style.Widget_MaterialComponents_Chip_Entry);
        }
        // RTL-friendly Chip Layout
        chip.setLayoutDirection(View.LAYOUT_DIRECTION_LOCALE);

        chip.setTag(tag);
        chip.setText(label);

        if (mIsEditable) {
            // reminder: the Filter style has checkable=true, but unless we explicitly set it
            // here in code, it won't take effect.
            chip.setCheckable(true);
            chip.setChecked(initialState);
            chip.setOnClickListener(filterChipListener);
        }
        return chip;
    }

    @Override
    public void getValue(@NonNull final DataManager target) {
        target.putLong(mField.getKey(), getValue());
    }

    @Override
    public boolean isEmpty() {
        return mRawValue == null || mRawValue == 0;
    }
}
