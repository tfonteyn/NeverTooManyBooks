/*
 * @Copyright 2018-2024 HardBackNutter
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
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;

import com.google.android.material.datepicker.MaterialDatePicker;
import com.google.android.material.datepicker.MaterialPickerOnPositiveButtonClickListener;

import java.lang.ref.WeakReference;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.Objects;
import java.util.Optional;

import com.hardbacknutter.nevertoomanybooks.core.parsers.DateParser;

abstract class DatePickerBase<S>
        implements MaterialPickerOnPositiveButtonClickListener<S> {

    private static final String TAG = "DatePicker";
    @NonNull
    final FragmentManager fragmentManager;
    @NonNull
    final String fragmentTag;

    @StringRes
    final int titleResId;

    @IdRes
    @NonNull
    final int[] fieldIds;

    boolean todayIfNone;

    @Nullable
    WeakReference<DatePickerListener> listener;

    private DateParser dateParser;

    /**
     * Constructor.
     *
     * @param fragmentManager The FragmentManager this fragment dialog be added to.
     * @param titleResId      for the dialog screen
     * @param fieldIds        field this dialog is bound to
     */
    DatePickerBase(@NonNull final FragmentManager fragmentManager,
                   @StringRes final int titleResId,
                   @IdRes @NonNull final int... fieldIds) {
        this.fragmentManager = fragmentManager;
        this.titleResId = titleResId;
        this.fieldIds = fieldIds;

        fragmentTag = TAG + String.join("_", Arrays.toString(fieldIds));
    }

    public void setDateParser(@NonNull final DateParser dateParser,
                              final boolean todayIfNone) {
        this.dateParser = dateParser;
        this.todayIfNone = todayIfNone;
    }

    /**
     * This <strong>MUST</strong> be called from {@link Fragment#onResume()}.
     * <p>
     * Developer note: yes, BOTH the {@link #listener} and the underlying
     * {@link MaterialDatePicker} listener must be set here AND at launch
     * time to ensure their validity after a screen rotation.
     *
     * @param listener to receive the results
     */
    public void onResume(@NonNull final DatePickerListener listener) {
        this.listener = new WeakReference<>(listener);

        //noinspection unchecked
        final MaterialDatePicker<S> picker = (MaterialDatePicker<S>)
                fragmentManager.findFragmentByTag(fragmentTag);
        if (picker != null) {
            // remove any dead listener, then set the current one
            picker.clearOnPositiveButtonClickListeners();
            picker.addOnPositiveButtonClickListener(this);
        }
    }

    /**
     * Parse the given date String to the number of milliseconds
     * from the epoch of 1970-01-01T00:00:00Z,
     * and return a {@code Long} suitable to use as a 'selection' for the Android date-picker.
     *
     * @param value       to parse
     * @param todayIfNone flag
     *
     * @return 'selection' value, otherwise {@code null}
     */
    @Nullable
    Long parseDate(@Nullable final String value,
                   final boolean todayIfNone) {
        Objects.requireNonNull(dateParser, "dateParser was NULL, call setDateParser() first");
        final Optional<LocalDateTime> date = dateParser.parse(value);
        if (date.isPresent()) {
            return date.get().toInstant(ZoneOffset.UTC).toEpochMilli();
        } else if (todayIfNone) {
            return Instant.now().toEpochMilli();
        } else {
            return null;
        }
    }

    /**
     * Parse the given date Strings to the number of milliseconds
     * from the epoch of 1970-01-01T00:00:00Z,
     * and return a {@code Pair} suitable to use as a 'selection' for the Android date-picker.
     *
     * @param startDate   to parse
     * @param endDate     to parse
     * @param todayIfNone flag
     *
     * @return Pair with 'selection' values, both of which can be {@code null}
     */
    @NonNull
    Pair<Long, Long> parseRange(@Nullable final String startDate,
                                @Nullable final String endDate,
                                final boolean todayIfNone) {
        final Long startSelection = parseDate(startDate, todayIfNone);
        final Long endSelection = parseDate(endDate, todayIfNone);
        // both set ? then make sure the order is correct
        if (startSelection != null && endSelection != null && startSelection > endSelection) {
            return new Pair<>(endSelection, startSelection);
        } else {
            return new Pair<>(startSelection, endSelection);
        }
    }
}
