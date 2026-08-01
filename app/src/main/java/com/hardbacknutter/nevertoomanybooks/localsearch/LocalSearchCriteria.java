/*
 * @Copyright 2018-2026 HardBackNutter
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
package com.hardbacknutter.nevertoomanybooks.localsearch;

import android.os.Bundle;
import android.os.Parcel;
import android.os.Parcelable;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * Holder class for search criteria with some methods to bulk manipulate them.
 * <p>
 * We combine three distinct types of criteria in a single object.
 * <ul>
 *     <li>a list of book-ids. When present, all other criteria are ignored.</li>
 *     <li>FTS based fields which can be used to construct an FTS 'MATCH' SQL clause</li>
 *     <li>Simple string criteria (currently only 'lender')</li>
 * </ul>
 */
public class LocalSearchCriteria
        implements Parcelable {

    /** {@link Parcelable}. */
    public static final Creator<LocalSearchCriteria> CREATOR = new Creator<>() {
        @Override
        @NonNull
        public LocalSearchCriteria createFromParcel(@NonNull final Parcel in) {
            return new LocalSearchCriteria(in);
        }

        @Override
        @NonNull
        public LocalSearchCriteria[] newArray(final int size) {
            return new LocalSearchCriteria[size];
        }
    };

    /** Log tag. */
    private static final String TAG = "LocalSearchCriteria";
    private static final String BKEY = TAG + ":a";

    /**
     * Bundle key for Author search text.
     * <p>
     * Important: {@code DBKey}'s and the {@code Book.BKEY_AUTHOR_LIST} key are used
     * for <strong>verified</strong> names.
     * This key is for the <strong>user search/unverified text</strong>
     */
    public static final String BKEY_SEARCH_TEXT_AUTHOR = TAG + ":author";
    /**
     * Bundle key for Series search text.
     * <p>
     * Important: {@code DBKey}'s and the {@code Book.BKEY_SERIES_LIST} key are used
     * for <strong>verified</strong> names.
     * This key is for the <strong>user search/unverified text</strong>
     */
    public static final String BKEY_SEARCH_TEXT_SERIES = TAG + ":series";

    /**
     * Bundle key for Publisher search text.
     * <p>
     * Important: {@code DBKey}'s and the {@code Book.BKEY_PUBLISHER_LIST} key are used
     * for <strong>verified</strong> names.
     * This key is for the <strong>user search/unverified text</strong>
     */
    public static final String BKEY_SEARCH_TEXT_PUBLISHER = TAG + ":publisher";

    /**
     * List of book ID's to display.
     * The RESULT of a search with {@link SearchFtsFragment}
     * which can be re-used for the builder.
     */
    @NonNull
    private final List<Long> bookIdList = new ArrayList<>();

    /** Book title to use in FTS search query. */
    @Nullable
    private String ftsBookTitle;
    /** Series title to use in FTS search query. */
    @Nullable
    private String ftsSeriesTitle;
    /** Author to use in FTS search query. */
    @Nullable
    private String ftsAuthor;
    /** Publisher to use in FTS search query. */
    @Nullable
    private String ftsPublisher;
    /**
     * Keywords to use in FTS search query.
     * <p>
     * Always use {@link #setFtsKeywords(String)} as we need to intercept the "." character.
     */
    @Nullable
    private String ftsKeywords;
    /** Name of the person we lend books to, to use in search query. */
    @Nullable
    private String loanee;

    public LocalSearchCriteria() {
    }

    /**
     * {@link Parcelable} Constructor.
     *
     * @param in Parcel to construct the object from
     */
    private LocalSearchCriteria(@NonNull final Parcel in) {
        //noinspection deprecation
        in.readList(bookIdList, getClass().getClassLoader());
        ftsBookTitle = in.readString();
        ftsSeriesTitle = in.readString();
        ftsAuthor = in.readString();
        ftsPublisher = in.readString();
        ftsKeywords = in.readString();
        loanee = in.readString();
    }

    @Nullable
    public static LocalSearchCriteria fromBundle(@Nullable final Bundle args) {
        if (args == null) {
            return null;
        }
        //noinspection deprecation
        return args.getParcelable(BKEY);
    }

    @NonNull
    public Bundle toBundle() {
        final Bundle args = new Bundle(1);
        args.putParcelable(BKEY, this);
        return args;
    }

    @Override
    public void writeToParcel(@NonNull final Parcel dest,
                              final int flags) {
        dest.writeList(bookIdList);
        dest.writeString(ftsBookTitle);
        dest.writeString(ftsSeriesTitle);
        dest.writeString(ftsAuthor);
        dest.writeString(ftsPublisher);
        dest.writeString(ftsKeywords);
        dest.writeString(loanee);
    }

    @Override
    public int describeContents() {
        return 0;
    }

    /**
     * Clear all criteria.
     */
    public void clear() {
        bookIdList.clear();
        ftsBookTitle = null;
        ftsSeriesTitle = null;
        ftsAuthor = null;
        ftsPublisher = null;
        ftsKeywords = null;
        loanee = null;
    }

    @NonNull
    public List<Long> getBookIdList() {
        // used directly!
        return bookIdList;
    }

    public void setBookIdList(@Nullable final List<Long> bookIdList) {
        this.bookIdList.clear();
        if (bookIdList != null) {
            this.bookIdList.addAll(bookIdList);
        }
    }

    @Nullable
    public String getFtsBookTitle() {
        return ftsBookTitle;
    }

    void setFtsBookTitle(@Nullable final String ftsBookTitle) {
        this.ftsBookTitle = ftsBookTitle;
    }

    @Nullable
    public String getFtsSeriesTitle() {
        return ftsSeriesTitle;
    }

    void setFtsSeriesTitle(@Nullable final String ftsSeriesTitle) {
        this.ftsSeriesTitle = ftsSeriesTitle;
    }

    @Nullable
    public String getFtsAuthor() {
        return ftsAuthor;
    }

    void setFtsAuthor(@Nullable final String ftsAuthor) {
        this.ftsAuthor = ftsAuthor;
    }

    @Nullable
    public String getFtsPublisher() {
        return ftsPublisher;
    }

    void setFtsPublisher(@Nullable final String ftsPublisher) {
        this.ftsPublisher = ftsPublisher;
    }

    @Nullable
    public String getFtsKeywords() {
        return ftsKeywords;
    }

    public void setFtsKeywords(@Nullable final String keywords) {
        if (keywords == null || keywords.isEmpty() || ".".equals(keywords)) {
            ftsKeywords = null;
        } else {
            ftsKeywords = keywords.strip();
        }
    }

    /**
     * Not supported by FTS.
     *
     * @return the loanee name, or {@code null} if none set
     */
    @Nullable
    public String getLoanee() {
        return loanee;
    }

    @SuppressWarnings("unused")
    void setLoanee(@Nullable final String loanee) {
        this.loanee = loanee;
    }

    /**
     * Get a list with all search words, for displaying.
     *
     * @return the list of criteria; can be empty
     */
    @NonNull
    public List<String> getDisplayText() {
        final List<String> list = new ArrayList<>();

        if (ftsBookTitle != null && !ftsBookTitle.isEmpty()) {
            list.add(ftsBookTitle);
        }
        if (ftsSeriesTitle != null && !ftsSeriesTitle.isEmpty()) {
            list.add(ftsSeriesTitle);
        }
        if (ftsAuthor != null && !ftsAuthor.isEmpty()) {
            list.add(ftsAuthor);
        }
        if (ftsPublisher != null && !ftsPublisher.isEmpty()) {
            list.add(ftsPublisher);
        }
        if (ftsKeywords != null && !ftsKeywords.isEmpty()) {
            list.add(ftsKeywords);
        }
        if (loanee != null && !loanee.isEmpty()) {
            list.add(loanee);
        }

        return list;
    }

    /**
     * Check if there are <strong>any</strong> criteria set.
     *
     * @return {@code true} if there are no criteria set
     */
    public boolean isEmpty() {
        return bookIdList.isEmpty()
               && (ftsBookTitle == null || ftsBookTitle.isEmpty())
               && (ftsSeriesTitle == null || ftsSeriesTitle.isEmpty())
               && (ftsAuthor == null || ftsAuthor.isEmpty())
               && (ftsPublisher == null || ftsPublisher.isEmpty())
               && (ftsKeywords == null || ftsKeywords.isEmpty())
               && (loanee == null || loanee.isEmpty());
    }

    @Override
    @NonNull
    public String toString() {
        return "LocalSearchCriteria{"
               + "ftsBookTitle=`" + ftsBookTitle + '`'
               + ", ftsSeriesTitle=`" + ftsSeriesTitle + '`'
               + ", ftsAuthor=`" + ftsAuthor + '`'
               + ", ftsPublisher=`" + ftsPublisher + '`'
               + ", ftsKeywords=`" + ftsKeywords + '`'
               + ", loanee=`" + loanee + '`'
               + ", bookIdList=" + bookIdList
               + '}';
    }
}
