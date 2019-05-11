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

import android.content.ContentResolver;
import android.net.Uri;
import android.os.Parcelable;

import androidx.annotation.NonNull;

import java.util.Map;

//
// ImageList and Image classes have one-to-one correspondence.
// The class hierarchy (* = abstract class):
//
//    IImageList
//    - BaseImageList (*)
//      - VideoList
//      - ImageList
//        - DrmImageList
//      - SingleImageList (contains UriImage)
//    - ImageListUber
//
//    IImage
//    - BaseImage (*)
//      - VideoObject
//      - Image
//        - DrmImage
//    - UriImage
//

/**
 * The interface of all image collections used in gallery.
 */
interface CropIImageList
        extends Parcelable, AutoCloseable {

    @NonNull
    Map<String, String> getBucketIds();

    void deactivate();

    /**
     * Returns the count of image objects.
     *
     * @return the number of images
     */
    int getCount();

    /**
     * @return {@code true} if the count of image objects is zero.
     */
    boolean isEmpty();

    /**
     * Returns the image at the ith position.
     *
     * @param index the position
     *
     * @return the image at the ith position
     */
    @NonNull
    CropIImage getImageAt(int index);

    /**
     * Returns the image with a particular Uri.
     *
     * @return the image, or {@code null} if not found.
     */
    @NonNull
    CropIImage getImageForUri(@NonNull Uri uri);

    /**
     * @return {@code true} if the image was removed.
     */
    boolean removeImage(@NonNull CropIImage image);

    /**
     * Removes the image at the ith position.
     *
     * @param index the position
     */
    boolean removeImageAt(int index);

    int getImageIndex(@NonNull CropIImage image);

    /**
     * Generate thumbnail for the image if it has not been generated.
     *
     * @param index the position of the image
     */
    void checkThumbnail(int index);

    /**
     * Opens this list for operation.
     */
    void open(@NonNull ContentResolver resolver);

    /**
     * Closes this list to release resources, no further operation is allowed.
     */
    void close();
}
