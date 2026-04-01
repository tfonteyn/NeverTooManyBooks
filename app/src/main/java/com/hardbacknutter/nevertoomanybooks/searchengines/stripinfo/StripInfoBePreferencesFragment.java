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
package com.hardbacknutter.nevertoomanybooks.searchengines.stripinfo;

import android.os.Bundle;
import android.view.View;

import androidx.annotation.Keep;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.hardbacknutter.nevertoomanybooks.BuildConfig;
import com.hardbacknutter.nevertoomanybooks.R;
import com.hardbacknutter.nevertoomanybooks.ServiceLocator;
import com.hardbacknutter.nevertoomanybooks.searchengines.AuthorResolverFactory;
import com.hardbacknutter.nevertoomanybooks.searchengines.CommonSettingsFactory;
import com.hardbacknutter.nevertoomanybooks.searchengines.EngineId;
import com.hardbacknutter.nevertoomanybooks.searchengines.SearchEngineConfig;
import com.hardbacknutter.nevertoomanybooks.searchengines.SiteAuthModule;
import com.hardbacknutter.nevertoomanybooks.settings.BaseSettingsFragment;
import com.hardbacknutter.nevertoomanybooks.settings.ConnectionValidationHelper;
import com.hardbacknutter.nevertoomanybooks.sync.stripinfo.StripInfoHandler;
import com.hardbacknutter.prefslib.BooleanSetting;
import com.hardbacknutter.prefslib.Setting;
import com.hardbacknutter.prefslib.SettingsDataStore;
import com.hardbacknutter.prefslib.SettingsManager;
import com.hardbacknutter.prefslib.SharedPreferencesDataStore;

@Keep
public class StripInfoBePreferencesFragment
        extends BaseSettingsFragment {

    /** Fragment/Log tag. */
    public static final String TAG = "StripInfoBePrefFrag";

    private static final String PK = EngineId.StripInfoBe.getPreferenceKey();
    private static final String PK_PASSWORD = PK + SiteAuthModule.PK_SUFFIX_HOST_PASSWORD;
    private static final String PK_USER = PK + SiteAuthModule.PK_SUFFIX_HOST_USER;

    @Nullable
    private BooleanSetting pSyncEnabled;
    @Nullable
    private BooleanSetting pLoginToSearch;

    @NonNull
    @Override
    protected SettingsManager.Builder onCreateSettings() {
        final SettingsDataStore store = new SharedPreferencesDataStore(
                ServiceLocator.getInstance().getSharedPreferences());
        //noinspection DataFlowIssue
        final SettingsManager.Builder factory = new SettingsManager.Builder(getContext(), store);

        factory.header(EngineId.StripInfoBe.getLabelResId());

        factory.bool(PK + '.' + SearchEngineConfig.PK_SEARCH_ISBN_PREFER_10,
                     R.string.pt_search_prefer_isbn10, null, p -> {
                    p.setIcon(R.drawable.barcode_24px);
                });
        factory.bool(PK + AuthorResolverFactory.PK_RESOLVE_AUTHORS
                     + EngineId.Bedetheque.getPreferenceKey(),
                     0, null, p -> {
                    p.setIcon(R.drawable.cloud_download_24px);
                    p.setChecked(true);
                    p.setTitle(getString(R.string.pt_fetch_author_info_using_site_x,
                                         getString(R.string.site_bedetheque)));
                });

        if (BuildConfig.ENABLE_STRIP_INFO_LOGIN) {
            factory.bool(StripInfoSearchEngine.PK_LOGIN_TO_SEARCH,
                         R.string.lbl_login_to_search,
                         this::onChangeLoginToSearch, p -> {
                        p.setIcon(R.drawable.login_24px);
                    });
            factory.bool(StripInfoHandler.PK_ENABLED,
                         R.string.option_enable_sync_options,
                         R.string.disabled, R.string.enabled,
                         this::onChangeEnableSync, p -> {
                        p.setIcon(R.drawable.login_24px);
                    });

            CommonSettingsFactory.credentials(factory, PK);
        }

        CommonSettingsFactory.timeouts(factory, PK);
        CommonSettingsFactory.troubleshoot(factory, PK);

        return factory;
    }

    @Override
    public void onViewCreated(@NonNull final View view,
                              @Nullable final Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        if (BuildConfig.ENABLE_STRIP_INFO_LOGIN) {
            final SettingsManager settingsManager = getSettingsManager();
            pSyncEnabled = settingsManager
                    .requireSetting(StripInfoHandler.PK_ENABLED);
            pLoginToSearch = settingsManager
                    .requireSetting(StripInfoSearchEngine.PK_LOGIN_TO_SEARCH);

            enableCredentials(pLoginToSearch.isChecked() || pSyncEnabled.isChecked());
        }

        new ConnectionValidationHelper(
                R.string.site_stripinfo_be, this, getProgressFrame(), () -> {
            if (BuildConfig.ENABLE_STRIP_INFO_LOGIN) {
                return pLoginToSearch.isChecked() || pSyncEnabled.isChecked();
            } else {
                return false;
            }
        }, this::popBackStackOrFinish);
    }

    private boolean onChangeEnableSync(@NonNull final Setting setting,
                                       @Nullable final Object newValue) {
        final boolean enabled = newValue != null && (boolean) newValue;
        //noinspection DataFlowIssue
        enableCredentials(pLoginToSearch.isChecked() || enabled);
        return true;
    }

    private boolean onChangeLoginToSearch(@NonNull final Setting setting,
                                          @Nullable final Object newValue) {
        final boolean enabled = newValue != null && (boolean) newValue;
        //noinspection DataFlowIssue
        enableCredentials(enabled || pSyncEnabled.isChecked());
        return true;
    }

    private void enableCredentials(final boolean enable) {
        getSettingsManager().setEnabled(enable, PK_USER, PK_PASSWORD);
    }
}
