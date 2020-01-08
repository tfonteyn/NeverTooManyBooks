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
package com.hardbacknutter.nevertoomanybooks.utils;

import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.provider.MediaStore;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.Objects;

import com.hardbacknutter.nevertoomanybooks.UniqueId;
import com.hardbacknutter.nevertoomanybooks.debug.Logger;

public class CameraHelper {

    /** Log tag. */
    private static final String TAG = "CameraHelper";

    /**
     * We use a single temporary file.
     * Note we might not always clean it up.
     * We just make sure an orphaned file is deleted before taking a new picture.
     * But as it's in the cache directory, Android can clean it when it wants.
     */
    private static final String CAMERA_FILENAME = "Camera";

    /** rotation angle to apply after a picture was taken. */
    private int mRotationAngle;
    /** by default, we tell the camera to give us full-size pictures. */
    private boolean mUseFullSize = true;
    private Fragment mFragment;
    private int mRequestCode;

    /**
     * DEBUG only.
     *
     * @param context Current context
     *
     * @return the default camera file.
     */
    public static File getCameraFile(@NonNull final Context context) {
        return StorageUtils.getTempCoverFile(context, CAMERA_FILENAME);
    }

    public static void deleteTempFile(@NonNull final Context context) {
        StorageUtils.deleteFile(StorageUtils.getTempCoverFile(context, CAMERA_FILENAME));
    }

    /**
     * Apply a rotation after acquiring a picture.
     *
     * @param rotationAngle to apply
     */
    public void setRotationAngle(final int rotationAngle) {
        mRotationAngle = rotationAngle;
    }

    public void setUseFullSize(final boolean useFullSize) {
        mUseFullSize = useFullSize;
    }

    /**
     * Start the camera to get an image.
     *
     * @param requestCode set/returned with the activity result
     */
    public void startCamera(@NonNull final Fragment fragment,
                            final int requestCode) {
        mFragment = fragment;
        mRequestCode = requestCode;

        Intent intent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
        if (mUseFullSize) {
            //noinspection ConstantConditions
            File file = StorageUtils.getTempCoverFile(fragment.getContext(), CAMERA_FILENAME);
            // delete any orphaned file.
            StorageUtils.deleteFile(file);

            Uri uri = GenericFileProvider.getUriForFile(fragment.getContext(), file);
            intent.putExtra(MediaStore.EXTRA_OUTPUT, uri);
        }

        //noinspection ConstantConditions
        if (ContextCompat.checkSelfPermission(fragment.getContext(), Manifest.permission.CAMERA)
            == PackageManager.PERMISSION_GRANTED) {
            // GO!
            fragment.startActivityForResult(intent, requestCode);

        } else {
            ((PermissionsHelper.RequestHandler) fragment).addPermissionCallback(
                    UniqueId.REQ_ANDROID_PERMISSIONS, (perms, grantResults) -> {
                        if (grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                            startCamera(mFragment, mRequestCode);
                        }
                    });
            //noinspection ConstantConditions
            ActivityCompat.requestPermissions(fragment.getActivity(),
                                              new String[]{Manifest.permission.CAMERA},
                                              UniqueId.REQ_ANDROID_PERMISSIONS);
        }
    }

    /**
     * Get the bitmap with optional rotation.
     *
     * @param data intent to read from, or {@code null}
     *             if you <strong>know there will be a full-sized file</strong>
     *
     * @return bitmap or {@code null}
     */
    @Nullable
    public Bitmap getBitmap(@NonNull final Context context,
                            @Nullable final Intent data) {
        Bitmap bm;
        if (mUseFullSize) {
            String fileSpec = StorageUtils.getTempCoverFile(context, CAMERA_FILENAME)
                                          .getAbsolutePath();
            bm = BitmapFactory.decodeFile(fileSpec);
        } else {
            Objects.requireNonNull(data);
            bm = data.getParcelableExtra("data");
        }

        if (bm != null && mRotationAngle != 0) {
            bm = ImageUtils.rotate(bm, mRotationAngle);
        }
        return bm;
    }

    /**
     * Get the file with optional rotation.
     *
     * @param data intent to read from, or {@code null}
     *             if you <strong>know there will be a full-sized file</strong>
     *
     * @return file or {@code null}
     */
    @Nullable
    public File getFile(@NonNull final Context context,
                        @Nullable final Intent data) {

        File file;
        if (mUseFullSize) {
            file = StorageUtils.getTempCoverFile(context, CAMERA_FILENAME);
            if (file.exists()) {
                if (mRotationAngle != 0) {
                    ImageUtils.rotate(context, file, mRotationAngle);
                }
                return file;
            }

        } else {
            Objects.requireNonNull(data);
            Bitmap bm = data.getParcelableExtra("data");
            if (bm != null) {
                if (mRotationAngle != 0) {
                    bm = ImageUtils.rotate(bm, mRotationAngle);
                }

                file = StorageUtils.getTempCoverFile(context, CAMERA_FILENAME);
                try (OutputStream os = new FileOutputStream(file.getAbsoluteFile())) {
                    bm.compress(Bitmap.CompressFormat.PNG, 100, os);
                    return file;

                } catch (@SuppressWarnings("OverlyBroadCatchBlock") @NonNull final IOException e) {
                    Logger.error(TAG, e);
                }
            }
        }
        return null;
    }

    @Override
    @NonNull
    public String toString() {
        return "CameraHelper{"
               + "mRotationAngle=" + mRotationAngle
               + ", mUseFullSize=" + mUseFullSize
               + '}';
    }
}
