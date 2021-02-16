/*
 * @Copyright 2018-2021 HardBackNutter
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
package com.hardbacknutter.nevertoomanybooks.widgets.fastscroller;

import android.graphics.Rect;
import android.view.View;
import android.view.WindowInsets;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

public class ScrollingViewOnApplyWindowInsetsListener
        implements View.OnApplyWindowInsetsListener {

    @NonNull
    private final Rect mPadding = new Rect();
    @Nullable
    private final FastScroller.OverlayProvider mOverlayProvider;

    /**
     * Constructor.
     *
     * @param view            the scrolling view
     * @param overlayProvider (optional) overlay for the view
     */
    ScrollingViewOnApplyWindowInsetsListener(
            @Nullable final View view,
            @Nullable final FastScroller.OverlayProvider overlayProvider) {

        if (view != null) {
            mPadding.set(view.getPaddingLeft(),
                         view.getPaddingTop(),
                         view.getPaddingRight(),
                         view.getPaddingBottom());
        }
        mOverlayProvider = overlayProvider;
    }

    @NonNull
    @Override
    public WindowInsets onApplyWindowInsets(@NonNull final View view,
                                            @NonNull final WindowInsets insets) {

        view.setPadding(mPadding.left + insets.getSystemWindowInsetLeft(),
                        mPadding.top,
                        mPadding.right + insets.getSystemWindowInsetRight(),
                        mPadding.bottom + insets.getSystemWindowInsetBottom());

        if (mOverlayProvider != null) {
            mOverlayProvider.setPadding(insets.getSystemWindowInsetLeft(),
                                        0,
                                        insets.getSystemWindowInsetRight(),
                                        insets.getSystemWindowInsetBottom());
        }
        return insets;
    }
}
