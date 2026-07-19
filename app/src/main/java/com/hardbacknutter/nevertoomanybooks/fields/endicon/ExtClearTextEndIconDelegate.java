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
package com.hardbacknutter.nevertoomanybooks.fields.endicon;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.AnimatorSet;
import android.animation.TimeInterpolator;
import android.animation.ValueAnimator;
import android.content.Context;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.view.animation.LinearInterpolator;
import android.widget.EditText;
import android.widget.ImageButton;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.interpolator.view.animation.LinearOutSlowInInterpolator;

import com.google.android.material.motion.MotionUtils;
import com.google.android.material.textfield.TextInputLayout;

import java.util.Objects;

import com.hardbacknutter.nevertoomanybooks.R;
import com.hardbacknutter.nevertoomanybooks.core.widgets.ExtTextWatcher;
import com.hardbacknutter.nevertoomanybooks.fields.MultiOnFocusChangeListener;

/**
 * <p>
 * The animation related code in this class was copied from material 1.12.0 library
 * {@code com.google.android.material.textfield.ClearTextEndIconDelegate}
 *
 * @see <a href="https://github.com/material-components/material-components-android/blob/master/lib/java/com/google/android/material/textfield/ClearTextEndIconDelegate.java">
 *         ClearTextEndIconDelegate.java</a>
 * @see <a href="https://github.com/material-components/material-components-android/pull/2025">
 *         generic input field with clear-text icon at the end.</a>
 */
public class ExtClearTextEndIconDelegate
        implements ExtEndIconDelegate {

    private static final TimeInterpolator LINEAR_INTERPOLATOR = new LinearInterpolator();
    private static final TimeInterpolator LINEAR_OUT_SLOW_IN_INTERPOLATOR =
            new LinearOutSlowInInterpolator();

    private static final int DEFAULT_ANIMATION_FADE_DURATION = 100;
    private static final int DEFAULT_ANIMATION_SCALE_DURATION = 150;
    private static final float ANIMATION_SCALE_FROM_VALUE = 0.8f;
    private final int animationFadeDuration;
    private final int animationScaleDuration;
    @NonNull
    private final TimeInterpolator animationFadeInterpolator;
    @NonNull
    private final TimeInterpolator animationScaleInterpolator;

    @Nullable
    private MultiOnFocusChangeListener onFocusChangeListener;

    private TextInputLayout endLayout;

    private AnimatorSet iconInAnim;
    private ValueAnimator iconOutAnim;

    private final TextWatcher clearTextEndIconTextWatcher = new ExtTextWatcher() {
        @Override
        public void afterTextChanged(@NonNull final Editable s) {
            if (endLayout.getSuffixText() != null) {
                return;
            }
            animateIcon(shouldBeVisible());
        }
    };

    private final View.OnFocusChangeListener fclIconAnimator =
            (v, hasFocus) -> animateIcon(shouldBeVisible());

    private ImageButton endIconView;

    private final TextInputLayout.OnEditTextAttachedListener clearTextOnEditTextAttachedListener =
            new TextInputLayout.OnEditTextAttachedListener() {
                @Override
                public void onEditTextAttached(@NonNull final TextInputLayout textInputLayout) {
                    final EditText editText = Objects.requireNonNull(textInputLayout.getEditText());
                    textInputLayout.setEndIconVisible(shouldBeVisible());
                    if (onFocusChangeListener != null) {
                        onFocusChangeListener.addOnFocusChangeListener(fclIconAnimator);
                    } else {
                        editText.setOnFocusChangeListener(fclIconAnimator);
                    }

                    endIconView.setOnFocusChangeListener(fclIconAnimator);
                    // Make sure there's always only one clear text watcher added
                    editText.removeTextChangedListener(clearTextEndIconTextWatcher);
                    editText.addTextChangedListener(clearTextEndIconTextWatcher);
                }
            };

    private final TextInputLayout.OnEndIconChangedListener endIconChangedListener =
            new TextInputLayout.OnEndIconChangedListener() {
                @Override
                public void onEndIconChanged(@NonNull final TextInputLayout textInputLayout,
                                             final int previousIcon) {
                    final EditText editText = textInputLayout.getEditText();
                    if (editText != null && previousIcon == TextInputLayout.END_ICON_CLEAR_TEXT) {
                        editText.post(() -> {
                            // Remove our listener from the edit text.
                            editText.removeTextChangedListener(clearTextEndIconTextWatcher);
                            // Make sure icon view is visible.
                            animateIcon(true);
                        });

                        if (onFocusChangeListener != null) {
                            onFocusChangeListener.removeOnFocusChangeListener(fclIconAnimator);
                        } else {
                            if (editText.getOnFocusChangeListener() == fclIconAnimator) {
                                editText.setOnFocusChangeListener(null);
                            }
                        }
                        if (endIconView.getOnFocusChangeListener() == fclIconAnimator) {
                            endIconView.setOnFocusChangeListener(null);
                        }
                    }
                }
            };

    /** The listener as set by the delegate user. */
    @Nullable
    private View.OnClickListener endIconOnClickListener;
    /**
     * The local listener which will forward to {@link #endIconOnClickListener} if set,
     * or handle locally if not set.
     */
    private final View.OnClickListener onEndIconClickListener = view -> {
        if (endIconOnClickListener != null) {
            endIconOnClickListener.onClick(view);
        } else {
            //noinspection DataFlowIssue
            final Editable text = endLayout.getEditText().getText();
            if (text != null) {
                text.clear();
            }
        }
        endLayout.refreshEndIconDrawableState();
    };

    /**
     * Private Constructor.
     *
     * @param context Current context
     */
    private ExtClearTextEndIconDelegate(@NonNull final Context context) {
        animationFadeDuration = MotionUtils.resolveThemeDuration(
                context, com.google.android.material.R.attr.motionDurationShort3,
                DEFAULT_ANIMATION_FADE_DURATION);
        animationScaleDuration = MotionUtils.resolveThemeDuration(
                context, com.google.android.material.R.attr.motionDurationShort3,
                DEFAULT_ANIMATION_SCALE_DURATION);
        animationFadeInterpolator = MotionUtils.resolveThemeInterpolator(
                context, com.google.android.material.R.attr.motionEasingLinearInterpolator,
                LINEAR_INTERPOLATOR);
        animationScaleInterpolator = MotionUtils.resolveThemeInterpolator(
                context, com.google.android.material.R.attr.motionEasingEmphasizedInterpolator,
                LINEAR_OUT_SLOW_IN_INTERPOLATOR);
    }

    /**
     * Constructor.
     *
     * @param til      to attach a new instance of the delegate to
     * @param listener for click events;
     *                 use {@code null} for the default action: clear the field.
     *
     * @return delegate for optional further configuration
     */
    @NonNull
    public static ExtClearTextEndIconDelegate attach(
            @NonNull final TextInputLayout til,
            @Nullable final View.OnClickListener listener) {
        final ExtClearTextEndIconDelegate delegate = new ExtClearTextEndIconDelegate(
                til.getContext());
        delegate.setEndIconOnClickListener(listener);
        delegate.setTextInputLayout(til);
        return delegate;
    }

    public void setOnFocusChangeListener(@Nullable final MultiOnFocusChangeListener listener) {
        this.onFocusChangeListener = listener;
    }

    @Override
    public void setEndIconOnClickListener(@Nullable final View.OnClickListener
                                                  endIconOnClickListener) {
        this.endIconOnClickListener = endIconOnClickListener;
    }

    @Override
    public void setTextInputLayout(@NonNull final TextInputLayout til) {
        endLayout = til;
        endIconView = endLayout
                .findViewById(com.google.android.material.R.id.text_input_end_icon);
        Objects.requireNonNull(endIconView, "NOT FOUND: R.id.text_input_end_icon");

        endLayout.setEndIconMode(TextInputLayout.END_ICON_CUSTOM);
        if (endLayout.getEndIconDrawable() == null) {
            endLayout.setEndIconDrawable(R.drawable.end_icon_clear);
        }
        endLayout.setEndIconContentDescription(R.string.cd_clear_text_end_icon);
        endLayout.setEndIconCheckable(false);
        endLayout.setEndIconOnClickListener(onEndIconClickListener);
        endLayout.addOnEditTextAttachedListener(clearTextOnEditTextAttachedListener);
        endLayout.addOnEndIconChangedListener(endIconChangedListener);

        initAnimators();
    }

    @Override
    public void updateEndIcon() {
        endLayout.setEndIconVisible(shouldBeVisible());
    }


    // copied from material ClearTextEndIconDelegate 1.12.0
    private void animateIcon(final boolean show) {
        final boolean shouldSkipAnimation = endLayout.isEndIconVisible() == show;
        if (show && !iconInAnim.isRunning()) {
            iconOutAnim.cancel();
            iconInAnim.start();
            if (shouldSkipAnimation) {
                iconInAnim.end();
            }
        } else if (!show) {
            iconInAnim.cancel();
            iconOutAnim.start();
            if (shouldSkipAnimation) {
                iconOutAnim.end();
            }
        }
    }

    // copied from material ClearTextEndIconDelegate 1.12.0
    private void initAnimators() {
        final ValueAnimator scaleAnimator = getScaleAnimator();
        final ValueAnimator fadeAnimator = getAlphaAnimator(0, 1);
        iconInAnim = new AnimatorSet();
        iconInAnim.playTogether(scaleAnimator, fadeAnimator);
        iconInAnim.addListener(
                new AnimatorListenerAdapter() {
                    @Override
                    public void onAnimationStart(final Animator animation) {
                        endLayout.setEndIconVisible(true);
                    }
                });
        iconOutAnim = getAlphaAnimator(1, 0);
        iconOutAnim.addListener(
                new AnimatorListenerAdapter() {
                    @Override
                    public void onAnimationEnd(final Animator animation) {
                        endLayout.setEndIconVisible(false);
                    }
                });
    }

    // copied from material ClearTextEndIconDelegate 1.12.0
    @NonNull
    private ValueAnimator getAlphaAnimator(final float... values) {
        final ValueAnimator animator = ValueAnimator.ofFloat(values);
        animator.setInterpolator(animationFadeInterpolator);
        animator.setDuration(animationFadeDuration);
        animator.addUpdateListener(animation -> {
            final float alpha = (float) animation.getAnimatedValue();
            endIconView.setAlpha(alpha);
        });

        return animator;
    }

    // copied from material ClearTextEndIconDelegate 1.12.0
    @NonNull
    private ValueAnimator getScaleAnimator() {
        final ValueAnimator animator = ValueAnimator.ofFloat(ANIMATION_SCALE_FROM_VALUE, 1);
        animator.setInterpolator(animationScaleInterpolator);
        animator.setDuration(animationScaleDuration);
        animator.addUpdateListener(animation -> {
            final float scale = (float) animation.getAnimatedValue();
            endIconView.setScaleX(scale);
            endIconView.setScaleY(scale);
        });
        return animator;
    }

    // copied from material ClearTextEndIconDelegate 1.12.0
    private boolean shouldBeVisible() {
        final EditText editText = endLayout.getEditText();
        // removed the checks on "hasFocus()" from the original code
        // because we WANT it to visible even if it does not have focus
        return editText != null
               // && (editText.hasFocus() || endIconView.hasFocus())
               && editText.getText().length() > 0;
    }
}
