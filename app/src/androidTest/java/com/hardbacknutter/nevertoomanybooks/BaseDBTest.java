/*
 * @Copyright 2018-2024 HardBackNutter
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
import androidx.preference.PreferenceManager;
import androidx.test.platform.app.InstrumentationRegistry;

import java.io.IOException;
import java.io.InputStream;
import java.util.Optional;

import com.hardbacknutter.nevertoomanybooks.booklist.style.BuiltinStyle;
import com.hardbacknutter.nevertoomanybooks.booklist.style.Style;
import com.hardbacknutter.nevertoomanybooks.core.database.DaoWriteException;
import com.hardbacknutter.nevertoomanybooks.core.storage.StorageException;
import com.hardbacknutter.nevertoomanybooks.covers.CoverVolume;
import com.hardbacknutter.nevertoomanybooks.settings.Prefs;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

@SuppressWarnings({"OverlyBroadThrowsClause", "MissingJavadoc"})
public abstract class BaseDBTest {

    protected ServiceLocator serviceLocator;
    protected Context context;

    @CallSuper
    public void setup(@NonNull final String localeCode)
            throws StorageException, DaoWriteException {
        StrictMode.setVmPolicy(new StrictMode.VmPolicy.Builder()
                                       .detectLeakedSqlLiteObjects()
                                       .detectLeakedClosableObjects()
                                       .penaltyDeath()
                                       .penaltyLog()
                                       .build());

        serviceLocator = ServiceLocator.getInstance();

        context = serviceLocator.getAppContext();
        PreferenceManager.getDefaultSharedPreferences(context)
                         .edit()
                         .putString(Prefs.PK_UI_LOCALE, localeCode)
                         .apply();
        context = serviceLocator.getLocalizedAppContext();

        CoverVolume.initVolume(context, 0);
        serviceLocator.getDb();
    }

    @NonNull
    protected Optional<Style> getTestStyle() {
        return BuiltinStyle.ALL.stream()
                               .filter(def -> def.getId() == BuiltinStyle.ID_FOR_TESTING_ONLY)
                               .findFirst()
                               .map(BuiltinStyle.Definition::getUuid)
                               .map(uuid -> serviceLocator.getStyles().getStyle(uuid))
                               .orElseThrow();
    }


    /** Load and parse a JSoup document from a raw html resource. */
    @NonNull
    protected Document loadDocument(final int resId,
                                    @Nullable final String charset,
                                    @NonNull final String locationHeader)
            throws IOException {
        final Document document;
        // getContext(): we want the "androidTest" context which is where our test resources live
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
