/*
 * @Copyright 2018-2021 HardBackNutter
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
package com.hardbacknutter.nevertoomanybooks;

import androidx.annotation.CallSuper;
import androidx.annotation.NonNull;

import org.junit.Before;

import com.hardbacknutter.nevertoomanybooks.database.dao.DaoWriteException;
import com.hardbacknutter.nevertoomanybooks.utils.AppDir;
import com.hardbacknutter.nevertoomanybooks.utils.exceptions.ExternalStorageException;

import static org.junit.Assert.fail;

public abstract class BaseDBTest {

    @Before
    @CallSuper
    public void setup()
            throws DaoWriteException {

        try {
            AppDir.initVolume(ServiceLocator.getAppContext(), 0);
        } catch (@NonNull final ExternalStorageException e) {
            fail(e.toString());
        }

        ServiceLocator.getInstance().initialiseDb(ServiceLocator.getGlobalPreferences());
    }
}
