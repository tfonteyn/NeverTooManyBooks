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
package com.hardbacknutter.nevertoomanybooks.database.dao;

import android.content.Context;
import android.database.Cursor;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringDef;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.function.Function;

import com.hardbacknutter.nevertoomanybooks.database.DBKey;
import com.hardbacknutter.nevertoomanybooks.entities.Author;
import com.hardbacknutter.nevertoomanybooks.entities.AuthorWork;
import com.hardbacknutter.nevertoomanybooks.entities.TocEntry;

@SuppressWarnings("UnusedReturnValue")
public interface AuthorDao
        extends EntityBookLinksDao<Author> {

    /**
     * Get a unique list of {@link Author} names in the specified format.
     *
     * @param key type of name wanted, one of
     *            {@link DBKey#AUTHOR_FAMILY_NAME},
     *            {@link DBKey#AUTHOR_GIVEN_NAMES},
     *            {@link DBKey#AUTHOR_FORMATTED},
     *            {@link DBKey#AUTHOR_FORMATTED_GIVEN_FIRST}
     *
     * @return list of all author names.
     */
    @NonNull
    List<String> getNames(@NonNull String key);

    /**
     * Get all Authors; mainly for the purpose of exports.
     *
     * @return Cursor over all Authors
     */
    @NonNull
    Cursor fetchAll();

    /**
     * Remove duplicates. We keep the first occurrence.
     *
     * @param context        Current context
     * @param list           List to clean up
     * @param localeSupplier deferred supplier for a {@link Locale}.
     *
     * @return {@code true} if the list was modified.
     */
    boolean pruneList(@NonNull Context context,
                      @NonNull Collection<Author> list,
                      @NonNull Function<Author, Locale> localeSupplier);

    /**
     * Return all the {@link AuthorWork} for the given {@link Author}.
     *
     * @param author         to retrieve
     * @param bookshelfId    limit the list to books on this shelf (pass -1 for all shelves)
     * @param withTocEntries add the toc entries
     * @param withBooks      add books without TOC as well; i.e. the toc of a book without a toc,
     *                       is the book title itself. (makes sense?)
     * @param orderBy        {@code null} for the default, or one of {@link WorksOrderBy}
     *
     * @return List of {@link AuthorWork} for this {@link Author}
     */
    @NonNull
    List<AuthorWork> getAuthorWorks(@NonNull Author author,
                                    long bookshelfId,
                                    boolean withTocEntries,
                                    boolean withBooks,
                                    @WorksOrderBy @Nullable String orderBy);

    /**
     * Count the {@link TocEntry}'s for the given {@link Author}.
     *
     * @param context    Current context
     * @param author     to count the TocEntries of
     * @param bookLocale Locale to use if the item has none set
     *
     * @return the number of {@link TocEntry} this {@link Author} has
     */
    long countTocEntries(@NonNull Context context,
                         @NonNull Author author,
                         @NonNull Locale bookLocale);

    /**
     * Update the 'complete' status for the given {@link Author}.
     * <p>
     * If successful, the author object will have been updated with the new status.
     *
     * @param author   to update
     * @param complete Flag indicating the user considers this item to be 'complete'
     *
     * @return {@code true} for success.
     */
    boolean setComplete(@NonNull Author author,
                        boolean complete);

    @StringDef({
            DBKey.TITLE_OB,
            DBKey.FIRST_PUBLICATION__DATE
    })
    @Retention(RetentionPolicy.SOURCE)
    @interface WorksOrderBy {

    }
}
