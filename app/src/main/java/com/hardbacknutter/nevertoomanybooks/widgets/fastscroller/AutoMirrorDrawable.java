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

import android.graphics.Canvas;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.appcompat.graphics.drawable.DrawableWrapper;

/**
 * Original code from <a href="https://github.com/zhanghai/AndroidFastScroll">
 * https://github.com/zhanghai/AndroidFastScroll</a>
 */
public class AutoMirrorDrawable
        extends DrawableWrapper {

    AutoMirrorDrawable(@NonNull final Drawable drawable) {
        super(drawable);
    }

    @Override
    public void draw(@NonNull final Canvas canvas) {
        if (needMirroring()) {
            float centerX = getBounds().exactCenterX();
            canvas.scale(-1, 1, centerX, 0);
            super.draw(canvas);
            canvas.scale(-1, 1, centerX, 0);
        } else {
            super.draw(canvas);
        }
    }

    @Override
    public boolean onLayoutDirectionChanged(final int layoutDirection) {
        super.onLayoutDirectionChanged(layoutDirection);
        return true;
    }

    @Override
    public boolean isAutoMirrored() {
        return true;
    }

    private boolean needMirroring() {
        return getLayoutDirection() == View.LAYOUT_DIRECTION_RTL;
    }

    @Override
    public boolean getPadding(@NonNull final Rect padding) {
        boolean hasPadding = super.getPadding(padding);
        if (needMirroring()) {
            int paddingStart = padding.left;
            padding.left = padding.right;
            padding.right = paddingStart;
        }
        return hasPadding;
    }
}
