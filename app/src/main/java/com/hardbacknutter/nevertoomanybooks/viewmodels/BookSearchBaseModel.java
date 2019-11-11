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
package com.hardbacknutter.nevertoomanybooks.viewmodels;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

import com.hardbacknutter.nevertoomanybooks.UniqueId;
import com.hardbacknutter.nevertoomanybooks.database.DAO;
import com.hardbacknutter.nevertoomanybooks.database.DBDefinitions;
import com.hardbacknutter.nevertoomanybooks.searches.SearchCoordinator;
import com.hardbacknutter.nevertoomanybooks.searches.SearchSites;
import com.hardbacknutter.nevertoomanybooks.searches.Site;
import com.hardbacknutter.nevertoomanybooks.tasks.managedtasks.TaskManager;

public class BookSearchBaseModel
        extends ViewModel
        implements ActivityResultDataModel {

    /** The last Intent returned as a result of creating a book. */
    private final Intent mResultData = new Intent();
    private final MutableLiveData<Bundle> mBookData = new MutableLiveData<>();
    private final MutableLiveData<Boolean> mSearchCancelled = new MutableLiveData<>();

    /** Database Access. */
    private DAO mDb;
    /** Sites to search on. */
    private ArrayList<Site> mSearchSites;

    @NonNull
    private String mNativeIdSearchText = "";
    @NonNull
    private String mIsbnSearchText = "";
    @NonNull
    private String mAuthorSearchText = "";
    @NonNull
    private String mTitleSearchText = "";
    @NonNull
    private String mPublisherSearchText = "";

    private TaskManager mTaskManager;
    private SearchCoordinator mSearchCoordinator;
    private boolean mIsSearchActive;
    private SearchCoordinator.OnSearchFinishedListener mOnSearchFinishedListener =
            (wasCancelled, bookData) -> {
                // Tell our listener they can close the progress dialog.
                mTaskManager.sendHeaderUpdate(null);

                mIsSearchActive = false;

                if (!wasCancelled) {
                    mBookData.setValue(bookData);
                } else {
                    mSearchCancelled.setValue(true);
                }
            };

    @NonNull
    public MutableLiveData<Bundle> getSearchResults() {
        return mBookData;
    }

    public boolean isSearchActive() {
        return mIsSearchActive;
    }

    public void setSearchActive(final boolean searchActive) {
        mIsSearchActive = searchActive;
    }

    @NonNull
    public MutableLiveData<Boolean> getSearchCancelled() {
        return mSearchCancelled;
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
     * @param args    {@link Intent#getExtras()} or {@link Fragment#getArguments()}
     * @param context Current context
     */
    public void init(@NonNull final Context context,
                     @NonNull final Bundle args,
                     @NonNull final TaskManager taskManager) {
        if (mDb == null) {
            mDb = new DAO();
            mTaskManager = taskManager;

            mNativeIdSearchText = args.getString(UniqueId.BKEY_SEARCH_BOOK_NATIVE_ID, "");
            mAuthorSearchText = args.getString(UniqueId.BKEY_SEARCH_AUTHOR, "");

            mIsbnSearchText = args.getString(DBDefinitions.KEY_ISBN, "");
            mTitleSearchText = args.getString(DBDefinitions.KEY_TITLE, "");
            mPublisherSearchText = args.getString(DBDefinitions.KEY_PUBLISHER, "");

            // use global preference.
            mSearchSites = SearchSites.getSites(context, SearchSites.ListType.Data);
        }
    }

    /**
     * NEVER close this database.
     *
     * @return the dao
     */
    public DAO getDb() {
        return mDb;
    }

    /**
     * Get the <strong>current</strong> preferred search sites.
     *
     * @return list
     */
    @NonNull
    public ArrayList<Site> getSearchSites() {
        return mSearchSites;
    }

    /**
     * Override the initial list.
     *
     * @param searchSites to use temporarily
     */
    public void setSearchSites(@NonNull final ArrayList<Site> searchSites) {
        mSearchSites = searchSites;
    }

    /**
     * Get the <strong>current</strong> preferred search sites.
     *
     * @return bitmask
     */
    public int getEnabledSearchSites() {
        return SearchSites.getEnabledSites(mSearchSites);
    }

    public void clearSearchText() {
        mNativeIdSearchText = "";
        mIsbnSearchText = "";
        mAuthorSearchText = "";
        mTitleSearchText = "";
        mPublisherSearchText = "";
    }

    @NonNull
    public String getNativeIdSearchText() {
        return mNativeIdSearchText;
    }

    public void setNativeIdSearchText(@NonNull final String nativeIdSearchText) {
        mNativeIdSearchText = nativeIdSearchText;
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

    @NonNull
    public Intent getActivityResultData() {
        return mResultData;
    }

    public void setLastBookData(@NonNull final Intent lastBookData) {
        mResultData.putExtras(lastBookData);
    }

    @NonNull
    public ArrayList<String> getAuthorNames(@NonNull final ArrayList<String> authorNames) {

        ArrayList<String> authors = mDb.getAuthorNames(
                DBDefinitions.KEY_AUTHOR_FORMATTED_GIVEN_FIRST);

        final Set<String> uniqueNames = new HashSet<>(authors.size());
        for (String s : authors) {
            uniqueNames.add(s.toLowerCase(Locale.getDefault()));
        }

        // Add the names the user has already tried (to handle errors and mistakes)
        for (String s : authorNames) {
            if (!uniqueNames.contains(s.toLowerCase(Locale.getDefault()))) {
                authors.add(s);
            }
        }

        return authors;
    }

    private SearchCoordinator getSearchCoordinator() {
        if (mSearchCoordinator == null) {
            mSearchCoordinator = new SearchCoordinator(mTaskManager, mOnSearchFinishedListener);
        }
        return mSearchCoordinator;
    }

    public void sendHeaderUpdate(@Nullable final String message) {
        mTaskManager.sendHeaderUpdate(message);
    }

    /**
     * Start a {@link SearchCoordinator#search(String, String, String, String)}
     *
     * @return {@code true} if at least one search was started.
     */
    public boolean startSearch() {
        SearchCoordinator searchCoordinator = getSearchCoordinator();
        searchCoordinator.setSearchSites(getSearchSites());
        searchCoordinator.setFetchThumbnail(true);
        return searchCoordinator.search(mIsbnSearchText,
                                        mAuthorSearchText, mTitleSearchText,
                                        mPublisherSearchText);
    }

    /**
     * Start a {@link SearchCoordinator#searchByNativeId(Site, String)}
     *
     * @return {@code true} if at least one search was started.
     */
    public boolean searchByNativeId(@NonNull final Site site) {
        // sanity check
        if (mNativeIdSearchText.isEmpty()) {
            throw new IllegalStateException("mNativeIdSearchText was empty");
        }

        SearchCoordinator searchCoordinator = getSearchCoordinator();
        searchCoordinator.setFetchThumbnail(true);
        searchCoordinator.searchByNativeId(site, mNativeIdSearchText);
        return true;
    }

}
