/*
 * @Copyright 2018-2025 HardBackNutter
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
package com.hardbacknutter.nevertoomanybooks.sync.calibre;

import androidx.annotation.NonNull;

import java.util.HashMap;
import java.util.Map;

import com.hardbacknutter.nevertoomanybooks.database.DBKey;
import com.hardbacknutter.nevertoomanybooks.entities.Identifier;

final class CalibreIdentifier {

    /** Key is the remote (Calibre) identifier. */
    static final Map<String, CalibreIdentifier> MAP = new HashMap<>();

    static {
        create("isbn", DBKey.BOOK_ISBN);

        create("amazon", Identifier.SID_ASIN);
        create("asin", Identifier.SID_ASIN);

        create("bedetheque", Identifier.SID_BEDETHEQUE);
        create("bnf", Identifier.SID_BNF);
        create("bl", Identifier.SID_BRITISH_LIBRARY);
        create("dnb", Identifier.SID_DNB);
        create("doi", Identifier.SID_DOI);
        create("douban", Identifier.SID_DOUBAN);
        create("goodreads", Identifier.SID_GOODREADS_BOOK);
        create("google", Identifier.SID_GOOGLE);
        create("isfdb", Identifier.SID_ISFDB);
        create("ppn", Identifier.SID_KBNL);
        create("lastdodo", Identifier.SID_LAST_DODO_NL);
        create("lccn", Identifier.SID_LCCN);
        create("librarything", Identifier.SID_LIBRARY_THING);
        create("mobi-asin", Identifier.SID_MOBI_ASIN);
        create("oclc", Identifier.SID_OCLC);
        create("openlibrary", Identifier.SID_OPEN_LIBRARY);
        create("stripinfo", Identifier.SID_STRIP_INFO);
        create("uri", Identifier.SID_URI);
    }

    @NonNull
    private final String remote;
    @NonNull
    private final String local;

    private CalibreIdentifier(@NonNull final String remote,
                              @NonNull final String local) {
        this.remote = remote;
        this.local = local;
    }

    private static void create(@NonNull final String remote,
                               @NonNull final String local) {
        final CalibreIdentifier ci = new CalibreIdentifier(remote, local);
        MAP.put(ci.remote, ci);
    }

    @NonNull
    public String getRemote() {
        return remote;
    }

    @NonNull
    public String getLocal() {
        return local;
    }
}
