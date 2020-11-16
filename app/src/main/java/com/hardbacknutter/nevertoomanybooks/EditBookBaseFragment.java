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
import android.widget.AutoCompleteTextView;
import android.widget.Button;
import android.widget.TextView;

import androidx.annotation.CallSuper;
import androidx.annotation.IdRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Objects;
import java.util.function.Supplier;

import com.hardbacknutter.nevertoomanybooks.datamanager.DataEditor;
import com.hardbacknutter.nevertoomanybooks.dialogs.DialogFragmentLauncherBase;
import com.hardbacknutter.nevertoomanybooks.dialogs.PartialDatePickerDialogFragment;
import com.hardbacknutter.nevertoomanybooks.entities.Book;
import com.hardbacknutter.nevertoomanybooks.entities.EntityStage;
import com.hardbacknutter.nevertoomanybooks.fields.Field;
import com.hardbacknutter.nevertoomanybooks.fields.Fields;
import com.hardbacknutter.nevertoomanybooks.fields.formatters.FieldFormatter;
import com.hardbacknutter.nevertoomanybooks.utils.dates.DateParser;
import com.hardbacknutter.nevertoomanybooks.utils.dates.PartialDate;
import com.hardbacknutter.nevertoomanybooks.viewmodels.EditBookFragmentViewModel;
import com.hardbacknutter.nevertoomanybooks.widgets.WrappedMaterialDatePicker;

public abstract class EditBookBaseFragment
        extends BookBaseFragment
        implements DataEditor<Book> {

    /** Log tag. */
    private static final String TAG = "EditBookBaseFragment";

    /** FragmentResultListener request key. */
    private static final String RK_DATE_PICKER_PARTIAL =
            TAG + ":rk:" + PartialDatePickerDialogFragment.TAG;

    /** Tag/requestKey for WrappedMaterialDatePicker. */
    private static final String RK_DATE_PICKER_SINGLE = TAG + ":rk:datePickerSingle";
    /** Tag/requestKey for WrappedMaterialDatePicker. */
    private static final String RK_DATE_PICKER_RANGE = TAG + ":rk:datePickerRange";

    private final PartialDatePickerDialogFragment.Launcher mPartialDatePickerLauncher =
            new PartialDatePickerDialogFragment.Launcher() {
                @Override
                public void onResult(@IdRes final int fieldId,
                                     @NonNull final PartialDate date) {
                    onDateSet(fieldId, date.getIsoString());
                }
            };

    private final WrappedMaterialDatePicker.Launcher mDatePickerLauncher =
            new WrappedMaterialDatePicker.Launcher() {
                @Override
                public void onResult(@NonNull final int[] fieldIds,
                                     @NonNull final long[] selections) {
                    onDateSet(fieldIds, selections);
                }
            };

    /** The view model. */
    EditBookFragmentViewModel mVm;

    @NonNull
    @Override
    Fields getFields() {
        return mVm.getFields(getFragmentId());
    }

    @NonNull
    @Override
    Book getBook() {
        return mVm.getBook();
    }

    @Override
    public void onCreate(@Nullable final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        //noinspection ConstantConditions
        mVm = new ViewModelProvider(getActivity()).get(EditBookFragmentViewModel.class);

        mPartialDatePickerLauncher.register(this, RK_DATE_PICKER_PARTIAL);

        mDatePickerLauncher.register(this, RK_DATE_PICKER_SINGLE);
        mDatePickerLauncher.register(this, RK_DATE_PICKER_RANGE);
    }

    @Override
    public void onCreateOptionsMenu(@NonNull final Menu menu,
                                    @NonNull final MenuInflater inflater) {
        inflater.inflate(R.menu.toolbar_save, menu);

        final MenuItem menuItem = menu.findItem(R.id.MENU_ACTION_CONFIRM);
        final Button button = menuItem.getActionView().findViewById(R.id.btn_confirm);
        button.setText(menuItem.getTitle());
        button.setOnClickListener(v -> onOptionsItemSelected(menuItem));

        super.onCreateOptionsMenu(menu, inflater);
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull final MenuItem item) {
        final int itemId = item.getItemId();

        if (itemId == R.id.MENU_ACTION_CONFIRM) {
            //noinspection ConstantConditions
            ((EditBookActivity) getActivity()).prepareSave(true);
            return true;
        }

        return super.onOptionsItemSelected(item);
    }

    @Override
    public void onResume() {

        // Not sure this is really needed; but it does no harm.
        // In theory, the editing fragment can trigger an internet search,
        // which after it comes back, brings along new data to be transferred to the book.
        // BUT: that new data would not be in the fragment arguments?
        //TODO: double check having book-data bundle in onResume.
        if (mVm.getBook().isNew()) {
            //noinspection ConstantConditions
            mVm.addFieldsFromBundle(getContext(), getArguments());
        }

        // hook up the Views, and calls {@link #onPopulateViews}
        super.onResume();
    }

    @Override
    @CallSuper
    public void onPause() {
        mVm.setUnfinishedEdits(getFragmentId(), hasUnfinishedEdits());

        if (mVm.getBook().getStage() == EntityStage.Stage.Dirty) {
            onSaveFields(mVm.getBook());
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
     * <p>
     * Dev. note: a Supplier is used so we don't load the list if the Field is actually not in use
     *
     * @param preferences Global preferences
     * @param field       to setup
     * @param list        Supplier with auto complete values
     */
    void addAutocomplete(@NonNull final SharedPreferences preferences,
                         @NonNull final Field<String, AutoCompleteTextView> field,
                         @NonNull final Supplier<List<String>> list) {

        // only bother when it's in use
        if (field.isUsed(preferences)) {
            final FieldFormatter<String> formatter = field.getAccessor().getFormatter();
            //noinspection ConstantConditions
            final Fields.FormattedDiacriticArrayAdapter adapter =
                    new Fields.FormattedDiacriticArrayAdapter(getContext(), list.get(), formatter);

            final AutoCompleteTextView view = field.getAccessor().getView();
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
     * If only one field is used, we just display a single date picker.
     *
     * @param preferences      Global preferences
     * @param dateSpanTitleId  title of the dialog box if both start and end-dates are used.
     * @param startDateTitleId title of the dialog box if the end-date is not in use
     * @param fieldStartDate   to setup for the start-date
     * @param endDateTitleId   title of the dialog box if the start-date is not in use
     * @param fieldEndDate     to setup for the end-date
     * @param todayIfNone      if true, and if the field was empty, we'll default to today's date.
     */
    @SuppressWarnings("SameParameterValue")
    void addDateRangePicker(@NonNull final SharedPreferences preferences,
                            @StringRes final int dateSpanTitleId,
                            @StringRes final int startDateTitleId,
                            @NonNull final Field<String, TextView> fieldStartDate,
                            @StringRes final int endDateTitleId,
                            @NonNull final Field<String, TextView> fieldEndDate,
                            final boolean todayIfNone) {

        final boolean startUsed = fieldStartDate.isUsed(preferences);
        if (startUsed) {
            // single date picker for the start-date
            //noinspection ConstantConditions
            fieldStartDate.getAccessor().getView().setOnClickListener(v -> mDatePickerLauncher
                    .launch(startDateTitleId,
                            fieldStartDate.getId(), getInstant(fieldStartDate, todayIfNone)));
        }

        if (fieldEndDate.isUsed(preferences)) {
            final TextView view = fieldEndDate.getAccessor().getView();
            if (startUsed) {
                // date-span picker for the end-date
                //noinspection ConstantConditions
                view.setOnClickListener(v -> mDatePickerLauncher
                        .launch(dateSpanTitleId,
                                fieldStartDate.getId(), getInstant(fieldStartDate, todayIfNone),
                                fieldEndDate.getId(), getInstant(fieldEndDate, todayIfNone)));
            } else {
                // without using a start-date, single date picker for the end-date
                //noinspection ConstantConditions
                view.setOnClickListener(v -> mDatePickerLauncher
                        .launch(endDateTitleId, fieldEndDate.getId(), getInstant(fieldEndDate,
                                                                                 todayIfNone)));
            }
        }
    }

    /**
     * Setup a date picker for selecting a single, full date.
     *
     * @param preferences Global preferences
     * @param field       to setup
     * @param titleId     title for the picker window
     * @param todayIfNone if true, and if the field was empty, we'll default to today's date.
     */
    @SuppressWarnings("SameParameterValue")
    void addDatePicker(@NonNull final SharedPreferences preferences,
                       @NonNull final Field<String, TextView> field,
                       @StringRes final int titleId,
                       final boolean todayIfNone) {
        if (field.isUsed(preferences)) {
            //noinspection ConstantConditions
            field.getAccessor().getView().setOnClickListener(v -> mDatePickerLauncher
                    .launch(titleId, field.getId(), getInstant(field, todayIfNone)));
        }
    }

    /**
     * Setup a date picker for selecting a partial date.
     *
     * @param preferences Global preferences
     * @param field       to setup
     * @param titleId     title for the picker window
     * @param todayIfNone if true, and if the field was empty, we'll default to today's date.
     */
    @SuppressWarnings("SameParameterValue")
    void addPartialDatePicker(@NonNull final SharedPreferences preferences,
                              @NonNull final Field<String, TextView> field,
                              @StringRes final int titleId,
                              final boolean todayIfNone) {
        if (field.isUsed(preferences)) {
            //noinspection ConstantConditions
            field.getAccessor().getView().setOnClickListener(v -> mPartialDatePickerLauncher
                    .launch(titleId, field.getId(), field.getAccessor().getValue(), todayIfNone));
        }
    }

    private void onDateSet(@NonNull final int[] fieldIds,
                           @NonNull final long[] selections) {

        for (int i = 0; i < fieldIds.length; i++) {
            if (selections[i] == WrappedMaterialDatePicker.NO_SELECTION) {
                onDateSet(fieldIds[i], "");
            } else {
                onDateSet(fieldIds[i], Instant.ofEpochMilli(selections[i])
                                              .atZone(ZoneId.systemDefault())
                                              .format(DateTimeFormatter.ISO_LOCAL_DATE));
            }
        }
    }

    private void onDateSet(@IdRes final int fieldId,
                           @NonNull final String dateStr) {

        final Field<String, TextView> field = getField(fieldId);
        field.getAccessor().setValue(dateStr);
        field.onChanged(true);

        if (fieldId == R.id.read_end) {
            getField(R.id.cbx_read).getAccessor().setValue(true);
        }
    }

    /**
     * Convert a Field value (String) to an Instant in time.
     *
     * @param field       to extract from
     * @param todayIfNone if set, and the incoming date is null, use 'today' for the date
     *
     * @return instant
     */
    @Nullable
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

    public abstract static class EditItemLauncher<T extends Parcelable>
            extends DialogFragmentLauncherBase {

        private static final String ORIGINAL = "original";
        private static final String MODIFIED = "modified";

        static <T extends Parcelable> void sendResult(@NonNull final Fragment fragment,
                                                      @NonNull final String requestKey,
                                                      @NonNull final T original,
                                                      @NonNull final T modified) {
            final Bundle result = new Bundle(2);
            result.putParcelable(ORIGINAL, original);
            result.putParcelable(MODIFIED, modified);
            fragment.getParentFragmentManager().setFragmentResult(requestKey, result);
        }

        @Override
        public void onFragmentResult(@NonNull final String requestKey,
                                     @NonNull final Bundle result) {
            onResult(Objects.requireNonNull(result.getParcelable(ORIGINAL)),
                     Objects.requireNonNull(result.getParcelable(MODIFIED)));
        }

        /**
         * Callback handler.
         *
         * @param original the original item
         * @param modified the modified item
         */
        public abstract void onResult(@NonNull T original,
                                      @NonNull T modified);
    }
}
