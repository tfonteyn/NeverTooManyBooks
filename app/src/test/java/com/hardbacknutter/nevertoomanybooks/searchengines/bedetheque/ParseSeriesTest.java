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

package com.hardbacknutter.nevertoomanybooks.searchengines.bedetheque;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.stream.Stream;

import com.hardbacknutter.nevertoomanybooks.Base;
import com.hardbacknutter.nevertoomanybooks.TestProgressListener;
import com.hardbacknutter.nevertoomanybooks._mocks.os.BundleMock;
import com.hardbacknutter.nevertoomanybooks.database.DBKey;
import com.hardbacknutter.nevertoomanybooks.entities.Book;
import com.hardbacknutter.nevertoomanybooks.entities.Series;
import com.hardbacknutter.nevertoomanybooks.searchengines.EngineId;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

public class ParseSeriesTest
        extends Base {

    private static final String TAG = "ParseSeriesTest";

    private BedethequeSearchEngine searchEngine;
    private Book book;

    @NonNull
    static Stream<Arguments> readArgs() {
        return Stream.of(

                Arguments.of("Lucky Luke", "Lucky Luke", null),
                Arguments.of("Lucky Luke Classics (en espagnol - Ediciones Kraken)",
                             "Lucky Luke Classics (en espagnol - Ediciones Kraken)",
                             "espagnol"),
                Arguments.of("Lucky Luke (Les aventures de)",
                             "Lucky Luke (Les aventures de)",
                             null),
                Arguments.of(" Lucky Luke según Morris (Las Aventuras de) (Ediciones Kraken) ",
                             " Lucky Luke según Morris (Las Aventuras de) (Ediciones Kraken) ",
                             null),
                Arguments.of("Lucky Luke (As aventuras de) (en portugais)",
                             "Lucky Luke (As aventuras de)",
                             "portugais"),

                Arguments.of("Afrique, petit Chaka... (L')",
                             "L'Afrique, petit Chaka...",
                             null),
                Arguments.of("Légende (du disque) de Bob Marley (La)",
                             "La Légende (du disque) de Bob Marley",
                             null),
                Arguments.of("Legende (en néerlandais)",
                             "Legende",
                             "néerlandais")
        );
    }

    @BeforeEach
    public void setup()
            throws Exception {
        super.setup();
        book = new Book(BundleMock.create());

        searchEngine = (BedethequeSearchEngine) EngineId.Bedetheque.createSearchEngine(context);
        searchEngine.setCaller(new TestProgressListener(TAG));
    }

    @ParameterizedTest
    @MethodSource("readArgs")
    void checkSeries(@NonNull final String name,
                     @NonNull final String expected,
                     @Nullable final String lang) {
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
