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
package com.hardbacknutter.nevertoomanybooks.backup.base;

import android.content.ContentResolver;
import android.content.Context;

import androidx.annotation.CallSuper;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.WorkerThread;

import java.io.BufferedInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.util.EnumSet;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

import com.hardbacknutter.nevertoomanybooks.R;
import com.hardbacknutter.nevertoomanybooks.ServiceLocator;
import com.hardbacknutter.nevertoomanybooks.backup.ImportException;
import com.hardbacknutter.nevertoomanybooks.backup.ImportHelper;
import com.hardbacknutter.nevertoomanybooks.backup.ImportResults;
import com.hardbacknutter.nevertoomanybooks.backup.RecordEncoding;
import com.hardbacknutter.nevertoomanybooks.backup.RecordReader;
import com.hardbacknutter.nevertoomanybooks.backup.RecordType;
import com.hardbacknutter.nevertoomanybooks.backup.bin.CoverRecordReader;
import com.hardbacknutter.nevertoomanybooks.tasks.ProgressListener;
import com.hardbacknutter.nevertoomanybooks.utils.exceptions.DiskFullException;

/**
 * This is the base for full-fledged archives which can contain
 * all {@link RecordType}.
 * <p>
 * It's encoding-agnostic with the exception of {@link RecordEncoding#Cover}.
 * <p>
 * Currently supported formats.
 * <ul>
 *     <li>v3:
 *         <ul>
 *             <li>{@link RecordType#MetaData} :     {@link RecordEncoding#Json}</li>
 *             <li>{@link RecordType#Styles} :       {@link RecordEncoding#Json}</li>
 *             <li>{@link RecordType#Preferences} :  {@link RecordEncoding#Json}</li>
 *             <li>{@link RecordType#Certificates} : {@link RecordEncoding#Json}</li>
 *             <li>{@link RecordType#Books} :        {@link RecordEncoding#Json}</li>
 *             <li>Multiple {@link RecordType#Cover}</li>
 *         </ul>
 *     </li>
 *     <li>v2:
 *         <ul>
 *             <li>{@link RecordType#MetaData} :     {@link RecordEncoding#Xml}</li>
 *             <li>{@link RecordType#Styles} :       {@link RecordEncoding#Xml}</li>
 *             <li>{@link RecordType#Preferences} :  {@link RecordEncoding#Xml}</li>
 *             <li>{@link RecordType#Certificates} : {@link RecordEncoding#Xml}</li>
 *             <li>{@link RecordType#Books} :        {@link RecordEncoding#Csv}</li>
 *             <li>Multiple {@link RecordType#Cover}</li>
 *         </ul>
 *     </li>
 *     <li>v1: obsolete</li>
 * </ul>
 */
public abstract class ArchiveReaderAbstract
        implements ArchiveReader {

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

    /** Re-usable cover reader. */
    @Nullable
    private RecordReader mCoverReader;
    /** The INFO data read from the start of the archive. */
    @Nullable
    private ArchiveMetaData mArchiveMetaData;

    /**
     * Constructor.
     *
     * @param context Current context
     * @param helper  import configuration
     */
    protected ArchiveReaderAbstract(@NonNull final Context context,
                                    @NonNull final ImportHelper helper) {
        mHelper = helper;
        mContentResolver = context.getContentResolver();

        mCoversText = context.getString(R.string.lbl_covers);
        mProgressMessage = context.getString(R.string.progress_msg_x_created_y_updated_z_skipped);
    }

    @Override
    public void validate(@NonNull final Context context)
            throws InvalidArchiveException, ImportException, IOException {
        if (mArchiveMetaData == null) {
            mArchiveMetaData = readMetaData(context);
        }

        // the info block will/can do more checks.
        mArchiveMetaData.validate();
    }

    @NonNull
    protected InputStream openInputStream()
            throws FileNotFoundException {
        final InputStream is = mContentResolver.openInputStream(mHelper.getUri());
        if (is == null) {
            throw new FileNotFoundException(mHelper.getUri().toString());
        }
        return new BufferedInputStream(is, RecordReader.BUFFER_SIZE);
    }

    /**
     * An archive based on this class <strong>must</strong> have an info block.
     *
     * <br><br>{@inheritDoc}
     */
    @NonNull
    @Override
    @WorkerThread
    public ArchiveMetaData readMetaData(@NonNull final Context context)
            throws InvalidArchiveException, ImportException, IOException {
        if (mArchiveMetaData == null) {
            final ArchiveReaderRecord record = seek(RecordType.MetaData);
            if (record == null) {
                throw new InvalidArchiveException(ERROR_INVALID_HEADER);
            }

            final Optional<RecordEncoding> encoding = record.getEncoding();
            if (encoding.isPresent()) {
                try (RecordReader recordReader = encoding
                        .get().createReader(context, EnumSet.of(RecordType.MetaData))) {
                    mArchiveMetaData = recordReader.readMetaData(record);
                }
            }

            // We MUST reset the stream here, so the caller gets a pristine stream.
            closeInputStream();
        }

        if (mArchiveMetaData == null) {
            throw new InvalidArchiveException(ERROR_INVALID_HEADER);
        }
        return mArchiveMetaData;
    }

    /**
     * Do a full import.
     * <ol>
     * <li>The header is assumed to already have been read by {@link #readMetaData(Context)}</li>
     * <li>Seek and read {@link RecordType#Styles}</li>
     * <li>Seek and read {@link RecordType#Preferences}</li>
     * <li>read sequentially and read records as encountered.</li>
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
    @WorkerThread
    public ImportResults read(@NonNull final Context context,
                              @NonNull final ProgressListener progressListener)
            throws InvalidArchiveException, ImportException,
                   IOException, DiskFullException {

        // Sanity check: the archive info should have been read during the validate phase
        Objects.requireNonNull(mArchiveMetaData, "info");

        if (mHelper.getImportEntries().contains(RecordType.Cover)) {
            mCoverReader = new CoverRecordReader();
        }

        final int archiveVersion = mArchiveMetaData.getArchiveVersion();
        switch (archiveVersion) {
            case 4:
                // future...
                readV4(context, progressListener);
                break;
            case 3:
            case 2:
                readV2(context, progressListener);
                break;
            case 1:
                throw new InvalidArchiveException("v1 no longer supported");

            default:
                throw new InvalidArchiveException("version=" + archiveVersion);
        }

        return mResults;
    }

    private void readV4(@NonNull final Context context,
                        @NonNull final ProgressListener progressListener)
            throws InvalidArchiveException, ImportException,
                   IOException, DiskFullException {

        ArchiveReaderRecord record = seek(RecordType.AutoDetect);
        while (record != null && !progressListener.isCancelled()) {
            if (record.getType().isPresent()) {
                readRecord(context, record, progressListener);
            }
            record = next();
        }
    }

    private void readV2(@NonNull final Context context,
                        @NonNull final ProgressListener progressListener)
            throws InvalidArchiveException, ImportException,
                   IOException, DiskFullException {

        final Set<RecordType> importEntries = mHelper.getImportEntries();
        try {
            final boolean readBooks = importEntries.contains(RecordType.Books);
            final boolean readCovers = importEntries.contains(RecordType.Cover);
            final boolean readCertificates = importEntries.contains(RecordType.Certificates);

            //noinspection ConstantConditions
            int estimatedSteps = 1 + mArchiveMetaData.getBookCount();
            if (readCovers) {
                if (mArchiveMetaData.hasCoverCount()) {
                    estimatedSteps += mArchiveMetaData.getCoverCount();
                } else {
                    // We don't have a count, so assume each book has 1 cover.
                    estimatedSteps *= 2;
                }
            }
            progressListener.setMaxPos(estimatedSteps);

            // Seek the styles record first.
            // We'll need them to resolve styles referenced in Preferences and Bookshelves.
            if (importEntries.contains(RecordType.Styles)) {
                progressListener.publishProgress(
                        1, context.getString(R.string.lbl_styles_long));
                final ArchiveReaderRecord record = seek(RecordType.Styles);
                if (record != null) {
                    readRecord(context, record, progressListener);
                }
                closeInputStream();
            }

            // Seek the preferences record next, so we can apply any prefs while reading data.
            if (importEntries.contains(RecordType.Preferences)) {
                progressListener.publishProgress(
                        1, context.getString(R.string.lbl_settings));
                final ArchiveReaderRecord record = seek(RecordType.Preferences);
                if (record != null) {
                    readRecord(context, record, progressListener);
                }
                closeInputStream();
            }

            // Get first record.
            ArchiveReaderRecord record = next();

            // process each entry based on type, unless we are cancelled.
            while (record != null && !progressListener.isCancelled()) {
                if (record.getType().isPresent()) {
                    final RecordType type = record.getType().get();

                    if ((type == RecordType.Cover && readCovers)
                        || (type == RecordType.Books && readBooks)
                        || (type == RecordType.Certificates && readCertificates)
                    ) {
                        readRecord(context, record, progressListener);
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
    }

    private void readRecord(@NonNull final Context context,
                            @NonNull final ArchiveReaderRecord record,
                            @NonNull final ProgressListener progressListener)
            throws InvalidArchiveException, ImportException,
                   IOException, DiskFullException {

        final Optional<RecordEncoding> encoding = record.getEncoding();
        if (encoding.isPresent()) {

            // there will be many covers... we're re-using a single RecordReader
            if (encoding.get() == RecordEncoding.Cover) {
                //noinspection ConstantConditions
                mResults.add(mCoverReader.read(context, record, mHelper, progressListener));
                // send accumulated progress for the total nr of covers
                final String msg = String.format(mProgressMessage,
                                                 mCoversText,
                                                 mResults.coversCreated,
                                                 mResults.coversUpdated,
                                                 mResults.coversSkipped);
                progressListener.publishProgress(1, msg);

            } else {
                // everything else, keep it clean and create a new reader for each entry.
                try (RecordReader recordReader = encoding
                        .get().createReader(context, mHelper.getImportEntries())) {

                    mResults.add(recordReader.read(context, record, mHelper, progressListener));
                }
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

        if (mCoverReader != null) {
            mCoverReader.close();
        }

        ServiceLocator.getInstance().getMaintenanceDao().purge();
    }

    /**
     * Read the next {@link ArchiveReaderRecord} from the backup.
     *
     * @return The next record, or {@code null} if at end
     *
     * @throws IOException on failure
     */
    @Nullable
    @WorkerThread
    protected abstract ArchiveReaderRecord next()
            throws IOException;

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
    @WorkerThread
    protected abstract ArchiveReaderRecord seek(@NonNull RecordType type)
            throws InvalidArchiveException, IOException;

    /**
     * Reset the reader so {@link #next()} will get the first record.
     *
     * @throws IOException on failure
     */
    protected abstract void closeInputStream()
            throws IOException;
}
