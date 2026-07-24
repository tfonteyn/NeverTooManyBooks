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

package com.hardbacknutter.nevertoomanybooks.widgets;

import android.content.Context;
import android.content.res.TypedArray;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.TextView;

import androidx.annotation.AttrRes;
import androidx.annotation.ColorInt;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StyleRes;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;

import com.hardbacknutter.nevertoomanybooks.R;

public class DismissibleBanner
        extends MaterialCardView {

    private TextView textView;
    private MaterialButton btnClose;

    @Nullable
    private OnDismissListener dismissListener;

    public DismissibleBanner(@NonNull final Context context) {
        this(context, null);
    }

    public DismissibleBanner(@NonNull final Context context,
                             @Nullable final AttributeSet attrs) {
        this(context, attrs, com.google.android.material.R.attr.materialCardViewStyle);
    }

    public DismissibleBanner(@NonNull final Context context,
                             @Nullable final AttributeSet attrs,
                             @AttrRes final int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init(context, attrs, defStyleAttr);
    }

    private void init(@NonNull final Context context,
                      @Nullable final AttributeSet attrs,
                      @AttrRes final int defStyleAttr) {
        // Inflate the layout and attach it to the parent!
        LayoutInflater.from(context).inflate(R.layout.view_dismissible_banner, this, true);

        textView = findViewById(R.id.bannerText);
        if (attrs != null) {
            final TypedArray typedArray = context.obtainStyledAttributes(
                    attrs, R.styleable.DismissibleBanner, defStyleAttr, 0);

            try {
                if (typedArray.hasValue(R.styleable.DismissibleBanner_android_text)) {
                    textView.setText(typedArray.getString(
                            R.styleable.DismissibleBanner_android_text));
                }
                final int textAppearanceResId = typedArray.getResourceId(
                        R.styleable.DismissibleBanner_android_textAppearance, 0);
                if (textAppearanceResId != 0) {
                    textView.setTextAppearance(textAppearanceResId);
                }
            } finally {
                typedArray.recycle();
            }
        }

        btnClose = findViewById(R.id.btnCloseBanner);
        btnClose.setOnClickListener(this::dismiss);
    }

    /**
     * Dismiss the banner.
     */
    public void dismiss() {
        dismiss(null);
    }

    private void dismiss(@Nullable final View v) {
        setVisibility(View.GONE);
        if (dismissListener != null) {
            dismissListener.onDismissed(this, (MaterialButton) v);
        }
    }

    public void showCloseButton(final boolean visible) {
        btnClose.setVisibility(visible ? View.VISIBLE : View.GONE);
    }

    /**
     * Set the text on the TextView.
     *
     * @param text text to be displayed
     *
     * @see TextView#setText(int)
     */
    public void setText(@Nullable final CharSequence text) {
        textView.setText(text);
    }

    /**
     * Set the colour for the text in the TextView.
     *
     * @param color A color value in the form 0xAARRGGBB.
     *              Do not pass a resource ID. To get a color value from a resource ID, call
     *              {@link androidx.core.content.ContextCompat#getColor(Context, int) getColor}.
     *
     * @see TextView#setTextColor(int)
     */
    public void setTextColor(@ColorInt final int color) {
        textView.setTextColor(color);
    }

    /**
     * Set the text style for the TextView.
     *
     * @param resId the resource identifier of the style to apply
     *
     * @see TextView#setTextAppearance(int)
     */
    public void setTextAppearance(@StyleRes final int resId) {
        textView.setTextAppearance(resId);
    }

    /**
     * Set a listener to be notified when the user taps the close-button.
     *
     * @param listener to use
     */
    public void setOnDismissListener(@Nullable final OnDismissListener listener) {
        this.dismissListener = listener;
    }

    /**
     * Listener interface for {@link #setOnDismissListener(OnDismissListener)}.
     */
    @FunctionalInterface
    public interface OnDismissListener {

        /**
         * Called when the banner is dismissed.
         *
         * @param banner The {@link DismissibleBanner}
         * @param button The button tapped, or {@code null} if {@link #dismiss()} was called.
         */
        void onDismissed(@NonNull DismissibleBanner banner,
                         @Nullable MaterialButton button);
    }
}
