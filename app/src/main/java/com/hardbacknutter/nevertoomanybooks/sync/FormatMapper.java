/*
 * @Copyright 2018-2022 HardBackNutter
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
package com.hardbacknutter.nevertoomanybooks.sync;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.preference.PreferenceManager;

import com.hardbacknutter.nevertoomanybooks.R;
import com.hardbacknutter.nevertoomanybooks.database.DBKey;
import com.hardbacknutter.nevertoomanybooks.settings.Prefs;

/**
 * System wide book format representation.
 * <p>
 * Good description at
 * <a href="http://www.isfdb.org/wiki/index.php/Help:Screen:NewPub#Format">isfdb</a>
 */
public final class FormatMapper
        extends MapperBase {

    // use all lowercase keys!
    static {
        // ISFDB
        // mass market paperback
        MAPPER.put("mmpb", R.string.book_format_paperback);
        MAPPER.put("pb", R.string.book_format_paperback);
        MAPPER.put("tp", R.string.book_format_paperback_large);
        MAPPER.put("hc", R.string.book_format_hardcover);
        MAPPER.put("ebook", R.string.book_format_ebook);
        MAPPER.put("digest", R.string.book_format_digest);
        MAPPER.put("audio cassette", R.string.book_format_audiobook);
        MAPPER.put("audio cd", R.string.book_format_audiobook);
        MAPPER.put("unknown", R.string.unknown);

        MAPPER.put("mass market paperback", R.string.book_format_paperback);
        MAPPER.put("paperback", R.string.book_format_paperback);
        MAPPER.put("hardcover", R.string.book_format_hardcover);

        // GoogleBooks, not already listed above.
        MAPPER.put("dimensions", R.string.book_format_dimensions);

        // KBNL, not already listed above.
        MAPPER.put("geb.", R.string.book_format_hardcover);
        MAPPER.put("gebonden", R.string.book_format_hardcover);

        // stripinfo.be
        MAPPER.put("softcover", R.string.book_format_softcover);
        MAPPER.put("digitaal", R.string.book_format_ebook);

        // bedetheque
        MAPPER.put("Couverture souple", R.string.book_format_softcover);
        MAPPER.put("format normal", R.string.book_format_hardcover);
        MAPPER.put("format grand", R.string.book_format_hardcover);
        MAPPER.put("format poche", R.string.book_format_paperback);


        // Others as seen in the wild
        MAPPER.put("hardback", R.string.book_format_hardcover);
    }

    public static boolean isMappingAllowed(@NonNull final Context context) {
        return PreferenceManager.getDefaultSharedPreferences(context)
                                .getBoolean(Prefs.pk_search_reformat_format, true);
    }

    @NonNull
    @Override
    public String getKey() {
        return DBKey.FORMAT;
    }
}
