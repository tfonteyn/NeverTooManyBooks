/*
 * @Copyright 2019 HardBackNutter
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
import android.widget.ArrayAdapter;

import androidx.annotation.CallSuper;
import androidx.annotation.IdRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;

import java.util.ArrayList;
import java.util.List;

import com.hardbacknutter.nevertoomanybooks.database.DBDefinitions;
import com.hardbacknutter.nevertoomanybooks.datamanager.DataEditor;
import com.hardbacknutter.nevertoomanybooks.datamanager.DataManager;
import com.hardbacknutter.nevertoomanybooks.datamanager.Fields;
import com.hardbacknutter.nevertoomanybooks.datamanager.Fields.Field;
import com.hardbacknutter.nevertoomanybooks.dialogs.PartialDatePickerDialogFragment;
import com.hardbacknutter.nevertoomanybooks.dialogs.checklist.CheckListDialogFragment;
import com.hardbacknutter.nevertoomanybooks.dialogs.checklist.CheckListItem;
import com.hardbacknutter.nevertoomanybooks.dialogs.picker.FieldPicker;
import com.hardbacknutter.nevertoomanybooks.dialogs.picker.ValuePicker;
import com.hardbacknutter.nevertoomanybooks.entities.Book;
import com.hardbacknutter.nevertoomanybooks.entities.Bookshelf;
import com.hardbacknutter.nevertoomanybooks.utils.Csv;
import com.hardbacknutter.nevertoomanybooks.utils.DateUtils;

/**
 * Base class for all fragments that appear in {@link EditBookFragment}.
 * <p>
 * Full list:
 * {@link EditBookFieldsFragment}
 * {@link EditBookPublicationFragment}
 * {@link EditBookNotesFragment}
 * {@link EditBookTocFragment}
 *
 * @param <T> type of the {@link CheckListItem}
 */
public abstract class EditBookBaseFragment<T>
        extends BookBaseFragment
        implements DataEditor {

    private final CheckListDialogFragment.CheckListResultsListener<T>
            mCheckListResultsListener = (fieldId, list) -> {
        Book book = mBookModel.getBook();

        if (fieldId == R.id.bookshelves) {
            ArrayList<Bookshelf> bsList = (ArrayList<Bookshelf>) list;
            book.putParcelableArrayList(UniqueId.BKEY_BOOKSHELF_ARRAY, bsList);
            getField(R.id.bookshelves).setValue(Csv.join(", ", bsList, Bookshelf::getName));

        } else if (fieldId == R.id.edition) {
            book.putEditions((ArrayList<Integer>) list);
            getField(R.id.edition).setValue(book.getLong(DBDefinitions.KEY_EDITION_BITMASK));
        }
    };

    private final PartialDatePickerDialogFragment.PartialDatePickerResultsListener
            mPartialDatePickerResultsListener =
            (destinationFieldId, year, month, day) ->
                    getField(destinationFieldId)
                            .setValue(DateUtils.buildPartialDate(year, month, day));

    @Override
    public void onAttachFragment(@NonNull final Fragment childFragment) {

        if (PartialDatePickerDialogFragment.TAG.equals(childFragment.getTag())) {
            ((PartialDatePickerDialogFragment) childFragment)
                    .setListener(mPartialDatePickerResultsListener);

        } else if (CheckListDialogFragment.TAG.equals(childFragment.getTag())) {
            //noinspection unchecked
            ((CheckListDialogFragment<T>) childFragment).setListener(mCheckListResultsListener);
        }
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
        saveFields();
        super.onPause();
    }

    @Override
    protected void onLoadFieldsFromBook() {
        super.onLoadFieldsFromBook();

        // new book ?
        if (!mBookModel.isExistingBook()) {
            populateNewBookFieldsFromBundle(getArguments());
        }
    }

    /**
     * Uses the values from the Bundle to populate the Book but don't overwrite existing values.
     * <p>
     * Can/should be overwritten for handling specific field defaults, e.g. Bookshelf.
     *
     * @param bundle to load values from
     */
    void populateNewBookFieldsFromBundle(@Nullable final Bundle bundle) {
        // Check if we have any data, for example from a Search
        if (bundle != null) {
            Bundle rawData = bundle.getBundle(UniqueId.BKEY_BOOK_DATA);
            if (rawData != null) {
                // if we do, add if not there yet
                getFields().setAllFrom(rawData, false);
            }
        }
    }

    /**
     * <br>{@inheritDoc}
     * <br>
     * <p>This is 'final' because we want inheritors to implement {@link #onSaveFieldsToBook}.
     */
    @Override
    public final void saveFields() {
        onSaveFieldsToBook();
    }

    /**
     * Default implementation of code to save existing data to the Book object.
     * We simply copy all {@link Field} into the {@link DataManager} e.g. the {@link Book}
     * <p>
     * Override as needed.
     */
    @CallSuper
    void onSaveFieldsToBook() {
        Fields fields = getFields();
        fields.putAllInto(mBookModel.getBook());
    }

    /**
     * The 'drop-down' menu button next to an AutoCompleteTextView field.
     * Allows us to show a {@link FieldPicker#FieldPicker} with a list of strings
     * to choose from.
     * <p>
     * Note that a {@link ValuePicker} uses a plain AlertDialog.
     *
     * @param field         {@link Field} to edit
     * @param dialogTitleId title of the dialog box.
     * @param fieldButtonId field/button to bind the PickListener to (can be same as fieldId)
     * @param list          list of strings to choose from.
     */
    void initValuePicker(@NonNull final Field<String> field,
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
        @SuppressWarnings("ConstantConditions")
        ArrayAdapter<String> adapter =
                new ArrayAdapter<>(getContext(), android.R.layout.simple_dropdown_item_1line, list);
        getFields().setAdapter(field.getId(), adapter);

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
     * @param dialogTitleId title of the dialog box.
     * @param todayIfNone   if true, and if the field was empty, pre-populate with today's date
     */
    void initPartialDatePicker(@NonNull final Field<String> field,
                               @StringRes final int dialogTitleId,
                               final boolean todayIfNone) {
        // only bother when it's in use
        if (field.isUsed()) {
            field.getView().setOnClickListener(v -> {
                FragmentManager fm = getChildFragmentManager();
                if (fm.findFragmentByTag(PartialDatePickerDialogFragment.TAG) == null) {
                    String value = field.getValue();
                    PartialDatePickerDialogFragment
                            .newInstance(field.getId(), value, dialogTitleId, todayIfNone)
                            .show(fm, PartialDatePickerDialogFragment.TAG);
                }
            });
        }
    }

    /**
     * Setup a checklist picker with the passed field.
     *
     * @param field         {@link Field} to edit
     * @param dialogTitleId title of the dialog box.
     * @param listGetter    {@link CheckListDialogFragment.CheckListEditorListGetter}
     *                      interface to get the *current* list
     */
    void initCheckListEditor(@NonNull final Field<?> field,
                             @StringRes final int dialogTitleId,
                             @NonNull final
                             CheckListDialogFragment.CheckListEditorListGetter<T> listGetter) {
        // only bother when it's in use
        if (field.isUsed()) {
            field.getView().setOnClickListener(v -> {
                FragmentManager fm = getChildFragmentManager();
                if (fm.findFragmentByTag(CheckListDialogFragment.TAG) == null) {
                    CheckListDialogFragment.newInstance(field.getId(), dialogTitleId, listGetter)
                                           .show(fm, CheckListDialogFragment.TAG);
                }
            });
        }
    }
}
