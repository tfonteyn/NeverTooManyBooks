/*
 * @Copyright 2019 HardBackNutter
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

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

class SearchSitesTest
        extends CommonSetup {

    @BeforeEach
    @Override
    public void setUp() {
        super.setUp();

        when(mSharedPreferences.getBoolean(eq("search.site.goodreads.enabled"),
                                           anyBoolean())).thenReturn(true);
        when(mSharedPreferences.getBoolean(eq("search.site.googlebooks.enabled"),
                                           anyBoolean())).thenReturn(true);
        when(mSharedPreferences.getBoolean(eq("search.site.librarything.enabled"),
                                           anyBoolean())).thenReturn(true);
        when(mSharedPreferences.getBoolean(eq("search.site.isfdb.enabled"),
                                           anyBoolean())).thenReturn(true);
        when(mSharedPreferences.getBoolean(eq("search.site.stripinfo.enabled"),
                                           anyBoolean())).thenReturn(true);
        when(mSharedPreferences.getBoolean(eq("search.site.kbnl.enabled"),
                                           anyBoolean())).thenReturn(true);
        when(mSharedPreferences.getBoolean(eq("search.site.openlibrary.enabled"),
                                           anyBoolean())).thenReturn(true);

    }

    @Test
    void site() {
        for (SiteList.Type type : SiteList.Type.values()) {
            List<Site> sites = SiteList.getSites(mContext, type);
            System.out.println(sites);
        }
    }
}
