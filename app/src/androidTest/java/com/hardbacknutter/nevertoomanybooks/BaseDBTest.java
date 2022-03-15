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

import android.os.StrictMode;

import androidx.annotation.CallSuper;

import org.junit.After;
import org.junit.Before;

import com.hardbacknutter.nevertoomanybooks.covers.CoverDir;
import com.hardbacknutter.nevertoomanybooks.database.dao.DaoWriteException;
import com.hardbacknutter.nevertoomanybooks.utils.exceptions.StorageException;

public abstract class BaseDBTest {

    protected ServiceLocator mSl;

    @Before
    @CallSuper
    public void setup()
            throws DaoWriteException, StorageException {

        StrictMode.setVmPolicy(new StrictMode.VmPolicy.Builder()
                                       .detectLeakedSqlLiteObjects()
                                       .detectLeakedClosableObjects()
                                       .penaltyDeath()
                                       .penaltyLog()
                                       .build());

        mSl = ServiceLocator.getInstance();

        CoverDir.initVolume(ServiceLocator.getAppContext(), 0);
        mSl.getDb();
    }

    @After
    @CallSuper
    public void closeDb() {
        mSl.recreate();
    }
}
