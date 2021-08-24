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
package com.hardbacknutter.nevertoomanybooks.widgets;

import android.os.Bundle;

import androidx.annotation.IdRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import androidx.core.util.Pair;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.fragment.app.FragmentResultListener;
import androidx.fragment.app.FragmentTransaction;

import com.google.android.material.datepicker.MaterialDatePicker;
import com.google.android.material.datepicker.MaterialPickerOnPositiveButtonClickListener;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Objects;

import com.hardbacknutter.nevertoomanybooks.FragmentLauncherBase;
import com.hardbacknutter.nevertoomanybooks.utils.dates.DateParser;

/**
 * Crazy wrapper around a {@link MaterialDatePicker}
 * to make it work with a {@link FragmentResultListener}.
 *
 * @param <S> selection; This class only supports a {@code Long}, or a {@code Pair<Long,Long>}.
 */
public final class WrappedMaterialDatePicker<S>
        implements MaterialPickerOnPositiveButtonClickListener<S> {

    /** Used instead of null, so our static method works. */
    public static final long NO_SELECTION = -1L;

    /** The wrapped picker. */
    private final MaterialDatePicker<S> mPicker;
    @IdRes
    private final int[] mFieldIds;
    /** key to use for the FragmentResultListener. */
    private final String mRequestKey;

    /**
     * Constructor.
     *
     * @param requestKey for use with the FragmentResultListener
     *                   Will ALSO be used as the regular tag for this fragment, as per
     *                   {@link FragmentTransaction#add(Fragment, String) FragmentTransaction.add}.
     * @param picker     the MaterialDatePicker to wrap
     * @param fieldIds   the fields which are bound to the single date or date-span
     */
    private WrappedMaterialDatePicker(@NonNull final String requestKey,
                                      @NonNull final MaterialDatePicker<S> picker,
                                      @NonNull @IdRes final int... fieldIds) {
        mRequestKey = requestKey;
        mPicker = picker;
        mFieldIds = fieldIds;
    }

    /**
     * Wrapper to {@link MaterialDatePicker#show(FragmentManager, String)}.
     *
     * @param fm The FragmentManager this fragment will be added to.
     */
    public void show(@NonNull final FragmentManager fm) {
        mPicker.addOnPositiveButtonClickListener(this);
        mPicker.show(fm, mRequestKey);
    }

    @Override
    public void onPositiveButtonClick(@Nullable final S selection) {
        if (selection == null) {
            Launcher.setResult(mPicker, mRequestKey, mFieldIds, NO_SELECTION);

        } else if (selection instanceof Long) {
            final long date = (Long) selection;
            Launcher.setResult(mPicker, mRequestKey, mFieldIds, date);

        } else if (selection instanceof Pair) {
            //noinspection unchecked
            final Pair<Long, Long> range = (Pair<Long, Long>) selection;
            final long start = range.first != null ? range.first : NO_SELECTION;
            final long end = range.second != null ? range.second : NO_SELECTION;
            Launcher.setResult(mPicker, mRequestKey, mFieldIds, start, end);

        } else {
            throw new IllegalArgumentException(selection.toString());
        }
    }

    public abstract static class Launcher
            extends FragmentLauncherBase {

        private static final String FIELD_ID = "fieldId";
        private static final String SELECTIONS = "selections";

        private DateParser mDateParser;

        public Launcher(@NonNull final String requestKey) {
            super(requestKey);
        }

        static void setResult(@NonNull final Fragment fragment,
                              @NonNull final String requestKey,
                              @NonNull final int[] fieldIds,
                              @Nullable final long... selection) {
            final Bundle result = new Bundle(2);
            result.putIntArray(FIELD_ID, fieldIds);
            result.putLongArray(SELECTIONS, selection);
            fragment.getParentFragmentManager().setFragmentResult(requestKey, result);
        }

        public void setDateParser(@NonNull final DateParser dateParser) {
            mDateParser = dateParser;
        }

        @Nullable
        private Long getInstant(@Nullable final String value,
                                final boolean todayIfNone) {
            final Instant date = mDateParser.parseToInstant(value, todayIfNone);
            if (date != null) {
                return date.toEpochMilli();
            }
            return null;
        }

        /**
         * Launch the dialog to select a single date.
         *
         * @param titleId     for the dialog screen
         * @param fieldId     field this dialog is bound to
         * @param value       current selection (a parsable date string),
         *                    or {@code null} for none
         * @param todayIfNone if {@code true}, and if the field value empty,
         *                    default to today's date.
         */
        public void launch(@StringRes final int titleId,
                           @IdRes final int fieldId,
                           @Nullable final String value,
                           final boolean todayIfNone) {

            final Long selection = getInstant(value, todayIfNone);

            new WrappedMaterialDatePicker<>(mRequestKey,
                                            MaterialDatePicker.Builder
                                                    .datePicker()
                                                    .setTitleText(titleId)
                                                    .setSelection(selection)
                                                    .build(),
                                            fieldId)
                    .show(mFragmentManager);
        }

        /**
         * Launch the dialog to select a single date.
         *
         * @param titleId for the dialog screen
         * @param fieldId field this dialog is bound to
         * @param time    current selection, or {@code null} for none
         */
        public void launch(@StringRes final int titleId,
                           @IdRes final int fieldId,
                           @Nullable final LocalDateTime time) {

            final Long selection =
                    time != null ? time.toInstant(ZoneOffset.UTC).toEpochMilli() : null;

            new WrappedMaterialDatePicker<>(mRequestKey,
                                            MaterialDatePicker.Builder
                                                    .datePicker()
                                                    .setTitleText(titleId)
                                                    .setSelection(selection)
                                                    .build(),
                                            fieldId)
                    .show(mFragmentManager);
        }

        /**
         * Launch the dialog to select a date span.
         *
         * @param titleId      for the dialog screen
         * @param startFieldId field for the start-date this dialog is bound to
         * @param timeStart    current start-selection, or {@code null} for none
         * @param endFieldId   field for the end-date this dialog is bound to
         * @param timeEnd      current end-selection, or {@code null} for none
         */
        public void launch(@StringRes final int titleId,
                           @IdRes final int startFieldId,
                           @Nullable final String timeStart,
                           @IdRes final int endFieldId,
                           @Nullable final String timeEnd,
                           final boolean todayIfNone) {

            Long startSelection = getInstant(timeStart, todayIfNone);
            Long endSelection = getInstant(timeEnd, todayIfNone);

            // both set ? then make sure the order is correct
            if (startSelection != null && endSelection != null && startSelection > endSelection) {
                final Long tmp = startSelection;
                startSelection = endSelection;
                endSelection = tmp;
            }

            new WrappedMaterialDatePicker<>(mRequestKey,
                                            MaterialDatePicker.Builder
                                                    .dateRangePicker()
                                                    .setTitleText(titleId)
                                                    .setSelection(new Pair<>(startSelection,
                                                                             endSelection))
                                                    .build(),
                                            startFieldId, endFieldId)
                    .show(mFragmentManager);
        }

        @Override
        public void onFragmentResult(@NonNull final String requestKey,
                                     @NonNull final Bundle result) {
            onResult(Objects.requireNonNull(result.getIntArray(FIELD_ID)),
                     Objects.requireNonNull(result.getLongArray(SELECTIONS)));
        }

        /**
         * Callback handler.
         * <p>
         * The resulting date can be reconstructed with for example
         *
         * <pre>
         *     {@code
         *              Instant.ofEpochMilli(selections[i])
         *                     .atZone(ZoneId.systemDefault())
         *                     .format(DateTimeFormatter.ISO_LOCAL_DATE)
         *     }
         * </pre>
         * Instant.ofEpochMilli(selections[0])
         *
         * @param fieldIds   the field(s) this dialog was bound to
         * @param selections the selected date(s)
         */
        public abstract void onResult(@NonNull int[] fieldIds,
                                      @NonNull long[] selections);
    }
}
