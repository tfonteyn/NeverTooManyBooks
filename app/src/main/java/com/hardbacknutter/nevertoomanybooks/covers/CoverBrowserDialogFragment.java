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

import android.content.DialogInterface;
import android.os.Bundle;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.EnumSet;

import com.hardbacknutter.nevertoomanybooks.R;
import com.hardbacknutter.nevertoomanybooks.databinding.DialogCoverBrowserContentBinding;
import com.hardbacknutter.nevertoomanybooks.dialogs.FFBaseDialogFragment;
import com.hardbacknutter.nevertoomanybooks.utils.WindowSizeClass;

/**
 * There is no matching BottomSheet class as we'll display
 * this component always fullscreen on small and medium size screens.
 */
public class CoverBrowserDialogFragment
        extends FFBaseDialogFragment<DialogCoverBrowserContentBinding> {

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
        final DialogCoverBrowserContentBinding vb = DialogCoverBrowserContentBinding.bind(
                view.findViewById(R.id.dialog_content));
        setSubtitle(delegate.getToolbarSubtitle());

        delegate.onViewCreated(vb);

        // Don't give it the gallery! We're auto-adapting in the delegate already
        adjustWindowSize(null, /* ignored */ 0f);
    }

    @Override
    public void onCancel(@NonNull final DialogInterface dialog) {
        ((CoverBrowserDelegate) delegate).cancelAllTasks();
        super.onCancel(dialog);
    }

    @Override
    public void onResume() {
        super.onResume();
        delegate.onResume();
    }
}
