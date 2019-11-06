/*
 * @Copyright 2019 HardBackNutter
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
package com.hardbacknutter.nevertoomanybooks.dialogs;

import android.annotation.SuppressLint;
import android.app.Dialog;
import android.content.Context;
import android.content.DialogInterface;
import android.os.Bundle;
import android.text.format.DateFormat;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.NumberPicker;

import androidx.annotation.CallSuper;
import androidx.annotation.IdRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import androidx.appcompat.app.AlertDialog;
import androidx.constraintlayout.widget.ConstraintLayout;
import androidx.fragment.app.DialogFragment;

import java.lang.ref.WeakReference;
import java.util.Calendar;
import java.util.Locale;

import com.hardbacknutter.nevertoomanybooks.BuildConfig;
import com.hardbacknutter.nevertoomanybooks.DEBUG_SWITCHES;
import com.hardbacknutter.nevertoomanybooks.R;
import com.hardbacknutter.nevertoomanybooks.UniqueId;
import com.hardbacknutter.nevertoomanybooks.debug.Logger;
import com.hardbacknutter.nevertoomanybooks.utils.DateUtils;
import com.hardbacknutter.nevertoomanybooks.utils.UserMessage;

/**
 * DialogFragment class to allow for selection of partial dates from 0AD to 9999AD.
 * <p>
 * Seems reasonable to disable relevant spinners if one is invalid, but it's actually
 * not very friendly when entering data for new books so we don't.
 * So for instance, if a day/month/year are set, and the user select "--" (unset) the month,
 * we leave the day setting unchanged.
 * A final validity check is done when trying to accept the date.
 */
public class PartialDatePickerDialogFragment
        extends DialogFragment {

    /** Fragment manager tag. */
    public static final String TAG = "PartialDatePickerDialogFragment";

    /** a standard sql style date string, must be correct. */
    private static final String BKEY_DATE = TAG + ":date";
    /** or the date split into components, which can partial. */
    private static final String BKEY_YEAR = TAG + ":year";
    /** range: 1..12. */
    private static final String BKEY_MONTH = TAG + ":month";
    private static final String BKEY_DAY = TAG + ":day";

    private static final String UNKNOWN_MONTH = "---";
    private static final String UNKNOWN_DAY = "--";

    /** All month names (abbreviated). */
    private final String[] mMonthNames = new String[13];

    /** Used for reading month names + calculating number of days in a month. */
    private Calendar mCalendarForCalculations;
    /** Cache the current year. */
    private int mCurrentYear;

    /** identifier of the field this dialog is bound to. */
    @IdRes
    private int mDestinationFieldId;
    /**
     * Currently displayed; {@code null} if empty/invalid.
     * The value is automatically updated by the dialog after every change.
     */
    @Nullable
    private Integer mYear;
    /** IMPORTANT: 1..12. (the jdk internals expect 0..11). */
    @Nullable
    private Integer mMonth;
    @Nullable
    private Integer mDay;
    private WeakReference<PartialDatePickerResultsListener> mListener;

    /**
     * Constructor.
     *
     * @param fieldId       the field whose content we want to edit
     * @param currentValue  the current value of the field
     * @param dialogTitleId resource id for the dialog title
     * @param todayIfNone   {@code true} if we should use 'today' if the field was empty.
     *
     * @return the new instance
     */
    public static PartialDatePickerDialogFragment newInstance(@IdRes final int fieldId,
                                                              @NonNull final String currentValue,
                                                              @StringRes final int dialogTitleId,
                                                              final boolean todayIfNone) {
        String date;
        if (todayIfNone && currentValue.isEmpty()) {
            date = DateUtils.localSqlDateForToday();
        } else {
            date = currentValue;
        }
        if (BuildConfig.DEBUG && DEBUG_SWITCHES.DATETIME) {
            Logger.debug(PartialDatePickerDialogFragment.class, "newInstance",
                         "date.toString(): " + date);
        }

        PartialDatePickerDialogFragment frag = new PartialDatePickerDialogFragment();
        Bundle args = new Bundle();
        args.putInt(UniqueId.BKEY_DIALOG_TITLE, dialogTitleId);
        args.putInt(UniqueId.BKEY_FIELD_ID, fieldId);
        args.putString(PartialDatePickerDialogFragment.BKEY_DATE, date);
        frag.setArguments(args);
        return frag;
    }

    @Override
    public void onCreate(@Nullable final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Get a calendar for Locale related info (defaults to current date)
        mCalendarForCalculations = Calendar.getInstance(Locale.getDefault());

        mCurrentYear = mCalendarForCalculations.get(Calendar.YEAR);

        // Set the day to 1 to avoid wrapping.
        mCalendarForCalculations.set(Calendar.DAY_OF_MONTH, 1);
        // First entry is 'unknown'
        mMonthNames[0] = UNKNOWN_MONTH;
        // Add all month names (abbreviated)
        for (int i = 1; i <= 12; i++) {
            mCalendarForCalculations.set(Calendar.MONTH, i - 1);
            mMonthNames[i] = String.format("%tb", mCalendarForCalculations);
        }

        Bundle args = requireArguments();
        mDestinationFieldId = args.getInt(UniqueId.BKEY_FIELD_ID);

        args = savedInstanceState != null ? savedInstanceState : args;
        if (args.containsKey(BKEY_DATE)) {
            // BKEY_DATE is only present in the original args
            setDate(args.getString(BKEY_DATE));
        } else {
            // These are only present in the savedInstanceState
            mYear = args.getInt(BKEY_YEAR);
            mMonth = args.getInt(BKEY_MONTH);
            mDay = args.getInt(BKEY_DAY);
        }

        // can't have a null year. The user can/should use the "clear" button if needed.
        if (mYear == null) {
            mYear = mCurrentYear;
        }
    }

    @NonNull
    @Override
    public Dialog onCreateDialog(@Nullable final Bundle savedInstanceState) {
        //noinspection ConstantConditions
        AlertDialog dialog = new PartialDatePickerDialog(getContext());
        Bundle args = getArguments();
        if (args != null) {
            @StringRes
            int titleId = args.getInt(UniqueId.BKEY_DIALOG_TITLE, R.string.edit);
            if (titleId != 0) {
                dialog.setTitle(titleId);
            }
        }
        return dialog;
    }

    @Override
    @CallSuper
    public void onSaveInstanceState(@NonNull final Bundle outState) {
        super.onSaveInstanceState(outState);
        if (mYear != null) {
            outState.putInt(BKEY_YEAR, mYear);
        }
        if (mMonth != null) {
            outState.putInt(BKEY_MONTH, mMonth);
        }
        if (mDay != null) {
            outState.putInt(BKEY_DAY, mDay);
        }
    }

    /**
     * Set the dialog OnClickListener. This allows us to validate the fields without
     * having the dialog close on us after the user clicks a button.
     */
    @Override
    public void onResume() {
        super.onResume();
        final AlertDialog dialog = (AlertDialog) getDialog();
        if (dialog != null) {
            dialog.getButton(Dialog.BUTTON_NEGATIVE).setOnClickListener(v -> dismiss());
            dialog.getButton(Dialog.BUTTON_NEUTRAL).setOnClickListener(v -> {
                mYear = null;
                mMonth = null;
                mDay = null;
                send();
            });
            dialog.getButton(Dialog.BUTTON_POSITIVE).setOnClickListener(v -> {
                if (mDay != null && mDay > 0 && (mMonth == null || mMonth == 0)) {
                    //noinspection ConstantConditions
                    UserMessage.show(getDialog().getWindow().getDecorView(),
                                     R.string.warning_if_day_set_month_and_year_must_be);

                } else if (mMonth != null && mMonth > 0 && mYear == null) {
                    //noinspection ConstantConditions
                    UserMessage.show(getDialog().getWindow().getDecorView(),
                                     R.string.warning_if_month_set_year_must_be);

                } else {
                    send();
                }
            });
        }
    }

    /**
     * Send the date back to the listener.
     */
    private void send() {
        dismiss();

        if (mListener.get() != null) {
            mListener.get().onPartialDatePickerSave(mDestinationFieldId, mYear, mMonth, mDay);
        } else {
            if (BuildConfig.DEBUG && DEBUG_SWITCHES.TRACE_WEAK_REFERENCES) {
                Logger.debug(this, "onPartialDatePickerSave",
                             Logger.WEAK_REFERENCE_TO_LISTENER_WAS_DEAD);
            }
        }
    }

    /**
     * Private helper, NOT a public accessor.
     * <ul>Allows partial dates:
     * <li>yyyy-mm-dd time</li>
     * <li>yyyy-mm-dd</li>
     * <li>yyyy-mm</li>
     * <li>yyyy</li>
     * </ul>
     *
     * @param dateString SQL formatted (partial) date, can be {@code null}.
     */
    private void setDate(@Nullable final String dateString) {
        if (dateString == null || dateString.isEmpty()) {
            mYear = mCurrentYear;
            mMonth = null;
            mDay = null;
            return;
        }

        Integer yyyy = null;
        Integer mm = null;
        Integer dd = null;
        try {
            String[] dateAndTime = dateString.split(" ");
            String[] date = dateAndTime[0].split("-");
            yyyy = Integer.parseInt(date[0]);
            if (date.length > 1) {
                mm = Integer.parseInt(date[1]);
            }
            if (date.length > 2) {
                dd = Integer.parseInt(date[2]);
            }
        } catch (@NonNull final NumberFormatException ignore) {
            // ignore. Any values we did get, are used.
        }

        mYear = yyyy;
        mMonth = mm;
        mDay = dd;
    }

    /**
     * Call this from {@link #onAttachFragment} in the parent.
     *
     * @param listener the object to send the result to.
     */
    public void setListener(@NonNull final PartialDatePickerResultsListener listener) {
        mListener = new WeakReference<>(listener);
    }

    /**
     * Listener interface to receive notifications when dialog is closed by any means.
     */
    public interface PartialDatePickerResultsListener {

        void onPartialDatePickerSave(@IdRes int destinationFieldId,
                                     @Nullable Integer year,
                                     @Nullable Integer month,
                                     @Nullable Integer day);
    }

    /**
     * Custom dialog. Button OnClickListener's must be set in the fragment onResume method.
     */
    class PartialDatePickerDialog
            extends AlertDialog {

        private final NumberPicker mDayPicker;

        /** Called after any change made to the pickers. */
        @SuppressWarnings("FieldCanBeLocal")
        private final NumberPicker.OnValueChangeListener mOnValueChangeListener =
                new NumberPicker.OnValueChangeListener() {
                    @Override
                    public void onValueChange(@NonNull final NumberPicker picker,
                                              final int oldVal,
                                              final int newVal) {
                        switch (picker.getId()) {
                            case R.id.PICKER_YEAR:
                                mYear = newVal;
                                // Small optimization: only February needs this
                                if (mMonth != null && mMonth == 2) {
                                    setDaysOfMonth();
                                }
                                break;
                            case R.id.PICKER_MONTH:
                                mMonth = newVal;
                                setDaysOfMonth();
                                break;
                            case R.id.PICKER_DAY:
                                mDay = newVal;
                                break;

                            default:
                                Logger.warnWithStackTrace(getContext(), this,
                                                          "id=" + picker.getId());
                                break;
                        }
                    }
                };

        /**
         * Constructor.
         *
         * <strong>Note:</strong> we explicitly pass in the inflater (independent from the
         * context) so we are 100% sure we're using the same one as in {@link #onCreateDialog}.
         * Call it paranoia.
         *
         * @param context Current context
         */
        PartialDatePickerDialog(@NonNull final Context context) {
            super(context);

            @SuppressLint("InflateParams")
            View root = LayoutInflater.from(context)
                                      .inflate(R.layout.dialog_partial_date_picker, null);

            // Ensure components match current Locale order
            reorderPickers(root);

            // Set the view
            setView(root);

            NumberPicker yearPicker = root.findViewById(R.id.year);
            yearPicker.setId(R.id.PICKER_YEAR);
            yearPicker.setMinValue(0);
            // we're optimistic...
            yearPicker.setMaxValue(2100);
            yearPicker.setOnValueChangedListener(mOnValueChangeListener);

            NumberPicker monthPicker = root.findViewById(R.id.month);
            monthPicker.setId(R.id.PICKER_MONTH);
            monthPicker.setMinValue(0);
            // 12 months + the 'not set'
            monthPicker.setMaxValue(12);
            monthPicker.setDisplayedValues(mMonthNames);
            monthPicker.setOnValueChangedListener(mOnValueChangeListener);

            mDayPicker = root.findViewById(R.id.day);
            mDayPicker.setId(R.id.PICKER_DAY);
            mDayPicker.setMinValue(0);
            // Make sure that the spinner can initially take any 'day' value. Otherwise,
            // when a dialog is reconstructed after rotation, the 'day' field will not be
            // restored by Android.
            mDayPicker.setMaxValue(31);
            mDayPicker.setFormatter(value -> value == 0 ? UNKNOWN_DAY : String.valueOf(value));
            mDayPicker.setOnValueChangedListener(mOnValueChangeListener);

            // initial date
            yearPicker.setValue(mYear != null ? mYear : mCurrentYear);
            monthPicker.setValue(mMonth != null ? mMonth : 0);
            mDayPicker.setValue(mDay != null ? mDay : 0);
            setDaysOfMonth();

            // no listeners. They must be set in the onResume of the fragment.
            setButton(DialogInterface.BUTTON_NEGATIVE, getString(android.R.string.cancel),
                      (DialogInterface.OnClickListener) null);
            setButton(DialogInterface.BUTTON_NEUTRAL, getString(R.string.clear),
                      (DialogInterface.OnClickListener) null);
            setButton(DialogInterface.BUTTON_POSITIVE, getString(android.R.string.ok),
                      (DialogInterface.OnClickListener) null);
        }

        /**
         * Depending on year/month selected, set the correct number of days.
         */
        private void setDaysOfMonth() {
            // Save the current day in case the regen alters it
            Integer daySave = mDay;

            // Determine the total days if we have a valid month/year
            int totalDays;
            if (mYear != null && mMonth != null && mMonth > 0) {
                mCalendarForCalculations.set(Calendar.YEAR, mYear);
                mCalendarForCalculations.set(Calendar.MONTH, mMonth - 1);
                totalDays = mCalendarForCalculations.getActualMaximum(Calendar.DAY_OF_MONTH);
            } else {
                // allow the user to start inputting with day first.
                totalDays = 31;
            }

            mDayPicker.setMaxValue(totalDays);

            // Ensure selected day is valid
            if (daySave == null || daySave == 0) {
                mDayPicker.setValue(0);
            } else {
                if (daySave > totalDays) {
                    daySave = totalDays;
                }
                mDayPicker.setValue(daySave);
            }
        }

        /**
         * Reorder the views in the dialog to suit the current Locale.
         *
         * @param root Root view
         */
        private void reorderPickers(@NonNull final View root) {
            char[] order;
            try {
                // This actually throws exception in some versions of Android, specifically when
                // the Locale specific date format has the day name (EEE) in it. So we exit and
                // just use our default order in these cases.
                // See Issue #712.
                order = DateFormat.getDateFormatOrder(getContext());
            } catch (@NonNull final RuntimeException e) {
                return;
            }

            // Default order is {year, month, day} so if that's the order then do nothing.
            if ((order[0] == 'y') && (order[1] == 'M')) {
                return;
            }

            // Remove the 3 pickers from their parent and add them back in the required order.
            ViewGroup parent = root.findViewById(R.id.dateSelector);
            // Get the three views
            ConstraintLayout y = parent.findViewById(R.id.yearSelector);
            ConstraintLayout m = parent.findViewById(R.id.monthSelector);
            ConstraintLayout d = parent.findViewById(R.id.daySelector);
            // Remove them
            parent.removeAllViews();
            // Re-add in the correct order.
            for (char c : order) {
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

    }
}
