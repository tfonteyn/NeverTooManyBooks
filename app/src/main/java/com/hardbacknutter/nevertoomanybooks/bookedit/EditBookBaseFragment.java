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
package com.hardbacknutter.nevertoomanybooks.bookedit;

import android.content.Context;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.widget.TextView;

import androidx.annotation.CallSuper;
import androidx.annotation.IdRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import androidx.appcompat.widget.Toolbar;
import androidx.core.view.MenuProvider;
import androidx.fragment.app.FragmentManager;
import androidx.lifecycle.Lifecycle;
import androidx.lifecycle.ViewModelProvider;

import java.time.Instant;
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
import com.hardbacknutter.nevertoomanybooks.core.parsers.RealNumberParser;
import com.hardbacknutter.nevertoomanybooks.core.utils.LocaleListUtils;
import com.hardbacknutter.nevertoomanybooks.core.widgets.ViewFocusOrder;
import com.hardbacknutter.nevertoomanybooks.core.widgets.datepicker.DatePickerListener;
import com.hardbacknutter.nevertoomanybooks.core.widgets.datepicker.DateRangePicker;
import com.hardbacknutter.nevertoomanybooks.core.widgets.datepicker.SingleDatePicker;
import com.hardbacknutter.nevertoomanybooks.database.DBKey;
import com.hardbacknutter.nevertoomanybooks.datamanager.DataEditor;
import com.hardbacknutter.nevertoomanybooks.dialogs.PartialDatePickerLauncher;
import com.hardbacknutter.nevertoomanybooks.entities.Author;
import com.hardbacknutter.nevertoomanybooks.entities.Book;
import com.hardbacknutter.nevertoomanybooks.entities.EntityStage;
import com.hardbacknutter.nevertoomanybooks.fields.Field;

public abstract class EditBookBaseFragment
        extends BaseFragment
        implements DataEditor<Book> {

    /** The view model. */
    EditBookViewModel vm;

    /** MUST keep a strong reference. */
    private final DatePickerListener datePickerListener = this::onDateSet;
    /** Listener for all field changes. MUST keep strong reference. */
    private final Field.AfterChangedListener afterChangedListener = this::onAfterFieldChange;
    private PartialDatePickerLauncher partialDatePickerLauncher;
    private DateParser dateParser;
    private RealNumberParser realNumberParser;

    @Override
    public void onCreate(@Nullable final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        //noinspection DataFlowIssue
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
        //noinspection DataFlowIssue
        final List<Locale> locales = LocaleListUtils.asList(getContext());
        dateParser = new FullDateParser(systemLocale, locales);
        realNumberParser = new RealNumberParser(locales);

        final FragmentManager fm = getChildFragmentManager();

        partialDatePickerLauncher = new PartialDatePickerLauncher(
                (fieldId, date) -> onDateSet(fieldId, date.getIsoString()));
        partialDatePickerLauncher.registerForFragmentResult(fm, this);
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
            //noinspection DataFlowIssue
            vm.addFieldsFromArguments(getContext(), getArguments());
        }

        // update the Fields for THIS fragment with their current View instances
        final List<Field<?, ? extends View>> fields = vm.getFields(getFragmentId());
        fields.forEach(field -> {
            //noinspection DataFlowIssue
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
        //noinspection DataFlowIssue
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
            //noinspection DataFlowIssue
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
        //noinspection DataFlowIssue
        fields.stream()
              .filter(Field::isAutoPopulated)
              .forEach(field -> field.setInitialValue(getContext(), book, realNumberParser));

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
    }

    /**
     * Listener for all field changes.
     *
     * @param field which got changed
     */
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

        final Field<String, TextView> startField = vm.requireField(startFieldId);
        final boolean startFieldIsUsed = startField.isUsed();
        final Field<String, TextView> endField = vm.requireField(endFieldId);
        final boolean endFieldIsUsed = endField.isUsed();

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
        if (field.isUsed()) {
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
        if (field.isUsed()) {
            //noinspection DataFlowIssue
            field.requireView().setOnClickListener(v -> partialDatePickerLauncher
                    .launch(getActivity(), pickerTitleId, field.getFieldViewId(),
                            field.getValue(), false));
        }
    }

    private void onDateSet(@NonNull final int[] fieldIds,
                           @NonNull final Long[] selections) {
        for (int i = 0; i < fieldIds.length; i++) {
            if (selections[i] == null) {
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

        // If we are setting the read-end date,
        // then we must set the read-flag/progress accordingly
        if (fieldId == R.id.read_end && !dateStr.isEmpty()) {
            final Book book = vm.getBook();
            book.putBoolean(DBKey.READ__BOOL, true);
            book.putString(DBKey.READ_PROGRESS, "");
        }
    }

    void onReadStatusChanged() {
        // Refresh the read_end value displayed
        final Field<String, TextView> readEnd = vm.requireField(R.id.read_end);
        readEnd.setValue(vm.getBook().getString(DBKey.READ_END__DATE));
    }

    private class MenuHandlersMenuProvider
            implements MenuProvider {

        @Override
        public void onCreateMenu(@NonNull final Menu menu,
                                 @NonNull final MenuInflater menuInflater) {
            //noinspection DataFlowIssue
            vm.getMenuHandlers().forEach(h -> h.onCreateMenu(getContext(), menu, menuInflater));
        }

        @Override
        public void onPrepareMenu(@NonNull final Menu menu) {
            final Context context = getContext();
            final Book book = vm.getBook();

            //noinspection DataFlowIssue
            vm.getMenuHandlers().forEach(h -> h.onPrepareMenu(context, menu, book));
        }

        @Override
        public boolean onMenuItemSelected(@NonNull final MenuItem menuItem) {
            final Context context = getContext();
            final Book book = vm.getBook();

            //noinspection DataFlowIssue
            return vm.getMenuHandlers().stream()
                     .anyMatch(h -> h.onMenuItemSelected(context, menuItem.getItemId(), book));
        }
    }
}
