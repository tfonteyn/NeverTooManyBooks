/*
 * @Copyright 2018-2025 HardBackNutter
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

package com.hardbacknutter.nevertoomanybooks.settings.widgets;

import android.content.Context;
import android.content.res.TypedArray;
import android.util.AttributeSet;
import android.view.View;
import android.widget.ImageView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.preference.ListPreference;
import androidx.preference.PreferenceViewHolder;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import com.hardbacknutter.nevertoomanybooks.R;

public class ListPreferenceWithHelp
        extends ListPreference {

    @Nullable
    private String helpText;

    public ListPreferenceWithHelp(@NonNull final Context context) {
        super(context);
    }

    public ListPreferenceWithHelp(@NonNull final Context context,
                                  @Nullable final AttributeSet attrs) {
        super(context, attrs);
        final TypedArray a = context.obtainStyledAttributes(attrs, R.styleable.PreferenceWithInfo);
        helpText = a.getString(R.styleable.PreferenceWithInfo_helpText);
        a.recycle();

        // android:widgetLayout="@layout/preference_info_icon"
        setWidgetLayoutResource(R.layout.preference_widget_help_icon);
    }

    public void setHelpText(@Nullable final String helpText) {
        this.helpText = helpText;
    }

    @Override
    public void onBindViewHolder(@NonNull final PreferenceViewHolder holder) {
        super.onBindViewHolder(holder);

        final ImageView infoIcon = (ImageView) holder.findViewById(R.id.info_icon);
        if (infoIcon != null) {
            infoIcon.setVisibility(View.VISIBLE);
            infoIcon.setOnClickListener(v -> {
                if (helpText != null && !helpText.isEmpty()) {
                    new MaterialAlertDialogBuilder(getContext())
                            .setTitle(getTitle())
                            .setMessage(helpText)
                            .setPositiveButton(android.R.string.ok, null)
                            .show();
                }
            });
        }
    }
}
