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

package com.hardbacknutter.nevertoomanybooks.settings;

import android.content.SharedPreferences;
import android.os.Bundle;

import androidx.annotation.Keep;
import androidx.annotation.Nullable;
import androidx.preference.SeekBarPreference;

import com.hardbacknutter.nevertoomanybooks.R;
import com.hardbacknutter.nevertoomanybooks.booklist.BooklistCursor;

@Keep
public class TuningFragment
        extends BasePreferenceFragment {

    public static final String TAG = "TuningFragment";

    @Override
    public void onCreatePreferences(@Nullable final Bundle savedInstanceState,
                                    @Nullable final String rootKey) {
        super.onCreatePreferences(savedInstanceState, rootKey);

        setPreferencesFromResource(R.xml.preferences_tuning, rootKey);

        final SharedPreferences prefs = getPreferenceManager().getSharedPreferences();

        SeekBarPreference p;
        // set all values from code to avoid the XML being out-of-sync

        p = findPreference(Tuning.PK_OFFSCREEN_CACHE_SIZE);
        //noinspection DataFlowIssue
        p.setSummary(getString(R.string.lbl_default_x,
                               String.valueOf(Tuning.DEFAULT_OFFSCREEN_CACHE_SIZE)));
        p.setDefaultValue(Tuning.DEFAULT_OFFSCREEN_CACHE_SIZE);
        p.setMin(Tuning.MIN_OFFSCREEN_CACHE_SIZE);
        p.setMax(Tuning.MAX_OFFSCREEN_CACHE_SIZE);
        // because androidx.preferences is [bug]'d
        //noinspection DataFlowIssue
        p.setValue(prefs.getInt(Tuning.PK_OFFSCREEN_CACHE_SIZE,
                                Tuning.DEFAULT_OFFSCREEN_CACHE_SIZE));


        p = findPreference(BooklistCursor.PK_PAGE_SIZE);
        //noinspection DataFlowIssue
        p.setSummary(getString(R.string.lbl_default_x,
                               String.valueOf(BooklistCursor.PAGE_SIZE_DEFAULT)));
        p.setDefaultValue(BooklistCursor.PAGE_SIZE_DEFAULT);
        p.setMin(BooklistCursor.PAGE_SIZE_MIN);
        p.setMax(BooklistCursor.PAGE_SIZE_MAX);
        // because androidx.preferences is [bug]'d
        p.setValue(prefs.getInt(BooklistCursor.PK_PAGE_SIZE,
                                BooklistCursor.PAGE_SIZE_DEFAULT));

        p = findPreference(BooklistCursor.PK_LRU_LIST_MULTIPLIER);
        //noinspection DataFlowIssue
        p.setSummary(getString(R.string.lbl_default_x,
                               String.valueOf(BooklistCursor.LRU_LIST_MULTIPLIER_DEFAULT)));
        p.setDefaultValue(BooklistCursor.LRU_LIST_MULTIPLIER_DEFAULT);
        p.setMin(BooklistCursor.LRU_LIST_MULTIPLIER_MIN);
        p.setMax(BooklistCursor.LRU_LIST_MULTIPLIER_MAX);
        // because androidx.preferences is [bug]'d
        p.setValue(prefs.getInt(BooklistCursor.PK_LRU_LIST_MULTIPLIER,
                                BooklistCursor.LRU_LIST_MULTIPLIER_DEFAULT));
    }
}
