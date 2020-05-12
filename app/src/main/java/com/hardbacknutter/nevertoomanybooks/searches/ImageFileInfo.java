/*
 * @Copyright 2020 HardBackNutter
 * @License GNU General Public License
 *
 * This file is part of NeverTooManyBooks.
 *
 * In August 2018, this project was forked from:
 * Book Catalogue 5.2.2 @2016 Philip Warner & Evan Leybourn
 *
 * Without their original creation, this project would not exist in its
 * current form. It was however largely rewritten/refactored and any
 * comments on this fork should be directed at HardBackNutter and not
 * at the original creators.
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
package com.hardbacknutter.nevertoomanybooks.searches;

import android.os.Parcel;
import android.os.Parcelable;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.io.File;

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
    public final ImageSize size;
    @Nullable
    public final String fileSpec;

    /**
     * Constructor. No file.
     */
    public ImageFileInfo(@NonNull final String isbn) {
        this.isbn = isbn;
        this.fileSpec = null;
        this.size = null;
    }

    /**
     * Constructor. File is considered 'Large'.
     */
    public ImageFileInfo(@NonNull final String isbn,
                         @NonNull final String fileSpec) {
        this.isbn = isbn;
        this.fileSpec = fileSpec;
        this.size = ImageSize.Large;
    }

    /**
     * Constructor.
     */
    public ImageFileInfo(@NonNull final String isbn,
                         @NonNull final String fileSpec,
                         @NonNull final ImageSize size) {
        this.isbn = isbn;
        this.fileSpec = fileSpec;
        this.size = size;
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
        int sizeOrdinal = in.readInt();
        if (sizeOrdinal >= 0) {
            size = ImageSize.values()[sizeOrdinal];
        } else {
            size = null;
        }
    }

    @Override
    public void writeToParcel(@NonNull final Parcel dest,
                              final int flags) {
        dest.writeString(isbn);
        dest.writeString(fileSpec);
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
               + ", fileSpec=`" + fileSpec + '`'
               + '}';
    }
}
