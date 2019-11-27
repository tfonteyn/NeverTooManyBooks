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
package com.hardbacknutter.nevertoomanybooks.searches.isfdb;

import android.content.Context;

import androidx.annotation.NonNull;

import java.net.SocketTimeoutException;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.fail;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Fairly simple test that does an active download, and checks if the resulting page
 * has the right "location" URL afterwards.
 * There have been some hick-ups from ISFDB with redirecting (Apache web server config issues);
 * and the JSoup parser is not fully redirect proof either.
 */
class AbstractBaseTest {

    private static final String sBaseUrl = "http://www.isfdb.org";

    @Mock
    Context mContext;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.initMocks(this);

        mContext = mock(Context.class);
        when(mContext.getApplicationContext()).thenReturn(mContext);
    }


    /**
     * Search for 0-88733-160-2; which has a single edition, so should redirect to the book.
     * Resulting url should have "pl.cgi".
     */
    @Test
    void searchSingleEditionIsbn() {
        DummyLoader loader = new DummyLoader();

        String url = sBaseUrl + "/cgi-bin/se.cgi?arg=0887331602&type=ISBN";
        String resultingUrl = null;
        try {
            resultingUrl = loader.loadPage(mContext, url);
        } catch (@NonNull final SocketTimeoutException e) {
            fail(e);
        }
        assertEquals(sBaseUrl + "/cgi-bin/pl.cgi?326539", resultingUrl);
    }


    /**
     * Search for 978-1-4732-0892-6; which has two editions.
     * Resulting url should have "se.cgi".
     */
    @Test
    void searchMultiEditionIsbn() {
        DummyLoader loader = new DummyLoader();

        String url = sBaseUrl + "/cgi-bin/se.cgi?arg=9781473208926&type=ISBN";
        String resultingUrl = null;
        try {
            resultingUrl = loader.loadPage(mContext, url);
        } catch (@NonNull final SocketTimeoutException e) {
            fail(e);
        }
        assertEquals(sBaseUrl + "/cgi-bin/se.cgi?arg=9781473208926&type=ISBN", resultingUrl);
    }

    private static class DummyLoader
            extends AbstractBase {

    }
}
