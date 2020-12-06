/*
 * @Copyright 2020 HardBackNutter
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
package com.hardbacknutter.nevertoomanybooks.backup.base;

import android.content.ContentResolver;
import android.content.Context;

import androidx.annotation.CallSuper;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Objects;
import java.util.Set;

import org.json.JSONException;
import org.json.JSONObject;

import com.hardbacknutter.nevertoomanybooks.R;
import com.hardbacknutter.nevertoomanybooks.backup.ImportException;
import com.hardbacknutter.nevertoomanybooks.backup.ImportHelper;
import com.hardbacknutter.nevertoomanybooks.backup.ImportResults;
import com.hardbacknutter.nevertoomanybooks.backup.csv.CsvRecordReader;
import com.hardbacknutter.nevertoomanybooks.backup.json.JsonRecordReader;
import com.hardbacknutter.nevertoomanybooks.backup.xml.XmlRecordReader;
import com.hardbacknutter.nevertoomanybooks.database.DAO;
import com.hardbacknutter.nevertoomanybooks.tasks.ProgressListener;

/**
 * This is the base for full-fledged archives which can contain
 * all {@link ArchiveReaderRecord.Type}.
 * <p>
 * Note: not split in two classes like the ArchiveWriter abstract and abstract-base.
 */
public abstract class ArchiveReaderAbstract
        implements ArchiveReader {

    /** Log tag. */
    private static final String TAG = "ArchiveReaderAbstract";
    /** Buffer for {@link #openInputStream()}. */
    private static final int BUFFER_SIZE = 65535;
    /** Database Access. */
    @NonNull
    private final DAO mDb;
    /** Import configuration. */
    @NonNull
    private final ImportHelper mHelper;
    /** Provide access to the Uri InputStream. */
    @NonNull
    private final ContentResolver mContentResolver;
    /** progress message. */
    private final String mCoversText;
    /** progress message. */
    private final String mProgressMessage;
    /** The accumulated results. */
    @NonNull
    private final ImportResults mResults = new ImportResults();
    @Nullable
    private XmlRecordReader mXmlReader;
    @Nullable
    private CoverRecordReader mCoverReader;
    /** The INFO data read from the start of the archive. */
    @Nullable
    private ArchiveInfo mArchiveInfo;

    /**
     * Constructor.
     *
     * @param context Current context
     * @param helper  import configuration
     */
    protected ArchiveReaderAbstract(@NonNull final Context context,
                                    @NonNull final ImportHelper helper) {
        mDb = new DAO(TAG);
        mContentResolver = context.getContentResolver();

        mHelper = helper;

        mCoversText = context.getString(R.string.lbl_covers);
        mProgressMessage = context.getString(R.string.progress_msg_x_created_y_updated_z_skipped);
    }

    @Override
    public void validate(@NonNull final Context context)
            throws IOException, InvalidArchiveException {
        if (mArchiveInfo == null) {
            mArchiveInfo = readHeader(context);
        }

        // the info block will/can do more checks.
        mArchiveInfo.validate();
    }

    @NonNull
    protected InputStream openInputStream()
            throws IOException {
        final InputStream is = mContentResolver.openInputStream(mHelper.getUri());
        if (is == null) {
            throw new IOException("Could not resolve uri=" + mHelper.getUri());
        }
        return new BufferedInputStream(is, BUFFER_SIZE);
    }

    @NonNull
    private XmlRecordReader getXmlReader(@NonNull final Context context) {
        if (mXmlReader == null) {
            mXmlReader = new XmlRecordReader(context, mDb);
        }
        return mXmlReader;
    }

    /**
     * An archive based on this class <strong>must</strong> have an info block.
     *
     * <br><br>{@inheritDoc}
     */
    @NonNull
    @Override
    public ArchiveInfo readHeader(@NonNull final Context context)
            throws IOException, InvalidArchiveException {
        if (mArchiveInfo == null) {
            final ArchiveReaderRecord record = seek(ArchiveReaderRecord.Type.InfoHeader);
            if (record == null) {
                throw new InvalidArchiveException(ERROR_INVALID_HEADER);
            }
            final ArchiveReaderRecord.Encoding recordEncoding = record.getEncoding();
            if (recordEncoding == null) {
                throw new InvalidArchiveException(ERROR_INVALID_HEADER);
            }

            // read the INFO and store for re-use
            switch (recordEncoding) {
                case Xml:
                    mArchiveInfo = getXmlReader(context).readArchiveHeader(record);
                    break;

                case Json:
                    try {
                        mArchiveInfo = new ArchiveInfo(new JSONObject(record.asString()));
                    } catch (@NonNull final JSONException e) {
                        throw new InvalidArchiveException(e);
                    }
                    break;

                case Csv:
                case Cover:
                default:
                    throw new InvalidArchiveException(ERROR_INVALID_HEADER);
            }

            // We MUST reset the stream here, so the caller gets a pristine stream.
            closeInputStream();
        }

        return mArchiveInfo;
    }

    /**
     * Do a full import.
     * <ol>
     *     <li>The header is assumed to already have been read by {@link #readHeader(Context)}</li>
     *     <li>Seek and read {@link ArchiveReaderRecord.Type#Styles}</li>
     *     <li>Seek and read {@link ArchiveReaderRecord.Type#Preferences}</li>
     *     <li>read sequentially and read records as encountered.</li>
     * </ol>
     *
     * @param context          Current context
     * @param progressListener Listener to receive progress information.
     *
     * @return results
     *
     * @throws IOException             on failure
     * @throws ImportException         on record format failures
     * @throws InvalidArchiveException on failure to recognise a supported archive
     */
    @NonNull
    @Override
    public ImportResults read(@NonNull final Context context,
                              @NonNull final ProgressListener progressListener)
            throws IOException, ImportException, InvalidArchiveException {

        final Set<ArchiveReaderRecord.Type> importEntries = mHelper.getImportEntries();

        boolean readStyles = importEntries.contains(ArchiveReaderRecord.Type.Styles);
        boolean readPrefs = importEntries.contains(ArchiveReaderRecord.Type.Preferences);

        final boolean readBooks = importEntries.contains(ArchiveReaderRecord.Type.Books);
        final boolean readCovers = importEntries.contains(ArchiveReaderRecord.Type.Cover);

        // progress counters
        int estimatedSteps = 1;

        try {
            // Sanity check: the archive info should have been read during the validate phase
            Objects.requireNonNull(mArchiveInfo, "info");

            estimatedSteps += mArchiveInfo.getBookCount();
            if (readCovers) {
                if (mArchiveInfo.hasCoverCount()) {
                    estimatedSteps += mArchiveInfo.getCoverCount();
                } else {
                    // We don't have a count, so assume each book has 1 cover.
                    estimatedSteps *= 2;
                }
            }
            progressListener.setMaxPos(estimatedSteps);

            // Seek the styles record first.
            // We'll need them to resolve styles referenced in Preferences and Bookshelves.
            if (readStyles) {
                progressListener
                        .publishProgressStep(1, context.getString(R.string.lbl_styles_long));
                final ArchiveReaderRecord record = seek(ArchiveReaderRecord.Type.Styles);
                if (record != null) {
                    mResults.add(getXmlReader(context).read(context, record, mHelper.getOptions(),
                                                            progressListener));
                    readStyles = false;
                }
                closeInputStream();
            }

            // Seek the preferences record next, so we can apply any prefs while reading data.
            if (readPrefs) {
                progressListener.publishProgressStep(1, context.getString(R.string.lbl_settings));
                final ArchiveReaderRecord record = seek(ArchiveReaderRecord.Type.Preferences);
                if (record != null) {
                    mResults.add(getXmlReader(context).read(context, record, mHelper.getOptions(),
                                                            progressListener));
                    readPrefs = false;
                }
                closeInputStream();
            }

            if (readCovers) {
                mCoverReader = new CoverRecordReader();
            }

            // Get first record.
            ArchiveReaderRecord record = next();

            // process each entry based on type, unless we are cancelled.
            while (record != null && !progressListener.isCancelled()) {
                final ArchiveReaderRecord.Type recordType = record.getType();
                if (recordType != null) {
                    switch (recordType) {
                        case Cover: {
                            if (readCovers) {
                                readCoverRecord(context, record, progressListener);
                            }
                            break;
                        }
                        case Books: {
                            if (readBooks) {
                                readBooksRecord(context, record, progressListener);
                            }
                            break;
                        }
                        case Preferences: {
                            // yes, we have already read them at the start.
                            // Leaving the code as we might support multiple entries in the future.
                            if (readPrefs) {
                                readPrefsRecord(context, record, progressListener);
                            }
                            break;
                        }
                        case Styles: {
                            // yes, we have already read them at the start.
                            // Leaving the code as we might support multiple entries in the future.
                            if (readStyles) {
                                readStylesRecord(context, record, progressListener);
                            }
                            break;
                        }
                        case InfoHeader:
                            // skip, already handled.
                        default: {
                            break;
                        }
                    }
                }
                record = next();
            }
        } finally {
            try {
                close();
            } catch (@NonNull final IOException ignore) {
                // ignore
            }
        }

        // do a cleanup after we finished reading
        mDb.purge();

        return mResults;
    }

    private void readStylesRecord(@NonNull final Context context,
                                  @NonNull final ArchiveReaderRecord record,
                                  @NonNull final ProgressListener progressListener)
            throws IOException {
        progressListener.publishProgressStep(1, context.getString(R.string.lbl_styles_long));
        mResults.add(getXmlReader(context)
                             .read(context, record, mHelper.getOptions(), progressListener));
    }

    private void readPrefsRecord(@NonNull final Context context,
                                 @NonNull final ArchiveReaderRecord record,
                                 @NonNull final ProgressListener progressListener)
            throws IOException {
        progressListener.publishProgressStep(1, context.getString(R.string.lbl_settings));
        mResults.add(getXmlReader(context)
                             .read(context, record, mHelper.getOptions(), progressListener));
    }

    private void readCoverRecord(@NonNull final Context context,
                                 @NonNull final ArchiveReaderRecord record,
                                 @NonNull final ProgressListener progressListener) {
        //noinspection ConstantConditions
        mResults.add(mCoverReader.read(context, record, mHelper.getOptions(), progressListener));
        // send accumulated progress for the total nr of covers
        final String msg = String.format(mProgressMessage,
                                         mCoversText,
                                         mResults.coversCreated,
                                         mResults.coversUpdated,
                                         mResults.coversSkipped);
        progressListener.publishProgressStep(1, msg);
    }

    private void readBooksRecord(@NonNull final Context context,
                                 @NonNull final ArchiveReaderRecord record,
                                 @NonNull final ProgressListener progressListener)
            throws IOException, ImportException {
        final ArchiveReaderRecord.Encoding recordEncoding = record.getEncoding();
        if (recordEncoding != null) {
            switch (recordEncoding) {
                case Csv:
                    try (RecordReader recordReader = new CsvRecordReader(context, mDb)) {
                        mResults.add(recordReader.read(context, record, mHelper.getOptions(),
                                                       progressListener));
                    }
                    break;

                case Json:
                    try (RecordReader recordReader = new JsonRecordReader(context, mDb)) {
                        mResults.add(recordReader.read(context, record, mHelper.getOptions(),
                                                       progressListener));
                    }
                    break;

                case Cover:
                case Xml:
                default:
                    // not applicable
                    break;
            }
        }
    }

    /**
     * Concrete reader should implement {@link #closeInputStream}.
     *
     * @throws IOException on failure
     */
    @Override
    @CallSuper
    public void close()
            throws IOException {
        closeInputStream();

        if (mXmlReader != null) {
            mXmlReader.close();
        }
        if (mCoverReader != null) {
            mCoverReader.close();
        }

        mDb.purge();
        mDb.close();
    }

    /**
     * Read the next {@link ArchiveReaderRecord} from the backup.
     *
     * @return The next record, or {@code null} if at end
     *
     * @throws IOException on failure
     */
    @Nullable
    protected abstract ArchiveReaderRecord next()
            throws InvalidArchiveException, IOException;

    /**
     * Scan the input for the desired record type.
     * It's the responsibility of the caller to call {@link #closeInputStream} as needed.
     *
     * @param type to get
     *
     * @return the record if found, or {@code null} if not found.
     *
     * @throws InvalidArchiveException on failure to recognise a supported archive
     * @throws IOException             on failure
     */
    @Nullable
    protected abstract ArchiveReaderRecord seek(@NonNull ArchiveReaderRecord.Type type)
            throws InvalidArchiveException, IOException;

    /**
     * Reset the reader so {@link #next()} will get the first record.
     *
     * @throws IOException on failure
     */
    protected abstract void closeInputStream()
            throws IOException;
}
