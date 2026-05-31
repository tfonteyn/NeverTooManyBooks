/*
 * @Copyright 2018-2026 HardBackNutter
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

import androidx.activity.result.ActivityResultLauncher;
import androidx.annotation.CallSuper;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import androidx.appcompat.widget.Toolbar;
import androidx.core.view.MenuCompat;
import androidx.core.view.MenuProvider;
import androidx.fragment.app.FragmentManager;
import androidx.lifecycle.Lifecycle;
import androidx.lifecycle.ViewModelProvider;

import java.util.List;

import com.hardbacknutter.nevertoomanybooks.ActivityRestarter;
import com.hardbacknutter.nevertoomanybooks.BaseFragment;
import com.hardbacknutter.nevertoomanybooks.BuildConfig;
import com.hardbacknutter.nevertoomanybooks.R;
import com.hardbacknutter.nevertoomanybooks.activityresultcontracts.EditBookshelvesContract;
import com.hardbacknutter.nevertoomanybooks.activityresultcontracts.SettingsContract;
import com.hardbacknutter.nevertoomanybooks.core.parsers.RealNumberParser;
import com.hardbacknutter.nevertoomanybooks.core.widgets.ViewFocusOrder;
import com.hardbacknutter.nevertoomanybooks.core.widgets.datepicker.DatePickerListener;
import com.hardbacknutter.nevertoomanybooks.core.widgets.datepicker.DateRangePicker;
import com.hardbacknutter.nevertoomanybooks.core.widgets.datepicker.SingleDatePicker;
import com.hardbacknutter.nevertoomanybooks.database.DBKey;
import com.hardbacknutter.nevertoomanybooks.datamanager.DataEditor;
import com.hardbacknutter.nevertoomanybooks.dialogs.inmemory.partialdate.PartialDatePickerLauncher;
import com.hardbacknutter.nevertoomanybooks.entities.Author;
import com.hardbacknutter.nevertoomanybooks.entities.Book;
import com.hardbacknutter.nevertoomanybooks.entities.EntityStage;
import com.hardbacknutter.nevertoomanybooks.fields.Field;
import com.hardbacknutter.nevertoomanybooks.utils.dates.DateUtils;

public abstract class EditBookBaseFragment
        extends BaseFragment
        implements DataEditor<Book> {

    private static final String TAG = "EditBookBaseFragment";
    private static final String RK_DATE_PICKER_PARTIAL = TAG + ":rk:pd";
    private static final String BKEY_DATE_PICKER_FIELD_KEY = TAG + ":pd:fieldKey";

    private ActivityResultLauncher<String> editSettingsLauncher;
    private ActivityResultLauncher<Long> manageBookshelvesLauncher;

    /** The view model. */
    EditBookViewModel vm;

    /** MUST keep a strong reference. */
    private final DatePickerListener datePickerListener = (fieldIds, selections)
            -> vm.onDateSet(fieldIds, selections);

    /** Listener for all field changes. MUST keep strong reference. */
    private final Field.AfterChangedListener afterChangedListener = this::onAfterFieldChange;

    private final PartialDatePickerLauncher.ResultListener partialDatePickerListener =
            (previousSelection, currentSelection, extras) -> {
                if (extras == null) {
                    throw new IllegalArgumentException("No extras?");
                }
                final String fieldKey = extras.getString(BKEY_DATE_PICKER_FIELD_KEY, null);
                if (fieldKey == null) {
                    throw new IllegalArgumentException("No fieldKey?");
                }
                vm.onDateSet(fieldKey, currentSelection.getIsoString());
            };

    private PartialDatePickerLauncher partialDatePickerLauncher;

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

        final FragmentManager fm = getChildFragmentManager();

        manageBookshelvesLauncher = registerForActivityResult(
                new EditBookshelvesContract(), ignored -> {
                });

        editSettingsLauncher = registerForActivityResult(
                new SettingsContract(), o -> o.ifPresent(result -> {
                    if (result.isRecreateActivity()) {
                        ActivityRestarter.recreate();
                    }
                }));

        partialDatePickerLauncher = new PartialDatePickerLauncher(RK_DATE_PICKER_PARTIAL,
                                                                  partialDatePickerListener);
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
            vm.addFieldsFromArguments(getArguments());
        }

        // update the Fields for THIS fragment with their current View instances
        final List<Field<?, ? extends View>> fields = vm.getFields(getFragmentId());
        fields.forEach(field -> {
            //noinspection DataFlowIssue
            field.setParentView(getView());
            // disable before we call onPopulateViews below
            field.setAfterFieldChangeListener(null);
        });

        // Load all Views from the book while preserving the stage of the book.
        book.lockStage();
        // make it so! Child classes should override this method,
        // and run 'field.setVisibility' with the flags they need
        onPopulateViews(fields, book);

        addDateHelpers();

        book.unlockStage();

        // re-enable after the onPopulateViews call above
        // Dev note: DO NOT use a 'this' reference directly
        fields.forEach(field -> field.setAfterFieldChangeListener(afterChangedListener));

        // All views should now have proper visibility set, so fix their focus order.
        //noinspection DataFlowIssue
        ViewFocusOrder.fix(getView());

        updateScreenTitle(book);
    }

    /**
     * Set the screen Toolbar title.
     *
     * @param book to use
     */
    private void updateScreenTitle(@NonNull final Book book) {
        final Toolbar toolbar = getToolbar();

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
     * This is where all fields should be populated with the values coming from the book.
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

        final Context context = getContext();
        final RealNumberParser realNumberParser = vm.getRealNumberParser();
        // Bulk load the data into the Views.
        // do NOT call notifyIfChanged, as this is the initial load
        //noinspection DataFlowIssue
        fields.stream()
              .filter(Field::isAutoPopulated)
              .forEach(field -> field.load(context, book, realNumberParser));
    }

    /**
     * Called immediately after {@link #onPopulateViews(List, Book)}.
     */
    private void addDateHelpers() {
        // With all Views populated, (re-)add the date helpers
        // which rely on fields having valid views
        // Instead of each fragment doing their own, we've centralised
        // them all here for ease of maintenance

        if (vm.handlesField(getFragmentId(), DBKey.PUBLICATION_DATE)) {
            addPartialDatePicker(R.string.lbl_date_published, DBKey.PUBLICATION_DATE);
        }

        if (vm.handlesField(getFragmentId(), DBKey.FIRST_PUBLICATION_DATE)) {
            addPartialDatePicker(R.string.lbl_date_first_publication, DBKey.FIRST_PUBLICATION_DATE);
        }

        if (vm.handlesField(getFragmentId(), DBKey.DATE_ACQUIRED)) {
            addDatePicker(R.string.lbl_date_acquired, DBKey.DATE_ACQUIRED);
        }

        if (vm.handlesField(getFragmentId(), DBKey.READ_END__DATE)) {
            addDateRangePicker(R.string.lbl_read,
                               R.string.lbl_read_start, DBKey.READ_START__DATE,
                               R.string.lbl_read_end, DBKey.READ_END__DATE);
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
     * Set up a date picker for selecting a date range.
     * <p>
     * Clicking on the start-date field will allow the user to set just the start-date.
     * Clicking on the end-date will prompt to select both the start and end dates.
     * <p>
     * If only one field is used, we just display a single date picker.
     *
     * @param titleId      title for the picker
     * @param startTitleId title of the picker if the end-date is not in use
     * @param startFieldKey to set up for the start-date
     * @param endTitleId   title of the picker if the start-date is not in use
     * @param endFieldKey   to set up for the end-date
     */
    private void addDateRangePicker(@StringRes final int titleId,
                                    final int startTitleId,
                                    @NonNull final String startFieldKey,
                                    final int endTitleId,
                                    @NonNull final String endFieldKey) {

        final Field<String, TextView> startField = vm.requireField(startFieldKey);
        final boolean startFieldIsUsed = startField.isUsed();
        final Field<String, TextView> endField = vm.requireField(endFieldKey);
        final boolean endFieldIsUsed = endField.isUsed();

        if (startFieldIsUsed) {
            // Always a single date picker for the start-date
            addDatePicker(startTitleId, startFieldKey);
        }

        if (endFieldIsUsed) {
            // If read+end fields are active; use a date-span picker for the end-date
            if (startFieldIsUsed) {
                final DateRangePicker dp = new DateRangePicker(getChildFragmentManager(),
                                                               titleId, startFieldKey, endFieldKey);
                dp.setDateParser(vm.getDateParser(), true);
                dp.onResume(datePickerListener);

                endField.requireView().setOnClickListener(v -> dp
                        .launch(startField.getValue(), endField.getValue(), datePickerListener));
            } else {
                // without using a start-date, single date picker for the end-date
                addDatePicker(endTitleId, endFieldKey);
            }
        }
    }

    /**
     * Set up a date picker for selecting a single, full date.
     *
     * @param pickerTitleId title for the picker window
     * @param fieldKey      the field to hookup
     */
    private void addDatePicker(@StringRes final int pickerTitleId,
                               @NonNull final String fieldKey) {

        final Field<String, TextView> field = vm.requireField(fieldKey);
        if (field.isUsed()) {
            final SingleDatePicker dp = new SingleDatePicker(getChildFragmentManager(),
                                                             pickerTitleId, fieldKey);
            dp.setDateParser(vm.getDateParser(), true);
            dp.onResume(datePickerListener);

            field.requireView().setOnClickListener(v -> dp
                    .launch(field.getValue(), datePickerListener));
        }
    }

    /**
     * Set up a date picker for selecting a partial date.
     *
     * @param pickerTitleId title for the picker window
     * @param fieldKey      the field to hookup
     */
    private void addPartialDatePicker(@StringRes final int pickerTitleId,
                                      @NonNull final String fieldKey) {
        final Field<String, TextView> field = vm.requireField(fieldKey);
        if (field.isUsed()) {
            field.requireView().setOnClickListener(v -> {
                // We're using the extras to pass the field id
                final Bundle extras = new Bundle(1);
                extras.putString(BKEY_DATE_PICKER_FIELD_KEY, field.getFieldKey());
                //noinspection DataFlowIssue
                partialDatePickerLauncher.launch(
                        getActivity(),
                        getString(pickerTitleId),
                        null,
                        DateUtils.todayIfNone(field.getValue(), false),
                        extras);
            });
        }
    }

    void onReadStatusUpdate(@NonNull final Boolean modified) {
        // Refresh the read_end value displayed
        final Field<String, TextView> readEnd = vm.requireField(DBKey.READ_END__DATE);
        readEnd.setValue(vm.getBook().getString(DBKey.READ_END__DATE));
    }

    private final class MenuHandlersMenuProvider
            implements MenuProvider {

        @Override
        public void onCreateMenu(@NonNull final Menu menu,
                                 @NonNull final MenuInflater inflater) {
            inflater.inflate(R.menu.settings, menu);
            MenuCompat.setGroupDividerEnabled(menu, true);

            final Book book = vm.getBook();
            //noinspection DataFlowIssue
            vm.getMenuHandlers().forEach(
                    h -> h.onCreateMenu(getContext(), inflater, menu, book));
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

            final int menuItemId = menuItem.getItemId();

            if (menuItemId == R.id.MENU_MANAGE_BOOKSHELVES) {
                // ENHANCE: if we ever have a primary-bookshelf, we should pass it here
                manageBookshelvesLauncher.launch(0L);
                return true;

            } else if (menuItemId == R.id.MENU_SETTINGS) {
                editSettingsLauncher.launch(null);
                return true;

            }

            //noinspection DataFlowIssue
            return vm.getMenuHandlers().stream()
                     .anyMatch(h -> h.onMenuItemSelected(context, menuItem.getItemId(), book));
        }
    }
}
