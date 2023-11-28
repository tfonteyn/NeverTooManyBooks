/*
 * @Copyright 2018-2023 HardBackNutter
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
import java.util.Optional;

import com.hardbacknutter.nevertoomanybooks.ServiceLocator;
import com.hardbacknutter.nevertoomanybooks.booklist.style.Style;
import com.hardbacknutter.nevertoomanybooks.core.utils.PartialDate;
import com.hardbacknutter.nevertoomanybooks.database.DBKey;
import com.hardbacknutter.nevertoomanybooks.utils.ReorderHelper;

/**
 * A 'light' Book object used where the full {@link Book} would be a performance penalty.
 */
public class BookLight
        implements AuthorWork, Entity {

    @NonNull
    private final String title;
    @NonNull
    private final String language;
    @Nullable
    private final Author primaryAuthor;
    @NonNull
    private final PartialDate firstPublicationDate;
    private long id;

    /**
     * Constructor.
     *
     * @param book to use
     */
    public BookLight(@NonNull final Book book) {
        this.id = book.getId();
        this.title = book.getTitle();
        this.language = book.getString(DBKey.LANGUAGE);
        this.primaryAuthor = book.getPrimaryAuthor();
        this.firstPublicationDate = book.getFirstPublicationDate();
    }

    /**
     * Constructor.
     *
     * @param id            book id
     * @param primaryAuthor Author of title
     * @param rowData       with data
     */
    public BookLight(final long id,
                     @Nullable final Author primaryAuthor,
                     @NonNull final DataHolder rowData) {
        this.id = id;
        this.title = rowData.getString(DBKey.TITLE);
        this.language = rowData.getString(DBKey.LANGUAGE);
        this.primaryAuthor = primaryAuthor;
        this.firstPublicationDate = new PartialDate(
                rowData.getString(DBKey.FIRST_PUBLICATION__DATE));
    }

    @Override
    @NonNull
    public Type getWorkType() {
        return AuthorWork.Type.BookLight;
    }

    @Override
    public long getId() {
        return id;
    }

    /**
     * Set the database row id of the Entity.
     *
     * @param id to set
     */
    public void setId(final long id) {
        this.id = id;
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
        return title;
    }

    @NonNull
    public List<BookLight> getBookTitles(@NonNull final Context context) {
        final List<BookLight> list = new ArrayList<>();
        list.add(this);
        return list;
    }

    @NonNull
    public Optional<Locale> getLocale(@NonNull final Context context) {
        if (language.isEmpty()) {
            return Optional.empty();
        } else {
            return ServiceLocator.getInstance().getAppLocale().getLocale(context, language);
        }
    }

    @Override
    @NonNull
    public String getLabel(@NonNull final Context context,
                           @Nullable final Details details,
                           @NonNull final Style style) {

        if (style.isShowReorderedTitle()) {
            final ReorderHelper reorderHelper = ServiceLocator.getInstance().getReorderHelper();
            return reorderHelper.reorder(context, title);
        } else {
            return title;
        }
    }

    @Override
    @Nullable
    public Author getPrimaryAuthor() {
        return primaryAuthor;
    }

    @Override
    @NonNull
    public PartialDate getFirstPublicationDate() {
        return firstPublicationDate;
    }
}
