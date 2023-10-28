/*
 * @Copyright 2018-2023 HardBackNutter
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
import androidx.preference.EditTextPreference;
import androidx.preference.Preference;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.util.regex.Pattern;

import com.hardbacknutter.nevertoomanybooks.R;
import com.hardbacknutter.nevertoomanybooks.utils.AttrUtils;

public class HostUrlValidator
        implements Preference.SummaryProvider<EditTextPreference> {

    private static final Pattern SIMPLE_URL_PATTERN = Pattern.compile("(http|https)://.+");

    public boolean isValidUrl(@NonNull final EditTextPreference preference) {
        final String text = preference.getText();
        if (text == null || text.isEmpty()) {
            return false;
        }
        return SIMPLE_URL_PATTERN.matcher(text).matches();
    }

    public void showUrlInvalidDialog(@NonNull final EditTextPreference preference,
                                     @Nullable final Runnable onDiscard) {
        new MaterialAlertDialogBuilder(preference.getContext())
                .setIcon(R.drawable.ic_baseline_info_24)
                .setTitle(R.string.error_invalid_url)
                .setMessage(preference.getText())
                .setPositiveButton(R.string.action_edit, (d, w) -> d.dismiss())
                .setNegativeButton(R.string.action_discard, (d, w) -> {
                    if (onDiscard != null) {
                        onDiscard.run();
                    }
                })
                .create()
                .show();
    }

    @Nullable
    @Override
    public CharSequence provideSummary(@NonNull final EditTextPreference preference) {
        final Context context = preference.getContext();
        String text = preference.getText();

        if (TextUtils.isEmpty(text)) {
            return context.getString(R.string.preference_not_set);

        } else if (isValidUrl(preference)) {
            return text;

        } else {
            text = context.getString(R.string.name_colon_value,
                                     context.getString(R.string.error_invalid_url), text);
            final Spannable spannable = new SpannableString(text);
            final int colorInt = AttrUtils
                    .getColorInt(context, com.google.android.material.R.attr.colorError);
            spannable.setSpan(new ForegroundColorSpan(colorInt), 0, text.length(), 0);
            return spannable;
        }
    }


}
