/*
 * @Copyright 2018-2026 HardBackNutter
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

import android.content.Context;

import java.io.IOException;

import com.hardbacknutter.nevertoomanybooks.BaseDBTest;
import com.hardbacknutter.nevertoomanybooks.TestProgressListener;
import com.hardbacknutter.nevertoomanybooks.searchengines.EngineId;

import org.jsoup.nodes.Document;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class MultiResultTest
        extends BaseDBTest {

    private static final String TAG = "MultiResultTest";

    private static final String UTF_8 = "UTF-8";

    private BedethequeSearchEngine searchEngine;

    @BeforeEach
    protected void setup()
            throws Exception {
        super.setup();
        final Context context = createContext();

        EngineId.Bedetheque.getConfig().setHttpLoggingEnabled(true);
        searchEngine = EngineId.Bedetheque.createSearchEngine(context);
        searchEngine.setCaller(new TestProgressListener(TAG));
    }

    @Test
    void multiResult()
            throws IOException {
        final String locationHeader = "https://www.bedetheque.com/search/albums?qs=1&RechTitre=Magdalena";
        final int resId = com.hardbacknutter.nevertoomanybooks.test
                .R.raw.bedetheque_miltiresult_magdalena;

        final Document document = loadHtml(resId, UTF_8, locationHeader);
        final String url = searchEngine.parseMultiResult(document);
        assertNotNull(url);
        // This is a live search, so this result can change overtime.
        assertEquals("https://www.bedetheque.com/BD-Amanda-Editoriale-Aurea-Tome-6-Magdalena-e-il-richiamo-de-la-guerra-279012.html", url);
    }
}
