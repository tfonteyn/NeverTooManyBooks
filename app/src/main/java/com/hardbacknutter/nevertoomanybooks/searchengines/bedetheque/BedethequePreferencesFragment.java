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

package com.hardbacknutter.nevertoomanybooks.searchengines.bedetheque;

import android.os.Bundle;

import androidx.annotation.Keep;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.preference.Preference;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import com.hardbacknutter.nevertoomanybooks.R;
import com.hardbacknutter.nevertoomanybooks.ServiceLocator;
import com.hardbacknutter.nevertoomanybooks.database.dao.BedethequeCacheDao;
import com.hardbacknutter.nevertoomanybooks.searchengines.EngineId;
import com.hardbacknutter.nevertoomanybooks.settings.BasePreferenceFragment;

@Keep
public class BedethequePreferencesFragment
        extends BasePreferenceFragment {

    private static final String PSK_CLEAR_AUTHOR_CACHE =
            EngineId.Bedetheque.getPreferenceKey() + ".cache.authors.clear";
    private int authorCacheCount = -1;
    private BedethequeCacheDao cacheDao;

    @Override
    public void onCreatePreferences(@Nullable final Bundle savedInstanceState,
                                    @Nullable final String rootKey) {
        super.onCreatePreferences(savedInstanceState, rootKey);

        cacheDao = ServiceLocator.getInstance().getBedethequeCacheDao();

        setPreferencesFromResource(R.xml.preferences_site_bedetheque, rootKey);

        final Preference purgeCache = findPreference(PSK_CLEAR_AUTHOR_CACHE);
        //noinspection DataFlowIssue
        setPurgeCacheSummary(purgeCache);

        purgeCache.setOnPreferenceClickListener(p -> {
            if (authorCacheCount > 0) {
                //noinspection DataFlowIssue
                new MaterialAlertDialogBuilder(getContext())
                        .setIcon(R.drawable.warning_24px)
                        .setMessage(R.string.option_purge_bedetheque_authors_cache)
                        .setNegativeButton(R.string.cancel, (d, w) -> d.dismiss())
                        .setPositiveButton(R.string.ok, (d, w) -> {
                            cacheDao.clearCache();
                            setPurgeCacheSummary(p);
                        })
                        .create()
                        .show();
            }
            return true;
        });
    }

    private void setPurgeCacheSummary(@NonNull final Preference preference) {
        if (preference.isEnabled()) {
            authorCacheCount = cacheDao.countAuthors();
            final String number;
            if (authorCacheCount > 0) {
                number = String.valueOf(authorCacheCount);
            } else {
                number = getString(R.string.none);
            }
            preference.setSummary(getString(R.string.name_colon_value,
                                            getString(R.string.lbl_authors), number));
        } else {
            authorCacheCount = -1;
            preference.setSummary("");
        }
    }
}
