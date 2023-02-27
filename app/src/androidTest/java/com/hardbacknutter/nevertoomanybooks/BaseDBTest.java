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
package com.hardbacknutter.nevertoomanybooks;

import android.content.Context;
import android.os.StrictMode;

import androidx.annotation.CallSuper;

import java.util.Locale;

import com.hardbacknutter.nevertoomanybooks.core.database.DaoWriteException;
import com.hardbacknutter.nevertoomanybooks.core.storage.StorageException;
import com.hardbacknutter.nevertoomanybooks.covers.CoverDir;

import org.junit.Before;

public abstract class BaseDBTest {

    protected ServiceLocator serviceLocator;
    protected Context context;
    protected Locale systemLocale;

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

        serviceLocator = ServiceLocator.getInstance();
        context = serviceLocator.getLocalizedAppContext();
        systemLocale = serviceLocator.getSystemLocaleList().get(0);

        CoverDir.initVolume(context, 0);
        serviceLocator.getDb();
    }
}
