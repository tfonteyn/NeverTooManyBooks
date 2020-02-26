/*
 * @Copyright 2020 HardBackNutter
 * @License GNU General Public License
 *
 * This file is part of NeverTooManyBooks.
 *
 * In August 2018, this project was forked from:
 * Book Catalogue 5.2.2 @2016 Philip Warner & Evan Leybourn
 *
 * Without their original creation, this project would not exist in its
 * current form. It was however largely rewritten/refactored and any
 * comments on this fork should be directed at HardBackNutter and not
 * at the original creators.
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

/*
 * Copyright 2019 Google LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.hardbacknutter.nevertoomanybooks.widgets.fastscroller;

import android.content.Context;
import android.content.res.Resources;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.widget.FrameLayout;
import android.widget.TextView;

import androidx.core.util.Consumer;

import com.hardbacknutter.nevertoomanybooks.R;

/**
 * Original code from <a href="https://github.com/zhanghai/AndroidFastScroll">
 * https://github.com/zhanghai/AndroidFastScroll</a>.
 */
final class PopupStyles {

    static final Consumer<TextView> DEFAULT = popupView -> {
        final Resources resources = popupView.getResources();
        final int minimumSize = resources.getDimensionPixelSize(R.dimen.afs_popup_min_size);
        popupView.setMinimumWidth(minimumSize);
        popupView.setMinimumHeight(minimumSize);

        final FrameLayout.LayoutParams layoutParams = (FrameLayout.LayoutParams)
                popupView.getLayoutParams();
        layoutParams.gravity = Gravity.END | Gravity.CENTER_VERTICAL;
        layoutParams.setMarginEnd(resources.getDimensionPixelOffset(
                R.dimen.afs_popup_margin_end));
        popupView.setLayoutParams(layoutParams);

        final Context context = popupView.getContext();
        popupView.setBackground(context.getDrawable(R.drawable.fastscroll_overlay_default));
        popupView.setEllipsize(TextUtils.TruncateAt.MIDDLE);
        popupView.setGravity(Gravity.CENTER);
        popupView.setIncludeFontPadding(false);
        popupView.setSingleLine(false);
        popupView.setTextColor(FastScroller.getColorInt(
                context, android.R.attr.textColorPrimaryInverse));
        popupView.setTextSize(TypedValue.COMPLEX_UNIT_PX, resources.getDimensionPixelSize(
                R.dimen.afs_popup_text_size));
    };

    static final Consumer<TextView> MD2 = popupView -> {
        final Resources resources = popupView.getResources();
        popupView.setMinimumWidth(resources.getDimensionPixelSize(
                R.dimen.afs_md2_popup_min_width));
        popupView.setMinimumHeight(resources.getDimensionPixelSize(
                R.dimen.afs_md2_popup_min_height));

        final FrameLayout.LayoutParams layoutParams = (FrameLayout.LayoutParams)
                popupView.getLayoutParams();
        layoutParams.gravity = Gravity.CENTER_HORIZONTAL | Gravity.TOP;
        layoutParams.setMarginEnd(resources.getDimensionPixelOffset(
                R.dimen.afs_md2_popup_margin_end));
        popupView.setLayoutParams(layoutParams);

        final Context context = popupView.getContext();
        popupView.setBackground(new Md2PopupBackground(context));
        popupView.setElevation(resources.getDimensionPixelOffset(R.dimen.afs_md2_popup_elevation));
        popupView.setEllipsize(TextUtils.TruncateAt.MIDDLE);
        popupView.setGravity(Gravity.CENTER);
        popupView.setIncludeFontPadding(false);
        popupView.setSingleLine(false);
        popupView.setTextColor(FastScroller.getColorInt(
                context, android.R.attr.textColorPrimaryInverse));
        popupView.setTextSize(TypedValue.COMPLEX_UNIT_PX, resources.getDimensionPixelSize(
                R.dimen.afs_md2_popup_text_size));
    };

    static final Consumer<TextView> CLASSIC = popupView -> {
        final Resources resources = popupView.getResources();
        final int minimumSize = resources.getDimensionPixelSize(R.dimen.afs_popup_min_size);
        popupView.setMinimumWidth(minimumSize);
        popupView.setMinimumHeight(minimumSize);

        final FrameLayout.LayoutParams layoutParams = (FrameLayout.LayoutParams)
                popupView.getLayoutParams();
        layoutParams.gravity = Gravity.END | Gravity.CENTER_VERTICAL;
        layoutParams.setMarginEnd(resources.getDimensionPixelOffset(
                R.dimen.afs_popup_margin_end));
        popupView.setLayoutParams(layoutParams);

        final Context context = popupView.getContext();
        popupView.setBackground(context.getDrawable(R.drawable.fastscroll_overlay_classic));

        popupView.setEllipsize(TextUtils.TruncateAt.MIDDLE);
        popupView.setGravity(Gravity.START);
        popupView.setIncludeFontPadding(false);
        popupView.setSingleLine(false);

        popupView.setTextColor(FastScroller.getColorInt(
                context, android.R.attr.textColorPrimary));
        popupView.setTextSize(TypedValue.COMPLEX_UNIT_PX, resources.getDimensionPixelSize(
                R.dimen.afs_popup_text_size));
    };

    private PopupStyles() {
    }
}
