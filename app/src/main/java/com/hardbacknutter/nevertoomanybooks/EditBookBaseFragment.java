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
package com.hardbacknutter.nevertoomanybooks;

import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Parcelable;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.widget.AutoCompleteTextView;
import android.widget.TextView;

import androidx.annotation.CallSuper;
import androidx.annotation.IdRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import androidx.core.util.Pair;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.fragment.app.FragmentResultListener;
import androidx.lifecycle.ViewModelProvider;
import androidx.preference.PreferenceManager;

import com.google.android.material.datepicker.MaterialDatePicker;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

import com.hardbacknutter.nevertoomanybooks.datamanager.DataEditor;
import com.hardbacknutter.nevertoomanybooks.debug.ErrorMsg;
import com.hardbacknutter.nevertoomanybooks.dialogs.PartialDatePickerDialogFragment;
import com.hardbacknutter.nevertoomanybooks.entities.Book;
import com.hardbacknutter.nevertoomanybooks.fields.Field;
import com.hardbacknutter.nevertoomanybooks.fields.Fields;
import com.hardbacknutter.nevertoomanybooks.fields.formatters.FieldFormatter;
import com.hardbacknutter.nevertoomanybooks.utils.DateParser;
import com.hardbacknutter.nevertoomanybooks.viewmodels.EditBookFragmentViewModel;
import com.hardbacknutter.nevertoomanybooks.widgets.WrappedMaterialDatePicker;

public abstract class EditBookBaseFragment
        extends BookBaseFragment
        implements DataEditor<Book> {

    /** Log tag. */
    private static final String TAG = "EditBookBaseFragment";

    /** FragmentResultListener request key. */
    private static final String RK_DATE_PICKER_PARTIAL =
            PartialDatePickerDialogFragment.TAG + ":rk";

    /** Tag/requestKey for WrappedMaterialDatePicker. */
    private static final String RK_DATE_PICKER_SINGLE = TAG + ":rk:datePickerSingle";
    /** Tag/requestKey for WrappedMaterialDatePicker. */
    private static final String RK_DATE_PICKER_RANGE = TAG + ":rk:datePickerRange";

    /** The view model. */
    EditBookFragmentViewModel mEditHelperVM;

    /** Dialog listener (strong reference). */
    private final PartialDatePickerDialogFragment.OnResultListener mPartialDatePickerListener =
            this::onPartialDateSet;
    /** Dialog listener (strong reference). */
    private final WrappedMaterialDatePicker.OnResultListener mDatePickerListener = this::onDateSet;

    /**
     * Convert a LocalDate to an Instant in time.
     *
     * @param field       to extract from
     * @param todayIfNone if set, and the incoming date is null, use 'today' for the date
     *
     * @return instant
     */
    private Instant getInstant(@NonNull final Field<String, TextView> field,
                               final boolean todayIfNone) {
        //noinspection ConstantConditions
        final LocalDateTime date = DateParser.getInstance(getContext())
                                             .parse(field.getAccessor().getValue());
        if (date == null && !todayIfNone) {
            return null;
        }

        if (date != null) {
            return date.toInstant(ZoneOffset.UTC);
        }
        return Instant.now();
    }

    /**
     * Convenience wrapper.
     * <p>
     * Return the Field associated with the passed ID.
     *
     * @param <T> type of Field value.
     * @param <V> type of View for this field.
     * @param id  Field/View ID
     *
     * @return Associated Field.
     */
    @NonNull
    <T, V extends View> Field<T, V> getField(@IdRes final int id) {
        return getFields().getField(id);
    }

    @Override
    public void onCreate(@Nullable final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        final FragmentManager fm = getChildFragmentManager();

        fm.setFragmentResultListener(RK_DATE_PICKER_PARTIAL, this, mPartialDatePickerListener);
        fm.setFragmentResultListener(RK_DATE_PICKER_SINGLE, this, mDatePickerListener);
        fm.setFragmentResultListener(RK_DATE_PICKER_RANGE, this, mDatePickerListener);

        final String fragmentTag = getTag();
        Objects.requireNonNull(fragmentTag, ErrorMsg.NULL_FRAGMENT_TAG);

        //noinspection ConstantConditions
        mEditHelperVM = new ViewModelProvider(getActivity())
                .get(fragmentTag, EditBookFragmentViewModel.class);

        mEditHelperVM.init();
    }

    @Override
    public void onViewCreated(@NonNull final View view,
                              @Nullable final Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        if (mEditHelperVM.shouldInitFields()) {
            onInitFields(getFields());
            mEditHelperVM.setFieldsAreInitialised();
        }
    }

    /**
     * Init all Fields, and add them the fields collection.
     * <p>
     * Note that Field views are <strong>NOT AVAILABLE</strong>.
     * <p>
     * The fields will be populated in {@link #onPopulateViews}
     *
     * @param fields the local fields collection to add your fields to
     */
    @CallSuper
    void onInitFields(@NonNull final Fields fields) {
    }

    @Override
    public void onCreateOptionsMenu(@NonNull final Menu menu,
                                    @NonNull final MenuInflater inflater) {
        inflater.inflate(R.menu.toolbar_save, menu);
        super.onCreateOptionsMenu(menu, inflater);
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull final MenuItem item) {
        //noinspection SwitchStatementWithTooFewBranches
        switch (item.getItemId()) {
            case R.id.MENU_SAVE:
                //noinspection ConstantConditions
                ((EditBookActivity) getActivity()).prepareSave(true);
                return true;
            default:
                return super.onOptionsItemSelected(item);
        }
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

        // hook up the Views, and calls {@link #onPopulateViews}
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
     * We simply copy all {@link Field} into the given {@link Book}.
     * <p>
     * Called from {@link #onPause()}.
     * Override as needed.
     * <p>
     * {@inheritDoc}
     */
    @SuppressWarnings("ParameterNameDiffersFromOverriddenParameter")
    @CallSuper
    @Override
    public void onSaveFields(@NonNull final Book book) {
        getFields().getAll(book);
    }

    /**
     * Setup an adapter for the AutoCompleteTextView, using the (optional) formatter.
     *
     * @param fieldId view to connect
     * @param list    with auto complete values
     */
    void addAutocomplete(@IdRes final int fieldId,
                         @NonNull final List<String> list) {
        //noinspection ConstantConditions
        final SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(getContext());

        final Field<?, ?> field = getField(fieldId);
        // only bother when it's in use and we have a list
        if (field.isUsed(prefs) && !list.isEmpty()) {
            final AutoCompleteTextView view = (AutoCompleteTextView) field.getAccessor().getView();
            //noinspection unchecked
            final Fields.FormattedDiacriticArrayAdapter adapter =
                    new Fields.FormattedDiacriticArrayAdapter(
                            getContext(), list,
                            (FieldFormatter<String>) field.getAccessor().getFormatter());
            //noinspection ConstantConditions
            view.setAdapter(adapter);
        }
    }

    /**
     * Setup a date picker for selecting a (full) date range.
     * <p>
     * Clicking on the start-date field will allow the user to set just the start-date.
     * Clicking on the end-date will prompt to select both the start and end dates.
     * <p>
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
        final SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(getContext());

        if (fieldStartDate.isUsed(prefs) && fieldEndDate.isUsed(prefs)) {

            // single date picker for the start-date
            addDatePicker(fieldStartDate, dialogTitleIdStart, true);

            // date-span for the end-date
            //noinspection ConstantConditions
            fieldEndDate.getAccessor().getView().setOnClickListener(v -> {
                final Instant timeStart = getInstant(fieldStartDate, todayIfNone);
                Long startSelection = timeStart != null ? timeStart.toEpochMilli() : null;

                final Instant timeEnd = getInstant(fieldEndDate, todayIfNone);
                Long endSelection = timeEnd != null ? timeEnd.toEpochMilli() : null;

                // sanity check
                if (startSelection != null && endSelection != null
                    && startSelection > endSelection) {
                    final Long tmp = startSelection;
                    startSelection = endSelection;
                    endSelection = tmp;
                }
                mEditHelperVM.setCurrentDialogFieldId(fieldStartDate.getId(), fieldEndDate.getId());

                new WrappedMaterialDatePicker<>(
                        MaterialDatePicker.Builder
                                .dateRangePicker()
                                .setTitleText(dialogTitleIdSpan)
                                .setSelection(new Pair<>(startSelection, endSelection))
                                .build()
                ).show(getChildFragmentManager(), RK_DATE_PICKER_RANGE);
            });

        } else if (fieldStartDate.isUsed(prefs)) {
            addDatePicker(fieldStartDate, dialogTitleIdStart, todayIfNone);
        } else if (fieldEndDate.isUsed(prefs)) {
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
        final SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(getContext());

        if (field.isUsed(prefs)) {
            //noinspection ConstantConditions
            field.getAccessor().getView().setOnClickListener(v -> {
                final Instant time = getInstant(field, todayIfNone);
                final Long selection = time != null ? time.toEpochMilli() : null;
                mEditHelperVM.setCurrentDialogFieldId(field.getId());

                new WrappedMaterialDatePicker<>(
                        MaterialDatePicker.Builder
                                .datePicker()
                                .setTitleText(dialogTitleId)
                                .setSelection(selection)
                                .build())
                        .show(getChildFragmentManager(), RK_DATE_PICKER_SINGLE);
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
        final SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(getContext());

        if (field.isUsed(prefs)) {
            //noinspection ConstantConditions
            field.getAccessor().getView().setOnClickListener(v -> {
                mEditHelperVM.setCurrentDialogFieldId(field.getId());
                PartialDatePickerDialogFragment
                        .newInstance(RK_DATE_PICKER_PARTIAL, dialogTitleId,
                                     field.getAccessor().getValue(), todayIfNone)
                        .show(getChildFragmentManager(), PartialDatePickerDialogFragment.TAG);
            });
        }
    }

    private void onPartialDateSet(@Nullable final Integer year,
                                  @Nullable final Integer month,
                                  @Nullable final Integer day) {
        String date;
        if (year == null || year == 0) {
            date = "";
        } else {
            date = String.format(Locale.ENGLISH, "%04d", year);

            if (month != null && month > 0) {
                String mm = Integer.toString(month);
                if (mm.length() == 1) {
                    mm = '0' + mm;
                }
                date += '-' + mm;

                if (day != null && day > 0) {
                    String dd = Integer.toString(day);
                    if (dd.length() == 1) {
                        dd = '0' + dd;
                    }
                    date += '-' + dd;
                }
            }
        }

        onDateSet(0, date);
    }

    private void onDateSet(@NonNull final long... selections) {
        int index = 0;
        for (long selection : selections) {
            if (selection == WrappedMaterialDatePicker.NO_SELECTION) {
                onDateSet(index, "");
            } else {
                onDateSet(index, Instant.ofEpochMilli(selection)
                                        .atZone(ZoneId.systemDefault())
                                        .format(DateTimeFormatter.ISO_LOCAL_DATE));
            }
            index++;
        }
    }

    private void onDateSet(@IdRes final int fieldIndex,
                           @NonNull final String value) {

        @IdRes
        final int fieldId = mEditHelperVM.getCurrentDialogFieldId()[fieldIndex];

        final Field<String, TextView> field = getField(fieldId);
        field.getAccessor().setValue(value);
        field.onChanged(true);

        if (fieldId == R.id.read_end) {
            getField(R.id.cbx_read).getAccessor().setValue(true);
        }
    }

    public interface OnResultListener<T extends Parcelable>
            extends FragmentResultListener {

        /* private. */ String ORIGINAL = "original";
        /* private. */ String MODIFIED = "modified";

        static <T extends Parcelable> void sendResult(@NonNull final Fragment fragment,
                                                      @NonNull final String requestKey,
                                                      @NonNull final T original,
                                                      @NonNull final T modified) {
            final Bundle result = new Bundle();
            result.putParcelable(ORIGINAL, original);
            result.putParcelable(MODIFIED, modified);
            fragment.getParentFragmentManager().setFragmentResult(requestKey, result);
        }

        @Override
        default void onFragmentResult(@NonNull final String requestKey,
                                      @NonNull final Bundle result) {
            onResult(Objects.requireNonNull(result.getParcelable(ORIGINAL)),
                     Objects.requireNonNull(result.getParcelable(MODIFIED)));
        }

        /**
         * Callback handler.
         */
        void onResult(@NonNull T original,
                      @NonNull T modified);
    }
}
