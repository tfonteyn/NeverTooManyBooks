/*
 * @Copyright 2018-2021 HardBackNutter
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

import org.junit.Before;
import org.junit.Test;

import com.hardbacknutter.nevertoomanybooks.ServiceLocator;
import com.hardbacknutter.nevertoomanybooks.bookdetails.ShowBookDetailsViewModel;
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

import static com.hardbacknutter.nevertoomanybooks.database.Constants.AuthorFullName;
import static com.hardbacknutter.nevertoomanybooks.database.Constants.BOOK_TITLE;
import static com.hardbacknutter.nevertoomanybooks.database.Constants.COVER;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/** Do NOT extend BaseSetup ! */
@MediumTest
public class BookTest {

    private static final String NEED_TWO_FILE =
            "pictures directory must contain at least two file to use for testing";
    private static final String NEED_A_PICTURES_DIRECTORY = "Need a pictures directory";
    private static final String NEED_A_TEMP_DIRECTORY = "Need a temp directory";
    private static final String EXT_JPG = ".jpg";

    private final Bookshelf[] mBookshelf = new Bookshelf[5];
    private final long[] mBookshelfId = new long[5];
    private final ArrayList<Bookshelf> mBookshelfList = new ArrayList<>();

    private final Author[] mAuthor = new Author[5];
    private final long[] mAuthorId = new long[5];
    private final ArrayList<Author> mAuthorList = new ArrayList<>();

    private final Publisher[] mPublisher = new Publisher[5];
    private final long[] mPublisherId = new long[5];
    private final ArrayList<Publisher> mPublisherList = new ArrayList<>();

    private final TocEntry[] mTocEntry = new TocEntry[5];
    private final long[] mTocEntryId = new long[5];
    private final ArrayList<TocEntry> mTocEntryList = new ArrayList<>();

    private final Book[] mBook = new Book[5];
    private final long[] mBookId = new long[5];

    private final String[] mOriginalImageFileName = new String[2];
    private final long[] mOriginalImageSize = new long[2];
    private final FileFilter mJpgFilter = pathname -> pathname.getPath().endsWith(EXT_JPG);

    /**
     * Clean the database.
     * Empty the temp directory.
     * Copy two pictures from the Pictures directory to the temp directory.
     */
    @Before
    public void setup()
            throws IOException, StorageException {

        mBookshelfList.clear();
        mAuthorList.clear();
        mPublisherList.clear();
        mTocEntryList.clear();

        final ServiceLocator sl = ServiceLocator.getInstance();
        final SynchronizedDb db = sl.getDb();

        Constants.deleteTocs(db);
        Constants.deleteBooks(db);
        Constants.deleteAuthors(db);
        Constants.deletePublishers(db);

        final Context context = sl.getLocalizedAppContext();

        final int actualVolume = CoverDir.initVolume(context, 0);
        assertEquals(0, actualVolume);

        final File coverDir = CoverDir.getDir(context);
        assertNotNull(NEED_A_PICTURES_DIRECTORY, coverDir);

        final File tempDir = CoverDir.getTemp(context);
        assertNotNull(NEED_A_TEMP_DIRECTORY, tempDir);

        // empty the temp dir
        //noinspection ResultOfMethodCallIgnored
        FileUtils.collectFiles(tempDir, mJpgFilter, Integer.MAX_VALUE).forEach(File::delete);


        mBookshelf[0] = Bookshelf.getBookshelf(context, Bookshelf.DEFAULT);
        mBookshelfList.clear();
        mBookshelfList.add(mBookshelf[0]);

        mAuthor[0] = Author.from(AuthorFullName(0));
        mAuthor[1] = Author.from(AuthorFullName(1));

        // insert author[0] but do NOT insert author[1]
        mAuthorId[0] = sl.getAuthorDao().insert(context, mAuthor[0]);
        mAuthorList.clear();
        mAuthorList.add(mAuthor[0]);

        mPublisher[0] = Publisher.from(Constants.PUBLISHER + "0");
        mPublisher[1] = Publisher.from(Constants.PUBLISHER + "1");

        // insert publisher[0] but do NOT publisher author[1]

        mPublisherId[0] = sl.getPublisherDao()
                            .insert(context, mPublisher[0], Locale.getDefault());
        mPublisherList.clear();
        mPublisherList.add(mPublisher[0]);



        final List<File> files = FileUtils.collectFiles(coverDir, mJpgFilter, 10);
        assertTrue(NEED_TWO_FILE, files.size() > 1);

        prepareTempCover(context, files, 0);
        prepareTempCover(context, files, 1);

        assertTrue(mBookshelf[0].getId() > 0);
        assertTrue(mAuthor[0].getId() > 0);
        assertTrue(mPublisher[0].getId() > 0);
    }

    /**
     * Copy a file from the Pictures directory to the temp with a new/temp name.
     *
     * @param files from the Pictures directory
     * @param cIdx  cover index to create
     *
     * @throws IOException on failure
     */
    private void prepareTempCover(@NonNull final Context context,
                                  @NonNull final List<File> files,
                                  final int cIdx)
            throws IOException, StorageException {

        mOriginalImageFileName[cIdx] = files.get(cIdx).getAbsolutePath();
        final File srcFile = new File(mOriginalImageFileName[cIdx]);
        mOriginalImageSize[cIdx] = srcFile.length();

        FileUtils.copy(srcFile, new File(CoverDir.getTemp(context), Constants.COVER[cIdx]));
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

        mBook[0] = prepareAndInsertBook(context, bookDao);
        mBookId[0] = mBook[0].getId();

        /*
         * test the inserted book
         */
        Book book = Book.from(mBookId[0]);
        assertEquals(mBookId[0], book.getId());
        checkBookAfterInitialInsert(book);

        List<Author> authors;
        String uuid;
        final List<Bookshelf> bookshelves;
        final List<Publisher> publishers;
        File cover;
        final File[] tempFiles;

        /*
         * update the stored book
         */
        book.setStage(EntityStage.Stage.WriteAble);
        book.putString(DBKey.KEY_TITLE, BOOK_TITLE + "0_upd");
        book.setStage(EntityStage.Stage.Dirty);

        authors = book.getAuthors();
        authors.add(mAuthor[1]);

        book.setCover(1, new File(tempDir, Constants.COVER[1]));

        assertEquals(tempDir.getAbsolutePath()
                     + File.separatorChar + Constants.COVER[1],
                     book.getString(Book.BKEY_TMP_FILE_SPEC[1]));

        assertEquals(EntityStage.Stage.Dirty, book.getStage());
        bookDao.update(context, book, 0);
        book.setStage(EntityStage.Stage.Clean);

        /*
         * test the updated book
         */
        book = Book.from(mBookId[0]);
        assertEquals(mBookId[0], book.getId());

        uuid = book.getString(DBKey.KEY_BOOK_UUID);

        assertEquals(BOOK_TITLE + "0_upd", book.getTitle());
        bookshelves = book.getBookshelves();
        assertEquals(1, bookshelves.size());
        assertEquals(mBookshelf[0], bookshelves.get(0));
        authors = book.getAuthors();
        assertEquals(2, authors.size());
        assertEquals(mAuthor[0], authors.get(0));
        assertEquals(mAuthor[1], authors.get(1));
        publishers = book.getPublishers();
        assertEquals(1, publishers.size());
        assertEquals(mPublisher[0], publishers.get(0));

        assertFalse(book.contains(Book.BKEY_TMP_FILE_SPEC[0]));
        assertFalse(book.contains(Book.BKEY_TMP_FILE_SPEC[1]));

        cover = book.getCoverFile(0);
        assertNotNull(cover);
        assertEquals(mOriginalImageSize[0], cover.length());
        assertEquals(uuid + EXT_JPG, cover.getName());

        cover = book.getCoverFile(1);
        assertNotNull(cover);
        assertEquals(mOriginalImageSize[1], cover.length());
        assertEquals(uuid + "_1" + EXT_JPG, cover.getName());

        tempFiles = tempDir.listFiles(mJpgFilter);
        assertNotNull(tempFiles);
        // both files should be gone now
        assertEquals(0, tempFiles.length);


        /*
         * Delete the second cover of the read-only book
         */
        book = Book.from(mBookId[0]);
        assertEquals(mBookId[0], book.getId());

        uuid = book.getString(DBKey.KEY_BOOK_UUID);

        assertFalse(book.contains(Book.BKEY_TMP_FILE_SPEC[0]));
        assertFalse(book.contains(Book.BKEY_TMP_FILE_SPEC[1]));

        // sanity check the cover is really there
        cover = new File(coverDir, uuid + "_1" + EXT_JPG);
        assertTrue(cover.exists());

        book.removeCover(1);

        assertFalse(book.contains(Book.BKEY_TMP_FILE_SPEC[0]));
        assertFalse(book.contains(Book.BKEY_TMP_FILE_SPEC[1]));

        // setCover(.. null) should have deleted the file
        cover = new File(coverDir, uuid + "_1" + EXT_JPG);
        assertFalse(cover.exists());

        cover = book.getCoverFile(1);
        assertNull(cover);

        /*
         * Add the second cover of the read-only book
         */
        final List<File> files = FileUtils.collectFiles(coverDir, mJpgFilter, 10);
        prepareTempCover(context, files, 1);

        book.setCover(1, new File(tempDir, Constants.COVER[1]));

        assertFalse(book.contains(Book.BKEY_TMP_FILE_SPEC[0]));
        assertFalse(book.contains(Book.BKEY_TMP_FILE_SPEC[1]));

        cover = new File(coverDir, uuid + "_1" + EXT_JPG);
        assertTrue(cover.exists());

        cover = book.getCoverFile(1);
        assertNotNull(cover);
        assertEquals(uuid + "_1" + EXT_JPG, cover.getName());
        assertTrue(cover.exists());
    }

    @Test
    public void showBookVM()
            throws DaoWriteException, StorageException, IOException {
        final ServiceLocator sl = ServiceLocator.getInstance();
        final Context context = sl.getLocalizedAppContext();
        final BookDao bookDao = sl.getBookDao();
        mBook[0] = prepareAndInsertBook(context, bookDao);
        mBookId[0] = mBook[0].getId();

        final ShowBookDetailsViewModel vm = new ShowBookDetailsViewModel();
        final Bundle args = new Bundle();
        args.putLong(DBKey.FK_BOOK, mBookId[0]);

        vm.init(args);
        final Book retrieved = vm.getBook();
        assertEquals(mBookId[0], retrieved.getId());
        checkBookAfterInitialInsert(retrieved);
    }

    /*
     * Create and insert a book.
     */
    private Book prepareAndInsertBook(@NonNull final Context context,
                                      @NonNull final BookDao bookDao)
            throws DaoWriteException, StorageException, IOException {

        final Book book = new Book();
        book.setStage(EntityStage.Stage.WriteAble);
        book.putString(DBKey.KEY_TITLE, Constants.BOOK_TITLE + "0");
        book.setStage(EntityStage.Stage.Dirty);

        book.putLong(DBKey.SID_ISFDB, Constants.BOOK_ISFDB_123);
        book.putString(DBKey.SID_LCCN, Constants.BOOK_LCCN_0);

        book.putParcelableArrayList(Book.BKEY_BOOKSHELF_LIST, mBookshelfList);
        book.putParcelableArrayList(Book.BKEY_AUTHOR_LIST, mAuthorList);
        book.putParcelableArrayList(Book.BKEY_PUBLISHER_LIST, mPublisherList);

        book.setCover(0, new File(CoverDir.getTemp(context), Constants.COVER[0]));

        assertEquals(CoverDir.getTemp(context).getAbsolutePath()
                     + File.separatorChar + Constants.COVER[0],
                     book.getString(Book.BKEY_TMP_FILE_SPEC[0]));

        assertEquals(EntityStage.Stage.Dirty, book.getStage());
        final long bookId = bookDao.insert(context, book, 0);
        book.setStage(EntityStage.Stage.Clean);

        assertTrue(bookId > 0);
        assertEquals(book.getId(), bookId);

        return book;
    }

    private void checkBookAfterInitialInsert(final Book book)
            throws StorageException {
        final Context context = ServiceLocator.getInstance().getLocalizedAppContext();

        assertEquals(EntityStage.Stage.Clean, book.getStage());

        final String uuid = book.getString(DBKey.KEY_BOOK_UUID);
        assertFalse(uuid.isEmpty());
        assertEquals(BOOK_TITLE + "0", book.getTitle());

        assertEquals(Constants.BOOK_ISFDB_123, book.getLong(DBKey.SID_ISFDB));

        // not saved, hence empty
        assertEquals("", book.getString(DBKey.SID_LCCN));


        final List<Bookshelf> bookshelves = book.getBookshelves();
        assertEquals(1, bookshelves.size());
        assertEquals(mBookshelf[0], bookshelves.get(0));

        final List<Author> authors = book.getAuthors();
        assertEquals(1, authors.size());
        assertEquals(mAuthor[0], authors.get(0));

        final List<Publisher> publishers = book.getPublishers();
        assertEquals(1, publishers.size());
        assertEquals(mPublisher[0], publishers.get(0));

        assertFalse(book.contains(Book.BKEY_TMP_FILE_SPEC[0]));
        assertFalse(book.contains(Book.BKEY_TMP_FILE_SPEC[1]));

        final File cover = book.getCoverFile(0);
        assertNotNull(cover);
        assertEquals(mOriginalImageSize[0], cover.length());
        assertEquals(uuid + EXT_JPG, cover.getName());

        final List<File> tempFiles =
                FileUtils.collectFiles(CoverDir.getTemp(context), mJpgFilter, 10);
        // expected: 1: because "0.jpg" should be gone, but "1.jpg" will still be there
        assertEquals(1, tempFiles.size());
        assertEquals(COVER[1], tempFiles.get(0).getName());
    }
}
