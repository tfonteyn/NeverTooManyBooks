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
package com.hardbacknutter.nevertoomanybooks.covers;

import android.os.Parcel;
import android.os.Parcelable;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.io.File;
import java.util.Optional;

import com.hardbacknutter.nevertoomanybooks.BuildConfig;
import com.hardbacknutter.nevertoomanybooks.DEBUG_SWITCHES;
import com.hardbacknutter.nevertoomanybooks.searchengines.SearchSites;

/**
 * Info about a cover file.
 */
public class ImageFileInfo
        implements Parcelable {

    /** {@link Parcelable}. */
    public static final Creator<ImageFileInfo> CREATOR = new Creator<>() {
        @Override
        public ImageFileInfo createFromParcel(@NonNull final Parcel in) {
            return new ImageFileInfo(in);
        }

        @Override
        public ImageFileInfo[] newArray(final int size) {
            return new ImageFileInfo[size];
        }
    };
    private static final String TAG = "ImageFileInfo";
    @NonNull
    private final String isbn;
    @Nullable
    private final Size size;
    @Nullable
    private final String fileSpec;
    @SearchSites.EngineId
    private final int engineId;

    /**
     * Constructor. No file.
     *
     * @param isbn of the book for this cover
     */
    ImageFileInfo(@NonNull final String isbn) {
        this.isbn = isbn;
        fileSpec = null;
        size = null;
        engineId = 0;
    }

    /**
     * Constructor.
     *
     * @param isbn     of the book for this cover
     * @param fileSpec (optional) of the cover file
     * @param size     (optional) size
     * @param engineId the search engine id
     */
    ImageFileInfo(@NonNull final String isbn,
                  @Nullable final String fileSpec,
                  @Nullable final Size size,
                  @SearchSites.EngineId final int engineId) {
        this.isbn = isbn;
        this.fileSpec = fileSpec;
        this.size = size;
        this.engineId = engineId;
    }

    /**
     * {@link Parcelable} Constructor.
     *
     * @param in Parcel to construct the object from
     */
    private ImageFileInfo(@NonNull final Parcel in) {
        //noinspection ConstantConditions
        isbn = in.readString();
        fileSpec = in.readString();
        engineId = in.readInt();
        size = in.readParcelable(getClass().getClassLoader());
    }

    @Override
    public void writeToParcel(@NonNull final Parcel dest,
                              final int flags) {
        dest.writeString(isbn);
        dest.writeString(fileSpec);
        dest.writeInt(engineId);
        dest.writeParcelable(size, flags);
    }

    @NonNull
    public String getIsbn() {
        return isbn;
    }

    @Nullable
    public Size getSize() {
        return size;
    }

    @SearchSites.EngineId
    int getEngineId() {
        return engineId;
    }

    @NonNull
    public Optional<File> getFile() {
        if (fileSpec != null && !fileSpec.isEmpty()) {
            final File file = new File(fileSpec);
            if (file.exists()) {
                return Optional.of(file);
            }
        }
        return Optional.empty();
    }

    /**
     * Check if this image is either bigger or equal to the given size,
     * or if we already established the image does not exist.
     *
     * @param size to compare to
     */
    boolean isUseThisImage(@NonNull final Size size) {
        // Does it have an actual file ?
        if (fileSpec != null) {
            // There is a file and it is good (as determined at download time)
            // But is the size we have suitable ? Bigger files are always better (we hope)...
            if (this.size != null && this.size.compareTo(size) >= 0) {
                // YES, use the file we already have
                if (BuildConfig.DEBUG && DEBUG_SWITCHES.COVERS) {
                    Log.d(TAG, "search|PRESENT|SUCCESS|imageFileInfo=" + this);
                }
                return true;
            }

            // else drop through and search for it.
            if (BuildConfig.DEBUG && DEBUG_SWITCHES.COVERS) {
                Log.d(TAG, "search|PRESENT|TO SMALL|imageFileInfo=" + this);
            }
            return false;

        } else {
            // a previous search failed, there simply is NO file
            if (BuildConfig.DEBUG && DEBUG_SWITCHES.COVERS) {
                Log.d(TAG, "search|PRESENT|NO FILE|imageFileInfo=" + this);
            }
            return true;
        }
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @NonNull
    @Override
    public String toString() {
        return "ImageFileInfo{"
               + "isbn=`" + isbn + '`'
               + ", size=" + size
               + ", engineId=" + engineId
               + ", fileSpec=`"
               + (fileSpec == null ? "" : fileSpec.substring(fileSpec.lastIndexOf('/')))
               + '`'
               + '}';
    }
}
