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
package com.hardbacknutter.nevertoomanybooks.database;

import java.util.Collection;
import java.util.HashSet;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import com.hardbacknutter.nevertoomanybooks.database.definitions.TableDefinition;
import com.hardbacknutter.nevertoomanybooks.debug.Logger;

import static org.junit.jupiter.api.Assertions.fail;

class DBDefinitionsTest {

    @BeforeAll
    static void startUp() {
        Logger.isJUnitTest = true;
    }

    @Test
    void duplicates() {
        final Collection<String> tNames = new HashSet<>();
        final Collection<String> tAliases = new HashSet<>();
        for (final TableDefinition table : DBDefinitions.ALL_TABLES.values()) {
            if (!tNames.add(table.getName())) {
                fail("Duplicate table name: " + table.getName());
            }
            if (!tAliases.add(table.getAlias())) {
                fail("Duplicate table alias: " + table.getAlias());
            }
        }
    }
}
