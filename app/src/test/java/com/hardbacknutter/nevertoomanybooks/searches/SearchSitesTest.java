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

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.hardbacknutter.nevertoomanybooks.Base;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class SearchSitesTest
        extends Base {

    @Override
    @BeforeEach
    public void setUp() {
        super.setUp();
        setupSearchEnginePreferences(mSharedPreferences);
    }

    @Test
    void dumpEngines() {
        SearchEngineRegistry.create(mContext);

        final Collection<SearchEngineRegistry.Config> all = SearchEngineRegistry.getAll();
        for (final SearchEngineRegistry.Config config : all) {
            assertNotNull(config);
            System.out.println("\n" + config);
        }
    }

    @Test
    void dumpSites() {
        SearchEngineRegistry.create(mContext);

        for (final Site.Type type : Site.Type.values()) {
            final List<Site> sites = type.getSites();
            System.out.println("\n------------------------------------------\n\n" + type);

            for (final Site site : sites) {
                final SearchEngineRegistry.Config config =
                        SearchEngineRegistry.getByEngineId(site.engineId);
                assertNotNull(config);
                final SearchEngine searchEngine = site.getSearchEngine(mContext);
                assertNotNull(searchEngine);

                System.out.println("\n" + config + "\n\n" + site + "\n\n" + searchEngine);
            }
        }
    }

    @Test
    void order() {
        SearchEngineRegistry.create(mContext);

        final ArrayList<Site> sites = Site.Type.Data.getSites();
        // 4 should be removed, 128/256 added as loadPrefs will have been called
        assertEquals("64,32,16,8,2,1,128,256",
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
