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
package com.hardbacknutter.nevertoomanybooks.searchengines;

import android.util.Log;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

public class EngineIdTest {

    private static final String TAG = "EngineIdTest";

    @Test
    public void dumpEngines() {
        final Collection<SearchEngineConfig> all = SearchEngineRegistry.getInstance().getAll();
        for (final SearchEngineConfig config : all) {
            assertNotNull(config);
            Log.d(TAG, "\n" + config);
        }
    }

    @Test
    public void dumpSites() {
        for (final Site.Type type : Site.Type.values()) {
            final List<Site> sites = type.getSites();
            Log.d(TAG, "\n------------------------------------------\n\n" + type);

            for (final Site site : sites) {
                final SearchEngineConfig config =
                        SearchEngineRegistry.getInstance().getByEngineId(site.getEngineId());
                assertNotNull(config);
                final SearchEngine searchEngine = site.getSearchEngine();
                assertNotNull(searchEngine);

                Log.d(TAG, "\n" + config + "\n\n" + site + "\n\n" + searchEngine);
            }
        }
    }

    @Test
    public void order() {
        final ArrayList<Site> sites = Site.Type.Data.getSites();
        // LIBRARY_THING should be removed, STRIP_INFO_BE + LAST_DODO
        // added as loadPrefs will have been called
        assertEquals(List.of(EngineId.Amazon,
                             EngineId.Goodreads,
                             EngineId.GoogleBooks,
                             EngineId.IsfDb,
                             EngineId.StripInfoBe,
                             EngineId.KbNl,
                             EngineId.LastDodoNl,
                             EngineId.OpenLibrary),
                     sites.stream()
                          .map(Site::getEngineId)
                          .collect(Collectors.toList()));

        final List<Site> reordered = Site.Type.reorder(
                sites, List.of(EngineId.GoogleBooks,
                               EngineId.Amazon,
                               EngineId.LibraryThing,
                               EngineId.IsfDb,
                               EngineId.KbNl,
                               EngineId.StripInfoBe,
                               EngineId.LastDodoNl));

        // LIBRARY_THING should be removed, GOODREADS/OPEN_LIBRARY
        // NOT added as loadPrefs will NOT have been called
        assertEquals(List.of(EngineId.GoogleBooks,
                             EngineId.Amazon,
                             EngineId.IsfDb,
                             EngineId.KbNl,
                             EngineId.StripInfoBe,
                             EngineId.LastDodoNl),
                     reordered.stream()
                              .map(Site::getEngineId)
                              .collect(Collectors.toList()));
    }
}
