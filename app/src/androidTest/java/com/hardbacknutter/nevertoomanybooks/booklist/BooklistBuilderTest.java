/*
 * @Copyright 2018-2025 HardBackNutter
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

import android.util.Log;

import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

import com.hardbacknutter.nevertoomanybooks.BaseDBTest;
import com.hardbacknutter.nevertoomanybooks.DEBUG_SWITCHES;
import com.hardbacknutter.nevertoomanybooks.ServiceLocator;
import com.hardbacknutter.nevertoomanybooks.booklist.filters.Filter;
import com.hardbacknutter.nevertoomanybooks.booklist.grouping.BooklistGroup;
import com.hardbacknutter.nevertoomanybooks.booklist.style.FieldVisibility;
import com.hardbacknutter.nevertoomanybooks.booklist.style.UserStyle;
import com.hardbacknutter.nevertoomanybooks.core.database.ColumnInfo;
import com.hardbacknutter.nevertoomanybooks.core.database.DaoWriteException;
import com.hardbacknutter.nevertoomanybooks.core.database.SynchronizedDb;
import com.hardbacknutter.nevertoomanybooks.core.database.TableDefinition;
import com.hardbacknutter.nevertoomanybooks.core.database.TableInfo;
import com.hardbacknutter.nevertoomanybooks.core.storage.StorageException;
import com.hardbacknutter.nevertoomanybooks.database.dao.BookshelfDao;
import com.hardbacknutter.nevertoomanybooks.entities.Bookshelf;
import com.hardbacknutter.nevertoomanybooks.utils.AppLocale;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.assertNotNull;

public class BooklistBuilderTest
        extends BaseDBTest {

    private static final String TAG = "BooklistBuilderTest";

    private BookshelfDao bookshelfDao;
    private Bookshelf bookshelf;
    private UserStyle style;

    @Before
    public void setup()
            throws DaoWriteException, StorageException {
        super.setup(AppLocale.SYSTEM_LANGUAGE);

        style = (UserStyle) getBuiltinStyle().clone(context);
        style.setName("test");
        serviceLocator.getStyles().insertOrUpdate(context, style);

        bookshelfDao = serviceLocator.getBookshelfDao();
        bookshelf = bookshelfDao.getDefault(context)
                                .orElseThrow();
        bookshelf.setStyle(context, style);
        bookshelfDao.update(context, bookshelf, Locale.UK);
    }

    @After
    public void breakdown()
            throws DaoWriteException {
        bookshelf.setStyle(context, getBuiltinStyle());
        bookshelfDao.update(context, bookshelf, Locale.UK);
        serviceLocator.getStyles().delete(style);
    }

    @Test
    public void t01() {
        // force all book level fields
        style.setFieldVisibility(FieldVisibility.Screen.List, Long.MAX_VALUE);
        // all groups...
        style.setGroupList(BooklistGroup.getAllGroups(style));

        final SynchronizedDb db = ServiceLocator.getInstance().getDb();
        final Collection<Filter> criteriaFilters = List.of();

        final BoBTask boBTask = new BoBTask();

        DEBUG_SWITCHES.BOB_USES_STANDARD_TABLE = true;
        final Booklist booklist = boBTask.buildBooklist(context, db, bookshelf, style,
                                                        RebuildBooklist.Collapsed, criteriaFilters);

        assertNotNull(booklist);

        final TableDefinition listTable = booklist.getListTable();
        final TableInfo listTableInfo = listTable.getTableInfo(db.getSQLiteDatabase());
        assertNotNull(listTableInfo);
        Log.d(TAG, listTableInfo.getColumns()
                                .stream()
                                .sorted(Comparator.comparingLong(ColumnInfo::getPosition))
                                .collect(Collectors.toList())
                                .toString()
        );
        final TableDefinition navTable = booklist.getNavTable();
        final TableInfo navTableInfo = navTable.getTableInfo(db.getSQLiteDatabase());
        assertNotNull(navTableInfo);
        Log.d(TAG, navTableInfo.getColumns()
                               .stream()
                               .sorted(Comparator.comparingLong(ColumnInfo::getPosition))
                               .collect(Collectors.toList())
                               .toString());
    }
}
