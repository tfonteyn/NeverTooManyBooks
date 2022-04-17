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
import com.hardbacknutter.nevertoomanybooks.entities.ParcelableEntity;

/**
 * A {@link ChipGroup} where each {@link Chip} represents one {@link ParcelableEntity} in a list.
 * <p>
 * A {@code null} value is always handled as an empty {@link ArrayList}.
 * <p>
 * Relies on {@link R.attr#appChipFilterStyle}
 */
public class ListChipGroupAccessor<T extends ParcelableEntity>
        extends BaseFieldViewAccessor<ArrayList<T>, ChipGroup> {

    @NonNull
    private final Supplier<List<T>> mListSupplier;

    @Nullable
    private final View.OnClickListener mEditChipListener;

    /**
     * Constructor.
     *
     * @param listSupplier for a list with all <strong>possible</strong> values
     */
    public ListChipGroupAccessor(@NonNull final Supplier<List<T>> listSupplier) {
        mListSupplier = listSupplier;

        mEditChipListener = view -> {
            //noinspection ConstantConditions
            final ArrayList<T> previous = new ArrayList<>(mRawValue);
            //noinspection unchecked
            final T current = (T) view.getTag();
            if (((Checkable) view).isChecked()) {
                mRawValue.add(current);
            } else {
                mRawValue.remove(current);
            }
            notifyIfChanged(previous);
        };
    }

    @NonNull
    @Override
    public ArrayList<T> getValue() {
        return mRawValue != null ? mRawValue : new ArrayList<>();
    }

    @Override
    public void setValue(@Nullable final ArrayList<T> value) {
        mRawValue = value != null ? value : new ArrayList<>();

        final ChipGroup chipGroup = getView();
        if (chipGroup != null) {
            chipGroup.removeAllViews();

            final Context context = chipGroup.getContext();

            for (final T entity : mListSupplier.get()) {

                final Chip chip = new Chip(context, null, R.attr.appChipFilterStyle);
                chip.setChecked(mRawValue.contains(entity));
                chip.setOnClickListener(mEditChipListener);

                // RTL-friendly Chip Layout
                chip.setLayoutDirection(View.LAYOUT_DIRECTION_LOCALE);

                chip.setTag(entity);
                chip.setText(entity.getLabel(context));

                chipGroup.addView(chip);
            }
        }
    }

    @Override
    public void setInitialValue(@NonNull final DataManager source) {
        mInitialValue = new ArrayList<>(source.getParcelableArrayList(mField.getKey()));
        setValue(mInitialValue);
    }

    @Override
    public void getValue(@NonNull final DataManager target) {
        target.putParcelableArrayList(mField.getKey(), getValue());
    }

    @Override
    public boolean isEmpty(@Nullable final ArrayList<T> value) {
        return value == null || value.isEmpty();
    }
}
