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
package com.hardbacknutter.nevertoomanybooks.covers;

import android.os.Parcel;
import android.os.Parcelable;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.io.File;

import com.hardbacknutter.nevertoomanybooks.searches.SearchEngine;
import com.hardbacknutter.nevertoomanybooks.searches.SearchSites;

/**
 * Value class containing info about a cover file.
 */
public class ImageFileInfo
        implements Parcelable {

    /** {@link Parcelable}. */
    public static final Creator<ImageFileInfo> CREATOR = new Creator<ImageFileInfo>() {
        @Override
        public ImageFileInfo createFromParcel(@NonNull final Parcel in) {
            return new ImageFileInfo(in);
        }

        @Override
        public ImageFileInfo[] newArray(final int size) {
            return new ImageFileInfo[size];
        }
    };

    @NonNull
    public final String isbn;
    @Nullable
    public final Size size;
    @Nullable
    public final String fileSpec;

    @SearchSites.EngineId
    public final int engineId;

    /**
     * Constructor. No file.
     */
    ImageFileInfo(@NonNull final String isbn) {
        this.isbn = isbn;
        this.fileSpec = null;
        this.size = null;
        this.engineId = 0;
    }

    /**
     * Constructor.
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

        int sizeOrdinal = in.readInt();
        if (sizeOrdinal >= 0) {
            size = Size.values()[sizeOrdinal];
        } else {
            size = null;
        }
    }

    @Override
    public void writeToParcel(@NonNull final Parcel dest,
                              final int flags) {
        dest.writeString(isbn);
        dest.writeString(fileSpec);
        dest.writeInt(engineId);

        dest.writeInt(size != null ? size.ordinal() : -1);
    }

    @Nullable
    public File getFile() {
        if (fileSpec != null && !fileSpec.isEmpty()) {
            return new File(fileSpec);
        }
        return null;
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
               + ", fileSpec=`" + (fileSpec == null ? "" :
                                   fileSpec.substring(fileSpec.lastIndexOf('/'))) + '`'
               + '}';
    }

    /**
     * Sizes of images downloaded by {@link SearchEngine} implementations.
     * These are open to interpretation (or not used at all) by individual sites.
     * <p>
     * The order must be from Small to Large so we can use {@link Enum#compareTo(Enum)}.
     */
    public enum Size {
        Small,
        Medium,
        Large;

        public static final Size[] SMALL_FIRST = {Small, Medium, Large};
        public static final Size[] LARGE_FIRST = {Large, Medium, Small};
    }
}
