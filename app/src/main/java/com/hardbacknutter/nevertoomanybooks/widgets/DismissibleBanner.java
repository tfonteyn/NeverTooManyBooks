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

import androidx.annotation.ColorInt;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

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
        super(context);
        init(context, null);
    }

    public DismissibleBanner(@NonNull final Context context,
                             @Nullable final AttributeSet attrs) {
        super(context, attrs, com.google.android.material.R.attr.materialCardViewStyle);
        init(context, attrs);
    }

    public DismissibleBanner(@NonNull final Context context,
                             @Nullable final AttributeSet attrs,
                             final int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init(context, attrs);
    }

    private void init(@NonNull final Context context,
                      @Nullable final AttributeSet attrs) {
        // Inflate the layout and attach it to the parent!
        LayoutInflater.from(context).inflate(R.layout.view_dismissible_banner, this, true);

        textView = findViewById(R.id.bannerText);
        btnClose = findViewById(R.id.btnCloseBanner);

        btnClose.setOnClickListener(v -> {
            setVisibility(View.GONE);
            if (dismissListener != null) {
                dismissListener.onDismissed(this);
            }
        });

        // Parse custom XML attributes if they exist
        if (attrs != null) {
            final TypedArray typedArray = context
                    .obtainStyledAttributes(attrs, R.styleable.DismissibleBanner);

            final String text = typedArray.getString(R.styleable.DismissibleBanner_android_text);
            if (text != null) {
                textView.setText(text);
            }
            typedArray.recycle();
        }
    }

    public void setText(@Nullable final CharSequence text) {
        textView.setText(text);
    }

    public void setTextColor(@ColorInt final int color) {
        textView.setTextColor(color);
    }

    public void setOnDismissListener(@Nullable final OnDismissListener listener) {
        this.dismissListener = listener;
    }

    public interface OnDismissListener {
        void onDismissed(@NonNull DismissibleBanner v);
    }
}
