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

import android.content.Context;
import android.os.Bundle;
import android.view.View;
import android.widget.AutoCompleteTextView;

import androidx.annotation.CallSuper;
import androidx.annotation.IdRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;

import java.util.List;
import java.util.Objects;

import com.hardbacknutter.nevertoomanybooks.datamanager.DataEditor;
import com.hardbacknutter.nevertoomanybooks.datamanager.DataManager;
import com.hardbacknutter.nevertoomanybooks.datamanager.Field;
import com.hardbacknutter.nevertoomanybooks.datamanager.Fields;
import com.hardbacknutter.nevertoomanybooks.datamanager.fieldformatters.FieldFormatter;
import com.hardbacknutter.nevertoomanybooks.debug.ErrorMsg;
import com.hardbacknutter.nevertoomanybooks.dialogs.entities.CheckListDialogFragment;
import com.hardbacknutter.nevertoomanybooks.dialogs.picker.PartialDatePickerDialogFragment;
import com.hardbacknutter.nevertoomanybooks.entities.Book;
import com.hardbacknutter.nevertoomanybooks.entities.Entity;
import com.hardbacknutter.nevertoomanybooks.goodreads.tasks.RequestAuthTask;
import com.hardbacknutter.nevertoomanybooks.viewmodels.EditBookFragmentViewModel;

/**
 * Base class for all fragments that appear in {@link EditBookFragment}.
 */
public abstract class EditBookBaseFragment
        extends BookBaseFragment
        implements DataEditor<Book> {

    EditBookFragmentViewModel mFragmentVM;

    private final CheckListDialogFragment.CheckListResultsListener
            mCheckListResultsListener = (fieldId, value) -> {
        Field<List<Entity>> field = getFields().getField(fieldId);
        mBookViewModel.getBook().putParcelableArrayList(field.getKey(), value);
        field.getAccessor().setValue(value);
        field.onChanged();
    };

    private final PartialDatePickerDialogFragment.PartialDatePickerResultsListener
            mPartialDatePickerResultsListener = (fieldId, value) -> {
        Field<String> field = mFragmentVM.getFields().getField(fieldId);
        field.getAccessor().setValue(value);
        field.onChanged();
    };

    @Override
    Fields getFields() {
        return mFragmentVM.getFields();
    }

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
        super.onActivityCreated(savedInstanceState);

        String tag = getTag();
        Objects.requireNonNull(tag, ErrorMsg.NULL_FRAGMENT_TAG);

        //noinspection ConstantConditions
        mFragmentVM = new ViewModelProvider(getActivity())
                .get(tag, EditBookFragmentViewModel.class);

        mFragmentVM.init(getArguments());
        mFragmentVM.getUserMessage().observe(getViewLifecycleOwner(), this::showUserMessage);
        mFragmentVM.getNeedsGoodreads().observe(getViewLifecycleOwner(), needs -> {
            if (needs != null && needs) {
                final Context context = getContext();
                //noinspection ConstantConditions
                RequestAuthTask.prompt(context, mFragmentVM.getGoodreadsTaskListener(context));
            }
        });
        onInitFields();

        // We hide the tab bar when editing Authors/Series on pop-up screens.
        // Make sure to set it visible here.
        final View tabBarLayout = getActivity().findViewById(R.id.tab_panel);
        if (tabBarLayout != null) {
            tabBarLayout.setVisibility(View.VISIBLE);
        }
    }

    /**
     * Init all Fields, and add them the {@link #getFields()} collection.
     * <p>
     * Note that Views are <strong>NOT AVAILABLE</strong>.
     *
     * Book data is available from {@link #mBookViewModel} but {@link #onResume()} is
     * were the fields/Views are normally loaded with that data.
     */
    @CallSuper
    void onInitFields() {
    }

    @Override
    public void onResume() {

        // Not sure this is really needed; but it does no harm.
        // In theory, the editing fragment can trigger an internet search,
        // which after it comes back, brings along new data to be transferred to the book.
        // BUT: that new data would not be in the fragment arguments?
        //TODO: double check having book-data bundle in onResume.
        if (mBookViewModel.getBook().isNew()) {
            //noinspection ConstantConditions
            mBookViewModel.addFieldsFromBundle(getContext(), getArguments());
        }

        // hook up the Views, and populate them with the book data
        super.onResume();
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
        onSaveFields(mBookViewModel.getBook());
        //noinspection ConstantConditions
        mBookViewModel.setUnfinishedEdits(getTag(), hasUnfinishedEdits());

        super.onPause();
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
        mFragmentVM.getFields().getAll(book);
    }

    /**
     * Setup an adapter for the AutoCompleteTextView, using the (optional)
     * formatter.
     *
     * @param fieldId view to connect
     * @param list    with auto complete values
     */
    void addAutocomplete(@IdRes final int fieldId,
                         @NonNull final List<String> list) {
        Field field = mFragmentVM.getFields().getField(fieldId);
        // only bother when it's in use and we have a list
        if (field.isUsed() && !list.isEmpty()) {
            AutoCompleteTextView view = (AutoCompleteTextView) field.getAccessor().getView();
            //noinspection unchecked
            Fields.FormattedDiacriticArrayAdapter adapter =
                    new Fields.FormattedDiacriticArrayAdapter(
                            view.getContext(), list,
                            (FieldFormatter<String>) field.getAccessor().getFormatter());
            view.setAdapter(adapter);
        }
    }

    /**
     * Setup a date picker.
     *
     * @param fieldId       view to connect
     * @param dialogTitleId title of the dialog box.
     * @param todayIfNone   if true, and if the field was empty,
     *                      pre-populate with today's date
     */
    void addDatePicker(@IdRes final int fieldId,
                       @StringRes final int dialogTitleId,
                       final boolean todayIfNone) {
        Field field = mFragmentVM.getFields().getField(fieldId);
        // only bother when it's in use
        if (field.isUsed()) {
            View view = field.getAccessor().getView();
            view.setOnClickListener(v -> PartialDatePickerDialogFragment
                    .newInstance(fieldId, dialogTitleId,
                                 (String) field.getAccessor().getValue(), todayIfNone)
                    .show(getChildFragmentManager(), PartialDatePickerDialogFragment.TAG));
        }
    }

    /**
     * Syntax sugar to add an OnClickListener to a Field.
     *
     * @param fieldId  view to connect
     * @param listener to use
     */
    void setOnClickListener(@IdRes final int fieldId,
                            @Nullable final View.OnClickListener listener) {
        Field field = mFragmentVM.getFields().getField(fieldId);
        // only bother when it's in use
        if (field.isUsed()) {
            field.getAccessor().getView().setOnClickListener(listener);
        }
    }
}
