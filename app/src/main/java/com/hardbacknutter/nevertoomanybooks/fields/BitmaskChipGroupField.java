/*
 * @Copyright 2018-2026 HardBackNutter
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
package com.hardbacknutter.nevertoomanybooks.fields;

import android.content.Context;
import android.view.View;
import android.widget.Checkable;

import androidx.annotation.IdRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;

import java.util.Map;
import java.util.function.Supplier;

import com.hardbacknutter.nevertoomanybooks.R;
import com.hardbacknutter.nevertoomanybooks.core.parsers.RealNumberParser;
import com.hardbacknutter.nevertoomanybooks.datamanager.DataManager;

/**
 * A {@link ChipGroup} where each {@link Chip} represents one bit in a bitmask.
 * <p>
 * A {@code null} value is always handled as {@code 0}.
 * <p>
 * Relies on {@link R.attr#appChipFilterStyle}
 */
public class BitmaskChipGroupField
        extends BaseField<Integer, ChipGroup> {

    @NonNull
    private final Supplier<Map<Integer, Integer>> mapSupplier;

    @Nullable
    private final View.OnClickListener editChipListener;

    /**
     * Constructor.
     *
     * @param fieldViewId the view id for this {@link Field}
     * @param fieldKey    Key used to access a {@link DataManager}
     *                    Set to {@code ""} to suppress all access.
     * @param mapSupplier for a Map with all <strong>possible</strong> values
     */
    public BitmaskChipGroupField(@IdRes final int fieldViewId,
                                 @NonNull final String fieldKey,
                                 @NonNull final Supplier<Map<Integer, Integer>> mapSupplier) {
        super(fieldViewId, fieldKey, fieldKey);
        this.mapSupplier = mapSupplier;

        editChipListener = view -> {
            final Integer previous = rawValue;
            final Integer bit = (Integer) view.getTag();
            if (((Checkable) view).isChecked()) {
                // add
                rawValue |= bit;
            } else {
                // remove
                rawValue &= ~bit;
            }
            notifyIfChanged(previous);
        };
    }

    @Override
    @NonNull
    public Integer getValue() {
        return rawValue != null ? rawValue : 0;
    }

    @Override
    public void setValue(@Nullable final Integer value) {
        super.setValue(value != null ? value : 0);

        final ChipGroup chipGroup = getView();
        if (chipGroup != null) {
            chipGroup.removeAllViews();

            final Context context = chipGroup.getContext();

            final int bits = getValue();
            for (final Map.Entry<Integer, Integer> entry : mapSupplier.get().entrySet()) {
                final Integer key = entry.getKey();
                final Integer resId = entry.getValue();

                final Chip chip = new Chip(context, null, R.attr.appChipFilterStyle);
                chip.setChecked((key & bits) != 0);
                chip.setOnClickListener(editChipListener);

                // RTL-friendly Chip Layout
                chip.setLayoutDirection(View.LAYOUT_DIRECTION_LOCALE);

                chip.setTag(key);
                chip.setText(resId);

                chipGroup.addView(chip);
            }
        }
    }

    @Override
    @Nullable
    public Integer load(@NonNull final Context context,
                        @NonNull final DataManager source,
                        @NonNull final RealNumberParser realNumberParser) {
        return source.getInt(getFieldKey());
    }

    @Override
    void save(@NonNull final DataManager target) {
        target.putLong(getFieldKey(), getValue());
    }

    @Override
    boolean isEmpty(@Nullable final Integer value) {
        return value == null || value == 0;
    }
}
