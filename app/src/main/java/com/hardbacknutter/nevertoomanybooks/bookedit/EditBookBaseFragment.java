/*
 * @Copyright 2018-2023 HardBackNutter
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
import android.os.Bundle;
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
import androidx.lifecycle.Lifecycle;
import androidx.lifecycle.ViewModelProvider;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;

import com.hardbacknutter.nevertoomanybooks.BaseFragment;
import com.hardbacknutter.nevertoomanybooks.BuildConfig;
import com.hardbacknutter.nevertoomanybooks.R;
import com.hardbacknutter.nevertoomanybooks.ServiceLocator;
import com.hardbacknutter.nevertoomanybooks.core.parsers.DateParser;
import com.hardbacknutter.nevertoomanybooks.core.parsers.FullDateParser;
import com.hardbacknutter.nevertoomanybooks.core.parsers.ISODateParser;
import com.hardbacknutter.nevertoomanybooks.core.utils.LocaleListUtils;
import com.hardbacknutter.nevertoomanybooks.core.widgets.ViewFocusOrder;
import com.hardbacknutter.nevertoomanybooks.core.widgets.datepicker.DatePickerListener;
import com.hardbacknutter.nevertoomanybooks.core.widgets.datepicker.DateRangePicker;
import com.hardbacknutter.nevertoomanybooks.core.widgets.datepicker.SingleDatePicker;
import com.hardbacknutter.nevertoomanybooks.datamanager.DataEditor;
import com.hardbacknutter.nevertoomanybooks.dialogs.PartialDatePickerDialogFragment;
import com.hardbacknutter.nevertoomanybooks.entities.Author;
import com.hardbacknutter.nevertoomanybooks.entities.Book;
import com.hardbacknutter.nevertoomanybooks.entities.EntityStage;
import com.hardbacknutter.nevertoomanybooks.fields.Field;
import com.hardbacknutter.nevertoomanybooks.utils.dates.PartialDate;

public abstract class EditBookBaseFragment
        extends BaseFragment
        implements DataEditor<Book> {

    /** Log tag. */
    private static final String TAG = "EditBookBaseFragment";

    /** FragmentResultListener request key. */
    private static final String RK_DATE_PICKER_PARTIAL =
            TAG + ":rk:" + PartialDatePickerDialogFragment.TAG;
    /** The view model. */
    EditBookViewModel vm;
    /** MUST keep a strong reference. */
    private final DatePickerListener datePickerListener = this::onDateSet;
    private final PartialDatePickerDialogFragment.Launcher partialDatePickerLauncher =
            new PartialDatePickerDialogFragment.Launcher() {
                @Override
                public void onResult(@IdRes final int fieldId,
                                     @NonNull final PartialDate date) {
                    onDateSet(fieldId, date.getIsoString());
                }
            };
    /** Listener for all field changes. MUST keep strong reference. */
    private final Field.AfterChangedListener afterChangedListener = this::onAfterFieldChange;
    private DateParser dateParser;

    @Override
    public void onCreate(@Nullable final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        //noinspection ConstantConditions
        vm = new ViewModelProvider(getActivity()).get(EditBookViewModel.class);
    }

    @Override
    @CallSuper
    public void onViewCreated(@NonNull final View view,
                              @Nullable final Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        getToolbar().addMenuProvider(new MenuHandlersMenuProvider(), getViewLifecycleOwner(),
                                     Lifecycle.State.RESUMED);

        final Locale systemLocale = ServiceLocator.getInstance().getSystemLocaleList().get(0);
        //noinspection ConstantConditions
        dateParser = new FullDateParser(new ISODateParser(systemLocale),
                                        LocaleListUtils.asList(getContext()));

        partialDatePickerLauncher.registerForFragmentResult(getChildFragmentManager(),
                                                            RK_DATE_PICKER_PARTIAL, this);
    }

    @CallSuper
    @Override
    public void onResume() {
        super.onResume();

        final Book book = vm.getBook();

        // Not sure this is really needed; but it does no harm.
        // In theory, the editing fragment can trigger an internet search,
        // which after it comes back, brings along new data to be transferred to the book.
        // BUT: that new data would not be in the fragment arguments?
        //TODO: double check having book-data bundle in onResume.
        if (book.isNew()) {
            //noinspection ConstantConditions
            vm.addFieldsFromArguments(getContext(), getArguments());
        }

        // update the Fields for THIS fragment with their current View instances
        final List<Field<?, ? extends View>> fields = vm.getFields(getFragmentId());
        fields.forEach(field -> {
            //noinspection ConstantConditions
            field.setParentView(getView());
            field.setAfterFieldChangeListener(null);
        });

        // Load all Views from the book while preserving the stage of the book.
        book.lockStage();
        // make it so! Child classes should override this method,
        // and run 'field.setVisibility' with the flags they need
        onPopulateViews(fields, book);

        book.unlockStage();

        // Dev note: DO NOT use a 'this' reference directly
        fields.forEach(field -> field.setAfterFieldChangeListener(afterChangedListener));

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
            //noinspection ConstantConditions
            toolbar.setSubtitle(Author.getLabel(getContext(), book.getAuthors()));
        }
    }

    /**
     * This is where all fields should be populate with the values coming from the book.
     * The base class (this one) manages all the actual fields, but 'special' fields can/should
     * be handled in overrides, calling super as the first step.
     * <p>
     * The {@link Field.AfterChangedListener} is disabled and
     * the book is locked during this call.
     *
     * @param fields current field collection
     * @param book   loaded book
     */
    @CallSuper
    void onPopulateViews(@NonNull final List<Field<?, ? extends View>> fields,
                         @NonNull final Book book) {
        // Bulk load the data into the Views.

        // do NOT call notifyIfChanged, as this is the initial load
        //noinspection ConstantConditions
        fields.stream()
              .filter(Field::isAutoPopulated)
              .forEach(field -> field.setInitialValue(getContext(), book));

        // With all Views populated, (re-)add the date helpers
        // which rely on fields having valid views

        if (vm.handlesField(getFragmentId(), R.id.date_published)) {
            addPartialDatePicker(R.string.lbl_date_published, R.id.date_published);
        }

        if (vm.handlesField(getFragmentId(), R.id.first_publication)) {
            addPartialDatePicker(R.string.lbl_date_first_publication, R.id.first_publication);
        }

        if (vm.handlesField(getFragmentId(), R.id.date_acquired)) {
            addDatePicker(R.string.lbl_date_acquired, R.id.date_acquired);
        }

        if (vm.handlesField(getFragmentId(), R.id.read_end)) {
            addDateRangePicker(R.string.lbl_read,
                               R.string.lbl_read_start, R.id.read_start,
                               R.string.lbl_read_end, R.id.read_end);
        }

        if (vm.handlesField(getFragmentId(), R.id.cbx_read)) {
            addReadCheckboxOnClickListener();
        }
    }

    /** Listener for all field changes. */
    @SuppressWarnings("WeakerAccess")
    @CallSuper
    void onAfterFieldChange(@NonNull final Field<?, ? extends View> field) {
        vm.getBook().setStage(EntityStage.Stage.Dirty);
    }

    @Override
    @CallSuper
    public void onPause() {
        vm.setUnfinishedEdits(getFragmentId(), hasUnfinishedEdits());
        if (vm.getBook().getStage() == EntityStage.Stage.Dirty) {
            onSaveFields(vm.getBook());
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
        vm.saveFields(getFragmentId(), book);
    }


    /**
     * Set the OnClickListener for the 'read' fields.
     * <p>
     * When user checks 'read', set the read-end date to today (unless set before)
     */
    private void addReadCheckboxOnClickListener() {
        vm.requireField(R.id.cbx_read).requireView().setOnClickListener(v -> {
            if (((Checkable) v).isChecked()) {
                final Field<String, TextView> readEnd = vm.requireField(R.id.read_end);
                if (readEnd.isEmpty()) {
                    readEnd.setValue(LocalDate.now().format(DateTimeFormatter.ISO_LOCAL_DATE));
                    readEnd.notifyIfChanged("");
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
     * @param titleId      title for the picker
     * @param startTitleId title of the picker if the end-date is not in use
     * @param startFieldId to setup for the start-date
     * @param endTitleId   title of the picker if the start-date is not in use
     * @param endFieldId   to setup for the end-date
     */
    private void addDateRangePicker(@StringRes final int titleId,
                                    final int startTitleId,
                                    @IdRes final int startFieldId,
                                    final int endTitleId,
                                    @IdRes final int endFieldId) {

        final Context context = getContext();
        final Field<String, TextView> startField = vm.requireField(startFieldId);
        //noinspection ConstantConditions
        final boolean startFieldIsUsed = startField.isUsed(context);
        final Field<String, TextView> endField = vm.requireField(endFieldId);
        final boolean endFieldIsUsed = endField.isUsed(context);

        if (startFieldIsUsed) {
            // Always a single date picker for the start-date
            addDatePicker(startTitleId, startFieldId);
        }

        if (endFieldIsUsed) {
            // If read+end fields are active; use a date-span picker for the end-date
            if (startFieldIsUsed) {
                final DateRangePicker dp = new DateRangePicker(getChildFragmentManager(),
                                                               titleId, startFieldId, endFieldId);
                dp.setDateParser(dateParser, true);
                dp.onResume(datePickerListener);

                endField.requireView().setOnClickListener(v -> dp
                        .launch(startField.getValue(), endField.getValue(), datePickerListener));
            } else {
                // without using a start-date, single date picker for the end-date
                addDatePicker(endTitleId, endFieldId);
            }
        }
    }

    /**
     * Setup a date picker for selecting a single, full date.
     *
     * @param pickerTitleId title for the picker window
     * @param fieldId       the field to hookup
     */
    private void addDatePicker(@StringRes final int pickerTitleId,
                               @IdRes final int fieldId) {

        final Field<String, TextView> field = vm.requireField(fieldId);
        //noinspection ConstantConditions
        if (field.isUsed(getContext())) {
            final SingleDatePicker dp = new SingleDatePicker(getChildFragmentManager(),
                                                             pickerTitleId, fieldId);
            dp.setDateParser(dateParser, true);
            dp.onResume(datePickerListener);

            field.requireView().setOnClickListener(v -> dp
                    .launch(field.getValue(), datePickerListener));
        }
    }

    /**
     * Setup a date picker for selecting a partial date.
     *
     * @param pickerTitleId title for the picker window
     * @param fieldId       the field to hookup
     */
    private void addPartialDatePicker(@StringRes final int pickerTitleId,
                                      @IdRes final int fieldId) {
        final Field<String, TextView> field = vm.requireField(fieldId);
        //noinspection ConstantConditions
        if (field.isUsed(getContext())) {
            field.requireView().setOnClickListener(v -> partialDatePickerLauncher
                    .launch(pickerTitleId, field.getFieldViewId(), field.getValue(), false));
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

        final Field<String, TextView> field = vm.requireField(fieldId);
        final String previous = field.getValue();
        field.setValue(dateStr);
        field.notifyIfChanged(previous);

        // special case, if a read-end date is set, then obviously we must set the read-flag
        if (fieldId == R.id.read_end) {
            vm.requireField(R.id.cbx_read).setValue(true);
        }
    }

    private class MenuHandlersMenuProvider
            implements MenuProvider {

        @Override
        public void onCreateMenu(@NonNull final Menu menu,
                                 @NonNull final MenuInflater menuInflater) {
            //noinspection ConstantConditions
            vm.getMenuHandlers().forEach(h -> h.onCreateMenu(getContext(), menu, menuInflater));
        }

        @Override
        public void onPrepareMenu(@NonNull final Menu menu) {
            final Book book = vm.getBook();

            vm.getMenuHandlers().forEach(h -> h.onPrepareMenu(menu, book));
        }

        @Override
        public boolean onMenuItemSelected(@NonNull final MenuItem menuItem) {
            final Context context = getContext();
            final Book book = vm.getBook();

            //noinspection ConstantConditions
            return vm.getMenuHandlers().stream()
                     .anyMatch(h -> h.onMenuItemSelected(context, menuItem, book));
        }
    }
}
