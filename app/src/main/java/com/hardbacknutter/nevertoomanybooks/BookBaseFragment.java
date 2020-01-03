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
import android.widget.ProgressBar;

import androidx.annotation.CallSuper;
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
 */
public abstract class BookBaseFragment
        extends Fragment {

    /** Log tag. */
    private static final String TAG = "BookBaseFragment";

    /** simple indeterminate progress spinner to show while doing lengthy work. */
    ProgressBar mProgressBar;

    /** The book. */
    BookBaseFragmentModel mBookModel;

    @Override
    @CallSuper
    public void onActivityCreated(@Nullable final Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);

        //noinspection ConstantConditions
        mProgressBar = getActivity().findViewById(R.id.progressBar);

        // Must be in the Activity scope.
        mBookModel = new ViewModelProvider(getActivity()).get(BookBaseFragmentModel.class);
        mBookModel.init(getArguments());
        mBookModel.getUserMessage().observe(getViewLifecycleOwner(), this::showUserMessage);
        mBookModel.getNeedsGoodreads().observe(getViewLifecycleOwner(), this::showNeedsGoodreads);

        initFields();
    }

    /**
     * Add any {@link Field} we need to {@link Fields}.
     * <p>
     * Called from {@link #onActivityCreated(Bundle)}.
     */
    @CallSuper
    void initFields() {
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

    /**
     * Get the fields collection.
     *
     * @return the fields
     */
    @NonNull
    abstract Fields getFields();

    /**
     * Load all Fields from the actual data store/manager.
     * <p>
     * Loads the data while preserving the isDirty() status.
     * Normally called from the base {@link #onResume},
     * but can explicitly be called after {@link Book#reload}.
     * <p>
     * This is 'final' because we want inheritors to implement {@link #onLoadFields}.
     */
    final void loadFields() {
        Book book = mBookModel.getBook();

        // disabling the AfterFieldChangeListener and preserve the 'dirty' status.
        getFields().setAfterFieldChangeListener(null);
        final boolean wasDirty = mBookModel.isDirty();
        // make it so!
        onLoadFields(book);
        // restore the dirt-status and the listener
        mBookModel.setDirty(wasDirty);
        getFields().setAfterFieldChangeListener((field, newValue) -> mBookModel.setDirty(true));

        // this is a good place to do this, as we use data from the book for the title.
        setActivityTitle(book);
    }

    /**
     * This is where you should populate all the fields with the values coming from the book.
     * The base class (this one) manages all the actual fields, but 'special' fields can/should
     * be handled in overrides, calling super as the first step.
     */
    @CallSuper
    void onLoadFields(@NonNull final Book book) {
        getFields().setAllFrom(book);
    }

    @Override
    @CallSuper
    public boolean onOptionsItemSelected(@NonNull final MenuItem item) {
        /*
         * We handle R.id.MENU_UPDATE_FROM_INTERNET here,
         * as it's shown by several children of this class.
         */
        //noinspection SwitchStatementWithTooFewBranches
        switch (item.getItemId()) {
            case R.id.MENU_UPDATE_FROM_INTERNET:
                Book book = mBookModel.getBook();
                ArrayList<Long> bookIds = new ArrayList<>();
                bookIds.add(book.getId());
                Intent intent = new Intent(getContext(), BookSearchActivity.class)
                        .putExtra(UniqueId.BKEY_FRAGMENT_TAG, UpdateFieldsFragment.TAG)
                        .putExtra(UniqueId.BKEY_ID_LIST, bookIds)
                        // pass the title for displaying to the user
                        .putExtra(DBDefinitions.KEY_TITLE,
                                  book.getString(DBDefinitions.KEY_TITLE))
                        // pass the author for displaying to the user
                        .putExtra(DBDefinitions.KEY_AUTHOR_FORMATTED,
                                  book.getString(DBDefinitions.KEY_AUTHOR_FORMATTED));
                startActivityForResult(intent, UniqueId.REQ_UPDATE_FIELDS_FROM_INTERNET);
                return true;

            default:
                return super.onOptionsItemSelected(item);
        }
    }

    /**
     * Set the activity title depending on View or Edit mode.
     */
    private void setActivityTitle(@NonNull final Book book) {

        //noinspection ConstantConditions
        ActionBar actionBar = ((AppCompatActivity) getActivity()).getSupportActionBar();
        if (actionBar != null) {
            if (book.isNew()) {
                // EDIT NEW book
                actionBar.setTitle(R.string.title_add_book);
                actionBar.setSubtitle(null);
            } else {
                // VIEW or EDIT existing book
                actionBar.setTitle(book.getString(DBDefinitions.KEY_TITLE));
                //noinspection ConstantConditions
                actionBar.setSubtitle(book.getAuthorTextShort(getContext()));
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

                    long newId = data.getLongExtra(DBDefinitions.KEY_PK_ID, 0);
                    if (newId != 0) {
                        // replace current book with the updated one,
                        // ENHANCE: merge if in edit mode.
                        mBookModel.setBook(newId);
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
}
