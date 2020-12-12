/*
 * @Copyright 2020 HardBackNutter
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
package com.hardbacknutter.nevertoomanybooks.searches;

import android.content.Context;

import androidx.test.platform.app.InstrumentationRegistry;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;

import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

public class SearchSitesTest {

    private SearchEngineRegistry mEngineRegistry;

    @Before
    public void setup() {
        final Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        SearchEngineRegistry.create(context);
        mEngineRegistry = SearchEngineRegistry.getInstance();
    }

    @Test
    public void dumpEngines() {
        final Collection<SearchEngineRegistry.Config> all = mEngineRegistry.getAll();
        for (final SearchEngineRegistry.Config config : all) {
            assertNotNull(config);
            System.out.println("\n" + config);
        }
    }

    @Test
    public void dumpSites() {
        final Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        for (final Site.Type type : Site.Type.values()) {
            final List<Site> sites = type.getSites();
            System.out.println("\n------------------------------------------\n\n" + type);

            for (final Site site : sites) {
                final SearchEngineRegistry.Config config =
                        mEngineRegistry.getByEngineId(site.engineId);
                assertNotNull(config);
                final SearchEngine searchEngine = site.getSearchEngine(context);
                assertNotNull(searchEngine);

                System.out.println("\n" + config + "\n\n" + site + "\n\n" + searchEngine);
            }
        }
    }

    /**
     * 1  : google
     * 2  : amazon
     * 4  : library thing
     * 8  : goodreads
     * 16 : isfdb
     * 32 : openlibrary
     * 64 : KB NL
     * 128: stripinfo.be
     * 256: lastdodo.nl
     * <p>
     * default order (2020-12-12): 2,8,1,4,16,128,64,256,32
     */
    @Test
    public void order() {
        final ArrayList<Site> sites = Site.Type.Data.getSites();
        // 4 should be removed, 128/256 added as loadPrefs will have been called
        assertEquals("2,8,1,16,128,64,256,32",
                     sites.stream()
                          .map(element -> String.valueOf(element.engineId))
                          .collect(Collectors.joining(",")));

        final List<Site> reordered = Site.Type.reorder(sites, "1,2,4,16,64,128,256,512");
        // 4/512 should be removed, 8/32 NOT added as loadPrefs will NOT have been called
        assertEquals("1,2,16,64,128,256",
                     reordered.stream()
                              .map(element -> String.valueOf(element.engineId))
                              .collect(Collectors.joining(",")));
    }
}
