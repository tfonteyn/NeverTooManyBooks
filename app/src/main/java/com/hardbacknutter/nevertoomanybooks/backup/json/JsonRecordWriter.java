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
package com.hardbacknutter.nevertoomanybooks.backup.json;

import android.content.Context;
import android.content.SharedPreferences;
import android.database.Cursor;

import androidx.annotation.AnyThread;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.preference.PreferenceManager;

import java.io.File;
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
import com.hardbacknutter.nevertoomanybooks.backup.RecordType;
import com.hardbacknutter.nevertoomanybooks.backup.RecordWriter;
import com.hardbacknutter.nevertoomanybooks.backup.base.ArchiveMetaData;
import com.hardbacknutter.nevertoomanybooks.backup.json.coders.BookCoder;
import com.hardbacknutter.nevertoomanybooks.backup.json.coders.BundleCoder;
import com.hardbacknutter.nevertoomanybooks.backup.json.coders.CertificateCoder;
import com.hardbacknutter.nevertoomanybooks.backup.json.coders.JsonCoder;
import com.hardbacknutter.nevertoomanybooks.backup.json.coders.ListStyleCoder;
import com.hardbacknutter.nevertoomanybooks.backup.json.coders.SharedPreferencesCoder;
import com.hardbacknutter.nevertoomanybooks.booklist.style.ListStyle;
import com.hardbacknutter.nevertoomanybooks.database.dao.BookDao;
import com.hardbacknutter.nevertoomanybooks.entities.Book;
import com.hardbacknutter.nevertoomanybooks.sync.calibre.CalibreContentServer;
import com.hardbacknutter.nevertoomanybooks.tasks.ProgressListener;
import com.hardbacknutter.nevertoomanybooks.utils.exceptions.GeneralParsingException;
import com.hardbacknutter.org.json.JSONArray;
import com.hardbacknutter.org.json.JSONException;
import com.hardbacknutter.org.json.JSONObject;

/**
 * <ul>Supports:
 *      <li>{@link RecordType#MetaData}</li>
 *      <li>{@link RecordType#Styles}</li>
 *      <li>{@link RecordType#Preferences}</li>
 *      <li>{@link RecordType#Certificates}</li>
 *      <li>{@link RecordType#Books}</li>
 * </ul>
 */
public class JsonRecordWriter
        implements RecordWriter {

    /** The format version of this RecordWriter. */
    private static final int VERSION = 1;

    /** Log tag. */
    private static final String TAG = "JsonRecordWriter";

    @Nullable
    private final LocalDateTime mUtcSinceDateTime;

    /**
     * Constructor.
     *
     * @param utcSinceDateTime (optional) UTC based date to select only items
     *                         modified or added since.
     */
    @AnyThread
    public JsonRecordWriter(@Nullable final LocalDateTime utcSinceDateTime) {
        mUtcSinceDateTime = utcSinceDateTime;
    }

    @Override
    public void writeMetaData(@NonNull final Writer writer,
                              @NonNull final ArchiveMetaData metaData)
            throws GeneralParsingException, IOException {
        try {
            writer.write(new BundleCoder().encode(metaData.getBundle()).toString());
        } catch (@NonNull final JSONException e) {
            throw new GeneralParsingException(e);
        }
    }

    @Override
    @NonNull
    public ExportResults write(@NonNull final Context context,
                               @NonNull final Writer writer,
                               @NonNull final Set<RecordType> entries,
                               @NonNull final ProgressListener progressListener)
            throws GeneralParsingException, IOException {

        final ExportResults results = new ExportResults();
        final JSONObject jsonData = new JSONObject();

        try {
            // Write styles first, and preferences next! This will facilitate & speedup
            // importing as we'll be seeking in the input archive for these.

            if (entries.contains(RecordType.Styles)
                && !progressListener.isCancelled()) {
                progressListener.publishProgress(1, context.getString(R.string.lbl_styles));
                final List<ListStyle> styles =
                        ServiceLocator.getInstance().getStyles().getStyles(context, true);
                if (!styles.isEmpty()) {
                    final JsonCoder<ListStyle> coder = new ListStyleCoder(context);
                    jsonData.put(RecordType.Styles.getName(), coder.encode(styles));
                }
                results.styles = styles.size();
            }

            if (entries.contains(RecordType.Preferences)
                && !progressListener.isCancelled()) {
                progressListener.publishProgress(1, context.getString(R.string.lbl_settings));
                final JsonCoder<SharedPreferences> coder = new SharedPreferencesCoder();
                jsonData.put(RecordType.Preferences.getName(),
                             coder.encode(PreferenceManager.getDefaultSharedPreferences(context)));
                results.preferences = 1;
            }

            if (entries.contains(RecordType.Certificates)
                && !progressListener.isCancelled()) {
                progressListener.publishProgress(1, context.getString(
                        R.string.lbl_certificate_ca));

                final JsonCoder<X509Certificate> coder = new CertificateCoder();
                final JSONObject certificates = new JSONObject();
                try {
                    // always export even if the CCS is disabled!
                    // The user might have temporarily switched it off.
                    certificates.put(CalibreContentServer.SERVER_CA, coder.encode(
                            CalibreContentServer.getCertificate(context)));
                    results.certificates++;

                } catch (@NonNull final IOException | CertificateException ignore) {
                    // no certificate (IOException) or invalid cert
                }
                if (!certificates.isEmpty()) {
                    jsonData.put(RecordType.Certificates.getName(), certificates);
                }
            }

            if (entries.contains(RecordType.Books)
                && !progressListener.isCancelled()) {
                final boolean collectCoverFilenames = entries.contains(RecordType.Cover);

                final JsonCoder<Book> coder = new BookCoder(context);

                int delta = 0;
                long lastUpdate = 0;

                final JSONArray bookArray = new JSONArray();
                final BookDao bookDao = ServiceLocator.getInstance().getBookDao();
                try (Cursor cursor = bookDao.fetchBooksForExport(mUtcSinceDateTime)) {
                    while (cursor.moveToNext() && !progressListener.isCancelled()) {
                        final Book book = Book.from(cursor);
                        bookArray.put(coder.encode(book));
                        results.addBook(book.getId());

                        if (collectCoverFilenames) {
                            for (int cIdx = 0; cIdx < 2; cIdx++) {
                                final File cover = book.getUuidCoverFile(cIdx);
                                if (cover != null && cover.exists()) {
                                    results.addCover(cover.getName());
                                }
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

        } catch (@NonNull final JSONException e) {
            throw new GeneralParsingException(e);
        }

        // Write the complete json output in one go
        if (!jsonData.isEmpty()) {
            writer.write(jsonData.toString());
        }
        return results;
    }
}
