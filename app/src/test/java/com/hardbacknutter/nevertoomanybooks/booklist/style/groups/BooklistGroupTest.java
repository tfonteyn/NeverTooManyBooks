/*
 * @Copyright 2018-2023 HardBackNutter
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
package com.hardbacknutter.nevertoomanybooks.booklist.style.groups;

import java.util.Collection;
import java.util.HashSet;
import java.util.Optional;

import com.hardbacknutter.nevertoomanybooks.booklist.style.BuiltinStyle;
import com.hardbacknutter.nevertoomanybooks.booklist.style.GlobalStyle;
import com.hardbacknutter.nevertoomanybooks.booklist.style.Style;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * Sanity check for duplicate prefix names and any missing keys.
 */
class BooklistGroupTest {

    @Test
    void duplicates() {
        // loop starting at 1 must exclude BOOK
        assertEquals(0, BooklistGroup.BOOK);

        final Optional<BuiltinStyle.Definition> definition =
                BuiltinStyle.getAll()
                            .stream()
                            .filter(def -> def.getId() == BuiltinStyle.DEFAULT_ID)
                            .findFirst();
        assertTrue(definition.isPresent());

        final GlobalStyle styleDefaults = new GlobalStyle();
        final Style style = new BuiltinStyle(definition.get(), styleDefaults, true, 1);

        final Collection<String> prefixes = new HashSet<>();
        for (int id = 0; id <= BooklistGroup.GROUP_KEY_MAX; id++) {
            final BooklistGroup group = BooklistGroup.newInstance(id, style);
            assertNotNull(group, "Missing id: " + id);

            final String prefix = group.getGroupKey().getKeyPrefix();
            if (!prefixes.add(prefix)) {
                fail("Duplicate keyPrefix: " + prefix);
            }
        }
    }
}
