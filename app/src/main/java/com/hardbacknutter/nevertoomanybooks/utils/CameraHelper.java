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
import android.annotation.SuppressLint;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.provider.MediaStore;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.RequiresPermission;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;

import java.io.File;

public class CameraHelper {

    @SuppressWarnings("FieldNotUsedInToString")
    @NonNull
    private final Fragment mFragment;

    @SuppressWarnings("FieldNotUsedInToString")
    @NonNull
    private final ActivityResultLauncher<String> mRequestPermissionLauncher;

    /** Set/returned with the activity result. */
    private int mRequestCode;

    /** The file the camera will write to. */
    private File mFile;

    /**
     * Constructor.
     *
     * @param fragment hosting fragment
     */
    @SuppressLint("MissingPermission")
    public CameraHelper(@NonNull final Fragment fragment) {
        mFragment = fragment;
        mRequestPermissionLauncher = mFragment.registerForActivityResult(
                new ActivityResultContracts.RequestPermission(), (isGranted) -> {
                    if (isGranted) {
                        startCameraInternal();
                    }
                });
    }


    /**
     * Start the camera to get an image.
     *
     * @param file        for the camera to write to
     * @param requestCode set/returned with the activity result
     */
    public void startCamera(@NonNull final File file,
                            final int requestCode) {
        mRequestCode = requestCode;
        mFile = file;

        //noinspection ConstantConditions
        if (ContextCompat.checkSelfPermission(mFragment.getContext(), Manifest.permission.CAMERA)
            == PackageManager.PERMISSION_GRANTED) {
            startCameraInternal();
        } else {
            mRequestPermissionLauncher.launch(Manifest.permission.CAMERA);
        }
    }

    @RequiresPermission(Manifest.permission.CAMERA)
    private void startCameraInternal() {
        //noinspection ConstantConditions
        final Uri uri = GenericFileProvider.createUri(mFragment.getContext(), mFile);
        final Intent intent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE)
                .putExtra(MediaStore.EXTRA_OUTPUT, uri);
        mFragment.startActivityForResult(intent, mRequestCode);
    }

    @Override
    @NonNull
    public String toString() {
        return "CameraHelper{"
               + ", mFile=" + mFile
               + ", mRequestCode=" + mRequestCode
               + '}';
    }
}
