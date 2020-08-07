/*
 * @Copyright 2020 HardBackNutter
 * @License GNU General Public License
 *
 * This file is part of NeverTooManyBooks.
 *
 * In August 2018, this project was forked from:
 * Book Catalogue 5.2.2 @2016 Philip Warner & Evan Leybourn
 *
 * Without their original creation, this project would not exist in its
 * current form. It was however largely rewritten/refactored and any
 * comments on this fork should be directed at HardBackNutter and not
 * at the original creators.
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

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.hardbacknutter.nevertoomanybooks.CommonMocks;
import com.hardbacknutter.nevertoomanybooks.utils.Csv;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.when;

class SearchSitesTest
        extends CommonMocks {

    @BeforeEach
    public void setUp() {
        super.setUp();

        when(mSharedPreferences.getBoolean(eq("search.site.goodreads.enabled"),
                                           anyBoolean())).thenReturn(true);
        when(mSharedPreferences.getString(eq("goodreads.host.url"),
                                          anyString()))
                .thenReturn("https://www.goodreads.com");

        when(mSharedPreferences.getBoolean(eq("search.site.googlebooks.enabled"),
                                           anyBoolean())).thenReturn(true);
        when(mSharedPreferences.getString(eq("googlebooks.host.url"),
                                          anyString()))
                .thenReturn("https://books.google.com");

        when(mSharedPreferences.getBoolean(eq("search.site.librarything.enabled"),
                                           anyBoolean())).thenReturn(true);
        when(mSharedPreferences.getString(eq("librarything.host.url"),
                                          anyString()))
                .thenReturn("https://www.librarything.com");

        when(mSharedPreferences.getBoolean(eq("search.site.isfdb.enabled"),
                                           anyBoolean())).thenReturn(true);
        when(mSharedPreferences.getString(eq("isfdb.host.url"),
                                          anyString()))
                .thenReturn("https://www.isfdb.com");

        when(mSharedPreferences.getBoolean(eq("search.site.stripinfo.enabled"),
                                           anyBoolean())).thenReturn(true);
        when(mSharedPreferences.getString(eq("stripinfo.host.url"),
                                          anyString()))
                .thenReturn("https://www.stripinfo.be");

        when(mSharedPreferences.getBoolean(eq("search.site.kbnl.enabled"),
                                           anyBoolean())).thenReturn(true);
        when(mSharedPreferences.getString(eq("kbnl.host.url"),
                                          anyString()))
                .thenReturn("https://www.kb.nl");

        when(mSharedPreferences.getBoolean(eq("search.site.openlibrary.enabled"),
                                           anyBoolean())).thenReturn(true);
        when(mSharedPreferences.getString(eq("openlibrary.host.url"),
                                          anyString()))
                .thenReturn("https://www.openlibrary.com");

        when(mSharedPreferences.getBoolean(eq("search.site.amazon.enabled"),
                                           anyBoolean())).thenReturn(true);
        when(mSharedPreferences.getString(eq("amazon.host.url"),
                                          anyString()))
                .thenReturn("https://www.amazon.co.uk");

    }

    @Test
    void dumpEngines() {
        SearchEngineRegistry.create(mContext);

        final Collection<SearchEngineRegistry.Config> all = SearchEngineRegistry.getAll();
        for (SearchEngineRegistry.Config config : all) {
            assertNotNull(config);
            System.out.println("\n" + config);
        }
    }

    @Test
    void dumpSites() {
        SearchEngineRegistry.create(mContext);

        for (Site.Type type : Site.Type.values()) {
            final List<Site> sites = type.getSites();
            System.out.println("\n------------------------------------------\n\n" + type);

            for (Site site : sites) {
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
        when(mSharedPreferences.getString(eq("search.siteOrder.data"), isNull()))
                // deliberate added 4 and omitted 128/256
                .thenReturn("64,32,16,8,4,2,1");

        SearchEngineRegistry.create(mContext);

        final ArrayList<Site> sites = Site.Type.Data.getSites();
        // 4 should be removed, 128/256 added as loadPrefs will have been called
        assertEquals("64,32,16,8,2,1,128,256",
                     Csv.join(sites, element -> String.valueOf(element.engineId)));


        final List<Site> reordered = Site.Type.reorder(sites, "1,2,4,16,64,128,256,512");
        // 4/512 should be removed, 8/32 NOT added as loadPrefs will NOT have been called
        assertEquals("1,2,16,64,128,256",
                     Csv.join(reordered, element -> String.valueOf(element.engineId)));
    }
}
