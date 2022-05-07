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

import android.os.Parcel;
import android.os.Parcelable;
import android.text.TextUtils;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

import com.hardbacknutter.nevertoomanybooks.database.dao.FtsDao;

/**
 * Holder class for search criteria with some methods to bulk manipulate them.
 */
public class SearchCriteria
        implements Parcelable {

    public static final Creator<SearchCriteria> CREATOR = new Creator<>() {
        @Override
        public SearchCriteria createFromParcel(@NonNull final Parcel in) {
            return new SearchCriteria(in);
        }

        @Override
        public SearchCriteria[] newArray(final int size) {
            return new SearchCriteria[size];
        }
    };

    /** Log tag. */
    private static final String TAG = "SearchCriteria";
    public static final String BKEY = TAG + ":a";
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
    /** Bundle key for generic search text. */
    private static final String BKEY_SEARCH_TEXT_KEYWORDS = TAG + ":keywords";
    /**
     * List of book ID's to display.
     * The RESULT of a search with {@link SearchFtsFragment}
     * which can be re-used for the builder.
     */
    @NonNull
    private final ArrayList<Long> mBookIdList = new ArrayList<>();

    /** Book title to use in FTS search query. */
    @Nullable
    private String mFtsBookTitle;
    /** Series title to use in FTS search query. */
    @Nullable
    private String mFtsSeriesTitle;
    /** Author to use in FTS search query. */
    @Nullable
    private String mFtsAuthor;
    /** Publisher to use in FTS search query. */
    @Nullable
    private String mFtsPublisher;
    /**
     * Keywords to use in FTS search query.
     * <p>
     * Always use {@link #setFtsKeywords(String)} as we need to intercept the "." character.
     */
    @Nullable
    private String mFtsKeywords;
    /** Name of the person we lend books to, to use in search query. */
    @Nullable
    private String mLoanee;

    SearchCriteria() {
    }

    /**
     * {@link Parcelable} Constructor.
     *
     * @param in Parcel to construct the object from
     */
    private SearchCriteria(@NonNull final Parcel in) {
        in.readList(mBookIdList, getClass().getClassLoader());
        mFtsBookTitle = in.readString();
        mFtsSeriesTitle = in.readString();
        mFtsAuthor = in.readString();
        mFtsPublisher = in.readString();
        mFtsKeywords = in.readString();
        mLoanee = in.readString();
    }

    @Override
    public void writeToParcel(@NonNull final Parcel dest,
                              final int flags) {
        dest.writeList(mBookIdList);
        dest.writeString(mFtsBookTitle);
        dest.writeString(mFtsSeriesTitle);
        dest.writeString(mFtsAuthor);
        dest.writeString(mFtsPublisher);
        dest.writeString(mFtsKeywords);
        dest.writeString(mLoanee);
    }

    @Override
    public int describeContents() {
        return 0;
    }

    public void clear() {
        mBookIdList.clear();
        mFtsBookTitle = null;
        mFtsSeriesTitle = null;
        mFtsAuthor = null;
        mFtsPublisher = null;
        mFtsKeywords = null;
        mLoanee = null;
    }

    @NonNull
    public ArrayList<Long> getBookIdList() {
        return mBookIdList;
    }

    public void setBookIdList(@Nullable final List<Long> bookIdList) {
        mBookIdList.clear();
        if (bookIdList != null) {
            mBookIdList.addAll(bookIdList);
        }
    }

    @NonNull
    public Optional<String> getFtsMatchQuery() {
        return FtsDao.createMatchString(mFtsBookTitle,
                                        mFtsSeriesTitle,
                                        mFtsAuthor,
                                        mFtsPublisher,
                                        mFtsKeywords);
    }

    @Nullable
    public String getFtsBookTitle() {
        return mFtsBookTitle;
    }

    public void setFtsBookTitle(@Nullable final String ftsBookTitle) {
        mFtsBookTitle = ftsBookTitle;
    }

    @Nullable
    public String getFtsSeriesTitle() {
        return mFtsSeriesTitle;
    }

    public void setFtsSeriesTitle(@Nullable final String ftsSeriesTitle) {
        mFtsSeriesTitle = ftsSeriesTitle;
    }

    @Nullable
    public String getFtsAuthor() {
        return mFtsAuthor;
    }

    public void setFtsAuthor(@Nullable final String ftsAuthor) {
        mFtsAuthor = ftsAuthor;
    }

    @Nullable
    public String getFtsPublisher() {
        return mFtsPublisher;
    }

    public void setFtsPublisher(@Nullable final String ftsPublisher) {
        mFtsPublisher = ftsPublisher;
    }

    @Nullable
    public String getFtsKeywords() {
        return mFtsKeywords;
    }

    public void setFtsKeywords(@Nullable final String keywords) {
        if (keywords == null || keywords.isEmpty() || ".".equals(keywords)) {
            mFtsKeywords = null;
        } else {
            mFtsKeywords = keywords.trim();
        }
    }

    /** Not supported by FTS. */
    @NonNull
    public Optional<String> getLoanee() {
        if (mLoanee != null && !mLoanee.trim().isEmpty()) {
            return Optional.of(mLoanee.trim());
        } else {
            return Optional.empty();
        }
    }

    public void setLoanee(@Nullable final String loanee) {
        mLoanee = loanee;
    }

    public void search(@NonNull final FtsDao dao,
                       final int maxSuggestions) {
        mBookIdList.clear();
        mBookIdList.addAll(dao.search(mFtsAuthor,
                                      mFtsBookTitle,
                                      mFtsSeriesTitle,
                                      mFtsPublisher,
                                      mFtsKeywords,
                                      maxSuggestions));
    }

    /**
     * Get a single string with all search words, for displaying.
     *
     * @return an Optional with a csv string.
     */
    @NonNull
    public Optional<String> getDisplayText() {
        final Collection<String> list = new ArrayList<>();

        if (mFtsBookTitle != null && !mFtsBookTitle.isEmpty()) {
            list.add(mFtsBookTitle);
        }
        if (mFtsSeriesTitle != null && !mFtsSeriesTitle.isEmpty()) {
            list.add(mFtsSeriesTitle);
        }
        if (mFtsAuthor != null && !mFtsAuthor.isEmpty()) {
            list.add(mFtsAuthor);
        }
        if (mFtsPublisher != null && !mFtsPublisher.isEmpty()) {
            list.add(mFtsPublisher);
        }
        if (mFtsKeywords != null && !mFtsKeywords.isEmpty()) {
            list.add(mFtsKeywords);
        }
        if (mLoanee != null && !mLoanee.isEmpty()) {
            list.add(mLoanee);
        }

        final String text = TextUtils.join(",", list);
        if (text.isEmpty()) {
            return Optional.empty();
        } else {
            return Optional.of(text);
        }
    }

    public boolean isEmpty() {
        return mBookIdList.isEmpty()
               && (mFtsBookTitle == null || mFtsBookTitle.isEmpty())
               && (mFtsSeriesTitle == null || mFtsSeriesTitle.isEmpty())
               && (mFtsAuthor == null || mFtsAuthor.isEmpty())
               && (mFtsPublisher == null || mFtsPublisher.isEmpty())
               && (mFtsKeywords == null || mFtsKeywords.isEmpty())
               && (mLoanee == null || mLoanee.isEmpty());
    }

    @Override
    @NonNull
    public String toString() {
        return "SearchCriteria{"
               + "mFtsBookTitle=`" + mFtsBookTitle + '`'
               + ", mFtsSeriesTitle=`" + mFtsSeriesTitle + '`'
               + ", mFtsAuthor=`" + mFtsAuthor + '`'
               + ", mFtsPublisher=`" + mFtsPublisher + '`'
               + ", mFtsKeywords=`" + mFtsKeywords + '`'
               + ", mLoanee=`" + mLoanee + '`'
               + ", mBookIdList=" + mBookIdList
               + '}';
    }
}
