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

package com.hardbacknutter.nevertoomanybooks.searchengines.bedetheque;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import javax.xml.parsers.ParserConfigurationException;

import com.hardbacknutter.nevertoomanybooks.Base;
import com.hardbacknutter.nevertoomanybooks._mocks.MockCancellable;
import com.hardbacknutter.nevertoomanybooks.database.DBKey;
import com.hardbacknutter.nevertoomanybooks.entities.Series;
import com.hardbacknutter.nevertoomanybooks.searchengines.EngineId;
import com.hardbacknutter.nevertoomanybooks.searchengines.Site;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.xml.sax.SAXException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

public class SeriesParserTest
        extends Base {

    private BedethequeSearchEngine searchEngine;

    @BeforeEach
    public void setup()
            throws ParserConfigurationException, SAXException {
        super.setup();

        searchEngine = (BedethequeSearchEngine) Site.Type.Data
                .getSite(EngineId.Bedetheque).getSearchEngine();
        searchEngine.setCaller(new MockCancellable());
    }

    @Test
    void parse01() {
        checkSeries("Lucky Luke", "Lucky Luke", null);
        checkSeries("Lucky Luke Classics (en espagnol - Ediciones Kraken)",
                    "Lucky Luke Classics (en espagnol - Ediciones Kraken)",
                    "espagnol");
        checkSeries("Lucky Luke (Les aventures de)",
                    "Lucky Luke (Les aventures de)",
                    null);
        checkSeries(" Lucky Luke según Morris (Las Aventuras de) (Ediciones Kraken) ",
                    " Lucky Luke según Morris (Las Aventuras de) (Ediciones Kraken) ",
                    null);
        checkSeries("Lucky Luke (As aventuras de) (en portugais)",
                    "Lucky Luke (As aventuras de)",
                    "portugais");

        checkSeries("Afrique, petit Chaka... (L')",
                    "L'Afrique, petit Chaka...",
                    null);
        checkSeries("Légende (du disque) de Bob Marley (La)",
                    "La Légende (du disque) de Bob Marley",
                    null);
        checkSeries("Legende (en néerlandais)",
                    "Legende",
                    "néerlandais");
    }

    void checkSeries(@NonNull final String name,
                     @NonNull final String expected,
                     @Nullable final String lang) {
        rawData.clear();
        final Series series = searchEngine.processSeries(rawData, name);
        assertEquals(expected, series.getTitle(), "for name=`" + name + '`');
        if (lang == null) {
            assertFalse(rawData.containsKey(DBKey.LANGUAGE), "for name=`" + name + '`');
        } else {
            assertEquals(lang, rawData.getString(DBKey.LANGUAGE), "for name=`" + name + '`');
        }
    }
}
