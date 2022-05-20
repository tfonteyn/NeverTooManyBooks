/*
 * @Copyright 2018-2021 HardBackNutter
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

@SuppressWarnings("WeakerAccess")
public class Identifier {

    public static final String AMAZON = "amazon";
    static final String GOODREADS = "goodreads";
    static final String GOOGLE = "google";
    static final String ISBN = "isbn";
    static final String ISFDB = "isfdb";
    static final String LASTDODO = "lastdodo";
    static final String LCCN = "lccn";
    static final String LIBRARYTHING = "librarything";
    static final String OCLC = "oclc";
    static final String OPENLIBRARY = "openlibrary";
    static final String STRIPINFO = "stripinfo";

    /** Key is the remote (Calibre) identifier. */
    public static final Map<String, Identifier> MAP = new HashMap<>();

    static {
        Identifier identifier;

        identifier = new Identifier(ISBN, DBKey.BOOK_ISBN, false);
        MAP.put(identifier.remote, identifier);
        identifier = new Identifier(OPENLIBRARY, DBKey.SID_OPEN_LIBRARY, false);
        MAP.put(identifier.remote, identifier);

        identifier = new Identifier(GOODREADS, DBKey.SID_GOODREADS_BOOK, true);
        MAP.put(identifier.remote, identifier);
        identifier = new Identifier(ISFDB, DBKey.SID_ISFDB, true);
        MAP.put(identifier.remote, identifier);
        identifier = new Identifier(LIBRARYTHING, DBKey.SID_LIBRARY_THING, true);
        MAP.put(identifier.remote, identifier);
        identifier = new Identifier(STRIPINFO, DBKey.SID_STRIP_INFO, true);
        MAP.put(identifier.remote, identifier);
        identifier = new Identifier(LASTDODO, DBKey.SID_LAST_DODO_NL, true);
        MAP.put(identifier.remote, identifier);

        // ENHANCE: Not stored locally for now, but recognised.
        identifier = new Identifier(AMAZON, DBKey.SID_ASIN, false, false);
        MAP.put(identifier.remote, identifier);
        identifier = new Identifier(GOOGLE, DBKey.SID_GOOGLE, false, false);
        MAP.put(identifier.remote, identifier);
        identifier = new Identifier(LCCN, DBKey.SID_LCCN, false, false);
        MAP.put(identifier.remote, identifier);
        identifier = new Identifier(OCLC, DBKey.SID_OCLC, false, false);
        MAP.put(identifier.remote, identifier);
    }

    @NonNull
    public final String remote;
    @NonNull
    public final String local;

    public final boolean isLocalLong;

    public final boolean isStoredLocally;

    public Identifier(@NonNull final String remote,
                      @NonNull final String local,
                      final boolean isLocalLong) {
        this.remote = remote;
        this.local = local;
        this.isLocalLong = isLocalLong;
        this.isStoredLocally = true;
    }

    public Identifier(@NonNull final String remote,
                      @NonNull final String local,
                      final boolean isLocalLong,
                      final boolean isStoredLocally) {
        this.remote = remote;
        this.local = local;
        this.isLocalLong = isLocalLong;
        this.isStoredLocally = isStoredLocally;
    }
}
