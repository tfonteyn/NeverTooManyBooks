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

package com.hardbacknutter.nevertoomanybooks.booklist.style;

import java.util.Locale;
import java.util.Set;

import com.hardbacknutter.nevertoomanybooks.BaseDBTest;
import com.hardbacknutter.nevertoomanybooks.core.database.DaoWriteException;
import com.hardbacknutter.nevertoomanybooks.core.storage.StorageException;
import com.hardbacknutter.nevertoomanybooks.database.dao.BookshelfDao;
import com.hardbacknutter.nevertoomanybooks.entities.Bookshelf;
import com.hardbacknutter.nevertoomanybooks.utils.AppLocale;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class MapDBKeyTest
        extends BaseDBTest {

    private BookshelfDao bookshelfDao;
    private Bookshelf bookshelf;
    private UserStyle style;

    @BeforeEach
    void setup()
            throws DaoWriteException, StorageException {
        super.setup(AppLocale.SYSTEM_LANGUAGE);

        style = (UserStyle) getBuiltinStyle().clone(context);
        style.setName("test");
        serviceLocator.getStyles().insertOrUpdate(context, style);

        bookshelfDao = serviceLocator.getBookshelfDao();
        bookshelf = bookshelfDao.getDefault();
        bookshelf.setStyle(context, style);
        bookshelfDao.update(context, bookshelf, Locale.UK);
    }

    @AfterEach
    void breakdown()
            throws DaoWriteException {
        bookshelf.setStyle(context, getBuiltinStyle());
        bookshelfDao.update(context, bookshelf, Locale.UK);
        serviceLocator.getStyles().delete(style);
    }

    /**
     * The fields globally supporting visibility
     * must all have a human-readable label.
     */
    @Test
    void visibilityKeysHaveLabels() {
        final Set<String> keys = new FieldVisibility().getKeys(true);
        assertFalse(keys.isEmpty());

        final long labelCount = keys
                .stream()
                .map(key -> MapDBKey.getLabel(context, key))
                .filter(label -> !label.isBlank())
                .count();

        assertEquals(labelCount, keys.size());
    }

    /**
     * The style BookLevelFields supporting sorting
     * must all have a human-readable label.
     */
    @Test
    void sortableBookLevelKeysHaveLabels() {
        // all supported fields will be there.
        final Set<String> keys = style.getBookLevelFieldsOrderBy().keySet();
        assertFalse(keys.isEmpty());

        final long labelCount = keys
                .stream()
                .map(key -> MapDBKey.getLabel(context, key))
                .filter(label -> !label.isBlank())
                .count();

        assertEquals(labelCount, keys.size());
    }


    /**
     * The Style.Screen.List fields supporting visibility
     * must all have a valid domain name.
     */
    @Test
    void visibilityKeysHaveDomainNames() {
        // force all fields
        style.setFieldVisibility(FieldVisibility.Screen.List, Long.MAX_VALUE);

        final Set<String> keys = style.getFieldVisibilityKeys(FieldVisibility.Screen.List, true);
        assertFalse(keys.isEmpty());

        final long domainCount = keys
                .stream()
                .map(MapDBKey::getDomainName)
                .filter(label -> !label.isBlank())
                .count();

        assertEquals(domainCount, keys.size());
    }

    /**
     * The Style.Screen.List fields supporting sorting
     * must all have a valid domain name.
     */
    @Test
    void sortableBookLevelKeysHaveDomainNames() {
        // all supported fields will be there.
        final Set<String> keys = style.getBookLevelFieldsOrderBy().keySet();
        assertFalse(keys.isEmpty());

        final long domainCount = keys
                .stream()
                .map(MapDBKey::getDomainName)
                .filter(label -> !label.isBlank())
                .count();

        assertEquals(domainCount, keys.size());
    }
}
