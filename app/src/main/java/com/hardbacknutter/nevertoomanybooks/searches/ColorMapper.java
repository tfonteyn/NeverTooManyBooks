/*
 * @Copyright 2019 HardBackNutter
 * @License GNU General Public License
 *
 * This file is part of NeverTooManyBooks.
 *
 * In August 2018, this project was forked from:
 * Book Catalogue 5.2.2 @2016 Philip Warner & Evan Leybourn
 *
 * Without their original creation, this project would not exist in its
 * current form. It was however largely rewritten/refactored and any
 * comments on this fork should be directed at HardBackNutter and not
 * at the original creators.
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
package com.hardbacknutter.nevertoomanybooks.searches;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.preference.PreferenceManager;

import com.hardbacknutter.nevertoomanybooks.R;
import com.hardbacknutter.nevertoomanybooks.database.DBDefinitions;
import com.hardbacknutter.nevertoomanybooks.settings.Prefs;

/**
 * System wide book color representation. Color is mainly meant for comics.
 */
public final class ColorMapper
        extends TerminologyMapperBase {

    // use all lowercase keys!
    static {
        // stripinfo.be
        MAPPER.put("kleur", R.string.book_color_full_color);
        MAPPER.put("zwart/wit", R.string.book_color_uncolored);
        MAPPER.put("Zwart/wit met steunkleur", R.string.book_color_support_color);
    }

    /**
     * Constructor.
     *
     * @param context Current context
     */
    ColorMapper(@NonNull final Context context) {
        super(context);
    }

    @Override
    public String getKey() {
        return DBDefinitions.KEY_COLOR;
    }

    static boolean isMappingAllowed(@NonNull final Context context) {
        return PreferenceManager.getDefaultSharedPreferences(context)
                                .getBoolean(Prefs.pk_search_reformat_color, false);
    }

}
