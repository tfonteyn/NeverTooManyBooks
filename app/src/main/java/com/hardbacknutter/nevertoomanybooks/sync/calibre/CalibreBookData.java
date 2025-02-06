/*
 * @Copyright 2018-2025 HardBackNutter
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
    public static final String BKEY = TAG + ":data";

    /**
     * FK to {@link DBDefinitions#TBL_CALIBRE_LIBRARIES}.
     */
    @IntRange(from = 1)
    private final long libraryId;
    @Nullable
    private CalibreLibrary library;

    @IntRange(from = 1)
    private final long calibreBookId;
    @NonNull
    private final String calibreBookUuid;
    @Nullable
    private String fileFormat;

    public CalibreBookData(@IntRange(from = 1) final long libraryId,
                           @IntRange(from = 1) final long calibreBookId,
                           @NonNull final String calibreBookUuid,
                           @Nullable final String fileFormat) {
        this.libraryId = libraryId;
        this.calibreBookId = calibreBookId;
        this.calibreBookUuid = calibreBookUuid;
        this.fileFormat = fileFormat;
    }

    public CalibreBookData(@NonNull final DataHolder rowData) {
        libraryId = rowData.getLong(DBKey.FK_CALIBRE_LIBRARY);
        calibreBookId = rowData.getLong(DBKey.CALIBRE.BOOK_ID);
        calibreBookUuid = rowData.getString(DBKey.CALIBRE.BOOK_UUID);
        fileFormat = rowData.getString(DBKey.CALIBRE.BOOK_MAIN_FORMAT);
    }

    private CalibreBookData(@NonNull final Parcel in) {
        libraryId = in.readLong();
        library = in.readParcelable(getClass().getClassLoader());
        calibreBookId = in.readLong();
        //noinspection DataFlowIssue
        calibreBookUuid = in.readString();
        fileFormat = in.readString();
    }

    @Override
    public void writeToParcel(@NonNull final Parcel dest,
                              final int flags) {
        dest.writeLong(libraryId);
        dest.writeParcelable(library, flags);
        dest.writeLong(calibreBookId);
        dest.writeString(calibreBookUuid);
        dest.writeString(fileFormat);
    }

    @Override
    public int describeContents() {
        return 0;
    }

    public long getLibraryId() {
        return libraryId;
    }

    public long getCalibreBookId() {
        return calibreBookId;
    }

    @NonNull
    public String getCalibreBookUuid() {
        return calibreBookUuid;
    }

    //URGENT/TEST: can we have a calibre book WITHOUT a file...
    @Nullable
    public String getFileFormat() {
        return fileFormat;
    }

    public void setFileFormat(@Nullable final String fileFormat) {
        this.fileFormat = fileFormat;
    }

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

        if (library != null) {
            return Optional.of(library);
        }

        // we should never get here... flw
        return Optional.empty();
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
