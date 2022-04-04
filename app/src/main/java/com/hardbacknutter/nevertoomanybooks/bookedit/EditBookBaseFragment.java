/*
 * @Copyright 2018-2021 HardBackNutter
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
package com.hardbacknutter.nevertoomanybooks.bookedit;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Parcelable;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.widget.Checkable;
import android.widget.TextView;

import androidx.annotation.CallSuper;
import androidx.annotation.IdRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import androidx.appcompat.widget.Toolbar;
import androidx.core.view.MenuProvider;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.Lifecycle;
import androidx.lifecycle.ViewModelProvider;
import androidx.preference.PreferenceManager;

import com.google.android.material.textfield.TextInputLayout;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Objects;

import com.hardbacknutter.nevertoomanybooks.BaseFragment;
import com.hardbacknutter.nevertoomanybooks.BuildConfig;
import com.hardbacknutter.nevertoomanybooks.FragmentLauncherBase;
import com.hardbacknutter.nevertoomanybooks.R;
import com.hardbacknutter.nevertoomanybooks.database.DBKey;
import com.hardbacknutter.nevertoomanybooks.database.SqlEncode;
import com.hardbacknutter.nevertoomanybooks.datamanager.DataEditor;
import com.hardbacknutter.nevertoomanybooks.dialogs.PartialDatePickerDialogFragment;
import com.hardbacknutter.nevertoomanybooks.entities.Author;
import com.hardbacknutter.nevertoomanybooks.entities.Book;
import com.hardbacknutter.nevertoomanybooks.entities.EntityStage;
import com.hardbacknutter.nevertoomanybooks.fields.EditField;
import com.hardbacknutter.nevertoomanybooks.fields.Field;
import com.hardbacknutter.nevertoomanybooks.utils.ParseUtils;
import com.hardbacknutter.nevertoomanybooks.utils.ViewFocusOrder;
import com.hardbacknutter.nevertoomanybooks.utils.dates.DateParser;
import com.hardbacknutter.nevertoomanybooks.utils.dates.FullDateParser;
import com.hardbacknutter.nevertoomanybooks.utils.dates.PartialDate;
import com.hardbacknutter.nevertoomanybooks.widgets.datepicker.DatePickerListener;
import com.hardbacknutter.nevertoomanybooks.widgets.datepicker.DateRangePicker;
import com.hardbacknutter.nevertoomanybooks.widgets.datepicker.SingleDatePicker;

public abstract class EditBookBaseFragment
        extends BaseFragment
        implements DataEditor<Book> {

    /** Log tag. */
    private static final String TAG = "EditBookBaseFragment";

    /** FragmentResultListener request key. */
    private static final String RK_DATE_PICKER_PARTIAL =
            TAG + ":rk:" + PartialDatePickerDialogFragment.TAG;
    /** The view model. */
    EditBookViewModel mVm;
    /** MUST keep a strong reference. */
    private final DatePickerListener mDatePickerListener = this::onDateSet;
    private final PartialDatePickerDialogFragment.Launcher mPartialDatePickerLauncher =
            new PartialDatePickerDialogFragment.Launcher(RK_DATE_PICKER_PARTIAL) {
                @Override
                public void onResult(@IdRes final int fieldId,
                                     @NonNull final PartialDate date) {
                    onDateSet(fieldId, date.getIsoString());
                }
            };
    private DateParser mDateParser;
    private MenuHandlersMenuProvider mMenuHandlersMenuProvider;
    /** Listener for all field changes. MUST keep strong reference. */
    private final EditField.AfterFieldChangeListener mAfterFieldChangeListener =
            this::onAfterFieldChange;

    @Override
    public void onCreate(@Nullable final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        //noinspection ConstantConditions
        mVm = new ViewModelProvider(getActivity()).get(EditBookViewModel.class);
    }

    @Override
    @CallSuper
    public void onViewCreated(@NonNull final View view,
                              @Nullable final Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        final Toolbar toolbar = getToolbar();
        mMenuHandlersMenuProvider = new MenuHandlersMenuProvider();
        toolbar.addMenuProvider(mMenuHandlersMenuProvider, getViewLifecycleOwner(),
                                Lifecycle.State.RESUMED);

        //noinspection ConstantConditions
        mDateParser = new FullDateParser(getContext());

        mPartialDatePickerLauncher.registerForFragmentResult(getChildFragmentManager(), this);
    }

    @CallSuper
    @Override
    public void onResume() {
        super.onResume();

        final Context context = getContext();
        //noinspection ConstantConditions
        final SharedPreferences global = PreferenceManager.getDefaultSharedPreferences(context);

        final Book book = mVm.getBook();

        // Not sure this is really needed; but it does no harm.
        // In theory, the editing fragment can trigger an internet search,
        // which after it comes back, brings along new data to be transferred to the book.
        // BUT: that new data would not be in the fragment arguments?
        //TODO: double check having book-data bundle in onResume.
        if (book.isNew()) {
            mVm.addFieldsFromBundle(getArguments());
        }

        // update the Fields for THIS fragment with their current View instances
        final List<EditField<?, ? extends View>> fields = mVm.getFields(getFragmentId());
        fields.forEach(field -> {
            //noinspection ConstantConditions
            field.setParentView(global, getView());
            field.setAfterFieldChangeListener(null);
        });

        // Load all Views from the book while preserving the stage of the book.
        book.lockStage();
        // make it so! Child classes can override this method.
        onPopulateViews(fields, book);

        book.unlockStage();

        // Dev note: DO NOT use a 'this' reference directly
        fields.forEach(field -> field.setAfterFieldChangeListener(mAfterFieldChangeListener));

        // All views should now have proper visibility set, so fix their focus order.
        //noinspection ConstantConditions
        ViewFocusOrder.fix(getView());

        final Toolbar toolbar = getToolbar();
        // Set the activity title
        if (book.isNew()) {
            // New book
            toolbar.setTitle(R.string.lbl_add_book);
            toolbar.setSubtitle(null);
        } else {
            // Existing book
            String title = book.getTitle();
            if (BuildConfig.DEBUG /* always */) {
                title = "[" + book.getId() + "] " + title;
            }
            toolbar.setTitle(title);
            toolbar.setSubtitle(Author.getCondensedNames(context, book.getAuthors()));
        }

        mMenuHandlersMenuProvider.onPrepareMenu(toolbar.getMenu());
    }

    /**
     * This is where all fields should be populate with the values coming from the book.
     * The base class (this one) manages all the actual fields, but 'special' fields can/should
     * be handled in overrides, calling super as the first step.
     * <p>
     * The {@link EditField.AfterFieldChangeListener} is disabled and
     * the book is locked during this call.
     *
     * @param fields current field collection
     * @param book   loaded book
     */
    @CallSuper
    void onPopulateViews(@NonNull final List<EditField<?, ? extends View>> fields,
                         @NonNull final Book book) {
        //noinspection ConstantConditions
        final SharedPreferences global = PreferenceManager
                .getDefaultSharedPreferences(getContext());

        if (book.isNew()) {
            if (mVm.handlesField(getFragmentId(), R.id.date_acquired)) {
                book.putString(DBKey.DATE_ACQUIRED, SqlEncode.date(LocalDateTime.now()));
            }

            if (mVm.handlesField(getFragmentId(), R.id.condition)) {
                if (DBKey.isUsed(global, DBKey.KEY_BOOK_CONDITION)) {
                    book.putInt(DBKey.KEY_BOOK_CONDITION, Book.CONDITION_AS_NEW);
                }
            }
        }

        // Bulk load the data into the Views.

        // do NOT call onChanged, as this is the initial load
        fields.stream()
              .filter(Field::isAutoPopulated)
              .forEach(field -> field.setInitialValue(book));

        // With all Views populated, (re-)add the date helpers
        // which rely on fields having valid views

        if (mVm.handlesField(getFragmentId(), R.id.date_published)) {
            addPartialDatePicker(global, R.string.lbl_date_published,
                                 R.id.lbl_date_published, R.id.date_published);
        }

        if (mVm.handlesField(getFragmentId(), R.id.first_publication)) {
            addPartialDatePicker(global, R.string.lbl_first_publication,
                                 R.id.lbl_first_publication, R.id.first_publication);
        }

        if (mVm.handlesField(getFragmentId(), R.id.date_acquired)) {
            addDatePicker(global, R.string.lbl_date_acquired,
                          R.id.lbl_date_acquired, R.id.date_acquired);
        }

        if (mVm.handlesField(getFragmentId(), R.id.read_end)) {
            addDateRangePicker(global, R.string.lbl_read,
                               R.string.lbl_read_start, R.id.lbl_read_start, R.id.read_start,
                               R.string.lbl_read_end, R.id.lbl_read_end, R.id.read_end);
        }

        if (mVm.handlesField(getFragmentId(), R.id.cbx_read)) {
            addReadCheckboxOnClickListener();
        }
    }

    /** Listener for all field changes. */
    @CallSuper
    public void onAfterFieldChange(@NonNull final EditField<?, ? extends View> field) {
        final Book book = mVm.getBook();

        book.setStage(EntityStage.Stage.Dirty);

        // For new books, we copy a number of fields as appropriate by default.
        // These fields might not be initialized, so use the 'Optional' returning method.
        if (book.isNew()) {
            if (field.getId() == R.id.price_listed_currency) {
                mVm.getField(R.id.price_paid_currency).ifPresent(
                        paidField -> {
                            if (!field.isEmpty() && paidField.isEmpty()) {
                                final String value = (String) field.getValue();
                                //noinspection ConstantConditions
                                mVm.getBook().putString(DBKey.PRICE_PAID_CURRENCY, value);
                                //noinspection unchecked
                                ((Field<String, ? extends View>) paidField).setValue(value);
                            }
                        });

            } else if (field.getId() == R.id.price_listed) {
                mVm.getField(R.id.price_paid).ifPresent(
                        paidField -> {
                            if (!field.isEmpty() && paidField.isEmpty()) {
                                // Normally its always a double; but technically it might not be.
                                final Double value = ParseUtils.toDouble(field.getValue(), null);
                                mVm.getBook().put(DBKey.PRICE_PAID, value);
                                //noinspection unchecked
                                ((Field<Double, ? extends View>) paidField).setValue(value);
                            }
                        });
            }
        }

        mMenuHandlersMenuProvider.onPrepareMenu(getToolbar().getMenu());
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
    @CallSuper
    @Override
    public void onSaveFields(@NonNull final Book book) {
        mVm.saveFields(getFragmentId(), book);
    }


    /**
     * Set the OnClickListener for the 'read' fields.
     * <p>
     * When user checks 'read', set the read-end date to today (unless set before)
     */
    private void addReadCheckboxOnClickListener() {
        mVm.requireField(R.id.cbx_read)
           .requireView().setOnClickListener(v -> {
               if (((Checkable) v).isChecked()) {
                   final EditField<String, TextView> readEnd = mVm.requireField(R.id.read_end);
                   if (readEnd.isEmpty()) {
                       readEnd.setValue(
                               LocalDate.now().format(DateTimeFormatter.ISO_LOCAL_DATE));
                       readEnd.onChanged();
                   }
               }
           });
    }

    /**
     * Setup a date picker for selecting a date range.
     * <p>
     * Clicking on the start-date field will allow the user to set just the start-date.
     * Clicking on the end-date will prompt to select both the start and end dates.
     * <p>
     * If only one field is used, we just display a single date picker.
     *
     * @param global       Global preferences
     * @param titleId      title for the picker
     * @param startTitleId title of the picker if the end-date is not in use
     * @param startFieldId to setup for the start-date
     * @param endTitleId   title of the picker if the start-date is not in use
     * @param endFieldId   to setup for the end-date
     */
    private void addDateRangePicker(@NonNull final SharedPreferences global,
                                    @StringRes final int titleId,

                                    final int startTitleId,
                                    @IdRes final int lblStartFieldId,
                                    @IdRes final int startFieldId,

                                    final int endTitleId,
                                    @IdRes final int lblEndFieldId,
                                    @IdRes final int endFieldId) {

        final EditField<String, TextView> startField = mVm.requireField(startFieldId);
        final EditField<String, TextView> endField = mVm.requireField(endFieldId);

        if (startField.isUsed(global)) {
            // Always a single date picker for the start-date
            addDatePicker(global, startTitleId, lblStartFieldId, startFieldId);
        }

        if (endField.isUsed(global)) {
            // If read+end fields are active; use a date-span picker for the end-date
            if (startField.isUsed(global)) {
                final DateRangePicker dp =
                        new DateRangePicker(getChildFragmentManager(),
                                            titleId,
                                            startFieldId, endFieldId);
                dp.setDateParser(mDateParser, true);
                dp.onResume(mDatePickerListener);

                endField.requireView().setOnClickListener(
                        v -> dp.launch(startField.getValue(), endField.getValue(),
                                       mDatePickerListener));

                //noinspection ConstantConditions
                ((TextInputLayout) getView().findViewById(lblEndFieldId))
                        .setEndIconOnClickListener(v -> endField.setValue(""));

            } else {
                // without using a start-date, single date picker for the end-date
                addDatePicker(global, endTitleId, lblEndFieldId, endFieldId);
            }
        }
    }

    /**
     * Setup a date picker for selecting a single, full date.
     *
     * @param global        Global preferences
     * @param pickerTitleId title for the picker window
     * @param lblFieldId    the label view for the field with the end-icon to clear the field
     * @param fieldId       the field to hookup
     */
    private void addDatePicker(@NonNull final SharedPreferences global,
                               @StringRes final int pickerTitleId,
                               @IdRes final int lblFieldId,
                               @IdRes final int fieldId) {

        final EditField<String, TextView> field = mVm.requireField(fieldId);
        if (field.isUsed(global)) {
            final SingleDatePicker dp = new SingleDatePicker(getChildFragmentManager(),
                                                             pickerTitleId, fieldId);
            dp.setDateParser(mDateParser, true);
            dp.onResume(mDatePickerListener);

            field.requireView().setOnClickListener(
                    v -> dp.launch(field.getValue(), mDatePickerListener));

            //noinspection ConstantConditions
            ((TextInputLayout) getView().findViewById(lblFieldId))
                    .setEndIconOnClickListener(v -> field.setValue(""));
        }
    }

    /**
     * Setup a date picker for selecting a partial date.
     *
     * @param global        Global preferences
     * @param pickerTitleId title for the picker window
     * @param lblFieldId    the label view for the field with the end-icon to clear the field
     * @param fieldId       the field to hookup
     */
    private void addPartialDatePicker(@NonNull final SharedPreferences global,
                                      @StringRes final int pickerTitleId,
                                      @IdRes final int lblFieldId,
                                      @IdRes final int fieldId) {
        final EditField<String, TextView> field = mVm.requireField(fieldId);
        if (field.isUsed(global)) {
            field.requireView().setOnClickListener(v -> mPartialDatePickerLauncher
                    .launch(pickerTitleId, field.getId(), field.getValue(), false));

            //noinspection ConstantConditions
            ((TextInputLayout) getView().findViewById(lblFieldId))
                    .setEndIconOnClickListener(v -> field.setValue(""));
        }
    }

    private void onDateSet(@NonNull final int[] fieldIds,
                           @NonNull final long[] selections) {
        for (int i = 0; i < fieldIds.length; i++) {
            if (selections[i] == DatePickerListener.NO_SELECTION) {
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

        final EditField<String, TextView> field = mVm.requireField(fieldId);
        field.setValue(dateStr);
        field.onChanged();

        // special case, if a read-end date is set, then obviously we must set the read-flag
        if (fieldId == R.id.read_end) {
            mVm.requireField(R.id.cbx_read).setValue(true);
        }
    }

    public abstract static class EditItemLauncher<T extends Parcelable>
            extends FragmentLauncherBase {

        private static final String ORIGINAL = "original";
        private static final String MODIFIED = "modified";

        EditItemLauncher(@NonNull final String requestKey) {
            super(requestKey);
        }

        static <T extends Parcelable> void setResult(@NonNull final Fragment fragment,
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
            onResult(Objects.requireNonNull(result.getParcelable(ORIGINAL), ORIGINAL),
                     Objects.requireNonNull(result.getParcelable(MODIFIED), MODIFIED));
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

    private class MenuHandlersMenuProvider
            implements MenuProvider {

        @Override
        public void onCreateMenu(@NonNull final Menu menu,
                                 @NonNull final MenuInflater menuInflater) {
            mVm.getMenuHandlers().forEach(h -> h.onCreateMenu(menu, menuInflater));

            onPrepareMenu(menu);
        }

        @Override
        public void onPrepareMenu(@NonNull final Menu menu) {
            final Book book = mVm.getBook();

            mVm.getMenuHandlers().forEach(h -> h.onPrepareMenu(menu, book));
        }

        @Override
        public boolean onMenuItemSelected(@NonNull final MenuItem menuItem) {
            final Context context = getContext();
            final Book book = mVm.getBook();

            //noinspection ConstantConditions
            return mVm.getMenuHandlers().stream()
                      .anyMatch(h -> h.onMenuItemSelected(context, menuItem, book));
        }
    }
}
