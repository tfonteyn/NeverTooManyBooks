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
package com.hardbacknutter.nevertoomanybooks.goodreads;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.DividerItemDecoration;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.snackbar.Snackbar;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import com.hardbacknutter.nevertoomanybooks.BaseActivity;
import com.hardbacknutter.nevertoomanybooks.R;
import com.hardbacknutter.nevertoomanybooks.covers.ImageScale;
import com.hardbacknutter.nevertoomanybooks.database.DBDefinitions;
import com.hardbacknutter.nevertoomanybooks.databinding.ActivityGoodreadsSearchBinding;
import com.hardbacknutter.nevertoomanybooks.debug.ErrorMsg;
import com.hardbacknutter.nevertoomanybooks.viewmodels.BooksOnBookshelfModel;

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

    /** The ViewModel. */
    private GrSearchTask mGrSearchTask;

    private WorksAdapter mWorksAdapter;

    private ActivityGoodreadsSearchBinding mVb;

    /**
     * Convenience method to start this activity, or redirect to the register activity if
     * this application was not registered yet.
     *
     * @param context Current context
     * @param bookId  the book to search for
     */
    public static void open(@NonNull final Context context,
                            final long bookId) {
        final GoodreadsAuth grAuth = new GoodreadsAuth(context);
        if (!grAuth.hasCredentials(context)) {
            context.startActivity(new Intent(context, GoodreadsRegistrationActivity.class));
        }

        final Intent data = new Intent(context, GoodreadsSearchActivity.class)
                .putExtra(DBDefinitions.KEY_PK_ID, bookId);
        context.startActivity(data);
    }

    @Override
    protected void onSetContentView() {
        mVb = ActivityGoodreadsSearchBinding.inflate(getLayoutInflater());
        setContentView(mVb.getRoot());
    }

    @Override
    public void onCreate(@Nullable final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setTitle(getString(R.string.progress_msg_searching_site,
                           getString(R.string.site_goodreads)));

        mGrSearchTask = new ViewModelProvider(this).get(GrSearchTask.class);
        mGrSearchTask.init(Objects.requireNonNull(getIntent().getExtras(), ErrorMsg.NULL_EXTRAS),
                           savedInstanceState);
        mGrSearchTask.onProgressUpdate().observe(this, message -> {
            if (message.text != null) {
                Snackbar.make(mVb.getRoot(), message.text, Snackbar.LENGTH_LONG).show();
            }
        });
        mGrSearchTask.onFailure().observe(this, message ->
                Snackbar.make(mVb.getRoot(), GrStatus.getMessage(this, message.result),
                              Snackbar.LENGTH_LONG).show());
        mGrSearchTask.onCancelled().observe(this, message ->
                Snackbar.make(mVb.getRoot(), R.string.cancelled, Snackbar.LENGTH_LONG).show());
        mGrSearchTask.onFinished().observe(this, message -> {
            mWorks.clear();
            if (message.result != null && !message.result.isEmpty()) {
                mWorks.addAll(message.result);
            } else {
                // we don't get a status back, so treat null/empty list as "oops"
                // Given this code is not actually used... it might be OOPS
                Snackbar.make(mVb.getRoot(), R.string.warning_no_matching_book_found,
                              Snackbar.LENGTH_LONG).show();
            }
            mWorksAdapter.notifyDataSetChanged();
        });

        mGrSearchTask.onBookNoLongerExists().observe(this, flag -> {
            if (flag) {
                Snackbar.make(mVb.filterText, R.string.warning_book_no_longer_exists,
                              Snackbar.LENGTH_LONG).show();
            }
        });

        updateViews();

        final LinearLayoutManager linearLayoutManager = new LinearLayoutManager(this);
        mVb.resultList.setLayoutManager(linearLayoutManager);
        mVb.resultList.addItemDecoration(
                new DividerItemDecoration(this, linearLayoutManager.getOrientation()));
        mWorksAdapter = new WorksAdapter(this, mWorks);
        mVb.resultList.setAdapter(mWorksAdapter);

        mVb.btnSearch.setOnClickListener(v -> doSearch());

        if (savedInstanceState == null) {
            // On first start of the activity only: if we have a book, start a search
            if (mGrSearchTask.getBookId() > 0) {
                doSearch();
            }
        }
    }

    private void updateViews() {
        if (mGrSearchTask.getBookId() > 0) {
            mVb.author.setText(mGrSearchTask.getAuthorText());
            mVb.title.setText(mGrSearchTask.getTitleText());
            mVb.isbn.setText(mGrSearchTask.getIsbnText());
            mVb.originalDetails.setVisibility(View.VISIBLE);
        } else {
            mVb.originalDetails.setVisibility(View.GONE);
        }

        mVb.filterText.setText(mGrSearchTask.getSearchText());
    }

    @Override
    protected void onPause() {
        //noinspection ConstantConditions
        mGrSearchTask.setSearchText(mVb.filterText.getText().toString().trim());
        super.onPause();
    }

    @Override
    protected void onSaveInstanceState(@NonNull final Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putString(BooksOnBookshelfModel.SearchCriteria.BKEY_SEARCH_TEXT_KEYWORDS,
                           mGrSearchTask.getSearchText());
    }

    /**
     * Start the search.
     */
    private void doSearch() {
        Snackbar.make(mVb.resultList, R.string.progress_msg_connecting,
                      Snackbar.LENGTH_LONG).show();
        //noinspection ConstantConditions
        mGrSearchTask.search(mVb.filterText.getText().toString().trim());
    }

    /**
     * Handle user clicking on a book.
     * This should show editions and allow the user to select a specific edition.
     * ENHANCE: Implement edition lookup - requires access to work.editions from Goodreads
     */
    private void onWorkSelected(@NonNull final GoodreadsWork work) {
        String msg = "Not implemented: requires access to work.editions from Goodreads";
        Snackbar.make(mVb.resultList, msg, Snackbar.LENGTH_LONG).show();
    }

    /**
     * Row ViewHolder for {@link WorksAdapter}.
     */
    private static class Holder
            extends RecyclerView.ViewHolder {

        @NonNull
        final ImageView coverView;
        @NonNull
        final TextView titleView;
        @NonNull
        final TextView authorView;

        Holder(@NonNull final View itemView) {
            super(itemView);

            coverView = itemView.findViewById(R.id.coverImage0);
            if (!DBDefinitions
                    .isUsed(itemView.getContext(), DBDefinitions.PREFS_IS_USED_THUMBNAIL)) {
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
            extends RecyclerView.Adapter<Holder> {

        private final int mMaxSize;

        private final List<GoodreadsWork> mItems;

        /**
         * Constructor.
         *
         * @param context Current context
         * @param items   List of items to display
         */
        WorksAdapter(@NonNull final Context context,
                     @NonNull final List<GoodreadsWork> items) {
            mItems = items;
            mMaxSize = ImageScale.getSize(context, ImageScale.SCALE_MEDIUM);
        }

        @NonNull
        @Override
        public Holder onCreateViewHolder(@NonNull final ViewGroup parent,
                                         final int viewType) {

            final View view = getLayoutInflater()
                    .inflate(R.layout.row_goodreads_work_item, parent, false);
            return new Holder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull final Holder holder,
                                     final int position) {

            final GoodreadsWork item = mItems.get(position);

            holder.itemView.setOnClickListener(v -> onWorkSelected(item));

            // get the cover (or start a background task to get it)
            item.fillImageView(holder.coverView, mMaxSize, mMaxSize);

            // Update the views based on the work
            holder.authorView.setText(item.authorName);
            holder.titleView.setText(item.title);
        }

        @Override
        public int getItemCount() {
            return mItems.size();
        }
    }
}
