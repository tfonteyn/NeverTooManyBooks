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

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.Menu;
import android.view.MenuInflater;
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

import com.hardbacknutter.nevertoomanybooks.database.DBDefinitions;
import com.hardbacknutter.nevertoomanybooks.datamanager.Fields;
import com.hardbacknutter.nevertoomanybooks.datamanager.Fields.Field;
import com.hardbacknutter.nevertoomanybooks.debug.Logger;
import com.hardbacknutter.nevertoomanybooks.entities.Book;
import com.hardbacknutter.nevertoomanybooks.goodreads.tasks.RequestAuthTask;
import com.hardbacknutter.nevertoomanybooks.searches.amazon.AmazonSearchEngine;
import com.hardbacknutter.nevertoomanybooks.utils.PermissionsHelper;
import com.hardbacknutter.nevertoomanybooks.viewmodels.BookBaseFragmentModel;

/**
 * Base class for {@link BookDetailsFragment} and {@link EditBookBaseFragment}.
 * <p>
 * This class supports the loading of a book. See {@link #loadFields}.
 */
public abstract class BookBaseFragment
        extends Fragment
        implements PermissionsHelper.RequestHandler {

    /** Log tag. */
    private static final String TAG = "BookBaseFragment";

    /** simple indeterminate progress spinner to show while doing lengthy work. */
    ProgressBar mProgressBar;

    /** The book. */
    BookBaseFragmentModel mBookModel;

    @Override
    public void onRequestPermissionsResult(final int requestCode,
                                           @NonNull final String[] permissions,
                                           @NonNull final int[] grantResults) {
        // Camera permissions
        onRequestPermissionsResultCallback(requestCode, permissions, grantResults);
    }

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
        mBookModel.getNeedsGoodreads().observe(getViewLifecycleOwner(), needs -> {
            if (needs != null && needs) {
                Context context = getContext();
                //noinspection ConstantConditions
                RequestAuthTask.prompt(context, mBookModel.getGoodreadsTaskListener(context));
            }
        });

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
        super.onResume();

        // This is done here using isVisible() due to ViewPager2
        // It ensures fragments in a ViewPager2 will refresh their individual option menus.
        // Children NOT running in a ViewPager2 must override this in their onResume()
        // with a simple setHasOptionsMenu(true);
        setHasOptionsMenu(isVisible());

        // set the View member as the Views will be (re)created each time.
        //noinspection ConstantConditions
        getFields().setParentView(getView());
        // and load the content into the views
        loadFields();
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

        // Set the activity title depending on View or Edit mode.
        // This is a good place to do this, as we use data from the book for the title.
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
     * This is where you should populate all the fields with the values coming from the book.
     * The base class (this one) manages all the actual fields, but 'special' fields can/should
     * be handled in overrides, calling super as the first step.
     */
    @CallSuper
    void onLoadFields(@NonNull final Book book) {
        getFields().setAllFrom(book);
    }

    @Override
    public void onCreateOptionsMenu(@NonNull final Menu menu,
                                    @NonNull final MenuInflater inflater) {

        if (menu.findItem(R.id.SUBMENU_VIEW_BOOK_AT_SITE) == null) {
            inflater.inflate(R.menu.sm_open_on_site, menu);
        }
        if (menu.findItem(R.id.SUBMENU_AMAZON_SEARCH) == null) {
            inflater.inflate(R.menu.sm_search_on_amazon, menu);
        }
        super.onCreateOptionsMenu(menu, inflater);
    }

    @Override
    @CallSuper
    public boolean onOptionsItemSelected(@NonNull final MenuItem item) {

        @SuppressWarnings("ConstantConditions")
        @NonNull
        Context context = getContext();

        Book book = mBookModel.getBook();
        switch (item.getItemId()) {
            case R.id.MENU_UPDATE_FROM_INTERNET: {
                ArrayList<Long> bookIds = new ArrayList<>();
                bookIds.add(book.getId());
                Intent intent = new Intent(context, BookSearchActivity.class)
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
            }

            case R.id.MENU_AMAZON_BOOKS_BY_AUTHOR: {
                AmazonSearchEngine.openWebsite(context, book.getPrimaryAuthor(context), null);
                return true;
            }
            case R.id.MENU_AMAZON_BOOKS_IN_SERIES: {
                AmazonSearchEngine.openWebsite(context, null, book.getPrimarySeries());
                return true;
            }
            case R.id.MENU_AMAZON_BOOKS_BY_AUTHOR_IN_SERIES: {
                AmazonSearchEngine.openWebsite(context,
                                               book.getPrimaryAuthor(context),
                                               book.getPrimarySeries());
                return true;
            }

            default:
                if (MenuHandler.handleOpenOnWebsiteMenus(context, item, book)) {
                    return true;
                }
                return super.onOptionsItemSelected(item);
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
