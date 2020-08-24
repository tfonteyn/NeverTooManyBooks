/*
 * @Copyright 2020 HardBackNutter
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
import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Matrix;
import android.graphics.Outline;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.hardbacknutter.nevertoomanybooks.R;

/**
 * Original code from <a href="https://github.com/zhanghai/AndroidFastScroll">
 * https://github.com/zhanghai/AndroidFastScroll</a>.
 */
class Md2PopupBackground
        extends Drawable {

    @NonNull
    private final Paint mPaint;
    private final int mPaddingStart;
    private final int mPaddingEnd;

    @NonNull
    private final Path mPath = new Path();

    @NonNull
    private final Matrix mTempMatrix = new Matrix();

    Md2PopupBackground(@NonNull final Context context) {
        mPaint = new Paint();
        mPaint.setAntiAlias(true);
        mPaint.setColor(FastScroller.getColorInt(context, R.attr.appFsOverlayBackgroundColor));
        mPaint.setStyle(Paint.Style.FILL);
        final Resources resources = context.getResources();
        mPaddingStart = resources.getDimensionPixelOffset(R.dimen.afs_md2_popup_padding_start);
        mPaddingEnd = resources.getDimensionPixelOffset(R.dimen.afs_md2_popup_padding_end);
    }

    private static void pathArcTo(@NonNull final Path path,
                                  final float centerX,
                                  final float centerY,
                                  final float radius,
                                  final float startAngle,
                                  final float sweepAngle) {
        path.arcTo(centerX - radius, centerY - radius, centerX + radius, centerY + radius,
                   startAngle, sweepAngle, false);
    }

    @Override
    public void draw(@NonNull final Canvas canvas) {
        canvas.drawPath(mPath, mPaint);
    }

    @Override
    public boolean onLayoutDirectionChanged(final int layoutDirection) {
        updatePath();
        return true;
    }

    @Override
    public void setAlpha(final int alpha) {
    }

    @Override
    public void setColorFilter(@Nullable final ColorFilter colorFilter) {
    }

    @Override
    public boolean isAutoMirrored() {
        return true;
    }

    private boolean isLayoutRTL() {
        return getLayoutDirection() == View.LAYOUT_DIRECTION_RTL;
    }

    @Override
    public int getOpacity() {
        return PixelFormat.TRANSLUCENT;
    }

    @Override
    protected void onBoundsChange(@NonNull final Rect bounds) {
        updatePath();
    }

    private void updatePath() {

        mPath.reset();

        final Rect bounds = getBounds();
        float width = bounds.width();
        final float height = bounds.height();
        final float r = height / 2;
        final float sqrt2 = (float) Math.sqrt(2);
        // Ensure we are convex.
        width = Math.max(r + sqrt2 * r, width);
        pathArcTo(mPath, r, r, r, 90, 180);
        final float o1X = width - sqrt2 * r;
        pathArcTo(mPath, o1X, r, r, -90, 45f);
        final float r2 = r / 5;
        final float o2X = width - sqrt2 * r2;
        pathArcTo(mPath, o2X, r, r2, -45, 90);
        pathArcTo(mPath, o1X, r, r, 45f, 45f);
        mPath.close();

        if (isLayoutRTL()) {
            mTempMatrix.setScale(-1, 1, width / 2, 0);
        } else {
            mTempMatrix.reset();
        }
        mTempMatrix.postTranslate(bounds.left, bounds.top);
        mPath.transform(mTempMatrix);
    }

    @Override
    public boolean getPadding(@NonNull final Rect padding) {
        if (isLayoutRTL()) {
            padding.set(mPaddingEnd, 0, mPaddingStart, 0);
        } else {
            padding.set(mPaddingStart, 0, mPaddingEnd, 0);
        }
        return true;
    }

    @Override
    public void getOutline(@NonNull final Outline outline) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q && !mPath.isConvex()) {
            // The outline path must be convex before Q, but we may run into floating point error
            // caused by calculation involving sqrt(2) or OEM implementation difference, so in this
            // case we just omit the shadow instead of crashing.
            super.getOutline(outline);
            return;
        }
        outline.setConvexPath(mPath);
    }
}
