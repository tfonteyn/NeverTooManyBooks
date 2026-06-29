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
package com.hardbacknutter.fastscroller;

import android.animation.TimeInterpolator;
import android.provider.Settings;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.interpolator.view.animation.FastOutLinearInInterpolator;
import androidx.interpolator.view.animation.LinearOutSlowInInterpolator;

/**
 * Original code from <a href="https://github.com/zhanghai/AndroidFastScroll">
 * https://github.com/zhanghai/AndroidFastScroll</a>.
 * <p>
 * Not all methods here as used, but for now keeping this class as-is allowing future
 * use of all features.
 */
class DefaultAnimationHelper
        implements FastScrollerOverlay.AnimationHelper {

    private static final int SHOW_DURATION_MILLIS = 150;
    private static final int HIDE_DURATION_MILLIS = 200;

    private static final TimeInterpolator SHOW_SCROLLBAR_INTERPOLATOR =
            new LinearOutSlowInInterpolator();
    private static final TimeInterpolator HIDE_SCROLLBAR_INTERPOLATOR =
            new FastOutLinearInInterpolator();
    private static final int AUTO_HIDE_SCROLLBAR_DELAY_MILLIS = 1500;

    @NonNull
    private final View view;
    private final long showDurationInMilli;
    private final long hideDurationInMilli;

    private boolean scrollbarAutoHideEnabled = true;

    private boolean showingScrollbar = true;
    private boolean showingPopup;

    DefaultAnimationHelper(@NonNull final View view) {
        this.view = view;

        final float durationScale = Settings.Global.getFloat(
                view.getContext().getContentResolver(),
                Settings.Global.ANIMATOR_DURATION_SCALE, 1.0f);

        showDurationInMilli = (long) (SHOW_DURATION_MILLIS * durationScale);
        hideDurationInMilli = (long) (HIDE_DURATION_MILLIS * durationScale);
    }

    @Override
    public void showScrollbar(@NonNull final View trackView,
                              @NonNull final View thumbView) {

        if (showingScrollbar) {
            return;
        }
        showingScrollbar = true;

        trackView.animate()
                 .alpha(1)
                 .translationX(0)
                 .setDuration(showDurationInMilli)
                 .setInterpolator(SHOW_SCROLLBAR_INTERPOLATOR)
                 .start();
        thumbView.animate()
                 .alpha(1)
                 .translationX(0)
                 .setDuration(showDurationInMilli)
                 .setInterpolator(SHOW_SCROLLBAR_INTERPOLATOR)
                 .start();
    }

    @Override
    public void hideScrollbar(@NonNull final View trackView,
                              @NonNull final View thumbView) {

        if (!showingScrollbar) {
            return;
        }
        showingScrollbar = false;

        final boolean isLayoutRtl = view.getLayoutDirection() == View.LAYOUT_DIRECTION_RTL;
        final int width = Math.max(trackView.getWidth(), thumbView.getWidth());
        final float translationX;
        if (isLayoutRtl) {
            translationX = trackView.getLeft() == 0 ? -width : 0;
        } else {
            translationX = trackView.getRight() == view.getWidth() ? width : 0;
        }
        trackView.animate()
                 .alpha(0)
                 .translationX(translationX)
                 .setDuration(hideDurationInMilli)
                 .setInterpolator(HIDE_SCROLLBAR_INTERPOLATOR)
                 .start();
        thumbView.animate()
                 .alpha(0)
                 .translationX(translationX)
                 .setDuration(hideDurationInMilli)
                 .setInterpolator(HIDE_SCROLLBAR_INTERPOLATOR)
                 .start();
    }

    @Override
    public boolean isScrollbarAutoHideEnabled() {
        return scrollbarAutoHideEnabled;
    }

    public void setScrollbarAutoHideEnabled(final boolean enabled) {
        scrollbarAutoHideEnabled = enabled;
    }

    @Override
    public int getScrollbarAutoHideDelayMillis() {
        return AUTO_HIDE_SCROLLBAR_DELAY_MILLIS;
    }

    @Override
    public void showPopup(@NonNull final View popupView) {

        if (showingPopup) {
            return;
        }
        showingPopup = true;

        popupView.animate()
                 .alpha(1)
                 .setDuration(showDurationInMilli)
                 .start();
    }

    @Override
    public void hidePopup(@NonNull final View popupView) {

        if (!showingPopup) {
            return;
        }
        showingPopup = false;

        popupView.animate()
                 .alpha(0)
                 .setDuration(hideDurationInMilli)
                 .start();
    }
}
