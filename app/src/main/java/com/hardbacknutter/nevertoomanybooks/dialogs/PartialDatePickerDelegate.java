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

package com.hardbacknutter.nevertoomanybooks.dialogs;

import android.os.Bundle;
import android.text.format.DateFormat;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.NumberPicker;

import androidx.annotation.IdRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.widget.Toolbar;
import androidx.constraintlayout.widget.ConstraintLayout;
import androidx.fragment.app.DialogFragment;
import androidx.lifecycle.ViewModelProvider;

import com.google.android.material.snackbar.Snackbar;

import java.time.DateTimeException;
import java.time.LocalDate;
import java.time.Month;
import java.time.format.TextStyle;
import java.util.Locale;
import java.util.Objects;

import com.hardbacknutter.nevertoomanybooks.BuildConfig;
import com.hardbacknutter.nevertoomanybooks.R;
import com.hardbacknutter.nevertoomanybooks.core.utils.PartialDate;
import com.hardbacknutter.nevertoomanybooks.databinding.DialogPartialDatePickerContentBinding;
import com.hardbacknutter.util.logger.LoggerFactory;

/**
 * DialogFragment class to allow for selection of partial dates from 0AD to 9999AD.
 * <p>
 * Seems reasonable to disable relevant day/month pickers if one is invalid, but it's actually
 * not very friendly when entering data for new books so we don't.
 * So for instance, if a day/month/year are set, and the user select "--" (unset) the month,
 * we leave the day setting unchanged.
 * A final validity check is done when trying to accept the date.
 */
class PartialDatePickerDelegate
        implements FlexDialogDelegate {

    private static final String TAG = "PartialDatePickerDelega";
    /** Displayed to user: unset month. */
    private static final String UNKNOWN_MONTH = "---";
    /** Displayed to user: unset day. */
    private static final String UNKNOWN_DAY = "--";

    /**
     * Maximum number of months in a year. Kept as a constant as I'm reasonably sure
     * this will not change in the foreseeable future. (⌐⊙_⊙)
     */
    private static final int MAX_MONTHS = 12;
    /** Maximum number of days in a month. */
    private static final int MAX_DAYS = 31;

    @NonNull
    private final DialogFragment owner;
    @NonNull
    private final String requestKey;
    @NonNull
    private final String dialogTitle;
    @IdRes
    private final int fieldId;
    private PartialDatePickerViewModel vm;
    private DialogPartialDatePickerContentBinding vb;
    @Nullable
    private Toolbar toolbar;

    /** This listener is called after <strong>any change</strong> made to the pickers. */
    private final NumberPicker.OnValueChangeListener valueChangeListener =
            (picker, oldVal, newVal) -> {
                final int pickerId = picker.getId();

                if (pickerId == R.id.year) {
                    vm.setYear(newVal);
                    // only February can be different number of days
                    if (vm.getMonth() == 2) {
                        updateDaysInMonth();
                    }

                } else if (pickerId == R.id.month) {
                    vm.setMonth(newVal);
                    updateDaysInMonth();

                } else if (pickerId == R.id.day) {
                    vm.setDay(newVal);

                } else {
                    if (BuildConfig.DEBUG /* always */) {
                        LoggerFactory.getLogger()
                                     .d(TAG, "valueChangeListener", "id=" + picker.getId());
                    }
                }
            };

    PartialDatePickerDelegate(@NonNull final DialogFragment owner,
                              @NonNull final Bundle args) {
        this.owner = owner;
        requestKey = Objects.requireNonNull(args.getString(DialogLauncher.BKEY_REQUEST_KEY),
                                            DialogLauncher.BKEY_REQUEST_KEY);
        //noinspection DataFlowIssue
        dialogTitle = owner.getContext().getString(
                args.getInt(PartialDatePickerLauncher.BKEY_DIALOG_TITLE_ID, R.string.action_edit));

        fieldId = args.getInt(PartialDatePickerLauncher.BKEY_FIELD_ID);

        vm = new ViewModelProvider(owner).get(PartialDatePickerViewModel.class);
        vm.init(args);
    }

    /**
     * Reorder the views in the dialog to suit the current Locale.
     *
     * @param root Root view
     */
    private void reorderPickers(@NonNull final View root) {
        final char[] order;
        try {
            // This actually throws exception in some versions of Android, specifically when
            // the Locale specific date format has the day name (EEE) in it. So we exit and
            // just use our default order in these cases.
            //   java.lang.IllegalArgumentException: Bad pattern character 'E' in E, d MMM yyyy
            //   at libcore.icu.ICU.getDateFormatOrder(ICU.java:165)
            //   at android.text.format.DateFormat.getDateFormatOrder(DateFormat.java:384)
            //
            // The underlying code relies on this code ONLY returning patterns with
            // d,L,M,y,G characters.
            // java.text.DateFormat.getDateInstance(java.text.DateFormat.SHORT, locale)
            //     .toPattern();
            order = DateFormat.getDateFormatOrder(root.getContext());
        } catch (@NonNull final RuntimeException e) {
            return;
        }

        // Default order is {year, month, day} so if that's the order do nothing.
        if (order[0] == 'y' && order[1] == 'M') {
            return;
        }

        // Remove the 3 pickers from their parent and add them back in the required order.
        final ViewGroup parent = root.findViewById(R.id.dateSelector);
        // Get the three views
        final ConstraintLayout y = parent.findViewById(R.id.yearSelector);
        final ConstraintLayout m = parent.findViewById(R.id.monthSelector);
        final ConstraintLayout d = parent.findViewById(R.id.daySelector);
        // Remove them
        parent.removeAllViews();
        // Re-add in the correct order.
        for (final char c : order) {
            switch (c) {
                case 'd':
                    parent.addView(d);
                    break;
                case 'M':
                    parent.addView(m);
                    break;
                default:
                    parent.addView(y);
                    break;
            }
        }
    }

    @NonNull
    @Override
    public View onCreateView(@NonNull final LayoutInflater inflater,
                             @Nullable final ViewGroup container) {
        // Ensure components match current Locale order BEFORE we bind the views.
        final View view = inflater.inflate(R.layout.dialog_partial_date_picker_content,
                                           container, false);
        reorderPickers(view);
        vb = DialogPartialDatePickerContentBinding.bind(view);
        return vb.getRoot();
    }

    @Override
    public void onCreateView(@NonNull final View view) {
        // Ensure components match current Locale order BEFORE we bind the views.
        reorderPickers(view);
        vb = DialogPartialDatePickerContentBinding.bind(view.findViewById(R.id.dialog_content));
    }

    @Override
    public void setToolbar(@Nullable final Toolbar toolbar) {
        this.toolbar = toolbar;
    }

    @Override
    public void onViewCreated() {
        if (toolbar != null) {
            initToolbar(toolbar);
        }

        // 0: 'not set'
        vb.year.setMinValue(0);
        // we're optimistic...
        vb.year.setMaxValue(2100);
        vb.year.setOnValueChangedListener(valueChangeListener);

        // 0: 'not set' + 1..12 real months
        vb.month.setMinValue(0);
        vb.month.setMaxValue(MAX_MONTHS);
        vb.month.setDisplayedValues(getMonthAbbr());
        vb.month.setOnValueChangedListener(valueChangeListener);

        // 0: 'not set'
        vb.day.setMinValue(0);
        // Make sure that the picker can initially take any 'day' value. Otherwise,
        // when a dialog is reconstructed after rotation, the 'day' field will not be
        // restored by Android.
        vb.day.setMaxValue(MAX_DAYS);
        vb.day.setFormatter(value -> value == 0 ? UNKNOWN_DAY : String.valueOf(value));
        vb.day.setOnValueChangedListener(valueChangeListener);

        // set the initial date
        vb.year.setValue(vm.getYear() != 0 ? vm.getYear() : LocalDate.now().getYear());
        vb.month.setValue(vm.getMonth());
        vb.day.setValue(vm.getDay());
        updateDaysInMonth();
    }

    @Override
    public void initToolbar(@NonNull final Toolbar toolbar) {
        FlexDialogDelegate.super.initToolbar(toolbar);
        toolbar.setTitle(dialogTitle);
    }

    @Override
    public void onToolbarNavigationClick(@NonNull final View v) {
        owner.dismiss();
    }

    @Override
    public boolean onToolbarButtonClick(@Nullable final View button) {
        if (button != null) {
            final int id = button.getId();
            if (id == R.id.btn_save || id == R.id.btn_positive) {
                if (saveChanges()) {
                    owner.dismiss();
                }
                return true;
            }
        }
        return false;
    }

    private boolean saveChanges() {
        if (vm.getDay() != 0 && vm.getMonth() == 0) {
            Snackbar.make(vb.getRoot(), R.string.warning_if_day_set_month_and_year_must_be,
                          Snackbar.LENGTH_LONG).show();

        } else if (vm.getMonth() != 0 && vm.getYear() == 0) {
            Snackbar.make(vb.getRoot(), R.string.warning_if_month_set_year_must_be,
                          Snackbar.LENGTH_LONG).show();

        } else {
            PartialDatePickerLauncher.setResult(owner, requestKey, fieldId,
                                                new PartialDate(vm.getYear(),
                                                                vm.getMonth(),
                                                                vm.getDay()));
            return true;
        }

        return false;
    }

    /**
     * Generate the month names (abbreviated). There are 13: first entry being 'unknown'.
     *
     * @return short month names
     */
    @NonNull
    private String[] getMonthAbbr() {
        final Locale userLocale = vb.getRoot().getResources().getConfiguration().getLocales()
                                    .get(0);
        final String[] monthNames = new String[13];
        monthNames[0] = UNKNOWN_MONTH;
        for (int i = 1; i <= MAX_MONTHS; i++) {
            monthNames[i] = Month.of(i).getDisplayName(TextStyle.SHORT, userLocale);
        }
        return monthNames;
    }

    /**
     * Depending on year/month selected, set the correct number of days.
     */
    private void updateDaysInMonth() {
        int currentlySelectedDay = vm.getDay();

        // Determine the total days if we have a valid month/year
        int totalDays;
        if (vm.getYear() != 0 && vm.getMonth() != 0) {
            try {
                // Should never throw here, but paranoia...
                totalDays = LocalDate.of(vm.getYear(), vm.getMonth(), 1).lengthOfMonth();
            } catch (@NonNull final DateTimeException e) {
                totalDays = MAX_DAYS;
            }
        } else {
            // allow the user to start inputting with day first.
            totalDays = MAX_DAYS;
        }

        vb.day.setMaxValue(totalDays);

        // Ensure selected day is valid
        if (currentlySelectedDay == 0) {
            vb.day.setValue(0);
        } else {
            if (currentlySelectedDay > totalDays) {
                currentlySelectedDay = totalDays;
            }
            vb.day.setValue(currentlySelectedDay);
        }
    }
}
