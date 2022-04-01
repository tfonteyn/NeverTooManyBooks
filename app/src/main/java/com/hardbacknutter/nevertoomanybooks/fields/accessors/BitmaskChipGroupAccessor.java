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

import java.util.Map;
import java.util.function.Function;

import com.hardbacknutter.nevertoomanybooks.R;
import com.hardbacknutter.nevertoomanybooks.datamanager.DataManager;

/**
 * A {@link ChipGroup} where each {@link Chip} represents one bit in a bitmask.
 * <p>
 * A {@code null} value is always handled as {@code 0}.
 * <p>
 * Relies on {@link R.attr#appChipFilterStyle}
 */
public class BitmaskChipGroupAccessor
        extends BaseFieldViewAccessor<Integer, ChipGroup> {

    @NonNull
    private final Function<Context, Map<Integer, String>> mValuesSupplier;

    @Nullable
    private final View.OnClickListener mEditChipListener;

    /**
     * Constructor.
     *
     * @param supplier for a Map with all <strong>possible</strong> values
     */
    public BitmaskChipGroupAccessor(@NonNull final
                                    Function<Context, Map<Integer, String>> supplier) {
        mValuesSupplier = supplier;

        mEditChipListener = view -> {
            final Integer current = (Integer) view.getTag();
            if (((Checkable) view).isChecked()) {
                // add
                mRawValue |= current;
            } else {
                // remove
                mRawValue &= ~current;
            }
            broadcastChange();
        };
    }

    @NonNull
    @Override
    public Integer getValue() {
        return mRawValue != null ? mRawValue : 0;
    }

    @Override
    public void setValue(@Nullable final Integer value) {
        mRawValue = value != null ? value : 0;

        final ChipGroup chipGroup = getView();
        if (chipGroup != null) {
            chipGroup.removeAllViews();

            final Context context = chipGroup.getContext();

            for (final Map.Entry<Integer, String> entry :
                    mValuesSupplier.apply(context).entrySet()) {

                final Chip chip = new Chip(context, null, R.attr.appChipFilterStyle);
                chip.setChecked((entry.getKey() & mRawValue) != 0);
                chip.setOnClickListener(mEditChipListener);

                // RTL-friendly Chip Layout
                chip.setLayoutDirection(View.LAYOUT_DIRECTION_LOCALE);

                chip.setTag(entry.getKey());
                chip.setText(entry.getValue());

                chipGroup.addView(chip);
            }
        }
    }

    @Override
    public void setInitialValue(@NonNull final DataManager source) {
        setInitialValue(source.getInt(mField.getKey()));
    }

    @Override
    public void getValue(@NonNull final DataManager target) {
        target.putLong(mField.getKey(), getValue());
    }

    @Override
    public boolean isChanged() {
        final Integer value = getValue();
        if ((mInitialValue == null || mInitialValue == 0) && value == 0) {
            return false;
        }
        return !value.equals(mInitialValue);
    }

    @Override
    public boolean isEmpty() {
        return getValue() == 0;
    }
}
