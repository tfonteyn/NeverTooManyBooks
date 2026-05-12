/*
 * @Copyright 2018-2026 HardBackNutter
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

package com.hardbacknutter.nevertoomanybooks.activityresultcontracts;

import android.content.Intent;
import android.net.Uri;
import android.provider.Settings;

import androidx.activity.result.ActivityResultCaller;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;

import com.hardbacknutter.nevertoomanybooks.R;

/**
 * A flexible way to handle permissions.
 * <p>
 * in {@link Fragment#onViewCreated}:
 * <pre>
 * {@code
 *      permissionRequester = new PermissionRequester(fragmentActivity, this);
 *      permissionRequester.addPermission(Manifest.permission.CAMERA, "dialog msg", true);
 *      permissionRequester.addPermission(Manifest.permission.READ_CONTACTS, "dialog msg", false);
 *      ...
 * }
 * </pre>
 * <p>
 * Call when needed:
 * <pre>
 * {@code
 *      permissionRequester.request(Manifest.permission.CAMERA, isGranted -> {
 *             if (isGranted) {
 *                 takePicture();
 *             }
 *         });
 * }
 * </pre>
 */
public class PermissionRequester {

    private static final String ERROR_MISSING_MESSAGE = "No message registered for :";
    private static final String ERROR_MISSING_LAUNCHER = "No launcher registered for :";

    @NonNull
    private final FragmentActivity fragmentActivity;
    @NonNull
    private final ActivityResultCaller caller;

    private final Map<String, ActivityResultLauncher<String>> launchers = new HashMap<>();
    private final Map<String, Consumer<Boolean>> callbacks = new HashMap<>();
    private final Map<String, CharSequence> rationaleMessages = new HashMap<>();
    private final Map<String, CharSequence> deniedMessages = new HashMap<>();
    private final Map<String, Boolean> requiredPermissions = new HashMap<>();

    /**
     * Constructor.
     *
     * @param fragmentActivity current context
     * @param caller           Fragment or Activity
     */
    public PermissionRequester(@NonNull final FragmentActivity fragmentActivity,
                               @NonNull final ActivityResultCaller caller) {
        this.fragmentActivity = fragmentActivity;
        this.caller = caller;
    }

    /**
     * Registers a launcher for the given permission.
     * Should be called once per permission before calling
     * {@link #request(String, Consumer)}.
     *
     * @param permission       to request
     * @param required         whether the permission is required;
     *                         as opposed to optional / nice-to-have
     * @param rationaleMessage Message to show in "rationale" dialog
     * @param deniedMessage    Message to show in the "denied" dialog
     */
    public void addPermission(@NonNull final String permission,
                              final boolean required,
                              @NonNull final CharSequence rationaleMessage,
                              @NonNull final CharSequence deniedMessage) {
        // Sanity check
        if (launchers.containsKey(permission)) {
            return;
        }

        rationaleMessages.put(permission, rationaleMessage);
        deniedMessages.put(permission, deniedMessage);
        requiredPermissions.put(permission, required);

        final ActivityResultLauncher<String> launcher = caller.registerForActivityResult(
                new ActivityResultContracts.RequestPermission(),
                isGranted -> {
                    final Consumer<Boolean> onResult = callbacks.remove(permission);
                    if (onResult == null) {
                        // We've been restarted. Perhaps the user rotated their device,
                        // or Android decided to [bleep] us.
                        // Simply quit. The user will need to re-engange the action
                        // which triggered the permission request.
                        return;
                    }

                    if (isGranted) {
                        onResult.accept(true);
                    } else {
                        //noinspection DataFlowIssue
                        if (requiredPermissions.getOrDefault(permission, false)) {
                            showDeniedDialog(permission, onResult);
                        } else {
                            // Silent for non-required
                            onResult.accept(false);
                        }
                    }
                });
        launchers.put(permission, launcher);
    }

    /**
     * Request the given permission, showing rationale if needed.
     *
     * @param permission to request
     * @param onResult   callback
     *
     * @throws IllegalStateException (debug) when no matching launcher was registered.
     */
    public void request(@NonNull final String permission,
                        @NonNull final Consumer<Boolean> onResult) {
        if (ContextCompat.checkSelfPermission(fragmentActivity, permission)
            == android.content.pm.PackageManager.PERMISSION_GRANTED) {
            onResult.accept(true);
            return;
        }

        final ActivityResultLauncher<String> launcher = launchers.get(permission);
        Objects.requireNonNull(launcher, ERROR_MISSING_LAUNCHER + permission);

        if (ActivityCompat.shouldShowRequestPermissionRationale(fragmentActivity, permission)) {
            showRationaleDialog(permission, launcher, onResult);
        } else {
            callbacks.put(permission, onResult);
            launcher.launch(permission);
        }
    }

    private void showRationaleDialog(@NonNull final String permission,
                                     @NonNull final ActivityResultLauncher<String> launcher,
                                     @NonNull final Consumer<Boolean> onResult) {
        final CharSequence message = rationaleMessages.get(permission);
        Objects.requireNonNull(message, ERROR_MISSING_MESSAGE + permission);

        new MaterialAlertDialogBuilder(fragmentActivity)
                .setTitle(R.string.lbl_permission_request)
                .setMessage(message)
                .setNegativeButton(R.string.cancel, (dialog, which) -> onResult.accept(false))
                .setPositiveButton(R.string.ok, (dialog, which) -> {
                    callbacks.put(permission, onResult);
                    launcher.launch(permission);
                })
                .show();
    }

    private void showDeniedDialog(@NonNull final String permission,
                                  @NonNull final Consumer<Boolean> onResult) {
        final CharSequence message = deniedMessages.get(permission);
        Objects.requireNonNull(message, ERROR_MISSING_MESSAGE + permission);

        new MaterialAlertDialogBuilder(fragmentActivity)
                .setTitle(R.string.lbl_permission_request)
                .setMessage(message)
                .setNegativeButton(R.string.cancel, (dialog, which) -> onResult.accept(false))
                .setPositiveButton(R.string.lbl_settings, (dialog, which) -> {
                    openAppSettings();
                    onResult.accept(false);
                })
                .show();
    }

    private void openAppSettings() {
        // Reminder: there is no public api to go straight to the permission
        // page itself; nor is there a public method to have "permissions" flash.
        final Intent intent = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
        final Uri uri = Uri.fromParts("package", fragmentActivity.getPackageName(), null);
        intent.setData(uri);
        fragmentActivity.startActivity(intent);
    }
}
