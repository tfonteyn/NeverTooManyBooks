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
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;
import androidx.annotation.WorkerThread;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.security.cert.CertificateException;
import java.util.EnumSet;
import java.util.Set;

import javax.net.ssl.SSLException;

import com.hardbacknutter.nevertoomanybooks.BuildConfig;
import com.hardbacknutter.nevertoomanybooks.backup.base.ArchiveEncoding;
import com.hardbacknutter.nevertoomanybooks.backup.base.ArchiveReader;
import com.hardbacknutter.nevertoomanybooks.backup.base.InvalidArchiveException;
import com.hardbacknutter.nevertoomanybooks.utils.UriInfo;
import com.hardbacknutter.nevertoomanybooks.utils.exceptions.ExternalStorageException;

public final class ImportHelper {

    /** <strong>Where</strong> we read from. */
    @NonNull
    private final Uri mUri;
    /** <strong>How</strong> to read from the Uri. */
    @NonNull
    private final ArchiveEncoding mEncoding;
    /** <strong>What</strong> is going to be imported. */
    @NonNull
    private final Set<RecordType> mImportEntries = EnumSet.noneOf(RecordType.class);

    /** Extra arguments for specific readers. The reader must define them. */
    private final Bundle mExtraArgs = new Bundle();

    /**
     * New Books/Covers are always imported
     * (if {@link RecordType#Books} is set obviously).
     */
    private Updates mUpdateOption = Updates.Skip;
    @SuppressWarnings("FieldNotUsedInToString")
    @Nullable
    private UriInfo mUriInfo;

    /**
     * Private constructor. Use the factory methods instead.
     *
     * @param uri      to read from
     * @param encoding which the uri uses
     */
    private ImportHelper(@NonNull final Uri uri,
                         @NonNull final ArchiveEncoding encoding) {
        mUri = uri;
        mEncoding = encoding;
        initWithDefault();
    }

    /**
     * Constructor using a generic Uri. The encoding will be determined from the Uri.
     *
     * @param context Current context
     * @param uri     to read from
     *
     * @throws InvalidArchiveException on failure to recognise a supported archive
     */
    @VisibleForTesting(otherwise = VisibleForTesting.PACKAGE_PRIVATE)
    public static ImportHelper withFile(@NonNull final Context context,
                                        @NonNull final Uri uri)
            throws FileNotFoundException, InvalidArchiveException {
        final ArchiveEncoding encoding = ArchiveEncoding.getEncoding(context, uri).orElseThrow(
                () -> new InvalidArchiveException(uri.toString()));

        return new ImportHelper(uri, encoding);
    }

    /**
     * Constructor for a Remote server.
     *
     * @param uri      for the server.
     * @param encoding i.e. the remote server to use
     */
    static ImportHelper withRemoteServer(@NonNull final Uri uri,
                                         @SuppressWarnings("SameParameterValue")
                                         @NonNull final ArchiveEncoding encoding) {
        if (!encoding.isRemoteServer()) {
            throw new IllegalStateException("Not a remote server");
        }
        return new ImportHelper(uri, encoding);
    }

    private void initWithDefault() {
        mImportEntries.clear();
        mImportEntries.add(RecordType.MetaData);

        switch (mEncoding) {
            case Csv:
                // Default: new books and sync updates
                mImportEntries.add(RecordType.Books);
                setNewAndUpdatedBooks();
                break;

            case Zip:
            case Tar:
                // Default: update all entries and sync updates
                mImportEntries.add(RecordType.Styles);
                mImportEntries.add(RecordType.Preferences);
                mImportEntries.add(RecordType.Certificates);
                mImportEntries.add(RecordType.Books);
                mImportEntries.add(RecordType.Cover);
                setNewAndUpdatedBooks();
                break;

            case SqLiteDb:
                // Default: new books only
                mImportEntries.add(RecordType.Books);
                setNewBooksOnly();
                break;

            case Json:
                mImportEntries.add(RecordType.Styles);
                mImportEntries.add(RecordType.Preferences);
                mImportEntries.add(RecordType.Certificates);
                mImportEntries.add(RecordType.Books);
                setNewAndUpdatedBooks();
                break;

            case CalibreCS:
                mImportEntries.add(RecordType.Books);
                mImportEntries.add(RecordType.Cover);
                setNewAndUpdatedBooks();
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
     * @param context Current context
     *
     * @return info
     */
    @NonNull
    public UriInfo getUriInfo(@NonNull final Context context) {
        if (mUriInfo == null) {
            if (mEncoding.isRemoteServer()) {
                final String displayName =
                        context.getString(mEncoding.getRemoteServerDescriptionResId());
                mUriInfo = new UriInfo(mUri, displayName, 0);

            } else {
                mUriInfo = new UriInfo(mUri);
            }
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
     * @throws CertificateException    on failures related to a user installed CA.
     * @throws SSLException            on secure connection failures
     */
    @NonNull
    @WorkerThread
    public ArchiveReader createArchiveReader(@NonNull final Context context)
            throws InvalidArchiveException,
                   ImportException,
                   IOException,
                   CertificateException,
                   ExternalStorageException {
        if (BuildConfig.DEBUG /* always */) {
            if (mImportEntries.isEmpty()) {
                throw new IllegalStateException("mImportEntries is empty");
            }
        }
        return mEncoding.createReader(context, this);
    }

    @NonNull
    public Bundle getExtraArgs() {
        return mExtraArgs;
    }

    public void setImportEntry(@NonNull final RecordType recordType,
                               final boolean isSet) {
        if (isSet) {
            mImportEntries.add(recordType);
        } else {
            mImportEntries.remove(recordType);
        }
    }

    @NonNull
    public Set<RecordType> getImportEntries() {
        return mImportEntries;
    }

    @SuppressWarnings("WeakerAccess")
    public boolean isNewBooksOnly() {
        return mUpdateOption == Updates.Skip;
    }

    public void setNewBooksOnly() {
        mUpdateOption = Updates.Skip;
    }

    public boolean isAllBooks() {
        return mUpdateOption == Updates.Overwrite;
    }

    public void setAllBooks() {
        mUpdateOption = Updates.Overwrite;
    }

    public boolean isNewAndUpdatedBooks() {
        return mUpdateOption == Updates.OnlyNewer;
    }

    @SuppressWarnings("WeakerAccess")
    public void setNewAndUpdatedBooks() {
        mUpdateOption = Updates.OnlyNewer;
    }

    public Updates getUpdateOption() {
        return mUpdateOption;
    }

    @Override
    @NonNull
    public String toString() {
        return "ImportHelper{"
               + "mUri=" + mUri
               + ", mArchiveEncoding=" + mEncoding
               + ", mImportEntries=" + mImportEntries
               + ", mUpdates=" + mUpdateOption
               + ", mExtraArgs=" + mExtraArgs
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
