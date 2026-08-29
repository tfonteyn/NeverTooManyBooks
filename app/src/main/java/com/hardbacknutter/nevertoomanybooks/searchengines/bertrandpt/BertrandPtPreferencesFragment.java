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
package com.hardbacknutter.nevertoomanybooks.searchengines.bertrandpt;

import android.content.Context;

import androidx.annotation.Keep;
import androidx.annotation.NonNull;

import com.hardbacknutter.nevertoomanybooks.R;
import com.hardbacknutter.nevertoomanybooks.ServiceLocator;
import com.hardbacknutter.nevertoomanybooks.searchengines.CommonSettingsFactory;
import com.hardbacknutter.nevertoomanybooks.searchengines.EngineId;
import com.hardbacknutter.nevertoomanybooks.searchengines.SearchEngine;
import com.hardbacknutter.nevertoomanybooks.searchengines.SearchEngineConfig;
import com.hardbacknutter.nevertoomanybooks.settings.BaseSettingsFragment;
import com.hardbacknutter.prefslib.SettingsDataStore;
import com.hardbacknutter.prefslib.SettingsManager;
import com.hardbacknutter.prefslib.SharedPreferencesDataStore;

@Keep
public class BertrandPtPreferencesFragment
        extends BaseSettingsFragment {

    private static final String PK_SEARCH_WEBSITE_MENU =
            EngineId.BertrandPt.getPreferenceKey()
            + '.' + SearchEngineConfig.PK_SEARCH_WEBSITE_MENU;

    @NonNull
    @Override
    protected SettingsManager.Builder onCreateSettings() {
        final SettingsDataStore store = new SharedPreferencesDataStore(
                ServiceLocator.getInstance().getSharedPreferences());
        //noinspection DataFlowIssue
        final SettingsManager.Builder factory = new SettingsManager.Builder(getContext(), store);
        final String pk = EngineId.BertrandPt.getPreferenceKey();

        factory.header(EngineId.BertrandPt.getLabelResId());

        factory.bool(pk + '.' + SearchEngineConfig.PK_SEARCH_WEBSITE_MENU,
                     R.string.pt_search_show_menu_options, null, p -> {
                    p.setIcon(R.drawable.shop_24px);

                    // The default depends on the user language.
                    final Context context = getContext();
                    @SuppressWarnings("DataFlowIssue")
                    final boolean checked = ((SearchEngine.SearchOnSite)
                            EngineId.BertrandPt.createSearchEngine(context))
                            .isShowSearchOnSiteMenu(context);
                    p.setChecked(checked);
                });
        factory.bool(pk + '.' + SearchEngineConfig.PK_SEARCH_ISBN_PREFER_10,
                     R.string.pt_search_prefer_isbn10, null, p -> {
                    p.setIcon(R.drawable.barcode_24px);
                });


        CommonSettingsFactory.timeouts(factory, pk);
        CommonSettingsFactory.troubleshoot(factory, pk);

        return factory;
    }
}
