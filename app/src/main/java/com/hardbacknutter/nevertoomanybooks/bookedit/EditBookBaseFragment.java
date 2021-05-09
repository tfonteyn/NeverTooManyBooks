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

import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Parcelable;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.widget.AutoCompleteTextView;
import android.widget.Button;
import android.widget.TextView;

import androidx.annotation.CallSuper;
import androidx.annotation.IdRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.lifecycle.ViewModelProvider;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Objects;
import java.util.function.Supplier;

import com.hardbacknutter.nevertoomanybooks.BaseFragment;
import com.hardbacknutter.nevertoomanybooks.BuildConfig;
import com.hardbacknutter.nevertoomanybooks.FragmentLauncherBase;
import com.hardbacknutter.nevertoomanybooks.R;
import com.hardbacknutter.nevertoomanybooks.database.DBKey;
import com.hardbacknutter.nevertoomanybooks.datamanager.DataEditor;
import com.hardbacknutter.nevertoomanybooks.dialogs.PartialDatePickerDialogFragment;
import com.hardbacknutter.nevertoomanybooks.entities.Author;
import com.hardbacknutter.nevertoomanybooks.entities.Book;
import com.hardbacknutter.nevertoomanybooks.entities.EntityStage;
import com.hardbacknutter.nevertoomanybooks.fields.Field;
import com.hardbacknutter.nevertoomanybooks.fields.FieldArrayAdapter;
import com.hardbacknutter.nevertoomanybooks.fields.Fields;
import com.hardbacknutter.nevertoomanybooks.fields.formatters.FieldFormatter;
import com.hardbacknutter.nevertoomanybooks.searchengines.amazon.AmazonHandler;
import com.hardbacknutter.nevertoomanybooks.utils.ViewBookOnWebsiteHandler;
import com.hardbacknutter.nevertoomanybooks.utils.ViewFocusOrder;
import com.hardbacknutter.nevertoomanybooks.utils.dates.PartialDate;
import com.hardbacknutter.nevertoomanybooks.widgets.WrappedMaterialDatePicker;

public abstract class EditBookBaseFragment
        extends BaseFragment
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

    private final WrappedMaterialDatePicker.Launcher mDatePickerLauncher =
            new WrappedMaterialDatePicker.Launcher(RK_DATE_PICKER_SINGLE) {
                @Override
                public void onResult(@NonNull final int[] fieldIds,
                                     @NonNull final long[] selections) {
                    onDateSet(fieldIds, selections);
                }
            };

    private final WrappedMaterialDatePicker.Launcher mDateRangePickerLauncher =
            new WrappedMaterialDatePicker.Launcher(RK_DATE_PICKER_RANGE) {
                @Override
                public void onResult(@NonNull final int[] fieldIds,
                                     @NonNull final long[] selections) {
                    onDateSet(fieldIds, selections);
                }
            };

    /** The view model. */
    EditBookViewModel mVm;

    private final PartialDatePickerDialogFragment.Launcher mPartialDatePickerLauncher =
            new PartialDatePickerDialogFragment.Launcher(RK_DATE_PICKER_PARTIAL) {
                @Override
                public void onResult(@IdRes final int fieldId,
                                     @NonNull final PartialDate date) {
                    onDateSet(fieldId, date.getIsoString());
                }
            };
    /** Listener for all field changes. Must keep strong reference. */
    private final Fields.AfterChangeListener mAfterChangeListener =
            fieldId -> mVm.getBook().setStage(EntityStage.Stage.Dirty);
    @Nullable
    private AmazonHandler mAmazonHandler;
    @Nullable
    private ViewBookOnWebsiteHandler mViewBookOnWebsiteHandler;

    /**
     * Init all Fields, and add them the fields collection.
     * <p>
     * Note that Field views are <strong>NOT AVAILABLE</strong> at this time.
     * <p>
     * Called from {@link #onViewCreated}.
     * The fields will be populated in {@link #onPopulateViews}
     *
     * @param fields collection to add to
     */
    abstract void onInitFields(@NonNull Fields fields);

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

    @NonNull
    private Fields getFields() {
        return mVm.getFields(getFragmentId());
    }

    @Override
    public void onCreate(@Nullable final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setHasOptionsMenu(true);

        final FragmentManager fm = getChildFragmentManager();

        mPartialDatePickerLauncher.registerForFragmentResult(fm, this);
        mDatePickerLauncher.registerForFragmentResult(fm, this);
        mDateRangePickerLauncher.registerForFragmentResult(fm, this);
    }

    @Override
    @CallSuper
    public void onViewCreated(@NonNull final View view,
                              @Nullable final Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        //noinspection ConstantConditions
        mViewBookOnWebsiteHandler = new ViewBookOnWebsiteHandler(getContext());
        mAmazonHandler = new AmazonHandler(getContext());

        //noinspection ConstantConditions
        mVm = new ViewModelProvider(getActivity()).get(EditBookViewModel.class);

        final Fields fields = getFields();
        if (fields.isEmpty()) {
            onInitFields(fields);
        }
    }

    @Override
    public void onCreateOptionsMenu(@NonNull final Menu menu,
                                    @NonNull final MenuInflater inflater) {
        inflater.inflate(R.menu.toolbar_action_save, menu);

        final MenuItem menuItem = menu.findItem(R.id.MENU_ACTION_CONFIRM);
        final Button button = menuItem.getActionView().findViewById(R.id.btn_confirm);
        button.setText(menuItem.getTitle());
        button.setOnClickListener(v -> onOptionsItemSelected(menuItem));

        if (menu.findItem(R.id.SUBMENU_VIEW_BOOK_AT_SITE) == null) {
            inflater.inflate(R.menu.sm_view_on_site, menu);
        }
        if (menu.findItem(R.id.SUBMENU_AMAZON_SEARCH) == null) {
            inflater.inflate(R.menu.sm_search_on_amazon, menu);
        }
        super.onCreateOptionsMenu(menu, inflater);
    }

    @Override
    public void onPrepareOptionsMenu(@NonNull final Menu menu) {
        final Book book = mVm.getBook();
        //noinspection ConstantConditions
        mViewBookOnWebsiteHandler.prepareMenu(menu, book);
        //noinspection ConstantConditions
        mAmazonHandler.prepareMenu(menu, book);

        super.onPrepareOptionsMenu(menu);
    }

    @CallSuper
    @Override
    public boolean onOptionsItemSelected(@NonNull final MenuItem item) {
        final Book book = mVm.getBook();
        final int itemId = item.getItemId();

        if (itemId == R.id.MENU_ACTION_CONFIRM) {
            //noinspection ConstantConditions
            ((EditBookActivity) getActivity()).prepareSave(true);
            return true;
        }

        if (mAmazonHandler != null && mAmazonHandler.onItemSelected(itemId, book)) {
            return true;
        }

        //noinspection ConstantConditions
        if (mViewBookOnWebsiteHandler.onItemSelected(item.getItemId(), book)) {
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    @CallSuper
    @Override
    public void onResume() {

        // Not sure this is really needed; but it does no harm.
        // In theory, the editing fragment can trigger an internet search,
        // which after it comes back, brings along new data to be transferred to the book.
        // BUT: that new data would not be in the fragment arguments?
        //TODO: double check having book-data bundle in onResume.
        if (mVm.getBook().isNew()) {
            mVm.addFieldsFromBundle(getArguments());
        }

        super.onResume();
        // hook up the Views, and calls {@link #onPopulateViews}
        populateViews();
    }

    /**
     * Load all Views from the book while preserving the isDirty() status.
     * <p>
     * This is final/private; child classes should override {@link #onPopulateViews}.
     */
    private void populateViews() {
        final Fields fields = getFields();
        //noinspection ConstantConditions
        fields.setParentView(getView());

        fields.setAfterChangeListener(null);
        final Book book = mVm.getBook();
        book.lockStage();
        // make it so!
        onPopulateViews(fields, book);
        book.unlockStage();
        fields.setAfterChangeListener(mAfterChangeListener);

        // All views should now have proper visibility set, so fix their focus order.
        ViewFocusOrder.fix(getView());

        // Set the activity title
        if (book.isNew()) {
            // New book
            setTitle(R.string.lbl_add_book);
            setSubtitle(null);
        } else {
            // Existing book
            String title = book.getString(DBKey.KEY_TITLE);
            if (BuildConfig.DEBUG /* always */) {
                title = "[" + book.getId() + "] " + title;
            }
            setTitle(title);
            //noinspection ConstantConditions
            setSubtitle(Author.getCondensedNames(
                    getContext(), book.getParcelableArrayList(Book.BKEY_AUTHOR_LIST)));
        }
    }

    /**
     * This is where you should populate all the fields with the values coming from the book.
     * The base class (this one) manages all the actual fields, but 'special' fields can/should
     * be handled in overrides, calling super as the first step.
     *
     * @param fields current field collection
     * @param book   loaded book
     */
    @CallSuper
    void onPopulateViews(@NonNull final Fields fields,
                         @NonNull final Book book) {
        fields.setAll(book);
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
     * @param global       Global preferences
     * @param field        to setup
     * @param listSupplier Supplier with auto complete values
     */
    void addAutocomplete(@NonNull final SharedPreferences global,
                         @NonNull final Field<String, AutoCompleteTextView> field,
                         @NonNull final Supplier<List<String>> listSupplier) {

        // only bother when it's in use
        if (field.isUsed(global)) {
            final FieldFormatter<String> formatter = field.getAccessor().getFormatter();
            final AutoCompleteTextView view = field.getAccessor().getView();
            //noinspection ConstantConditions
            view.setAdapter(new FieldArrayAdapter(getContext(), listSupplier.get(), formatter));
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
     * @param global           Global preferences
     * @param dateSpanTitleId  title of the dialog box if both start and end-dates are used.
     * @param startDateTitleId title of the dialog box if the end-date is not in use
     * @param fieldStartDate   to setup for the start-date
     * @param endDateTitleId   title of the dialog box if the start-date is not in use
     * @param fieldEndDate     to setup for the end-date
     * @param todayIfNone      if true, and if the field was empty, we'll default to today's date.
     */
    @SuppressWarnings("SameParameterValue")
    void addDateRangePicker(@NonNull final SharedPreferences global,
                            @StringRes final int dateSpanTitleId,
                            @StringRes final int startDateTitleId,
                            @NonNull final Field<String, TextView> fieldStartDate,
                            @StringRes final int endDateTitleId,
                            @NonNull final Field<String, TextView> fieldEndDate,
                            final boolean todayIfNone) {

        final boolean startUsed = fieldStartDate.isUsed(global);
        if (startUsed) {
            // single date picker for the start-date
            //noinspection ConstantConditions
            fieldStartDate.getAccessor().getView().setOnClickListener(v -> mDatePickerLauncher
                    .launch(startDateTitleId, fieldStartDate.getId(),
                            mVm.getInstant(fieldStartDate, todayIfNone)));
        }

        if (fieldEndDate.isUsed(global)) {
            final TextView view = fieldEndDate.getAccessor().getView();
            if (startUsed) {
                // date-span picker for the end-date
                //noinspection ConstantConditions
                view.setOnClickListener(v -> mDateRangePickerLauncher
                        .launch(dateSpanTitleId,
                                fieldStartDate.getId(), mVm.getInstant(fieldStartDate, todayIfNone),
                                fieldEndDate.getId(), mVm.getInstant(fieldEndDate, todayIfNone)));
            } else {
                // without using a start-date, single date picker for the end-date
                //noinspection ConstantConditions
                view.setOnClickListener(v -> mDatePickerLauncher
                        .launch(endDateTitleId, fieldEndDate.getId(),
                                mVm.getInstant(fieldEndDate, todayIfNone)));
            }
        }
    }

    /**
     * Setup a date picker for selecting a single, full date.
     *
     * @param global      Global preferences
     * @param field       to setup
     * @param titleId     title for the picker window
     * @param todayIfNone if true, and if the field was empty, we'll default to today's date.
     */
    @SuppressWarnings("SameParameterValue")
    void addDatePicker(@NonNull final SharedPreferences global,
                       @NonNull final Field<String, TextView> field,
                       @StringRes final int titleId,
                       final boolean todayIfNone) {
        if (field.isUsed(global)) {
            //noinspection ConstantConditions
            field.getAccessor().getView().setOnClickListener(v -> mDatePickerLauncher
                    .launch(titleId, field.getId(), mVm.getInstant(field, todayIfNone)));
        }
    }

    /**
     * Setup a date picker for selecting a partial date.
     *
     * @param global      Global preferences
     * @param field       to setup
     * @param titleId     title for the picker window
     * @param todayIfNone if true, and if the field was empty, we'll default to today's date.
     */
    @SuppressWarnings("SameParameterValue")
    void addPartialDatePicker(@NonNull final SharedPreferences global,
                              @NonNull final Field<String, TextView> field,
                              @StringRes final int titleId,
                              final boolean todayIfNone) {
        if (field.isUsed(global)) {
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
