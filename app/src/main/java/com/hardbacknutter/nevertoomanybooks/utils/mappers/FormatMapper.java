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
 * System wide book format representation.
 * <p>
 * Good description at
 * <a href="http://www.isfdb.org/wiki/index.php/Help:Screen:NewPub#Format">isfdb</a>
 * <p>
 * Some search engines will do their own mapping.
 */
public final class FormatMapper
        extends MapperBase {

    /** Maps site format terminology to our own. */
    private static final Map<String, Integer> MAPPINGS = new HashMap<>();

    // use all lowercase keys!
    static {
        // ### Plain hardcover ###
        MAPPINGS.put("hc", R.string.book_format_hardcover);
        MAPPINGS.put("hardcover", R.string.book_format_hardcover);
        MAPPINGS.put("hardback", R.string.book_format_hardcover);
        // dutch - KBNL
        MAPPINGS.put("geb.", R.string.book_format_hardcover);
        // dutch - KBNL
        MAPPINGS.put("gebonden", R.string.book_format_hardcover);
        // french - BOL
        MAPPINGS.put("couverture rigide", R.string.book_format_hardcover);
        // french - Stripweb
        MAPPINGS.put("relié", R.string.book_format_hardcover);

        // portuguese
        MAPPINGS.put("capa dura", R.string.book_format_hardcover);


        // ### Plain paperback ###

        MAPPINGS.put("mmpb", R.string.book_format_paperback);
        MAPPINGS.put("mass market paperback", R.string.book_format_paperback);
        MAPPINGS.put("pb", R.string.book_format_paperback);
        MAPPINGS.put("paperback", R.string.book_format_paperback);
        // dutch - KBNL
        MAPPINGS.put("pbk.", R.string.book_format_paperback);
        // french - BOL
        MAPPINGS.put("broché", R.string.book_format_paperback);
        // portuguese
        MAPPINGS.put("capa mole", R.string.book_format_paperback);

        // ### Mid-size (a.k.a 'trade') format paperback ###
        MAPPINGS.put("tp", R.string.book_format_paperback_large);
        MAPPINGS.put("tpb", R.string.book_format_paperback_large);


        // ### Large format softcover ###
        // dutch - stripinfo.be
        MAPPINGS.put("softcover", R.string.book_format_softcover);


        // ### e-books ###
        MAPPINGS.put("ebook", R.string.book_format_ebook);
        MAPPINGS.put("e-book", R.string.book_format_ebook);
        // dutch - stripinfo.be
        MAPPINGS.put("digitaal", R.string.book_format_ebook);
        // french - BOL
        MAPPINGS.put("livre numérique", R.string.book_format_ebook);


        // ### Audio-books ###
        MAPPINGS.put("audio cassette", R.string.book_format_audiobook);
        MAPPINGS.put("audio cd", R.string.book_format_audiobook);
        // dutch - BOL
        MAPPINGS.put("digitaal luisterboek", R.string.book_format_audiobook);
        // french - BOL
        MAPPINGS.put("livre audio numérique", R.string.book_format_audiobook);
        // portuguese
        MAPPINGS.put("audiolivro", R.string.book_format_audiobook);


        // ### Special ###
        MAPPINGS.put("digest", R.string.book_format_digest);
        MAPPINGS.put("unknown", R.string.unknown);
        // english - GoogleBooks
        MAPPINGS.put("dimensions", R.string.book_format_dimensions);
    }

    /**
     * Constructor.
     */
    @VisibleForTesting
    public FormatMapper() {
        super(DBKey.FORMAT, MAPPINGS);
    }

    @NonNull
    public static Optional<Mapper> create(@NonNull final Context context) {
        if (PreferenceManager.getDefaultSharedPreferences(context)
                             .getBoolean(Prefs.pk_search_reformat_format, true)) {
            return Optional.of(new FormatMapper());
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
