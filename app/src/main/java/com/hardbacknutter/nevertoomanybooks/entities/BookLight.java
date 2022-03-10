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

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

import com.hardbacknutter.nevertoomanybooks.ServiceLocator;
import com.hardbacknutter.nevertoomanybooks.utils.dates.PartialDate;

/**
 * A 'light' Book object used where the full {@link Book} would be a performance penalty.
 */
public class BookLight
        implements AuthorWork, Entity {

    @NonNull
    private final String mTitle;
    @NonNull
    private final String mLanguage;
    @Nullable
    private final Author mPrimaryAuthor;
    @NonNull
    private final PartialDate mFirstPublicationDate;
    private long mId;

    /**
     * Constructor.
     *
     * @param id                   book id
     * @param title                Title
     * @param language             Language
     * @param primaryAuthor        Author of title
     * @param firstPublicationDate first publication
     */
    public BookLight(final long id,
                     @NonNull final String title,
                     @NonNull final String language,
                     @Nullable final Author primaryAuthor,
                     @Nullable final String firstPublicationDate) {
        mId = id;
        mTitle = title;
        mLanguage = language;
        mPrimaryAuthor = primaryAuthor;
        mFirstPublicationDate = new PartialDate(firstPublicationDate);
    }

    @Override
    @NonNull
    public Type getWorkType() {
        return AuthorWork.Type.BookLight;
    }

    @Override
    public long getId() {
        return mId;
    }

    public void setId(final long id) {
        mId = id;
    }

    /**
     * Get the <strong>unformatted</strong> title.
     * <p>
     * You probably want to call {@link #getLabel(Context)} instead.
     *
     * @return the title
     */
    @NonNull
    public String getTitle() {
        return mTitle;
    }

    @NonNull
    public List<BookLight> getBookTitles(@NonNull final Context context) {
        final List<BookLight> list = new ArrayList<>();
        list.add(this);
        return list;
    }

    @NonNull
    public Locale getLocale(@NonNull final Context context,
                            @NonNull final Locale defValue) {
        if (mLanguage.isEmpty()) {
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
        return getLabel(context, mTitle, () -> null);
    }

    @Override
    @Nullable
    public Author getPrimaryAuthor() {
        return mPrimaryAuthor;
    }

    @Override
    @NonNull
    public PartialDate getFirstPublicationDate() {
        return mFirstPublicationDate;
    }
}
