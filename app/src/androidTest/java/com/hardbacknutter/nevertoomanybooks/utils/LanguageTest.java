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

package com.hardbacknutter.nevertoomanybooks.utils;

import android.util.Log;

import java.util.Locale;

import com.hardbacknutter.nevertoomanybooks.BaseDBTest;
import com.hardbacknutter.nevertoomanybooks.core.database.DaoWriteException;
import com.hardbacknutter.nevertoomanybooks.core.storage.StorageException;

import org.junit.Before;
import org.junit.Test;

public class LanguageTest
        extends BaseDBTest {

    private static final String TAG = "LanguageTest";

    @Before
    public void setup()
            throws DaoWriteException, StorageException {
        super.setup(AppLocale.SYSTEM_LANGUAGE);
    }

    @Test
    public void ancientGreek() {
        final Languages l = serviceLocator.getLanguages();

        // result is "grc" which is correct
        Log.d(TAG, "grc=" + l.getLocaleIsoFromISO3(Locale.UK, "grc"));

        // result is "Ancient Greek" again correct
        Log.d(TAG, "display:" + l.getDisplayLanguageFromISO3(context, "grc"));

        // This fails as Ancient Greek is not a supported Locale on Android
        Log.d(TAG, "locale =" + new Locale("grc"));
    }
}
