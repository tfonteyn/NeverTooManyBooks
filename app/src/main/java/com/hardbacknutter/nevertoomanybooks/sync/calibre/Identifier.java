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
package com.hardbacknutter.nevertoomanybooks.sync.calibre;

import androidx.annotation.NonNull;

import java.util.HashMap;
import java.util.Map;

import com.hardbacknutter.nevertoomanybooks.database.DBKey;

public final class Identifier {

    static final String AMAZON = "amazon";
    /** Key is the remote (Calibre) identifier. */
    static final Map<String, Identifier> MAP = new HashMap<>();
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
        Identifier identifier;

        identifier = new Identifier(AMAZON, DBKey.SID_ASIN, false, false);
        MAP.put(identifier.remote, identifier);
        identifier = new Identifier(BEDETHEQUE, DBKey.SID_BEDETHEQUE, true);
        MAP.put(identifier.remote, identifier);
        identifier = new Identifier(GOODREADS, DBKey.SID_GOODREADS_BOOK, true);
        MAP.put(identifier.remote, identifier);
        identifier = new Identifier(GOOGLE, DBKey.SID_GOOGLE, false, false);
        MAP.put(identifier.remote, identifier);
        identifier = new Identifier(ISBN, DBKey.BOOK_ISBN, false);
        MAP.put(identifier.remote, identifier);
        identifier = new Identifier(ISFDB, DBKey.SID_ISFDB, true);
        MAP.put(identifier.remote, identifier);
        identifier = new Identifier(LASTDODO, DBKey.SID_LAST_DODO_NL, true);
        MAP.put(identifier.remote, identifier);
        identifier = new Identifier(LCCN, DBKey.SID_LCCN, false, false);
        MAP.put(identifier.remote, identifier);
        identifier = new Identifier(LIBRARYTHING, DBKey.SID_LIBRARY_THING, true);
        MAP.put(identifier.remote, identifier);
        identifier = new Identifier(OCLC, DBKey.SID_OCLC, false, false);
        MAP.put(identifier.remote, identifier);
        identifier = new Identifier(OPENLIBRARY, DBKey.SID_OPEN_LIBRARY, false);
        MAP.put(identifier.remote, identifier);
        identifier = new Identifier(STRIPINFO, DBKey.SID_STRIP_INFO, true);
        MAP.put(identifier.remote, identifier);
    }

    @NonNull
    public final String remote;
    @NonNull
    public final String local;
    final boolean isLocalLong;

    final boolean isStoredLocally;

    private Identifier(@NonNull final String remote,
                       @NonNull final String local,
                       final boolean isLocalLong) {
        this.remote = remote;
        this.local = local;
        this.isLocalLong = isLocalLong;
        this.isStoredLocally = true;
    }

    private Identifier(@NonNull final String remote,
                       @NonNull final String local,
                       final boolean isLocalLong,
                       final boolean isStoredLocally) {
        this.remote = remote;
        this.local = local;
        this.isLocalLong = isLocalLong;
        this.isStoredLocally = isStoredLocally;
    }
}
