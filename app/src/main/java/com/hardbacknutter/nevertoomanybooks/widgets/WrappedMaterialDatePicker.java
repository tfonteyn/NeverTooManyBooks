/*
 * @Copyright 2020 HardBackNutter
 * @License GNU General Public License
 *
 * This file is part of NeverTooManyBooks.
 *
 * In August 2018, this project was forked from:
 * Book Catalogue 5.2.2 @2016 Philip Warner & Evan Leybourn
 *
 * Without their original creation, this project would not exist in its
 * current form. It was however largely rewritten/refactored and any
 * comments on this fork should be directed at HardBackNutter and not
 * at the original creators.
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

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.util.Pair;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.fragment.app.FragmentResultListener;
import androidx.fragment.app.FragmentTransaction;

import com.google.android.material.datepicker.MaterialDatePicker;
import com.google.android.material.datepicker.MaterialPickerOnPositiveButtonClickListener;

import java.util.Objects;

/**
 * Crazy wrapper around a {@link MaterialDatePicker}
 * to make it work with a {@link FragmentResultListener}.
 * <p>
 * Usage:
 * <pre>
 *     {@code
 *
 *     private static final String REQUEST_KEY_DATE_PICKER_SINGLE = "datePickerSingle";
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
 *                 REQUEST_KEY_DATE_PICKER_SINGLE, this, mDatePickerListener);
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
 *                  .show(getChildFragmentManager(), REQUEST_KEY_DATE_PICKER_SINGLE);
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
    /** key to use for the FragmentResultListener. */
    private String mRequestKey;

    public WrappedMaterialDatePicker(@NonNull final MaterialDatePicker<S> picker) {
        mPicker = picker;
    }

    /**
     * Wrapper to {@link MaterialDatePicker#show(FragmentManager, String)}.
     *
     * @param manager    The FragmentManager this fragment will be added to.
     * @param requestKey The key to use for the FragmentResultListener.
     *                   Will ALSO be used as the regular tag for this fragment, as per
     *                   {@link FragmentTransaction#add(Fragment, String) FragmentTransaction.add}.
     */
    public void show(@NonNull FragmentManager manager,
                     @Nullable String requestKey) {
        mRequestKey = requestKey;

        mPicker.addOnPositiveButtonClickListener(this);
        mPicker.show(manager, requestKey);
    }

    @Override
    public void onPositiveButtonClick(@Nullable final S selection) {
        if (selection == null) {
            OnResultListener.sendResult(mPicker, mRequestKey, NO_SELECTION);

        } else if (selection instanceof Long) {
            final long date = (Long) selection;
            OnResultListener.sendResult(mPicker, mRequestKey, date);

        } else if (selection instanceof Pair) {
            //noinspection unchecked
            final Pair<Long, Long> range = (Pair<Long, Long>) selection;
            final long start = range.first != null ? range.first : NO_SELECTION;
            final long end = range.second != null ? range.second : NO_SELECTION;
            OnResultListener.sendResult(mPicker, mRequestKey, start, end);

        } else {
            throw new IllegalStateException(selection.toString());
        }
    }

    public interface OnResultListener
            extends FragmentResultListener {

        /* private. */ String SELECTIONS = "selections";

        static void sendResult(@NonNull final Fragment fragment,
                               @NonNull final String requestKey,
                               @Nullable final long... selection) {
            final Bundle result = new Bundle();
            result.putLongArray(SELECTIONS, selection);
            fragment.getParentFragmentManager().setFragmentResult(requestKey, result);
        }

        @Override
        default void onFragmentResult(@NonNull final String requestKey,
                                      @NonNull final Bundle result) {
            onResult(Objects.requireNonNull(result.getLongArray(SELECTIONS)));
        }

        void onResult(@NonNull final long... selections);
    }
}
