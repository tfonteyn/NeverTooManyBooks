/*
 * Copyright (C) 2009 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.eleybourn.bookcatalogue.cropper;

import android.graphics.Bitmap;
import android.net.Uri;

import androidx.annotation.NonNull;

import java.io.InputStream;

import com.eleybourn.bookcatalogue.entities.Entity;

/**
 * The interface of all images used in gallery.
 */
public interface CropIImage extends Entity {

    int UNCONSTRAINED = -1;

    /** Get the image list which contains this image. */
    @NonNull
    CropIImageList getContainer();

    /** Get the bitmap for the full size image. */
    @NonNull
    Bitmap fullSizeBitmap(int minSideLength,
                          int maxNumberOfPixels);

    @NonNull
    Bitmap fullSizeBitmap(int minSideLength,
                          int maxNumberOfPixels,
                          boolean rotateAsNeeded);

    @NonNull
    Bitmap fullSizeBitmap(int minSideLength,
                          int maxNumberOfPixels,
                          boolean rotateAsNeeded,
                          boolean useNative);

    int getDegreesRotated();

    /** Get the input stream associated with a given full size image. */
    @NonNull
    InputStream fullSizeImageData();

    long fullSizeImageId();

    @NonNull
    Uri fullSizeImageUri();

    /** Get the path of the (full size) image data. */
    @NonNull
    String getDataPath();

    @NonNull
    String getTitle();

    /** Get/Set the title of the image. */
    void setTitle(@NonNull String name);

    /** Get metadata of the image. */
    long getDateTaken();

    @NonNull
    String getMimeType();

    int getWidth();

    int getHeight();

    @NonNull
    String getLabel();

    /** Get property of the image. */
    boolean isReadonly();

    boolean isDrm();

    /** Get the bitmap/uri of the medium thumbnail. */
    @NonNull
    Bitmap thumbBitmap(boolean rotateAsNeeded);

    @NonNull
    Uri thumbUri();

    /** Get the bitmap of the mini thumbnail. */
    @NonNull
    Bitmap miniThumbBitmap();

    /** Rotate the image. */
    boolean rotateImageBy(int degrees);

}