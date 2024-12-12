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

package com.hardbacknutter.nevertoomanybooks.dialogs;

import android.annotation.SuppressLint;
import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.PopupWindow;
import android.widget.TextView;

import androidx.annotation.NonNull;

import com.hardbacknutter.nevertoomanybooks.R;

public final class InfoPopup {

    private InfoPopup() {
    }

    /**
     * Show a popup info text.
     *
     * @param infoView the View from which we'll take the content-description as text to display
     *                 and anchor the popup to.
     */
    public static void show(@NonNull final View infoView) {
        show(infoView, 0, 0, infoView.getContentDescription());
    }

    /**
     * Show a popup info text. A tap outside of the popup will make it go away again.
     *
     * @param anchor the view on which to pin the popup window
     * @param xoff   A horizontal offset from the anchor in pixels
     * @param yoff   A vertical offset from the anchor in pixels
     * @param text   to display
     */
    @SuppressLint("UseCompatLoadingForDrawables")
    public static void show(@NonNull final View anchor,
                            final int xoff,
                            final int yoff,
                            @NonNull final CharSequence text) {
        final Context context = anchor.getContext();
        @SuppressLint("InflateParams")
        final View root = LayoutInflater.from(context).inflate(R.layout.popup_info, null, false);
        final TextView infoView = root.findViewById(R.id.info);
        infoView.setText(text);

        final PopupWindow popup = new PopupWindow(context);
        popup.setContentView(root);
        // make the rounded corners transparent
        popup.setBackgroundDrawable(context.getDrawable(R.drawable.bg_info_popup));
        popup.setFocusable(true);
        popup.showAsDropDown(anchor, xoff, yoff);
    }
}
