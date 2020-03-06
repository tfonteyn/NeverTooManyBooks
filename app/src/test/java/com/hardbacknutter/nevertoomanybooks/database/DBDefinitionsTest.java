/*
 * @Copyright 2020 HardBackNutter
 * @License GNU General Public License
 *
 * This file is part of NeverTooManyBooks.
 *
 * In August 2018, this project was forked from:
 * Book Catalogue 5.2.2 @2016 Philip Warner & Evan Leybourn
 *
 * Without their original creation, this project would not exist in its
 * current form. It was however largely rewritten/refactored and any
 * comments on this fork should be directed at HardBackNutter and not
 * at the original creators.
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
package com.hardbacknutter.nevertoomanybooks.database;

import java.util.Collection;
import java.util.HashSet;

import org.junit.jupiter.api.Test;

import com.hardbacknutter.nevertoomanybooks.database.definitions.TableDefinition;

import static org.junit.jupiter.api.Assertions.fail;

class DBDefinitionsTest {

    @Test
    void duplicates() {
        Collection<String> tNames = new HashSet<>();
        Collection<String> tAliases = new HashSet<>();
        for (TableDefinition table : DBDefinitions.ALL_TABLES.values()) {
            if (!tNames.add(table.getName())) {
                fail("Duplicate table name: " + table.getName());
            }
            if (!tAliases.add(table.getAlias())) {
                fail("Duplicate table alias: " + table.getAlias());
            }
        }
    }
}
