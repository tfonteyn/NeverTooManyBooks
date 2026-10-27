/*
 * @Copyright 2018-2026 HardBackNutter
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

package com.hardbacknutter.nevertoomanybooks.sync.calibre;

import android.os.Parcel;
import android.os.Parcelable;

import androidx.annotation.IntRange;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.Objects;
import java.util.Optional;

import com.hardbacknutter.nevertoomanybooks.ServiceLocator;
import com.hardbacknutter.nevertoomanybooks.database.DBDefinitions;
import com.hardbacknutter.nevertoomanybooks.database.DBKey;
import com.hardbacknutter.nevertoomanybooks.entities.DataHolder;

public class CalibreBookData
        implements Parcelable {

    /** {@link Parcelable}. */
    public static final Creator<CalibreBookData> CREATOR = new Creator<>() {
        @Override
        @NonNull
        public CalibreBookData createFromParcel(@NonNull final Parcel in) {
            return new CalibreBookData(in);
        }

        @Override
        @NonNull
        public CalibreBookData[] newArray(final int size) {
            return new CalibreBookData[size];
        }
    };

    private static final String TAG = "CalibreBookData";
    /** Bundle key to pass this object around. */
    public static final String BKEY = TAG + ":data";

    /**
     * FK to {@link DBDefinitions#TBL_CALIBRE_LIBRARIES}.
     */
    @IntRange(from = 1)
    private final long libraryId;

    /**
     * The remote Calibre book id is an {@code int}.
     * Instead of our usual {@code long} usage for id's we stick to {@code int}
     * for strict consistency with the Calibre server.
     */
    @IntRange(from = 1)
    private final int calibreBookId;

    @NonNull
    private final String calibreBookUuid;
    @Nullable
    private CalibreLibrary library;
    @Nullable
    private String fileFormat;

    /**
     * Constructor.
     *
     * @param libraryId       the raw Calibre library id
     * @param calibreBookId   numeric Calibre book id
     * @param calibreBookUuid Calibre book UUID
     * @param fileFormat      (optional) the main file format
     */
    public CalibreBookData(@IntRange(from = 1) final long libraryId,
                           @IntRange(from = 1) final int calibreBookId,
                           @NonNull final String calibreBookUuid,
                           @Nullable final String fileFormat) {
        this.libraryId = libraryId;
        this.calibreBookId = calibreBookId;
        this.calibreBookUuid = calibreBookUuid;
        this.fileFormat = fileFormat;
    }

    /**
     * Constructor.
     *
     * @param rowData with the data
     */
    public CalibreBookData(@NonNull final DataHolder rowData) {
        libraryId = rowData.getLong(DBKey.FK_CALIBRE_LIBRARY);
        calibreBookId = rowData.getInt(DBKey.CALIBRE.BOOK_ID);
        calibreBookUuid = rowData.getString(DBKey.CALIBRE.BOOK_UUID);
        fileFormat = rowData.getString(DBKey.CALIBRE.BOOK_MAIN_FORMAT, null);
    }

    private CalibreBookData(@NonNull final Parcel in) {
        libraryId = in.readLong();
        //noinspection deprecation
        library = in.readParcelable(getClass().getClassLoader());
        calibreBookId = in.readInt();
        //noinspection DataFlowIssue
        calibreBookUuid = in.readString();
        fileFormat = in.readString();
    }

    @Override
    public void writeToParcel(@NonNull final Parcel dest,
                              final int flags) {
        dest.writeLong(libraryId);
        dest.writeParcelable(library, flags);
        dest.writeInt(calibreBookId);
        dest.writeString(calibreBookUuid);
        dest.writeString(fileFormat);
    }

    @Override
    public int describeContents() {
        return 0;
    }

    /**
     * Get the unresolved library id.
     *
     * @return id
     *
     * @see #getCalibreLibrary()
     */
    @IntRange(from = 1)
    public long getLibraryId() {
        return libraryId;
    }

    /**
     * The Calibre numerical book id.
     *
     * @return id
     */
    @IntRange(from = 1)
    public int getCalibreBookId() {
        return calibreBookId;
    }

    /**
     * The Calibre book UUID string.
     *
     * @return UUID
     */
    @NonNull
    public String getCalibreBookUuid() {
        return calibreBookUuid;
    }

    /**
     * The file-format field is considered optional.
     *
     * @return file format (the extension); can be {@code null}.
     */
    @Nullable
    public String getFileFormat() {
        return fileFormat;
    }

    public void setFileFormat(@Nullable final String fileFormat) {
        this.fileFormat = fileFormat;
    }

    /**
     * Get the resolved {@link CalibreLibrary}.
     *
     * @return library
     */
    @NonNull
    public Optional<CalibreLibrary> getCalibreLibrary() {
        if (library != null) {
            // resolved earlier.
            return Optional.of(library);
        }

        library = ServiceLocator
                .getInstance()
                .getCalibreLibraryDao()
                .findById(libraryId)
                // Paranoia: we should never get here
                .orElse(null);

        return Optional.ofNullable(library);
    }

    /**
     * Equality does <strong>not</strong> include the potentially cached {@link #library}.
     */
    @Override
    public boolean equals(@Nullable final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final CalibreBookData that = (CalibreBookData) o;
        return libraryId == that.libraryId
               && calibreBookId == that.calibreBookId
               && Objects.equals(calibreBookUuid, that.calibreBookUuid)
               && Objects.equals(fileFormat, that.fileFormat);
    }

    @Override
    public int hashCode() {
        return Objects.hash(libraryId, calibreBookId, calibreBookUuid, fileFormat);
    }

    @Override
    @NonNull
    public String toString() {
        return "CalibreBookData{"
               + "libraryId=" + libraryId
               + ", calibreBookId=" + calibreBookId
               + ", calibreBookUuid='" + calibreBookUuid + '\''
               + ", fileFormat='" + fileFormat + '\''
               + ", library=" + library
               + '}';
    }
}
