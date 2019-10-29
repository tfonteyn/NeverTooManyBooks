/*
 * @Copyright 2019 HardBackNutter
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
package com.hardbacknutter.nevertoomanybooks.settings;

import android.os.Bundle;

import androidx.appcompat.app.AlertDialog;
import androidx.preference.Preference;

import com.hardbacknutter.nevertoomanybooks.R;
import com.hardbacknutter.nevertoomanybooks.database.CoversDAO;

/**
 * Used/defined in xml/preferences.xml
 */
public class ImagesPreferencesFragment
        extends BasePreferenceFragment {

    @Override
    public void onCreatePreferences(final Bundle savedInstanceState,
                                    final String rootKey) {

        //noinspection ConstantConditions
        getActivity().setTitle(R.string.pg_thumbnails);

        setPreferencesFromResource(R.xml.preferences_images, rootKey);

        // Purge image cache database table.
        Preference preference = findPreference(Prefs.psk_purge_image_cache);
        if (preference != null) {
            preference.setOnPreferenceClickListener(p -> {
                //noinspection ConstantConditions
                new AlertDialog.Builder(getContext())
                        .setIconAttribute(android.R.attr.alertDialogIcon)
                        .setTitle(R.string.lbl_purge_image_cache)
                        .setMessage(R.string.lbl_purge_image_cache)
                        .setNegativeButton(android.R.string.cancel, (d, w) -> d.dismiss())
                        .setPositiveButton(android.R.string.ok, (d, w) -> CoversDAO.deleteAll())
                        .create()
                        .show();
                return true;
            });
        }
    }

}
