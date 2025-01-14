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

package com.hardbacknutter.nevertoomanybooks.database.dao;

import android.content.Context;

import androidx.annotation.IntRange;
import androidx.annotation.NonNull;

import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.function.Function;

import com.hardbacknutter.nevertoomanybooks.core.database.DaoInsertException;
import com.hardbacknutter.nevertoomanybooks.core.database.DaoUpdateException;
import com.hardbacknutter.nevertoomanybooks.entities.Tag;

public interface TagDao {

    @IntRange(from = 1)
    long insert(@NonNull Tag tag)
            throws DaoInsertException;

    void update(@NonNull Tag tag)
            throws DaoUpdateException;

    boolean delete(@NonNull Tag tag);

    /**
     * Get the list of all tags, ordered by name.
     *
     * @return list
     */
    @NonNull
    List<Tag> getList();

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
                      @NonNull Collection<Tag> list,
                      @NonNull Function<Tag, Locale> localeSupplier);

    void fixId(@NonNull Tag tag);

    void refresh(@NonNull Tag tag);

    void insertOrUpdate(@NonNull Context context,
                        @IntRange(from = 1) long bookId,
                        @NonNull Collection<Tag> list,
                        @NonNull Function<Tag, Locale> localeSupplier)
            throws DaoInsertException, DaoUpdateException;

    @NonNull
    Optional<Tag> findById(@IntRange(from = 1) long id);

    @NonNull
    Optional<Tag> findByName(@NonNull Tag tag);

    @NonNull
    Collection<Tag> getByBookId(@IntRange(from = 1) long bookId);
}
