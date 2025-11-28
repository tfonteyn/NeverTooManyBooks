/*
 * @Copyright 2018-2025 HardBackNutter
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
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;

import com.hardbacknutter.nevertoomanybooks.ServiceLocator;
import com.hardbacknutter.nevertoomanybooks.booklist.style.Style;
import com.hardbacknutter.nevertoomanybooks.core.parsers.PartialDateParser;
import com.hardbacknutter.nevertoomanybooks.core.utils.LocaleListUtils;
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
        // FIXME: optimize this by moving the PartialDateParser to the caller
        this.firstPublicationDate = new PartialDateParser()
                .parse(rowData.getString(DBKey.FIRST_PUBLICATION_DATE))
                .orElse(PartialDate.NOT_SET);
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

    /**
     * Get the Book's Locale (based on its language).
     *
     * @param context Current context
     *
     * @return the Locale, or the users preferred Locale if no language was set.
     */
    @NonNull
    public Optional<Locale> getLocale(@NonNull final Context context) {
        if (language.isEmpty()) {
            return Optional.empty();
        } else {
            final Locale userLocale = context.getResources().getConfiguration().getLocales().get(0);
            return ServiceLocator.getInstance().getAppLocale().getLocale(language, userLocale);
        }
    }

    @Override
    @NonNull
    public String getLabel(@NonNull final Context context,
                           @Nullable final Details details,
                           @NonNull final Style style) {

        if (style.isShowReorderedTitle()) {
            final ReorderHelper reorderHelper = new ReorderHelper(
                    LocaleListUtils.asList(context.getResources().getConfiguration().getLocales()));
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
    public List<Author> getAuthors() {
        return Collections.singletonList(primaryAuthor);
    }

    @Override
    @NonNull
    public PartialDate getFirstPublicationDate() {
        return firstPublicationDate;
    }

    @Override
    public boolean equals(@Nullable final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final BookLight that = (BookLight) o;
        // if both 'exist' but have different ID's -> different.
        if (id != 0 && that.id != 0 && id != that.id) {
            return false;
        }

        // The ids MAY be different, but at least one is != 0
        return Objects.equals(title, that.title)
               && Objects.equals(language, that.language)
               && Objects.equals(primaryAuthor, that.primaryAuthor)
               && Objects.equals(firstPublicationDate, that.firstPublicationDate);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, title, language, primaryAuthor, firstPublicationDate);
    }

    @Override
    @NonNull
    public String toString() {
        return "BookLight{"
               + "id=" + id
               + ", title=`" + title + '`'
               + ", language=`" + language + '`'
               + ", primaryAuthor=" + primaryAuthor
               + ", firstPublicationDate=" + firstPublicationDate
               + '}';
    }
}
