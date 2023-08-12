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
package com.hardbacknutter.nevertoomanybooks.utils.mappers;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.preference.PreferenceManager;

import com.hardbacknutter.nevertoomanybooks.R;
import com.hardbacknutter.nevertoomanybooks.database.DBKey;
import com.hardbacknutter.nevertoomanybooks.settings.Prefs;

/**
 * System wide book color representation. Color is mainly meant for comics.
 * <p>
 * Note that the KBNL SearchEngine does it's own mapping.
 */
public final class ColorMapper
        extends MapperBase {

    // use all lowercase keys!
    static {
        // stripinfo.be
        MAPPER.put("kleur", R.string.book_color_full_color);
        MAPPER.put("zwart/wit", R.string.book_color_black_and_white);
        MAPPER.put("Zwart/wit met steunkleur", R.string.book_color_support_color);

        // lastdodo.nl
        MAPPER.put("gekleurd", R.string.book_color_full_color);
        MAPPER.put("ongekleurd", R.string.book_color_black_and_white);

        // bedetheque
        MAPPER.put("N&B", R.string.book_color_black_and_white);
        MAPPER.put("Monochromie", R.string.book_color_black_and_white);
        // B&W with 1 or 2 support colors
        MAPPER.put("Bichromie", R.string.book_color_support_color);
        // extremely seldom used; we'll set to same as "Bichromie"
        MAPPER.put("Trichromie", R.string.book_color_support_color);
        MAPPER.put("Quadrichromie", R.string.book_color_full_color);

    }
    @NonNull
    @Override
    public String getKey() {
        return DBKey.COLOR;
    }

    public static boolean isMappingAllowed(@NonNull final Context context) {
        return PreferenceManager.getDefaultSharedPreferences(context)
                                .getBoolean(Prefs.pk_search_reformat_color, true);
    }
}