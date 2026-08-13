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
package com.hardbacknutter.nevertoomanybooks.searchengines.amazon;

import android.os.Bundle;
import android.text.InputType;
import android.view.View;

import androidx.activity.OnBackPressedCallback;
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
import com.hardbacknutter.nevertoomanybooks.settings.widgets.HostUrlValidator;
import com.hardbacknutter.prefslib.SettingsDataStore;
import com.hardbacknutter.prefslib.SettingsManager;
import com.hardbacknutter.prefslib.SharedPreferencesDataStore;
import com.hardbacknutter.prefslib.StringSetting;

/**
 * The user can edit the Amazon URL to direct it to their local site.
 * We do check the URL for being valid, but do NOT run a connection test.
 * (the less we connect to Amazon the better).
 */
@Keep
public class AmazonPreferencesFragment
        extends BaseSettingsFragment {

    /** Preferences - Type: {@code String}. */
    private static final String PK_HOST_URL = EngineId.Amazon.getPreferenceKey()
                                              + '.' + SearchEngineConfig.PK_HOST_URL;
    private StringSetting pHostUrl;
    private HostUrlValidator hostUrlValidator;
    private final OnBackPressedCallback backPressedCallback =
            new OnBackPressedCallback(true) {
                @SuppressWarnings("DataFlowIssue")
                @Override
                public void handleOnBackPressed() {
                    final String url = pHostUrl.getValue();
                    if (hostUrlValidator.isValidUrl(url)) {
                        popBackStackOrFinish();
                    } else {
                        //noinspection DataFlowIssue
                        new MaterialAlertDialogBuilder(getContext())
                                .setIcon(R.drawable.info_24px)
                                .setTitle(R.string.error_invalid_url)
                                .setMessage(url)
                                .setPositiveButton(R.string.action_edit, (d, w)
                                        -> getSettingsManager().performClick(PK_HOST_URL))
                                .setNegativeButton(R.string.action_discard, (d, w)
                                        -> popBackStackOrFinish())
                                .create()
                                .show();
                    }
                }
            };

    @Override
    public void onCreate(@Nullable final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        hostUrlValidator = new HostUrlValidator();
    }

    @NonNull
    @Override
    protected SettingsManager.Builder onCreateSettings() {
        final SettingsDataStore store = new SharedPreferencesDataStore(
                ServiceLocator.getInstance().getSharedPreferences());
        //noinspection DataFlowIssue
        final SettingsManager.Builder factory = new SettingsManager.Builder(getContext(), store);

        final String pk = EngineId.Amazon.getPreferenceKey();

        factory.header(EngineId.Amazon.getLabelResId());

        factory.text(PK_HOST_URL,
                     R.string.lbl_website_address, null, p -> {
                    p.setIcon(R.drawable.link_24px);
                    p.setInputType(InputType.TYPE_CLASS_TEXT
                                   | InputType.TYPE_TEXT_VARIATION_URI);
                    p.setValue(EngineId.Amazon.getDefaultUrl());
                    p.setSummaryProvider(c -> hostUrlValidator.getSummary(c, p.getValue()));
                });

        factory.bool(pk + '.' + SearchEngineConfig.PK_SEARCH_WEBSITE_MENU,
                     R.string.pt_search_show_menu_options, null, p -> {
                    p.setIcon(R.drawable.shop_24px);
                    p.setChecked(true);
                });

        factory.bool(pk + '.' + SearchEngineConfig.PK_SEARCH_ISBN_PREFER_10,
                     R.string.pt_search_prefer_isbn10, null, p -> {
                    p.setIcon(R.drawable.barcode_24px);
                });


        CommonSettingsFactory.troubleshoot(factory, pk);

        return factory;
    }

    @Override
    public void onViewCreated(@NonNull final View view,
                              @Nullable final Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        //noinspection DataFlowIssue
        getActivity().getOnBackPressedDispatcher()
                     .addCallback(getViewLifecycleOwner(), backPressedCallback);

        pHostUrl = getSettingsManager().requireSetting(PK_HOST_URL);
    }
}
