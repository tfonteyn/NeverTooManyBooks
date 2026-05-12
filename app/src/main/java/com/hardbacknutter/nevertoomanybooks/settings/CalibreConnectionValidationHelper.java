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

package com.hardbacknutter.nevertoomanybooks.settings;

import android.Manifest;
import android.os.Build;
import android.view.View;

import androidx.activity.OnBackPressedCallback;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import java.util.function.BooleanSupplier;
import java.util.function.Supplier;

import com.hardbacknutter.nevertoomanybooks.R;
import com.hardbacknutter.nevertoomanybooks.activityresultcontracts.PermissionRequester;
import com.hardbacknutter.nevertoomanybooks.settings.widgets.HostUrlValidator;

public class CalibreConnectionValidationHelper
        extends ConnectionValidationHelper {

    @NonNull
    private final HostUrlValidator hostUrlValidator;
    @NonNull
    private final Supplier<String> urlSupplier;

    @Nullable
    private PermissionRequester permissionRequester;

    @SuppressWarnings("FieldCanBeLocal")
    private final OnBackPressedCallback backPressedCallback =
            new OnBackPressedCallback(true) {
                @Override
                public void handleOnBackPressed() {
                    if (shouldProposeValidation()) {
                        final CharSequence url = urlSupplier.get();
                        if (hostUrlValidator.isValidUrl(url)) {
                            proposeValidation();
                        } else {
                            showInvalidUrlDialog(url);
                        }
                    } else {
                        finish.run();
                    }
                }
            };

    /**
     * Constructor. Should be called from {@code Fragment#onViewCreated}.
     *
     * @param owner             of this helper
     * @param progressFrame     to use
     * @param proposeValidation callback to check if when finishing,
     *                          the connection should be validated
     * @param hostUrlValidator  to use
     * @param urlSupplier       the url to use with the hostUrlValidator
     * @param finish            callback to finish the Fragment/Activity
     */
    public CalibreConnectionValidationHelper(@NonNull final Fragment owner,
                                             @NonNull final View progressFrame,
                                             @NonNull final BooleanSupplier proposeValidation,
                                             @NonNull final HostUrlValidator hostUrlValidator,
                                             @NonNull final Supplier<String> urlSupplier,
                                             @NonNull final Runnable finish) {
        super(R.string.site_calibre, owner, progressFrame, proposeValidation, finish);
        this.hostUrlValidator = hostUrlValidator;
        this.urlSupplier = urlSupplier;

        //noinspection DataFlowIssue
        owner.getActivity().getOnBackPressedDispatcher()
             .addCallback(owner.getViewLifecycleOwner(), backPressedCallback);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.CINNAMON_BUN) {
            permissionRequester = new PermissionRequester(owner.getActivity(), owner);
            final String calibre = owner.getString(R.string.site_calibre);
            permissionRequester.addPermission(
                    Manifest.permission.ACCESS_LOCAL_NETWORK, true,
                    owner.getString(R.string.warning_local_network_permission_required, calibre),
                    owner.getString(R.string.warning_local_network_permission_denied, calibre)
            );
        }

        init();
    }

    void validate() {
        // just checking on null is enough.
        // Leaving the unneeded SDK check as a reminder
        if (permissionRequester != null
            && Build.VERSION.SDK_INT >= Build.VERSION_CODES.CINNAMON_BUN) {
            permissionRequester.request(Manifest.permission.ACCESS_LOCAL_NETWORK, isGranted -> {
                if (isGranted) {
                    super.validate();
                }
            });
        } else {
            super.validate();
        }
    }
}
