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
package com.hardbacknutter.nevertoomanybooks.sync.calibre;

import androidx.annotation.NonNull;

import java.util.HashMap;
import java.util.Map;

import com.hardbacknutter.nevertoomanybooks.database.DBKey;
import com.hardbacknutter.nevertoomanybooks.entities.Identifier;

public final class CalibreIdentifier {

    static final String AMAZON = "amazon";
    /** Key is the remote (Calibre) identifier. */
    static final Map<String, CalibreIdentifier> MAP = new HashMap<>();
    private static final String BEDETHEQUE = "bedetheque";
    private static final String GOODREADS = "goodreads";
    private static final String GOOGLE = "google";
    private static final String ISBN = "isbn";
    private static final String ISFDB = "isfdb";
    private static final String LASTDODO = "lastdodo";
    private static final String LCCN = "lccn";
    private static final String LIBRARYTHING = "librarything";
    private static final String OCLC = "oclc";
    private static final String OPENLIBRARY = "openlibrary";
    private static final String STRIPINFO = "stripinfo";

    static {
        CalibreIdentifier cId;

        cId = new CalibreIdentifier(ISBN, DBKey.BOOK_ISBN);
        MAP.put(cId.remote, cId);

        cId = new CalibreIdentifier(AMAZON, Identifier.SID_ASIN);
        MAP.put(cId.remote, cId);
        cId = new CalibreIdentifier(BEDETHEQUE, Identifier.SID_BEDETHEQUE);
        MAP.put(cId.remote, cId);
        cId = new CalibreIdentifier(GOODREADS, Identifier.SID_GOODREADS_BOOK);
        MAP.put(cId.remote, cId);
        cId = new CalibreIdentifier(GOOGLE, Identifier.SID_GOOGLE);
        MAP.put(cId.remote, cId);
        cId = new CalibreIdentifier(ISFDB, Identifier.SID_ISFDB);
        MAP.put(cId.remote, cId);
        cId = new CalibreIdentifier(LASTDODO, Identifier.SID_LAST_DODO_NL);
        MAP.put(cId.remote, cId);
        cId = new CalibreIdentifier(LCCN, Identifier.SID_LCCN);
        MAP.put(cId.remote, cId);
        cId = new CalibreIdentifier(LIBRARYTHING, Identifier.SID_LIBRARY_THING);
        MAP.put(cId.remote, cId);
        cId = new CalibreIdentifier(OCLC, Identifier.SID_OCLC);
        MAP.put(cId.remote, cId);
        cId = new CalibreIdentifier(OPENLIBRARY, Identifier.SID_OPEN_LIBRARY);
        MAP.put(cId.remote, cId);
        cId = new CalibreIdentifier(STRIPINFO, Identifier.SID_STRIP_INFO);
        MAP.put(cId.remote, cId);
    }

    @NonNull
    public final String remote;
    @NonNull
    public final String local;

    private CalibreIdentifier(@NonNull final String remote,
                              @NonNull final String local) {
        this.remote = remote;
        this.local = local;
    }
}
