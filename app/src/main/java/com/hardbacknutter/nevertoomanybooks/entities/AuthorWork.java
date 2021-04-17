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

import com.hardbacknutter.nevertoomanybooks.database.dao.AuthorDao;
import com.hardbacknutter.nevertoomanybooks.utils.dates.PartialDate;

/**
 * Abstracts/shield a {@link Book} / {@link TocEntry} for use in a list
 * of works by an {@link Author}. i.e. {@link AuthorDao#getAuthorWorks}.
 */
public interface AuthorWork {

    /** As used by the DAO. */
    char TYPE_TOC = 'T';
    /** As used by the DAO. */
    char TYPE_BOOK = 'B';

    /**
     * Get the type of this entry.
     *
     * @return type
     */
    char getType();

    /**
     * Get the database row id of the entity.
     *
     * @return id
     */
    long getId();

    void setId(long id);

    /**
     * Get the formatted title.
     *
     * @param context Current context
     *
     * @return formatted title
     */
    @NonNull
    String getLabel(@NonNull Context context);

    @NonNull
    PartialDate getFirstPublicationDate();

    @Nullable
    Author getPrimaryAuthor();

    default int getBookCount() {
        return 1;
    }
}
