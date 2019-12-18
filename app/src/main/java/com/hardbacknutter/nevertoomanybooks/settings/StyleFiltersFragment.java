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

import androidx.annotation.NonNull;
import androidx.annotation.XmlRes;
import androidx.fragment.app.DialogFragment;
import androidx.preference.Preference;

import com.hardbacknutter.nevertoomanybooks.R;
import com.hardbacknutter.nevertoomanybooks.widgets.BitmaskPreference;

/**
 * Used/defined in xml/preferences_styles.xml
 * <p>
 * Uses a custom dialog for bitmask preferences which can be set to 'unused'.
 */
public class StyleFiltersFragment
        extends StyleBaseFragment {

    private static final int REQ_BITMASK_DIALOG = 0;
    private static final String TAG = "StyleFiltersFragment";
    private static final String DIALOG_FRAGMENT_TAG = TAG + ":dialog";

    @Override
    @XmlRes
    protected int getLayoutId() {
        return R.xml.preferences_styles_filters;
    }

    @Override
    void updateSummary(@NonNull final String key) {
        if (Prefs.pk_bob_filter_editions.equals(key)) {
            BitmaskPreference preference = findPreference(key);
            // if it is in use, the summary is build by the parent class
            if (preference != null && !preference.isUsed()) {
                preference.setSummary(R.string.bookshelf_all_books);
                return;
            }
        }

        super.updateSummary(key);
    }

    @Override
    public void onDisplayPreferenceDialog(@NonNull final Preference preference) {
        //noinspection SwitchStatementWithTooFewBranches
        switch (preference.getKey()) {
            case Prefs.pk_bob_filter_editions: {
                // check if dialog is already showing
                if (getParentFragmentManager().findFragmentByTag(DIALOG_FRAGMENT_TAG) != null) {
                    return;
                }

                final DialogFragment f = BitmaskPreference.BitmaskPreferenceDialogFragment
                        .newInstance(preference.getKey(), R.string.bookshelf_all_books);

                f.setTargetFragment(this, REQ_BITMASK_DIALOG);
                f.show(getParentFragmentManager(), DIALOG_FRAGMENT_TAG);
                break;
            }
            default:
                super.onDisplayPreferenceDialog(preference);
                break;
        }
    }
}
