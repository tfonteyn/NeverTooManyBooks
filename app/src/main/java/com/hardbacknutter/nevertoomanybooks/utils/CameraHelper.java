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
package com.hardbacknutter.nevertoomanybooks.utils;

import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.provider.MediaStore;

import androidx.annotation.AnyThread;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;

import java.io.File;

import com.hardbacknutter.nevertoomanybooks.RequestCode;

public class CameraHelper {

    /** Log tag. */
    private static final String TAG = "CameraHelper";

    /**
     * We use a single temporary file.
     * Note we might not always clean it up.
     * We just make sure an orphaned file is deleted before taking a new picture.
     * But as it's in the cache directory, Android can clean it when it wants.
     */
    private static final String TEMP_FILENAME = TAG + ".jpg";

    /** Needed while checking permissions. */
    private int mRequestCode;

    /**
     * DEBUG only.
     *
     * @param context Current context
     *
     * @return the default camera file.
     */
    @NonNull
    public static File getCameraFile(@NonNull final Context context) {
        return AppDir.Cache.getFile(context, TEMP_FILENAME);
    }

    /**
     * Start the camera to get an image.
     *
     * @param fragment    which will check camera permissions
     * @param requestCode set/returned with the activity result
     */
    public void startCamera(@NonNull final Fragment fragment,
                            final int requestCode) {
        mRequestCode = requestCode;

        final Context context = fragment.getContext();

        final Intent intent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
        //noinspection ConstantConditions
        final File file = AppDir.Cache.getFile(context, TEMP_FILENAME);
        // delete any orphaned file.
        FileUtils.delete(file);

        final Uri uri = GenericFileProvider.getUriForFile(context, file);
        intent.putExtra(MediaStore.EXTRA_OUTPUT, uri);

        if (ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA)
            == PackageManager.PERMISSION_GRANTED) {
            // GO!
            fragment.startActivityForResult(intent, requestCode);

        } else {
            ((PermissionsHelper.RequestHandler) fragment).addPermissionCallback(
                    RequestCode.ANDROID_PERMISSIONS, (perms, grantResults) -> {
                        if (grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                            startCamera(fragment, mRequestCode);
                        }
                    });
            //noinspection ConstantConditions
            ActivityCompat.requestPermissions(fragment.getActivity(),
                                              new String[]{Manifest.permission.CAMERA},
                                              RequestCode.ANDROID_PERMISSIONS);
        }
    }

    /**
     * Get the file.
     *
     * @param context Current context
     *
     * @return file or {@code null}
     */
    @Nullable
    @AnyThread
    public File getFile(@NonNull final Context context) {
        final File file = AppDir.Cache.getFile(context, TEMP_FILENAME);
        if (file.exists()) {
            return file;
        }
        return null;
    }

    public void cleanup(@NonNull final Context context) {
        FileUtils.delete(AppDir.Cache.getFile(context, TEMP_FILENAME));
    }

    @Override
    @NonNull
    public String toString() {
        return "CameraHelper{"
               + ", mRequestCode=" + mRequestCode
               + '}';
    }
}
