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
package com.hardbacknutter.nevertoomanybooks.backup;

import android.content.Context;
import android.net.Uri;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;
import androidx.annotation.WorkerThread;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.EnumSet;
import java.util.Set;

import javax.net.ssl.SSLException;

import com.hardbacknutter.nevertoomanybooks.BuildConfig;
import com.hardbacknutter.nevertoomanybooks.backup.common.ArchiveEncoding;
import com.hardbacknutter.nevertoomanybooks.backup.common.ArchiveReader;
import com.hardbacknutter.nevertoomanybooks.backup.common.InvalidArchiveException;
import com.hardbacknutter.nevertoomanybooks.backup.common.RecordType;
import com.hardbacknutter.nevertoomanybooks.utils.UriInfo;
import com.hardbacknutter.nevertoomanybooks.utils.exceptions.CoverStorageException;

public final class ImportHelper {

    /** <strong>What</strong> is going to be imported. */
    @NonNull
    private final Set<RecordType> mRecordTypes = EnumSet.noneOf(RecordType.class);
    /** <strong>Where</strong> we read from. */
    @NonNull
    private final Uri mUri;
    /** <strong>How</strong> to read from the Uri. */
    @NonNull
    private final ArchiveEncoding mEncoding;


    /**
     * New Books/Covers are always imported
     * (if {@link RecordType#Books} is set obviously).
     */
    private Updates mUpdateOption = Updates.Skip;
    @SuppressWarnings("FieldNotUsedInToString")
    @Nullable
    private UriInfo mUriInfo;

    /**
     * Private constructor. Use the factory method instead.
     *
     * @param uri      to read from
     * @param encoding which the uri uses
     */
    private ImportHelper(@NonNull final Uri uri,
                         @NonNull final ArchiveEncoding encoding) {
        mUri = uri;
        mEncoding = encoding;
        initWithDefault(encoding);
    }

    /**
     * Constructor. The encoding will be determined from the Uri.
     *
     * @param context Current context
     * @param uri     to read from
     *
     * @throws InvalidArchiveException on failure to recognise a supported archive
     */
    @VisibleForTesting(otherwise = VisibleForTesting.PACKAGE_PRIVATE)
    public static ImportHelper newInstance(@NonNull final Context context,
                                           @NonNull final Uri uri)
            throws FileNotFoundException, InvalidArchiveException {

        final ArchiveEncoding encoding = ArchiveEncoding.getEncoding(context, uri).orElseThrow(
                () -> new InvalidArchiveException(uri.toString()));

        return new ImportHelper(uri, encoding);
    }

    private void initWithDefault(@NonNull final ArchiveEncoding encoding) {
        mRecordTypes.clear();
        mRecordTypes.add(RecordType.MetaData);

        switch (encoding) {
            case Csv:
                mRecordTypes.add(RecordType.Books);
                mUpdateOption = Updates.OnlyNewer;
                break;

            case Zip:
            case Tar:
                mRecordTypes.add(RecordType.Styles);
                mRecordTypes.add(RecordType.Preferences);
                mRecordTypes.add(RecordType.Certificates);
                mRecordTypes.add(RecordType.Books);
                mRecordTypes.add(RecordType.Cover);
                mUpdateOption = Updates.OnlyNewer;
                break;

            case SqLiteDb:
                mRecordTypes.add(RecordType.Books);
                mUpdateOption = Updates.Skip;
                break;

            case Json:
                mRecordTypes.add(RecordType.Styles);
                mRecordTypes.add(RecordType.Preferences);
                mRecordTypes.add(RecordType.Certificates);
                mRecordTypes.add(RecordType.Books);
                mUpdateOption = Updates.OnlyNewer;
                break;

            case Xml:
            default:
                break;
        }
    }

    /**
     * Get the Uri for the user location to read from.
     *
     * @return Uri
     */
    @NonNull
    public Uri getUri() {
        return mUri;
    }

    /**
     * Get the {@link UriInfo} of the archive (based on the Uri/Encoding).
     *
     * @return info
     */
    @NonNull
    public UriInfo getUriInfo() {
        if (mUriInfo == null) {
            mUriInfo = new UriInfo(mUri);
        }
        return mUriInfo;
    }

    /**
     * Get the encoding of the source.
     *
     * @return encoding
     */
    @NonNull
    ArchiveEncoding getEncoding() {
        return mEncoding;
    }

    /**
     * Create an {@link ArchiveReader} based on the type.
     *
     * @param context Current context
     *
     * @return a new reader
     *
     * @throws InvalidArchiveException on failure to produce a supported reader
     * @throws ImportException         on a decoding/parsing of data issue
     * @throws IOException             on other failures
     * @throws SSLException            on secure connection failures
     */
    @NonNull
    @WorkerThread
    public ArchiveReader createReader(@NonNull final Context context)
            throws InvalidArchiveException,
                   ImportException,
                   IOException,
                   CoverStorageException {
        if (BuildConfig.DEBUG /* always */) {
            if (mRecordTypes.isEmpty()) {
                throw new IllegalStateException("mImportEntries is empty");
            }
        }
        return mEncoding.createReader(context, this);
    }

    public void setRecordType(@NonNull final RecordType recordType,
                              final boolean isSet) {
        if (isSet) {
            mRecordTypes.add(recordType);
        } else {
            mRecordTypes.remove(recordType);
        }
    }

    @NonNull
    public Set<RecordType> getRecordTypes() {
        return mRecordTypes;
    }

    @NonNull
    public Updates getUpdateOption() {
        return mUpdateOption;
    }

    public void setUpdateOption(@NonNull final Updates updateOption) {
        mUpdateOption = updateOption;
    }

    @Override
    @NonNull
    public String toString() {
        return "ImportHelper{"
               + "mUri=" + mUri
               + ", mEncoding=" + mEncoding
               + ", mRecordTypes=" + mRecordTypes
               + ", mUpdateOption=" + mUpdateOption
               + '}';
    }

    /**
     * Existing Books/Covers handling.
     */
    public enum Updates {
        /** skip updates entirely. Current data is untouched. */
        Skip,
        /** Overwrite current data with incoming data. */
        Overwrite,
        /** check the "update_date" field and only import newer data. */
        OnlyNewer
    }
}
