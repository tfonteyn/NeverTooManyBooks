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
package com.hardbacknutter.nevertoomanybooks.sync.stripinfo;

import android.content.Context;

import androidx.annotation.NonNull;

import java.util.Optional;

import com.hardbacknutter.nevertoomanybooks.core.utils.IntListPref;
import com.hardbacknutter.nevertoomanybooks.entities.Bookshelf;
import com.hardbacknutter.nevertoomanybooks.searchengines.EngineId;

/**
 * Helper class to determine mapping the stripinfo.be specific wishlist/owned flags
 * to a {@link Bookshelf}.
 */
public class BookshelfMapper {

    /** The {@link Bookshelf} to which the wishlist is mapped. */
    public static final String PK_BOOKSHELF_WISHLIST =
            EngineId.StripInfoBe.getPreferenceKey() + ".bookshelf.wishlist";
    /** The {@link Bookshelf} to which the owned-books list is mapped. */
    public static final String PK_BOOKSHELF_OWNED =
            EngineId.StripInfoBe.getPreferenceKey() + ".bookshelf.owned";

    @NonNull
    private static Optional<Bookshelf> getBookshelf(@NonNull final Context context,
                                                    @NonNull final String key) {
        final int id = IntListPref.getInt(context, key, 0);
        return id == 0 ? Optional.empty() : Bookshelf.getBookshelf(context, id);
    }

    /**
     * Get the mapped bookshelf where to put "wanted" books on.
     *
     * @param context Current context
     *
     * @return the wish-list Bookshelf if configured
     */
    @NonNull
    Optional<Bookshelf> getWishListBookshelf(@NonNull final Context context) {
        return getBookshelf(context, PK_BOOKSHELF_WISHLIST);
    }

    /**
     * Get the mapped bookshelf where to put "owned" books on.
     *
     * @param context Current context
     *
     * @return the owned-books Bookshelf if configured
     */
    @NonNull
    Optional<Bookshelf> getOwnedBooksBookshelf(@NonNull final Context context) {
        return getBookshelf(context, PK_BOOKSHELF_OWNED);
    }
}
