/*
 * @Copyright 2018-2026 HardBackNutter
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

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

import com.hardbacknutter.nevertoomanybooks.R;
import com.hardbacknutter.nevertoomanybooks.ServiceLocator;
import com.hardbacknutter.nevertoomanybooks.database.DBKey;
import com.hardbacknutter.nevertoomanybooks.entities.Book;

/**
 * System-wide book colour representation. Colour is mainly meant for comics.
 */
public final class ColorMapper
        extends MapperBase {

    /**
     * Whether to normalise {@link DBKey#COLOR} values after a search.
     * <p>
     * {@code boolean}
     */
    @VisibleForTesting
    public static final String PK_SEARCH_REFORMAT_COLOR = "search.reformat.color";

    /** Maps site colour terminology to our own. */
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
        // B&W with 1 or 2 support colours
        MAPPINGS.put("bichromie", R.string.book_color_support_color);
        // extremely seldom used; map the same as "Bichromie"
        MAPPINGS.put("trichromie", R.string.book_color_support_color);
        // extremely seldom used; map as full-colour
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
     *
     * @param locale Current Locale
     */
    @VisibleForTesting
    public ColorMapper(@NonNull final Locale locale) {
        super(DBKey.COLOR, MAPPINGS, locale);
    }

    /**
     * Constructor.
     *
     * @param locale Current Locale
     *
     * @return instance
     */
    @NonNull
    static Optional<Mapper> create(@NonNull final Locale locale) {
        if (ServiceLocator.getInstance().getSharedPreferences()
                          .getBoolean(PK_SEARCH_REFORMAT_COLOR, true)) {
            return Optional.of(new ColorMapper(locale));
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
