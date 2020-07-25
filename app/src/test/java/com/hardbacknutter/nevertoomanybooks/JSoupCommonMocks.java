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
package com.hardbacknutter.nevertoomanybooks;

import androidx.annotation.NonNull;

import java.io.IOException;
import java.io.InputStream;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;

import com.hardbacknutter.nevertoomanybooks.searches.JsoupSearchEngineBase;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

public class JSoupCommonMocks
        extends CommonMocks {

    /** Helper: Load the data from the given file, and populate {@link #mRawData} */
    protected void loadData(@NonNull JsoupSearchEngineBase searchEngine,
                            @NonNull final String charsetName,
                            @NonNull final String locationHeader,
                            @NonNull final String filename) {
        Document document;
        try (InputStream is = this.getClass().getResourceAsStream(filename)) {
            assertNotNull(is);
            document = Jsoup.parse(is, charsetName, locationHeader);

            assertNotNull(document);
            assertTrue(document.hasText());

            final boolean[] fetchThumbnails = {false, false};
            searchEngine.parse(document, fetchThumbnails, mRawData);

            assertFalse(mRawData.isEmpty());

            System.out.println(mRawData);

        } catch (@NonNull final IOException e) {
            fail(e);
        }
    }
}
