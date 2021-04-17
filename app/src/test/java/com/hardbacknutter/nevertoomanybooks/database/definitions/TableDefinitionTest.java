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
package com.hardbacknutter.nevertoomanybooks.database.definitions;

import org.junit.jupiter.api.Test;

import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.TBL_BOOKS;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.TBL_BOOKSHELF;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.TBL_BOOK_BOOKSHELF;
import static org.junit.jupiter.api.Assertions.assertEquals;

class TableDefinitionTest {

    private static final String BS_J_BBS =
            "bookshelf AS bsh"
            + " JOIN book_bookshelf AS bbsh"
            + " ON (bsh._id=bbsh.bookshelf_id)";

    private static final String BS_J_BBS_B =
            "bookshelf AS bsh"
            + " JOIN book_bookshelf AS bbsh"
            + " ON (bsh._id=bbsh.bookshelf_id)"
            + " JOIN books AS b"
            + " ON (b._id=bbsh.book)";

    @Test
    void ref() {
        assertEquals("bookshelf AS bsh", TBL_BOOKSHELF.ref());
    }

    @Test
    void joinAndRef() {
        assertEquals(BS_J_BBS, TBL_BOOKSHELF.ref() + TBL_BOOKSHELF.join(TBL_BOOK_BOOKSHELF));
    }

    @Test
    void singleStartJoin() {
        assertEquals(BS_J_BBS, TBL_BOOKSHELF.startJoin(TBL_BOOK_BOOKSHELF));
    }

    @Test
    void startJoinAndJoin() {
        assertEquals(BS_J_BBS_B, TBL_BOOKSHELF.startJoin(TBL_BOOK_BOOKSHELF)
                                 + TBL_BOOK_BOOKSHELF.join(TBL_BOOKS));
    }

    @Test
    void multiStartJoin() {
        assertEquals(BS_J_BBS_B, TBL_BOOKSHELF.startJoin(TBL_BOOK_BOOKSHELF, TBL_BOOKS));
    }
}
