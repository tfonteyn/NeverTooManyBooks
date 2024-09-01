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
package com.hardbacknutter.nevertoomanybooks.searchengines;

import android.content.Context;
import android.util.Log;

import java.util.List;

import com.hardbacknutter.nevertoomanybooks.ServiceLocator;

import org.junit.Test;

import static org.junit.Assert.assertNotNull;

@SuppressWarnings("MissingJavadoc")
public class EngineIdTest {

    private static final String TAG = "EngineIdTest";

    @Test
    public void dumpEngines() {
        SearchEngineConfig.getAll().forEach(config -> {
            assertNotNull(config);
            Log.d(TAG, "\n" + config);
        });
    }

    @Test
    public void dumpSites() {
        final Context context = ServiceLocator.getInstance().getAppContext();

        for (final Site.Type type : Site.Type.values()) {
            final List<Site> sites = type.getSites();
            Log.d(TAG, "\n------------------------------------------\n\n" + type);

            for (final Site site : sites) {
                final EngineId engineId = site.getEngineId();
                final SearchEngineConfig config = engineId.getConfig();
                assertNotNull(config);
                final SearchEngine searchEngine = engineId.createSearchEngine(context);
                assertNotNull(searchEngine);

                Log.d(TAG, "\n" + config + "\n\n" + site + "\n\n" + searchEngine);
            }
        }
    }
}
