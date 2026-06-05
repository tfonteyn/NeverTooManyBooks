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

package com.hardbacknutter.nevertoomanybooks.backup.csv;

import java.util.List;

import com.hardbacknutter.nevertoomanybooks.BaseDBTest;
import com.hardbacknutter.nevertoomanybooks.core.storage.StorageException;
import com.hardbacknutter.nevertoomanybooks.io.DataReaderException;
import com.hardbacknutter.nevertoomanybooks.utils.AppLocale;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class CsvRecordReaderGuessTest
        extends BaseDBTest {

    // 2026-05-31
    private static final String GOODREADS =
            "Book Id,Title,Author,Author l-f,Additional Authors,ISBN,ISBN13," +
            "My Rating,Average Rating,Publisher,Binding,Number of Pages," +
            "Year Published,Original Publication Year,Date Read,Date Added," +
            "Bookshelves,Bookshelves with positions,Exclusive Shelf," +
            "My Review,Spoiler,Private Notes,Read Count,Owned Copies\n";

    // Calibre 9.9.0 with some custom fields added
    private static final String CALIBRE =
            "author_sort,authors,#country,cover,timestamp,formats,isbn,id,identifiers,languages," +
            "library_name,pubdate,publisher,rating,#read,#read_progress,#ebook," +
            "series,series_index,size,#status,tags,title,title_sort,uuid\n";

    private static final String CALIBRE_WITH_COMMENTS = CALIBRE.substring(0, CALIBRE.length() - 1)
                                                        + ",comments,#test\n";

    private static final String BC =
            "\"_id\",\"author_details\",\"title\",\"isbn\",\"publisher\",\"date_published\"," +
            "\"rating\",\"bookshelf_id\",\"bookshelf\",\"read\",\"series_details\",\"pages\"," +
            "\"notes\",\"list_price\",\"anthology\",\"location\",\"read_start\",\"read_end\"," +
            "\"format\",\"signed\",\"loaned_to\",\"anthology_titles\",\"description\"," +
            "\"genre\",\"language\",\"date_added\",\"goodreads_book_id\"," +
            "\"last_goodreads_sync_date\",\"last_update_date\",\"book_uuid\",\n";

    @BeforeEach
    void setup()
            throws StorageException {
        super.setup(AppLocale.SYSTEM_LANGUAGE);
    }

    @Test
    void guessBC()
            throws DataReaderException {
        final List<String> columns = CsvRecordReader.parse(context, 0, BC);
        final CsvFormat format = CsvFormat.guess(BC, columns);

        assertEquals(CsvFormat.BC, format);
    }

    @Test
    void guessGoodreads()
            throws DataReaderException {
        final List<String> columns = CsvRecordReader.parse(context, 0, GOODREADS);
        final CsvFormat format = CsvFormat.guess(GOODREADS, columns);

        assertEquals(CsvFormat.Goodreads, format);
    }

    @Test
    void guessCalibre()
            throws DataReaderException {
        final List<String> columns = CsvRecordReader.parse(context, 0, CALIBRE);
        final CsvFormat format = CsvFormat.guess(CALIBRE, columns);

        assertEquals(CsvFormat.Calibre, format);
    }

    @Test
    void guessCalibreWithComments()
            throws DataReaderException {
        final List<String> columns = CsvRecordReader.parse(context, 0, CALIBRE_WITH_COMMENTS);
        final CsvFormat format = CsvFormat.guess(CALIBRE_WITH_COMMENTS, columns);

        assertEquals(CsvFormat.Calibre, format);
    }
}