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

import android.content.Context;
import android.net.Uri;
import android.os.Parcel;
import android.os.Parcelable;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.Objects;

import com.hardbacknutter.nevertoomanybooks.backup.csv.CsvArchiveReader;
import com.hardbacknutter.nevertoomanybooks.backup.db.DbArchiveReader;
import com.hardbacknutter.nevertoomanybooks.backup.tar.TarArchiveReader;
import com.hardbacknutter.nevertoomanybooks.backup.zip.ZipArchiveReader;
import com.hardbacknutter.nevertoomanybooks.debug.SanityCheck;

public class ImportHelper
        implements Parcelable {

    /** {@link Parcelable}. */
    public static final Creator<ImportHelper> CREATOR = new Creator<ImportHelper>() {
        @Override
        public ImportHelper createFromParcel(@NonNull final Parcel source) {
            return new ImportHelper(source);
        }

        @Override
        public ImportHelper[] newArray(final int size) {
            return new ImportHelper[size];
        }
    };

    /**
     * all defined flags.
     */
    private static final int MASK = Options.ENTITIES | Options.IS_SYNC;
    @NonNull
    private final Uri mUri;
    @Nullable
    private ArchiveContainer mArchiveContainer;
    @Nullable
    private ArchiveInfo mArchiveInfo;
    /**
     * Bitmask.
     * Contains the user selected options before doing the import/export.
     * After the import/export, reflects the entities actually imported/exported.
     */
    @Options.Bits
    private int mOptions;
    @Nullable
    private ImportResults mResults;

    /**
     * Constructor.
     *
     * @param uri to read from
     */
    public ImportHelper(@NonNull final Uri uri) {
        mUri = uri;
    }

    /**
     * {@link Parcelable} Constructor.
     *
     * @param in Parcel to construct the object from
     */
    private ImportHelper(@NonNull final Parcel in) {
        mOptions = in.readInt();
        //noinspection ConstantConditions
        mUri = in.readParcelable(getClass().getClassLoader());
        mResults = in.readParcelable(getClass().getClassLoader());
    }


    /**
     * Check if we have an {@link ArchiveReader} available that can read the passed Uri.
     *
     * @param context Current context
     *
     * @return {@code true} if supported
     */
    @SuppressWarnings("BooleanMethodIsAlwaysInverted")
    public boolean isSupported(@NonNull final Context context) {
        switch (getContainer(context)) {
            case CsvBooks:
            case Zip:
            case Tar:
            case SqLiteDb:
                return true;

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

    /**
     * Get the archive container as read from the uri.
     *
     * @param context Current context
     *
     * @return container
     */
    @NonNull
    public ArchiveContainer getContainer(@NonNull final Context context) {
        if (mArchiveContainer == null) {
            mArchiveContainer = ArchiveContainer.create(context, mUri);
        }
        return mArchiveContainer;
    }

    // TODO: split this up into one check for each entity we could import.
    public boolean isBooksOnlyContainer(@NonNull final Context context) {
        final ArchiveContainer container = getContainer(context);
        return ArchiveContainer.CsvBooks == container
               || ArchiveContainer.SqLiteDb == container;
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
     * Get the archive creation date.
     *
     * @param context Current context
     *
     * @return the date, or {@code null} if none present
     *
     * @throws InvalidArchiveException on failure to recognise a supported archive
     * @throws IOException             on other failures
     */
    @Nullable
    public LocalDateTime getArchiveCreationDate(@NonNull final Context context)
            throws InvalidArchiveException, IOException {

        final ArchiveInfo info = getInfo(context);
        if (info == null) {
            return null;
        } else {
            return info.getCreationDate(context);
        }
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
    ArchiveReader getArchiveReader(@NonNull final Context context)
            throws InvalidArchiveException, IOException {

        // Validate the settings before going ahead.
        SanityCheck.requirePositiveValue(mOptions & MASK, "mOptions");

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
                reader = new DbArchiveReader(context, this);
                break;

            case Xml:
            case Unknown:
            default:
                throw new InvalidArchiveException(ArchiveContainer.IMPORT_NOT_SUPPORTED);
        }

        reader.validate(context);
        return reader;
    }

    @NonNull
    public ImportResults getResults() {
        return Objects.requireNonNull(mResults, "mResults");
    }

    public void setResults(@NonNull final ImportResults results) {
        mResults = results;
    }


    /**
     * Called from the dialog via its View listeners.
     *
     * @param optionBit bit or combination of bits
     * @param isSet     bit value
     */
    public void setOption(@Options.Bits final int optionBit,
                          final boolean isSet) {
        if (isSet) {
            mOptions |= optionBit;
        } else {
            mOptions &= ~optionBit;
        }
    }


    public boolean isOptionSet(@Options.Bits final int optionBit) {
        return (mOptions & optionBit) != 0;
    }

    @Options.Bits
    public int getOptions() {
        return mOptions;
    }

    /**
     * Should be called <strong>before</strong> the import to indicate what should be imported.
     * Should be called <strong>after</strong> the import to set what was actually imported.
     *
     * @param options set
     */
    public void setOptions(@Options.Bits final int options) {
        mOptions = options;
    }

    /**
     * Check if there any options set that will cause us to import anything.
     *
     * @return {@code true} if something will be imported
     */
    public boolean hasEntityOption() {
        return (mOptions & Options.ENTITIES) != 0;
    }


    @Override
    public void writeToParcel(@NonNull final Parcel dest,
                              final int flags) {
        dest.writeInt(mOptions);
        dest.writeParcelable(mUri, flags);
        dest.writeParcelable(mResults, flags);
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    @NonNull
    public String toString() {
        return "ImportHelper{"
               + ", mOptions=0b" + Integer.toBinaryString(mOptions)
               + ", mOptions=" + Options.toString(mOptions)
               + ", mUri=" + mUri
               + ", mArchiveType=" + mArchiveContainer
               + ", mArchiveInfo=" + mArchiveInfo
               + ", mResults=" + mResults
               + '}';
    }
}
