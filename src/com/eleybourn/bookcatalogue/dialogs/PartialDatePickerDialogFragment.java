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
package com.eleybourn.bookcatalogue.dialogs;

import android.annotation.SuppressLint;
import android.app.Dialog;
import android.content.Context;
import android.content.DialogInterface;
import android.os.Bundle;
import android.text.Editable;
import android.text.Selection;
import android.text.TextWatcher;
import android.text.format.DateFormat;
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
import androidx.annotation.StringRes;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.DialogFragment;

import java.lang.ref.WeakReference;
import java.util.Calendar;

import com.eleybourn.bookcatalogue.BuildConfig;
import com.eleybourn.bookcatalogue.DEBUG_SWITCHES;
import com.eleybourn.bookcatalogue.R;
import com.eleybourn.bookcatalogue.UniqueId;
import com.eleybourn.bookcatalogue.debug.Logger;
import com.eleybourn.bookcatalogue.utils.DateUtils;
import com.eleybourn.bookcatalogue.utils.UserMessage;

/**
 * DialogFragment class to allow for selection of partial dates from 0AD to 9999AD.
 * <p>
 * ENHANCE: add a 'clear' button.
 *
 * @author pjw
 */
public class PartialDatePickerDialogFragment
        extends DialogFragment {

    /** Fragment manager tag. */
    public static final String TAG = PartialDatePickerDialogFragment.class.getSimpleName();

    /** a standard sql style date string, must be correct. */
    private static final String BKEY_DATE = TAG + ":date";
    /** or the date split into components, which can partial. */
    private static final String BKEY_YEAR = TAG + ":year";
    private static final String BKEY_MONTH = TAG + ":month";
    private static final String BKEY_DAY = TAG + ":day";

    private static final String UNKNOWN_MONTH = "---";
    private static final String UNKNOWN_DAY = "--";

    /** identifier of the field this dialog is bound to. */
    @IdRes
    private int mDestinationFieldId;

    /**
     * Currently displayed; {@code null} if empty/invalid.
     * The value is automatically updated by the dialog after every change.
     */
    @Nullable
    private Integer mYear;
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
     * @param dialogTitleId titel resource id for the dialog
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

    /**
     * Create the underlying dialog.
     */
    @NonNull
    @Override
    public Dialog onCreateDialog(@Nullable final Bundle savedInstanceState) {

        Bundle args = requireArguments();

        int titleId = args.getInt(UniqueId.BKEY_DIALOG_TITLE, R.string.edit);
        mDestinationFieldId = args.getInt(UniqueId.BKEY_FIELD_ID);

        args = savedInstanceState == null ? requireArguments() : savedInstanceState;
        if (args.containsKey(BKEY_DATE)) {
            setDate(args.getString(BKEY_DATE, ""));
        } else {
            mYear = args.getInt(BKEY_YEAR);
            mMonth = args.getInt(BKEY_MONTH);
            mDay = args.getInt(BKEY_DAY);
        }

        //noinspection ConstantConditions
        PartialDatePickerDialog dialog = new PartialDatePickerDialog(getContext());
        if (titleId != 0) {
            dialog.setTitle(titleId);
        }

        dialog.setButton(DialogInterface.BUTTON_NEGATIVE, getString(android.R.string.cancel),
                         (d, which) -> dismiss());
        dialog.setButton(DialogInterface.BUTTON_POSITIVE, getString(android.R.string.ok),
                         (d, which) -> checkAndSend());
        return dialog;
    }

    /**
     * Ensure the date is 'hierarchically valid';
     * require year, if month is non-null,
     * require month, if day non-null
     * <p>
     * If it is, send it to the listener.
     */
    private void checkAndSend() {

        if (mDay != null && mDay > 0 && (mMonth == null || mMonth == 0)) {
            //noinspection ConstantConditions
            UserMessage.showUserMessage(getView(),
                                        R.string.warning_if_day_set_month_and_year_must_be);

        } else if (mMonth != null && mMonth > 0 && mYear == null) {
            //noinspection ConstantConditions
            UserMessage.showUserMessage(getView(),
                                        R.string.warning_if_month_set_year_must_be);

        } else {
            dismiss();
            if (mListener.get() != null) {
                mListener.get().onPartialDatePickerSave(mDestinationFieldId, mYear, mMonth, mDay);
            } else {
                if (BuildConfig.DEBUG && DEBUG_SWITCHES.TRACE_WEAK_REFERENCES) {
                    Logger.debug(this, "onPartialDatePickerSave",
                                 "WeakReference to listener was dead");
                }
            }
        }
    }

    /**
     * Private helper, NOT a public accessor.
     * <p>
     * Now allowing partial dates:
     * yyyy-mm-dd time
     * yyyy-mm-dd
     * yyyy-mm
     * yyyy
     *
     * @param dateString SQL formatted (partial) date, may be {@code null}
     */
    private void setDate(@NonNull final String dateString) {
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
        } catch (NumberFormatException ignore) {
        }

        mYear = yyyy;
        mMonth = mm;
        mDay = dd;
        PartialDatePickerDialog dialog = (PartialDatePickerDialog) getDialog();
        if (dialog != null) {
            dialog.updateDisplay();
        }
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
     * Call this from {@link #onAttachFragment} in the parent.
     *
     * @param listener the object to send the result to.
     */
    public void setListener(final PartialDatePickerResultsListener listener) {
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
     * The custom dialog.
     */
    class PartialDatePickerDialog
            extends AlertDialog {

        /** Local ref to year text view. */
        private final EditText mYearView;
        /** Local ref to month spinner. */
        private final Spinner mMonthSpinner;
        /** Local ref to day spinner. */
        private final Spinner mDaySpinner;

        /** Local ref to day spinner adapter. */
        private final ArrayAdapter<String> mDayAdapter;

        /**
         * Constructor.
         *
         * @param context caller context
         */
        @SuppressLint("SetTextI18n")
        PartialDatePickerDialog(@NonNull final Context context) {
            super(context);

            @SuppressWarnings("ConstantConditions")
            @SuppressLint("InflateParams")
            View root = getActivity().getLayoutInflater()
                                     .inflate(R.layout.dialog_partial_date_picker, null);

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
                    new ArrayAdapter<>(getContext(), android.R.layout.simple_spinner_item);
            monthAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
            mMonthSpinner.setAdapter(monthAdapter);

            // Create day spinner adapter
            mDayAdapter = new ArrayAdapter<>(getContext(), android.R.layout.simple_spinner_item);
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
            // Set the day to 1... so avoid wrap on short months (default to current date)
            cal.set(Calendar.DAY_OF_MONTH, 1);
            // Add all month names (abbreviated)
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
                            handleMonth(mMonthSpinner.getSelectedItemPosition());
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
                            handleDay(mDaySpinner.getSelectedItemPosition());
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
            root.findViewById(R.id.plusYear).setOnClickListener(
                    v -> {
                        String text;
                        if (mYear != null) {
                            text = (++mYear).toString();
                        } else {
                            text = String.valueOf(Calendar.getInstance().get(Calendar.YEAR));
                        }
                        mYearView.setText(text);
                    }
            );

            // Handle YEAR -
            root.findViewById(R.id.minusYear).setOnClickListener(
                    v -> {
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
            );

            // Handle MONTH +
            root.findViewById(R.id.plusMonth).setOnClickListener(
                    v -> {
                        int pos = (mMonthSpinner.getSelectedItemPosition() + 1)
                                % mMonthSpinner.getCount();
                        mMonthSpinner.setSelection(pos);
                    }
            );

            // Handle MONTH -
            root.findViewById(R.id.minusMonth).setOnClickListener(
                    v -> {
                        int pos = (mMonthSpinner.getSelectedItemPosition() - 1
                                + mMonthSpinner.getCount()) % mMonthSpinner.getCount();
                        mMonthSpinner.setSelection(pos);
                    }
            );

            // Handle DAY +
            root.findViewById(R.id.plusDay).setOnClickListener(
                    v -> {
                        int pos = (mDaySpinner.getSelectedItemPosition() + 1)
                                % mDaySpinner.getCount();
                        mDaySpinner.setSelection(pos);
                    }
            );

            // Handle DAY -
            root.findViewById(R.id.minusDay).setOnClickListener(
                    v -> {
                        int pos = (mDaySpinner.getSelectedItemPosition() - 1
                                + mDaySpinner.getCount()) % mDaySpinner.getCount();
                        mDaySpinner.setSelection(pos);
                    }
            );

            // Set the initial date
            updateDisplay();
        }

        /**
         * Set the date to display.
         */
        void updateDisplay() {
            String yearVal;
            if (mYear != null) {
                yearVal = mYear.toString();
            } else {
                yearVal = "";
            }
            mYearView.setText(yearVal);
            Editable e = mYearView.getEditableText();
            Selection.setSelection(e, e.length(), e.length());
            if (yearVal.isEmpty()) {
                mYearView.requestFocus();
                //noinspection ConstantConditions
                getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE
                                                     | WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
            }

            if (mMonth == null || mMonth == 0) {
                mMonthSpinner.setSelection(0);
            } else {
                mMonthSpinner.setSelection(mMonth);
            }

            if (mDay == null || mDay == 0) {
                mDaySpinner.setSelection(0);
            } else {
                mDaySpinner.setSelection(mDay);
            }
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
            boolean isMonth = (pos != null) && (pos > 0);

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
            boolean isSelected = pos != null && pos > 0;
            mDay = isSelected ? pos : null;
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
                    mDayAdapter.add(String.valueOf(i));
                }
            } else {
                for (int i = mDayAdapter.getCount() - 1; i > totalDays; i--) {
                    mDayAdapter.remove(String.valueOf(i));
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
                order = DateFormat.getDateFormatOrder(getContext());
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
    }
}
