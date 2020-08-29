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
package com.hardbacknutter.nevertoomanybooks.backup;

import android.content.Context;
import android.net.Uri;
import android.os.Parcel;
import android.os.Parcelable;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.io.IOException;
import java.util.Objects;

import com.hardbacknutter.nevertoomanybooks.BuildConfig;
import com.hardbacknutter.nevertoomanybooks.R;
import com.hardbacknutter.nevertoomanybooks.backup.base.ArchiveImportTask;
import com.hardbacknutter.nevertoomanybooks.backup.base.ArchiveInfo;
import com.hardbacknutter.nevertoomanybooks.backup.base.ArchiveReader;
import com.hardbacknutter.nevertoomanybooks.backup.base.ImportException;
import com.hardbacknutter.nevertoomanybooks.backup.base.ImportResults;
import com.hardbacknutter.nevertoomanybooks.backup.base.InvalidArchiveException;
import com.hardbacknutter.nevertoomanybooks.backup.base.Options;
import com.hardbacknutter.nevertoomanybooks.backup.csv.CsvArchiveReader;
import com.hardbacknutter.nevertoomanybooks.backup.db.DbArchiveReader;
import com.hardbacknutter.nevertoomanybooks.backup.tar.TarArchiveReader;
import com.hardbacknutter.nevertoomanybooks.backup.zip.ZipArchiveReader;
import com.hardbacknutter.nevertoomanybooks.debug.ErrorMsg;
import com.hardbacknutter.nevertoomanybooks.dialogs.StandardDialogs;

public class ImportManager
        implements Parcelable {

    /** {@link Parcelable}. */
    public static final Creator<ImportManager> CREATOR = new Creator<ImportManager>() {
        @Override
        public ImportManager createFromParcel(@NonNull final Parcel source) {
            return new ImportManager(source);
        }

        @Override
        public ImportManager[] newArray(final int size) {
            return new ImportManager[size];
        }
    };

    /**
     * 0: all books
     * 1: only new books and books with more recent update_date fields should be imported.
     */
    public static final int IMPORT_ONLY_NEW_OR_UPDATED = 1 << 16;

    /**
     * all defined flags.
     */
    private static final int MASK = Options.ENTITIES | IMPORT_ONLY_NEW_OR_UPDATED;
    @NonNull
    private final Uri mUri;
    /**
     * Bitmask.
     * Contains the user selected options before doing the import/export.
     * After the import/export, reflects the entities actually imported/exported.
     */
    private int mOptions;
    @Nullable
    private ImportResults mResults;
    @Nullable
    private ArchiveContainer mArchiveContainer;

    @Nullable
    private ArchiveInfo mArchiveInfo;

    /**
     * Constructor.
     *
     * @param options to import
     * @param uri     to read from
     */
    public ImportManager(final int options,
                         @NonNull final Uri uri) {
        mOptions = options;
        mUri = uri;
    }

    /**
     * {@link Parcelable} Constructor.
     *
     * @param in Parcel to construct the object from
     */
    private ImportManager(@NonNull final Parcel in) {
        mOptions = in.readInt();
        //noinspection ConstantConditions
        mUri = in.readParcelable(getClass().getClassLoader());
        mResults = in.readParcelable(getClass().getClassLoader());
    }

    public static String createErrorReport(@NonNull final Context context,
                                           @Nullable final Exception e) {
        String msg = null;

        if (e instanceof InvalidArchiveException) {
            msg = context.getString(R.string.error_import_invalid_archive);

        } else if (e instanceof ImportException) {
            msg = e.getLocalizedMessage();

        } else if (e instanceof IOException) {
            //ENHANCE: if (message.exception.getCause() instanceof ErrnoException) {
            //           int errno = ((ErrnoException) message.exception.getCause()).errno;
            msg = StandardDialogs.createBadError(context, R.string.error_storage_not_readable);
        }

        // generic unknown message
        if (msg == null || msg.isEmpty()) {
            msg = context.getString(R.string.error_unexpected_error);
        }

        return msg;
    }

    /** Called from the dialog via its View listeners. */
    void setOption(final int optionBit,
                   final boolean isSet) {
        if (isSet) {
            mOptions |= optionBit;
        } else {
            mOptions &= ~optionBit;
        }
    }

    public int getOptions() {
        return mOptions;
    }

    /** Called <strong>after</strong> the export/import to report back what was handled. */
    public void setOptions(final int options) {
        mOptions = options;
    }

    /**
     * Are there any options set that will cause us to import anything?
     *
     * @return {@code true} if something will be imported
     */
    boolean hasEntityOption() {
        return (mOptions & Options.ENTITIES) != 0;
    }

    /**
     * Check if we have an {@link ArchiveReader} available that can read the passed Uri.
     *
     * @return {@code true} if supported
     */
    @SuppressWarnings("BooleanMethodIsAlwaysInverted")
    public boolean isSupported(@NonNull final ArchiveContainer type) {

        switch (type) {
            case CsvBooks:
            case Zip:
            case Tar:
                return true;

            case SqLiteDb:
                return BuildConfig.IMPORT_CALIBRE;

            case Xml:
            case Unknown:
            default:
                return false;
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

    @NonNull
    public ArchiveContainer getContainer(@NonNull final Context context) {
        if (mArchiveContainer == null) {
            mArchiveContainer = ArchiveContainer.create(context, mUri);
        }
        return mArchiveContainer;
    }

    /**
     * Get the {@link ArchiveInfo}.
     * <p>
     * This allows us to show the info contained to the user without starting an actual import.
     *
     * @param context Current context
     *
     * @return the info bundle, or {@code null} if the archive does not provide info
     *
     * @throws InvalidArchiveException on failure to recognise a supported archive
     * @throws IOException             on other failures
     */
    @Nullable
    ArchiveInfo getInfo(@NonNull final Context context)
            throws InvalidArchiveException, IOException {
        if (mArchiveInfo == null) {
            try (ArchiveReader reader = getArchiveReader(context)) {
                mArchiveInfo = reader.readArchiveInfo(context);
            }
        }
        return mArchiveInfo;
    }

    /**
     * Create an {@link ArchiveReader} for the specified Uri.
     *
     * @param context Current context
     *
     * @return a new reader
     *
     * @throws InvalidArchiveException on failure to recognise a supported archive
     * @throws IOException             on other failures
     */
    @NonNull
    public ArchiveReader getArchiveReader(@NonNull final Context context)
            throws InvalidArchiveException, IOException {

        final ArchiveReader reader;
        switch (getContainer(context)) {
            case Zip:
                reader = new ZipArchiveReader(context, this);
                break;

            case Tar:
                reader = new TarArchiveReader(context, this);
                break;

            case CsvBooks:
                reader = new CsvArchiveReader(this);
                break;

            case SqLiteDb:
                if (BuildConfig.IMPORT_CALIBRE) {
                    reader = new DbArchiveReader(context, this);
                } else {
                    throw new InvalidArchiveException(ErrorMsg.IMPORT_NOT_SUPPORTED);
                }
                break;

            case Xml:
            case Unknown:
            default:
                throw new InvalidArchiveException(ErrorMsg.IMPORT_NOT_SUPPORTED);
        }

        reader.validate(context);
        return reader;
    }

    @NonNull
    public ImportResults getResults() {
        Objects.requireNonNull(mResults);
        return mResults;
    }

    public void setResults(@NonNull final ImportResults results) {
        mResults = results;
    }

    /**
     * Will be called by {@link ArchiveImportTask}.
     */
    public void validate() {
        if ((mOptions & MASK) == 0) {
            throw new IllegalStateException(ErrorMsg.OPTIONS_NOT_SET);
        }
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(@NonNull final Parcel dest,
                              final int flags) {
        dest.writeInt(mOptions);
        dest.writeParcelable(mUri, flags);
        dest.writeParcelable(mResults, flags);
    }

    @Override
    @NonNull
    public String toString() {
        return "ImportHelper{"
               + ", mOptions=0b" + Integer.toBinaryString(mOptions)
               + ", mUri=" + mUri
               + ", mArchiveType=" + mArchiveContainer
               + ", mArchiveInfo=" + mArchiveInfo
               + ", mResults=" + mResults
               + '}';
    }

    public boolean isSet(final int optionBit) {
        return (mOptions & optionBit) != 0;
    }
}
