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
package com.hardbacknutter.nevertoomanybooks.searchengines.librarything;

import androidx.annotation.Keep;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import com.hardbacknutter.nevertoomanybooks.R;
import com.hardbacknutter.nevertoomanybooks.ServiceLocator;
import com.hardbacknutter.nevertoomanybooks.searchengines.CommonSettingsFactory;
import com.hardbacknutter.nevertoomanybooks.searchengines.EngineId;
import com.hardbacknutter.nevertoomanybooks.searchengines.SearchEngineConfig;
import com.hardbacknutter.nevertoomanybooks.settings.BaseSettingsFragment;
import com.hardbacknutter.prefslib.Setting;
import com.hardbacknutter.prefslib.SettingsDataStore;
import com.hardbacknutter.prefslib.SettingsManager;
import com.hardbacknutter.prefslib.SharedPreferencesDataStore;

@Keep
public class LibraryThingPreferencesFragment
        extends BaseSettingsFragment {

    @NonNull
    @Override
    protected SettingsManager.Builder onCreateSettings() {
        final SettingsDataStore store = new SharedPreferencesDataStore(
                ServiceLocator.getInstance().getSharedPreferences());
        //noinspection DataFlowIssue
        final SettingsManager.Builder factory = new SettingsManager.Builder(getContext(), store);

        final String pk = EngineId.LibraryThing.getPreferenceKey();

        factory.header(EngineId.LibraryThing.getLabelResId());

        factory.bool(pk + '.' + SearchEngineConfig.PK_SEARCH_ISBN_PREFER_10,
                     R.string.pt_search_prefer_isbn10, null, p -> {
                    p.setIcon(R.drawable.barcode_24px);
                });

        factory.header(R.string.lbl_credentials);
        factory.text(LibraryThingSearchEngine.PK_API_TOKEN,
                     R.string.lbl_api_token,
                     this::onChangeApiToken, p -> {
                    p.setIcon(R.drawable.security_24px);
                });

        CommonSettingsFactory.timeouts(factory, pk);
        CommonSettingsFactory.troubleshoot(factory, pk);

        return factory;
    }

    private boolean onChangeApiToken(@NonNull final Setting setting,
                                     @Nullable final Object newValue) {
        final int len = newValue != null ? ((CharSequence) newValue).length() : 0;
        if (len == 0 || len == LibraryThingSearchEngine.TOKEN_LEN) {
            return true;
        }
        //noinspection DataFlowIssue
        new MaterialAlertDialogBuilder(getContext())
                .setIcon(R.drawable.error_24px)
                .setTitle(R.string.lbl_api_token)
                .setMessage(getString(R.string.vldt_exact_length_required,
                                      LibraryThingSearchEngine.TOKEN_LEN))
                .setPositiveButton(R.string.action_edit, (d, w) -> {
                    d.dismiss();
                    getSettingsManager().performClick(
                            LibraryThingSearchEngine.PK_API_TOKEN);
                })
                .create()
                .show();
        return false;
    }
}
