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
import androidx.annotation.VisibleForTesting;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.EnumSet;

import com.hardbacknutter.nevertoomanybooks.R;
import com.hardbacknutter.nevertoomanybooks.io.ArchiveEncoding;
import com.hardbacknutter.nevertoomanybooks.io.ArchiveMetaData;
import com.hardbacknutter.nevertoomanybooks.io.DataReader;
import com.hardbacknutter.nevertoomanybooks.io.DataReaderException;
import com.hardbacknutter.nevertoomanybooks.io.DataReaderHelperBase;
import com.hardbacknutter.nevertoomanybooks.io.RecordType;
import com.hardbacknutter.nevertoomanybooks.utils.UriInfo;
import com.hardbacknutter.nevertoomanybooks.utils.exceptions.CoverStorageException;

/**
 * The glue between the ViewModel and the underlying business logic to perform an import.
 */
public final class ImportHelper
        extends DataReaderHelperBase<ArchiveMetaData, ImportResults> {

    /** <strong>Where</strong> we read from. */
    @NonNull
    private final Uri mUri;
    /** <strong>How</strong> we read from the uri. */
    @NonNull
    private final ArchiveEncoding mEncoding;

    @NonNull
    private final UriInfo mUriInfo;

    /**
     * Constructor. The encoding will be determined from the Uri.
     *
     * @param context Current context
     * @param uri     to read from
     *
     * @throws DataReaderException on failure to recognise a supported archive
     */
    @VisibleForTesting(otherwise = VisibleForTesting.PACKAGE_PRIVATE)
    public ImportHelper(@NonNull final Context context,
                        @NonNull final Uri uri)
            throws FileNotFoundException, DataReaderException {

        mUri = uri;
        mUriInfo = new UriInfo(mUri);
        mEncoding = ArchiveEncoding.getEncoding(context, uri).orElseThrow(
                () -> new DataReaderException(context.getString(
                        R.string.error_import_file_not_supported)));

        // set the defaults according to the encoding
        switch (mEncoding) {
            case Csv:
                addRecordType(RecordType.Books);
                setUpdateOption(DataReader.Updates.OnlyNewer);
                break;

            case Zip:
            case Tar:
                addRecordType(EnumSet.of(RecordType.Styles,
                                         RecordType.Preferences,
                                         RecordType.Certificates,
                                         RecordType.Books,
                                         RecordType.Cover));
                setUpdateOption(DataReader.Updates.OnlyNewer);
                break;

            case SqLiteDb:
                addRecordType(RecordType.Books);
                setUpdateOption(DataReader.Updates.Skip);
                break;

            case Json:
                addRecordType(EnumSet.of(RecordType.Styles,
                                         RecordType.Preferences,
                                         RecordType.Certificates,
                                         RecordType.Books));
                setUpdateOption(DataReader.Updates.OnlyNewer);
                break;

            case Xml:
                // not supported
            default:
                break;
        }
    }

    /**
     * Get the location to read from.
     */
    @NonNull
    public ArchiveEncoding getEncoding() {
        return mEncoding;
    }

    /**
     * Get the {@link Uri} to read from.
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
        return mUriInfo;
    }

    @NonNull
    protected DataReader<ArchiveMetaData, ImportResults> createReader(
            @NonNull final Context context)
            throws IOException, CoverStorageException,
                   DataReaderException {
        return mEncoding.createReader(context, this);
    }

    @Override
    @NonNull
    public String toString() {
        return "ImportHelper{"
               + super.toString()
               + ", mUri=" + mUri
               + ", mEncoding=" + mEncoding
               + ", mUriInfo=" + mUriInfo
               + '}';
    }
}
