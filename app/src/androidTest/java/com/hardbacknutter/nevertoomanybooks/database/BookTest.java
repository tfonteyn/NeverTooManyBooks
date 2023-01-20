/*
 * @Copyright 2018-2022 HardBackNutter
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
package com.hardbacknutter.nevertoomanybooks.database;

import android.content.Context;
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.test.filters.MediumTest;

import java.io.File;
import java.io.FileFilter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

import com.hardbacknutter.nevertoomanybooks.ServiceLocator;
import com.hardbacknutter.nevertoomanybooks.bookdetails.ShowBookDetailsViewModel;
import com.hardbacknutter.nevertoomanybooks.booklist.style.BuiltinStyle;
import com.hardbacknutter.nevertoomanybooks.booklist.style.Style;
import com.hardbacknutter.nevertoomanybooks.booklist.style.StylesHelper;
import com.hardbacknutter.nevertoomanybooks.covers.CoverDir;
import com.hardbacknutter.nevertoomanybooks.database.dao.BookDao;
import com.hardbacknutter.nevertoomanybooks.database.dao.DaoWriteException;
import com.hardbacknutter.nevertoomanybooks.database.dbsync.SynchronizedDb;
import com.hardbacknutter.nevertoomanybooks.entities.Author;
import com.hardbacknutter.nevertoomanybooks.entities.Book;
import com.hardbacknutter.nevertoomanybooks.entities.Bookshelf;
import com.hardbacknutter.nevertoomanybooks.entities.EntityStage;
import com.hardbacknutter.nevertoomanybooks.entities.Publisher;
import com.hardbacknutter.nevertoomanybooks.entities.TocEntry;
import com.hardbacknutter.nevertoomanybooks.utils.FileUtils;
import com.hardbacknutter.nevertoomanybooks.utils.exceptions.StorageException;

import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/** Do NOT extend BaseSetup ! */
@SuppressWarnings({"TypeMayBeWeakened",
        "MismatchedQueryAndUpdateOfCollection",
        "MismatchedReadAndWriteOfArray"})
@MediumTest
public class BookTest {

    private static final String NEED_TWO_FILE =
            "pictures directory must contain at least two file to use for testing";
    private static final String NEED_A_PICTURES_DIRECTORY = "Need a pictures directory";
    private static final String NEED_A_TEMP_DIRECTORY = "Need a temp directory";
    private static final String EXT_JPG = ".jpg";

    private final Bookshelf[] bookshelf = new Bookshelf[5];
    private final long[] bookshelfId = new long[5];
    private final ArrayList<Bookshelf> bookshelfList = new ArrayList<>();

    private final Author[] author = new Author[5];
    private final long[] authorId = new long[5];
    private final ArrayList<Author> authorList = new ArrayList<>();

    private final Publisher[] publisher = new Publisher[5];
    private final long[] publisherId = new long[5];
    private final ArrayList<Publisher> publisherList = new ArrayList<>();

    private final ArrayList<TocEntry> tocEntryList = new ArrayList<>();

    private final String[] originalImageFileName = new String[2];
    private final long[] originalImageSize = new long[2];
    private final FileFilter jpgFilter = pathname -> pathname.getPath().endsWith(EXT_JPG);

    /**
     * Clean the database.
     * Empty the temp directory.
     * Copy two pictures from the Pictures directory to the temp directory.
     */
    @Before
    public void setup()
            throws IOException, StorageException, DaoWriteException {

        bookshelfList.clear();
        authorList.clear();
        publisherList.clear();
        tocEntryList.clear();

        final ServiceLocator sl = ServiceLocator.getInstance();
        final SynchronizedDb db = sl.getDb();

        TestConstants.deleteTocs(db);
        TestConstants.deleteBooks(db);
        TestConstants.deleteAuthors(db);
        TestConstants.deletePublishers(db);

        final Context context = sl.getLocalizedAppContext();
        final Locale bookLocale = Locale.getDefault();

        final int actualVolume = CoverDir.initVolume(context, 0);
        assertEquals(0, actualVolume);

        final File coverDir = CoverDir.getDir(context);
        assertNotNull(NEED_A_PICTURES_DIRECTORY, coverDir);

        final File tempDir = CoverDir.getTemp(context);
        assertNotNull(NEED_A_TEMP_DIRECTORY, tempDir);

        // empty the temp dir
        //noinspection ResultOfMethodCallIgnored
        FileUtils.collectFiles(tempDir, jpgFilter).forEach(File::delete);


        bookshelf[0] = Bookshelf.getBookshelf(context, Bookshelf.DEFAULT);
        bookshelfList.clear();
        bookshelfList.add(bookshelf[0]);

        author[0] = Author.from(TestConstants.AuthorFullName(0));
        author[1] = Author.from(TestConstants.AuthorFullName(1));

        // insert author[0] but do NOT insert author[1]
        authorId[0] = sl.getAuthorDao().insert(context, author[0], bookLocale);
        authorList.clear();
        authorList.add(author[0]);

        publisher[0] = Publisher.from(TestConstants.PUBLISHER + "0");
        publisher[1] = Publisher.from(TestConstants.PUBLISHER + "1");

        // insert publisher[0] but do NOT insert publisher[1]
        publisherId[0] = sl.getPublisherDao().insert(context, publisher[0], bookLocale);
        publisherList.clear();
        publisherList.add(publisher[0]);


        final List<File> files = FileUtils.collectFiles(coverDir, jpgFilter, 10);
        assertTrue(NEED_TWO_FILE, files.size() > 1);

        prepareTempCover(context, files, 0);
        prepareTempCover(context, files, 1);

        assertTrue(bookshelf[0].getId() > 0);
        assertTrue(author[0].getId() > 0);
        assertTrue(publisher[0].getId() > 0);
    }

    /**
     * Copy a file from the Pictures directory to the temp with a new/temp name.
     *
     * @param files from the Pictures directory
     * @param cIdx  cover index to create
     *
     * @throws IOException on generic/other IO failures
     */
    private void prepareTempCover(@NonNull final Context context,
                                  @NonNull final List<File> files,
                                  final int cIdx)
            throws IOException, StorageException {

        originalImageFileName[cIdx] = files.get(cIdx).getAbsolutePath();
        final File srcFile = new File(originalImageFileName[cIdx]);
        originalImageSize[cIdx] = srcFile.length();

        FileUtils.copy(srcFile, new File(CoverDir.getTemp(context), TestConstants.COVER[cIdx]));
    }

    /**
     * <ol>
     *     <li>Insert a book on the default bookshelf, with 1 author,
     *          1 publisher, 1 front cover image.</li>
     *     <li>Retrieve it by id and test.</li>
     *     <li>update the retrieved book, change title, add author, add back cover</li>
     *     <li>Retrieve it by id and test.</li>
     *     <li>Delete the back cover while the book is {@link EntityStage.Stage#Clean}</li>
     *
     * </ol>
     */
    @Test
    public void book()
            throws DaoWriteException, IOException, StorageException {

        final ServiceLocator sl = ServiceLocator.getInstance();
        final Context context = sl.getLocalizedAppContext();
        final BookDao bookDao = sl.getBookDao();

        final File coverDir = CoverDir.getDir(context);
        assertNotNull(NEED_A_PICTURES_DIRECTORY, coverDir);

        final File tempDir = CoverDir.getTemp(context);
        assertNotNull(NEED_A_TEMP_DIRECTORY, tempDir);

        // Do the initial insert and test it
        final long bookId = prepareAndInsertBook(context, bookDao);
        Book book = Book.from(bookId);
        assertEquals(bookId, book.getId());
        checkBookAfterInitialInsert(context, book);

        List<Author> authors;
        String uuid;
        final List<Bookshelf> bookshelves;
        final List<Publisher> publishers;
        final File[] tempFiles;

        /*
         * update the stored book
         */
        book.setStage(EntityStage.Stage.WriteAble);
        book.putString(DBKey.TITLE, TestConstants.BOOK_TITLE + "0_upd");
        book.setStage(EntityStage.Stage.Dirty);

        authors = book.getAuthors();
        authors.add(author[1]);

        // the book already has a front cover, now add a back cover
        book.setCover(1, new File(tempDir, TestConstants.COVER[1]));
        // we're in 'Dirty' mode, so must be a temp file
        mustHaveTempCover(context, book, 1);

        assertEquals(EntityStage.Stage.Dirty, book.getStage());
        bookDao.update(context, book);
        book.setStage(EntityStage.Stage.Clean);

        /*
         * test the updated book
         */
        book = Book.from(bookId);
        assertEquals(bookId, book.getId());

        uuid = book.getString(DBKey.BOOK_UUID, null);
        assertNotNull(uuid);
        assertFalse(uuid.isEmpty());

        assertEquals(TestConstants.BOOK_TITLE + "0_upd", book.getTitle());
        bookshelves = book.getBookshelves();
        assertEquals(1, bookshelves.size());
        assertEquals(bookshelf[0], bookshelves.get(0));
        authors = book.getAuthors();
        assertEquals(2, authors.size());
        assertEquals(author[0], authors.get(0));
        assertEquals(author[1], authors.get(1));
        publishers = book.getPublishers();
        assertEquals(1, publishers.size());
        assertEquals(publisher[0], publishers.get(0));

        mustHavePersistedCover(book, 0);
        mustHavePersistedCover(book, 1);

        // We've used both temp files, so both files should be gone now
        tempFiles = tempDir.listFiles(jpgFilter);
        assertNotNull(tempFiles);
        assertEquals(0, tempFiles.length);


        /*
         * Delete the second cover of the read-only book
         */
        book = Book.from(bookId);
        assertEquals(bookId, book.getId());

        uuid = book.getString(DBKey.BOOK_UUID, null);
        assertNotNull(uuid);
        assertFalse(uuid.isEmpty());

        assertFalse(book.contains(Book.BKEY_TMP_FILE_SPEC[0]));
        assertFalse(book.contains(Book.BKEY_TMP_FILE_SPEC[1]));

        // sanity check the cover is really there
        mustHavePersistedCover(book, 1);

        book.removeCover(1);

        assertFalse(book.contains(Book.BKEY_TMP_FILE_SPEC[0]));
        assertFalse(book.contains(Book.BKEY_TMP_FILE_SPEC[1]));

        //the back cover must be gone
        assertFalse(new File(coverDir, uuid + "_1" + EXT_JPG).exists());

        // the front cover should still be there
        mustHavePersistedCover(book, 0);

        /*
         * Add a new second cover of the read-only book
         */
        final List<File> files = FileUtils.collectFiles(coverDir, jpgFilter, 10);
        prepareTempCover(context, files, 1);

        // will/must store the cover immediately
        book.setCover(1, new File(tempDir, TestConstants.COVER[1]));

        assertFalse(book.contains(Book.BKEY_TMP_FILE_SPEC[0]));
        assertFalse(book.contains(Book.BKEY_TMP_FILE_SPEC[1]));

        mustHavePersistedCover(book, 1);
    }

    private void mustHaveTempCover(@NonNull final Context context,
                                   @NonNull final Book book,
                                   final int cIdx)
            throws StorageException {

        assertTrue(book.contains(Book.BKEY_TMP_FILE_SPEC[cIdx]));
        assertEquals(CoverDir.getTemp(context).getAbsolutePath()
                     + File.separatorChar + TestConstants.COVER[cIdx],
                     book.getString(Book.BKEY_TMP_FILE_SPEC[cIdx], null));
    }

    /**
     * Check if the book has an actual cover file which exists with the correct name
     * and has the correct length.
     *
     * @param book to check
     * @param cIdx 0..n image index
     */
    private void mustHavePersistedCover(@NonNull final Book book,
                                        final int cIdx) {
        // we're testing a permanent file, the temp string must not exist
        assertFalse(book.contains(Book.BKEY_TMP_FILE_SPEC[cIdx]));

        final Optional<File> oCover = book.getCover(cIdx);
        assertTrue(oCover.isPresent());
        final File cover = oCover.get();
        assertNotNull(cover);
        assertTrue(cover.exists());

        assertEquals(originalImageSize[cIdx], cover.length());

        final String uuid = book.getString(DBKey.BOOK_UUID, null);
        assertNotNull(uuid);
        assertFalse(uuid.isEmpty());
        final String expectedFilename = uuid + (cIdx == 0 ? "" : "_" + cIdx) + EXT_JPG;
        assertEquals(expectedFilename, cover.getName());
    }

    @Test
    public void showBookVM()
            throws DaoWriteException, StorageException, IOException {
        final ServiceLocator serviceLocator = ServiceLocator.getInstance();
        final Context context = serviceLocator.getLocalizedAppContext();

        final StylesHelper helper = serviceLocator.getStyles();
        final Optional<Style> s1 = helper.getStyle(context, BuiltinStyle.UUID_FOR_TESTING_ONLY);
        assertTrue(s1.isPresent());

        final BookDao bookDao = serviceLocator.getBookDao();
        final long bookId = prepareAndInsertBook(context, bookDao);
        final ShowBookDetailsViewModel vm = new ShowBookDetailsViewModel();
        final Bundle args = ServiceLocator.newBundle();
        args.putLong(DBKey.FK_BOOK, bookId);

        vm.init(context, args, s1.get());
        final Book retrieved = vm.getBook();
        assertEquals(bookId, retrieved.getId());
        checkBookAfterInitialInsert(context, retrieved);
    }

    /*
     * Create and insert a book.
     */
    private long prepareAndInsertBook(@NonNull final Context context,
                                      @NonNull final BookDao bookDao)
            throws DaoWriteException, StorageException, IOException {

        final Book book = new Book();
        book.setStage(EntityStage.Stage.WriteAble);
        book.putString(DBKey.TITLE, TestConstants.BOOK_TITLE + "0");
        book.setStage(EntityStage.Stage.Dirty);

        book.putLong(DBKey.SID_ISFDB, TestConstants.BOOK_ISFDB_123);
        book.putString(DBKey.SID_LCCN, TestConstants.BOOK_LCCN_0);

        book.setBookshelves(bookshelfList);
        book.setAuthors(authorList);
        book.setPublishers(publisherList);

        book.setCover(0, new File(CoverDir.getTemp(context), TestConstants.COVER[0]));
        // we're in 'Dirty' mode, so must be a temp file
        mustHaveTempCover(context, book, 0);

        assertEquals(EntityStage.Stage.Dirty, book.getStage());
        final long bookId = bookDao.insert(context, book);
        book.setStage(EntityStage.Stage.Clean);

        assertTrue(bookId > 0);
        assertEquals(book.getId(), bookId);

        return bookId;
    }

    private void checkBookAfterInitialInsert(@NonNull final Context context,
                                             @NonNull final Book book)
            throws StorageException {

        assertEquals(EntityStage.Stage.Clean, book.getStage());

        final String uuid = book.getString(DBKey.BOOK_UUID, null);
        assertNotNull(uuid);
        assertFalse(uuid.isEmpty());
        assertEquals(TestConstants.BOOK_TITLE + "0", book.getTitle());

        assertEquals(TestConstants.BOOK_ISFDB_123, book.getLong(DBKey.SID_ISFDB));

        // not saved, hence null
        assertNull(book.getString(DBKey.SID_LCCN, null));


        final List<Bookshelf> bookshelves = book.getBookshelves();
        assertEquals(1, bookshelves.size());
        assertEquals(bookshelf[0], bookshelves.get(0));

        final List<Author> authors = book.getAuthors();
        assertEquals(1, authors.size());
        assertEquals(author[0], authors.get(0));

        final List<Publisher> publishers = book.getPublishers();
        assertEquals(1, publishers.size());
        assertEquals(publisher[0], publishers.get(0));

        mustHavePersistedCover(book, 0);
        assertFalse(book.contains(Book.BKEY_TMP_FILE_SPEC[1]));

        final List<File> tempFiles =
                FileUtils.collectFiles(CoverDir.getTemp(context), jpgFilter, 10);
        // expected: 1: because "0.jpg" should be gone, but "1.jpg" will still be there
        assertEquals(1, tempFiles.size());
        assertEquals(TestConstants.COVER[1], tempFiles.get(0).getName());
    }
}
