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
import java.util.Objects;

import com.hardbacknutter.nevertoomanybooks.dialogs.DialogFragmentLauncherBase;

/**
 * Crazy wrapper around a {@link MaterialDatePicker}
 * to make it work with a {@link FragmentResultListener}.
 * <p>
 * Usage:
 * <pre>
 *     {@code
 *
 *     private static final String RK_DATE_PICKER_SINGLE = TAG + ":rk:" + "datePickerSingle";
 *     private final WrappedMaterialDatePicker.OnResultListener mDatePickerListener
 *         = new WrappedMaterialDatePicker.OnResultListener() {
 *         @Override
 *         public void onResult(@NonNull final long... selections) {
 *             // do something with the selections
 *             // datePicker()      : one selection
 *             // dateRangePicker() : two selections
 *             // Can be NO_SELECTION, or the usual long value
 *         }
 *     };
 *
 *     @Override
 *     public void onCreate(@Nullable final Bundle savedInstanceState) {
 *         super.onCreate(savedInstanceState);
 *         getChildFragmentManager().setFragmentResultListener(
 *                 RK_DATE_PICKER_SINGLE, this, mDatePickerListener);
 *         ...
 *     }
 *
 *      // create/show:
 *      new WrappedMaterialDatePicker<>(
 *              MaterialDatePicker.Builder
 *                            .datePicker()
 *                            .setTitleText(dialogTitleId)
 *                            .setSelection(selection)
 *                            .build())
 *                  .show(getChildFragmentManager(), RK_DATE_PICKER_SINGLE);
 *     }
 * </pre>
 *
 * @param <S> selection; This class only supports a {@code Long}, or a {@code Pair<Long,Long>}.
 */
public class WrappedMaterialDatePicker<S>
        implements MaterialPickerOnPositiveButtonClickListener<S> {

    /** Used instead of null, so our static method works. */
    public static final long NO_SELECTION = -1L;

    /** The wrapped picker. */
    private final MaterialDatePicker<S> mPicker;
    @IdRes
    private final int[] mFieldIds;
    /** key to use for the FragmentResultListener. */
    private String mRequestKey;

    private WrappedMaterialDatePicker(@NonNull final MaterialDatePicker<S> picker,
                                      @NonNull @IdRes final int... fieldIds) {
        mPicker = picker;
        mFieldIds = fieldIds;
    }

    /**
     * Wrapper to {@link MaterialDatePicker#show(FragmentManager, String)}.
     *
     * @param fm         The FragmentManager this fragment will be added to.
     * @param requestKey The key to use for the FragmentResultListener.
     *                   Will ALSO be used as the regular tag for this fragment, as per
     *                   {@link FragmentTransaction#add(Fragment, String) FragmentTransaction.add}.
     */
    public void show(@NonNull final FragmentManager fm,
                     @NonNull final String requestKey) {
        mRequestKey = requestKey;

        mPicker.addOnPositiveButtonClickListener(this);
        mPicker.show(fm, requestKey);
    }

    @Override
    public void onPositiveButtonClick(@Nullable final S selection) {
        if (selection == null) {
            Launcher.sendResult(mPicker, mRequestKey, mFieldIds, NO_SELECTION);

        } else if (selection instanceof Long) {
            final long date = (Long) selection;
            Launcher.sendResult(mPicker, mRequestKey, mFieldIds, date);

        } else if (selection instanceof Pair) {
            //noinspection unchecked
            final Pair<Long, Long> range = (Pair<Long, Long>) selection;
            final long start = range.first != null ? range.first : NO_SELECTION;
            final long end = range.second != null ? range.second : NO_SELECTION;
            Launcher.sendResult(mPicker, mRequestKey, mFieldIds, start, end);

        } else {
            throw new IllegalArgumentException(selection.toString());
        }
    }

    public abstract static class Launcher
            extends DialogFragmentLauncherBase {

        private static final String FIELD_ID = "fieldId";
        private static final String SELECTIONS = "selections";

        static void sendResult(@NonNull final Fragment fragment,
                               @NonNull final String requestKey,
                               @NonNull final int[] fieldIds,
                               @Nullable final long... selection) {
            final Bundle result = new Bundle(2);
            result.putIntArray(FIELD_ID, fieldIds);
            result.putLongArray(SELECTIONS, selection);
            fragment.getParentFragmentManager().setFragmentResult(requestKey, result);
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
                           @Nullable final Instant time) {

            final Long selection = time != null ? time.toEpochMilli() : null;

            new WrappedMaterialDatePicker<>(
                    MaterialDatePicker.Builder
                            .datePicker()
                            .setTitleText(titleId)
                            .setSelection(selection)
                            .build(),
                    fieldId)
                    .show(mFragmentManager, mRequestKey);
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
                           @Nullable final Instant timeStart,
                           @IdRes final int endFieldId,
                           @Nullable final Instant timeEnd) {

            Long startSelection = timeStart != null ? timeStart.toEpochMilli() : null;
            Long endSelection = timeEnd != null ? timeEnd.toEpochMilli() : null;

            // both set ? then make sure the order is correct
            if (startSelection != null && endSelection != null && startSelection > endSelection) {
                final Long tmp = startSelection;
                startSelection = endSelection;
                endSelection = tmp;
            }

            new WrappedMaterialDatePicker<>(
                    MaterialDatePicker.Builder
                            .dateRangePicker()
                            .setTitleText(titleId)
                            .setSelection(new Pair<>(startSelection, endSelection))
                            .build(),
                    startFieldId, endFieldId)
                    .show(mFragmentManager, mRequestKey);
        }

        @Override
        public void onFragmentResult(@NonNull final String requestKey,
                                     @NonNull final Bundle result) {
            onResult(Objects.requireNonNull(result.getIntArray(FIELD_ID)),
                     Objects.requireNonNull(result.getLongArray(SELECTIONS)));
        }

        /**
         * Callback handler.
         *
         * @param fieldIds   the field(s) this dialog was bound to
         * @param selections the selected date(s)
         */
        public abstract void onResult(@NonNull int[] fieldIds,
                                      @NonNull long[] selections);

    }
}
