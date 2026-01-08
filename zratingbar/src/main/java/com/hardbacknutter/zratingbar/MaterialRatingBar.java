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

/*
 * Copyright (c) 2016 Zhang Hai <Dreaming.in.Code.ZH@Gmail.com>
 * All Rights Reserved.
 */

package com.hardbacknutter.zratingbar;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.res.ColorStateList;
import android.content.res.TypedArray;
import android.graphics.PorterDuff;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.LayerDrawable;
import android.util.AttributeSet;
import android.view.View;
import android.widget.RatingBar;

import androidx.annotation.Keep;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/**
 * "@Keep" is REQUIRED!
 */
// AppCompatRatingBar will add undesired measuring behaviour.
@SuppressLint("AppCompatCustomView")
@Keep
@SuppressWarnings("WeakerAccess")
public class MaterialRatingBar
        extends RatingBar {

    private static final float FLOAT_EPSILON = 0.1f;
    private final TintInfo tintInfo = new TintInfo();

    private MaterialRatingDrawable drawable;

    @Nullable
    private OnRatingChangeListener onRatingChangeListener;
    private float lastKnownRating;

    public MaterialRatingBar(@NonNull final Context context) {
        super(context);

        init(null, 0);
    }

    public MaterialRatingBar(@NonNull final Context context,
                             @Nullable final AttributeSet attrs) {
        super(context, attrs);

        init(attrs, 0);
    }

    public MaterialRatingBar(@NonNull final Context context,
                             @Nullable final AttributeSet attrs,
                             final int defStyleAttr) {
        super(context, attrs, defStyleAttr);

        init(attrs, defStyleAttr);
    }

    private void init(@Nullable final AttributeSet attrs,
                      final int defStyleAttr) {

        final Context context = getContext();
        final TypedArray a = context.obtainStyledAttributes(attrs, R.styleable.MaterialRatingBar,
                                                            defStyleAttr, 0);
        if (a.hasValue(R.styleable.MaterialRatingBar_mrb_progressTint)) {
            tintInfo.progressTintList =
                    getColorStateList(a, R.styleable.MaterialRatingBar_mrb_progressTint);
            tintInfo.hasProgressTintList = true;
        }
        if (a.hasValue(R.styleable.MaterialRatingBar_mrb_progressTintMode)) {
            tintInfo.progressTintMode = parseTintMode(a.getInt(
                    R.styleable.MaterialRatingBar_mrb_progressTintMode, -1));
            tintInfo.hasProgressTintMode = true;
        }
        if (a.hasValue(R.styleable.MaterialRatingBar_mrb_secondaryProgressTint)) {
            tintInfo.secondaryProgressTintList =
                    getColorStateList(a, R.styleable.MaterialRatingBar_mrb_secondaryProgressTint);
            tintInfo.hasSecondaryProgressTintList = true;
        }
        if (a.hasValue(R.styleable.MaterialRatingBar_mrb_secondaryProgressTintMode)) {
            tintInfo.secondaryProgressTintMode = parseTintMode(a.getInt(
                    R.styleable.MaterialRatingBar_mrb_secondaryProgressTintMode, -1));
            tintInfo.hasSecondaryProgressTintMode = true;
        }
        if (a.hasValue(R.styleable.MaterialRatingBar_mrb_progressBackgroundTint)) {
            tintInfo.progressBackgroundTintList =
                    getColorStateList(a, R.styleable.MaterialRatingBar_mrb_progressBackgroundTint);
            tintInfo.hasProgressBackgroundTintList = true;
        }
        if (a.hasValue(R.styleable.MaterialRatingBar_mrb_progressBackgroundTintMode)) {
            tintInfo.progressBackgroundTintMode = parseTintMode(a.getInt(
                    R.styleable.MaterialRatingBar_mrb_progressBackgroundTintMode, -1));
            tintInfo.hasProgressBackgroundTintMode = true;
        }
        if (a.hasValue(R.styleable.MaterialRatingBar_mrb_indeterminateTint)) {
            tintInfo.indeterminateTintList =
                    getColorStateList(a, R.styleable.MaterialRatingBar_mrb_indeterminateTint);
            tintInfo.hasIndeterminateTintList = true;
        }
        if (a.hasValue(R.styleable.MaterialRatingBar_mrb_indeterminateTintMode)) {
            tintInfo.indeterminateTintMode = parseTintMode(a.getInt(
                    R.styleable.MaterialRatingBar_mrb_indeterminateTintMode, -1));
            tintInfo.hasIndeterminateTintMode = true;
        }
        final boolean fillBackgroundStars = a.getBoolean(
                R.styleable.MaterialRatingBar_mrb_fillBackgroundStars, isIndicator());
        a.recycle();

        drawable = new MaterialRatingDrawable(getContext(), fillBackgroundStars);
        drawable.setStarCount(getNumStars());
        setProgressDrawable(drawable);
    }

    @Override
    public void setNumStars(final int numStars) {
        super.setNumStars(numStars);

        // drawable can be null during super class initialisation.
        if (drawable != null) {
            drawable.setStarCount(numStars);
        }
    }

    @Override
    protected synchronized void onMeasure(final int widthMeasureSpec,
                                          final int heightMeasureSpec) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec);

        final int height = getMeasuredHeight();
        final int width = Math.round(height * drawable.getTileRatio() * getNumStars());
        setMeasuredDimension(View.resolveSizeAndState(width, widthMeasureSpec, 0), height);
    }

    @Override
    public void setProgressDrawable(final Drawable d) {
        super.setProgressDrawable(d);

        // progressTintInfo can be null during super class initialisation.
        if (tintInfo != null) {
            applyProgressTints();
        }
    }

    @Override
    public void setIndeterminateDrawable(final Drawable d) {
        super.setIndeterminateDrawable(d);

        // progressTintInfo can be null during super class initialisation.
        if (tintInfo != null) {
            applyIndeterminateTint();
        }
    }

    @Nullable
    @Override
    public ColorStateList getProgressTintList() {
        // Samsung Android 10 might call this in super class constructor.
        if (tintInfo == null) {
            return null;
        }
        return tintInfo.progressTintList;
    }

    @Override
    public void setProgressTintList(@Nullable final ColorStateList tint) {
        tintInfo.progressTintList = tint;
        tintInfo.hasProgressTintList = true;

        applyPrimaryProgressTint();
    }

    /**
     * Parses a {@link PorterDuff.Mode} from a tintMode attribute's enum value.
     */
    @Nullable
    private PorterDuff.Mode parseTintMode(final int value) {
        switch (value) {
            case 3:
                return PorterDuff.Mode.SRC_OVER;
            case 5:
                return PorterDuff.Mode.SRC_IN;
            case 9:
                return PorterDuff.Mode.SRC_ATOP;
            case 14:
                return PorterDuff.Mode.MULTIPLY;
            case 15:
                return PorterDuff.Mode.SCREEN;
            case 16:
                return PorterDuff.Mode.ADD;
            default:
                return null;
        }
    }

    @Nullable
    private ColorStateList getColorStateList(@NonNull final TypedArray a,
                                             final int index) {
        if (a.hasValue(index)) {
            final int resourceId = a.getResourceId(index, 0);
            if (resourceId != 0) {
                return getContext().getResources()
                                   .getColorStateList(resourceId, getContext().getTheme());
            }
        }
        return a.getColorStateList(index);
    }

    @Nullable
    @Override
    public PorterDuff.Mode getProgressTintMode() {
        return tintInfo.progressTintMode;
    }

    @Override
    public void setProgressTintMode(@Nullable final PorterDuff.Mode tintMode) {
        tintInfo.progressTintMode = tintMode;
        tintInfo.hasProgressTintMode = true;

        applyPrimaryProgressTint();
    }

    @Nullable
    @Override
    public ColorStateList getSecondaryProgressTintList() {
        return tintInfo.secondaryProgressTintList;
    }

    @Override
    public void setSecondaryProgressTintList(@Nullable final ColorStateList tint) {
        tintInfo.secondaryProgressTintList = tint;
        tintInfo.hasSecondaryProgressTintList = true;

        applySecondaryProgressTint();
    }

    @Nullable
    @Override
    public PorterDuff.Mode getSecondaryProgressTintMode() {
        return tintInfo.secondaryProgressTintMode;
    }

    @Override
    public void setSecondaryProgressTintMode(@Nullable final PorterDuff.Mode tintMode) {
        tintInfo.secondaryProgressTintMode = tintMode;
        tintInfo.hasSecondaryProgressTintMode = true;

        applySecondaryProgressTint();
    }

    @Nullable
    @Override
    public ColorStateList getProgressBackgroundTintList() {
        return tintInfo.progressBackgroundTintList;
    }

    @Override
    public void setProgressBackgroundTintList(@Nullable final ColorStateList tint) {
        tintInfo.progressBackgroundTintList = tint;
        tintInfo.hasProgressBackgroundTintList = true;

        applyProgressBackgroundTint();
    }

    @Nullable
    @Override
    public PorterDuff.Mode getProgressBackgroundTintMode() {
        return tintInfo.progressBackgroundTintMode;
    }

    @Override
    public void setProgressBackgroundTintMode(@Nullable final PorterDuff.Mode tintMode) {
        tintInfo.progressBackgroundTintMode = tintMode;
        tintInfo.hasProgressBackgroundTintMode = true;

        applyProgressBackgroundTint();
    }

    @Nullable
    @Override
    public ColorStateList getIndeterminateTintList() {
        return tintInfo.indeterminateTintList;
    }

    @Override
    public void setIndeterminateTintList(@Nullable final ColorStateList tint) {
        tintInfo.indeterminateTintList = tint;
        tintInfo.hasIndeterminateTintList = true;

        applyIndeterminateTint();
    }

    @Nullable
    @Override
    public PorterDuff.Mode getIndeterminateTintMode() {
        return tintInfo.indeterminateTintMode;
    }

    @Override
    public void setIndeterminateTintMode(@Nullable final PorterDuff.Mode tintMode) {
        tintInfo.indeterminateTintMode = tintMode;
        tintInfo.hasIndeterminateTintMode = true;

        applyIndeterminateTint();
    }

    private void applyProgressTints() {
        if (getProgressDrawable() == null) {
            return;
        }
        applyPrimaryProgressTint();
        applyProgressBackgroundTint();
        applySecondaryProgressTint();
    }

    private void applyPrimaryProgressTint() {
        if (getProgressDrawable() == null) {
            return;
        }
        if (tintInfo.hasProgressTintList || tintInfo.hasProgressTintMode) {
            final Drawable target = getTintTargetFromProgressDrawable(android.R.id.progress, true);
            if (target != null) {
                applyTintForDrawable(target, tintInfo.progressTintList,
                                     tintInfo.hasProgressTintList,
                                     tintInfo.progressTintMode,
                                     tintInfo.hasProgressTintMode);
            }
        }
    }

    private void applySecondaryProgressTint() {
        if (getProgressDrawable() == null) {
            return;
        }
        if (tintInfo.hasSecondaryProgressTintList
            || tintInfo.hasSecondaryProgressTintMode) {
            final Drawable target = getTintTargetFromProgressDrawable(
                    android.R.id.secondaryProgress,
                    false);
            if (target != null) {
                applyTintForDrawable(target, tintInfo.secondaryProgressTintList,
                                     tintInfo.hasSecondaryProgressTintList,
                                     tintInfo.secondaryProgressTintMode,
                                     tintInfo.hasSecondaryProgressTintMode);
            }
        }
    }

    private void applyProgressBackgroundTint() {
        if (getProgressDrawable() == null) {
            return;
        }
        if (tintInfo.hasProgressBackgroundTintList
            || tintInfo.hasProgressBackgroundTintMode) {
            final Drawable target = getTintTargetFromProgressDrawable(android.R.id.background,
                                                                      false);
            if (target != null) {
                applyTintForDrawable(target, tintInfo.progressBackgroundTintList,
                                     tintInfo.hasProgressBackgroundTintList,
                                     tintInfo.progressBackgroundTintMode,
                                     tintInfo.hasProgressBackgroundTintMode);
            }
        }
    }

    @Nullable
    private Drawable getTintTargetFromProgressDrawable(final int layerId,
                                                       final boolean shouldFallback) {
        final Drawable progressDrawable = getProgressDrawable();
        if (progressDrawable == null) {
            return null;
        }
        progressDrawable.mutate();
        Drawable layerDrawable = null;
        if (progressDrawable instanceof LayerDrawable) {
            layerDrawable = ((LayerDrawable) progressDrawable).findDrawableByLayerId(layerId);
        }
        if (layerDrawable == null && shouldFallback) {
            layerDrawable = progressDrawable;
        }
        return layerDrawable;
    }

    private void applyIndeterminateTint() {
        final Drawable indeterminateDrawable = getIndeterminateDrawable();
        if (indeterminateDrawable == null) {
            return;
        }
        if (tintInfo.hasIndeterminateTintList
            || tintInfo.hasIndeterminateTintMode) {
            indeterminateDrawable.mutate();
            applyTintForDrawable(indeterminateDrawable, tintInfo.indeterminateTintList,
                                 tintInfo.hasIndeterminateTintList,
                                 tintInfo.indeterminateTintMode,
                                 tintInfo.hasIndeterminateTintMode);
        }
    }

    // Progress drawables in this library has already rewritten tint related methods for
    // compatibility.
    private void applyTintForDrawable(@NonNull final Drawable drawable,
                                      @Nullable final ColorStateList tintList,
                                      final boolean hasTintList,
                                      @Nullable final PorterDuff.Mode tintMode,
                                      final boolean hasTintMode) {

        if (hasTintList || hasTintMode) {

            if (hasTintList) {
                drawable.setTintList(tintList);
            }

            if (hasTintMode) {
                drawable.setTintMode(tintMode);
            }

            // The drawable (or one of its children) may not have been
            // stateful before applying the tint, so let's try again.
            if (drawable.isStateful()) {
                drawable.setState(getDrawableState());
            }
        }
    }

    /**
     * Get the listener that is listening for rating change events.
     *
     * @return The listener, may be null.
     */
    @Nullable
    public OnRatingChangeListener getOnRatingChangeListener() {
        return onRatingChangeListener;
    }

    /**
     * Sets the listener to be called when the rating changes.
     *
     * @param listener The listener.
     */
    public void setOnRatingChangeListener(@Nullable final OnRatingChangeListener listener) {
        onRatingChangeListener = listener;
    }

    @Override
    public synchronized void setSecondaryProgress(final int secondaryProgress) {
        super.setSecondaryProgress(secondaryProgress);

        // HACK: Check and call our listener here because this method is always called by
        // updateSecondaryProgress() from onProgressRefresh().
        final float rating = getRating();
        if (onRatingChangeListener != null && Math.abs(rating - lastKnownRating) < FLOAT_EPSILON) {
            onRatingChangeListener.onRatingChanged(this, rating);
        }
        lastKnownRating = rating;
    }

    /**
     * A callback that notifies clients when the rating has been changed.
     * This includes changes that were initiated by the user through a touch
     * gesture or arrow key/trackball as well as changes that were initiated
     * programmatically. This callback <strong>will</strong> be called
     * continuously while the user is dragging, different from framework's
     * {@link OnRatingBarChangeListener}.
     */
    @FunctionalInterface
    public interface OnRatingChangeListener {

        /**
         * Notification that the rating has changed. This <strong>will</strong> be called
         * continuously while the user is dragging, different from framework's
         * {@link OnRatingBarChangeListener}.
         *
         * @param ratingBar The RatingBar whose rating has changed.
         * @param rating    The current rating. This will be in the range 0..numStars.
         */
        void onRatingChanged(@NonNull MaterialRatingBar ratingBar,
                             float rating);
    }

    /**
     * @noinspection CheckStyle
     */
    private static class TintInfo {

        @Nullable
        public ColorStateList progressTintList;
        @Nullable
        public PorterDuff.Mode progressTintMode;
        public boolean hasProgressTintList;
        public boolean hasProgressTintMode;

        @Nullable
        public ColorStateList secondaryProgressTintList;
        @Nullable
        public PorterDuff.Mode secondaryProgressTintMode;
        public boolean hasSecondaryProgressTintList;
        public boolean hasSecondaryProgressTintMode;

        @Nullable
        public ColorStateList progressBackgroundTintList;
        @Nullable
        public PorterDuff.Mode progressBackgroundTintMode;
        public boolean hasProgressBackgroundTintList;
        public boolean hasProgressBackgroundTintMode;

        @Nullable
        public ColorStateList indeterminateTintList;
        @Nullable
        public PorterDuff.Mode indeterminateTintMode;
        public boolean hasIndeterminateTintList;
        public boolean hasIndeterminateTintMode;
    }
}
