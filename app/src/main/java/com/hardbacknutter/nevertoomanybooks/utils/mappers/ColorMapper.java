/*
 * @Copyright 2018-2024 HardBackNutter
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
package com.hardbacknutter.nevertoomanybooks.utils.mappers;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.annotation.VisibleForTesting;
import androidx.preference.PreferenceManager;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import com.hardbacknutter.nevertoomanybooks.R;
import com.hardbacknutter.nevertoomanybooks.database.DBKey;
import com.hardbacknutter.nevertoomanybooks.entities.Book;
import com.hardbacknutter.nevertoomanybooks.settings.Prefs;

/**
 * System wide book color representation. Color is mainly meant for comics.
 */
public final class ColorMapper
        extends MapperBase {

    /** Maps site color terminology to our own. */
    private static final Map<String, Integer> MAPPINGS = new HashMap<>();

    // use all lowercase keys!
    static {
        // stripinfo.be
        MAPPINGS.put("kleur", R.string.book_color_full_color);
        MAPPINGS.put("zwart/wit", R.string.book_color_black_and_white);
        MAPPINGS.put("zwart/wit met steunkleur", R.string.book_color_support_color);

        // lastdodo.nl
        MAPPINGS.put("gekleurd", R.string.book_color_full_color);
        MAPPINGS.put("ongekleurd", R.string.book_color_black_and_white);
        // Based on the term used in "Suske en Wiske Tweekleuren reeks"
        MAPPINGS.put("gedeeltelijk gekleurd", R.string.book_color_support_color);

        // bedetheque
        MAPPINGS.put("n&b", R.string.book_color_black_and_white);
        MAPPINGS.put("monochromie", R.string.book_color_black_and_white);
        // B&W with 1 or 2 support colors
        MAPPINGS.put("bichromie", R.string.book_color_support_color);
        // extremely seldom used; map the same as "Bichromie"
        MAPPINGS.put("trichromie", R.string.book_color_support_color);
        // extremely seldom used; map as full-color
        MAPPINGS.put("quadrichromie", R.string.book_color_full_color);

        // kbnl
        // As usual on this site, the data is unstructured... we do our best
        MAPPINGS.put("gekleurde illustraties", R.string.book_color_full_color);
        MAPPINGS.put("gekleurde ill", R.string.book_color_full_color);
        MAPPINGS.put("blauw-witte illustraties", R.string.book_color_black_and_white);
        MAPPINGS.put("ill", R.string.book_color_black_and_white);
        MAPPINGS.put("zw. ill", R.string.book_color_black_and_white);
        MAPPINGS.put("w. tek", R.string.book_color_black_and_white);
        MAPPINGS.put("ill.(zw./w.)", R.string.book_color_black_and_white);
    }

    /**
     * Constructor.
     */
    @VisibleForTesting
    public ColorMapper() {
        super(DBKey.COLOR, MAPPINGS);
    }

    @NonNull
    public static Optional<Mapper> create(@NonNull final Context context) {
        if (PreferenceManager.getDefaultSharedPreferences(context)
                             .getBoolean(Prefs.PK_SEARCH_REFORMAT_COLOR, true)) {
            return Optional.of(new ColorMapper());
        } else {
            return Optional.empty();
        }
    }

    @Override
    public void map(@NonNull final Context context,
                    @NonNull final Book book) {
        mapString(context, book);
    }
}
