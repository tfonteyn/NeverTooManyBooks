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
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.test.platform.app.InstrumentationRegistry;

import java.io.IOException;
import java.io.InputStream;

import com.hardbacknutter.nevertoomanybooks.core.database.DaoWriteException;
import com.hardbacknutter.nevertoomanybooks.core.storage.StorageException;
import com.hardbacknutter.nevertoomanybooks.covers.CoverVolume;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.junit.Before;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

public abstract class BaseDBTest {

    protected ServiceLocator serviceLocator;
    protected Context context;

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
        context = ServiceLocator.getInstance().getLocalizedAppContext();

        CoverVolume.initVolume(context, 0);
        ServiceLocator.getInstance().getDb();
    }

    /** Load and parse a JSoup document from a raw html resource. */
    @NonNull
    protected Document loadDocument(final int resId,
                                    @Nullable final String charset,
                                    @NonNull final String locationHeader)
            throws IOException {
        final Document document;
        try (InputStream is = InstrumentationRegistry.getInstrumentation().getContext()
                                                     .getResources().openRawResource(resId)) {
            assertNotNull(is);
            document = Jsoup.parse(is, charset, locationHeader);
            assertNotNull(document);
            assertTrue(document.hasText());
        }
        return document;
    }
}
