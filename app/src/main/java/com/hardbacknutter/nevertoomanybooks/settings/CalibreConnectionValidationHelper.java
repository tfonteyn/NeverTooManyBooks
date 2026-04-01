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

import android.view.View;

import androidx.activity.OnBackPressedCallback;
import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.util.function.BooleanSupplier;
import java.util.function.Supplier;

import com.hardbacknutter.nevertoomanybooks.R;
import com.hardbacknutter.nevertoomanybooks.settings.widgets.HostUrlValidator;

public class CalibreConnectionValidationHelper
        extends ConnectionValidationHelper {

    @NonNull
    private final HostUrlValidator hostUrlValidator;
    @NonNull
    private final Supplier<String> urlSupplier;

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
    }

    private void showInvalidUrlDialog(@NonNull final CharSequence url) {
        //noinspection DataFlowIssue
        new MaterialAlertDialogBuilder(owner.getContext())
                .setIcon(R.drawable.info_24px)
                .setTitle(R.string.error_invalid_url)
                .setMessage(url)
                .setPositiveButton(R.string.action_edit, (d, w) -> {
                    // no action, just stay on the screen
                })
                .setNegativeButton(R.string.action_not_now, (d, w) -> {
                    finish.run();
                })
                .create()
                .show();
    }
}
