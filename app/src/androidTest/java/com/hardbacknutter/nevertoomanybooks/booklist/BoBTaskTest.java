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

package com.hardbacknutter.nevertoomanybooks.booklist;

import java.util.Optional;
import java.util.Set;

import com.hardbacknutter.nevertoomanybooks.BaseDBTest;
import com.hardbacknutter.nevertoomanybooks.booklist.style.FieldVisibility;
import com.hardbacknutter.nevertoomanybooks.booklist.style.Style;
import com.hardbacknutter.nevertoomanybooks.core.database.Sort;

import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class BoBTaskTest
        extends BaseDBTest {

    /**
     * The style Style.Screen.List fields supporting visibility
     * must all have DomainExpressions.
     */
    @Test
    public void visibilityKeysHaveDomainExpressions() {
        final Optional<Style> s1 = getTestStyle();
        assertTrue(s1.isPresent());

        final Set<String> keys = s1.get().getFieldVisibilityKeys(FieldVisibility.Screen.List, true);
        assertFalse(keys.isEmpty());

        final long expressionCount = keys
                .stream()
                .map(key -> BoBTask.createDomainExpressions(key, Sort.Unsorted,
                                                            s1.get()))
                .count();

        // some keys generate two expressions, don't bother checking
        // exact number as we're testing on NOT throwing an exception for key not found
        assertTrue(expressionCount > 0);
    }

    /**
     * The style Style.Screen.List fields supporting sorting
     * must all have DomainExpressions.
     */
    @Test
    public void sortableBookLevelKeysHaveDomainExpressions() {
        final Optional<Style> s1 = getTestStyle();
        assertTrue(s1.isPresent());

        final Set<String> keys = s1.get().getBookLevelFieldsOrderBy().keySet();
        assertFalse(keys.isEmpty());

        final long expressionCount = keys
                .stream()
                .map(key -> BoBTask.createDomainExpressions(key, Sort.Unsorted,
                                                            s1.get()))
                .count();

        // some keys generate two expressions, don't bother checking
        // exact number as we're testing on NOT throwing an exception for key not found
        assertTrue(expressionCount > 0);
    }
}