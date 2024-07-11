/*
 * @Copyright 2018-2024 HardBackNutter
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
package com.hardbacknutter.nevertoomanybooks.backup.json;

import android.net.Uri;

import androidx.test.filters.MediumTest;

import java.io.File;
import java.io.IOException;
import java.security.cert.CertificateException;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Random;
import java.util.Set;

import com.hardbacknutter.nevertoomanybooks.BaseDBTest;
import com.hardbacknutter.nevertoomanybooks.DbPrep;
import com.hardbacknutter.nevertoomanybooks.ServiceLocator;
import com.hardbacknutter.nevertoomanybooks.TestProgressListener;
import com.hardbacknutter.nevertoomanybooks.backup.ExportHelper;
import com.hardbacknutter.nevertoomanybooks.backup.ExportResults;
import com.hardbacknutter.nevertoomanybooks.backup.ImportHelper;
import com.hardbacknutter.nevertoomanybooks.backup.ImportResults;
import com.hardbacknutter.nevertoomanybooks.core.database.DaoWriteException;
import com.hardbacknutter.nevertoomanybooks.core.network.CredentialsException;
import com.hardbacknutter.nevertoomanybooks.core.storage.StorageException;
import com.hardbacknutter.nevertoomanybooks.database.DBKey;
import com.hardbacknutter.nevertoomanybooks.database.dao.BookDao;
import com.hardbacknutter.nevertoomanybooks.entities.Book;
import com.hardbacknutter.nevertoomanybooks.io.ArchiveEncoding;
import com.hardbacknutter.nevertoomanybooks.io.ArchiveMetaData;
import com.hardbacknutter.nevertoomanybooks.io.DataReader;
import com.hardbacknutter.nevertoomanybooks.io.DataReaderException;
import com.hardbacknutter.nevertoomanybooks.io.DataWriterException;
import com.hardbacknutter.nevertoomanybooks.io.RecordType;
import com.hardbacknutter.nevertoomanybooks.utils.AppLocale;

import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

@MediumTest
@SuppressWarnings("MissingJavadoc")
public class JsonArchiveWriterTest
        extends BaseDBTest {

    private static final String TAG = "JsonArchiveWriterTest";

    private long bookInDb;
    private int nrOfStyles;

    private Locale systemLocale;

    @Before
    public void setup()
            throws DaoWriteException, StorageException, IOException, DataReaderException {
        super.setup(AppLocale.SYSTEM_LANGUAGE);

        systemLocale = serviceLocator.getSystemLocaleList().get(0);

        bookInDb = new DbPrep().maybeInstallTestData(context);
        // +1 for the global style which will be added during export
        nrOfStyles = serviceLocator.getStyles().getStyles(true).size() + 1;
    }

    @Test
    public void styles()
            throws DataReaderException, DataWriterException,
                   IOException, StorageException, CredentialsException, CertificateException {

        final File file = new File(context.getFilesDir(), TAG + "-styles.json");
        //noinspection ResultOfMethodCallIgnored
        file.delete();

        final ExportResults exportResults;

        final ExportHelper exportHelper = new ExportHelper(ArchiveEncoding.Json,
                                                           EnumSet.of(RecordType.Styles),
                                                           systemLocale);

        exportHelper.setUri(Uri.fromFile(file));

        exportResults = exportHelper.write(context, new TestProgressListener(TAG + ":export"));

        assertEquals(0, exportResults.getBookCount());
        assertEquals(0, exportResults.getCoverCount());
        assertEquals(0, exportResults.preferences);
        assertEquals(nrOfStyles, exportResults.styles);
        assertFalse(exportResults.database);

        final ImportHelper importHelper = new ImportHelper(context, systemLocale,
                                                           Uri.fromFile(file));
        // The default, fail if the default was changed without changing this test!
        assertEquals(DataReader.Updates.OnlyNewer, importHelper.getUpdateOption());

        importHelper.addRecordType(RecordType.Styles);

        assertTrue(importHelper.readMetaData(context).isPresent());

        final ImportResults importResults = importHelper
                .read(context, new TestProgressListener(TAG + ":import"));
        assertNotNull(importResults);
        assertEquals(exportResults.styles, importResults.styles);
    }

    @Test
    public void books()
            throws DataReaderException, DataWriterException, DaoWriteException, IOException,
                   StorageException, CredentialsException, CertificateException {

        final File file = new File(context.getFilesDir(), TAG + "-books.json");
        //noinspection ResultOfMethodCallIgnored
        file.delete();

        final ExportResults exportResults;

        final ExportHelper exportHelper = new ExportHelper(ArchiveEncoding.Json,
                                                           EnumSet.of(RecordType.Preferences,
                                                                      RecordType.Styles,
                                                                      RecordType.Books),
                                                           systemLocale);

        exportHelper.setUri(Uri.fromFile(file));

        exportResults = exportHelper.write(context, new TestProgressListener(TAG + ":export"));

        assertEquals(bookInDb, exportResults.getBookCount());
        assertEquals(0, exportResults.getCoverCount());
        assertEquals(1, exportResults.preferences);
        assertEquals(nrOfStyles, exportResults.styles);
        assertFalse(exportResults.database);

        // Now modify/delete some books. We have at least 10 books to play with
        final List<Long> ids = exportResults.getBooksExported();

        // Don't listen to lint. It proposes java.util.random.RandomGenerator
        // but that is JDK 17 and not available on Android.
        //noinspection TypeMayBeWeakened
        final Random random = new Random();
        final long deletedBookId = ids.get(random.nextInt(10) + 1);
        final long modifiedBookId = ids.get(random.nextInt(10) + 1);

        final BookDao bookDao = ServiceLocator.getInstance().getBookDao();
        bookDao.delete(deletedBookId);

        final Book book = Book.from(modifiedBookId);
        book.putString(DBKey.PERSONAL_NOTES,
                       "MODIFIED " + book.getString(DBKey.PERSONAL_NOTES, null));
        bookDao.update(context, book, Set.of());

        final ImportHelper importHelper = new ImportHelper(context, systemLocale,
                                                           Uri.fromFile(file));
        // The default, fail if the default was changed without changing this test!
        assertEquals(DataReader.Updates.OnlyNewer, importHelper.getUpdateOption());

        importHelper.addRecordType(RecordType.Books);
        importHelper.setUpdateOption(DataReader.Updates.Skip);

        final Optional<ArchiveMetaData> optMetaData = importHelper.readMetaData(context);
        assertTrue(optMetaData.isPresent());
        final ArchiveMetaData metaData = optMetaData.get();
        assertTrue(metaData.getBookCount().isPresent());
        assertEquals(bookInDb, (long) metaData.getBookCount().get());


        ImportResults importResults = importHelper
                .read(context, new TestProgressListener(TAG + ":import"));
        assertNotNull(importResults);
        assertEquals(exportResults.getBookCount(), importResults.booksProcessed);

        // we re-created the deleted book
        assertEquals(1, importResults.booksCreated);
        assertEquals(0, importResults.booksUpdated);
        // we skipped the updated book
        assertEquals(exportResults.getBookCount() - 1, importResults.booksSkipped);
        assertEquals(0, importResults.booksFailed);


        importHelper.addRecordType(RecordType.Books);
        importHelper.setUpdateOption(DataReader.Updates.Overwrite);

        importResults = importHelper.read(context, new TestProgressListener(TAG + ":header"));
        assertNotNull(importResults);
        assertEquals(exportResults.getBookCount(), importResults.booksProcessed);


        assertEquals(0, importResults.booksCreated);
        // we did an overwrite of ALL books
        assertEquals(bookInDb, importResults.booksUpdated);
        // so we skipped none
        assertEquals(0, importResults.booksSkipped);
        assertEquals(0, importResults.booksFailed);
    }
}
