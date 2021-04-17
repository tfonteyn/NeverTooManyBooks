/*
 * @Copyright 2018-2021 HardBackNutter
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

import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.ArrayList;
import java.util.Collection;

import com.hardbacknutter.nevertoomanybooks.database.DBKey;
import com.hardbacknutter.nevertoomanybooks.entities.Book;

/**
 * Holder class for search criteria with some methods to bulk manipulate them.
 */
public class SearchCriteria {

    /** Log tag. */
    private static final String TAG = "SearchCriteria";

    /** Bundle key for generic search text. */
    public static final String BKEY_SEARCH_TEXT_KEYWORDS = TAG + ":keywords";
    /**
     * Bundle key for Author search text
     * (all DB KEY's and the ARRAY key is for authors with verified names).
     */
    public static final String BKEY_SEARCH_TEXT_AUTHOR = TAG + ":author";
    /**
     * Bundle key for Publisher search text
     * (all DB KEY's and the ARRAY key is for publishers with verified names).
     */
    public static final String BKEY_SEARCH_TEXT_PUBLISHER = TAG + ":publisher";

    /**
     * List of book ID's to display.
     * The RESULT of a search with {@link SearchFtsFragment}
     * which can be re-used for the builder.
     */
    @Nullable
    private ArrayList<Long> mBookIdList;

    /**
     * Author to use in FTS search query.
     * Supported in the builder and {@link SearchFtsFragment}.
     */
    @Nullable
    private String mFtsAuthor;

    /**
     * Publisher to use in FTS search query.
     * Supported in the builder and {@link SearchFtsFragment}.
     */
    @Nullable
    private String mFtsPublisher;

    /**
     * Title to use in FTS search query.
     * Supported in the builder and {@link SearchFtsFragment}.
     */
    @Nullable
    private String mFtsTitle;

    /**
     * Series to use in FTS search query.
     * Supported in the builder, but not yet user-settable.
     */
    @Nullable
    private String mFtsSeries;

    /**
     * Name of the person we lend books to, to use in search query.
     * Supported in the builder, but not yet user-settable.
     */
    @Nullable
    private String mLoanee;

    /**
     * Keywords to use in FTS search query.
     * Supported in the builder and {@link SearchFtsFragment}.
     * <p>
     * Always use the setter as we need to intercept the "." character.
     */
    @Nullable
    private String mFtsKeywords;

    public void clear() {
        mFtsKeywords = null;
        mFtsAuthor = null;
        mFtsPublisher = null;
        mFtsTitle = null;

        mFtsSeries = null;
        mLoanee = null;

        mBookIdList = null;
    }

    @Nullable
    public String getLoanee() {
        return mLoanee;
    }

    @Nullable
    public ArrayList<Long> getBookIdList() {
        return mBookIdList;
    }

    @Nullable
    public String getFtsAuthor() {
        return mFtsAuthor;
    }

    @Nullable
    public String getFtsPublisher() {
        return mFtsPublisher;
    }

    @Nullable
    public String getFtsTitle() {
        return mFtsTitle;
    }

    @Nullable
    public String getFtsSeries() {
        return mFtsSeries;
    }

    @Nullable
    public String getFtsKeywords() {
        return mFtsKeywords;
    }

    /**
     * Get a single string with all FTS search words, for displaying.
     *
     * @return csv string, can be empty, but never {@code null}.
     */
    @NonNull
    public String getFtsSearchText() {
        final Collection<String> list = new ArrayList<>();

        if (mFtsAuthor != null && !mFtsAuthor.isEmpty()) {
            list.add(mFtsAuthor);
        }
        if (mFtsPublisher != null && !mFtsPublisher.isEmpty()) {
            list.add(mFtsPublisher);
        }
        if (mFtsTitle != null && !mFtsTitle.isEmpty()) {
            list.add(mFtsTitle);
        }
        if (mFtsSeries != null && !mFtsSeries.isEmpty()) {
            list.add(mFtsSeries);
        }
        if (mFtsKeywords != null && !mFtsKeywords.isEmpty()) {
            list.add(mFtsKeywords);
        }
        return TextUtils.join(",", list);
    }

    public void setKeywords(@Nullable final String keywords) {
        if (keywords == null || keywords.isEmpty() || ".".equals(keywords)) {
            mFtsKeywords = null;
        } else {
            mFtsKeywords = keywords.trim();
        }
    }

    /**
     * Only copies the criteria which are set.
     * Criteria not set in the bundle, are preserved!
     *
     * @param bundle     with criteria.
     * @param clearFirst Flag to force clearing all before loading the new criteria
     *
     * @return {@code true} if at least one criteria was set
     */
    public boolean from(@NonNull final Bundle bundle,
                        final boolean clearFirst) {
        if (clearFirst) {
            clear();
        }
        boolean isSet = false;

        if (bundle.containsKey(BKEY_SEARCH_TEXT_KEYWORDS)) {
            setKeywords(bundle.getString(BKEY_SEARCH_TEXT_KEYWORDS));
            isSet = true;
        }
        if (bundle.containsKey(BKEY_SEARCH_TEXT_AUTHOR)) {
            mFtsAuthor = bundle.getString(BKEY_SEARCH_TEXT_AUTHOR);
            isSet = true;
        }
        if (bundle.containsKey(BKEY_SEARCH_TEXT_PUBLISHER)) {
            mFtsPublisher = bundle.getString(BKEY_SEARCH_TEXT_PUBLISHER);
            isSet = true;
        }
        if (bundle.containsKey(DBKey.KEY_TITLE)) {
            mFtsTitle = bundle.getString(DBKey.KEY_TITLE);
            isSet = true;
        }
        if (bundle.containsKey(DBKey.KEY_SERIES_TITLE)) {
            mFtsSeries = bundle.getString(DBKey.KEY_SERIES_TITLE);
            isSet = true;
        }

        if (bundle.containsKey(DBKey.KEY_LOANEE)) {
            mLoanee = bundle.getString(DBKey.KEY_LOANEE);
            isSet = true;
        }
        if (bundle.containsKey(Book.BKEY_BOOK_ID_LIST)) {
            //noinspection unchecked
            mBookIdList = (ArrayList<Long>) bundle.getSerializable(Book.BKEY_BOOK_ID_LIST);
            isSet = true;
        }

        return isSet;
    }

    /**
     * Put the search criteria as extras in the Intent.
     *
     * @param intent which will be used start an Activity
     */
    public void to(@NonNull final Intent intent) {
        intent.putExtra(BKEY_SEARCH_TEXT_KEYWORDS, mFtsKeywords)
              .putExtra(BKEY_SEARCH_TEXT_AUTHOR, mFtsAuthor)
              .putExtra(BKEY_SEARCH_TEXT_PUBLISHER, mFtsPublisher)
              .putExtra(DBKey.KEY_TITLE, mFtsTitle)
              .putExtra(DBKey.KEY_SERIES_TITLE, mFtsSeries)

              .putExtra(DBKey.KEY_LOANEE, mLoanee)
              .putExtra(Book.BKEY_BOOK_ID_LIST, mBookIdList);
    }

    public boolean isEmpty() {
        return (mFtsKeywords == null || mFtsKeywords.isEmpty())
               && (mFtsAuthor == null || mFtsAuthor.isEmpty())
               && (mFtsPublisher == null || mFtsPublisher.isEmpty())
               && (mFtsTitle == null || mFtsTitle.isEmpty())
               && (mFtsSeries == null || mFtsSeries.isEmpty())

               && (mLoanee == null || mLoanee.isEmpty())
               && (mBookIdList == null || mBookIdList.isEmpty());
    }

    public boolean hasIdList() {
        return mBookIdList != null && !mBookIdList.isEmpty();
    }

    @Override
    @NonNull
    public String toString() {
        return "SearchCriteria{"
               + "mFtsAuthor=`" + mFtsAuthor + '`'
               + ", mFtsTitle=`" + mFtsTitle + '`'
               + ", mFtsSeries=`" + mFtsSeries + '`'
               + ", mFtsPublisher=`" + mFtsPublisher + '`'
               + ", mLoanee=`" + mLoanee + '`'
               + ", mFtsKeywords=`" + mFtsKeywords + '`'
               + ", mBookIdList=" + mBookIdList
               + '}';
    }
}
