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

package com.hardbacknutter.nevertoomanybooks.searchengines.bol;

import android.os.Bundle;

import androidx.annotation.Keep;
import androidx.annotation.Nullable;
import androidx.preference.ListPreference;
import androidx.preference.SwitchPreference;

import java.util.Locale;

import com.hardbacknutter.nevertoomanybooks.R;
import com.hardbacknutter.nevertoomanybooks.searchengines.EngineId;
import com.hardbacknutter.nevertoomanybooks.searchengines.ShoppingMenuHandler;
import com.hardbacknutter.nevertoomanybooks.settings.BasePreferenceFragment;
import com.hardbacknutter.nevertoomanybooks.settings.Prefs;

@Keep
public class BolPreferencesFragment
        extends BasePreferenceFragment {

    private static final String DEF_COUNTRY = "";

    private final CharSequence[] entries = new CharSequence[3];
    private final CharSequence[] entryValues = {DEF_COUNTRY, "be", "nl"};

    @Override
    public void onCreatePreferences(@Nullable final Bundle savedInstanceState,
                                    @Nullable final String rootKey) {
        super.onCreatePreferences(savedInstanceState, rootKey);
        setPreferencesFromResource(R.xml.preferences_site_bol, rootKey);

        entries[0] = getString(R.string.lbl_system_default);
        entries[1] = new Locale("nl", "BE").getDisplayCountry();
        entries[2] = new Locale("nl", "NL").getDisplayCountry();

        final ListPreference p = findPreference(BolSearchEngine.PK_BOL_COUNTRY);
        //noinspection DataFlowIssue
        p.setEntries(entries);
        p.setEntryValues(entryValues);
        p.setSummaryProvider(ListPreference.SimpleSummaryProvider.getInstance());

        // The ListPreference has an issue that the initial value is set during the inflation
        // step. At that time, the default value is ONLY available from xml.
        // Internally it will then use this to set the value.
        // Workaround: set the default, and if the pref has no value, set it as well...
        p.setDefaultValue(DEF_COUNTRY);
        if (p.getValue() == null) {
            p.setValue(DEF_COUNTRY);
        }

        // We need to set this manually, as the default depends on the user language.
        final SwitchPreference showShoppingMenu = findPreference(
                EngineId.Bol.getPreferenceKey() + '.' + Prefs.PK_SEARCH_SHOW_SHOPPING_MENU);
        final ShoppingMenuHandler shoppingMenuHandler = new BolMenuHandler(
        );
        //noinspection DataFlowIssue
        showShoppingMenu.setChecked(shoppingMenuHandler.isShowMenu(getContext()));
    }
}
