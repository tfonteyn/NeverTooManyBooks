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

import android.util.SparseArray;

import androidx.annotation.NonNull;

/**
 * Handle permissions from outside of Fragment/Activities.
 * <p>
 * Usage:
 *
 * <pre>
 * {@code
 *     public class YourFragment extends Fragment
 *         implements PermissionsHelper.RequestHandler {
 *
 *         @Override
 *         public void onRequestPermissionsResult(final int requestCode,
 *                                                @NonNull final String[] permissions,
 *                                                @NonNull final int[] grantResults) {
 *
 *             onRequestPermissionsResultCallback(requestCode, permissions, grantResults);
 *         }
 *     }
 * }
 * </pre>
 * <p>
 * And where you need to request permissions:
 * <pre>
 * {@code
 *     ((PermissionsHelper.RequestHandler) fragment).addPermissionCallback(
 *                     UniqueId.REQ_ANDROID_PERMISSIONS, (perms, grantResults) -> {
 *                         if (grantResults[0] == PackageManager.PERMISSION_GRANTED) {
 *                             doSomething();
 *                         }
 *                     });
 *     ActivityCompat.requestPermissions(fragment.getActivity(),
 *                                       new String[]{Manifest.permission.SOME_PERMISSION},
 *                                       UniqueId.REQ_ANDROID_PERMISSIONS);
 * }
 * </pre>
 */
public interface PermissionsHelper {

    SparseArray<ResultHandler> permissionCallback = new SparseArray<>();

    interface RequestHandler {

        default void addPermissionCallback(final int requestCode,
                                           @NonNull final ResultHandler callback) {
            permissionCallback.put(requestCode, callback);
        }

        default void onRequestPermissionsResultCallback(final int requestCode,
                                                        @NonNull final String[] permissions,
                                                        @NonNull final int[] grantResults) {

            final PermissionsHelper.ResultHandler callback = permissionCallback.get(requestCode);
            if (callback != null) {
                callback.requestResult(permissions, grantResults);
            } else {
                throw new IllegalStateException("no registered callback for requestCode="
                                                + requestCode);
            }
        }
    }

    interface ResultHandler {

        void requestResult(@NonNull String[] permissions,
                           @NonNull int[] grantResults);
    }

}
