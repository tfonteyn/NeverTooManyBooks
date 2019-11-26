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

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.MenuItem;
import android.view.View;

import androidx.annotation.CallSuper;
import androidx.annotation.IdRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;

import com.google.android.material.snackbar.Snackbar;

import java.util.ArrayList;
import java.util.Objects;

import com.hardbacknutter.nevertoomanybooks.baseactivity.BaseActivity;
import com.hardbacknutter.nevertoomanybooks.database.DBDefinitions;
import com.hardbacknutter.nevertoomanybooks.datamanager.Fields;
import com.hardbacknutter.nevertoomanybooks.datamanager.Fields.Field;
import com.hardbacknutter.nevertoomanybooks.debug.Logger;
import com.hardbacknutter.nevertoomanybooks.entities.Book;
import com.hardbacknutter.nevertoomanybooks.goodreads.tasks.RequestAuthTask;
import com.hardbacknutter.nevertoomanybooks.viewmodels.BookBaseFragmentModel;

/**
 * Base class for {@link BookDetailsFragment} and {@link EditBookBaseFragment}.
 * <p>
 * This class supports the loading of a book. See {@link #loadFields}.
 * <p>
 * BookBaseFragment -> BookDetailsFragment.
 * BookBaseFragment -> EditBookBaseFragment.
 * BookBaseFragment -> EditBookBaseFragment -> EditBookFieldsFragment
 * BookBaseFragment -> EditBookBaseFragment -> EditBookNotesFragment
 * BookBaseFragment -> EditBookBaseFragment -> EditBookPublicationFragment
 * BookBaseFragment -> EditBookBaseFragment -> EditBookTocFragment
 */
public abstract class BookBaseFragment
        extends Fragment {

    private static final String TAG = "BookBaseFragment";

    /** The book. Must be in the Activity scope. */
    BookBaseFragmentModel mBookModel;

    /**
     * Set the activity title depending on View or Edit mode.
     */
    private void setActivityTitle() {
        Book book = mBookModel.getBook();

        @SuppressWarnings("ConstantConditions")
        ActionBar actionBar = ((AppCompatActivity) getActivity()).getSupportActionBar();
        if (actionBar != null) {
            if (book.getId() > 0) {
                // VIEW or EDIT existing book
                actionBar.setTitle(book.getString(DBDefinitions.KEY_TITLE));
                //noinspection ConstantConditions
                actionBar.setSubtitle(book.getAuthorTextShort(getContext()));
            } else {
                // EDIT NEW book
                actionBar.setTitle(R.string.title_add_book);
                actionBar.setSubtitle(null);
            }
        }
    }

    /**
     * Called if an interaction with Goodreads failed due to authorization issues.
     * Prompts the user to register.
     *
     * @param needs {@code true} if registration is needed
     */
    private void showNeedsGoodreads(@Nullable final Boolean needs) {
        if (needs != null && needs) {
            //noinspection ConstantConditions
            RequestAuthTask.needsRegistration(getContext(), mBookModel.getGoodreadsTaskListener());
        }
    }

    /**
     * Allows the ViewModel to send us a message to display to the user.
     *
     * @param message to display
     */
    private void showUserMessage(@Nullable final String message) {
        View view = getView();
        if (view != null && message != null && !message.isEmpty()) {
            Snackbar.make(view, message, Snackbar.LENGTH_LONG).show();
        }
    }

    /**
     * Get the fields collection.
     *
     * @return the fields
     */
    @NonNull
    abstract Fields getFields();

    /**
     * Add any {@link Field} we need to {@link Fields}.
     * <p>
     * Do not add any View related calls here.
     */
    @CallSuper
    void initFields() {
    }

    /**
     * Load all Fields from the actual data store/manager.
     * <p>
     * Loads the data while preserving the isDirty() status.
     * Normally called from the base onResume, but can also be called after {@link Book#reload}.
     * <p>
     * This is 'final' because we want inheritors to implement {@link #onLoadFieldsFromBook}.
     */
    final void loadFields() {
        // load the book, while disabling the AfterFieldChangeListener
        getFields().setAfterFieldChangeListener(null);
        // preserve the 'dirty' status.
        final boolean wasDirty = mBookModel.isDirty();

        // make it so!
        onLoadFieldsFromBook();
        // get dirty...
        mBookModel.setDirty(wasDirty);
        getFields().setAfterFieldChangeListener((field, newValue) -> mBookModel.setDirty(true));

        // this is a good place to do this, as we use data from the book for the title.
        setActivityTitle();
    }

    /**
     * This is where you should populate all the fields with the values coming from the book.
     * The base class (this one) manages all the actual fields, but 'special' fields can/should
     * be handled in overrides, calling super as the first step.
     */
    @CallSuper
    void onLoadFieldsFromBook() {
        getFields().setAllFrom(mBookModel.getBook());
    }

    @Override
    public void onActivityResult(final int requestCode,
                                 final int resultCode,
                                 @Nullable final Intent data) {
        if (BuildConfig.DEBUG && DEBUG_SWITCHES.ON_ACTIVITY_RESULT) {
            Logger.enterOnActivityResult(TAG, requestCode, resultCode, data);
        }
        //noinspection SwitchStatementWithTooFewBranches
        switch (requestCode) {
            case UniqueId.REQ_UPDATE_FIELDS_FROM_INTERNET:
                if (resultCode == Activity.RESULT_OK) {
                    Objects.requireNonNull(data);
                    //noinspection unchecked
                    ArrayList<Long> bookIds =
                            (ArrayList<Long>) data.getSerializableExtra(UniqueId.BKEY_ID_LIST);

                    if (bookIds != null && bookIds.size() == 1) {
                        // replace current book with the updated one,
                        // ENHANCE: merge if in edit mode.
                        mBookModel.setBook(bookIds.get(0));
                    }
                }
                break;

            default:
                if (BuildConfig.DEBUG && DEBUG_SWITCHES.ON_ACTIVITY_RESULT) {
                    Log.d(TAG, "onActivityResult|NOT HANDLED"
                               + "|requestCode=" + requestCode
                               + "|resultCode=" + resultCode, new Throwable());
                }
                super.onActivityResult(requestCode, resultCode, data);
                break;
        }
    }

    @Override
    public void onCreate(@Nullable final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // Mandatory
        setHasOptionsMenu(true);
    }

    /**
     * Registers the {@link Book} as a ViewModel, and load/create the its data as needed.
     * <p>
     * <br>{@inheritDoc}
     */
    @Override
    @CallSuper
    public void onActivityCreated(@Nullable final Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);

        //noinspection ConstantConditions
        mBookModel = new ViewModelProvider(getActivity()).get(BookBaseFragmentModel.class);
        mBookModel.init(getArguments());
        mBookModel.getUserMessage().observe(getViewLifecycleOwner(), this::showUserMessage);
        mBookModel.getNeedsGoodreads().observe(getViewLifecycleOwner(), this::showNeedsGoodreads);

        initFields();
    }

    /**
     * Trigger the Fragment to load its Fields from the Book.
     * <p>
     * <br>{@inheritDoc}
     */
    @Override
    @CallSuper
    public void onResume() {
        if (BuildConfig.DEBUG && DEBUG_SWITCHES.TRACK) {
            Log.d(TAG, "ENTER|onResume");
        }
        super.onResume();
        if (getActivity() instanceof BaseActivity) {
            BaseActivity activity = (BaseActivity) getActivity();
            if (activity.isGoingToRecreate()) {
                return;
            }
        }

        // set the View member as the Views will be (re)created each time.
        //noinspection ConstantConditions
        getFields().setParentView(getView());
        // and load the content into the views
        loadFields();

        if (BuildConfig.DEBUG && DEBUG_SWITCHES.TRACK) {
            Log.d(TAG, "EXIT|onResume");
        }
    }

    @Override
    public void onPause() {
        super.onPause();
        if (BuildConfig.DEBUG && DEBUG_SWITCHES.TRACK) {
            Log.d(TAG, "EXIT|onPause");
        }
    }

    @Override
    @CallSuper
    public boolean onOptionsItemSelected(@NonNull final MenuItem item) {
        Book book = mBookModel.getBook();

        //noinspection SwitchStatementWithTooFewBranches
        switch (item.getItemId()) {
            case R.id.MENU_UPDATE_FROM_INTERNET:
                ArrayList<Long> bookIds = new ArrayList<>();
                bookIds.add(book.getId());
                Intent intentUpdateFields =
                        new Intent(getContext(), BookSearchActivity.class)
                                .putExtra(UniqueId.BKEY_FRAGMENT_TAG, UpdateFieldsFragment.TAG)
                                .putExtra(UniqueId.BKEY_ID_LIST, bookIds)
                                // pass the title for displaying to the user
                                .putExtra(DBDefinitions.KEY_TITLE,
                                          book.getString(DBDefinitions.KEY_TITLE))
                                // pass the author for displaying to the user
                                .putExtra(DBDefinitions.KEY_AUTHOR_FORMATTED,
                                          book.getString(DBDefinitions.KEY_AUTHOR_FORMATTED));
                startActivityForResult(intentUpdateFields,
                                       UniqueId.REQ_UPDATE_FIELDS_FROM_INTERNET);
                return true;

            default:
                return super.onOptionsItemSelected(item);
        }
    }

    /**
     * Hides unused fields if they have no useful data.
     * Should normally be called at the *end* of {@link #onLoadFieldsFromBook}
     *
     * @param hideIfEmpty set to {@code true} when displaying; {@code false} when editing.
     */
    void showOrHideFields(final boolean hideIfEmpty) {

        // do all fields with their related fields
        //noinspection ConstantConditions
        getFields().resetVisibility(getView(), hideIfEmpty);

//        // Hide the baseline for the read labels if both labels are gone.
//        // If both labels are visible, then make the baseline invisible.
//        showOrHide(R.id.lbl_read_start_end_baseline, View.INVISIBLE,
//                              R.id.lbl_read_start, R.id.lbl_read_end);
//        // Hide the baseline for the value field if the labels are gone.
//        showOrHide(R.id.read_start_end_baseline, View.INVISIBLE,
//                              R.id.lbl_read_start_end_baseline);
//
//        showOrHide(R.id.lbl_publication_dates_baseline, View.INVISIBLE,
//                   R.id.lbl_first_publication, R.id.lbl_date_published);
//        // Hide the baseline for the value field if the labels are gone.
//        showOrHide(R.id.publication_dates_baseline, View.INVISIBLE,
//                   R.id.lbl_publication_dates_baseline);
    }

    /**
     * Syntax sugar.
     * <p>
     * If all 'fields' are View.GONE, set 'sectionLabelId' to View.GONE as well.
     * Otherwise, set 'sectionLabelId' to View.VISIBLE.
     *
     * @param sectionLabelId field to set
     * @param fields         to check
     */
    void setSectionLabelVisibility(@SuppressWarnings("SameParameterValue")
                                   @IdRes final int sectionLabelId,
                                   @NonNull @IdRes final int... fields) {
        showOrHide(sectionLabelId, View.VISIBLE, fields);
    }

    /**
     * If all 'fields' are View.GONE, set 'fieldToSet' to View.GONE as well.
     * Otherwise, set 'fieldToSet' to the desired visibility.
     *
     * @param fieldToSet      field to set
     * @param visibilityToSet to use for the fieldToSet
     * @param fields          to test for having the same visibility
     */
    private void showOrHide(@IdRes final int fieldToSet,
                            @SuppressWarnings("SameParameterValue") final int visibilityToSet,
                            @NonNull @IdRes final int... fields) {
        //noinspection ConstantConditions
        View fieldView = getView().findViewById(fieldToSet);
        if (fieldView != null) {
            boolean allGone = hasSameVisibility(View.GONE, fields);
            fieldView.setVisibility(allGone ? View.GONE : visibilityToSet);
        }
    }

    /**
     * Check if all fields have the same visibility.
     *
     * @param visibility to check
     * @param fields     to check
     *
     * @return {@code true} if all fields have the same visibility
     */
    private boolean hasSameVisibility(@SuppressWarnings("SameParameterValue") final int visibility,
                                      @IdRes @NonNull final int[] fields) {
        View view = getView();
        for (int fieldId : fields) {
            //noinspection ConstantConditions
            View field = view.findViewById(fieldId);
            if (field != null && field.getVisibility() != visibility) {
                return false;
            }
        }
        return true;
    }
}
