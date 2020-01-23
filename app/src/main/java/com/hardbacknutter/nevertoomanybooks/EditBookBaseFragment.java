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

import android.os.Bundle;
import android.view.View;
import android.widget.AutoCompleteTextView;

import androidx.annotation.CallSuper;
import androidx.annotation.IdRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModel;
import androidx.lifecycle.ViewModelProvider;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import com.hardbacknutter.nevertoomanybooks.database.DBDefinitions;
import com.hardbacknutter.nevertoomanybooks.datamanager.DataEditor;
import com.hardbacknutter.nevertoomanybooks.datamanager.DataManager;
import com.hardbacknutter.nevertoomanybooks.datamanager.Fields;
import com.hardbacknutter.nevertoomanybooks.datamanager.Fields.Field;
import com.hardbacknutter.nevertoomanybooks.dialogs.PartialDatePickerDialogFragment;
import com.hardbacknutter.nevertoomanybooks.dialogs.checklist.CheckListDialogFragment;
import com.hardbacknutter.nevertoomanybooks.dialogs.picker.FieldPicker;
import com.hardbacknutter.nevertoomanybooks.dialogs.picker.ValuePicker;
import com.hardbacknutter.nevertoomanybooks.entities.Book;
import com.hardbacknutter.nevertoomanybooks.entities.Bookshelf;
import com.hardbacknutter.nevertoomanybooks.utils.Csv;
import com.hardbacknutter.nevertoomanybooks.utils.DateUtils;
import com.hardbacknutter.nevertoomanybooks.widgets.DiacriticArrayAdapter;

/**
 * Base class for all fragments that appear in {@link EditBookFragment}.
 * <p>
 * Full list:
 * {@link EditBookFieldsFragment}
 * {@link EditBookPublicationFragment}
 * {@link EditBookNotesFragment}
 * {@link EditBookTocFragment}
 */
public abstract class EditBookBaseFragment
        extends BookBaseFragment
        implements DataEditor<Book> {

    /** The fields collection. */
    private Fields mFields;

    private final CheckListDialogFragment.CheckListResultsListener
            mCheckListResultsListener = (destinationFieldId, list) -> {
        Book book = mBookModel.getBook();
        if (destinationFieldId == R.id.bookshelves) {
            // store
            book.putBookshelves(mBookModel.getDb(), list);
            // and refresh on screen
            getFields().getField(R.id.bookshelves)
                       .setValue(Csv.join(", ", book.getParcelableArrayList(
                               UniqueId.BKEY_BOOKSHELF_ARRAY), Bookshelf::getName));

        } else if (destinationFieldId == R.id.edition) {
            book.putEditions(list);
            getFields().getField(R.id.edition)
                       .setValue(book.getLong(DBDefinitions.KEY_EDITION_BITMASK));
        }
    };

    private final PartialDatePickerDialogFragment.PartialDatePickerResultsListener
            mPartialDatePickerResultsListener =
            (destinationFieldId, year, month, day) ->
                    getFields().getField(destinationFieldId)
                               .setValue(DateUtils.buildPartialDate(year, month, day));

    @Override
    public void onAttachFragment(@NonNull final Fragment childFragment) {

        if (PartialDatePickerDialogFragment.TAG.equals(childFragment.getTag())) {
            ((PartialDatePickerDialogFragment) childFragment)
                    .setListener(mPartialDatePickerResultsListener);

        } else if (CheckListDialogFragment.TAG.equals(childFragment.getTag())) {
            ((CheckListDialogFragment) childFragment)
                    .setListener(mCheckListResultsListener);
        }
    }

    @Override
    public void onActivityCreated(@Nullable final Bundle savedInstanceState) {
        // We hide the tab bar when editing Authors/Series on pop-up screens.
        // Make sure to set it visible here.
        //noinspection ConstantConditions
        View tabBarLayout = getActivity().findViewById(R.id.tab_panel);
        if (tabBarLayout != null) {
            tabBarLayout.setVisibility(View.VISIBLE);
        }

        super.onActivityCreated(savedInstanceState);
    }

    @NonNull
    @Override
    Fields getFields() {
        return mFields;
    }

    @Override
    void initFields() {
        mFields = new Fields();
        super.initFields();
    }

    /**
     * Trigger the Fragment to save its Fields to the Book.
     * <p>
     * This is always done, even when the user 'cancel's the edit.
     * The latter will result in a "are you sure" where they can 'cancel the cancel'
     * and continue with all data present.
     * <p>
     * <br>{@inheritDoc}
     */
    @Override
    @CallSuper
    public void onPause() {
        onSaveFields(mBookModel.getBook());
        //noinspection ConstantConditions
        UnfinishedEdits model = new ViewModelProvider(getActivity()).get(UnfinishedEdits.class);
        if (hasUnfinishedEdits()) {
            // Flag up this fragment as having unfinished edits.
            model.fragments.add(getTag());
        } else {
            model.fragments.remove(getTag());
        }
        super.onPause();
    }

    @Override
    protected void onLoadFields(@NonNull final Book book) {
        super.onLoadFields(book);

        // new book ?
        if (book.isNew()) {
            onLoadFieldsFromNewData(book, getArguments());
        }
    }

    /**
     * Add values from the Bundle to the Book but don't overwrite existing values.
     * <p>
     * Override for handling specific field defaults, e.g. Bookshelf.
     *
     * @param args a Bundle to load values from
     */
    void onLoadFieldsFromNewData(@NonNull final Book book,
                                 @Nullable final Bundle args) {
        // Check if we have any data, for example from a Search
        if (args != null) {
            Bundle rawData = args.getBundle(UniqueId.BKEY_BOOK_DATA);
            if (rawData != null) {
                // if we do, add if not there yet
                getFields().setAllFrom(rawData, false);
            }
        }
    }

    /**
     * Default implementation of code to save existing data to the Book object.
     * We simply copy all {@link Field} into the given {@link DataManager} e.g. the {@link Book}
     * <p>
     * Called from {@link #onPause()}.
     * Override as needed.
     */
    @CallSuper
    public void onSaveFields(@NonNull final Book book) {
        getFields().putAllInto(book);
    }

    /**
     * The 'drop-down' menu button next to an AutoCompleteTextView field.
     * Allows us to show a {@link FieldPicker#FieldPicker} with a list of strings
     * to choose from.
     * <p>
     * Note that a {@link ValuePicker} uses a plain AlertDialog.
     *
     * @param field         {@link Field} to edit
     * @param fieldView     view to connect
     * @param dialogTitleId title of the dialog box.
     * @param fieldButtonId field/button to bind the PickListener to (can be same as fieldId)
     * @param list          list of strings to choose from.
     */
    void initValuePicker(@NonNull final Field<String> field,
                         @NonNull final AutoCompleteTextView fieldView,
                         @StringRes final int dialogTitleId,
                         @IdRes final int fieldButtonId,
                         @NonNull final List<String> list) {
        // only bother when it's in use
        if (!field.isUsed()) {
            return;
        }

        //noinspection ConstantConditions
        View fieldButton = getView().findViewById(fieldButtonId);

        if (list.isEmpty()) {
            fieldButton.setEnabled(false);
            return;
        }
        // We got a list, set it up.

        // Get the list to use in the AutoCompleteTextView
        //noinspection ConstantConditions
        DiacriticArrayAdapter<String> adapter = new DiacriticArrayAdapter<>(
                getContext(), android.R.layout.simple_dropdown_item_1line, list);

        fieldView.setAdapter(adapter);

        // Get the drop-down button for the list and setup dialog
        fieldButton.setOnClickListener(v -> {
            FieldPicker<String> picker = new FieldPicker<>(getContext(),
                                                           getString(dialogTitleId),
                                                           field, list);
            picker.show();
        });
    }

    /**
     * Setup a date picker with the passed field.
     *
     * @param field         {@link Field} to edit
     * @param fieldView     view to connect
     * @param dialogTitleId title of the dialog box.
     * @param todayIfNone   if true, and if the field was empty, pre-populate with today's date
     */
    void initPartialDatePicker(@NonNull final Field<String> field,
                               @NonNull final View fieldView,
                               @StringRes final int dialogTitleId,
                               final boolean todayIfNone) {
        // only bother when it's in use
        if (field.isUsed()) {
            fieldView.setOnClickListener(v -> PartialDatePickerDialogFragment
                    .newInstance(fieldView.getId(), dialogTitleId, field.getValue(), todayIfNone)
                    .show(getChildFragmentManager(), PartialDatePickerDialogFragment.TAG));
        }
    }

    /**
     * Setup a checklist picker with the passed field.
     *
     * @param field         {@link Field} to edit
     * @param fieldView     view to connect
     * @param dialogTitleId title of the dialog box.
     * @param listGetter    {@link CheckListDialogFragment.ListGetter}
     *                      interface to get the *current* list
     */
    void initCheckListEditor(@NonNull final Field<?> field,
                             @NonNull final View fieldView,
                             @StringRes final int dialogTitleId,
                             @NonNull final CheckListDialogFragment.ListGetter listGetter) {
        // only bother when it's in use
        if (field.isUsed()) {
            fieldView.setOnClickListener(v -> CheckListDialogFragment
                    .newInstance(fieldView.getId(), dialogTitleId, listGetter.getList())
                    .show(getChildFragmentManager(), CheckListDialogFragment.TAG));
        }
    }

    /**
     * ViewModels must be public.
     */
    @SuppressWarnings("WeakerAccess")
    public static class UnfinishedEdits
            extends ViewModel {

        /** key: fragmentTag. */
        Set<String> fragments = new HashSet<>();
    }
}
