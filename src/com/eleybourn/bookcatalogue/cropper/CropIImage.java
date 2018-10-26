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
import android.support.annotation.NonNull;

import java.io.InputStream;

/**
 * The interface of all images used in gallery.
 */
public interface CropIImage {

    int THUMBNAIL_TARGET_SIZE = 320;
    int MINI_THUMB_TARGET_SIZE = 96;
    int UNCONSTRAINED = -1;

    /*
     * https://android.googlesource.com/platform/packages/apps/Camera2/+/3574026/src/com/android/camera/crop/CropActivity.java
     * https://android.googlesource.com/platform/packages/apps/Camera2/+/3574026/src/com/android/camera/crop/CropExtras.java
     */
    String BKEY_OUTPUT_X = "outputX";
    String BKEY_OUTPUT_Y = "outputY";
    String REQUEST_KEY_SCALE = "scale";
    String BKEY_SCALE_UP_IF_NEEDED = "scaleUpIfNeeded";
    String BKEY_ASPECT_X = "aspectX";
    String BKEY_ASPECT_Y = "aspectY";
    String BKEY_RETURN_DATA = "return-data";
    String BKEY_DATA = "data";
    String KEY_CROPPED_RECT = "cropped-rect";
    String KEY_SET_AS_WALLPAPER = "set-as-wallpaper";
    String KEY_SPOTLIGHT_X = "spotlightX";
    String KEY_SPOTLIGHT_Y = "spotlightY";
    String KEY_SHOW_WHEN_LOCKED = "showWhenLocked";
    String KEY_OUTPUT_FORMAT = "outputFormat";

    /*
    Not sure on docs yet. Might be BC itself, but at least some are used by external cropper code.
     */
    String BKEY_CIRCLE_CROP = "circleCrop";
    String REQUEST_KEY_IMAGE_ABSOLUTE_PATH = "image-path";
    String REQUEST_KEY_OUTPUT_ABSOLUTE_PATH = "output";
    String REQUEST_KEY_WHOLE_IMAGE = "whole-image";
    String REQUEST_KEY_NO_FACE_DETECTION = "noFaceDetection";

    boolean ROTATE_AS_NEEDED = true;
    boolean NO_ROTATE = false;
    boolean USE_NATIVE = true;
    boolean NO_NATIVE = false;



    /** Get the image list which contains this image. */
    @NonNull
    CropIImageList getContainer();

    /** Get the bitmap for the full size image. */
    @NonNull
    Bitmap fullSizeBitmap(int minSideLength,
                          int maxNumberOfPixels);

    @NonNull
    Bitmap fullSizeBitmap(int minSideLength,
                          int maxNumberOfPixels, boolean rotateAsNeeded);

    @NonNull
    Bitmap fullSizeBitmap(int minSideLength,
                          int maxNumberOfPixels, boolean rotateAsNeeded, boolean useNative);

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

    /** Get/Set the title of the image */
    void setTitle(String name);

    /** Get metadata of the image */
    long getDateTaken();

    @NonNull
    String getMimeType();

    int getWidth();

    int getHeight();

    @NonNull
    String getDisplayName();

    /** Get property of the image */
    boolean isReadonly();

    boolean isDrm();

    /** Get the bitmap/uri of the medium thumbnail */
    @NonNull
    Bitmap thumbBitmap(boolean rotateAsNeeded);

    @NonNull
    Uri thumbUri();

    /** Get the bitmap of the mini thumbnail. */
    @NonNull
    Bitmap miniThumbBitmap();

    /** Rotate the image */
    boolean rotateImageBy(int degrees);

}