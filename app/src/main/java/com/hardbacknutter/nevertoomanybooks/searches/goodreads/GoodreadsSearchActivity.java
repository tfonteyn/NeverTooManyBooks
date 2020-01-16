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
package com.hardbacknutter.nevertoomanybooks.searches.goodreads;

import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.constraintlayout.widget.Group;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.DividerItemDecoration;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.snackbar.Snackbar;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import com.hardbacknutter.nevertoomanybooks.App;
import com.hardbacknutter.nevertoomanybooks.R;
import com.hardbacknutter.nevertoomanybooks.UniqueId;
import com.hardbacknutter.nevertoomanybooks.baseactivity.BaseActivity;
import com.hardbacknutter.nevertoomanybooks.database.CursorRow;
import com.hardbacknutter.nevertoomanybooks.database.DAO;
import com.hardbacknutter.nevertoomanybooks.database.DBDefinitions;
import com.hardbacknutter.nevertoomanybooks.goodreads.GoodreadsWork;
import com.hardbacknutter.nevertoomanybooks.goodreads.tasks.FetchWorksTask;
import com.hardbacknutter.nevertoomanybooks.tasks.TaskListener;
import com.hardbacknutter.nevertoomanybooks.utils.ImageUtils;
import com.hardbacknutter.nevertoomanybooks.widgets.RecyclerViewAdapterBase;
import com.hardbacknutter.nevertoomanybooks.widgets.RecyclerViewViewHolderBase;

/**
 * ENHANCE: the actual search/display are now implemented. But this activity is still disabled,
 * as {@link #onWorkSelected} needs implementing which relies on access,
 * or... maybe find some workaround?
 * <p>
 * Activity to handle searching Goodreads for books that did not automatically convert.
 * These are typically books without an ISBN.
 * <p>
 * The search criteria is setup to contain the book author, title and ISBN.
 * The user can edit these, search Goodreads, and then review the results.
 * <p>
 * IMPORTANT: always use {@link #open} to start this activity. Unless it's called
 * from a place where we know for certain that we have Goodreads authorization tokens.
 */
public class GoodreadsSearchActivity
        extends BaseActivity {

    /** Log tag. */
    private static final String TAG = "GoodreadsSearchActivity";

    private final List<GoodreadsWork> mWorks = new ArrayList<>();

    private TextView mIsbnView;
    private TextView mAuthorView;
    private TextView mTitleView;
    /** Group to set visibility on author/title/isbn fields. */
    private Group mDetailsGroup;

    private TextView mSearchTextView;
    /** The View for the resulting list of 'works'. */
    private RecyclerView mListView;
    /** The ViewModel. */
    private GrSearchViewModel mModel;
    private WorksAdapter mWorksAdapter;

    /**
     * Convenience method to start this activity, or redirect to the register activity if
     * this application was not registered yet.
     *
     * @param context Current context
     * @param bookId  the book to search for
     */
    public static void open(@NonNull final Context context,
                            final long bookId) {
        if (!GoodreadsManager.hasCredentials(context)) {
            context.startActivity(new Intent(context, GoodreadsRegistrationActivity.class));
        }

        Intent data = new Intent(context, GoodreadsSearchActivity.class)
                .putExtra(DBDefinitions.KEY_PK_ID, bookId);
        context.startActivity(data);
    }

    @Override
    protected int getLayoutId() {
        return R.layout.activity_goodreads_search;
    }

    @Override
    public void onCreate(@Nullable final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setTitle(getString(R.string.progress_msg_searching_site,
                           getString(R.string.site_goodreads)));

        mModel = new ViewModelProvider(this).get(GrSearchViewModel.class);
        mModel.init(Objects.requireNonNull(getIntent().getExtras()), savedInstanceState);

        mModel.getWorks().observe(this, goodreadsWorks -> {
            mWorks.clear();
            if (goodreadsWorks != null && !goodreadsWorks.isEmpty()) {
                mWorks.addAll(goodreadsWorks);
            } else {
                Snackbar.make(mListView, R.string.warning_no_matching_book_found,
                              Snackbar.LENGTH_LONG).show();
            }
            mWorksAdapter.notifyDataSetChanged();
        });
        mModel.getBookNoLongerExists().observe(this, flag -> {
            if (flag) {
                Snackbar.make(mSearchTextView, R.string.warning_book_no_longer_exists,
                              Snackbar.LENGTH_LONG).show();
            }
        });

        mSearchTextView = findViewById(R.id.filter_text);
        mIsbnView = findViewById(R.id.isbn);
        mAuthorView = findViewById(R.id.author);
        mTitleView = findViewById(R.id.title);
        mDetailsGroup = findViewById(R.id.original_details);

        updateViews();

        mListView = findViewById(R.id.resultList);
        LinearLayoutManager linearLayoutManager = new LinearLayoutManager(this);
        mListView.setLayoutManager(linearLayoutManager);
        mListView.addItemDecoration(
                new DividerItemDecoration(this, linearLayoutManager.getOrientation()));
        mWorksAdapter = new WorksAdapter(this, mWorks);
        mListView.setAdapter(mWorksAdapter);

        findViewById(R.id.btn_search).setOnClickListener(v -> doSearch());

        if (savedInstanceState == null) {
            // On first start of the activity only: if we have a book, start a search
            if (mModel.getBookId() > 0) {
                doSearch();
            }
        }
    }

    private void updateViews() {
        if (mModel.getBookId() > 0) {
            mAuthorView.setText(mModel.getAuthorText());
            mTitleView.setText(mModel.getTitleText());
            mIsbnView.setText(mModel.getIsbnText());
            mDetailsGroup.setVisibility(View.VISIBLE);
        } else {
            mDetailsGroup.setVisibility(View.GONE);
        }

        mSearchTextView.setText(mModel.getSearchText());
    }

    @Override
    protected void onPause() {
        mModel.setSearchText(mSearchTextView.getText().toString().trim());
        super.onPause();
    }

    @Override
    protected void onSaveInstanceState(@NonNull final Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putString(UniqueId.BKEY_SEARCH_TEXT, mModel.getSearchText());
    }

    /**
     * Start the search.
     */
    private void doSearch() {
        Snackbar.make(mListView, R.string.progress_msg_connecting, Snackbar.LENGTH_LONG).show();
        mModel.search(mSearchTextView.getText().toString().trim());
    }

    /**
     * Handle user clicking on a book.
     * This should show editions and allow the user to select a specific edition.
     * ENHANCE: Implement edition lookup - requires access to work.editions from Goodreads
     */
    private void onWorkSelected(@NonNull final GoodreadsWork work) {
        String msg = "Not implemented: requires access to work.editions from Goodreads";
        Snackbar.make(mListView, msg, Snackbar.LENGTH_LONG).show();
    }

    public static class GrSearchViewModel
            extends ViewModel {

        private final MutableLiveData<List<GoodreadsWork>> mWorks = new MutableLiveData<>();
        private final MutableLiveData<Boolean> mBookNoLongerExists = new MutableLiveData<>();
        private final TaskListener<List<GoodreadsWork>> mTaskListener = message ->
                mWorks.setValue(message.result);

        /** Database Access. */
        private DAO mDb;
        /** Data from the 'incoming' book. */
        private long mBookId;
        private String mIsbnText;
        private String mAuthorText;
        private String mTitleText;
        private String mSearchText;

        /** Observable. */
        @NonNull
        public MutableLiveData<List<GoodreadsWork>> getWorks() {
            return mWorks;
        }

        /** Observable. */
        @NonNull
        MutableLiveData<Boolean> getBookNoLongerExists() {
            return mBookNoLongerExists;
        }

        @Override
        protected void onCleared() {
            if (mDb != null) {
                mDb.close();
            }
        }

        /**
         * Pseudo constructor.
         *
         * @param args {@link Intent#getExtras()} or {@link Fragment#getArguments()}
         */
        public void init(@NonNull final Bundle args,
                         @Nullable final Bundle savedInstanceState) {
            if (mDb == null) {
                mDb = new DAO(TAG);

                mBookId = args.getLong(DBDefinitions.KEY_PK_ID);
                if (mBookId > 0) {
                    try (Cursor cursor = mDb.fetchBookById(mBookId)) {
                        if (cursor.moveToFirst()) {
                            final CursorRow cursorRow = new CursorRow(cursor);
                            mAuthorText = cursorRow.getString(
                                    DBDefinitions.KEY_AUTHOR_FORMATTED_GIVEN_FIRST);
                            mTitleText = cursorRow.getString(DBDefinitions.KEY_TITLE);
                            mIsbnText = cursorRow.getString(DBDefinitions.KEY_ISBN);
                        } else {
                            mBookNoLongerExists.setValue(true);
                        }
                    }
                    mSearchText = mAuthorText + ' ' + mTitleText + ' ' + mIsbnText + ' ';
                }
            }
            Bundle currentArgs = savedInstanceState != null ? savedInstanceState : args;
            mSearchText = currentArgs.getString(UniqueId.BKEY_SEARCH_TEXT, mSearchText);
        }

        public long getBookId() {
            return mBookId;
        }

        @Nullable
        String getIsbnText() {
            return mIsbnText;
        }

        @Nullable
        String getAuthorText() {
            return mAuthorText;
        }

        @Nullable
        String getTitleText() {
            return mTitleText;
        }

        @Nullable
        String getSearchText() {
            return mSearchText;
        }

        void setSearchText(@Nullable final String searchText) {
            mSearchText = searchText;
        }

        public void search(@NonNull final String searchText) {
            mSearchText = searchText;
            if (!mSearchText.isEmpty()) {
                new FetchWorksTask(mSearchText, mTaskListener).execute();
            }
        }
    }

    /**
     * Holder pattern for search results.
     */
    private static class Holder
            extends RecyclerViewViewHolderBase {

        @NonNull
        final ImageView coverView;
        @NonNull
        final TextView titleView;
        @NonNull
        final TextView authorView;

        Holder(@NonNull final View itemView) {
            super(itemView);

            coverView = itemView.findViewById(R.id.coverImage0);
            if (!App.isUsed(UniqueId.BKEY_THUMBNAIL)) {
                coverView.setVisibility(View.GONE);
            }
            authorView = itemView.findViewById(R.id.author);
            titleView = itemView.findViewById(R.id.title);
        }
    }

    /**
     * Adapter that uses holder pattern to display Goodreads books and
     * allows for background image retrieval.
     */
    private class WorksAdapter
            extends RecyclerViewAdapterBase<GoodreadsWork, Holder> {

        private final int mMaxSize;

        /**
         * Constructor.
         *
         * @param context Current context
         * @param items   List of items to display
         */
        WorksAdapter(@NonNull final Context context,
                     @NonNull final List<GoodreadsWork> items) {
            super(context, items, null);

            mMaxSize = ImageUtils.getMaxImageSize(context, ImageUtils.SCALE_MEDIUM);
        }

        @NonNull
        @Override
        public Holder onCreateViewHolder(@NonNull final ViewGroup parent,
                                         final int viewType) {

            View view = getLayoutInflater()
                    .inflate(R.layout.row_goodreads_work_item, parent, false);
            return new Holder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull final Holder holder,
                                     final int position) {
            super.onBindViewHolder(holder, position);

            GoodreadsWork item = getItem(position);

            holder.itemView.setOnClickListener(v -> onWorkSelected(item));

            // get the cover (or start a background task to get it)
            item.fillImageView(holder.coverView, mMaxSize, mMaxSize);

            // Update the views based on the work
            holder.authorView.setText(item.authorName);
            holder.titleView.setText(item.title);
        }
    }
}
