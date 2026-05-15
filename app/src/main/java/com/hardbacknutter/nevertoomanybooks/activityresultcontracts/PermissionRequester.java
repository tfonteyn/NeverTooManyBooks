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

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.provider.Settings;

import androidx.activity.result.ActivityResultCaller;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContract;
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
 *      pr = new PermissionRequester(fragmentActivity, this);
 *      pr.addPermission(Manifest.permission.CAMERA, true,
 *                       "request msg", "denied msg");
 *      pr.addPermission(Manifest.permission.READ_CONTACTS, false,
 *                       "request msg", "denied msg");
 *      ...
 * }
 * </pre>
 * Call when needed:
 * <pre>
 * {@code
 *      pr.request(Manifest.permission.CAMERA, isGranted -> {
 *             if (isGranted) {
 *                 takePicture();
 *             }
 *         });
 * }
 * </pre>
 *
 * This class uses a private preference file to track permission requests.
 */
public class PermissionRequester {

    private static final String ERROR_MISSING_MESSAGE = "No message registered for :";
    private static final String ERROR_MISSING_LAUNCHER = "No launcher registered for :";
    /** Use a private preference file. */
    private static final String PERM_PREFS = "permissions.prefs";
    /** Preference prefix. */
    private static final String PERMISSION_REQUESTED = "permission.requested.";

    @NonNull
    private final FragmentActivity activity;
    @NonNull
    private final ActivityResultCaller contractOwner;

    private final Map<String, ActivityResultLauncher<String>> launchers = new HashMap<>();
    private final Map<String, Consumer<Boolean>> callbacks = new HashMap<>();
    private final Map<String, CharSequence> rationaleMessages = new HashMap<>();
    private final Map<String, CharSequence> deniedMessages = new HashMap<>();
    private final Map<String, Boolean> requiredPermissions = new HashMap<>();

    /**
     * Constructor.
     *
     * @param activity      the hosting Activity
     * @param contractOwner the component which handles the {@link ActivityResultContract}
     */
    public PermissionRequester(@NonNull final FragmentActivity activity,
                               @NonNull final ActivityResultCaller contractOwner) {
        this.activity = activity;
        this.contractOwner = contractOwner;
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

        final ActivityResultLauncher<String> launcher = contractOwner.registerForActivityResult(
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

                    // remember we asked!
                    setPermissionWasRequested(permission);

                    if (isGranted) {
                        // ok, all we need, we're done.
                        onResult.accept(true);
                        return;
                    }

                    userDenied(permission, onResult);
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
        if (ContextCompat.checkSelfPermission(activity, permission)
            == android.content.pm.PackageManager.PERMISSION_GRANTED) {
            onResult.accept(true);
            return;
        }

        // Check if we should show the rationale why we need/want the permission.
        // If the user denied once, they can be asked again.
        // If they ticked "Don't ask again" or denied it previously this will fail.
        if (ActivityCompat.shouldShowRequestPermissionRationale(activity, permission)) {
            // Show the message, and start the permission launcher if the user taps "OK"
            showRationaleDialog(permission, onResult);
            return;
        }

        // The very first request was done, and the user denied us.
        // Is it the FIRST time we're asking again?
        if (isFirstTimeRequesting(permission)) {
            // then repeat the request
            launchRequest(permission, onResult);
            return;
        }

        // User has ticked "Don't ask again" or denied it previously.
        userDenied(permission, onResult);
    }


    private void launchRequest(@NonNull final String permission,
                               @NonNull final Consumer<Boolean> onResult) {

        final ActivityResultLauncher<String> launcher = launchers.get(permission);
        Objects.requireNonNull(launcher, ERROR_MISSING_LAUNCHER + permission);

        callbacks.put(permission, onResult);
        launcher.launch(permission);
    }

    private void showRationaleDialog(@NonNull final String permission,
                                     @NonNull final Consumer<Boolean> onResult) {
        final CharSequence message = rationaleMessages.get(permission);
        Objects.requireNonNull(message, ERROR_MISSING_MESSAGE + permission);

        new MaterialAlertDialogBuilder(activity)
                .setTitle(R.string.lbl_permission_request)
                .setMessage(message)
                .setNegativeButton(R.string.cancel, (dialog, which) -> onResult.accept(false))
                .setPositiveButton(R.string.ok, (dialog, which)
                        -> launchRequest(permission, onResult))
                .show();
    }

    /**
     * If the permission was optional, this method simply passes
     * {@code not granted} to the callback.
     * <p>
     * Otherwise explain to the user they denied a permission we need.
     * Offer an option to be redirected to the app settings.
     * Regardless of option chosen, we'll pass {@code not granted} to the callback.
     *
     * @param permission requested
     * @param onResult   callback
     */
    private void userDenied(@NonNull final String permission,
                            @NonNull final Consumer<Boolean> onResult) {
        // If this was an optional permission...
        //noinspection DataFlowIssue
        if (!requiredPermissions.getOrDefault(permission, false)) {
            // user denied, we're done
            onResult.accept(false);
            return;
        }

        // remind the user that without this permission we're blocked.
        final CharSequence message = deniedMessages.get(permission);
        Objects.requireNonNull(message, ERROR_MISSING_MESSAGE + permission);

        new MaterialAlertDialogBuilder(activity)
                .setTitle(R.string.lbl_permission_request)
                .setMessage(message)
                .setNegativeButton(R.string.cancel, (dialog, which) -> onResult.accept(false))
                .setPositiveButton(R.string.lbl_settings, (dialog, which) -> {
                    openAppSettings();
                    onResult.accept(false);
                })
                .show();
    }

    private boolean isFirstTimeRequesting(@NonNull final String permission) {
        return activity.getSharedPreferences(PERM_PREFS, Context.MODE_PRIVATE)
                       .getBoolean(PERMISSION_REQUESTED + permission, true);
    }

    private void setPermissionWasRequested(@NonNull final String permission) {
        activity.getSharedPreferences(PERM_PREFS, Context.MODE_PRIVATE)
                .edit().putBoolean(PERMISSION_REQUESTED + permission, false).apply();
    }

    private void openAppSettings() {
        // Reminder: there is no public api to go straight to the permission
        // page itself; nor is there a public method to have "permissions" flash.
        final Intent intent = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
        final Uri uri = Uri.fromParts("package", activity.getPackageName(), null);
        intent.setData(uri);
        activity.startActivity(intent);
    }
}
