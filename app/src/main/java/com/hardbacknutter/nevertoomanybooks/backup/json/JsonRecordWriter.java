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
package com.hardbacknutter.nevertoomanybooks.backup.json;

import android.content.Context;
import android.content.SharedPreferences;
import android.database.Cursor;

import androidx.annotation.AnyThread;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.util.Pair;
import androidx.preference.PreferenceManager;

import java.io.IOException;
import java.io.Writer;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

import com.hardbacknutter.nevertoomanybooks.R;
import com.hardbacknutter.nevertoomanybooks.ServiceLocator;
import com.hardbacknutter.nevertoomanybooks.backup.ExportResults;
import com.hardbacknutter.nevertoomanybooks.backup.json.coders.BookCoder;
import com.hardbacknutter.nevertoomanybooks.backup.json.coders.BookshelfCoder;
import com.hardbacknutter.nevertoomanybooks.backup.json.coders.BundleCoder;
import com.hardbacknutter.nevertoomanybooks.backup.json.coders.CalibreCustomFieldCoder;
import com.hardbacknutter.nevertoomanybooks.backup.json.coders.CalibreLibraryCoder;
import com.hardbacknutter.nevertoomanybooks.backup.json.coders.CertificateCoder;
import com.hardbacknutter.nevertoomanybooks.backup.json.coders.DeletedBooksCoder;
import com.hardbacknutter.nevertoomanybooks.backup.json.coders.JsonCoder;
import com.hardbacknutter.nevertoomanybooks.backup.json.coders.SharedPreferencesCoder;
import com.hardbacknutter.nevertoomanybooks.backup.json.coders.StyleCoder;
import com.hardbacknutter.nevertoomanybooks.backup.zip.ZipArchiveWriter;
import com.hardbacknutter.nevertoomanybooks.booklist.style.Style;
import com.hardbacknutter.nevertoomanybooks.booklist.style.StylesHelper;
import com.hardbacknutter.nevertoomanybooks.covers.CoverStorage;
import com.hardbacknutter.nevertoomanybooks.database.DBKey;
import com.hardbacknutter.nevertoomanybooks.database.dao.BookDao;
import com.hardbacknutter.nevertoomanybooks.entities.Book;
import com.hardbacknutter.nevertoomanybooks.entities.Bookshelf;
import com.hardbacknutter.nevertoomanybooks.io.ArchiveMetaData;
import com.hardbacknutter.nevertoomanybooks.io.DataWriterException;
import com.hardbacknutter.nevertoomanybooks.io.RecordType;
import com.hardbacknutter.nevertoomanybooks.io.RecordWriter;
import com.hardbacknutter.nevertoomanybooks.sync.calibre.CalibreContentServer;
import com.hardbacknutter.nevertoomanybooks.sync.calibre.CalibreCustomField;
import com.hardbacknutter.nevertoomanybooks.sync.calibre.CalibreLibrary;
import com.hardbacknutter.nevertoomanybooks.tasks.ProgressListener;
import com.hardbacknutter.org.json.JSONArray;
import com.hardbacknutter.org.json.JSONException;
import com.hardbacknutter.org.json.JSONObject;

/**
 * Write JSON encoded {@link RecordType}s.
 * <p>
 * Supports:
 * <ul>
 *      <li>{@link RecordType#MetaData}</li>
 *      <li>{@link RecordType#Styles}</li>
 *      <li>{@link RecordType#Preferences}</li>
 *      <li>{@link RecordType#Certificates}</li>
 *      <li>{@link RecordType#Bookshelves}</li>
 *      <li>{@link RecordType#CalibreLibraries}</li>
 *      <li>{@link RecordType#CalibreCustomFields}</li>
 *      <li>{@link RecordType#DeletedBooks}</li>
 *      <li>{@link RecordType#Books}</li>
 * </ul>
 */
public class JsonRecordWriter
        implements RecordWriter {

    @Nullable
    private final LocalDateTime utcSinceDateTime;

    /**
     * Constructor.
     *
     * @param utcSinceDateTime (optional) UTC based date to select only items
     *                         modified or added since.
     */
    @AnyThread
    public JsonRecordWriter(@Nullable final LocalDateTime utcSinceDateTime) {
        this.utcSinceDateTime = utcSinceDateTime;
    }

    @Override
    public void writeMetaData(@NonNull final Context context,
                              @NonNull final Writer writer,
                              @NonNull final ArchiveMetaData metaData)
            throws DataWriterException,
                   IOException {
        try {
            writer.write(new BundleCoder(context)
                                 .encode(metaData.getData())
                                 .toString());
        } catch (@NonNull final JSONException e) {
            throw new DataWriterException(e);
        }
    }

    /**
     * IMPORTANT:
     * For the current supported backup version(s), {@link ZipArchiveWriter}
     * will call this method with <strong>ONE</strong> RecordType at a time.
     * i.e. Styles OR Preferences OR ...
     * and hence the jsonData written will be several virtual files (records), one for each type.
     * <p>
     * {@link JsonArchiveWriter}, will call this method <strong>ONCE</strong>
     * with ALL RecordType's.
     * <p>
     * The code is written for the latter case while being compliant with the former.
     * <p>
     * There is a <strong>strict order</strong> of the entries:
     * <ol>
     *     <li>These always come first in the given order</li>
     *     <li>{@link RecordType#MetaData}</li>
     *     <li>{@link RecordType#Styles}</li>
     *     <li>{@link RecordType#Preferences}</li>
     *
     *     <li>These depend on other types being included or not</li>
     *     <li>{@link RecordType#Certificates}</li>
     *     <li>{@link RecordType#Bookshelves}</li>
     *     <li>{@link RecordType#CalibreLibraries}</li>
     *     <li>{@link RecordType#CalibreCustomFields}</li>
     *
     *     <li>These always come last in the given order</li>
     *     <li>{@link RecordType#Books}</li>
     *     <li>{@link RecordType#Cover}</li>
     * </ol>
     *
     * @param context          Current context
     * @param writer           Writer to write to
     * @param recordTypes      The set of records which should be written.
     * @param progressListener Progress and cancellation interface
     *
     * @return results summary
     *
     * @throws DataWriterException on a decoding/parsing of data issue
     * @throws IOException         on generic/other IO failures
     */
    @Override
    @NonNull
    public ExportResults write(@NonNull final Context context,
                               @NonNull final Writer writer,
                               @NonNull final Set<RecordType> recordTypes,
                               @NonNull final ProgressListener progressListener)
            throws DataWriterException,
                   IOException {

        final ServiceLocator serviceLocator = ServiceLocator.getInstance();
        final StylesHelper stylesHelper = serviceLocator.getStyles();
        final Style defaultStyle = stylesHelper.getDefault();
        final CoverStorage coverStorage = serviceLocator.getCoverStorage();

        final Set<RecordType> resolvedRecordTypes = RecordType.addRelatedTypes(recordTypes);

        final ExportResults results = new ExportResults();
        final JSONObject jsonData = new JSONObject();

        try {
            // Write styles first, and preferences next! This will facilitate & speedup
            // importing as we'll be seeking in the input archive for these.

            if (resolvedRecordTypes.contains(RecordType.Styles)
                && !progressListener.isCancelled()) {
                progressListener.publishProgress(1, context.getString(R.string.lbl_styles));

                final List<Style> styles = stylesHelper.getStyles(true);
                if (!styles.isEmpty()) {
                    final JsonCoder<Style> coder = new StyleCoder();
                    jsonData.put(RecordType.Styles.getName(), coder.encode(styles));
                }
                results.styles = styles.size();
            }

            if (resolvedRecordTypes.contains(RecordType.Preferences)
                && !progressListener.isCancelled()) {
                progressListener.publishProgress(1, context.getString(R.string.lbl_settings));

                final JsonCoder<SharedPreferences> coder = new SharedPreferencesCoder();
                jsonData.put(RecordType.Preferences.getName(), coder.encode(
                        PreferenceManager.getDefaultSharedPreferences(context)));
                results.preferences = 1;
            }

            if (resolvedRecordTypes.contains(RecordType.Certificates)
                && !progressListener.isCancelled()) {
                progressListener.publishProgress(1, context.getString(
                        R.string.lbl_certificates));

                final JSONObject certificates = new JSONObject();
                final JsonCoder<X509Certificate> coder = new CertificateCoder();
                try {
                    // always export even if the CCS is disabled!
                    // The user might have temporarily switched it off.
                    certificates.put(CalibreContentServer.SERVER_CA, coder.encode(
                            CalibreContentServer.getCertificate(context)));
                    results.certificates++;

                } catch (@NonNull final IOException | CertificateException ignore) {
                    // no certificate (IOException) or invalid cert; just ignore it.
                }
                if (!certificates.isEmpty()) {
                    jsonData.put(RecordType.Certificates.getName(), certificates);
                }
            }

            if (resolvedRecordTypes.contains(RecordType.Bookshelves)
                && !progressListener.isCancelled()) {
                progressListener.publishProgress(1, context.getString(
                        R.string.lbl_bookshelves));

                final List<Bookshelf> bookshelves =
                        ServiceLocator.getInstance().getBookshelfDao().getAll();
                if (!bookshelves.isEmpty()) {
                    jsonData.put(RecordType.Bookshelves.getName(),
                                 new BookshelfCoder(context, defaultStyle).encode(bookshelves));
                }
                results.bookshelves = bookshelves.size();
            }

            if (resolvedRecordTypes.contains(RecordType.CalibreLibraries)
                && !progressListener.isCancelled()) {
                progressListener.publishProgress(1, context.getString(
                        R.string.site_calibre));

                final List<CalibreLibrary> libraries =
                        ServiceLocator.getInstance().getCalibreLibraryDao().getAllLibraries();
                if (!libraries.isEmpty()) {
                    jsonData.put(RecordType.CalibreLibraries.getName(),
                                 new CalibreLibraryCoder(context, defaultStyle).encode(libraries));
                }
                results.calibreLibraries = libraries.size();
            }

            if (resolvedRecordTypes.contains(RecordType.CalibreCustomFields)
                && !progressListener.isCancelled()) {
                progressListener.publishProgress(1, context.getString(
                        R.string.site_calibre));

                final List<CalibreCustomField> fields =
                        ServiceLocator.getInstance().getCalibreCustomFieldDao().getCustomFields();
                if (!fields.isEmpty()) {
                    jsonData.put(RecordType.CalibreCustomFields.getName(),
                                 new CalibreCustomFieldCoder().encode(fields));
                }
                results.calibreCustomFields = fields.size();
            }

            if (resolvedRecordTypes.contains(RecordType.DeletedBooks)
                && !progressListener.isCancelled()) {
                progressListener.publishProgress(1, context.getString(
                        R.string.lbl_books));

                final List<Pair<String, String>> list =
                        ServiceLocator.getInstance().getDeletedBooksDao().getAll(null);
                if (!list.isEmpty()) {
                    jsonData.put(RecordType.DeletedBooks.getName(),
                                 new DeletedBooksCoder().encode(list));
                }
                results.deletedBooks = list.size();
            }

            if (resolvedRecordTypes.contains(RecordType.Books)
                && !progressListener.isCancelled()) {

                final boolean collectCoverFilenames = resolvedRecordTypes.contains(
                        RecordType.Cover);

                final JsonCoder<Book> coder = new BookCoder(context, defaultStyle);

                int delta = 0;
                long lastUpdate = 0;

                final JSONArray bookArray = new JSONArray();
                final BookDao bookDao = ServiceLocator.getInstance().getBookDao();
                try (Cursor cursor = bookDao.fetchBooksForExport(utcSinceDateTime)) {
                    while (cursor.moveToNext() && !progressListener.isCancelled()) {
                        final Book book = Book.from(cursor);
                        bookArray.put(coder.encode(book));
                        results.addBook(book.getId());

                        if (collectCoverFilenames) {
                            for (int cIdx = 0; cIdx < 2; cIdx++) {
                                final String uuid = book.getString(DBKey.BOOK_UUID);
                                coverStorage.getPersistedFile(uuid, cIdx)
                                            .ifPresent(results::addCover);
                            }
                        }

                        delta++;
                        final long now = System.currentTimeMillis();
                        if ((now - lastUpdate) > progressListener.getUpdateIntervalInMs()) {
                            progressListener.publishProgress(delta, book.getTitle());
                            lastUpdate = now;
                            delta = 0;
                        }
                    }
                }

                if (!bookArray.isEmpty()) {
                    jsonData.put(RecordType.Books.getName(), bookArray);
                }
            }

            // Write the complete json output in one go
            if (!jsonData.isEmpty()) {
                // can throw StackOverflowError;
                // but we've only ever seen that in the emulator... flw
                writer.write(jsonData.toString());
            }

        } catch (@NonNull final JSONException | StackOverflowError e) {
            throw new DataWriterException(e);
        }

        return results;
    }
}
