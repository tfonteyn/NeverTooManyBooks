/*
 * @Copyright 2020 HardBackNutter
 * @License GNU General Public License
 *
 * This file is part of NeverTooManyBooks.
 *
 * In August 2018, this project was forked from:
 * Book Catalogue 5.2.2 @2016 Philip Warner & Evan Leybourn
 *
 * Without their original creation, this project would not exist in its
 * current form. It was however largely rewritten/refactored and any
 * comments on this fork should be directed at HardBackNutter and not
 * at the original creators.
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
package com.hardbacknutter.nevertoomanybooks.datamanager.fieldformatters;

import android.content.Context;
import android.text.method.LinkMovementMethod;
import android.view.View;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.hardbacknutter.nevertoomanybooks.utils.LinkifyUtils;

/**
 * FieldFormatter for 'html' fields.
 * <ul>
 * <li>Multiple fields: <strong>no</strong></li>
 * <li>Extract: <strong>local variable</strong></li>
 * </ul>
 */
public class HtmlFormatter
        implements FieldFormatter<String> {

    @Nullable
    private String mRawValue;

    @NonNull
    @Override
    public String format(@NonNull final Context context,
                         @Nullable final String rawValue) {
        if (rawValue == null) {
            return "";
        } else {
            return rawValue;
        }
    }

    @Override
    public void apply(@Nullable final String rawValue,
                      @NonNull final View view) {
        mRawValue = rawValue;

        TextView textView = (TextView) view;
        textView.setText(LinkifyUtils.fromHtml(format(view.getContext(), rawValue)));
        textView.setMovementMethod(LinkMovementMethod.getInstance());
        textView.setFocusable(true);
        textView.setTextIsSelectable(true);
    }

    @NonNull
    @Override
    public String extract(@NonNull final View view) {
        return mRawValue != null ? mRawValue : "";
    }
}
