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

package com.hardbacknutter.prefslib.internal;

import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.CallSuper;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.hardbacknutter.prefslib.Setting;

/**
 * The key is stored as a raw tag on the {@code itemView}.
 */
public class SettingViewHolder
        extends RecyclerView.ViewHolder {

    @NonNull
    private final ImageView iconView;
    @NonNull
    private final TextView titleView;
    @NonNull
    private final TextView summaryView;

    SettingViewHolder(@NonNull final View itemView,
                      @NonNull final ImageView iconView,
                      @NonNull final TextView titleView,
                      @NonNull final TextView summaryView) {
        super(itemView);
        this.iconView = iconView;
        this.titleView = titleView;
        this.summaryView = summaryView;
    }

    private static void setViewAndChildrenEnabled(@NonNull final View view,
                                                  final boolean enabled) {
        view.setEnabled(enabled);
        if (view instanceof ViewGroup) {
            final ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) {
                setViewAndChildrenEnabled(group.getChildAt(i), enabled);
            }
        }
    }

    @NonNull
    protected String getKey() {
        return (String) itemView.getTag();
    }

    public void performClick() {
        itemView.performClick();
    }

    @CallSuper
    public void onBind(@NonNull final Setting setting,
                       final boolean enabled) {
        setViewAndChildrenEnabled(itemView, enabled);

        itemView.setTag(setting.getKey());

        if (setting.getIconResId() != 0) {
            iconView.setImageResource(setting.getIconResId());
            iconView.setVisibility(View.VISIBLE);
        } else {
            iconView.setVisibility(View.INVISIBLE);
        }

        titleView.setText(setting.getTitle());

        final CharSequence summary = setting.getSummary(itemView.getContext());
        if (summary != null) {
            summaryView.setText(summary);
            summaryView.setVisibility(View.VISIBLE);
        } else {
            summaryView.setVisibility(View.GONE);
        }
    }
}
