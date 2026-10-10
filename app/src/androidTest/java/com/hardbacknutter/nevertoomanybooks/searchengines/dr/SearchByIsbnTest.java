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

package com.hardbacknutter.nevertoomanybooks.searchengines.dr;

import android.content.Context;

import androidx.test.platform.app.InstrumentationRegistry;

import com.hardbacknutter.nevertoomanybooks.TestProgressListener;
import com.hardbacknutter.nevertoomanybooks.database.DBKey;
import com.hardbacknutter.nevertoomanybooks.entities.Book;
import com.hardbacknutter.nevertoomanybooks.searchengines.BookSearchCriteria;
import com.hardbacknutter.nevertoomanybooks.searchengines.CoverFileSpecArray;
import com.hardbacknutter.nevertoomanybooks.searchengines.EngineId;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Live read-only lookup; never saves a book or changes user preferences. */
class SearchByIsbnTest {
    @Test
    void findsRequestedIsbnAndCover() throws Exception {
        final Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        final DrSearchEngine engine = EngineId.Dr.createSearchEngine(context);
        engine.setCaller(new TestProgressListener("DrLiveTest"));
        final BookSearchCriteria criteria = new BookSearchCriteria();
        criteria.setRawProductCode("9786052361795");
        assertTrue(criteria.getProductCode().isIsbn());
        criteria.setFetchCovers(new boolean[]{true, false, false, false});
        final Book book = engine.searchByIsbn(context, criteria);
        assertEquals("Milena'ya Mektuplar", book.getString(DBKey.TITLE));
        assertEquals("9786052361795", book.getRawProductCode());
        assertEquals("Franz Kafka", book.getAuthors().get(0).getFormattedName(true));
        assertFalse(CoverFileSpecArray.getList(book, 0).isEmpty());
    }

    @Test
    void findsAnotherEdition() throws Exception {
        final Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        final DrSearchEngine engine = EngineId.Dr.createSearchEngine(context);
        engine.setCaller(new TestProgressListener("DrLiveTest"));
        final BookSearchCriteria criteria = new BookSearchCriteria();
        criteria.setRawProductCode("9789753638029");
        assertTrue(criteria.getProductCode().isIsbn());
        criteria.setFetchCovers(new boolean[DBKey.NR_OF_BOOK_COVERS]);
        final Book book = engine.searchByIsbn(context, criteria);
        assertEquals("9789753638029", book.getRawProductCode());
        assertFalse(book.getString(DBKey.TITLE).isBlank());
    }
}
