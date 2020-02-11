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

import androidx.annotation.CallSuper;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModel;
import androidx.lifecycle.ViewModelProvider;

import java.util.Collection;
import java.util.HashSet;

import com.hardbacknutter.nevertoomanybooks.database.DBDefinitions;
import com.hardbacknutter.nevertoomanybooks.datamanager.DataEditor;
import com.hardbacknutter.nevertoomanybooks.datamanager.DataManager;
import com.hardbacknutter.nevertoomanybooks.datamanager.Fields;
import com.hardbacknutter.nevertoomanybooks.datamanager.Fields.Field;
import com.hardbacknutter.nevertoomanybooks.dialogs.PartialDatePickerDialogFragment;
import com.hardbacknutter.nevertoomanybooks.dialogs.checklist.CheckListDialogFragment;
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
            String value = Csv.join(", ", book.getParcelableArrayList(
                    UniqueId.BKEY_BOOKSHELF_ARRAY), Bookshelf::getName);
            Field<String> field = getFields().getField(R.id.bookshelves);
            field.getAccessor().setValue(value);
            field.onChanged();

        } else if (destinationFieldId == R.id.edition) {
            book.putEditions(list);
            Long value = book.getLong(DBDefinitions.KEY_EDITION_BITMASK);
            Field<Long> field = getFields().getField(R.id.edition);
            field.getAccessor().setValue(value);
            field.onChanged();
        }
    };

    private final PartialDatePickerDialogFragment.PartialDatePickerResultsListener
            mPartialDatePickerResultsListener =
            (destinationFieldId, year, month, day) -> {
                String value = DateUtils.buildPartialDate(year, month, day);
                Field<String> field = getFields().getField(destinationFieldId);
                field.getAccessor().setValue(value);
                field.onChanged();
            };

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
        final View tabBarLayout = getActivity().findViewById(R.id.tab_panel);
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
        final UnfinishedEdits model =
                new ViewModelProvider(getActivity()).get(UnfinishedEdits.class);
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
        // new book ?
        if (book.isNew()) {
            onAddFromNewData(book, getArguments());
        }

        super.onLoadFields(book);
    }

    /**
     * Add values from the Bundle to the Book but don't overwrite existing values.
     * <p>
     * Override for handling specific field defaults, e.g. Bookshelf.
     *
     * @param book to add to
     * @param args a Bundle to load values from
     */
    void onAddFromNewData(@NonNull final Book book,
                          @Nullable final Bundle args) {
        if (args != null) {
            final Bundle rawData = args.getBundle(UniqueId.BKEY_BOOK_DATA);
            if (rawData != null) {
                for (String key : rawData.keySet()) {
                    // add, but do not overwrite
                    if (!book.contains(key)) {
                        book.put(key, rawData.get(key));
                    }
                }
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
        getFields().getAll(book);
    }

    /**
     * ViewModels must be public.
     */
    @SuppressWarnings("WeakerAccess")
    public static class UnfinishedEdits
            extends ViewModel {

        /** key: fragmentTag. */
        final Collection<String> fragments = new HashSet<>();
    }
}
