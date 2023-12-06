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
import com.hardbacknutter.nevertoomanybooks.bookdetails.ShowBookDetailsViewModel;
import com.hardbacknutter.nevertoomanybooks.booklist.style.BuiltinStyle;
import com.hardbacknutter.nevertoomanybooks.booklist.style.Style;
import com.hardbacknutter.nevertoomanybooks.core.database.DaoWriteException;
import com.hardbacknutter.nevertoomanybooks.core.storage.FileUtils;
import com.hardbacknutter.nevertoomanybooks.core.storage.StorageException;
import com.hardbacknutter.nevertoomanybooks.covers.CoverStorage;
import com.hardbacknutter.nevertoomanybooks.covers.CoverVolume;
import com.hardbacknutter.nevertoomanybooks.database.dao.BookDao;
import com.hardbacknutter.nevertoomanybooks.database.dao.LoaneeDao;
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

@SuppressWarnings({"TypeMayBeWeakened",
        "MismatchedQueryAndUpdateOfCollection",
        "MismatchedReadAndWriteOfArray",
        "OverlyBroadThrowsClause",
        "MissingJavadoc"})
@MediumTest
public class BookTest
        extends BaseSetup {

    private static final String EXT_JPG = ".jpg";
    private final List<Bookshelf> bookshelfList = new ArrayList<>();
    private final List<Author> authorList = new ArrayList<>();
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
        super.setup();

        coverStorage = serviceLocator.getCoverStorage();

        final int actualVolume = CoverVolume.initVolume(context, 0);
        assertEquals(0, actualVolume);

        final File coverDir = coverStorage.getDir();
        assertNotNull("Need a cover directory", coverDir);

        final File tempDir = coverStorage.getTempDir();
        assertNotNull("Need a temp directory", tempDir);

        // empty the temp dir
        //noinspection ResultOfMethodCallIgnored
        FileUtils.collectFiles(tempDir, jpgFilter).forEach(File::delete);

        final Locale bookLocale = Locale.getDefault();

        // bookshelf[0] is the hard default
        bookshelfList.clear();
        bookshelfList.add(bookshelfArray[0]);

        // insert ONLY author[0]
        authorIdArray[0] = serviceLocator.getAuthorDao().insert(context, authorArray[0],
                                                                bookLocale);
        authorList.clear();
        authorList.add(authorArray[0]);

        // insert ONLY publisher[0]
        publisherIdArray[0] = serviceLocator.getPublisherDao().insert(context, publisherArray[0],
                                                                      bookLocale);
        publisherList.clear();
        publisherList.add(publisherArray[0]);

        // No tocs
        tocEntryList.clear();

        final DbPrep dbPrep = new DbPrep();
        for (int i = 0; i < 2; i++) {
            final File coverFile = dbPrep.getFile(i);
            originalImageFileName[i] = coverFile.getAbsolutePath();
            originalImageSize[i] = coverFile.length();
        }

        assertTrue(bookshelfArray[0].getId() > 0);
        assertTrue(authorArray[0].getId() > 0);
        assertTrue(publisherArray[0].getId() > 0);
    }

    /**
     * <ol>
     *     <li>Insert a book on the default bookshelf, with 1 author,
     *          1 publisher, 1 front cover image.</li>
     *     <li>Retrieve it by id and test.</li>
     *     <li>update the retrieved book, change title, add author</li>
     *     <li>Retrieve it by id and test.</li>
     * </ol>
     */
    @Test
    public void book()
            throws DaoWriteException, IOException, StorageException {

        final int bookIdx = 0;

        final BookDao bookDao = serviceLocator.getBookDao();

        // Do the initial insert and test it
        final long bookId = prepareAndInsertBook(context, bookDao, bookIdx);
        Book book = Book.from(bookId);
        assertEquals(bookId, book.getId());
        assertBookMatchesInitialInsert(book, bookIdx);

        List<Author> authors;
        /*
         * update the stored book; change the title and add an Author.
         */
        book.setStage(EntityStage.Stage.WriteAble);
        book.putString(DBKey.TITLE, TestConstants.BOOK_TITLE[bookIdx] + "_upd");
        book.setStage(EntityStage.Stage.Dirty);

        authors = book.getAuthors();
        authors.add(this.authorArray[1]);

        assertEquals(EntityStage.Stage.Dirty, book.getStage());
        bookDao.update(context, book, Set.of());
        book.setStage(EntityStage.Stage.Clean);

        /*
         * test the updated book
         */
        book = Book.from(bookId);
        assertEquals(bookId, book.getId());

        final String uuid = book.getString(DBKey.BOOK_UUID, null);
        assertNotNull(uuid);
        assertFalse(uuid.isEmpty());

        assertEquals(TestConstants.BOOK_TITLE[bookIdx] + "_upd", book.getTitle());

        final List<Bookshelf> bookshelves = book.getBookshelves();
        assertEquals(1, bookshelves.size());
        assertEquals(this.bookshelfArray[0], bookshelves.get(0));

        authors = book.getAuthors();
        assertEquals(2, authors.size());
        assertEquals(this.authorArray[0], authors.get(0));
        assertEquals(this.authorArray[1], authors.get(1));

        final List<Publisher> publishers = book.getPublishers();
        assertEquals(1, publishers.size());
        assertEquals(this.publisherArray[0], publishers.get(0));
    }

    @Test
    public void Lending()
            throws DaoWriteException, IOException, StorageException {

        final int bookIdx = 0;

        final BookDao bookDao = serviceLocator.getBookDao();
        final LoaneeDao loaneeDao = serviceLocator.getLoaneeDao();

        final long bookId = prepareAndInsertBook(context, bookDao, bookIdx);

        loaneeDao.setLoanee(bookId, "TheAdversary");

        final Book book = Book.from(bookId);
        assertEquals("TheAdversary", book.getString(DBKey.LOANEE_NAME));

        book.putString(DBKey.LOANEE_NAME, "TheAdversary2");
        loaneeDao.setLoanee(book);

        assertEquals("TheAdversary2", loaneeDao.findLoaneeByBookId(bookId));

        final List<String> people = loaneeDao.getList();
        assertEquals(1, people.size());
        assertEquals("TheAdversary2", people.get(0));

        loaneeDao.delete(book);
        assertFalse(book.contains(DBKey.LOANEE_NAME));
    }

    @Test
    public void covers()
            throws DaoWriteException, IOException, StorageException {

        final int bookIdx = 0;

        final BookDao bookDao = serviceLocator.getBookDao();

        final File coverDir = coverStorage.getDir();
        final File tempDir = coverStorage.getTempDir();

        final long bookId = prepareAndInsertBook(context, bookDao, bookIdx);
        Book book = Book.from(bookId);

        // Test Dirty mode
        book.setStage(EntityStage.Stage.Dirty);
        // the book already has a front cover, add a back cover
        book.setCover(1, new File(tempDir, DbPrep.COVER[1]));
        // we're in 'Dirty' mode, so must be a temp file
        assertBookHasTempCover(book, 1);

        bookDao.update(context, book, Set.of());
        book.setStage(EntityStage.Stage.Clean);

        // reload
        book = Book.from(bookId);
        final String uuid = book.getString(DBKey.BOOK_UUID, null);

        assertBookHasPersistedCover(book, 0);
        assertBookHasPersistedCover(book, 1);

        // We've used both temp files, so both files should be gone now
        final File[] tempFiles;
        tempFiles = tempDir.listFiles(jpgFilter);
        assertNotNull(tempFiles);
        assertEquals(0, tempFiles.length);

        // sanity check there must NOT be any temp cover fileSpecs.
        assertFalse(book.contains(Book.BKEY_TMP_FILE_SPEC[0]));
        assertFalse(book.contains(Book.BKEY_TMP_FILE_SPEC[1]));

        // sanity check the cover is really there
        assertBookHasPersistedCover(book, 1);
        // remove it
        book.removeCover(1);
        // there must NOT be any temp cover fileSpecs.
        assertFalse(book.contains(Book.BKEY_TMP_FILE_SPEC[0]));
        assertFalse(book.contains(Book.BKEY_TMP_FILE_SPEC[1]));
        // the front cover should still be there
        assertBookHasPersistedCover(book, 0);
        //the back cover must be gone
        assertFalse(new File(coverDir, uuid + "_1" + EXT_JPG).exists());

        // Add a new back cover to the read-only book
        final File coverFile = new DbPrep().getFile(1);
        originalImageFileName[1] = coverFile.getAbsolutePath();
        originalImageSize[1] = coverFile.length();

        assertEquals(EntityStage.Stage.Clean, book.getStage());
        // We're in Clean mode; This call will/must store the cover immediately
        book.setCover(1, new File(tempDir, DbPrep.COVER[1]));
        assertEquals(EntityStage.Stage.Clean, book.getStage());

        // there must NOT be any temp cover fileSpecs.
        assertFalse(book.contains(Book.BKEY_TMP_FILE_SPEC[0]));
        assertFalse(book.contains(Book.BKEY_TMP_FILE_SPEC[1]));
        // We once again must have front and back cover
        assertBookHasPersistedCover(book, 0);
        assertBookHasPersistedCover(book, 1);
    }

    @Test
    public void showBookVM()
            throws DaoWriteException, StorageException, IOException {

        final int bookIdx = 0;

        final StylesHelper helper = serviceLocator.getStyles();
        final Optional<Style> s1 = helper.getStyle(BuiltinStyle.HARD_DEFAULT_UUID);
        assertTrue(s1.isPresent());

        final BookDao bookDao = serviceLocator.getBookDao();
        final long bookId = prepareAndInsertBook(context, bookDao, bookIdx);
        final ShowBookDetailsViewModel vm = new ShowBookDetailsViewModel();
        final Bundle args = serviceLocator.newBundle();
        args.putLong(DBKey.FK_BOOK, bookId);

        vm.init(context, args, s1.get());
        final Book retrieved = vm.getBook();
        assertEquals(bookId, retrieved.getId());
        assertBookMatchesInitialInsert(retrieved, bookIdx);
    }

    /**
     * Create and insert a book. It will have a front cover, but no back cover.
     *
     * @return book id
     */
    private long prepareAndInsertBook(@NonNull final Context context,
                                      @NonNull final BookDao bookDao,
                                      final int bookIdx)
            throws DaoWriteException, StorageException, IOException {

        final Book book = new Book();
        book.setStage(EntityStage.Stage.WriteAble);
        book.putString(DBKey.TITLE, TestConstants.BOOK_TITLE[bookIdx]);
        book.setStage(EntityStage.Stage.Dirty);

        book.putLong(DBKey.SID_ISFDB, TestConstants.BOOK_ISFDB[bookIdx]);
        book.putString(DBKey.SID_LCCN, TestConstants.BOOK_LCCN[bookIdx]);

        book.setBookshelves(bookshelfList);
        book.setAuthors(authorList);
        book.setPublishers(publisherList);

        // Add a front cover but no back cover
        final File tempDir = coverStorage.getTempDir();
        book.setCover(0, new File(tempDir, DbPrep.COVER[0]));
        // we're in 'Dirty' mode, so must be a temp file
        assertBookHasTempCover(book, 0);

        // Inserting the data should change the stage from Dirty to Clean
        assertEquals(EntityStage.Stage.Dirty, book.getStage());
        final long bookId = bookDao.insert(context, book, Set.of());
        book.setStage(EntityStage.Stage.Clean);

        assertTrue(bookId > 0);
        assertEquals(book.getId(), bookId);

        return bookId;
    }

    private void assertBookMatchesInitialInsert(@NonNull final Book book,
                                                final int bookIdx)
            throws StorageException {

        assertEquals(EntityStage.Stage.Clean, book.getStage());

        final String uuid = book.getString(DBKey.BOOK_UUID, null);
        assertNotNull(uuid);
        assertFalse(uuid.isEmpty());
        assertEquals(TestConstants.BOOK_TITLE[bookIdx], book.getTitle());

        assertEquals(TestConstants.BOOK_ISFDB[bookIdx], book.getLong(DBKey.SID_ISFDB));

        // not saved, hence null
        assertNull(book.getString(DBKey.SID_LCCN, null));


        final List<Bookshelf> bookshelves = book.getBookshelves();
        assertEquals(1, bookshelves.size());
        assertEquals(this.bookshelfArray[0], bookshelves.get(0));

        final List<Author> authors = book.getAuthors();
        assertEquals(1, authors.size());
        assertEquals(this.authorArray[0], authors.get(0));

        final List<Publisher> publishers = book.getPublishers();
        assertEquals(1, publishers.size());
        assertEquals(this.publisherArray[0], publishers.get(0));

        assertBookHasPersistedCover(book, 0);
        assertFalse(book.contains(Book.BKEY_TMP_FILE_SPEC[1]));

        final File tempDir = coverStorage.getTempDir();
        final List<File> tempFiles = FileUtils.collectFiles(tempDir, jpgFilter, 10);
        // expected: 1: because "0.jpg" should be gone, but "1.jpg" will still be there
        assertEquals(1, tempFiles.size());
        assertEquals(DbPrep.COVER[1], tempFiles.get(0).getName());
    }

    private void assertBookHasTempCover(@NonNull final Book book,
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
    private void assertBookHasPersistedCover(@NonNull final Book book,
                                             @IntRange(from = 0, to = 1) final int cIdx) {
        // there must NOT be any temp cover fileSpecs.
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
}
