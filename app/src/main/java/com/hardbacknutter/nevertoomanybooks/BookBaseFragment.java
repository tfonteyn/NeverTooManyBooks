/*
 * @Copyright 2020 HardBackNutter
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
package com.hardbacknutter.nevertoomanybooks;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.widget.ProgressBar;

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

import com.hardbacknutter.nevertoomanybooks.covers.CoverBrowserDialogFragment;
import com.hardbacknutter.nevertoomanybooks.covers.CoverHandler;
import com.hardbacknutter.nevertoomanybooks.database.DBDefinitions;
import com.hardbacknutter.nevertoomanybooks.debug.ErrorMsg;
import com.hardbacknutter.nevertoomanybooks.debug.Logger;
import com.hardbacknutter.nevertoomanybooks.entities.Author;
import com.hardbacknutter.nevertoomanybooks.entities.Book;
import com.hardbacknutter.nevertoomanybooks.entities.EntityStage;
import com.hardbacknutter.nevertoomanybooks.entities.Series;
import com.hardbacknutter.nevertoomanybooks.fields.Field;
import com.hardbacknutter.nevertoomanybooks.fields.Fields;
import com.hardbacknutter.nevertoomanybooks.goodreads.GrStatus;
import com.hardbacknutter.nevertoomanybooks.goodreads.tasks.GrAuthTask;
import com.hardbacknutter.nevertoomanybooks.searches.amazon.AmazonSearchEngine;
import com.hardbacknutter.nevertoomanybooks.tasks.messages.FinishedMessage;
import com.hardbacknutter.nevertoomanybooks.tasks.messages.ProgressMessage;
import com.hardbacknutter.nevertoomanybooks.utils.PermissionsHelper;
import com.hardbacknutter.nevertoomanybooks.utils.ViewFocusOrder;
import com.hardbacknutter.nevertoomanybooks.viewmodels.BookViewModel;
import com.hardbacknutter.nevertoomanybooks.viewmodels.LiveDataEvent;

/**
 * Base class for {@link BookDetailsFragment} and {@link EditBookBaseFragment}.
 * <p>
 * This class supports the loading of a book. See {@link #populateViews}.
 */
public abstract class BookBaseFragment
        extends Fragment
        implements PermissionsHelper.RequestHandler {

    /** FragmentResultListener request key. */
    public static final String RK_COVER_BROWSER = CoverBrowserDialogFragment.TAG + ":rk:";

    /** Log tag. */
    private static final String TAG = "BookBaseFragment";

    /** Handles cover replacement, rotation, etc. */
    final CoverHandler[] mCoverHandler = new CoverHandler[2];

    /** Forwarding listener; send the selected image to the correct handler. */
    private final CoverBrowserDialogFragment.OnResultListener mOnCoverBrowserListener =
            (cIdx, fileSpec) -> mCoverHandler[cIdx].onFileSelected(cIdx, fileSpec);

    /** Goodreads authorization task. */
    GrAuthTask mGrAuthTask;

    /** simple indeterminate progress spinner to show while doing lengthy work. */
    ProgressBar mProgressBar;

    /** The book. Must be in the Activity scope. */
    BookViewModel mBookViewModel;

    /** Listener for all field changes. Must keep strong reference. */
    private final Fields.AfterChangeListener mAfterChangeListener =
            new Fields.AfterChangeListener() {
                @Override
                public void afterFieldChange(@IdRes final int fieldId) {
                    mBookViewModel.getBook().setStage(EntityStage.Stage.Dirty);
                }
            };

    @NonNull
    abstract Fields getFields();

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

    /**
     * Init all Fields, and add them the fields collection.
     * <p>
     * Note that Field views are <strong>NOT AVAILABLE</strong>.
     * <p>
     * Called from {@link #onViewCreated}.
     * The fields will be populated in {@link #onPopulateViews}
     *
     * @param fields collection to add to
     */
    abstract void onInitFields(@NonNull Fields fields);

    @Override
    public void onRequestPermissionsResult(final int requestCode,
                                           @NonNull final String[] permissions,
                                           @NonNull final int[] grantResults) {
        // Camera permissions
        onRequestPermissionsResultCallback(requestCode, permissions, grantResults);
    }

    @Override
    public void onCreate(@Nullable final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setHasOptionsMenu(true);

        getChildFragmentManager()
                .setFragmentResultListener(RK_COVER_BROWSER, this, mOnCoverBrowserListener);

        //noinspection ConstantConditions
        mBookViewModel = new ViewModelProvider(getActivity()).get(BookViewModel.class);
    }

    @Override
    @CallSuper
    public void onViewCreated(@NonNull final View view,
                              @Nullable final Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        final Fields fields = getFields();
        if (fields.isEmpty()) {
            onInitFields(fields);
        }

        //noinspection ConstantConditions
        mProgressBar = getActivity().findViewById(R.id.progressBar);

        mGrAuthTask = new ViewModelProvider(this).get(GrAuthTask.class);
        mGrAuthTask.onProgressUpdate().observe(getViewLifecycleOwner(), this::onProgress);
        mGrAuthTask.onCancelled().observe(getViewLifecycleOwner(), this::onCancelled);
        mGrAuthTask.onFailure().observe(getViewLifecycleOwner(), this::onGrFailure);
        mGrAuthTask.onFinished().observe(getViewLifecycleOwner(), this::onGrFinished);
    }

    private void onProgress(@NonNull final ProgressMessage message) {
        if (message.text != null) {
            //noinspection ConstantConditions
            Snackbar.make(getView(), message.text, Snackbar.LENGTH_LONG).show();
        }
    }

    private void onCancelled(@NonNull final LiveDataEvent message) {
        if (message.isNewEvent()) {
            //noinspection ConstantConditions
            Snackbar.make(getView(), R.string.warning_task_cancelled, Snackbar.LENGTH_LONG).show();
        }
    }

    private void onGrFailure(@NonNull final FinishedMessage<Exception> message) {
        if (message.isNewEvent()) {
            //noinspection ConstantConditions
            Snackbar.make(getView(), GrStatus.getMessage(getContext(), message.result),
                          Snackbar.LENGTH_LONG).show();
        }
    }

    private void onGrFinished(@NonNull final FinishedMessage<GrStatus> message) {
        if (message.isNewEvent()) {
            Objects.requireNonNull(message.result, ErrorMsg.NULL_TASK_RESULTS);
            if (message.result.getStatus() == GrStatus.FAILED_CREDENTIALS) {
                //noinspection ConstantConditions
                mGrAuthTask.prompt(getContext());
            } else {
                //noinspection ConstantConditions
                Snackbar.make(getView(), message.result.getMessage(getContext()),
                              Snackbar.LENGTH_LONG).show();
            }
        }
    }

    @Override
    @CallSuper
    public void onResume() {
        super.onResume();
        // hook up the Views, and calls {@link #onPopulateViews}
        populateViews();
    }

    /**
     * Load all Views from the book.
     * <p>
     * Loads the data while preserving the isDirty() status.
     * Normally called from the base {@link #onResume},
     * but can explicitly be called after {@link BookViewModel#reload}.
     * <p>
     * This is final; Inheritors should implement {@link #onPopulateViews}.
     */
    final void populateViews() {
        final Fields fields = getFields();

        final Book book = mBookViewModel.getBook();
        //noinspection ConstantConditions
        fields.setParentView(getView());

        fields.setAfterChangeListener(null);
        mBookViewModel.getBook().lockStage();
        // make it so!
        onPopulateViews(fields, book);
        mBookViewModel.getBook().unlockStage();
        fields.setAfterChangeListener(mAfterChangeListener);

        // All views should now have proper visibility set, so fix their focus order.
        ViewFocusOrder.fix(getView());

        // Set the activity title
        //noinspection ConstantConditions
        final ActionBar actionBar = ((AppCompatActivity) getActivity()).getSupportActionBar();
        if (book.isNew()) {
            // EDIT NEW book
            //noinspection ConstantConditions
            actionBar.setTitle(R.string.lbl_add_book);
            actionBar.setSubtitle(null);
        } else {
            // VIEW or EDIT existing book
            String title = book.getString(DBDefinitions.KEY_TITLE);
            if (BuildConfig.DEBUG /* always */) {
                title = "[" + book.getId() + "] " + title;
            }
            //noinspection ConstantConditions
            actionBar.setTitle(title);
            //noinspection ConstantConditions
            actionBar.setSubtitle(Author.getCondensedNames(
                    getContext(), book.getParcelableArrayList(Book.BKEY_AUTHOR_LIST)));
        }
    }

    /**
     * This is where you should populate all the fields with the values coming from the book.
     * The base class (this one) manages all the actual fields, but 'special' fields can/should
     * be handled in overrides, calling super as the first step.
     * <p>
     * Do not call this method directly, instead call {@link #populateViews}.
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
        final Book book = mBookViewModel.getBook();
        MenuHandler.prepareOptionalMenus(menu, book);

        super.onPrepareOptionsMenu(menu);
    }

    @Override
    @CallSuper
    public boolean onOptionsItemSelected(@NonNull final MenuItem item) {

        final Context context = getContext();

        final Book book = mBookViewModel.getBook();

        switch (item.getItemId()) {
            case R.id.MENU_UPDATE_FROM_INTERNET: {
                final ArrayList<Long> bookIdList = new ArrayList<>();
                bookIdList.add(book.getId());
                final Intent intent = new Intent(context, BookSearchActivity.class)
                        .putExtra(BaseActivity.BKEY_FRAGMENT_TAG, UpdateFieldsFragment.TAG)
                        .putExtra(Book.BKEY_BOOK_ID_LIST, bookIdList)
                        // pass the title for displaying to the user
                        .putExtra(DBDefinitions.KEY_TITLE, book.getString(DBDefinitions.KEY_TITLE))
                        // pass the author for displaying to the user
                        .putExtra(DBDefinitions.KEY_AUTHOR_FORMATTED,
                                  book.getString(DBDefinitions.KEY_AUTHOR_FORMATTED));
                startActivityForResult(intent, RequestCode.UPDATE_FIELDS_FROM_INTERNET);
                return true;
            }
            case R.id.MENU_AMAZON_BOOKS_BY_AUTHOR: {
                final Author primAuthor = book.getPrimaryAuthor();
                if (primAuthor != null) {
                    //noinspection ConstantConditions
                    final String url = AmazonSearchEngine.createUrl(
                            context, primAuthor.getFormattedName(true), null);
                    context.startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url)));
                }
                return true;
            }
            case R.id.MENU_AMAZON_BOOKS_IN_SERIES: {
                final Series primSeries = book.getPrimarySeries();
                if (primSeries != null) {
                    //noinspection ConstantConditions
                    final String url = AmazonSearchEngine.createUrl(
                            context, null, primSeries.getTitle());
                    context.startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url)));
                }
                return true;
            }
            case R.id.MENU_AMAZON_BOOKS_BY_AUTHOR_IN_SERIES: {
                final Author primAuthor = book.getPrimaryAuthor();
                final Series primSeries = book.getPrimarySeries();
                if (primAuthor != null && primSeries != null) {
                    //noinspection ConstantConditions
                    final String url = AmazonSearchEngine.createUrl(
                            context, primAuthor.getFormattedName(true), primSeries.getTitle());
                    context.startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url)));
                }
                return true;
            }

            default: {
                //noinspection ConstantConditions
                if (MenuHandler.handleOpenOnWebsiteMenus(context, item.getItemId(), book)) {
                    return true;
                }
                return super.onOptionsItemSelected(item);
            }
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
            case RequestCode.UPDATE_FIELDS_FROM_INTERNET:
                if (resultCode == Activity.RESULT_OK) {
                    Objects.requireNonNull(data, ErrorMsg.NULL_INTENT_DATA);

                    final long newId = data.getLongExtra(DBDefinitions.KEY_PK_ID, 0);
                    if (newId > 0) {
                        // replace current book with the updated one,
                        // ENHANCE: merge if in edit mode.
                        mBookViewModel.loadBook(newId);
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
