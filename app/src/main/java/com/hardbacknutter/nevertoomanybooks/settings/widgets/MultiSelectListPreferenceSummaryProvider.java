/*
 * @Copyright 2018-2024 HardBackNutter
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

import androidx.annotation.NonNull;
import androidx.preference.MultiSelectListPreference;
import androidx.preference.Preference;

import java.util.StringJoiner;

import com.hardbacknutter.nevertoomanybooks.R;
import com.hardbacknutter.util.logger.LoggerFactory;

public final class MultiSelectListPreferenceSummaryProvider
        implements Preference.SummaryProvider<MultiSelectListPreference> {

    private static final String TAG = "MSLPSummaryProvider";

    private static final MultiSelectListPreferenceSummaryProvider INSTANCE =
            new MultiSelectListPreferenceSummaryProvider();

    private MultiSelectListPreferenceSummaryProvider() {
    }

    /**
     * Retrieve a singleton instance of this simple
     * {@link androidx.preference.Preference.SummaryProvider} implementation.
     *
     * @return a singleton instance of this simple
     *         {@link androidx.preference.Preference.SummaryProvider} implementation
     */
    @NonNull
    public static MultiSelectListPreferenceSummaryProvider getInstance() {
        return INSTANCE;
    }

    @Override
    @NonNull
    public CharSequence provideSummary(@NonNull final MultiSelectListPreference preference) {
        final StringJoiner items = new StringJoiner(", ");
        for (final String s : preference.getValues()) {
            final int index = preference.findIndexOfValue(s);
            if (index >= 0) {
                items.add(preference.getEntries()[index]);

            } else {
                // This re-surfaces sometimes after a careless dev. change.
                LoggerFactory.getLogger()
                              .e(TAG, new Throwable(),
                                 "MultiSelectListPreference:"
                                 + "\n s=" + s
                                 + "\n key=" + preference.getKey()
                                 + "\n entries=" + String.join(",", preference.getEntries())
                                 + "\n entryValues="
                                 + String.join(",", preference.getEntryValues())
                                 + "\n values=" + preference.getValues());
            }
        }

        if (items.length() > 0) {
            return items.toString();
        } else {
            // the preference has no values set, but that is a VALID setting and will be used.
            return preference.getContext().getString(R.string.none);
        }
    }

}
