package com.eleybourn.bookcatalogue.viewmodels;

import android.content.Intent;
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.ViewModel;

import com.eleybourn.bookcatalogue.App;
import com.eleybourn.bookcatalogue.BookSearchBaseFragment;
import com.eleybourn.bookcatalogue.UniqueId;
import com.eleybourn.bookcatalogue.database.DAO;
import com.eleybourn.bookcatalogue.database.DBDefinitions;
import com.eleybourn.bookcatalogue.searches.SearchSites;
import com.eleybourn.bookcatalogue.settings.Prefs;

public class BookSearchBaseModel
        extends ViewModel {

    /** Database access. */
    private DAO mDb;

    /** sites to search on. Can be overridden by the user (option menu). */
    private int mSearchSites = SearchSites.SEARCH_ALL;

    /** Objects managing current search. */
    private long mSearchCoordinatorId;

    /** The last Intent returned as a result of creating a book. */
    @Nullable
    private Intent mLastBookData;

    @NonNull
    private String mIsbnSearchText = "";
    @NonNull
    private String mAuthorSearchText = "";
    @NonNull
    private String mTitleSearchText = "";
    @NonNull
    private String mPublisherSearchText = "";


    @Override
    protected void onCleared() {
        if (mDb != null) {
            mDb.close();
        }
    }

    /**
     * Pseudo constructor.
     *
     * @param args Bundle with arguments
     */
    public void init(@NonNull final Bundle args) {
        if (mDb == null) {
            mDb = new DAO();
        }

        mSearchCoordinatorId = args.getLong(BookSearchBaseFragment.BKEY_SEARCH_COORDINATOR_ID);

        // optional, use ALL if not there
        mSearchSites = args.getInt(UniqueId.BKEY_SEARCH_SITES, SearchSites.SEARCH_ALL);

        mIsbnSearchText = args.getString(DBDefinitions.KEY_ISBN, "");
        mAuthorSearchText = args.getString(UniqueId.BKEY_SEARCH_AUTHOR, "");
        mTitleSearchText = args.getString(DBDefinitions.KEY_TITLE, "");
        mPublisherSearchText = args.getString(DBDefinitions.KEY_PUBLISHER, "");
    }

    /**
     * NEVER close this database.
     *
     * @return the dao
     */
    public DAO getDb() {
        return mDb;
    }

    public int getSearchSites() {
        return mSearchSites;
    }

    public void setSearchSites(final int searchSites) {
        mSearchSites = searchSites;
    }

    public long getSearchCoordinatorId() {
        return mSearchCoordinatorId;
    }

    public void setSearchCoordinator(final long searchCoordinator) {
        mSearchCoordinatorId = searchCoordinator;
    }

    public void clearSearchText() {
        mIsbnSearchText = "";
        mAuthorSearchText = "";
        mTitleSearchText = "";
        mPublisherSearchText = "";
    }

    @NonNull
    public String getIsbnSearchText() {
        return mIsbnSearchText;
    }

    public void setIsbnSearchText(@NonNull final String isbnSearchText) {
        mIsbnSearchText = isbnSearchText;
    }

    @NonNull
    public String getAuthorSearchText() {
        return mAuthorSearchText;
    }

    public void setAuthorSearchText(@NonNull final String authorSearchText) {
        mAuthorSearchText = authorSearchText;
    }

    @NonNull
    public String getTitleSearchText() {
        return mTitleSearchText;
    }

    public void setTitleSearchText(@NonNull final String titleSearchText) {
        mTitleSearchText = titleSearchText;
    }

    @NonNull
    public String getPublisherSearchText() {
         return mPublisherSearchText;
    }

    public void setPublisherSearchText(@NonNull final String publisherSearchText) {
        mPublisherSearchText = publisherSearchText;
    }

    @Nullable
    public Intent getLastBookData() {
        return mLastBookData;
    }

    public void setLastBookData(@Nullable final Intent lastBookData) {
        mLastBookData = lastBookData;
    }

    /**
     * At least one criteria must be available.
     * The publisher is optional.
     *
     * @return {@code true} if we have something to search on.
     */
    public boolean hasSearchData() {
        return !mIsbnSearchText.isEmpty()
                || !mAuthorSearchText.isEmpty()
                || !mTitleSearchText.isEmpty();
    }
}
