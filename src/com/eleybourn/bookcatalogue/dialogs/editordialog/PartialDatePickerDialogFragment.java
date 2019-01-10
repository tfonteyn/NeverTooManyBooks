/*
 * @copyright 2013 Philip Warner
 * @license GNU General Public License
 *
 * This file is part of Book Catalogue.
 *
 * Book Catalogue is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Book Catalogue is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with Book Catalogue.  If not, see <http://www.gnu.org/licenses/>.
 */
package com.eleybourn.bookcatalogue.dialogs.editordialog;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.Dialog;
import android.os.Bundle;
import android.text.Editable;
import android.text.Selection;
import android.text.TextWatcher;
import android.text.format.DateFormat;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.Spinner;

import androidx.annotation.CallSuper;
import androidx.annotation.IdRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;

import com.eleybourn.bookcatalogue.R;
import com.eleybourn.bookcatalogue.debug.Tracker;
import com.eleybourn.bookcatalogue.dialogs.StandardDialogs;

import java.util.Calendar;
import java.util.Objects;

/**
 * TODO: as the Dialog is now an inner class, remove the listener between DialogFragment and Dialog.
 * <p>
 * DialogFragment class to allow for selection of partial dates from 0AD to 9999AD.
 *
 * @author pjw
 */
public class PartialDatePickerDialogFragment
        extends
        EditorDialogFragment<PartialDatePickerDialogFragment.OnPartialDatePickerResultsListener> {

    /** a standard sql style date string, must be correct. */
    public static final String BKEY_DATE = "date";
    /** or the date split into components, which can partial. */
    public static final String BKEY_YEAR = "year";
    public static final String BKEY_MONTH = "month";
    public static final String BKEY_DAY = "day";
    /**
     * Object to handle changes.
     */
    private final PartialDatePickerDialog.OnPartialDatePickerResultsListener mDialogListener =
            new PartialDatePickerDialog.OnPartialDatePickerResultsListener() {
                public void onPartialDatePickerSave(final Integer year,
                                                    final Integer month,
                                                    final Integer day) {
                    getFragmentListener()
                            .onPartialDatePickerSave(PartialDatePickerDialogFragment.this,
                                                     mDestinationFieldId, year, month, day);
                }

                @Override
                public void onPartialDatePickerCancel() {
                    getFragmentListener()
                            .onPartialDatePickerCancel(PartialDatePickerDialogFragment.this,
                                                       mDestinationFieldId);
                }
            };

    /** Currently displayed; null if empty/invalid. */
    @Nullable
    private Integer mYear;
    @Nullable
    private Integer mMonth;
    @Nullable
    private Integer mDay;

    /**
     * Create the underlying dialog.
     */
    @NonNull
    @Override
    public Dialog onCreateDialog(@Nullable final Bundle savedInstanceState) {
        initStandardArgs(savedInstanceState);

        if (savedInstanceState != null) {
            if (savedInstanceState.containsKey(BKEY_DATE)) {
                setDate(savedInstanceState.getString(BKEY_DATE, ""));
            } else {
                mYear = savedInstanceState.getInt(BKEY_YEAR);
                mMonth = savedInstanceState.getInt(BKEY_MONTH);
                mDay = savedInstanceState.getInt(BKEY_DAY);
            }
        } else {
            Bundle args = getArguments();
            Objects.requireNonNull(args);
            if (args.containsKey(BKEY_DATE)) {
                setDate(args.getString(BKEY_DATE, ""));
            } else {
                mYear = args.getInt(BKEY_YEAR);
                mMonth = args.getInt(BKEY_MONTH);
                mDay = args.getInt(BKEY_DAY);
            }
        }

        // Create the dialog and listen (locally) for its events
        PartialDatePickerDialog editor = new PartialDatePickerDialog(requireActivity());
        if (mTitleId != 0) {
            editor.setTitle(mTitleId);
        }
        editor.setDate(mYear, mMonth, mDay);
        editor.setResultsListener(mDialogListener);
        return editor;
    }

    /**
     * Private helper, NOT a public accessor which would be violating the Fragment contract.
     *
     * @param dateString SQL formatted date, may be null
     */
    private void setDate(@NonNull final String dateString) {
        Integer yyyy = null;
        Integer mm = null;
        Integer dd = null;
        try {
            String[] dateAndTime = dateString.split(" ");
            String[] date = dateAndTime[0].split("-");
            yyyy = Integer.parseInt(date[0]);
            mm = Integer.parseInt(date[1]);
            dd = Integer.parseInt(date[2]);
        } catch (NumberFormatException ignore) {
        }

        setDate(yyyy, mm, dd);
    }

    /**
     * Private helper, NOT a public accessor which would be violating the Fragment contract.
     */
    private void setDate(@Nullable final Integer year,
                         @Nullable final Integer month,
                         @Nullable final Integer day) {
        mYear = year;
        mMonth = month;
        mDay = day;
        PartialDatePickerDialog d = (PartialDatePickerDialog) getDialog();
        if (d != null) {
            d.setDate(mYear, mMonth, mDay);
        }
    }

    @Override
    @CallSuper
    public void onSaveInstanceState(@NonNull final Bundle outState) {
        if (mYear != null) {
            outState.putInt(BKEY_YEAR, mYear);
        }
        if (mMonth != null) {
            outState.putInt(BKEY_MONTH, mMonth);
        }
        if (mDay != null) {
            outState.putInt(BKEY_DAY, mDay);
        }
        super.onSaveInstanceState(outState);
    }

    /**
     * Make sure data is saved in onPause() because onSaveInstanceState()
     * will have lost the views.
     */
    @Override
    @CallSuper
    public void onPause() {
        Tracker.enterOnPause(this);
        PartialDatePickerDialog dialog = (PartialDatePickerDialog) getDialog();
        if (dialog != null) {
            mYear = dialog.getYear();
            mMonth = dialog.getMonth();
            mDay = dialog.getDay();
        }
        super.onPause();
        Tracker.exitOnPause(this);
    }

    /**
     * Listener interface to receive notifications when dialog is closed by any means.
     */
    public interface OnPartialDatePickerResultsListener {

        void onPartialDatePickerSave(@NonNull final PartialDatePickerDialogFragment dialog,
                                     @IdRes final int destinationFieldId,
                                     @Nullable final Integer year,
                                     @Nullable final Integer month,
                                     @Nullable final Integer day);

        void onPartialDatePickerCancel(@NonNull final PartialDatePickerDialogFragment dialog,
                                       @IdRes final int destinationFieldId);
    }


    static class PartialDatePickerDialog
            extends AlertDialog {

        private static final String UNKNOWN_MONTH = "---";
        private static final String UNKNOWN_DAY = "--";
        /** Calling context. */
        @NonNull
        private final Activity mActivity;
        /** Local ref to month spinner. */
        private final Spinner mMonthSpinner;
        /** Local ref to day spinner. */
        private final Spinner mDaySpinner;
        /** Local ref to day spinner adapter. */
        @NonNull
        private final ArrayAdapter<String> mDayAdapter;
        /** Local ref to year text view. */
        private final EditText mYearView;
        /** Currently displayed year; null if empty/invalid. */
        @Nullable
        private Integer mYear;
        /** Currently displayed month; null if empty/invalid. */
        @Nullable
        private Integer mMonth;
        /** Currently displayed day; null if empty/invalid. */
        @Nullable
        private Integer mDay;
        /** Listener to be called when date is set or dialog cancelled. */
        private OnPartialDatePickerResultsListener mListener;

        /**
         * Constructor.
         *
         * @param activity Calling context
         */
        PartialDatePickerDialog(@NonNull final Activity activity) {
            this(activity, null, null, null);
        }

        /**
         * Constructor.
         *
         * @param activity Calling context
         * @param year     Starting year
         * @param month    Starting month
         * @param day      Starting day
         */
        @SuppressWarnings("SameParameterValue")
        private PartialDatePickerDialog(@NonNull final Activity activity,
                                        @Nullable final Integer year,
                                        @Nullable final Integer month,
                                        @Nullable final Integer day) {
            super(activity);

            mActivity = activity;

            mYear = year;
            mMonth = month;
            mDay = day;

            // Get the layout
            LayoutInflater inf = this.getLayoutInflater();
            @SuppressLint("InflateParams")
            View root = inf.inflate(R.layout.dialog_partial_date_picker, null);

            // Ensure components match current locale order
            reorderPickers(root);

            // Set the view
            setView(root);

            // Get UI components for later use
            mYearView = root.findViewById(R.id.year);
            mMonthSpinner = root.findViewById(R.id.month);
            mDaySpinner = root.findViewById(R.id.day);

            // Create month spinner adapter
            ArrayAdapter<String> monthAdapter =
                    new ArrayAdapter<>(activity, android.R.layout.simple_spinner_item);
            monthAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
            mMonthSpinner.setAdapter(monthAdapter);

            // Create day spinner adapter
            mDayAdapter = new ArrayAdapter<>(activity, android.R.layout.simple_spinner_item);
            mDayAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
            mDaySpinner.setAdapter(mDayAdapter);

            // First entry is 'unknown'
            monthAdapter.add(UNKNOWN_MONTH);
            mDayAdapter.add(UNKNOWN_DAY);

            // Make sure that the spinner can initially take any 'day' value. Otherwise,
            // when a dialog is reconstructed after rotation, the 'day' field will not be
            // restored by Android.
            regenDaysOfMonth(31);

            // Get a calendar for locale-related info
            Calendar cal = Calendar.getInstance();
            // Set the day to 1...so avoid wrap on short months (default to current date)
            cal.set(Calendar.DAY_OF_MONTH, 1);
            // Add all month named (abbreviated)
            for (int i = 0; i < 12; i++) {
                cal.set(Calendar.MONTH, i);
                monthAdapter.add(String.format("%tb", cal));
            }

            // Handle selections from the MONTH spinner
            mMonthSpinner.setOnItemSelectedListener(
                    new AdapterView.OnItemSelectedListener() {

                        @Override
                        public void onItemSelected(@NonNull final AdapterView<?> parent,
                                                   @NonNull final View view,
                                                   final int position,
                                                   final long id) {
                            int pos = mMonthSpinner.getSelectedItemPosition();
                            handleMonth(pos);
                        }

                        @Override
                        public void onNothingSelected(@NonNull final AdapterView<?> parent) {
                            handleMonth(null);
                        }
                    }
            );

            // Handle selections from the DAY spinner
            mDaySpinner.setOnItemSelectedListener(
                    new AdapterView.OnItemSelectedListener() {

                        @Override
                        public void onItemSelected(@NonNull final AdapterView<?> parent,
                                                   @NonNull final View view,
                                                   final int position,
                                                   final long id) {
                            int pos = mDaySpinner.getSelectedItemPosition();
                            handleDay(pos);
                        }

                        @Override
                        public void onNothingSelected(@NonNull final AdapterView<?> parent) {
                            handleDay(null);
                        }
                    }
            );

            // Handle all changes to the YEAR text
            mYearView.addTextChangedListener(new TextWatcher() {
                @Override
                public void beforeTextChanged(@NonNull final CharSequence s,
                                              final int start,
                                              final int count,
                                              final int after) {
                }

                @Override
                public void onTextChanged(@NonNull final CharSequence s,
                                          final int start,
                                          final int before,
                                          final int count) {
                }

                @Override
                public void afterTextChanged(@NonNull final Editable s) {
                    handleYear();
                }
            });

            // Handle YEAR +
            root.findViewById(R.id.plus).setOnClickListener(
                    new View.OnClickListener() {
                        @Override
                        public void onClick(@NonNull final View v) {
                            String text;
                            if (mYear != null) {
                                text = (++mYear).toString();
                            } else {
                                text = String.valueOf(Calendar.getInstance().get(Calendar.YEAR));
                            }
                            mYearView.setText(text);
                        }
                    }
            );

            // Handle YEAR -
            root.findViewById(R.id.minus).setOnClickListener(
                    new View.OnClickListener() {
                        @Override
                        public void onClick(@NonNull final View v) {
                            String text;
                            if (mYear != null) {
                                // We can't support negative years yet because of sorting
                                // issues and the fact that the Calendar object bugs out
                                // with them. To fix the calendar object interface we
                                // would need to translate -ve years to Epoch settings
                                // throughout the app. For now, not many people have books
                                // written before 0AD, so it's a low priority.
                                if (mYear > 0) {
                                    text = (--mYear).toString();
                                    mYearView.setText(text);
                                }
                            } else {
                                text = String.valueOf(Calendar.getInstance().get(Calendar.YEAR));
                                mYearView.setText(text);
                            }
                        }
                    }
            );

            // Handle MONTH +
            root.findViewById(R.id.plusMonth)
                .setOnClickListener(
                        new View.OnClickListener() {
                            @Override
                            public void onClick(@NonNull final View v) {
                                int pos = (mMonthSpinner.getSelectedItemPosition() + 1)
                                        % mMonthSpinner.getCount();
                                mMonthSpinner.setSelection(pos);
                            }
                        }
                );

            // Handle MONTH -
            root.findViewById(R.id.minusMonth)
                .setOnClickListener(
                        new View.OnClickListener() {
                            @Override
                            public void onClick(@NonNull final View v) {
                                int pos = (mMonthSpinner.getSelectedItemPosition() - 1
                                        + mMonthSpinner.getCount()) % mMonthSpinner.getCount();
                                mMonthSpinner.setSelection(pos);
                            }
                        }
                );

            // Handle DAY +
            root.findViewById(R.id.plusDay)
                .setOnClickListener(
                        new View.OnClickListener() {
                            @Override
                            public void onClick(@NonNull final View v) {
                                int pos = (mDaySpinner.getSelectedItemPosition() + 1)
                                        % mDaySpinner.getCount();
                                mDaySpinner.setSelection(pos);
                            }
                        }
                );

            // Handle DAY -
            root.findViewById(R.id.minusDay)
                .setOnClickListener(
                        new View.OnClickListener() {
                            @Override
                            public void onClick(@NonNull final View v) {
                                int pos = (mDaySpinner.getSelectedItemPosition() - 1
                                        + mDaySpinner.getCount()) % mDaySpinner.getCount();
                                mDaySpinner.setSelection(pos);
                            }
                        }
                );

            // Handle OK
            root.findViewById(R.id.confirm)
                .setOnClickListener(
                        new View.OnClickListener() {
                            @Override
                            public void onClick(@NonNull final View v) {
                                // Ensure the date is 'hierarchically valid';
                                // require year, if month is non-null,
                                // require month, if day non-null
                                if (mDay != null && mDay > 0 && (mMonth == null || mMonth == 0)) {
                                    StandardDialogs.showUserMessage(
                                            mActivity,
                                            R.string.warning_if_day_set_month_and_year_must_be);
                                } else if (mMonth != null && mMonth > 0 && mYear == null) {
                                    StandardDialogs.showUserMessage(
                                            mActivity,
                                            R.string.warning_if_month_set_year_must_be);
                                } else {
                                    if (mListener != null) {
                                        mListener.onPartialDatePickerSave(mYear, mMonth, mDay);
                                    }
                                }
                            }
                        }
                );

            // Handle Cancel
            root.findViewById(R.id.cancel).setOnClickListener(
                    new View.OnClickListener() {
                        @Override
                        public void onClick(@NonNull final View v) {
                            if (mListener != null) {
                                mListener.onPartialDatePickerCancel();
                            }
                        }
                    }
            );

            // Set the initial date
            setDate(year, month, day);
        }

        /**
         * Set the date to display.
         *
         * @param year  Year (or null)
         * @param month Month (or null)
         * @param day   Day (or null)
         */
        public void setDate(@Nullable final Integer year,
                            @Nullable final Integer month,
                            @Nullable final Integer day) {
            mYear = year;
            mMonth = month;
            mDay = day;

            String yearVal;
            if (year != null) {
                yearVal = year.toString();
            } else {
                yearVal = "";
            }
            mYearView.setText(yearVal);
            Editable e = mYearView.getEditableText();
            Selection.setSelection(e, e.length(), e.length());
            if (yearVal.isEmpty()) {
                mYearView.requestFocus();
                //noinspection ConstantConditions
                getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE);
            }

            if (month == null || month == 0) {
                mMonthSpinner.setSelection(0);
            } else {
                mMonthSpinner.setSelection(month);
            }

            if (day == null || day == 0) {
                mDaySpinner.setSelection(0);
            } else {
                mDaySpinner.setSelection(day);
            }
        }

        @Nullable
        public Integer getYear() {
            return mYear;
        }

        @Nullable
        public Integer getMonth() {
            return mMonth;
        }

        @Nullable
        public Integer getDay() {
            return mDay;
        }

        /**
         * Handle changes to the YEAR field.
         */
        private void handleYear() {
            // Try to convert to integer
            String val = mYearView.getText().toString().trim();
            try {
                mYear = Integer.parseInt(val);
            } catch (NumberFormatException e) {
                mYear = null;
            }

            // Seems reasonable to disable other spinners if year invalid, but it's actually
            // not very friendly when entering data for new books.
            regenDaysOfMonth(null);

            // Handle the result
            //if (mYear == null) {
            //  mMonthSpinner.setEnabled(false);
            //  mDaySpinner.setEnabled(false);
            //} else {
            //  // Enable other spinners as appropriate
            //  mMonthSpinner.setEnabled(true);
            //  mDaySpinner.setEnabled(mMonthSpinner.getSelectedItemPosition() > 0);
            //  regenDaysOfMonth(null);
            //}

        }

        /**
         * Handle changes to the MONTH field.
         */
        private void handleMonth(@Nullable final Integer pos) {
            // See if we got a valid month
            boolean isMonth = (pos != null && pos > 0);

            // Seems reasonable to disable other spinners if year invalid, but it actually
            // not very friendly when entering data for new books.
            if (!isMonth) {
                // If not, disable DAY spinner; we leave current value intact in
                // case a valid month is set later
                //mDaySpinner.setEnabled(false);
                mMonth = null;
            } else {
                // Set the month and make sure DAY spinner is valid
                mMonth = pos;
                //mDaySpinner.setEnabled(true);
                //regenDaysOfMonth(null);
            }
            regenDaysOfMonth(null);
        }

        /**
         * Handle changes to the DAY spinner.
         */
        private void handleDay(@Nullable final Integer pos) {
            boolean isSelected = (pos != null && pos > 0);
            if (!isSelected) {
                mDay = null;
            } else {
                mDay = pos;
            }
        }

        /**
         * Depending on year/month selected, generate the DAYS spinner values.
         */
        private void regenDaysOfMonth(@Nullable Integer totalDays) {
            // Save the current day in case the regen alters it
            Integer daySave = mDay;
            //ArrayAdapter<String> days = (ArrayAdapter<String>)mDaySpinner.getAdapter();

            // Make sure we have the 'no-day' value in the dialog
            if (mDayAdapter.getCount() == 0) {
                mDayAdapter.add("--");
            }

            // Determine the total days if not passed to us
            if (totalDays == null || totalDays == 0) {
                if (mYear != null && mMonth != null && mMonth > 0) {
                    // Get a calendar for the year/month
                    Calendar cal = Calendar.getInstance();
                    cal.set(Calendar.YEAR, mYear);
                    cal.set(Calendar.MONTH, mMonth - 1);
                    // Add appropriate days
                    totalDays = cal.getActualMaximum(Calendar.DAY_OF_MONTH);
                } else {
                    totalDays = 31;
                }
            }

            // Update the list
            // Don't forget we have a '--' in the adapter
            if (mDayAdapter.getCount() <= totalDays) {
                for (int i = mDayAdapter.getCount(); i <= totalDays; i++) {
                    mDayAdapter.add(i + "");
                }
            } else {
                for (int i = mDayAdapter.getCount() - 1; i > totalDays; i--) {
                    mDayAdapter.remove(i + "");
                }
            }

            // Ensure selected day is valid
            if (daySave == null || daySave == 0) {
                mDaySpinner.setSelection(0);
            } else {
                if (daySave > totalDays) {
                    daySave = totalDays;
                }
                mDaySpinner.setSelection(daySave);
            }
        }

        /**
         * Reorder the views in the dialog to suit the current locale.
         *
         * @param root Root view
         */
        private void reorderPickers(@NonNull final View root) {
            char[] order;
            try {
                // This actually throws exception in some versions of Android, specifically when
                // the locale-specific date format has the day name (EEE) in it. So we exit and
                // just use our default order in these cases.
                // See Issue #712.
                order = DateFormat.getDateFormatOrder(mActivity);
            } catch (RuntimeException e) {
                return;
            }

            /* Default order is {year, month, date} so if that's the order then do nothing.
             */
            if ((order[0] == 'y') && (order[1] == 'M')) {
                return;
            }

            /* Remove the 3 pickers from their parent and then add them back in the
             * required order.
             */
            ViewGroup parent = root.findViewById(R.id.dateSelector);
            // Get the three views
            View y = root.findViewById(R.id.yearSelector);
            View m = root.findViewById(R.id.monthSelector);
            View d = root.findViewById(R.id.daySelector);
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

        void setResultsListener(@NonNull final OnPartialDatePickerResultsListener listener) {
            mListener = listener;
        }

        /**
         * Listener to receive notifications when dialog is closed by any means.
         *
         * @author pjw
         */
        protected interface OnPartialDatePickerResultsListener {

            void onPartialDatePickerSave(@Nullable final Integer year,
                                         @Nullable final Integer month,
                                         @Nullable final Integer day);

            void onPartialDatePickerCancel();
        }

    }
}
