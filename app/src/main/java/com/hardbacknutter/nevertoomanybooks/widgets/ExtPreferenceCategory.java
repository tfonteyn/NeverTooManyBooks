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
package com.hardbacknutter.nevertoomanybooks.widgets;

import android.content.Context;
import android.util.AttributeSet;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.preference.PreferenceCategory;
import androidx.preference.PreferenceViewHolder;

/**
 * In your "res/som_prefs.xml" file(s), replace {@code <PreferenceCategory ...}
 * with {@code <com.hardbacknutter.nevertoomanybooks.widgets.ExtPreferenceCategory ...}
 * and the {@code android:summary} attribute will be able to display multiple lines
 * of text.
 */
public class ExtPreferenceCategory
        extends PreferenceCategory {

    public ExtPreferenceCategory(@NonNull final Context context,
                                 @Nullable final AttributeSet attrs,
                                 final int defStyleAttr,
                                 final int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);
    }

    public ExtPreferenceCategory(@NonNull final Context context,
                                 @Nullable final AttributeSet attrs,
                                 final int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    public ExtPreferenceCategory(@NonNull final Context context,
                                 @Nullable final AttributeSet attrs) {
        super(context, attrs);
    }

    public ExtPreferenceCategory(@NonNull final Context context) {
        super(context);
    }

    @Override
    public void onBindViewHolder(@NonNull final PreferenceViewHolder holder) {
        super.onBindViewHolder(holder);

        final TextView summaryView = (TextView) holder.findViewById(android.R.id.summary);
        if (summaryView != null) {
            summaryView.setSingleLine(false);
            //summaryView.setMaxLines(10);
        }
    }
}
