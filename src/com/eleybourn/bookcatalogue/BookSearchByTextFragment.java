package com.eleybourn.bookcatalogue;

import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;
import android.widget.EditText;

import androidx.annotation.CallSuper;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.ActionBar;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Set;

import com.eleybourn.bookcatalogue.database.DatabaseDefinitions;
import com.eleybourn.bookcatalogue.debug.Logger;
import com.eleybourn.bookcatalogue.debug.Tracker;
import com.eleybourn.bookcatalogue.dialogs.HintManager;
import com.eleybourn.bookcatalogue.searches.SearchCoordinator;
import com.eleybourn.bookcatalogue.utils.UserMessage;

public class BookSearchByTextFragment
        extends BookSearchBaseFragment {

    /** Fragment manager tag. */
    public static final String TAG = BookSearchByTextFragment.class.getSimpleName();

    /** A list of author names we have already searched for in this session. */
    @NonNull
    private final ArrayList<String> mAuthorNames = new ArrayList<>();
    /** */
    private ArrayAdapter<String> mAuthorAdapter;

    private EditText mTitleView;
    private AutoCompleteTextView mAuthorView;
    @NonNull
    private String mAuthorSearchText = "";
    @NonNull
    private String mTitleSearchText = "";

    @Override
    @Nullable
    public View onCreateView(@NonNull final LayoutInflater inflater,
                             @Nullable final ViewGroup container,
                             @Nullable final Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_booksearch_by_text, container, false);
    }

    @Override
    public void onActivityCreated(@Nullable final Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);
        Bundle args = savedInstanceState == null ? requireArguments() : savedInstanceState;
        mAuthorSearchText = args.getString(UniqueId.BKEY_SEARCH_AUTHOR, "");
        mTitleSearchText = args.getString(DatabaseDefinitions.KEY_TITLE, "");

        ActionBar actionBar = mActivity.getSupportActionBar();
        if (actionBar != null) {
            actionBar.setTitle(R.string.title_search_for);
            actionBar.setSubtitle(null);
        }

        View view = requireView();
        mTitleView = view.findViewById(R.id.title);
        mAuthorView = view.findViewById(R.id.author);

        populateAuthorList();

        view.findViewById(R.id.btn_search).setOnClickListener(v -> {
            mAuthorSearchText = mAuthorView.getText().toString().trim();
            mTitleSearchText = mTitleView.getText().toString().trim();
            prepareSearch();
        });

        // Display hint if required
        if (savedInstanceState == null) {
            HintManager.displayHint(getLayoutInflater(), R.string.hint_book_search_by_text,
                                    null);
        }
    }

    private void prepareSearch() {
        if (mAuthorAdapter.getPosition(mAuthorSearchText) < 0) {
            // Based on code from filipeximenes we also need to update the adapter here in
            // case no author or book is added, but we still want to see 'recent' entries.
            if (!mAuthorSearchText.isEmpty()) {
                boolean found = false;
                for (String s : mAuthorNames) {
                    if (s.equalsIgnoreCase(mAuthorSearchText)) {
                        found = true;
                        break;
                    }
                }
                if (!found) {
                    // Keep a list of names as typed to use when we recreate list
                    mAuthorNames.add(mAuthorSearchText);
                    // Add to adapter, in case search produces no results
                    mAuthorAdapter.add(mAuthorSearchText);
                }
            }
        }

        startSearch();
    }

    /**
     * Start the actual search with the {@link SearchCoordinator} in the background.
     * <p>
     * The results will arrive in {@link #onSearchFinished}
     */
    private void startSearch() {
        // check if we have an active search, if so, quit.
        if (mSearchManagerId != 0) {
            return;
        }

        //sanity check
        if ((mAuthorSearchText.isEmpty()) || mTitleSearchText.isEmpty()) {
            UserMessage.showUserMessage(mAuthorView, R.string.warning_required_at_least_one);
            return;
        }
        if (super.startSearch(mAuthorSearchText, mTitleSearchText, "")) {
            // reset the details so we don't restart the search unnecessarily
            mAuthorSearchText = "";
            mTitleSearchText = "";
        }
    }

    /**
     * results of search started by {@link #startSearch}.
     * <p>
     * The details will get sent to {@link EditBookActivity}
     */
    public void onSearchFinished(final boolean wasCancelled,
                                 @NonNull final Bundle bookData) {
        Logger.info(this, Tracker.State.Enter,
                    "onSearchFinished", "SearchManagerId=" + mSearchManagerId);
        try {
            if (!wasCancelled) {
                mActivity.getTaskManager().sendHeaderUpdate(R.string.progress_msg_adding_book);
                Intent intent = new Intent(getContext(), EditBookActivity.class)
                        .putExtra(UniqueId.BKEY_BOOK_DATA, bookData);
                startActivityForResult(intent, REQ_BOOK_EDIT);

                // Clear the data entry fields ready for the next one
                mAuthorView.setText("");
                mTitleView.setText("");
            }
        } finally {
            // Clean up
            mSearchManagerId = 0;
            // Make sure the base message will be empty.
            mActivity.getTaskManager().sendHeaderUpdate(null);
        }
    }

    @Override
    @CallSuper
    public void onActivityResult(final int requestCode,
                                 final int resultCode,
                                 @Nullable final Intent data) {
        Tracker.enterOnActivityResult(this, requestCode, resultCode, data);
        // for now nothing local.
//        switch (requestCode) {
//            default:
        super.onActivityResult(requestCode, resultCode, data);
//                break;
//        }

        // refresh, we could have modified/created Authors while editing
        // (even when cancelled the edit)
        populateAuthorList();

        Tracker.exitOnActivityResult(this);
    }

    @Override
    public void onPause() {
        super.onPause();
        mAuthorSearchText = mAuthorView.getText().toString().trim();
        mTitleSearchText = mTitleView.getText().toString().trim();
    }

    @Override
    @CallSuper
    public void onSaveInstanceState(@NonNull final Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putString(UniqueId.BKEY_SEARCH_AUTHOR, mAuthorSearchText);
        outState.putString(DatabaseDefinitions.KEY_TITLE, mTitleSearchText);
    }

    /**
     * Setup the adapter for the Author AutoCompleteTextView field.
     */
    private void populateAuthorList() {
        // Get all known authors and build a Set of the names
        final ArrayList<String> authors = mDb.getAuthorsFormattedName();
        final Set<String> uniqueNames = new HashSet<>();
        for (String s : authors) {
            uniqueNames.add(s.toUpperCase());
        }

        // Add the names the user has already tried (to handle errors and mistakes)
        for (String s : mAuthorNames) {
            if (!uniqueNames.contains(s.toUpperCase())) {
                authors.add(s);
            }
        }

        // Now get an adapter based on the combined names
        mAuthorAdapter = new ArrayAdapter<>(mActivity, android.R.layout.simple_dropdown_item_1line,
                                            authors);
        mAuthorView.setAdapter(mAuthorAdapter);
    }
}
