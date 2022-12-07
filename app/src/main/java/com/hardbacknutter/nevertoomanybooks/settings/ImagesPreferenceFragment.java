/*
 * @Copyright 2018-2022 HardBackNutter
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

import android.os.Bundle;

import androidx.annotation.Keep;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.preference.ListPreference;
import androidx.preference.Preference;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import com.hardbacknutter.nevertoomanybooks.R;
import com.hardbacknutter.nevertoomanybooks.ServiceLocator;

/**
 * Used/defined in xml/preferences.xml
 */
@Keep
public class ImagesPreferenceFragment
        extends BasePreferenceFragment {

    private static final String PSK_PURGE_IMAGE_CACHE = "psk_purge_image_cache";

    @Override
    public void onCreatePreferences(@Nullable final Bundle savedInstanceState,
                                    @Nullable final String rootKey) {
        super.onCreatePreferences(savedInstanceState, rootKey);
        setPreferencesFromResource(R.xml.preferences_images, rootKey);

        //noinspection ConstantConditions
        findPreference(Prefs.pk_camera_image_autorotate)
                .setSummaryProvider(ListPreference.SimpleSummaryProvider.getInstance());

        //noinspection ConstantConditions
        findPreference(Prefs.pk_camera_image_action)
                .setSummaryProvider(ListPreference.SimpleSummaryProvider.getInstance());

        // Purge image cache database table.
        final Preference purgeCache = findPreference(PSK_PURGE_IMAGE_CACHE);
        //noinspection ConstantConditions
        setPurgeCacheSummary(purgeCache);

        purgeCache.setOnPreferenceClickListener(p -> {
            //noinspection ConstantConditions
            new MaterialAlertDialogBuilder(getContext())
                    .setIcon(R.drawable.ic_baseline_warning_24)
                    .setMessage(R.string.option_purge_image_cache)
                    .setNegativeButton(android.R.string.cancel, (d, w) -> d.dismiss())
                    .setPositiveButton(android.R.string.ok, (d, w) -> {
                        ServiceLocator.getInstance().getCoverCacheDao().deleteAll();
                        setPurgeCacheSummary(p);
                    })
                    .create()
                    .show();
            return true;
        });
    }

    private void setPurgeCacheSummary(@NonNull final Preference preference) {
        if (preference.isEnabled()) {
            final int count = ServiceLocator.getInstance().getCoverCacheDao().count();
            final String number;
            if (count > 0) {
                number = String.valueOf(count);
            } else {
                number = getString(R.string.none);
            }
            preference.setSummary(getString(R.string.name_colon_value,
                                            getString(R.string.lbl_covers), number));
        } else {
            preference.setSummary("");
        }
    }
}
