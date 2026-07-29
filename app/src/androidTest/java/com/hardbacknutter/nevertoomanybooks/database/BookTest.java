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
package com.hardbacknutter.nevertoomanybooks.database;

import android.content.Context;

import androidx.annotation.IntRange;
import androidx.annotation.NonNull;

import java.io.File;
import java.io.FileFilter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;

import com.hardbacknutter.nevertoomanybooks.BaseDBTest;
import com.hardbacknutter.nevertoomanybooks.DbPrep;
import com.hardbacknutter.nevertoomanybooks.InstantTaskExecutorExtension;
import com.hardbacknutter.nevertoomanybooks.bookdetails.ShowBookDetailsInput;
import com.hardbacknutter.nevertoomanybooks.bookdetails.ShowBookDetailsViewModel;
import com.hardbacknutter.nevertoomanybooks.booklist.style.BuiltinStyle;
import com.hardbacknutter.nevertoomanybooks.booklist.style.Style;
import com.hardbacknutter.nevertoomanybooks.core.database.DaoWriteException;
import com.hardbacknutter.nevertoomanybooks.core.storage.FileUtils;
import com.hardbacknutter.nevertoomanybooks.core.storage.StorageException;
import com.hardbacknutter.nevertoomanybooks.covers.CoverStorage;
import com.hardbacknutter.nevertoomanybooks.covers.CoverStorageException;
import com.hardbacknutter.nevertoomanybooks.database.dao.BookDao;
import com.hardbacknutter.nevertoomanybooks.database.dao.LoaneeDao;
import com.hardbacknutter.nevertoomanybooks.database.dao.StylesHelper;
import com.hardbacknutter.nevertoomanybooks.entities.Author;
import com.hardbacknutter.nevertoomanybooks.entities.Book;
import com.hardbacknutter.nevertoomanybooks.entities.Bookshelf;
import com.hardbacknutter.nevertoomanybooks.entities.EntityStage;
import com.hardbacknutter.nevertoomanybooks.entities.Identifier;
import com.hardbacknutter.nevertoomanybooks.entities.Publisher;
import com.hardbacknutter.nevertoomanybooks.entities.TocEntry;
import com.hardbacknutter.nevertoomanybooks.utils.AppLocale;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** LiveData requirement: {@code @ExtendWith(InstantTaskExecutorExtension.class)} */
@SuppressWarnings({"LongLine", "MismatchedQueryAndUpdateOfCollection", "MismatchedReadAndWriteOfArray"})
@ExtendWith(InstantTaskExecutorExtension.class)
class BookTest
        extends BaseDBTest {

    private static final String EXT_JPG = ".jpg";
    private final List<Bookshelf> bookshelfList = new ArrayList<>();
    private final List<Author> authorList = new ArrayList<>();
    private final List<Publisher> publisherList = new ArrayList<>();
    private final List<TocEntry> tocEntryList = new ArrayList<>();
    private final String[] originalImageFileName = new String[DBKey.NR_OF_BOOK_COVERS];
    private final long[] originalImageSize = new long[DBKey.NR_OF_BOOK_COVERS];
    private final FileFilter jpgFilter = pathname -> pathname.getPath().endsWith(EXT_JPG);

    private CoverStorage coverStorage;
    private BookDao bookDao;
    private LoaneeDao loaneeDao;

    private DBTestHelper h;

    /**
     * Clean the database.
     * Empty the temp directory.
     * Copy two pictures from the Pictures directory to the temp directory.
     */
    @BeforeEach
    void setup()
            throws IOException, StorageException, DaoWriteException {
        super.setup(AppLocale.SYSTEM_LANGUAGE);
        h = new DBTestHelper(serviceLocator);

        bookDao = serviceLocator.getBookDao();
        loaneeDao = serviceLocator.getLoaneeDao();

        coverStorage = serviceLocator.getCoverStorage();

        final File coverDir = coverStorage.getDir();
        assertNotNull(coverDir);

        final File tempDir = coverStorage.getTempDir();
        assertNotNull(tempDir);

        // empty the temp dir
        //noinspection ResultOfMethodCallIgnored
        FileUtils.collectFiles(tempDir, jpgFilter).forEach(File::delete);

        final Locale bookLocale = Locale.getDefault();

        // bookshelf[0] is the hard default
        bookshelfList.clear();
        bookshelfList.add(h.bookshelfArray[0]);

        // insert ONLY author[0]
        h.authorIdArray[0] = serviceLocator.getAuthorDao().insert(context, h.authorArray[0],
                                                                  bookLocale);
        authorList.clear();
        authorList.add(h.authorArray[0]);

        // insert ONLY publisher[0]
        h.publisherIdArray[0] = serviceLocator.getPublisherDao()
                                              .insert(context, h.publisherArray[0],
                                                      bookLocale);
        publisherList.clear();
        publisherList.add(h.publisherArray[0]);

        // No tocs
        tocEntryList.clear();

        final DbPrep dbPrep = new DbPrep();
        for (int cIdx = 0; cIdx < DBKey.NR_OF_BOOK_COVERS; cIdx++) {
            final File coverFile = dbPrep.getFile(cIdx);
            originalImageFileName[cIdx] = coverFile.getAbsolutePath();
            originalImageSize[cIdx] = coverFile.length();
        }

        assertTrue(h.bookshelfArray[0].getId() > 0);
        assertTrue(h.authorArray[0].getId() > 0);
        assertTrue(h.publisherArray[0].getId() > 0);
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
    void book()
            throws DaoWriteException, IOException, StorageException {

        final int bookIdx = 0;

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
        book.setTitle(TestConstants.BOOK_TITLE[bookIdx] + "_upd");
        book.setStage(EntityStage.Stage.Dirty);

        authors = book.getAuthors();
        authors.add(this.h.authorArray[1]);

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

        assertEquals(TestConstants.BOOK_TITLE[bookIdx] + "_upd", book.getString(DBKey.TITLE, null));

        final List<Bookshelf> bookshelves = book.getBookshelves();
        assertEquals(1, bookshelves.size());
        assertEquals(this.h.bookshelfArray[0], bookshelves.get(0));

        authors = book.getAuthors();
        assertEquals(2, authors.size());
        assertEquals(this.h.authorArray[0], authors.get(0));
        assertEquals(this.h.authorArray[1], authors.get(1));

        final List<Publisher> publishers = book.getPublishers();
        assertEquals(1, publishers.size());
        assertEquals(this.h.publisherArray[0], publishers.get(0));
    }

    @Test
    void lending()
            throws DaoWriteException, IOException, StorageException {

        final int bookIdx = 0;

        final long bookId = prepareAndInsertBook(context, bookDao, bookIdx);

        loaneeDao.setLoanee(bookId, "TheAdversary");

        final Book book = Book.from(bookId);
        assertEquals("TheAdversary", book.getString(DBKey.LOANEE_NAME, null));

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
    void covers()
            throws DaoWriteException, IOException, StorageException {

        final int bookIdx = 0;

        final File coverDir = coverStorage.getDir();
        final File tempDir = coverStorage.getTempDir();

        final long bookId = prepareAndInsertBook(context, bookDao, bookIdx);
        Book book = Book.from(bookId);

        // Test Dirty mode
        book.setStage(EntityStage.Stage.Dirty);
        // the book already has a front cover, add a back cover
        book.setImage(context, 1, new File(tempDir, DbPrep.COVER[1]));
        // we're in 'Dirty' mode, so must be a temp file
        assertBookHasTempCover(book, 1);

        bookDao.update(context, book, Set.of());
        book.setStage(EntityStage.Stage.Clean);

        // reload
        book = Book.from(bookId);
        final String uuid = book.getString(DBKey.BOOK_UUID, null);

        assertBookHasPersistedCover(book, 0);
        assertBookHasPersistedCover(book, 1);

        // We've used both temp files, so files 1+2 should be gone now
        // but 3+4 will still be there
        final File[] tempFiles;
        tempFiles = tempDir.listFiles(jpgFilter);
        assertNotNull(tempFiles);
        assertEquals(2, tempFiles.length);

        // sanity check there must NOT be any temp cover fileSpecs.
        assertBookHasNoTempCovers(book);

        // sanity check the cover is really there
        assertBookHasPersistedCover(book, 1);
        // remove it
        book.removeImage(context, 1);
        // there must NOT be any temp cover fileSpecs.
        assertBookHasNoTempCovers(book);
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
        book.setImage(context, 1, new File(tempDir, DbPrep.COVER[1]));
        assertEquals(EntityStage.Stage.Clean, book.getStage());

        // there must NOT be any temp cover fileSpecs.
        assertBookHasNoTempCovers(book);
        // We once again must have front and back cover
        assertBookHasPersistedCover(book, 0);
        assertBookHasPersistedCover(book, 1);
    }

    @Test
    void showBookVM()
            throws DaoWriteException, StorageException, IOException {

        final int bookIdx = 0;

        final StylesHelper helper = serviceLocator.getStyles();
        final Optional<Style> s1 = helper.getStyle(BuiltinStyle.HARD_DEFAULT_UUID);
        assertTrue(s1.isPresent());

        final long bookId = prepareAndInsertBook(context, bookDao, bookIdx);
        final ShowBookDetailsViewModel vm = new ShowBookDetailsViewModel();

        final ShowBookDetailsInput args = new ShowBookDetailsInput(
                bookId, serviceLocator.getBookshelfDao().getDefault(), false);

        vm.init(context, args, s1.get());
        vm.loadBook();

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
                                      @SuppressWarnings("SameParameterValue") final int bookIdx)
            throws DaoWriteException, StorageException, IOException {

        final Book book = new Book();
        book.setStage(EntityStage.Stage.WriteAble);
        book.setTitle(TestConstants.BOOK_TITLE[bookIdx]);
        book.setStage(EntityStage.Stage.Dirty);

        book.setIdentifiers(List.of(
                new Identifier.Value(Identifier.SID_ISFDB, TestConstants.BOOK_ISFDB[bookIdx]),
                new Identifier.Value(Identifier.SID_LCCN, TestConstants.BOOK_LCCN[bookIdx])
        ));

        book.setBookshelves(bookshelfList);
        book.setAuthors(authorList);
        book.setPublishers(publisherList);

        // Add a front cover only
        final File tempDir = coverStorage.getTempDir();
        book.setImage(context, 0, new File(tempDir, DbPrep.COVER[0]));
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
                                                @SuppressWarnings("SameParameterValue") final int bookIdx)
            throws CoverStorageException {

        assertEquals(EntityStage.Stage.Clean, book.getStage());

        final String uuid = book.getString(DBKey.BOOK_UUID, null);
        assertNotNull(uuid);
        assertFalse(uuid.isEmpty());
        assertEquals(TestConstants.BOOK_TITLE[bookIdx], book.getString(DBKey.TITLE, null));

        assertEquals(TestConstants.BOOK_ISFDB[bookIdx],
                     book.requireIdentifierValue(Identifier.SID_ISFDB));
        assertEquals(TestConstants.BOOK_LCCN[bookIdx],
                     book.requireIdentifierValue(Identifier.SID_LCCN));

        final List<Bookshelf> bookshelves = book.getBookshelves();
        assertEquals(1, bookshelves.size());
        assertEquals(this.h.bookshelfArray[0], bookshelves.get(0));

        final List<Author> authors = book.getAuthors();
        assertEquals(1, authors.size());
        assertEquals(this.h.authorArray[0], authors.get(0));

        final List<Publisher> publishers = book.getPublishers();
        assertEquals(1, publishers.size());
        assertEquals(this.h.publisherArray[0], publishers.get(0));

        assertBookHasPersistedCover(book, 0);
        assertFalse(book.contains(Book.BKEY_TMP_FILE_SPEC[1]));
        assertFalse(book.contains(Book.BKEY_TMP_FILE_SPEC[2]));
        assertFalse(book.contains(Book.BKEY_TMP_FILE_SPEC[3]));

        final File tempDir = coverStorage.getTempDir();
        final List<File> tempFiles = FileUtils.collectFiles(tempDir, jpgFilter, 10);
        // expected: 3: because "0.jpg" should be gone,
        // but "1.jpg", "2.jpg", "3.jpg" will still be there
        assertEquals(3, tempFiles.size());
        assertEquals(DbPrep.COVER[1], tempFiles.get(0).getName());
        assertEquals(DbPrep.COVER[2], tempFiles.get(1).getName());
        assertEquals(DbPrep.COVER[3], tempFiles.get(2).getName());
    }

    private void assertBookHasTempCover(@NonNull final Book book,
                                        @IntRange(from = 0, to = 3) final int cIdx)
            throws CoverStorageException {

        assertTrue(book.contains(Book.BKEY_TMP_FILE_SPEC[cIdx]));

        final File tempDir = coverStorage.getTempDir();
        assertEquals(tempDir.getAbsolutePath()
                     + File.separatorChar + DbPrep.COVER[cIdx],
                     book.getString(Book.BKEY_TMP_FILE_SPEC[cIdx], null));
    }

    private void assertBookHasNoTempCovers(@NonNull final Book book) {
        for (int i = 0; i < Book.BKEY_TMP_FILE_SPEC.length; i++) {
            assertFalse(book.contains(Book.BKEY_TMP_FILE_SPEC[i]));
        }
    }

    /**
     * Check if the book has an actual cover file which exists with the correct name
     * and has the correct length.
     *
     * @param book to check
     * @param cIdx 0..n image index
     */
    private void assertBookHasPersistedCover(@NonNull final Book book,
                                             @IntRange(from = 0, to = 3) final int cIdx) {
        // there must NOT be any temp cover fileSpecs.
        assertFalse(book.contains(Book.BKEY_TMP_FILE_SPEC[cIdx]));

        final Optional<File> oCover = book.getImage(context, cIdx);
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
