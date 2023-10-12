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

package com.hardbacknutter.nevertoomanybooks.booklist.style;

import java.util.Optional;
import java.util.Set;

import com.hardbacknutter.nevertoomanybooks.BaseDBTest;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class MapDBKeyTest
        extends BaseDBTest {

    /**
     * The fields globally supporting visibility
     * must all have a human readable label.
     */
    @Test
    public void visibilityKeysHaveLabels() {
        final Set<String> keys = new FieldVisibility().getKeys(true);
        assertFalse(keys.isEmpty());

        final long labelCount = keys
                .stream()
                .map(key -> MapDBKey.getLabel(context, key))
                .count();

        assertEquals(labelCount, keys.size());
    }

    /**
     * The style BookLevelFields supporting sorting
     * must all have a human readable label.
     */
    @Test
    public void sortableBookLevelKeysHaveLabels() {
        final StylesHelper helper = serviceLocator.getStyles();
        final Optional<Style> s1 = helper.getStyle(BuiltinStyle.UUID_FOR_TESTING_ONLY);
        assertTrue(s1.isPresent());

        final Set<String> keys = s1.get().getBookLevelFieldsOrderBy().keySet();
        assertFalse(keys.isEmpty());

        final long labelCount = keys
                .stream()
                .map(key -> MapDBKey.getLabel(context, key))
                .count();

        assertEquals(labelCount, keys.size());
    }


    /**
     * The style Style.Screen.List fields supporting visibility
     * must all have a valid domain name.
     */
    @Test
    public void visibilityKeysHaveDomainNames() {
        final StylesHelper helper = serviceLocator.getStyles();
        final Optional<Style> s1 = helper.getStyle(BuiltinStyle.UUID_FOR_TESTING_ONLY);
        assertTrue(s1.isPresent());

        final Set<String> keys = s1.get().getFieldVisibility(FieldVisibility.Screen.List)
                                   .getKeys(true);
        assertFalse(keys.isEmpty());

        final long domainCount = keys
                .stream()
                .map(MapDBKey::getDomainName)
                .count();

        assertEquals(domainCount, keys.size());
    }

    /**
     * The style Style.Screen.List fields supporting sorting
     * must all have a valid domain name.
     */
    @Test
    public void sortableBookLevelKeysHaveDomainNames() {
        final StylesHelper helper = serviceLocator.getStyles();
        final Optional<Style> s1 = helper.getStyle(BuiltinStyle.UUID_FOR_TESTING_ONLY);
        assertTrue(s1.isPresent());

        final Set<String> keys = s1.get().getBookLevelFieldsOrderBy().keySet();
        assertFalse(keys.isEmpty());

        final long domainCount = keys
                .stream()
                .map(MapDBKey::getDomainName)
                .count();

        assertEquals(domainCount, keys.size());
    }


}
