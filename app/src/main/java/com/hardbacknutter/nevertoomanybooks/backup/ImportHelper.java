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
package com.hardbacknutter.nevertoomanybooks.backup;

import android.content.Context;
import android.net.Uri;

import androidx.annotation.NonNull;
import androidx.annotation.VisibleForTesting;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.EnumSet;
import java.util.Locale;

import com.hardbacknutter.nevertoomanybooks.R;
import com.hardbacknutter.nevertoomanybooks.core.network.CredentialsException;
import com.hardbacknutter.nevertoomanybooks.core.storage.StorageException;
import com.hardbacknutter.nevertoomanybooks.io.ArchiveEncoding;
import com.hardbacknutter.nevertoomanybooks.io.ArchiveMetaData;
import com.hardbacknutter.nevertoomanybooks.io.DataReader;
import com.hardbacknutter.nevertoomanybooks.io.DataReaderException;
import com.hardbacknutter.nevertoomanybooks.io.DataReaderHelperBase;
import com.hardbacknutter.nevertoomanybooks.io.RecordType;
import com.hardbacknutter.nevertoomanybooks.utils.UriInfo;

/**
 * The glue between the ViewModel and the underlying business logic to perform an import.
 */
public final class ImportHelper
        extends DataReaderHelperBase<ArchiveMetaData, ImportResults> {

    /** <strong>Where</strong> we read from. */
    @NonNull
    private final Uri uri;
    /** <strong>How</strong> we read from the uri. */
    @NonNull
    private final ArchiveEncoding encoding;

    @NonNull
    private final UriInfo uriInfo;
    @SuppressWarnings("FieldNotUsedInToString")
    @NonNull
    private final Locale systemLocale;

    /**
     * Constructor. The encoding will be determined from the Uri.
     *
     * @param context      Current context
     * @param systemLocale to use for ISO date parsing
     * @param uri          to read from
     *
     * @throws DataReaderException   on failure to recognise a supported archive
     * @throws FileNotFoundException on...
     */
    @VisibleForTesting(otherwise = VisibleForTesting.PACKAGE_PRIVATE)
    public ImportHelper(@NonNull final Context context,
                        @NonNull final Locale systemLocale,
                        @NonNull final Uri uri)
            throws FileNotFoundException, DataReaderException {

        this.uri = uri;
        uriInfo = new UriInfo(this.uri);

        this.systemLocale = systemLocale;

        encoding = ArchiveEncoding.getEncoding(context, uri).orElseThrow(
                () -> new DataReaderException(context.getString(
                        R.string.error_import_file_not_supported)));

        // set the defaults according to the encoding
        switch (encoding) {
            case Csv:
                addRecordType(RecordType.Books);
                setUpdateOption(DataReader.Updates.OnlyNewer);
                break;

            case Zip:
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

            default:
                break;
        }
    }

    /**
     * Get the type of archive (file) to read from.
     *
     * @return encoding
     */
    @NonNull
    public ArchiveEncoding getEncoding() {
        return encoding;
    }

    /**
     * Get the {@link Uri} to read from.
     *
     * @return Uri
     */
    @NonNull
    public Uri getUri() {
        return uri;
    }

    /**
     * Get the {@link UriInfo} of the archive (based on the Uri/Encoding).
     *
     * @return info
     */
    @NonNull
    public UriInfo getUriInfo() {
        return uriInfo;
    }

    @NonNull
    protected DataReader<ArchiveMetaData, ImportResults> createReader(
            @NonNull final Context context)
            throws DataReaderException,
                   CredentialsException,
                   StorageException,
                   IOException {
        return encoding.createReader(context, systemLocale, this);
    }

    @Override
    @NonNull
    public String toString() {
        return "ImportHelper{"
               + super.toString()
               + ", uri=" + uri
               + ", encoding=" + encoding
               + ", uriInfo=" + uriInfo
               + '}';
    }
}
