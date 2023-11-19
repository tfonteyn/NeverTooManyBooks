/*
 * @Copyright 2018-2023 HardBackNutter
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

import androidx.annotation.IntRange;
import androidx.annotation.NonNull;
import androidx.arch.core.executor.testing.InstantTaskExecutorRule;
import androidx.test.filters.MediumTest;

import java.io.File;
import java.io.FileFilter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;

import com.hardbacknutter.nevertoomanybooks.DbPrep;
import com.hardbacknutter.nevertoomanybooks.ServiceLocator;
import com.hardbacknutter.nevertoomanybooks.bookdetails.ShowBookDetailsViewModel;
import com.hardbacknutter.nevertoomanybooks.booklist.style.BuiltinStyle;
import com.hardbacknutter.nevertoomanybooks.booklist.style.Style;
import com.hardbacknutter.nevertoomanybooks.core.database.DaoWriteException;
import com.hardbacknutter.nevertoomanybooks.core.database.SynchronizedDb;
import com.hardbacknutter.nevertoomanybooks.core.storage.FileUtils;
import com.hardbacknutter.nevertoomanybooks.core.storage.StorageException;
import com.hardbacknutter.nevertoomanybooks.covers.CoverStorage;
import com.hardbacknutter.nevertoomanybooks.covers.CoverVolume;
import com.hardbacknutter.nevertoomanybooks.database.dao.BookDao;
import com.hardbacknutter.nevertoomanybooks.database.dao.StylesHelper;
import com.hardbacknutter.nevertoomanybooks.entities.Author;
import com.hardbacknutter.nevertoomanybooks.entities.Book;
import com.hardbacknutter.nevertoomanybooks.entities.Bookshelf;
import com.hardbacknutter.nevertoomanybooks.entities.EntityStage;
import com.hardbacknutter.nevertoomanybooks.entities.Publisher;
import com.hardbacknutter.nevertoomanybooks.entities.TocEntry;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestRule;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/** Do NOT extend BaseSetup ! */
@SuppressWarnings({"TypeMayBeWeakened",
        "MismatchedQueryAndUpdateOfCollection",
        "MismatchedReadAndWriteOfArray",
        "OverlyBroadThrowsClause",
        "MissingJavadoc"})
@MediumTest
public class BookTest {

    private static final String NEED_A_PICTURES_DIRECTORY = "Need a pictures directory";
    private static final String NEED_A_TEMP_DIRECTORY = "Need a temp directory";
    private static final String EXT_JPG = ".jpg";
    private final Bookshelf[] bookshelf = new Bookshelf[5];
    private final long[] bookshelfId = new long[5];
    private final List<Bookshelf> bookshelfList = new ArrayList<>();
    private final Author[] author = new Author[5];
    private final long[] authorId = new long[5];
    private final List<Author> authorList = new ArrayList<>();
    private final Publisher[] publisher = new Publisher[5];
    private final long[] publisherId = new long[5];
    private final List<Publisher> publisherList = new ArrayList<>();
    private final List<TocEntry> tocEntryList = new ArrayList<>();
    private final String[] originalImageFileName = new String[2];
    private final long[] originalImageSize = new long[2];
    private final FileFilter jpgFilter = pathname -> pathname.getPath().endsWith(EXT_JPG);
    /**
     * LiveData requirement.
     */
    @Rule
    public TestRule rule = new InstantTaskExecutorRule();

    private CoverStorage coverStorage;


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

        final ServiceLocator serviceLocator = ServiceLocator.getInstance();
        final SynchronizedDb db = serviceLocator.getDb();
        coverStorage = serviceLocator.getCoverStorage();

        TestConstants.deleteTocs(db);
        TestConstants.deleteBooks(db);
        TestConstants.deleteAuthors(db);
        TestConstants.deletePublishers(db);

        final Context context = serviceLocator.getLocalizedAppContext();
        final Locale bookLocale = Locale.getDefault();

        final int actualVolume = CoverVolume.initVolume(context, 0);
        assertEquals(0, actualVolume);

        final File coverDir = coverStorage.getDir();
        assertNotNull(NEED_A_PICTURES_DIRECTORY, coverDir);

        final File tempDir = coverStorage.getTempDir();
        assertNotNull(NEED_A_TEMP_DIRECTORY, tempDir);

        // empty the temp dir
        //noinspection ResultOfMethodCallIgnored
        FileUtils.collectFiles(tempDir, jpgFilter).forEach(File::delete);

        bookshelf[0] = serviceLocator.getBookshelfDao()
                                     .getBookshelf(context, Bookshelf.HARD_DEFAULT)
                                     .orElseThrow();
        bookshelfList.clear();
        bookshelfList.add(bookshelf[0]);

        author[0] = Author.from(TestConstants.AuthorFullName(0));
        author[1] = Author.from(TestConstants.AuthorFullName(1));

        // insert author[0] but do NOT insert author[1]
        authorId[0] = serviceLocator.getAuthorDao().insert(context, author[0], bookLocale);
        authorList.clear();
        authorList.add(author[0]);

        publisher[0] = Publisher.from(TestConstants.PUBLISHER + "0");
        publisher[1] = Publisher.from(TestConstants.PUBLISHER + "1");

        // insert publisher[0] but do NOT insert publisher[1]
        publisherId[0] = serviceLocator.getPublisherDao().insert(context, publisher[0], bookLocale);
        publisherList.clear();
        publisherList.add(publisher[0]);

        final DbPrep dbPrep = new DbPrep();
        for (int i = 0; i < 2; i++) {
            final File coverFile = dbPrep.getFile(i);
            originalImageFileName[i] = coverFile.getAbsolutePath();
            originalImageSize[i] = coverFile.length();
        }


        assertTrue(bookshelf[0].getId() > 0);
        assertTrue(author[0].getId() > 0);
        assertTrue(publisher[0].getId() > 0);
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

        final ServiceLocator serviceLocator = ServiceLocator.getInstance();
        final Context context = serviceLocator.getLocalizedAppContext();
        final BookDao bookDao = serviceLocator.getBookDao();

        final File coverDir = coverStorage.getDir();
        assertNotNull(NEED_A_PICTURES_DIRECTORY, coverDir);

        final File tempDir = coverStorage.getTempDir();
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
        book.setCover(1, new File(tempDir, DbPrep.COVER[1]));
        // we're in 'Dirty' mode, so must be a temp file
        mustHaveTempCover(context, book, 1);

        assertEquals(EntityStage.Stage.Dirty, book.getStage());
        bookDao.update(context, book, Set.of());
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

        // Add a new second cover of the read-only book
        final File coverFile = new DbPrep().getFile(1);
        originalImageFileName[1] = coverFile.getAbsolutePath();
        originalImageSize[1] = coverFile.length();

        // will/must store the cover immediately
        book.setCover(1, new File(tempDir, DbPrep.COVER[1]));

        assertFalse(book.contains(Book.BKEY_TMP_FILE_SPEC[0]));
        assertFalse(book.contains(Book.BKEY_TMP_FILE_SPEC[1]));

        mustHavePersistedCover(book, 1);
    }

    private void mustHaveTempCover(@NonNull final Context context,
                                   @NonNull final Book book,
                                   @IntRange(from = 0, to = 1) final int cIdx)
            throws StorageException {

        assertTrue(book.contains(Book.BKEY_TMP_FILE_SPEC[cIdx]));
        final File tempDir = coverStorage.getTempDir();
        assertEquals(tempDir.getAbsolutePath()
                     + File.separatorChar + DbPrep.COVER[cIdx],
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
                                        @IntRange(from = 0, to = 1) final int cIdx) {
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
        final Optional<Style> s1 = helper.getStyle(BuiltinStyle.HARD_DEFAULT_UUID);
        assertTrue(s1.isPresent());

        final BookDao bookDao = serviceLocator.getBookDao();
        final long bookId = prepareAndInsertBook(context, bookDao);
        final ShowBookDetailsViewModel vm = new ShowBookDetailsViewModel();
        final Bundle args = serviceLocator.newBundle();
        args.putLong(DBKey.FK_BOOK, bookId);
        // FIXME: FAILS with Cannot invoke setValue on a background thread
        //  but the code does work in normal usage
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

        final File tempDir = coverStorage.getTempDir();
        book.setCover(0, new File(tempDir, DbPrep.COVER[0]));
        // we're in 'Dirty' mode, so must be a temp file
        mustHaveTempCover(context, book, 0);

        assertEquals(EntityStage.Stage.Dirty, book.getStage());
        final long bookId = bookDao.insert(context, book, Set.of());
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

        final File tempDir = coverStorage.getTempDir();
        final List<File> tempFiles = FileUtils.collectFiles(tempDir, jpgFilter, 10);
        // expected: 1: because "0.jpg" should be gone, but "1.jpg" will still be there
        assertEquals(1, tempFiles.size());
        assertEquals(DbPrep.COVER[1], tempFiles.get(0).getName());
    }
}
