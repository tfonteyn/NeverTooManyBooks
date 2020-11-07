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
import android.view.View;
import android.widget.Checkable;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

import com.hardbacknutter.nevertoomanybooks.R;
import com.hardbacknutter.nevertoomanybooks.datamanager.DataManager;
import com.hardbacknutter.nevertoomanybooks.entities.Entity;

/**
 * A {@link ChipGroup} where each {@link Chip} represents one {@link Entity} in a list.
 * <p>
 * A {@code null} value is always handled as an empty {@link ArrayList}.
 * <p>
 * Relies on {@link R.attr#appChipDisplayStyle} and <br> {@link R.attr#appChipFilterStyle}
 */
public class EntityListChipGroupAccessor
        extends BaseDataAccessor<ArrayList<Entity>, ChipGroup> {

    private final Supplier<List<Entity>> mListSupplier;

    @Nullable
    private final View.OnClickListener mEditChipListener;

    /**
     * Constructor.
     *
     * @param listSupplier for a list with all <strong>possible</strong> values
     * @param isEditable   flag
     */
    public EntityListChipGroupAccessor(@NonNull final Supplier<List<Entity>> listSupplier,
                                       final boolean isEditable) {
        mListSupplier = listSupplier;
        mIsEditable = isEditable;

        if (mIsEditable) {
            mEditChipListener = view -> {
                final Entity current = (Entity) view.getTag();
                if (((Checkable) view).isChecked()) {
                    //noinspection ConstantConditions
                    mRawValue.add(current);
                } else {
                    //noinspection ConstantConditions
                    mRawValue.remove(current);
                }
            };
        } else {
            mEditChipListener = null;
        }
    }

    @NonNull
    @Override
    public ArrayList<Entity> getValue() {
        return mRawValue != null ? mRawValue : new ArrayList<>();
    }

    @Override
    public void setValue(@Nullable final ArrayList<Entity> value) {
        mRawValue = value != null ? value : new ArrayList<>();

        final ChipGroup chipGroup = getView();
        if (chipGroup != null) {
            chipGroup.removeAllViews();
            final Context context = chipGroup.getContext();

            // *all* values
            for (final Entity entity : mListSupplier.get()) {
                final boolean isSet = mRawValue.contains(entity);
                // if editable, all values; if not editable only the set values.
                if (isSet || mIsEditable) {
                    final Chip chip;
                    if (mIsEditable) {
                        chip = new Chip(context, null, R.attr.appChipFilterStyle);
                        chip.setChecked(isSet);
                        chip.setOnClickListener(mEditChipListener);
                        addTouchSignalsDirty(chip);

                    } else {
                        chip = new Chip(context, null, R.attr.appChipDisplayStyle);
                    }

                    // RTL-friendly Chip Layout
                    chip.setLayoutDirection(View.LAYOUT_DIRECTION_LOCALE);

                    chip.setTag(entity);
                    chip.setText(entity.getLabel(context));

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
