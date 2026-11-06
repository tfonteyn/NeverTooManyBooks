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

package com.hardbacknutter.nevertoomanybooks.database.dao.impl;

import com.hardbacknutter.nevertoomanybooks.BaseDBTest;
import com.hardbacknutter.nevertoomanybooks.database.DBDefinitions;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class DomainDefaultsTest
        extends BaseDBTest {

    private static final String INVALID_DEFAULT = "Invalid default";

    /**
     * These defaults are a prerequisite for the correctness of other tests.
     * This test guards against a sloppy developer changing them...
     * <p>
     * We're not testing all of them, just the crucial ones.
     */
    @Test
    void domainDefaults() {
        assertEquals("", DBDefinitions.DOM_BOOK_DATE_ACQUIRED.getDefault(),
                     INVALID_DEFAULT);
        assertEquals("", DBDefinitions.DOM_BOOK_DATE_READ_START.getDefault(),
                     INVALID_DEFAULT);
        assertEquals("", DBDefinitions.DOM_BOOK_DATE_READ_END.getDefault(),
                     INVALID_DEFAULT);

        // Boolean 'false'
        assertEquals("0", DBDefinitions.DOM_BOOK_READ.getDefault(),
                     INVALID_DEFAULT);

        assertEquals("0.0", DBDefinitions.DOM_BOOK_PRICE_LISTED.getDefault(),
                     INVALID_DEFAULT);
        assertEquals("0.0", DBDefinitions.DOM_BOOK_PRICE_PAID.getDefault(),
                     INVALID_DEFAULT);

        assertEquals("S", DBDefinitions.DOM_IDENTIFIER_TYPE.getDefault(),
                     INVALID_DEFAULT);
    }
}
