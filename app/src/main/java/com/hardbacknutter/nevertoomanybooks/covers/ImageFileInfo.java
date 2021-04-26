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

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.io.File;

import com.hardbacknutter.nevertoomanybooks.searchengines.SearchEngine;
import com.hardbacknutter.nevertoomanybooks.searchengines.SearchSites;

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
    private final String mIsbn;
    @Nullable
    private final Size mSize;
    @Nullable
    private final String mFileSpec;

    @SearchSites.EngineId
    private final int mEngineId;

    /**
     * Constructor. No file.
     *
     * @param isbn of the book for this cover
     */
    ImageFileInfo(@NonNull final String isbn) {
        mIsbn = isbn;
        mFileSpec = null;
        mSize = null;
        mEngineId = 0;
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
        mIsbn = isbn;
        mFileSpec = fileSpec;
        mSize = size;
        mEngineId = engineId;
    }

    /**
     * {@link Parcelable} Constructor.
     *
     * @param in Parcel to construct the object from
     */
    private ImageFileInfo(@NonNull final Parcel in) {
        //noinspection ConstantConditions
        mIsbn = in.readString();
        mFileSpec = in.readString();
        mEngineId = in.readInt();

        final int sizeOrdinal = in.readInt();
        if (sizeOrdinal >= 0) {
            mSize = Size.values()[sizeOrdinal];
        } else {
            mSize = null;
        }
    }

    @Override
    public void writeToParcel(@NonNull final Parcel dest,
                              final int flags) {
        dest.writeString(mIsbn);
        dest.writeString(mFileSpec);
        dest.writeInt(mEngineId);

        dest.writeInt(mSize != null ? mSize.ordinal() : -1);
    }

    @NonNull
    public String getIsbn() {
        return mIsbn;
    }

    @Nullable
    public Size getSize() {
        return mSize;
    }

    @SearchSites.EngineId
    int getEngineId() {
        return mEngineId;
    }

    boolean hasFileSpec() {
        return mFileSpec != null;
    }

    @Nullable
    public File getFile() {
        if (mFileSpec != null && !mFileSpec.isEmpty()) {
            return new File(mFileSpec);
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
               + "mIsbn=`" + mIsbn + '`'
               + ", mSize=" + mSize
               + ", mEngineId=" + mEngineId
               + ", mFileSpec=`"
               + (mFileSpec == null ? "" : mFileSpec.substring(mFileSpec.lastIndexOf('/')))
               + '`'
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
