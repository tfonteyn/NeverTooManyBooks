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

import android.database.Cursor;
import android.net.Uri;
import android.os.LocaleList;

import java.io.File;
import java.io.IOException;
import java.security.cert.CertificateException;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

import com.hardbacknutter.nevertoomanybooks.BaseDBTest;
import com.hardbacknutter.nevertoomanybooks.ServiceLocator;
import com.hardbacknutter.nevertoomanybooks.TestProgressListener;
import com.hardbacknutter.nevertoomanybooks.backup.ImportHelper;
import com.hardbacknutter.nevertoomanybooks.backup.ImportResults;
import com.hardbacknutter.nevertoomanybooks.backup.TestUtils;
import com.hardbacknutter.nevertoomanybooks.bookreadstatus.ReadingProgress;
import com.hardbacknutter.nevertoomanybooks.core.network.CredentialsException;
import com.hardbacknutter.nevertoomanybooks.core.parsers.RealNumberParser;
import com.hardbacknutter.nevertoomanybooks.core.storage.StorageException;
import com.hardbacknutter.nevertoomanybooks.core.utils.ISBN;
import com.hardbacknutter.nevertoomanybooks.core.utils.LocaleListUtils;
import com.hardbacknutter.nevertoomanybooks.database.DBKey;
import com.hardbacknutter.nevertoomanybooks.database.dao.BookDao;
import com.hardbacknutter.nevertoomanybooks.database.dao.BookshelfDao;
import com.hardbacknutter.nevertoomanybooks.entities.Author;
import com.hardbacknutter.nevertoomanybooks.entities.Book;
import com.hardbacknutter.nevertoomanybooks.entities.Bookshelf;
import com.hardbacknutter.nevertoomanybooks.entities.Identifier;
import com.hardbacknutter.nevertoomanybooks.entities.Publisher;
import com.hardbacknutter.nevertoomanybooks.entities.Series;
import com.hardbacknutter.nevertoomanybooks.entities.Tag;
import com.hardbacknutter.nevertoomanybooks.io.ArchiveMetaData;
import com.hardbacknutter.nevertoomanybooks.io.BasicMetaData;
import com.hardbacknutter.nevertoomanybooks.io.DataReader;
import com.hardbacknutter.nevertoomanybooks.io.DataReaderException;
import com.hardbacknutter.nevertoomanybooks.io.RecordType;
import com.hardbacknutter.nevertoomanybooks.utils.AppLocale;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CalibreCsvImportTest
        extends BaseDBTest {

    private static final String TAG = "CalibreCsvImportTest";

    private BookDao bookDao;
    private int booksPresent;

    @BeforeEach
    void setup()
            throws StorageException {
        super.setup(AppLocale.SYSTEM_LANGUAGE);

        final ServiceLocator locator = ServiceLocator.getInstance();

        bookDao = locator.getBookDao();
        booksPresent = bookDao.count();

        final BookshelfDao bookshelfDao = locator.getBookshelfDao();
        final Optional<Bookshelf> cal = bookshelfDao.findByName("Calibre Library");
        if (cal.isPresent()) {
            final Bookshelf bookshelf = cal.get();
            final long id = bookshelf.getId();
            final List<Long> bookIds = bookshelfDao.getBookIds(id);
            bookIds.forEach(bik -> bookDao.delete(bik));
            bookshelfDao.delete(context, bookshelf);
        }
    }

    @SuppressWarnings("LocalCanBeFinal")
    @Test
    void calibre()
            throws DataReaderException, IOException,
                   StorageException, CredentialsException, CertificateException {

        final LocaleList userLocales = context.getResources().getConfiguration().getLocales();
        final List<Locale> allLocales = LocaleListUtils.asList(userLocales);
        final RealNumberParser ratingNumberParser = new RealNumberParser(allLocales);

        File file;
        ImportHelper importHelper;
        Optional<ArchiveMetaData> oMetaData;
        ArchiveMetaData metaData;
        ImportResults importResults;

        file = TestUtils.createFile(
                com.hardbacknutter.nevertoomanybooks.test.R.raw.calibre,
                new File(context.getCacheDir(), "calibre.csv"));

        importHelper = new ImportHelper(context, Uri.fromFile(file));

        oMetaData = importHelper.readMetaData(context);
        assertTrue(oMetaData.isPresent());
        metaData = oMetaData.get();
        assertNotNull(metaData);
        Assertions.assertEquals(CsvFormat.Calibre,
                                metaData.getData().getParcelable(CsvFormat.BKEY));
        assertTrue(metaData.getData().containsKey(BasicMetaData.SUPPORTS_DATE_LAST_UPDATED));
        assertTrue(metaData.getData().getBoolean(BasicMetaData.SUPPORTS_DATE_LAST_UPDATED));

        importHelper.addRecordType(RecordType.Books);
        importHelper.setUpdateOption(DataReader.Updates.Overwrite);
        importResults = importHelper.read(context, new TestProgressListener(TAG));

        assertEquals(18, importResults.booksProcessed);
        assertEquals(18, importResults.getBooksCreated());
        assertEquals(0, importResults.getBooksUpdated());
        assertEquals(0, importResults.booksSkipped);
        assertEquals(0, importResults.booksFailed);
        assertEquals(booksPresent + 18, bookDao.count());

        // "Zelazny, Roger","Roger Zelazny","","/path/to/cover.jpg",
        // "2020-06-17T18:49:53+01:00","epub","9780061936456","39",
        // "amazon:0061936456,isbn:9780061936456,google:V-A-l-a7qE0C",
        // "eng","Calibre Library","2010-04-13T01:00:00+01:00",
        // "HarperCollins","4","","0%","","","1.0","277135","",
        // "Fiction, Science Fiction",
        // "Creatures of Light and Darkness","Creatures of Light and Darkness",
        // "8578dcf0-cd1c-404c-beed-d71e89393cfd"

        try (Cursor cursor = bookDao.fetchByIsbn(List.of(new ISBN("9780061936456", true)))) {
            assertEquals(1, cursor.getCount());
            assertTrue(cursor.moveToNext());
            final Book book = Book.from(cursor);

            assertEquals("Creatures of Light and Darkness", book.getString(DBKey.TITLE, null));
            assertEquals("9780061936456", book.getString(DBKey.ISBN, null));
            assertEquals(4.0f, book.getFloat(DBKey.RATING, ratingNumberParser), 0.1f);
            assertEquals("epub", book.getString(DBKey.FORMAT, null));
            assertEquals("eng", book.getString(DBKey.LANGUAGE, null));
            assertEquals("2010-04-13", book.getString(DBKey.PUBLICATION_DATE, null));

            assertEquals("0061936456", book.requireIdentifierValue(Identifier.SID_ASIN));
            assertEquals("V-A-l-a7qE0C", book.requireIdentifierValue(Identifier.SID_GOOGLE));

            final List<Tag> allTags = book.getTags();
            assertEquals(2, allTags.size());
            Tag tag;
            tag = allTags.get(0);
            assertEquals("Fiction", tag.getName());
            tag = allTags.get(1);
            assertEquals("Science Fiction", tag.getName());

            final List<Publisher> allPublishers = book.getPublishers();
            assertEquals(1, allPublishers.size());
            assertEquals("HarperCollins", allPublishers.get(0).getName());

            final List<Author> allAuthors = book.getAuthors();
            assertEquals(1, allAuthors.size());
            Author author;
            author = allAuthors.get(0);
            assertEquals("Zelazny", author.getFamilyName());
            assertEquals("Roger", author.getGivenNames());

            final List<Bookshelf> allBookshelves = book.getBookshelves();
            assertEquals(1, allBookshelves.size());
            Bookshelf bookshelf;
            bookshelf = allBookshelves.get(0);
            assertEquals("Calibre Library", bookshelf.getName());
        }

        // "Tolkien, J. R. R. & Tolkien, Christopher","J. R. R. Tolkien & Christopher Tolkien","",
        // "/path/to/cover.jpg","2026-01-02T21:47:10+00:00","mobi","9780044407263","1304",
        // "isbn:9780044407263,mobi-asin:6e645e3a-fa21-4d11-9047-fafe3ae302f4","eng",
        // "Calibre Library","2018-08-06T17:00:00+01:00","Unwin Hyman","","","37%","",
        // "Bombadil","1.0","369076","",
        // "Fantasy, Fiction, Lord of the Rings, Middle Earth (Imaginary place)",
        // "The Adventures of Tom Bombadil, and Other Verses From the Red Book",
        // "Adventures of Tom Bombadil, and Other Verses From the Red Book, The",
        // "b8fbf2a5-dc38-4957-bafe-6d1a92deac7b"

        try (Cursor cursor = bookDao.fetchByIsbn(List.of(new ISBN("9780044407263", true)))) {
            assertEquals(1, cursor.getCount());
            assertTrue(cursor.moveToNext());
            final Book book = Book.from(cursor);

            assertEquals("The Adventures of Tom Bombadil, and Other Verses From the Red Book",
                         book.getString(DBKey.TITLE, null));
            assertEquals("9780044407263", book.getString(DBKey.ISBN, null));
            assertEquals("mobi", book.getString(DBKey.FORMAT, null));
            assertEquals("eng", book.getString(DBKey.LANGUAGE, null));
            assertEquals("2018-08-06", book.getString(DBKey.PUBLICATION_DATE, null));

            final ReadingProgress readingProgress = book.getReadingProgress();
            assertTrue(readingProgress.asPercentage());
            assertEquals(37, readingProgress.getPercentage());

            assertEquals("6e645e3a-fa21-4d11-9047-fafe3ae302f4",
                         book.requireIdentifierValue(Identifier.SID_ASIN));

            final List<Tag> allTags = book.getTags();
            assertEquals(4, allTags.size());
            Tag tag;
            tag = allTags.get(0);
            assertEquals("Fantasy", tag.getName());
            tag = allTags.get(1);
            assertEquals("Fiction", tag.getName());
            tag = allTags.get(2);
            assertEquals("Lord of the Rings", tag.getName());
            tag = allTags.get(3);
            assertEquals("Middle Earth (Imaginary place)", tag.getName());

            final List<Publisher> allPublishers = book.getPublishers();
            assertEquals(1, allPublishers.size());
            assertEquals("Unwin Hyman", allPublishers.get(0).getName());

            final List<Series> allSeries = book.getSeries();
            assertEquals(1, allSeries.size());
            final Series series = allSeries.get(0);
            assertEquals("Bombadil", series.getTitle());
            assertEquals("1.0", series.getNumber());

            final List<Author> allAuthors = book.getAuthors();
            assertEquals(2, allAuthors.size());
            Author author;

            author = allAuthors.get(0);
            assertEquals("Tolkien", author.getFamilyName());
            assertEquals("J. R. R.", author.getGivenNames());
            author = allAuthors.get(1);
            // wrong order, see note in DefaultBookCoder
            assertEquals("Tolkien", author.getFamilyName());
            assertEquals("Christopher", author.getGivenNames());

            final List<Bookshelf> allBookshelves = book.getBookshelves();
            assertEquals(1, allBookshelves.size());
            Bookshelf bookshelf;
            bookshelf = allBookshelves.get(0);
            assertEquals("Calibre Library", bookshelf.getName());
        }
    }
}
