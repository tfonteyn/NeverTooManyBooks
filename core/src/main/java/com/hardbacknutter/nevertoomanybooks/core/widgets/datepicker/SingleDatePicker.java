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
package com.hardbacknutter.nevertoomanybooks.core.widgets.datepicker;

import androidx.annotation.IdRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import androidx.fragment.app.FragmentManager;

import com.google.android.material.datepicker.MaterialDatePicker;

import java.lang.ref.WeakReference;
import java.time.LocalDateTime;
import java.time.ZoneOffset;

import com.hardbacknutter.nevertoomanybooks.core.BuildConfig;

/**
 * Uses a {@link MaterialDatePicker} to let the user pick a single/exact date.
 */
public class SingleDatePicker
        extends DatePickerBase<Long> {

    /**
     * Constructor.
     *
     * @param titleResId for the dialog screen
     * @param fieldId    field this dialog is bound to
     */
    public SingleDatePicker(@NonNull final FragmentManager fm,
                            @StringRes final int titleResId,
                            @IdRes final int fieldId) {
        super(fm, titleResId, fieldId);
    }

    /**
     * Launch the dialog to select a single date.
     *
     * @param value current selection (a parsable date string), or {@code null} for none
     */
    public void launch(@Nullable final String value,
                       @NonNull final DatePickerListener listener) {
        launch(parseDate(value, todayIfNone), listener);
    }

    /**
     * Launch the dialog to select a single date.
     *
     * @param date current selection, or {@code null} for none
     */
    public void launch(@Nullable final LocalDateTime date,
                       @NonNull final DatePickerListener listener) {
        launch(date != null ? date.toInstant(ZoneOffset.UTC).toEpochMilli() : null, listener);
    }

    private void launch(@Nullable final Long selection,
                        @NonNull final DatePickerListener listener) {
        this.listener = new WeakReference<>(listener);

        //noinspection unchecked
        MaterialDatePicker<Long> picker = (MaterialDatePicker<Long>)
                fragmentManager.findFragmentByTag(fragmentTag);
        if (picker == null) {
            picker = MaterialDatePicker.Builder
                    .datePicker()
                    .setTitleText(titleResId)
                    .setSelection(selection)
                    .build();
            picker.show(fragmentManager, fragmentTag);
        }

        // remove any dead listener, then set the current one
        picker.clearOnPositiveButtonClickListeners();
        picker.addOnPositiveButtonClickListener(this);
    }

    @Override
    public void onPositiveButtonClick(@Nullable final Long selection) {
        if (BuildConfig.DEBUG /* always */) {
            if (listener == null) {
                throw new NullPointerException("onPositiveButtonClick|listener was NULL");
            } else if (listener.get() == null) {
                throw new NullPointerException("onPositiveButtonClick|listener was dead");
            }
        }

        if (listener != null && listener.get() != null) {
            listener.get().onResult(fieldIds, new long[]{
                    selection == null ? DatePickerListener.NO_SELECTION : selection});
        }
    }
}
