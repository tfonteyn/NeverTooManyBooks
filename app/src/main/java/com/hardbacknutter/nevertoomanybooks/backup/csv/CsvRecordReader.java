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
package com.hardbacknutter.nevertoomanybooks.backup.csv;

import android.content.Context;
import android.database.sqlite.SQLiteDoneException;
import android.os.Parcel;
import android.os.Parcelable;

import androidx.annotation.AnyThread;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

import com.hardbacknutter.nevertoomanybooks.BuildConfig;
import com.hardbacknutter.nevertoomanybooks.DEBUG_SWITCHES;
import com.hardbacknutter.nevertoomanybooks.R;
import com.hardbacknutter.nevertoomanybooks.ServiceLocator;
import com.hardbacknutter.nevertoomanybooks.backup.BaseRecordReader;
import com.hardbacknutter.nevertoomanybooks.backup.ImportResults;
import com.hardbacknutter.nevertoomanybooks.backup.csv.coders.BookCoder;
import com.hardbacknutter.nevertoomanybooks.booklist.style.Style;
import com.hardbacknutter.nevertoomanybooks.core.LoggerFactory;
import com.hardbacknutter.nevertoomanybooks.core.database.DaoWriteException;
import com.hardbacknutter.nevertoomanybooks.core.database.SynchronizedDb;
import com.hardbacknutter.nevertoomanybooks.core.database.Synchronizer;
import com.hardbacknutter.nevertoomanybooks.core.storage.StorageException;
import com.hardbacknutter.nevertoomanybooks.core.tasks.ProgressListener;
import com.hardbacknutter.nevertoomanybooks.database.DBKey;
import com.hardbacknutter.nevertoomanybooks.entities.Book;
import com.hardbacknutter.nevertoomanybooks.io.ArchiveReaderRecord;
import com.hardbacknutter.nevertoomanybooks.io.DataReader;
import com.hardbacknutter.nevertoomanybooks.io.DataReaderException;
import com.hardbacknutter.nevertoomanybooks.io.RecordReader;
import com.hardbacknutter.nevertoomanybooks.io.RecordType;

/**
 * Implementation of {@link RecordReader} that reads a CSV file.
 * <p>
 * Supports:
 * <ul>
 *      <li>{@link RecordType#Books}</li>
 * </ul>
 * <p>
 * A CSV file which was not written by this app, should be careful about encoding the following
 * characters:
 * <p>
 * <strong>DOUBLE escape the '*' character; i.e. '*' should be encoded as \\\\*</strong>
 * <p>
 * Always <strong>escape:</strong>
 * <ul>
 *      <li>"</li>
 *      <li>'</li>
 *      <li>\</li>
 *      <li>\r</li>
 *      <li>\n</li>
 *      <li>\t</li>
 *      <li>|</li>
 *      <li>(</li>
 *      <li>)</li>
 * </ul>
 * <p>
 * Unescaped special characters:
 * <ul>
 *      <li>',' is recognised/used in an Author name: "family, given-names",<br>
 *          and as a list separator in a list of Bookshelf names.</li>
 *      <li>'|' is used as an element separator for fields that take more than one value.<br>
 *          e.g. a list of Author names, Series, ...</li>
 *      <li>'*' is used in few places where an element itself can consist of multiple parts.
 *          e.g. See Author and Series object encoding. (for advanced usage)</li>
 *      <li>'(' and ')' are used to add numbers or dates (between the brackets) to items.
 *          e.g. TOC entries can contain a date, Series will have the book number,... </li>
 * </ul>
 * <p>
 * Space characters are encoded for Authors names while exporting,
 * but it's not strictly needed although some exotic names might get mangled;
 * when in doubt, escape.
 * <p>
 * ENHANCE: after an import offer to run an internet update on the list of book id's
 * This would/could be split up....
 * - books inserted which had a uuid/id
 * - books updated
 * - books inserted while not having a uuid/id; i.e. unknown input source -> would require ISBN
 */
public class CsvRecordReader
        extends BaseRecordReader {

    /** Log tag. */
    private static final String TAG = "CsvRecordReader";
    private static final String GOODREADS_ = "goodreads_";

    /**
     * Constructor.
     * <p>
     * Only supports {@link RecordType#Books}.
     *
     * @param systemLocale to use for ISO date parsing
     * @param updateOption options
     */
    @AnyThread
    public CsvRecordReader(@NonNull final Locale systemLocale,
                           @NonNull final DataReader.Updates updateOption) {
        super(systemLocale, updateOption);
    }

    /**
     * This CSV parser is not a complete parser, but it is "good enough".
     *
     * @param context Current context
     * @param row     row number; used for error reporting
     * @param line    with CSV fields
     *
     * @return a list with the row fields
     *
     * @throws DataReaderException on failure to parse this line
     */
    @NonNull
    public static List<String> parse(@NonNull final Context context,
                                     final int row,
                                     @NonNull final String line)
            throws DataReaderException {
        try {
            // Fields found in row
            final List<String> fields = new ArrayList<>();
            // Temporary storage for current field
            final StringBuilder sb = new StringBuilder();

            // Current position
            int pos = 0;
            // In a quoted string
            boolean inQuotes = false;
            // Found an escape char
            boolean isEsc = false;
            // 'Current' char
            char c;
            // Last position in row
            final int endPos = line.length() - 1;
            // 'Next' char
            char next = line.isEmpty() ? '\0' : line.charAt(0);

            // '\0' is used as (and artificial) null character indicating end-of-string.
            while (next != '\0') {
                // Get current and next char
                c = next;
                if (pos < endPos) {
                    next = line.charAt(pos + 1);
                } else {
                    next = '\0';
                }

                if (isEsc) {
                    switch (c) {
                        case '\\':
                            sb.append('\\');
                            break;

                        case 'r':
                            sb.append('\r');
                            break;

                        case 't':
                            sb.append('\t');
                            break;

                        case 'n':
                            sb.append('\n');
                            break;

                        default:
                            sb.append(c);
                            break;
                    }
                    isEsc = false;

                } else if (inQuotes) {
                    switch (c) {
                        case '"':
                            if (next == '"') {
                                // substitute two successive quotes with one quote
                                pos++;
                                if (pos < endPos) {
                                    next = line.charAt(pos + 1);
                                } else {
                                    next = '\0';
                                }
                                sb.append(c);

                            } else {
                                // end of quoted string
                                inQuotes = false;
                            }
                            break;

                        case '\\':
                            isEsc = true;
                            break;

                        default:
                            sb.append(c);
                            break;
                    }
                } else {
                    // This is just a raw string; no escape or quote active.
                    // Ignore leading whitespace.
                    if (c != ' ' && c != '\t' || sb.length() != 0) {
                        switch (c) {
                            case '"':
                                if (sb.length() > 0) {
                                    // Fields with inner quotes MUST be escaped
                                    throw new DataReaderException(context.getString(
                                            R.string.warning_import_csv_unescaped_quote, row, pos));
                                } else {
                                    inQuotes = true;
                                }
                                break;

                            case '\\':
                                isEsc = true;
                                break;

                            case ',':
                                // Add this field and reset for the next.
                                fields.add(sb.toString());
                                sb.setLength(0);
                                break;

                            default:
                                sb.append(c);
                                break;
                        }
                    }
                }
                pos++;
            }

            // Add the remaining chunk
            fields.add(sb.toString());

            return fields;

        } catch (@NonNull final StackOverflowError e) {
            // StackOverflowError has been seen when the StringBuilder overflows.
            // The stack at the time was 1040kb. Not reproduced as yet.
            LoggerFactory.getLogger().e(TAG, e, "line.length=" + line.length()
                                                + "\n" + line);
            throw new DataReaderException(context.getString(R.string.error_import_csv_line_to_long,
                                                            row, line.length()), e);
        }
    }

    @Override
    @NonNull
    public ImportResults read(@NonNull final Context context,
                              @NonNull final ArchiveReaderRecord record,
                              @NonNull final ProgressListener progressListener)
            throws DataReaderException,
                   StorageException,
                   IOException {

        results = new ImportResults();

        if (record.getType().isPresent()) {
            if (record.getType().get() == RecordType.Books) {

                // Read the whole file content into a list of lines.
                final List<String> allLines;
                // Don't close this stream
                final InputStream is = record.getInputStream();
                final Reader isr = new InputStreamReader(is, StandardCharsets.UTF_8);
                final BufferedReader reader = new BufferedReader(isr, RecordReader.BUFFER_SIZE);

                try {
                    allLines = reader.lines().collect(Collectors.toList());
                } catch (@NonNull final UncheckedIOException e) {
                    // caused by lines()
                    //noinspection DataFlowIssue
                    throw e.getCause();
                }

                if (!allLines.isEmpty()) {
                    readBooks(context, allLines, progressListener);
                }
            }
        }

        if (BuildConfig.DEBUG && DEBUG_SWITCHES.IMPORT_CSV_BOOKS) {
            LoggerFactory.getLogger().d(TAG, "read", "results=" + results);
        }
        return results;
    }

    private void readBooks(@NonNull final Context context,
                           @NonNull final List<String> books,
                           @NonNull final ProgressListener progressListener)
            throws StorageException,
                   DataReaderException {

        // First line in the import file must be the column names.
        final String columnHeader = books.get(0);
        // Now try and guess where this CSV file might have come from.
        final Origin origin = Origin.guess(columnHeader);

        // Parse the column header to use as keys into the book.
        final List<String> csvColumnNames = parse(context, 0, columnHeader)
                .stream()
                .map(name -> name.toLowerCase(Locale.ENGLISH))
                .map(origin::mapColumnName)
                .collect(Collectors.toList());

        // Check for required columns we cannot do without
        // If a sync was requested, we'll need this column or cannot proceed.
        if (getUpdateOption() == DataReader.Updates.OnlyNewer) {
            requireColumnOrThrow(context, csvColumnNames, DBKey.DATE_LAST_UPDATED__UTC);
        }

        // One book == One row. We start after the headings row.
        int row = 1;
        // Instance in time when we last send a progress message
        long lastUpdateTime = 0;
        // Count the nr of books in between progress updates.
        int delta = 0;

        // not perfect, but good enough
        if (progressListener.getMaxPos() < books.size()) {
            progressListener.setMaxPos(books.size());
        }

        final SynchronizedDb db = ServiceLocator.getInstance().getDb();

        Synchronizer.SyncLock txLock = null;

        final Style defaultStyle = ServiceLocator.getInstance().getStyles().getDefault();
        final BookCoder bookCoder = new BookCoder(context, defaultStyle);

        while (row < books.size() && !progressListener.isCancelled()) {

            if (!db.inTransaction()) {
                txLock = db.beginTransaction(true);
            }
            try {
                final List<String> csvDataRow = parse(context, row, books.get(row));

                if (csvDataRow.size() == csvColumnNames.size()) {
                    final Book book = bookCoder.decode(context, csvColumnNames, csvDataRow);
                    preprocessId(book);
                    preprocessUuid(book);
                    importBook(context, book);

                    if (txLock != null) {
                        db.setTransactionSuccessful();
                    }
                } else {
                    final String msg = context.getString(
                            R.string.error_import_csv_column_count_mismatch, row);
                    results.handleRowException(context, row, new DataReaderException(msg), msg);
                }
            } catch (@NonNull final DaoWriteException | DataReaderException
                                    | SQLiteDoneException e) {
                results.handleRowException(context, row, e, null);

            } finally {
                if (txLock != null) {
                    db.endTransaction(txLock);
                }
            }

            row++;

            delta++;
            final long now = System.currentTimeMillis();
            if (now - lastUpdateTime > progressListener.getUpdateIntervalInMs()
                && !progressListener.isCancelled()) {
                progressListener.publishProgress(delta, results.createBooksSummaryLine(context));
                lastUpdateTime = now;
                delta = 0;
            }
        }

        // minus 1 to compensate for the last increment
        results.booksProcessed = row - 1;
    }

    /**
     * Process the (optional) ID into a known format/type.
     *
     * @param book the book
     */
    private void preprocessId(@NonNull final Book book) {

        // String: see book init, we copied all fields we find in the import file as text.
        final String idStr = book.getString(DBKey.PK_ID, null);
        // ALWAYS remove the String typed id first!
        book.remove(DBKey.PK_ID);

        if (idStr != null && !idStr.isEmpty()) {
            // convert it to the expected "long" type
            try {
                final long id = Long.parseLong(idStr);
                // Add it back to the book as a long
                book.putLong(DBKey.PK_ID, id);

            } catch (@NonNull final NumberFormatException ignore) {
                // don't log, we'll continue without an id
            }
        }
    }

    /**
     * Process the (optional) UUID into a known format/type.
     *
     * @param book the book
     */
    private void preprocessUuid(@NonNull final Book book) {

        @Nullable
        final String uuid;

        // Get the "book_uuid", and remove from book if null/blank
        if (book.contains(DBKey.BOOK_UUID)) {
            uuid = book.getString(DBKey.BOOK_UUID, null);
            if (uuid == null || uuid.isEmpty()) {
                book.remove(DBKey.BOOK_UUID);
            }

        } else if (book.contains("uuid")) {
            // second chance: see if we have a "uuid" column.
            uuid = book.getString("uuid", null);
            // ALWAYS remove as we won't use this key again.
            book.remove("uuid");
            // but if we got a UUID from it, store that UUID using the correct key
            if (uuid != null && !uuid.isEmpty()) {
                book.putString(DBKey.BOOK_UUID, uuid);
            }
        }
    }

    /**
     * Require a column to be present. First one found; remainders are not needed.
     *
     * @param context        Current context
     * @param columnsPresent the column names which are present
     * @param names          columns which should be checked for, in order of preference
     *
     * @throws DataReaderException if no suitable column is present
     */
    private void requireColumnOrThrow(@NonNull final Context context,
                                      @NonNull final List<String> columnsPresent,
                                      @NonNull final String... names)
            throws DataReaderException {


        for (final String name : names) {
            if (columnsPresent.contains(name)) {
                return;
            }
        }

        throw new DataReaderException(context.getString(
                R.string.error_import_csv_missing_columns_x, String.join(",", names)));
    }

    public enum Origin
            implements Parcelable {
        /** A Goodreads export. */
        Goodreads(R.string.site_goodreads) {
            @NonNull
            public String mapColumnName(@NonNull final String name) {
                // From a test export on 2024-04-22:
                // Book Id,Title,
                // Author,Author l-f,Additional Authors,
                // ISBN,ISBN13,
                // My Rating,Average Rating,
                // Publisher,Binding,Number of Pages,
                // Year Published,Original Publication Year,Date Read,Date Added,
                // Bookshelves,Bookshelves with positions,Exclusive Shelf,
                // My Review, Spoiler,
                // Private Notes,
                // Read Count,Owned Copies
                switch (name) {
                    case "book id":
                        return DBKey.SID_GOODREADS_BOOK;
                    case "title":
                        return DBKey.TITLE;
                    case "author l-f":
                        // Will be decoded during import
                        return DBKey.AUTHOR_FORMATTED;
                    case "additional authors":
                        // Added in addition to the one above
                        return BookCoder.GOODREADS_ADDITIONAL_AUTHORS;
                    case "isbn":
                        // ISBN-10; will be used if the "isbn13" field is empty
                        return BookCoder.GOODREADS_ISBN10;
                    case "isbn13":
                        return DBKey.BOOK_ISBN;
                    case "my rating":
                        return BookCoder.GOODREADS_MY_RATING;
                    case "average rating":
                        return BookCoder.GOODREADS_AVERAGE_RATING;
                    case "publisher":
                        return DBKey.PUBLISHER_NAME;
                    case "binding":
                        return DBKey.FORMAT;
                    case "number of pages":
                        return DBKey.PAGE_COUNT;
                    case "year published":
                        return DBKey.BOOK_PUBLICATION__DATE;
                    case "original publication year":
                        return DBKey.FIRST_PUBLICATION__DATE;
                    case "date read":
                        return DBKey.READ_END__DATE;
                    case "date added":
                        return DBKey.DATE_ADDED__UTC;
                    case "bookshelves":
                        return BookCoder.GOODREADS_BOOKSHELVES;
                    case "private notes":
                        return DBKey.PERSONAL_NOTES;

                    // The next set are simply ignored for now
                    case "author":
                    case "bookshelves with positions":
                    case "exclusive shelf":
                    case "my review":
                    case "spoiler":
                    case "read count":
                    case "owned copies":
                        return GOODREADS_ + name;

                    default:
                        // Unknown on 2024-04-22
                        LoggerFactory.getLogger().w(TAG, "Unknown Goodreads csv column=" + name);
                        return GOODREADS_ + name;
                }
            }
        },
        /** The original BC format, or the extended but obsolete NTMB 1.x .. 3.x format. */
        BC(R.string.lbl_book_catalogue) {
            @NonNull
            public String mapColumnName(@NonNull final String name) {
                return name;
            }
        },
        /** No idea... */
        Unknown(R.string.unknown) {
            @NonNull
            public String mapColumnName(@NonNull final String name) {
                return name;
            }
        };

        /** Bundle key if we get passed around. */
        public static final String BKEY = "Origin:bk";

        /** {@link Parcelable}. */
        public static final Creator<Origin> CREATOR = new Creator<>() {
            @Override
            @NonNull
            public Origin createFromParcel(@NonNull final Parcel in) {
                return values()[in.readInt()];
            }

            @Override
            @NonNull
            public Origin[] newArray(final int size) {
                return new Origin[size];
            }
        };

        @StringRes
        private final int labelId;

        Origin(@StringRes final int labelId) {
            this.labelId = labelId;
        }

        @NonNull
        static Origin guess(@NonNull final String columnHeader) {
            // RELEASE: check the latest Goodreads CSV export file header.
            // A download on 2024-04-22 showed a header starting like this:
            if (columnHeader.startsWith(
                    "Book Id,Title,Author,Author l-f,Additional Authors,ISBN,ISBN13,")) {
                return Origin.Goodreads;

            } else if (columnHeader.startsWith("\"_id\",")) {
                return Origin.BC;

            }

            return Origin.Unknown;
        }

        /**
         * Map a column name as found in the input file to a {@link DBKey} if possible.
         * Columns that need more processing <strong>MUST NOT</strong> use a {@link DBKey}.
         *
         * @param name to map
         *
         * @return mapped name
         */
        @NonNull
        public abstract String mapColumnName(@NonNull String name);

        @Override
        public int describeContents() {
            return 0;
        }

        @Override
        public void writeToParcel(@NonNull final Parcel dest,
                                  final int flags) {
            dest.writeInt(ordinal());
        }

        @NonNull
        public CharSequence getLabel(@NonNull final Context context) {
            return context.getString(labelId);
        }
    }
}
