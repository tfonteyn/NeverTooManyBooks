/*
 * @Copyright 2018-2024 HardBackNutter
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
package com.hardbacknutter.nevertoomanybooks.covers;

import android.os.Bundle;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.EnumSet;

import com.hardbacknutter.nevertoomanybooks.R;
import com.hardbacknutter.nevertoomanybooks.dialogs.BaseFFDialogFragment;
import com.hardbacknutter.nevertoomanybooks.utils.WindowSizeClass;

public class CoverBrowserDialogFragment
        extends BaseFFDialogFragment {

    /**
     * No-arg constructor for OS use.
     */
    public CoverBrowserDialogFragment() {
        super(R.layout.dialog_cover_browser,
              R.layout.dialog_cover_browser_content,
              // Fullscreen on Medium screens
              EnumSet.of(WindowSizeClass.Medium),
              EnumSet.of(WindowSizeClass.Medium));
    }

    @Override
    public void onCreate(@Nullable final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        delegate = new CoverBrowserDelegate(this, requireArguments());
    }

    @Override
    public void onViewCreated(@NonNull final View view,
                              @Nullable final Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        // Don't give it the gallery! We're auto-adapting in the delegate already
        adjustWindowSize(null, /* ignored */ 0f);
    }
}
