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

package com.hardbacknutter.nevertoomanybooks.searchengines.bedetheque;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.Arrays;
import java.util.Collection;

import com.hardbacknutter.nevertoomanybooks.BaseDBTest;
import com.hardbacknutter.nevertoomanybooks.TestProgressListener;
import com.hardbacknutter.nevertoomanybooks.core.database.DaoWriteException;
import com.hardbacknutter.nevertoomanybooks.core.storage.StorageException;
import com.hardbacknutter.nevertoomanybooks.database.DBKey;
import com.hardbacknutter.nevertoomanybooks.entities.Book;
import com.hardbacknutter.nevertoomanybooks.entities.Series;
import com.hardbacknutter.nevertoomanybooks.searchengines.EngineId;
import com.hardbacknutter.nevertoomanybooks.utils.AppLocale;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

@SuppressWarnings("MissingJavadoc")
@RunWith(Parameterized.class)
public class SeriesParseTest
        extends BaseDBTest {

    private static final String TAG = "SeriesParseTest";
    @NonNull
    private final String name;
    @NonNull
    private final String expected;
    @Nullable
    private final String lang;

    private BedethequeSearchEngine searchEngine;
    private Book book;

    @Parameterized.Parameters
    public static Collection<Object[]> data() {
        return Arrays.asList(new Object[][]{

                {"Lucky Luke", "Lucky Luke", null},
                {"Lucky Luke Classics (en espagnol - Ediciones Kraken)",
                             "Lucky Luke Classics (en espagnol - Ediciones Kraken)",
                        "espagnol"},
                {"Lucky Luke (Les aventures de)",
                             "Lucky Luke (Les aventures de)",
                        null},
                {" Lucky Luke según Morris (Las Aventuras de) (Ediciones Kraken) ",
                             " Lucky Luke según Morris (Las Aventuras de) (Ediciones Kraken) ",
                        null},
                {"Lucky Luke (As aventuras de) (en portugais)",
                             "Lucky Luke (As aventuras de)",
                        "portugais"},

                {"Afrique, petit Chaka... (L')",
                             "L'Afrique, petit Chaka...",
                        null},
                {"Légende (du disque) de Bob Marley (La)",
                             "La Légende (du disque) de Bob Marley",
                        null},
                {"Legende (en néerlandais)",
                             "Legende",
                        "néerlandais"}});
    }

    public SeriesParseTest(@NonNull final String name,
                           @NonNull final String expected,
                           @Nullable final String lang) {
        this.name = name;
        this.expected = expected;
        this.lang = lang;

    }

    @Before
    public void setup()
            throws DaoWriteException, StorageException {
        super.setup(AppLocale.SYSTEM_LANGUAGE);

        book = new Book();

        searchEngine = (BedethequeSearchEngine) EngineId.Bedetheque.createSearchEngine(context);
        searchEngine.setCaller(new TestProgressListener(TAG));
    }

    @Test
    public void checkSeries() {
        book.clearData();
        final Series series = searchEngine.processSeries(name, book);
        assertEquals(expected, series.getTitle(), "for name=`" + name + '`');
        if (lang == null) {
            assertFalse(book.contains(DBKey.LANGUAGE), "for name=`" + name + '`');
        } else {
            assertEquals(lang, book.getString(DBKey.LANGUAGE, null), "for name=`" + name + '`');
        }
    }
}
