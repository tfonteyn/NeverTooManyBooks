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
package com.hardbacknutter.nevertoomanybooks.fields.endicon;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.AnimatorSet;
import android.animation.TimeInterpolator;
import android.animation.ValueAnimator;
import android.content.SharedPreferences;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.view.animation.LinearInterpolator;
import android.widget.EditText;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.interpolator.view.animation.LinearOutSlowInInterpolator;

import com.google.android.material.internal.CheckableImageButton;
import com.google.android.material.textfield.TextInputLayout;

import java.util.Objects;
import java.util.function.Consumer;

import com.hardbacknutter.nevertoomanybooks.R;
import com.hardbacknutter.nevertoomanybooks.fields.Field;

/**
 * https://github.com/material-components/material-components-android/pull/2025
 * generic input field with clear-text icon at the end. -
 * <p>
 * Most of the code in this class was copied from:
 * com.google.android.material.textfield.ClearTextEndIconDelegate
 */
public class ExtClearTextEndIconDelegate
        implements ExtEndIconDelegate {

    private static final TimeInterpolator LINEAR_INTERPOLATOR = new LinearInterpolator();
    private static final TimeInterpolator LINEAR_OUT_SLOW_IN_INTERPOLATOR =
            new LinearOutSlowInInterpolator();
    private static final int ANIMATION_FADE_DURATION = 100;
    private static final int ANIMATION_SCALE_DURATION = 150;
    private static final float ANIMATION_SCALE_FROM_VALUE = 0.8f;

    private TextInputLayout textInputLayout;
    private final TextWatcher clearTextEndIconTextWatcher = new TextWatcher() {
        @Override
        public void beforeTextChanged(final CharSequence s,
                                      final int start,
                                      final int count,
                                      final int after) {
        }

        @Override
        public void onTextChanged(final CharSequence s,
                                  final int start,
                                  final int before,
                                  final int count) {
        }

        @Override
        public void afterTextChanged(@NonNull final Editable s) {
            if (textInputLayout.getSuffixText() != null) {
                return;
            }
            animateIcon(shouldBeVisible());
        }
    };
    private AnimatorSet iconInAnim;
    private ValueAnimator iconOutAnim;
    private CheckableImageButton endIconView;

    private final View.OnFocusChangeListener onFocusChangeListener =
            (v, hasFocus) -> animateIcon(shouldBeVisible());

    private final TextInputLayout.OnEditTextAttachedListener clearTextOnEditTextAttachedListener =
            new TextInputLayout.OnEditTextAttachedListener() {
                @Override
                public void onEditTextAttached(@NonNull final TextInputLayout textInputLayout) {
                    final EditText editText = textInputLayout.getEditText();
                    textInputLayout.setEndIconVisible(shouldBeVisible());
                    //noinspection ConstantConditions
                    editText.setOnFocusChangeListener(onFocusChangeListener);
                    endIconView.setOnFocusChangeListener(onFocusChangeListener);
                    // Make sure there's always only one clear text text watcher added
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
                        // Remove any listeners set on the edit text.
                        editText.post(() -> {
                            editText.removeTextChangedListener(
                                    clearTextEndIconTextWatcher);
                            // Make sure icon view is visible.
                            animateIcon(/* show= */ true);
                        });
                        if (editText.getOnFocusChangeListener() == onFocusChangeListener) {
                            editText.setOnFocusChangeListener(null);
                        }
                        if (endIconView.getOnFocusChangeListener() == onFocusChangeListener) {
                            endIconView.setOnFocusChangeListener(null);
                        }
                    }
                }
            };
    @Nullable
    private Consumer<View> endIconOnClickConsumer;

    @Override
    public void setOnClickConsumer(@Nullable final Consumer<View> endIconOnClickConsumer) {
        this.endIconOnClickConsumer = endIconOnClickConsumer;
    }

    /** Called from {@link Field#setParentView(View, SharedPreferences)} */
    @Override
    public void setTextInputLayout(@NonNull final TextInputLayout til) {
        textInputLayout = til;
        endIconView = textInputLayout.findViewById(R.id.text_input_end_icon);
        Objects.requireNonNull(endIconView, "NOT FOUND: R.id.text_input_end_icon");

        textInputLayout.setEndIconMode(TextInputLayout.END_ICON_CUSTOM);
        if (textInputLayout.getEndIconDrawable() == null) {
            textInputLayout.setEndIconDrawable(R.drawable.ic_baseline_cancel_24);
        }
        textInputLayout.setEndIconContentDescription(
                textInputLayout.getResources().getText(R.string.cd_clear_text_end_icon));
        textInputLayout.setEndIconCheckable(false);
        textInputLayout.setEndIconOnClickListener(v -> {
            if (endIconOnClickConsumer != null) {
                endIconOnClickConsumer.accept(v);
            } else {
                //noinspection ConstantConditions
                final Editable text = textInputLayout.getEditText().getText();
                if (text != null) {
                    text.clear();
                }
            }
            textInputLayout.refreshEndIconDrawableState();
        });
        textInputLayout.addOnEditTextAttachedListener(clearTextOnEditTextAttachedListener);
        textInputLayout.addOnEndIconChangedListener(endIconChangedListener);

        initAnimators();
    }

    /** Called from {@link Field#setValue(Object)} */
    @Override
    public void updateEndIcon() {
        textInputLayout.setEndIconVisible(shouldBeVisible());
    }

    private void initAnimators() {
        final ValueAnimator scaleAnimator = getScaleAnimator();
        final ValueAnimator fadeAnimator = getAlphaAnimator(0, 1);
        iconInAnim = new AnimatorSet();
        iconInAnim.playTogether(scaleAnimator, fadeAnimator);
        iconInAnim.addListener(
                new AnimatorListenerAdapter() {
                    @Override
                    public void onAnimationStart(final Animator animation) {
                        textInputLayout.setEndIconVisible(true);
                    }
                });
        iconOutAnim = getAlphaAnimator(1, 0);
        iconOutAnim.addListener(
                new AnimatorListenerAdapter() {
                    @Override
                    public void onAnimationEnd(final Animator animation) {
                        textInputLayout.setEndIconVisible(false);
                    }
                });
    }

    private void animateIcon(final boolean show) {
        final boolean shouldSkipAnimation = textInputLayout.isEndIconVisible() == show;
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

    @NonNull
    private ValueAnimator getAlphaAnimator(final float... values) {
        final ValueAnimator animator = ValueAnimator.ofFloat(values);
        animator.setInterpolator(LINEAR_INTERPOLATOR);
        animator.setDuration(ANIMATION_FADE_DURATION);
        animator.addUpdateListener(animation -> {
            final float alpha = (float) animation.getAnimatedValue();
            endIconView.setAlpha(alpha);
        });

        return animator;
    }

    @NonNull
    private ValueAnimator getScaleAnimator() {
        final ValueAnimator animator = ValueAnimator.ofFloat(ANIMATION_SCALE_FROM_VALUE, 1);
        animator.setInterpolator(LINEAR_OUT_SLOW_IN_INTERPOLATOR);
        animator.setDuration(ANIMATION_SCALE_DURATION);
        animator.addUpdateListener(animation -> {
            final float scale = (float) animation.getAnimatedValue();
            endIconView.setScaleX(scale);
            endIconView.setScaleY(scale);
        });
        return animator;
    }

    private boolean shouldBeVisible() {
        final EditText editText = textInputLayout.getEditText();
        return editText != null && editText.getText().length() > 0;
    }

}
