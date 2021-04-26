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
package com.hardbacknutter.nevertoomanybooks.entities;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.Locale;

import com.hardbacknutter.nevertoomanybooks.database.dao.AuthorDao;
import com.hardbacknutter.nevertoomanybooks.utils.dates.PartialDate;

/**
 * Fakes a Book with just enough information to implement an {@link AuthorWork}.
 * This is a transitive solution.
 * <p>
 * See {@link AuthorDao#getAuthorWorks}.
 */
public class BookAsWork
        implements ItemWithTitle, AuthorWork {

    @NonNull
    private final Author mAuthor;
    @NonNull
    private final String mTitle;
    @NonNull
    private final PartialDate mFirstPublicationDate;
    /** Row ID. */
    private long mId;

    /**
     * Constructor.
     *
     * @param id                   row id
     * @param author               Author of title
     * @param title                Title
     * @param firstPublicationDate first publication
     */
    public BookAsWork(final long id,
                      @NonNull final Author author,
                      @NonNull final String title,
                      @Nullable final String firstPublicationDate) {
        mId = id;
        mAuthor = author;
        mTitle = title;
        mFirstPublicationDate = new PartialDate(firstPublicationDate);
    }

    @Override
    public char getType() {
        return AuthorWork.TYPE_BOOK;
    }

    @Override
    public long getId() {
        return mId;
    }

    @Override
    public void setId(final long id) {
        mId = id;
    }

    @NonNull
    @Override
    public String getTitle() {
        return mTitle;
    }

    @NonNull
    @Override
    public String getLabel(@NonNull final Context context) {
        final Locale userLocale = context.getResources().getConfiguration().getLocales().get(0);
        return reorderTitleForDisplaying(context, userLocale);
    }

    @NonNull
    @Override
    public PartialDate getFirstPublicationDate() {
        return mFirstPublicationDate;
    }

    @Nullable
    @Override
    public Author getPrimaryAuthor() {
        return mAuthor;
    }
}
