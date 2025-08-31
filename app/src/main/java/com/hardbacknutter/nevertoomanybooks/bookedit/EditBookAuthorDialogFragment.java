/*
 * @Copyright 2018-2025 HardBackNutter
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
package com.hardbacknutter.nevertoomanybooks.bookedit;

import android.app.Dialog;
import android.os.Bundle;
import android.view.ViewGroup;

import androidx.annotation.Nullable;
import androidx.window.layout.WindowMetricsCalculator;

import com.hardbacknutter.nevertoomanybooks.core.widgets.ScreenSize;
import com.hardbacknutter.nevertoomanybooks.dialogs.FlexClassicDialogFragment;

public class EditBookAuthorDialogFragment
        extends FlexClassicDialogFragment {

    /** Use 90% of available screen width. */
    private static final float WIDTH_RATIO = 0.9f;

    @Override
    public void onCreate(@Nullable final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        delegate = new EditBookAuthorDelegate(this, requireArguments());
    }

    @Override
    public void onStart() {
        super.onStart();
        //noinspection DataFlowIssue
        final ScreenSize screenSize = ScreenSize.compute(getActivity());
        // Tablets and medium phones in landscape mode;
        // Maximize the width to show as many checkboxes as possible without scrolling.
        if (screenSize.getWidth().isAtLeast(ScreenSize.Value.Expanded)) {
            final Dialog dialog = getDialog();
            // sanity check
            if (dialog != null) {
                //noinspection DataFlowIssue
                final int screenWidth = WindowMetricsCalculator
                        .getOrCreate()
                        .computeCurrentWindowMetrics(getContext())
                        .getBounds()
                        .width();
                //noinspection DataFlowIssue
                dialog.getWindow().setLayout((int) (screenWidth * WIDTH_RATIO),
                                             ViewGroup.LayoutParams.WRAP_CONTENT);
            }
        }
    }
}
