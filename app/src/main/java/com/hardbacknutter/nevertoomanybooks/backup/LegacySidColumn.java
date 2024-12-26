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

package com.hardbacknutter.nevertoomanybooks.backup;

import java.util.Map;

import com.hardbacknutter.nevertoomanybooks.entities.Identifier;

public final class LegacySidColumn {

    public static final Map<String, String> MAP = Map.of(
            "goodreads_book_id", Identifier.SID_GOODREADS_BOOK,
            "isfdb_book_id", Identifier.SID_ISFDB,
            "lt_book_id", Identifier.SID_LIBRARY_THING,
            "ol_book_id", Identifier.SID_OPEN_LIBRARY,
            "si_book_id", Identifier.SID_STRIP_INFO,
            "ld_book_id", Identifier.SID_LAST_DODO_NL,
            "bdt_book_id", Identifier.SID_BEDETHEQUE
    );

    private LegacySidColumn() {
    }
}
