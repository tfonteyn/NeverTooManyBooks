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

import androidx.annotation.Keep;
import androidx.annotation.NonNull;

import com.hardbacknutter.nevertoomanybooks.R;
import com.hardbacknutter.nevertoomanybooks.ServiceLocator;
import com.hardbacknutter.nevertoomanybooks.booklist.BooklistCursor;
import com.hardbacknutter.prefslib.SettingsDataStore;
import com.hardbacknutter.prefslib.SettingsManager;
import com.hardbacknutter.prefslib.SharedPreferencesDataStore;

@Keep
public class TuningFragment
        extends BaseSettingsFragment {

    public static final String TAG = "TuningFragment";

    @SuppressWarnings("CodeBlock2Expr")
    @NonNull
    @Override
    protected SettingsManager.Builder onCreateSettings() {
        final SettingsDataStore store = new SharedPreferencesDataStore(
                ServiceLocator.getInstance().getSharedPreferences());
        //noinspection DataFlowIssue
        final SettingsManager.Builder factory = new SettingsManager.Builder(getContext(), store);

        factory.header(R.string.lbl_database, p -> {
            p.setSummary(R.string.lbl_troubleshooting_warning);
        });

        factory.floatRange(Tuning.PK_OFFSCREEN_CACHE_SIZE,
                           R.string.tuning_offscreen_cache_size,
                           Tuning.MIN_OFFSCREEN_CACHE_SIZE,
                           Tuning.MAX_OFFSCREEN_CACHE_SIZE, null, p -> {
                    p.setValue(Tuning.DEFAULT_OFFSCREEN_CACHE_SIZE);
                    p.setSummary(getString(R.string.lbl_default_x, String.valueOf(
                            Tuning.DEFAULT_OFFSCREEN_CACHE_SIZE)));
                });

        factory.floatRange(BooklistCursor.PK_PAGE_SIZE,
                           R.string.tuning_page_size,
                           BooklistCursor.PAGE_SIZE_MIN,
                           BooklistCursor.PAGE_SIZE_MAX, null, p -> {
                    p.setValue(BooklistCursor.PAGE_SIZE_DEFAULT);
                    p.setSummary(getString(R.string.lbl_default_x, String.valueOf(
                            BooklistCursor.PAGE_SIZE_DEFAULT)));
                });

        factory.floatRange(BooklistCursor.PK_LRU_LIST_MULTIPLIER,
                           R.string.tuning_lru_list_multiplier,
                           BooklistCursor.LRU_LIST_MULTIPLIER_MIN,
                           BooklistCursor.LRU_LIST_MULTIPLIER_MAX, null, p -> {
                    p.setValue(BooklistCursor.LRU_LIST_MULTIPLIER_DEFAULT);
                    p.setSummary(getString(R.string.lbl_default_x, String.valueOf(
                            BooklistCursor.LRU_LIST_MULTIPLIER_DEFAULT)));
                });

        return factory;
    }
}
