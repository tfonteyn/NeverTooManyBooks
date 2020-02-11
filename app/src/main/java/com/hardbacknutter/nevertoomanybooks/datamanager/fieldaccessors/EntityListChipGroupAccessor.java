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

import java.util.ArrayList;
import java.util.List;

import com.hardbacknutter.nevertoomanybooks.R;
import com.hardbacknutter.nevertoomanybooks.datamanager.DataManager;
import com.hardbacknutter.nevertoomanybooks.entities.Entity;

/**
 * FieldFormatter for a list field. Formats the items as a CSV String.
 * <ul>
 * <li>Multiple fields: <strong>no</strong></li>
 * <li>Extract: <strong>local variable</strong></li>
 * </ul>
 * <p>
 * The Entity values are set as the tags on the Chip objects.
 * The Chip objects get no ids.
 */
public class EntityListChipGroupAccessor
        extends BaseDataAccessor<ArrayList<Entity>> {

    private List<Entity> mAll;

    public void setList(@NonNull final List<Entity> all) {
        mAll = all;
    }

    @Override
    public void setView(@NonNull final View view) {
        super.setView(view);
        addTouchSignalsDirty(view);
    }

    @NonNull
    @Override
    public ArrayList<Entity> getValue() {
        return mRawValue != null ? mRawValue : new ArrayList<>();
    }

    @Override
    public void setValue(@NonNull final ArrayList<Entity> value) {
        mRawValue = value;

        ViewGroup view = (ViewGroup) getView();
        if (view != null) {
            view.removeAllViews();
            ChipGroup chipGroup = (ChipGroup) view;
            Context context = chipGroup.getContext();

            // *all* values
            for (Entity entity : mAll) {
                boolean isSet = mRawValue.contains(entity);
                // if editable, all values; if not editable only the set values.
                if (isSet || isEditable()) {
                    Chip chip = new Chip(context, null,
                                         R.style.Widget_MaterialComponents_Chip_Choice);
                    chip.setLayoutDirection(View.LAYOUT_DIRECTION_LOCALE);
                    chip.setTag(entity);
                    chip.setText(entity.getLabel(context));
                    chip.setSelected(isSet);
                    chip.setClickable(isEditable());
                    if (isEditable()) {
                        chip.setOnClickListener(v -> {
                            Entity current = (Entity) v.getTag();
                            if (v.isSelected()) {
                                mRawValue.remove(current);
                            } else {
                                mRawValue.add(current);
                            }
                            v.setSelected(!v.isSelected());
                        });
                    }
                    chipGroup.addView(chip);
                }
            }
        }
    }

    @Override
    public void setValue(@NonNull final DataManager source) {
        setValue(new ArrayList<>(source.getParcelableArrayList(mField.getKey())));
    }

    @Override
    public void getValue(@NonNull final DataManager target) {
        target.putParcelableArrayList(mField.getKey(), getValue());
    }

    @Override
    public boolean isEmpty() {
        return mRawValue == null || mRawValue.isEmpty();
    }
}
