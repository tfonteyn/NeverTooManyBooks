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
import java.util.Objects;

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
import com.hardbacknutter.nevertoomanybooks.fields.Fields;
import com.hardbacknutter.nevertoomanybooks.searchengines.amazon.AmazonHandler;
import com.hardbacknutter.nevertoomanybooks.utils.ViewBookOnWebsiteHandler;
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

    /** MUST keep a strong reference. */
    private final DatePickerListener mDatePickerListener = this::onDateSet;

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
    /** Listener for all field changes. MUST keep strong reference. */
    private final Fields.AfterChangeListener mAfterChangeListener = this::onAfterFieldChange;
    @Nullable
    private AmazonHandler mAmazonHandler;
    @Nullable
    private ViewBookOnWebsiteHandler mViewBookOnWebsiteHandler;
    private DateParser mDateParser;

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

        //noinspection ConstantConditions
        mVm = new ViewModelProvider(getActivity()).get(EditBookViewModel.class);
    }

    @Override
    @CallSuper
    public void onViewCreated(@NonNull final View view,
                              @Nullable final Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        final Context context = getContext();
        //noinspection ConstantConditions
        mViewBookOnWebsiteHandler = new ViewBookOnWebsiteHandler(context);
        mAmazonHandler = new AmazonHandler(context);

        mDateParser = new FullDateParser(getContext());

        final FragmentManager fm = getChildFragmentManager();
        mPartialDatePickerLauncher.registerForFragmentResult(fm, this);

        final Fields fields = getFields();
        if (fields.isEmpty()) {
            onInitFields(fields);
        }
    }

    @Override
    public void onCreateOptionsMenu(@NonNull final Menu menu,
                                    @NonNull final MenuInflater inflater) {

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
        super.onResume();

        // Not sure this is really needed; but it does no harm.
        // In theory, the editing fragment can trigger an internet search,
        // which after it comes back, brings along new data to be transferred to the book.
        // BUT: that new data would not be in the fragment arguments?
        //TODO: double check having book-data bundle in onResume.
        if (mVm.getBook().isNew()) {
            mVm.addFieldsFromBundle(getArguments());
        }

        // update all Fields with their current View instances
        final Fields fields = getFields();
        //noinspection ConstantConditions
        fields.setParentView(getView());

        // Load all Views from the book while preserving the stage of the book.
        fields.setAfterChangeListener(null);
        final Book book = mVm.getBook();
        book.lockStage();

        // make it so! Child classes can override this method.
        onPopulateViews(fields, book);

        book.unlockStage();
        // Dev note: DO NOT use a 'this' reference directly
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
     * This is where all fields should be populate with the values coming from the book.
     * The base class (this one) manages all the actual fields, but 'special' fields can/should
     * be handled in overrides, calling super as the first step.
     * <p>
     * The {@link Fields.AfterChangeListener} is disabled and the book is locked during this call.
     *
     * @param fields current field collection
     * @param book   loaded book
     */
    @CallSuper
    void onPopulateViews(@NonNull final Fields fields,
                         @NonNull final Book book) {
        fields.setAll(book);
    }

    /** Listener for all field changes. */
    @CallSuper
    public void onAfterFieldChange(@NonNull final Field<?, ? extends View> field) {
        final Book book = mVm.getBook();

        book.setStage(EntityStage.Stage.Dirty);

        // For new books, we copy a number of fields as appropriate by default.
        if (book.isNew()) {
            if (field.getId() == R.id.price_listed_currency) {
                mVm.findField(R.id.price_paid_currency)
                   .ifPresent(paidField -> {
                       if (!field.isEmpty() && paidField.isEmpty()) {
                           // Get the CURRENT value from the field, and store it in the BOOK
                           // as the destination field is on another fragment.
                           book.put(DBKey.PRICE_PAID_CURRENCY, field.getValue());
                           // If it was on the same fragment, we'd need to store it in the field.
                           // Maybe just do both all the time ?
                           // ((Field<String, View>)paidField).setValue(value);
                       }
                   });
            } else if (field.getId() == R.id.price_listed) {
                mVm.findField(R.id.price_paid)
                   .ifPresent(paidField -> {
                       if (!field.isEmpty() && paidField.isEmpty()) {
                           // see above for comments
                           book.put(DBKey.PRICE_PAID, field.getValue());
                       }
                   });
            }
        }
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
        getFields().getAll(book);
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
     * @param startFieldId to setup for the start-date
     * @param startTitleId title of the picker if the end-date is not in use
     * @param endFieldId   to setup for the end-date
     * @param endTitleId   title of the picker if the start-date is not in use
     */
    void addDateRangePicker(@NonNull final SharedPreferences global,
                            @StringRes final int titleId,

                            @IdRes final int startFieldId,
                            final int startTitleId,

                            @IdRes final int endFieldId,
                            final int endTitleId) {

        final Field<String, TextView> startField = getField(startFieldId);
        final Field<String, TextView> endField = getField(endFieldId);

        if (startField.isUsed(global)) {
            // Always a single date picker for the start-date
            addDatePicker(global, startFieldId, startTitleId);
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
                                       mDatePickerListener
                                      ));

            } else {
                // without using a start-date, single date picker for the end-date
                addDatePicker(global, endFieldId, endTitleId);
            }
        }
    }

    /**
     * Setup a date picker for selecting a single, full date.
     *
     * @param global  Global preferences
     * @param fieldId the field to hookup
     * @param titleId title for the picker window
     */
    void addDatePicker(@NonNull final SharedPreferences global,
                       @IdRes final int fieldId,
                       @StringRes final int titleId) {

        final Field<String, TextView> field = getField(fieldId);
        if (field.isUsed(global)) {
            final SingleDatePicker dp = new SingleDatePicker(getChildFragmentManager(),
                                                             titleId, fieldId);
            dp.setDateParser(mDateParser, true);
            dp.onResume(mDatePickerListener);

            field.requireView().setOnClickListener(v -> dp.launch(field.getValue(),
                                                                  mDatePickerListener));
        }
    }

    /**
     * Setup a date picker for selecting a partial date.
     *
     * @param global  Global preferences
     * @param fieldId the field to hookup
     * @param titleId title for the picker window
     */
    void addPartialDatePicker(@NonNull final SharedPreferences global,
                              @IdRes final int fieldId,
                              @StringRes final int titleId) {
        final Field<String, TextView> field = getField(fieldId);
        if (field.isUsed(global)) {
            field.requireView().setOnClickListener(v -> mPartialDatePickerLauncher
                    .launch(titleId, field.getId(), field.getValue(), false));
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

        final Field<String, TextView> field = getField(fieldId);
        field.setValue(dateStr);
        field.onChanged();

        if (fieldId == R.id.read_end) {
            getField(R.id.cbx_read).setValue(true);
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
