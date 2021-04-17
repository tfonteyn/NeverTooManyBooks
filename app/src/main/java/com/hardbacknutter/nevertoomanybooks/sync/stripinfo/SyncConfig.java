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
package com.hardbacknutter.nevertoomanybooks.sync.stripinfo;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.preference.PreferenceManager;

import com.hardbacknutter.nevertoomanybooks.entities.Bookshelf;
import com.hardbacknutter.nevertoomanybooks.settings.Prefs;

public class SyncConfig {

    /** The {@link Bookshelf} to which the wishlist is mapped. */
    public static final String PK_BOOKSHELF_WISHLIST =
            StripInfoAuth.PREF_KEY + ".bookshelf.wishlist";
    public static final String PK_BOOKSHELF_OWNED =
            StripInfoAuth.PREF_KEY + ".bookshelf.owned";

    @Nullable
    private static Bookshelf getBookshelf(@NonNull final Context context,
                                          @NonNull final String key) {
        final SharedPreferences global = PreferenceManager.getDefaultSharedPreferences(context);
        final int id = Prefs.getIntListPref(global, key, Bookshelf.DEFAULT);
        return Bookshelf.getBookshelf(context, id);
    }

    /**
     * Get the mapped bookshelf where to put "wanted" books on.
     *
     * @param context Current context
     *
     * @return Bookshelf, or {@code null} if not configured.
     */
    @Nullable
    Bookshelf getWishListBookshelf(@NonNull final Context context) {
        return getBookshelf(context, PK_BOOKSHELF_WISHLIST);
    }

    /**
     * Get the mapped bookshelf where to put "owned" books on.
     *
     * @param context Current context
     *
     * @return Bookshelf, or {@code null} if not configured.
     */
    @Nullable
    Bookshelf getOwnedBooksBookshelf(@NonNull final Context context) {
        return getBookshelf(context, PK_BOOKSHELF_OWNED);
    }
}
