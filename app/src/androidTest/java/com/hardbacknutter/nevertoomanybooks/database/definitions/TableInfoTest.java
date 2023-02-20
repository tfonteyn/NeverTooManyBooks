/*
 * @Copyright 2018-2022 HardBackNutter
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

import androidx.test.filters.MediumTest;

import com.hardbacknutter.nevertoomanybooks.core.database.SynchronizedDb;
import com.hardbacknutter.nevertoomanybooks.core.database.TableInfo;
import com.hardbacknutter.nevertoomanybooks.database.BaseSetup;
import com.hardbacknutter.nevertoomanybooks.database.DBDefinitions;

import org.junit.Test;

import static org.junit.Assert.assertNotNull;

@MediumTest
public class TableInfoTest
        extends BaseSetup {

    @Test
    public void bookTableInfo() {
        final SynchronizedDb db = serviceLocator.getDb();
        final TableInfo tableInfo = db.getTableInfo(DBDefinitions.TBL_BOOKS);
        assertNotNull(tableInfo);

        System.out.println(tableInfo);
    }
}