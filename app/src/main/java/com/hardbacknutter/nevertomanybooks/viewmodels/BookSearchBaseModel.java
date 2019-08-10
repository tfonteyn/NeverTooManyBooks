/*
 * @Copyright 2019 HardBackNutter
 * @License GNU General Public License
 *
 * This file is part of NeverToManyBooks.
 *
 * In August 2018, this project was forked from:
 * Book Catalogue 5.2.2 @copyright 2010 Philip Warner & Evan Leybourn
 *
 * Without their original creation, this project would not exist in its current form.
 * It was however largely rewritten/refactored and any comments on this fork
 * should be directed at HardBackNutter and not at the original creator.
 *
 * NeverToManyBooks is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * NeverToManyBooks is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 * See the GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with NeverToManyBooks. If not, see <http://www.gnu.org/licenses/>.
 */
package com.hardbacknutter.nevertomanybooks.viewmodels;

import android.content.Intent;
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.ViewModel;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

import com.hardbacknutter.nevertomanybooks.UniqueId;
import com.hardbacknutter.nevertomanybooks.database.DAO;
import com.hardbacknutter.nevertomanybooks.database.DBDefinitions;
import com.hardbacknutter.nevertomanybooks.searches.SearchSites;

public class BookSearchBaseModel
        extends ViewModel {

    /** Database Access. */
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

            // optional, use all if not defined
            mSearchSites = args.getInt(UniqueId.BKEY_SEARCH_SITES, SearchSites.SEARCH_ALL);

            mIsbnSearchText = args.getString(DBDefinitions.KEY_ISBN, "");
            mAuthorSearchText = args.getString(UniqueId.BKEY_SEARCH_AUTHOR, "");
            mTitleSearchText = args.getString(DBDefinitions.KEY_TITLE, "");
            mPublisherSearchText = args.getString(DBDefinitions.KEY_PUBLISHER, "");
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

    @NonNull
    public ArrayList<String> getAuthorNames(final ArrayList<String> authorNames,
                                            final Locale locale) {

        ArrayList<String> authors = mDb.getAuthorNames(
                DBDefinitions.KEY_AUTHOR_FORMATTED_GIVEN_FIRST);

        final Set<String> uniqueNames = new HashSet<>(authors.size());
        for (String s : authors) {
            uniqueNames.add(s.toUpperCase(locale));
        }

        // Add the names the user has already tried (to handle errors and mistakes)
        for (String s : authorNames) {
            if (!uniqueNames.contains(s.toUpperCase(locale))) {
                authors.add(s);
            }
        }

        return authors;
    }
}
