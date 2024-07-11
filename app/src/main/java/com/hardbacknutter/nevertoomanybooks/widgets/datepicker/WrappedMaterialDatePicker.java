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
package com.hardbacknutter.nevertoomanybooks.widgets.datepicker;

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
import androidx.lifecycle.LifecycleOwner;

import com.google.android.material.datepicker.MaterialDatePicker;
import com.google.android.material.datepicker.MaterialPickerOnPositiveButtonClickListener;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Objects;
import java.util.Optional;

import com.hardbacknutter.nevertoomanybooks.core.parsers.DateParser;

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
    @NonNull
    private final MaterialDatePicker<S> picker;
    @IdRes
    @NonNull
    private final int[] fieldIds;
    /** key to use for the FragmentResultListener. */
    @NonNull
    private final String requestKey;

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
        this.requestKey = requestKey;
        this.picker = picker;
        this.fieldIds = fieldIds;
    }

    /**
     * Wrapper to {@link MaterialDatePicker#show(FragmentManager, String)}.
     *
     * @param fm The FragmentManager this fragment will be added to.
     */
    public void show(@NonNull final FragmentManager fm) {
        picker.addOnPositiveButtonClickListener(this);
        picker.show(fm, requestKey);
    }

    @Override
    public void onPositiveButtonClick(@Nullable final S selection) {
        if (selection == null) {
            Launcher.setResult(picker, requestKey, fieldIds, NO_SELECTION);

        } else if (selection instanceof Long) {
            final long date = (Long) selection;
            Launcher.setResult(picker, requestKey, fieldIds, date);

        } else if (selection instanceof Pair) {
            //noinspection unchecked
            final Pair<Long, Long> range = (Pair<Long, Long>) selection;
            final long start = range.first != null ? range.first : NO_SELECTION;
            final long end = range.second != null ? range.second : NO_SELECTION;
            Launcher.setResult(picker, requestKey, fieldIds, start, end);

        } else {
            throw new IllegalArgumentException(selection.toString());
        }
    }

    public static class Launcher
            implements FragmentResultListener {

        private static final String FIELD_ID = "fieldId";
        private static final String SELECTIONS = "selections";

        @NonNull
        private final String requestKey;
        @NonNull
        private final ResultListener resultListener;
        private FragmentManager fragmentManager;

        private DateParser dateParser;

        public Launcher(@NonNull final String requestKey,
                        @NonNull final ResultListener resultListener) {
            this.requestKey = requestKey;
            this.resultListener = resultListener;
        }

        /**
         * Encode and forward the results to {@link #onFragmentResult(String, Bundle)}.
         *
         * @param fragment   the calling DialogFragment
         * @param requestKey to use
         * @param fieldIds   one or two field resource ids this dialog was bound to
         * @param selections one or two values with the selected date(s);
         *                   either/both can be {@code null}
         *
         * @see #onFragmentResult(String, Bundle)
         */
        @SuppressWarnings("StaticMethodOnlyUsedInOneClass")
        static void setResult(@NonNull final Fragment fragment,
                              @NonNull final String requestKey,
                              @NonNull final int[] fieldIds,
                              @Nullable final long... selections) {
            final Bundle result = new Bundle(2);
            result.putIntArray(FIELD_ID, fieldIds);
            result.putLongArray(SELECTIONS, selections);
            fragment.getParentFragmentManager().setFragmentResult(requestKey, result);
        }

        public void registerForFragmentResult(@NonNull final FragmentManager fragmentManager,
                                              @NonNull final LifecycleOwner lifecycleOwner) {
            this.fragmentManager = fragmentManager;
            this.fragmentManager.setFragmentResultListener(requestKey, lifecycleOwner, this);
        }

        public void setDateParser(@NonNull final DateParser dateParser) {
            this.dateParser = dateParser;
        }

        /**
         * Parse the given date String to the number of milliseconds
         * from the epoch of 1970-01-01T00:00:00Z.
         * and return a {@code Long} suitable to use as a 'selection' for the Android date-picker.
         *
         * @param value       to parse
         * @param todayIfNone flag
         *
         * @return 'selection' value, otherwise {@code null}
         */
        @Nullable
        private Long parseToInstant(@Nullable final CharSequence value,
                                    final boolean todayIfNone) {
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
         * Launch the dialog to select a single date.
         *
         * @param titleResId  for the dialog screen
         * @param fieldId     field this dialog is bound to
         * @param value       current selection (a parsable date string),
         *                    or {@code null} for none
         * @param todayIfNone if {@code true}, and if the field value empty,
         *                    default to today's date.
         */
        public void launch(@StringRes final int titleResId,
                           @IdRes final int fieldId,
                           @Nullable final CharSequence value,
                           final boolean todayIfNone) {

            final Long selection = parseToInstant(value, todayIfNone);

            new WrappedMaterialDatePicker<>(requestKey,
                                            MaterialDatePicker.Builder
                                                    .datePicker()
                                                    .setTitleText(titleResId)
                                                    .setSelection(selection)
                                                    .build(),
                                            fieldId)
                    .show(fragmentManager);
        }

        /**
         * Launch the dialog to select a single date.
         *
         * @param titleResId for the dialog screen
         * @param fieldId    field this dialog is bound to
         * @param time       current selection, or {@code null} for none
         */
        public void launch(@StringRes final int titleResId,
                           @IdRes final int fieldId,
                           @Nullable final LocalDateTime time) {

            final Long selection =
                    time != null ? time.toInstant(ZoneOffset.UTC).toEpochMilli() : null;

            new WrappedMaterialDatePicker<>(requestKey,
                                            MaterialDatePicker.Builder
                                                    .datePicker()
                                                    .setTitleText(titleResId)
                                                    .setSelection(selection)
                                                    .build(),
                                            fieldId)
                    .show(fragmentManager);
        }

        /**
         * Launch the dialog to select a date span.
         *
         * @param titleResId   for the dialog screen
         * @param startFieldId field for the start-date this dialog is bound to
         * @param timeStart    current start-selection, or {@code null} for none
         * @param endFieldId   field for the end-date this dialog is bound to
         * @param timeEnd      current end-selection, or {@code null} for none
         * @param todayIfNone  flag; if timeStart/timeEnd is not set, {@code true} will
         *                     set {@code today} as their value.
         */
        public void launch(@StringRes final int titleResId,
                           @IdRes final int startFieldId,
                           @Nullable final CharSequence timeStart,
                           @IdRes final int endFieldId,
                           @Nullable final CharSequence timeEnd,
                           final boolean todayIfNone) {

            Long startSelection = parseToInstant(timeStart, todayIfNone);
            Long endSelection = parseToInstant(timeEnd, todayIfNone);

            // both set ? then make sure the order is correct
            if (startSelection != null && endSelection != null && startSelection > endSelection) {
                final Long tmp = startSelection;
                startSelection = endSelection;
                endSelection = tmp;
            }

            new WrappedMaterialDatePicker<>(requestKey,
                                            MaterialDatePicker.Builder
                                                    .dateRangePicker()
                                                    .setTitleText(titleResId)
                                                    .setSelection(new Pair<>(startSelection,
                                                                             endSelection))
                                                    .build(),
                                            startFieldId, endFieldId)
                    .show(fragmentManager);
        }

        @Override
        public void onFragmentResult(@NonNull final String requestKey,
                                     @NonNull final Bundle result) {
            resultListener.onResult(
                    Objects.requireNonNull(result.getIntArray(FIELD_ID), FIELD_ID),
                    Objects.requireNonNull(result.getLongArray(SELECTIONS), SELECTIONS));
        }

        @FunctionalInterface
        public interface ResultListener {
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
             * @param fieldIds   one or two field resource ids this dialog was bound to
             * @param selections one or two values with the selected date(s);
             *                   either/both can be {@code null}
             */
            void onResult(@NonNull int[] fieldIds,
                          @NonNull long[] selections);
        }
    }
}
