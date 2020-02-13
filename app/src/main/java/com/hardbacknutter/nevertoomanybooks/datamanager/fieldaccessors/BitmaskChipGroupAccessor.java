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

import androidx.annotation.NonNull;

import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;

import java.util.Map;

import com.hardbacknutter.nevertoomanybooks.R;
import com.hardbacknutter.nevertoomanybooks.datamanager.DataManager;

public class BitmaskChipGroupAccessor
        extends BaseDataAccessor<Integer> {

    @NonNull
    private final Map<Integer, String> mAll;

    public BitmaskChipGroupAccessor(@NonNull final Map<Integer, String> allValues,
                                    final boolean isEditable) {
        mAll = allValues;
        mIsEditable = isEditable;
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
                Chip chip = new Chip(context, null,
                                     R.style.Widget_MaterialComponents_Chip_Choice);
                chip.setLayoutDirection(View.LAYOUT_DIRECTION_LOCALE);
                chip.setId(entry.getKey());
                chip.setText(entry.getValue());
                chip.setSelected(isSet);
                chip.setClickable(mIsEditable);
                if (mIsEditable) {
                    chip.setOnClickListener(v -> {
                        int current = v.getId();
                        if (v.isSelected()) {
                            // remove
                            mRawValue &= ~current;
                        } else {
                            // add
                            mRawValue |= current;
                        }
                        v.setSelected(!v.isSelected());
                    });
                }
                chipGroup.addView(chip);
            }
        }
    }

    @Override
    public void setValue(@NonNull final DataManager source) {
        setValue(source.getInt(mField.getKey()));
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
