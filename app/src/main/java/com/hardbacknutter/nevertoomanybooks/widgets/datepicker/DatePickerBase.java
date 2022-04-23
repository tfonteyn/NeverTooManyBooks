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
package com.hardbacknutter.nevertoomanybooks.widgets.datepicker;

import androidx.annotation.IdRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;

import com.google.android.material.datepicker.MaterialDatePicker;
import com.google.android.material.datepicker.MaterialPickerOnPositiveButtonClickListener;

import java.lang.ref.WeakReference;
import java.time.Instant;
import java.util.Arrays;
import java.util.Objects;

import com.hardbacknutter.nevertoomanybooks.utils.dates.DateParser;

abstract class DatePickerBase<S>
        implements MaterialPickerOnPositiveButtonClickListener<S> {

    private static final String TAG = "DatePicker";
    @NonNull
    final FragmentManager mFragmentManager;
    @NonNull
    final String mFragmentTag;

    @StringRes
    final int mTitleId;

    @IdRes
    @NonNull
    final int[] mFieldIds;

    boolean mTodayIfNone;

    @Nullable
    WeakReference<DatePickerListener> mListener;

    private DateParser mDateParser;

    /**
     * Constructor.
     *
     * @param titleId  for the dialog screen
     * @param fieldIds field this dialog is bound to
     */
    DatePickerBase(@NonNull final FragmentManager fragmentManager,
                   @StringRes final int titleId,
                   @IdRes @NonNull final int... fieldIds) {
        mFragmentManager = fragmentManager;
        mTitleId = titleId;
        mFieldIds = fieldIds;

        mFragmentTag = TAG + String.join("_", Arrays.toString(fieldIds));
    }

    public void setDateParser(@NonNull final DateParser dateParser,
                              final boolean todayIfNone) {
        mDateParser = dateParser;
        mTodayIfNone = todayIfNone;
    }

    /**
     * This <strong>MUST</strong> be called from {@link Fragment#onResume()}.
     * <p>
     * Developer note: yes, BOTH the {@link #mListener} and the underlying
     * {@link MaterialDatePicker} listener must be set here AND at launch
     * time to ensure their validity after a screen rotation.
     */
    public void onResume(@NonNull final DatePickerListener listener) {
        mListener = new WeakReference<>(listener);

        //noinspection unchecked
        final MaterialDatePicker<S> picker = (MaterialDatePicker<S>)
                mFragmentManager.findFragmentByTag(mFragmentTag);
        if (picker != null) {
            // remove any dead listener, then set the current one
            picker.clearOnPositiveButtonClickListeners();
            picker.addOnPositiveButtonClickListener(this);
        }
    }

    @Nullable
    Long parseDate(@Nullable final String value,
                   final boolean todayIfNone) {
        Objects.requireNonNull(mDateParser, "mDateParser was NULL, call setDateParser() first");

        final Instant date = mDateParser.parseToInstant(value, todayIfNone);
        if (date != null) {
            return date.toEpochMilli();
        }
        return null;
    }
}
