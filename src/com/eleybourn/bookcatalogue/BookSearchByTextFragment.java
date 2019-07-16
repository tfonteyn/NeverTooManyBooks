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

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

import com.eleybourn.bookcatalogue.database.DBDefinitions;
import com.eleybourn.bookcatalogue.debug.Logger;
import com.eleybourn.bookcatalogue.debug.Tracker;
import com.eleybourn.bookcatalogue.dialogs.TipManager;
import com.eleybourn.bookcatalogue.searches.SearchCoordinator;
import com.eleybourn.bookcatalogue.searches.SearchSites;
import com.eleybourn.bookcatalogue.utils.LocaleUtils;

public class BookSearchByTextFragment
        extends BookSearchBaseFragment {

    /** Fragment manager tag. */
    public static final String TAG = "BookSearchByTextFragment";

    /** A list of author names we have already searched for in this session. */
    @NonNull
    private final ArrayList<String> mAuthorNames = new ArrayList<>();
    private ArrayAdapter<String> mAuthorAdapter;

    private EditText mTitleView;
    private AutoCompleteTextView mAuthorView;
    // ENHANCE: add auto-completion for publishers?
    private EditText mPublisherView;
    private final SearchCoordinator.SearchFinishedListener mSearchFinishedListener =
            new SearchCoordinator.SearchFinishedListener() {
                /**
                 * results of search.
                 * <p>
                 * The details will get sent to {@link EditBookActivity}
                 * <p>
                 * <br>{@inheritDoc}
                 */
                @Override
                public void onSearchFinished(final boolean wasCancelled,
                                             @NonNull final Bundle bookData) {
                    if (BuildConfig.DEBUG && DEBUG_SWITCHES.SEARCH_INTERNET) {
                        Logger.debugEnter(this, "onSearchFinished",
                                          "SearchCoordinatorId="
                                                  + mBookSearchBaseModel.getSearchCoordinatorId());
                    }
                    try {
                        if (!wasCancelled) {
                            mTaskManager.sendHeaderUpdate(R.string.progress_msg_adding_book);

                            if (!bookData.containsKey(DBDefinitions.KEY_TITLE)) {
                                bookData.putString(DBDefinitions.KEY_TITLE,
                                                   mTitleView.getText().toString().trim());
                            }
                            //noinspection ConstantConditions
                            if (!bookData.containsKey(UniqueId.BKEY_AUTHOR_ARRAY)
                                    || bookData.getParcelableArrayList(
                                    UniqueId.BKEY_AUTHOR_ARRAY).isEmpty()) {
                                // does NOT use the array, that's reserved for verified names.
                                bookData.putString(DBDefinitions.KEY_AUTHOR_FORMATTED,
                                                   mAuthorView.getText().toString().trim());
                            }
                            if (!bookData.containsKey(DBDefinitions.KEY_PUBLISHER)) {
                                bookData.putString(DBDefinitions.KEY_PUBLISHER,
                                                   mPublisherView.getText().toString().trim());
                            }

                            Intent intent = new Intent(getContext(), EditBookActivity.class)
                                    .putExtra(UniqueId.BKEY_BOOK_DATA, bookData);
                            startActivityForResult(intent, UniqueId.REQ_BOOK_EDIT);

                            // Clear the data entry fields ready for the next one
                            mAuthorView.setText("");
                            mTitleView.setText("");
                            mPublisherView.setText("");
                        }
                    } finally {
                        // Clean up
                        mBookSearchBaseModel.setSearchCoordinator(0);
                        // Make sure the base message will be empty.
                        mTaskManager.sendHeaderUpdate(null);
                    }
                }
            };

    @Override
    @Nullable
    public View onCreateView(@NonNull final LayoutInflater inflater,
                             @Nullable final ViewGroup container,
                             @Nullable final Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_booksearch_by_text, container, false);
        mTitleView = view.findViewById(R.id.title);
        mAuthorView = view.findViewById(R.id.author);
        mPublisherView = view.findViewById(R.id.publisher);
        view.findViewById(R.id.publisher_group)
            .setVisibility(SearchSites.usePublisher() ? View.VISIBLE : View.GONE);

        return view;
    }

    @Override
    public void onActivityCreated(@Nullable final Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);

        mTitleView.setText(mBookSearchBaseModel.getTitleSearchText());
        mAuthorView.setText(mBookSearchBaseModel.getAuthorSearchText());
        mPublisherView.setText(mBookSearchBaseModel.getPublisherSearchText());

        //noinspection ConstantConditions
        getActivity().setTitle(R.string.lbl_search_for_books);

        populateAuthorList();

        //noinspection ConstantConditions
        getView().findViewById(R.id.btn_search).setOnClickListener(v -> {
            mBookSearchBaseModel.setAuthorSearchText(mAuthorView.getText().toString().trim());
            mBookSearchBaseModel.setTitleSearchText(mTitleView.getText().toString().trim());
            mBookSearchBaseModel.setPublisherSearchText(mPublisherView.getText().toString().trim());
            prepareSearch();
        });

        // Display hint if required
        if (savedInstanceState == null) {
            TipManager.display(getLayoutInflater(), R.string.tip_book_search_by_text, null);
        }
    }

    private void prepareSearch() {

        String authorSearchText = mBookSearchBaseModel.getAuthorSearchText();
        if (mAuthorAdapter.getPosition(authorSearchText) < 0) {
            // Based on code from filipeximenes we also need to update the adapter here in
            // case no author or book is added, but we still want to see 'recent' entries.
            if (!authorSearchText.isEmpty()) {
                boolean found = false;
                for (String s : mAuthorNames) {
                    if (s.equalsIgnoreCase(authorSearchText)) {
                        found = true;
                        break;
                    }
                }
                if (!found) {
                    // Keep a list of names as typed to use when we recreate list
                    mAuthorNames.add(authorSearchText);
                    // Add to adapter, in case search produces no results
                    mAuthorAdapter.add(authorSearchText);
                }
            }
        }

        startSearch();
    }

    @Override
    SearchCoordinator.SearchFinishedListener getSearchFinishedListener() {
        return mSearchFinishedListener;
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
        mBookSearchBaseModel.setAuthorSearchText(mAuthorView.getText().toString().trim());
        mBookSearchBaseModel.setTitleSearchText(mTitleView.getText().toString().trim());
        mBookSearchBaseModel.setPublisherSearchText(mPublisherView.getText().toString().trim());
    }

    @Override
    @CallSuper
    public void onSaveInstanceState(@NonNull final Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putString(UniqueId.BKEY_SEARCH_AUTHOR,
                           mBookSearchBaseModel.getAuthorSearchText());
        outState.putString(DBDefinitions.KEY_TITLE,
                           mBookSearchBaseModel.getTitleSearchText());
        outState.putString(DBDefinitions.KEY_PUBLISHER,
                           mBookSearchBaseModel.getPublisherSearchText());
    }

    /**
     * Setup the adapter for the Author AutoCompleteTextView field.
     * Uses {@link DBDefinitions#KEY_AUTHOR_FORMATTED_GIVEN_FIRST} as not all
     * search sites can copy with the formatted version.
     */
    private void populateAuthorList() {
        //noinspection ConstantConditions
        Locale locale = LocaleUtils.from(getContext());
        // Get all known authors and build a Set of the names
        final ArrayList<String> authors = mBookSearchBaseModel.getDb().getAuthorNames(
                DBDefinitions.KEY_AUTHOR_FORMATTED_GIVEN_FIRST);

        final Set<String> uniqueNames = new HashSet<>(authors.size());
        for (String s : authors) {
            uniqueNames.add(s.toUpperCase(locale));
        }

        // Add the names the user has already tried (to handle errors and mistakes)
        for (String s : mAuthorNames) {
            if (!uniqueNames.contains(s.toUpperCase(locale))) {
                authors.add(s);
            }
        }

        // Now get an adapter based on the combined names
        mAuthorAdapter = new ArrayAdapter<>(getContext(),
                                            android.R.layout.simple_dropdown_item_1line,
                                            authors);
        mAuthorView.setAdapter(mAuthorAdapter);
    }
}
