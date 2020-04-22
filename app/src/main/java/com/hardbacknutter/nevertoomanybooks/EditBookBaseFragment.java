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
package com.hardbacknutter.nevertoomanybooks;

import android.content.Context;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.AutoCompleteTextView;
import android.widget.TextView;

import androidx.annotation.CallSuper;
import androidx.annotation.IdRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import androidx.core.util.Pair;
import androidx.fragment.app.DialogFragment;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;

import com.google.android.material.datepicker.MaterialDatePicker;
import com.google.android.material.datepicker.MaterialPickerOnPositiveButtonClickListener;

import java.util.Date;
import java.util.List;
import java.util.Objects;

import com.hardbacknutter.nevertoomanybooks.datamanager.DataEditor;
import com.hardbacknutter.nevertoomanybooks.datamanager.DataManager;
import com.hardbacknutter.nevertoomanybooks.debug.ErrorMsg;
import com.hardbacknutter.nevertoomanybooks.dialogs.picker.DatePickerResultsListener;
import com.hardbacknutter.nevertoomanybooks.dialogs.picker.PartialDatePickerDialogFragment;
import com.hardbacknutter.nevertoomanybooks.entities.Book;
import com.hardbacknutter.nevertoomanybooks.fields.Field;
import com.hardbacknutter.nevertoomanybooks.fields.Fields;
import com.hardbacknutter.nevertoomanybooks.fields.formatters.FieldFormatter;
import com.hardbacknutter.nevertoomanybooks.goodreads.tasks.RequestAuthTask;
import com.hardbacknutter.nevertoomanybooks.utils.DateUtils;
import com.hardbacknutter.nevertoomanybooks.viewmodels.EditBookFragmentViewModel;

/**
 * Base class for all fragments that appear in {@link EditBookFragment}.
 */
public abstract class EditBookBaseFragment
        extends BookBaseFragment
        implements DataEditor<Book> {

    /** Log tag. */
    private static final String TAG = "EditBookBaseFragment";

    /** Tag for MaterialDatePicker. */
    private static final String TAG_DATE_PICKER_SINGLE = TAG + ":datePickerSingle";
    /** Tag for MaterialDatePicker. */
    private static final String TAG_DATE_PICKER_RANGE = TAG + ":datePickerRange";

    /** The view model. */
    EditBookFragmentViewModel mFragmentVM;

    /** Dialog listener (strong reference). */
    private final DatePickerResultsListener
            mPartialDatePickerResultsListener = (year, month, day) -> {
        final int fieldId = mFragmentVM.getCurrentDialogFieldId()[0];
        String date = DateUtils.buildPartialDate(year, month, day);
        onDateSet(fieldId, date);
    };

    /** Dialog listener (strong reference). */
    private final MaterialPickerOnPositiveButtonClickListener<Long>
            mDatePickerListener = selection -> {
        final int fieldId = mFragmentVM.getCurrentDialogFieldId()[0];
        onDateSet(fieldId, selection);
    };

    /** Dialog listener (strong reference). */
    private final MaterialPickerOnPositiveButtonClickListener<Pair<Long, Long>>
            mDateRangePickerListener = selection -> {
        final int fieldIdStart = mFragmentVM.getCurrentDialogFieldId()[0];
        onDateSet(fieldIdStart, selection.first);
        final int fieldIdEnd = mFragmentVM.getCurrentDialogFieldId()[1];
        onDateSet(fieldIdEnd, selection.second);
    };

    /** Used by the base class. Subclasses and this class should bypass this method. */
    @Override
    Fields getFields() {
        return mFragmentVM.getFields();
    }

    @Override
    public void onAttachFragment(@NonNull final Fragment childFragment) {
        if (BuildConfig.DEBUG) {
            Log.d(getClass().getName(), "onAttachFragment: " + childFragment.getTag());
        }
        super.onAttachFragment(childFragment);

        if (PartialDatePickerDialogFragment.TAG.equals(childFragment.getTag())) {
            ((PartialDatePickerDialogFragment) childFragment)
                    .setListener(mPartialDatePickerResultsListener);

        } else if (TAG_DATE_PICKER_SINGLE.equals(childFragment.getTag())) {
            //noinspection unchecked
            ((MaterialDatePicker<Long>) childFragment)
                    .addOnPositiveButtonClickListener(mDatePickerListener);

        } else if (TAG_DATE_PICKER_RANGE.equals(childFragment.getTag())) {
            //noinspection unchecked
            ((MaterialDatePicker<Pair<Long, Long>>) childFragment)
                    .addOnPositiveButtonClickListener(mDateRangePickerListener);
        }
    }

    @Override
    public void onActivityCreated(@Nullable final Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);

        String tag = getTag();
        Objects.requireNonNull(tag, ErrorMsg.NULL_FRAGMENT_TAG);

        //noinspection ConstantConditions
        mFragmentVM = new ViewModelProvider(getActivity())
                .get(tag, EditBookFragmentViewModel.class);

        mFragmentVM.init(getArguments());
        mFragmentVM.onUserMessage().observe(getViewLifecycleOwner(), this::showUserMessage);
        mFragmentVM.onNeedsGoodreads().observe(getViewLifecycleOwner(), needs -> {
            if (needs != null && needs) {
                final Context context = getContext();
                //noinspection ConstantConditions
                RequestAuthTask.prompt(context, mFragmentVM.getGoodreadsTaskListener(context));
            }
        });
        onInitFields();

        // We hide the tab bar when editing Authors/Series on pop-up screens.
        // Make sure to set it visible here.
        final View tabBarLayout = getActivity().findViewById(R.id.tab_panel);
        if (tabBarLayout != null) {
            tabBarLayout.setVisibility(View.VISIBLE);
        }
    }

    /**
     * Init all Fields, and add them the {@link #getFields()} collection.
     * <p>
     * Note that Views are <strong>NOT AVAILABLE</strong>.
     * <p>
     * Book data is available from {@link #mBookViewModel} but {@link #onResume()} is
     * were the fields/Views are normally loaded with that data.
     */
    @CallSuper
    void onInitFields() {
    }

    @Override
    public void onResume() {

        // Not sure this is really needed; but it does no harm.
        // In theory, the editing fragment can trigger an internet search,
        // which after it comes back, brings along new data to be transferred to the book.
        // BUT: that new data would not be in the fragment arguments?
        //TODO: double check having book-data bundle in onResume.
        if (mBookViewModel.getBook().isNew()) {
            //noinspection ConstantConditions
            mBookViewModel.addFieldsFromBundle(getContext(), getArguments());
        }

        // hook up the Views, and populate them with the book data
        super.onResume();
    }

    /**
     * Trigger the Fragment to save its Fields to the Book.
     * <p>
     * This is always done, even when the user 'cancel's the edit.
     * The latter will result in a "are you sure" where they can 'cancel the cancel'
     * and continue with all data present.
     *
     * <br><br>{@inheritDoc}
     */
    @Override
    @CallSuper
    public void onPause() {
        // Avoid saving a 2nd time after the user has initiated saving.
        if (!mBookViewModel.isSaved()) {
            onSaveFields(mBookViewModel.getBook());
            //noinspection ConstantConditions
            mBookViewModel.setUnfinishedEdits(getTag(), hasUnfinishedEdits());
        }
        super.onPause();
    }

    /**
     * Default implementation of code to save existing data to the Book object.
     * We simply copy all {@link Field} into the given {@link DataManager} e.g. the {@link Book}
     * <p>
     * Called from {@link #onPause()}.
     * Override as needed.
     * <p>
     * {@inheritDoc}
     */
    @CallSuper
    @Override
    public void onSaveFields(@NonNull final Book book) {
        mFragmentVM.getFields().getAll(book);
    }

    /**
     * Setup an adapter for the AutoCompleteTextView, using the (optional) formatter.
     *
     * @param fieldId view to connect
     * @param list    with auto complete values
     */
    void addAutocomplete(@IdRes final int fieldId,
                         @NonNull final List<String> list) {
        final Field field = mFragmentVM.getFields().getField(fieldId);
        // only bother when it's in use and we have a list
        //noinspection ConstantConditions
        if (field.isUsed(getContext()) && !list.isEmpty()) {
            final AutoCompleteTextView view = (AutoCompleteTextView) field.getAccessor().getView();
            //noinspection unchecked
            final Fields.FormattedDiacriticArrayAdapter adapter =
                    new Fields.FormattedDiacriticArrayAdapter(
                            view.getContext(), list,
                            (FieldFormatter<String>) field.getAccessor().getFormatter());
            view.setAdapter(adapter);
        }
    }

    /**
     * Setup a date picker for selecting a partial date.
     * If only one field is used, this method diverts to {@link #addDatePicker}.
     *
     * @param dialogTitleIdSpan  title of the dialog box if both start and end-dates are used.
     * @param dialogTitleIdStart title of the dialog box if the end-date is not in use
     * @param fieldStartDate     to setup for the start-date
     * @param dialogTitleIdEnd   title of the dialog box if the start-date is not in use
     * @param fieldEndDate       to setup for the end-date
     * @param todayIfNone        if true, and if the field was empty, we'll default to today's date.
     */
    @SuppressWarnings("SameParameterValue")
    void addDateRangePicker(@StringRes final int dialogTitleIdSpan,
                            @StringRes final int dialogTitleIdStart,
                            @NonNull final Field<String, TextView> fieldStartDate,
                            @StringRes final int dialogTitleIdEnd,
                            @NonNull final Field<String, TextView> fieldEndDate,
                            final boolean todayIfNone) {
        //noinspection ConstantConditions
        if (fieldStartDate.isUsed(getContext()) && fieldEndDate.isUsed(getContext())) {
            final View.OnClickListener listener = v -> {
                Long timeStart = DateUtils.parseTime(fieldStartDate.getAccessor().getValue(),
                                                     todayIfNone);
                Long timeEnd = DateUtils.parseTime(fieldEndDate.getAccessor().getValue(),
                                                   todayIfNone);
                // sanity check
                if (timeStart != null && timeEnd != null && timeStart > timeEnd) {
                    Long tmp = timeStart;
                    timeStart = timeEnd;
                    timeEnd = tmp;
                }

                final MaterialDatePicker picker = MaterialDatePicker.Builder
                        .dateRangePicker()
                        .setTitleText(dialogTitleIdSpan)
                        .setSelection(new Pair<>(timeStart, timeEnd))
                        .build();
                mFragmentVM.setCurrentDialogFieldId(fieldStartDate.getId(), fieldEndDate.getId());
                picker.show(getChildFragmentManager(), TAG_DATE_PICKER_RANGE);
            };

            fieldStartDate.getAccessor().getView().setOnClickListener(listener);
            fieldEndDate.getAccessor().getView().setOnClickListener(listener);

        } else if (fieldStartDate.isUsed(getContext())) {
            addDatePicker(fieldStartDate, dialogTitleIdStart, todayIfNone);
        } else if (fieldEndDate.isUsed(getContext())) {
            addDatePicker(fieldEndDate, dialogTitleIdEnd, todayIfNone);
        }
    }

    /**
     * Setup a date picker for selecting a single, full date.
     *
     * @param field         to setup
     * @param dialogTitleId title of the dialog box.
     * @param todayIfNone   if true, and if the field was empty, we'll default to today's date.
     */
    void addDatePicker(@NonNull final Field<String, TextView> field,
                       @StringRes final int dialogTitleId,
                       final boolean todayIfNone) {
        //noinspection ConstantConditions
        if (field.isUsed(getContext())) {
            field.getAccessor().getView().setOnClickListener(v -> {
                final Long selection = DateUtils.parseTime(field.getAccessor().getValue(),
                                                           todayIfNone);
                final MaterialDatePicker picker = MaterialDatePicker.Builder
                        .datePicker()
                        .setTitleText(dialogTitleId)
                        .setSelection(selection)
                        .build();
                mFragmentVM.setCurrentDialogFieldId(field.getId());
                picker.show(getChildFragmentManager(), TAG_DATE_PICKER_SINGLE);
            });
        }
    }

    /**
     * Setup a date picker for selecting a partial date.
     *
     * @param field         to setup
     * @param dialogTitleId title of the dialog box.
     * @param todayIfNone   if true, and if the field was empty, we'll default to today's date.
     */
    @SuppressWarnings("SameParameterValue")
    void addPartialDatePicker(@NonNull final Field<String, TextView> field,
                              @StringRes final int dialogTitleId,
                              final boolean todayIfNone) {
        //noinspection ConstantConditions
        if (field.isUsed(getContext())) {
            field.getAccessor().getView().setOnClickListener(v -> {
                DialogFragment picker = PartialDatePickerDialogFragment.newInstance(
                        dialogTitleId, field.getAccessor().getValue(), todayIfNone);
                mFragmentVM.setCurrentDialogFieldId(field.getId());
                picker.show(getChildFragmentManager(), PartialDatePickerDialogFragment.TAG);
            });
        }
    }


    private void onDateSet(@IdRes final int fieldId,
                           @Nullable final Long selection) {
        String value;
        if (selection != null) {
            value = DateUtils.localSqlDate(new Date(selection));
        } else {
            value = "";
        }
        onDateSet(fieldId, value);
    }

    private void onDateSet(@IdRes final int fieldId,
                           @NonNull final String value) {
        Field<String, TextView> field = mFragmentVM.getFields().getField(fieldId);
        field.getAccessor().setValue(value);
        field.onChanged(true);

        if (fieldId == R.id.read_end) {
            mFragmentVM.getFields().getField(R.id.cbx_read).getAccessor().setValue(true);
        }
    }
}
