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
import java.util.Objects;

import com.hardbacknutter.nevertoomanybooks.ServiceLocator;
import com.hardbacknutter.nevertoomanybooks.utils.dates.PartialDate;

/**
 * A 'light' Book object used where the full {@link Book} would be a performance penalty.
 */
public class BookLight
        implements AuthorWork, ReorderTitle {

    @Nullable
    private final String mLanguage;
    @NonNull
    private final PartialDate mFirstPublicationDate;
    @Nullable
    private final Author mAuthor;
    private long mId;
    @NonNull
    private String mTitle;

    /**
     * Constructor.
     *
     * @param id       book id
     * @param title    Title
     * @param language Language
     */
    public BookLight(final long id,
                     @NonNull final String title,
                     @Nullable final String language) {
        this(id, title, language, null, null);
    }

    /**
     * Constructor.
     *
     * @param id                   book id
     * @param title                Title
     * @param language             Language
     * @param firstPublicationDate first publication
     * @param author               Author of title
     */
    public BookLight(final long id,
                     @NonNull final String title,
                     @Nullable final String language,
                     @Nullable final String firstPublicationDate,
                     @Nullable final Author author) {
        mId = id;
        mTitle = title;
        mLanguage = language;
        mAuthor = author;
        mFirstPublicationDate = new PartialDate(firstPublicationDate);
    }

    @Override
    public char getWorkType() {
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

    @Override
    @NonNull
    public String getTitle() {
        return mTitle;
    }

    public void setTitle(@NonNull final String title) {
        mTitle = title;
    }

    @Override
    @NonNull
    public Locale getLocale(@NonNull final Context context,
                            @NonNull final Locale defValue) {
        if (mLanguage == null || mLanguage.isEmpty()) {
            return defValue;
        } else {
            final Locale locale = ServiceLocator.getInstance().getAppLocale()
                                                .getLocale(context, mLanguage);
            return Objects.requireNonNullElse(locale, defValue);
        }
    }

    @Override
    @NonNull
    public String getLabel(@NonNull final Context context) {
        if (ReorderTitle.forDisplay(context)) {
            return reorder(context);
        } else {
            return getTitle();
        }
    }

    @Override
    @Nullable
    public Author getPrimaryAuthor() {
        return mAuthor;
    }

    @Override
    @NonNull
    public PartialDate getFirstPublicationDate() {
        return mFirstPublicationDate;
    }
}
