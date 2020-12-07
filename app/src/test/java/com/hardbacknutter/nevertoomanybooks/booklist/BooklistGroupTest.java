/*
 * @Copyright 2020 HardBackNutter
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
package com.hardbacknutter.nevertoomanybooks.booklist;

import java.util.Collection;
import java.util.HashSet;

import org.junit.jupiter.api.Test;

import com.hardbacknutter.nevertoomanybooks.booklist.style.groups.BooklistGroup;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.fail;

class BooklistGroupTest {

    @Test
    void duplicates() {
        // loop starting at 1 must exclude BOOK
        assertEquals(0, BooklistGroup.BOOK);

        final Collection<String> prefixes = new HashSet<>();
        for (int id = 0; id <= BooklistGroup.GROUP_KEY_MAX; id++) {
            final BooklistGroup.GroupKey groupKey = BooklistGroup.GroupKey.getGroupKey(id);
            assertNotNull(groupKey, "Missing id: " + id);

            final String prefix = groupKey.getKeyPrefix();
            if (!prefixes.add(prefix)) {
                fail("Duplicate keyPrefix: " + prefix);
            }
        }
    }
}
