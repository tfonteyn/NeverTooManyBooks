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

package com.hardbacknutter.nevertoomanybooks.settings.widgets;

import android.content.Context;
import android.text.Spannable;
import android.text.SpannableString;
import android.text.TextUtils;
import android.text.style.ForegroundColorSpan;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.regex.Pattern;

import com.hardbacknutter.nevertoomanybooks.R;
import com.hardbacknutter.nevertoomanybooks.core.utils.AttrUtils;

public class HostUrlValidator {

    private static final Pattern SIMPLE_URL_PATTERN = Pattern.compile("(http|https)://.+");

    public boolean isValidUrl(@Nullable final CharSequence text) {
        if (text == null || text.length() == 0) {
            return false;
        }
        return SIMPLE_URL_PATTERN.matcher(text).matches();
    }

    @NonNull
    public CharSequence getSummary(@NonNull final Context context,
                                   @Nullable final CharSequence text) {
        CharSequence s = text;
        if (TextUtils.isEmpty(s)) {
            return context.getString(R.string.preference_not_set);

        } else if (isValidUrl(s)) {
            return s;

        } else {
            s = context.getString(R.string.name_colon_value,
                                  context.getString(R.string.error_invalid_url), s);
            final Spannable spannable = new SpannableString(s);
            // don't use android.R.attr.colorError which is API 29+ only
            final int colorInt = AttrUtils
                    .getColorInt(context, androidx.appcompat.R.attr.colorError);
            spannable.setSpan(new ForegroundColorSpan(colorInt), 0, s.length(), 0);
            return spannable;
        }
    }
}
