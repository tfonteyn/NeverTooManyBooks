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
import androidx.preference.PreferenceManager;

import java.util.HashMap;
import java.util.Locale;
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
        // ################## Plain hardcover ##################
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
        // german - Amazon
        MAPPINGS.put("gebundenes buch", R.string.book_format_hardcover);
        // portuguese
        MAPPINGS.put("capa dura", R.string.book_format_hardcover);
        // simplified chinese - Douban
        MAPPINGS.put("精装", R.string.book_format_hardcover);
        // czech
        MAPPINGS.put("pevná / vázaná", R.string.book_format_hardcover);
        MAPPINGS.put("pevná / vázaná s přebalem", R.string.book_format_hardcover);
        // Greek
        MAPPINGS.put("Σκληρό εξώφυλλο", R.string.book_format_hardcover);

        // ################## Plain paperback ##################

        MAPPINGS.put("mmpb", R.string.book_format_paperback);
        MAPPINGS.put("mass market paperback", R.string.book_format_paperback);
        MAPPINGS.put("pb", R.string.book_format_paperback);
        MAPPINGS.put("paperback", R.string.book_format_paperback);
        MAPPINGS.put("perfect paperback", R.string.book_format_paperback);
        MAPPINGS.put("pocket book", R.string.book_format_paperback);
        // dutch - KBNL
        MAPPINGS.put("pbk.", R.string.book_format_paperback);
        // french - BOL
        MAPPINGS.put("broché", R.string.book_format_paperback);
        // portuguese
        MAPPINGS.put("capa mole", R.string.book_format_paperback);
        // simplified chinese - Douban
        MAPPINGS.put("平装", R.string.book_format_paperback);
        // czech
        MAPPINGS.put("měkká / brožovaná", R.string.book_format_paperback);
        // Greek
        MAPPINGS.put("Μαλακό εξώφυλλο", R.string.book_format_paperback);


        // ################## Mid-size (a.k.a 'trade') format paperback ##################
        MAPPINGS.put("tp", R.string.book_format_paperback_large);
        MAPPINGS.put("tpb", R.string.book_format_paperback_large);


        // ################## Large format softcover ##################
        // dutch - stripinfo.be
        MAPPINGS.put("softcover", R.string.book_format_softcover);


        // ################## e-books ##################
        // Do not add "Kindle" to this list, leave it as-is!
        // The user will typically only use a single amazon site, so the
        // kindle-label will always be in their language already.
        MAPPINGS.put("ebook", R.string.book_format_ebook);
        MAPPINGS.put("e-book", R.string.book_format_ebook);
        // dutch - stripinfo.be
        MAPPINGS.put("digitaal", R.string.book_format_ebook);
        // french - BOL
        MAPPINGS.put("livre numérique", R.string.book_format_ebook);
        // simplified chinese - Douban
        MAPPINGS.put("电子图书", R.string.book_format_ebook);
        // czech
        MAPPINGS.put("ekniha", R.string.book_format_ebook);

        // ################## Audio-books ##################
        MAPPINGS.put("audiobook", R.string.book_format_audiobook);
        MAPPINGS.put("audio cassette", R.string.book_format_audiobook);
        MAPPINGS.put("audio cd", R.string.book_format_audiobook);
        MAPPINGS.put("cd", R.string.book_format_audiobook);
        // dutch - BOL
        MAPPINGS.put("digitaal luisterboek", R.string.book_format_audiobook);
        // french - BOL
        MAPPINGS.put("livre audio numérique", R.string.book_format_audiobook);
        // german
        MAPPINGS.put("hörbuch", R.string.book_format_audiobook);
        // portuguese
        MAPPINGS.put("audiolivro", R.string.book_format_audiobook);
        // spanish
        MAPPINGS.put("audiolibro", R.string.book_format_audiobook);
        // czech
        MAPPINGS.put("audiokniha", R.string.book_format_audiobook);


        // ################## Special ##################
        MAPPINGS.put("digest", R.string.book_format_digest);
        MAPPINGS.put("unknown", R.string.book_format_unknown);
        // english - GoogleBooks
        MAPPINGS.put("dimensions", R.string.book_format_dimensions);
        // goodreads... this is a weird one. Normally specific to children/baby books
        // it seems, but seen used on goodreads for hardcover comics.
        // Hoping we don't have users who collect the former...
        MAPPINGS.put("board book", R.string.book_format_hardcover);
    }

    /**
     * Constructor.
     *
     * @param locale Current Locale
     */
    @VisibleForTesting
    public FormatMapper(@NonNull final Locale locale) {
        super(DBKey.FORMAT, MAPPINGS, locale);
    }

    /**
     * Constructor.
     *
     * @param context Current context
     * @param locale  Current Locale
     *
     * @return instance
     */
    @NonNull
    static Optional<Mapper> create(@NonNull final Context context,
                                   @NonNull final Locale locale) {
        if (PreferenceManager.getDefaultSharedPreferences(context)
                             .getBoolean(Prefs.PK_SEARCH_REFORMAT_FORMAT, true)) {
            return Optional.of(new FormatMapper(locale));
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
