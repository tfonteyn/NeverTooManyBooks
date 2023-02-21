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
import androidx.core.util.Pair;
import androidx.fragment.app.FragmentManager;

import com.google.android.material.datepicker.MaterialDatePicker;

import java.lang.ref.WeakReference;

import com.hardbacknutter.nevertoomanybooks.core.BuildConfig;

/**
 * Uses a {@link MaterialDatePicker} to let the user pick a date range.
 */
public class DateRangePicker
        extends DatePickerBase<Pair<Long, Long>> {

    /**
     * Constructor.
     *
     * @param titleResId       for the dialog screen
     * @param startDateFieldId field to bind the start-date to
     * @param endDateFieldId   field to bind the end-date to
     */
    public DateRangePicker(@NonNull final FragmentManager fm,
                           @StringRes final int titleResId,
                           @IdRes final int startDateFieldId,
                           @IdRes final int endDateFieldId) {
        super(fm, titleResId, startDateFieldId, endDateFieldId);
    }

    /**
     * Launch the dialog to select a date span.
     *
     * @param startDate current start-selection (a parsable date string), or {@code null} for none
     * @param endDate   current end-selection (a parsable date string), or {@code null} for none
     */
    public void launch(@Nullable final String startDate,
                       @Nullable final String endDate,
                       @NonNull final DatePickerListener listener) {

        this.listener = new WeakReference<>(listener);

        final Long startSelection = parseDate(startDate, todayIfNone);
        final Long endSelection = parseDate(endDate, todayIfNone);
        final Pair<Long, Long> selection;

        // both set ? then make sure the order is correct
        if (startSelection != null && endSelection != null && startSelection > endSelection) {
            selection = new Pair<>(endSelection, startSelection);
        } else {
            selection = new Pair<>(startSelection, endSelection);
        }

        //noinspection unchecked
        MaterialDatePicker<Pair<Long, Long>> picker = (MaterialDatePicker<Pair<Long, Long>>)
                fragmentManager.findFragmentByTag(fragmentTag);
        if (picker == null) {
            picker = MaterialDatePicker.Builder
                    .dateRangePicker()
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
    public void onPositiveButtonClick(@Nullable final Pair<Long, Long> selection) {
        if (BuildConfig.DEBUG /* always */) {
            if (listener == null) {
                throw new NullPointerException("onPositiveButtonClick|listener was NULL");
            } else if (listener.get() == null) {
                throw new NullPointerException("onPositiveButtonClick|listener was dead");
            }
        }

        if (listener != null && listener.get() != null) {
            final long[] selections;
            if (selection == null) {
                selections = new long[]{DatePickerListener.NO_SELECTION};
            } else {
                final long start = selection.first != null ? selection.first
                                                           : DatePickerListener.NO_SELECTION;
                final long end = selection.second != null ? selection.second
                                                          : DatePickerListener.NO_SELECTION;
                selections = new long[]{start, end};
            }
            listener.get().onResult(fieldIds, selections);
        }
    }
}
