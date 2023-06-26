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
 * System wide book format representation.
 * <p>
 * Good description at
 * <a href="http://www.isfdb.org/wiki/index.php/Help:Screen:NewPub#Format">isfdb</a>
 * <p>
 * Some search engines will do their own mapping.
 */
public final class FormatMapper
        extends MapperBase {

    // use all lowercase keys!
    static {
        // ### Plain hardcover ###
        MAPPER.put("hc", R.string.book_format_hardcover);
        MAPPER.put("hardcover", R.string.book_format_hardcover);
        MAPPER.put("hardback", R.string.book_format_hardcover);
        // dutch - KBNL
        MAPPER.put("geb.", R.string.book_format_hardcover);
        // dutch - KBNL
        MAPPER.put("gebonden", R.string.book_format_hardcover);
        // french - BOL
        MAPPER.put("couverture rigide", R.string.book_format_hardcover);
        // portuguese
        MAPPER.put("capa dura", R.string.book_format_hardcover);


        // ### Plain paperback ###

        MAPPER.put("mmpb", R.string.book_format_paperback);
        MAPPER.put("mass market paperback", R.string.book_format_paperback);
        MAPPER.put("pb", R.string.book_format_paperback);
        MAPPER.put("paperback", R.string.book_format_paperback);
        // dutch - KBNL
        MAPPER.put("pbk.", R.string.book_format_paperback);
        // french - BOL
        MAPPER.put("broché", R.string.book_format_paperback);
        // portuguese
        MAPPER.put("capa mole", R.string.book_format_paperback);

        // ### Mid-size (a.k.a 'trade') format paperback ###
        MAPPER.put("tp", R.string.book_format_paperback_large);


        // ### Large format softcover ###
        // dutch - stripinfo.be
        MAPPER.put("softcover", R.string.book_format_softcover);


        // ### e-books ###
        MAPPER.put("ebook", R.string.book_format_ebook);
        MAPPER.put("e-book", R.string.book_format_ebook);
        // dutch - stripinfo.be
        MAPPER.put("digitaal", R.string.book_format_ebook);
        // french - BOL
        MAPPER.put("livre numérique", R.string.book_format_ebook);


        // ### Audio-books ###
        MAPPER.put("audio cassette", R.string.book_format_audiobook);
        MAPPER.put("audio cd", R.string.book_format_audiobook);
        // dutch - BOL
        MAPPER.put("digitaal luisterboek", R.string.book_format_audiobook);
        // french - BOL
        MAPPER.put("livre audio numérique", R.string.book_format_audiobook);
        // portuguese
        MAPPER.put("audiolivro", R.string.book_format_audiobook);


        // ### Special ###
        MAPPER.put("digest", R.string.book_format_digest);
        MAPPER.put("unknown", R.string.unknown);
        // english - GoogleBooks
        MAPPER.put("dimensions", R.string.book_format_dimensions);
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
