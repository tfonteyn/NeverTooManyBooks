/*
 * @Copyright 2018-2023 HardBackNutter
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
import android.os.Parcelable;
import android.view.View;
import android.widget.Checkable;

import androidx.annotation.IdRes;
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
 * A {@link ChipGroup} where each {@link Chip} represents one
 * {@link Parcelable} & {@link Entity} in a list.
 * <p>
 * A {@code null} value is always handled as an empty {@link ArrayList}.
 * <p>
 * Relies on {@link R.attr#appChipFilterStyle}
 *
 * @param <T> type of Entity (== Field) value.
 */
public class ListChipGroupField<T extends Parcelable & Entity>
        extends BaseField<ArrayList<T>, ChipGroup> {

    @NonNull
    private final Supplier<List<T>> listSupplier;

    @Nullable
    private final View.OnClickListener editChipListener;

    /**
     * Constructor.
     *
     * @param fragmentId   the hosting {@link FragmentId} for this {@link Field}
     * @param fieldViewId  the view id for this {@link Field}
     * @param fieldKey     Key used to access a {@link DataManager}
     *                     Set to {@code ""} to suppress all access.
     * @param listSupplier for a list with all <strong>possible</strong> values
     */
    public ListChipGroupField(@NonNull final FragmentId fragmentId,
                              @IdRes final int fieldViewId,
                              @NonNull final String fieldKey,
                              @NonNull final Supplier<List<T>> listSupplier) {
        super(fragmentId, fieldViewId, fieldKey, fieldKey);
        this.listSupplier = listSupplier;

        editChipListener = view -> {
            //noinspection ConstantConditions
            final ArrayList<T> previous = new ArrayList<>(rawValue);
            //noinspection unchecked
            final T current = (T) view.getTag();
            if (((Checkable) view).isChecked()) {
                rawValue.add(current);
            } else {
                rawValue.remove(current);
            }
            notifyIfChanged(previous);
        };
    }

    @Override
    @NonNull
    public ArrayList<T> getValue() {
        return rawValue != null ? rawValue : new ArrayList<>();
    }

    @Override
    public void setValue(@Nullable final ArrayList<T> value) {
        super.setValue(value != null ? value : new ArrayList<>());

        final ChipGroup chipGroup = getView();
        if (chipGroup != null) {
            chipGroup.removeAllViews();

            final Context context = chipGroup.getContext();

            for (final T entity : listSupplier.get()) {

                final Chip chip = new Chip(context, null, R.attr.appChipFilterStyle);
                chip.setChecked(rawValue != null && rawValue.contains(entity));
                chip.setOnClickListener(editChipListener);

                // RTL-friendly Chip Layout
                chip.setLayoutDirection(View.LAYOUT_DIRECTION_LOCALE);

                chip.setTag(entity);
                chip.setText(entity.getLabel(context));

                chipGroup.addView(chip);
            }
        }
    }

    @Override
    public void setInitialValue(@NonNull final Context context,
                                @NonNull final DataManager source) {
        initialValue = new ArrayList<>(source.getParcelableArrayList(fieldKey));
        setValue(initialValue);
    }

    @Override
    void internalPutValue(@NonNull final DataManager target) {
        target.putParcelableArrayList(fieldKey, getValue());
    }

    @Override
    boolean isEmpty(@Nullable final ArrayList<T> value) {
        return value == null || value.isEmpty();
    }
}
