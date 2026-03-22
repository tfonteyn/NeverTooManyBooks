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

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;

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

    public CalibreConnectionValidationHelper(@NonNull final Fragment owner,
                                             @NonNull final View progressFrame,
                                             @NonNull final BooleanSupplier shouldProposeValidation,
                                             @NonNull final Supplier<String> urlSupplier,
                                             @NonNull final Runnable finish) {
        super(R.string.site_calibre, owner, progressFrame, shouldProposeValidation, finish);
        this.urlSupplier = urlSupplier;

        hostUrlValidator = new HostUrlValidator();
    }

    @NonNull
    public HostUrlValidator getHostUrlValidator() {
        return hostUrlValidator;
    }

    @Override
    protected void proposeValidation() {
        final CharSequence text = urlSupplier.get();
        if (!hostUrlValidator.isValidUrl(text)) {
            //noinspection DataFlowIssue
            hostUrlValidator.showUrlInvalidDialog(owner.getContext(), text, null, finish);
            return;
        }
        super.proposeValidation();
    }
}
