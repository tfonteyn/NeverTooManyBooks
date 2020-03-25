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

import java.util.List;
import java.util.Locale;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.hardbacknutter.nevertoomanybooks.CommonSetup;
import com.hardbacknutter.nevertoomanybooks.searches.goodreads.GoodreadsSearchEngine;

import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

class SearchSitesTest
        extends CommonSetup {

    @BeforeEach
    public void setUp() {
        super.setUp();

        when(mSharedPreferences.getBoolean(eq("search.site.goodreads.enabled"),
                                           anyBoolean())).thenReturn(true);
        when(mSharedPreferences.getString(eq("goodreads.host.url"),
                                          anyString())).thenReturn("https://www.goodreads.com");

        when(mSharedPreferences.getBoolean(eq("search.site.googlebooks.enabled"),
                                           anyBoolean())).thenReturn(true);
        when(mSharedPreferences.getString(eq("googlebooks.host.url"),
                                          anyString())).thenReturn("https://books.google.com");

        when(mSharedPreferences.getBoolean(eq("search.site.librarything.enabled"),
                                           anyBoolean())).thenReturn(true);
        when(mSharedPreferences.getString(eq("librarything.host.url"),
                                          anyString())).thenReturn("https://www.librarything.com");

        when(mSharedPreferences.getBoolean(eq("search.site.isfdb.enabled"),
                                           anyBoolean())).thenReturn(true);
        when(mSharedPreferences.getString(eq("isfdb.host.url"),
                                          anyString())).thenReturn("https://www.isfdb.com");

        when(mSharedPreferences.getBoolean(eq("search.site.stripinfo.enabled"),
                                           anyBoolean())).thenReturn(true);
        when(mSharedPreferences.getString(eq("stripinfo.host.url"),
                                          anyString())).thenReturn("https://www.stripinfo.be");

        when(mSharedPreferences.getBoolean(eq("search.site.kbnl.enabled"),
                                           anyBoolean())).thenReturn(true);
        when(mSharedPreferences.getString(eq("kbnl.host.url"),
                                          anyString())).thenReturn("https://www.kb.nl");

        when(mSharedPreferences.getBoolean(eq("search.site.openlibrary.enabled"),
                                           anyBoolean())).thenReturn(true);
        when(mSharedPreferences.getString(eq("openlibrary.host.url"),
                                          anyString())).thenReturn("https://www.openlibrary.com");

        when(mSharedPreferences.getBoolean(eq("search.site.amazon.enabled"),
                                           anyBoolean())).thenReturn(true);
        when(mSharedPreferences.getString(eq("amazon.host.url"),
                                          anyString())).thenReturn("https://www.amazon.co.uk");
    }

    @Test
    void site() {
        Locale systemLocale = Locale.US;
        Locale userLocale = Locale.UK;

        for (SiteList.Type type : SiteList.Type.values()) {

            List<Site> sites = SiteList.getList(mContext, systemLocale, userLocale, type)
                                       .getSites(false);
            System.out.println("type=" + type);

            for (Site site : sites) {
                SearchEngine searchEngine;
                if (site.id == SearchSites.GOODREADS) {
                    searchEngine = new GoodreadsSearchEngine(mContext);
                } else {
                    searchEngine = site.getSearchEngine();
                }
                System.out.println(site + ", locale=" + searchEngine.getLocale(mContext));
            }
        }
    }
}
