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

package com.hardbacknutter.nevertoomanybooks.searchengines.bedetheque;

import android.content.Context;
import android.os.Bundle;
import android.view.View;

import androidx.annotation.Keep;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import com.hardbacknutter.nevertoomanybooks.R;
import com.hardbacknutter.nevertoomanybooks.ServiceLocator;
import com.hardbacknutter.nevertoomanybooks.database.dao.BedethequeCacheDao;
import com.hardbacknutter.nevertoomanybooks.searchengines.AuthorResolverHelper;
import com.hardbacknutter.nevertoomanybooks.searchengines.CommonSettingsFactory;
import com.hardbacknutter.nevertoomanybooks.searchengines.EngineId;
import com.hardbacknutter.nevertoomanybooks.searchengines.SearchEngineConfig;
import com.hardbacknutter.nevertoomanybooks.settings.BaseSettingsFragment;
import com.hardbacknutter.prefslib.Setting;
import com.hardbacknutter.prefslib.SettingsDataStore;
import com.hardbacknutter.prefslib.SettingsManager;
import com.hardbacknutter.prefslib.SharedPreferencesDataStore;

@Keep
public class BedethequePreferencesFragment
        extends BaseSettingsFragment {

    private static final String PSK_CLEAR_AUTHOR_CACHE =
            EngineId.Bedetheque.getPreferenceKey() + ".cache.authors.clear";
    private int authorCacheCount = -1;
    private BedethequeCacheDao cacheDao;

    @SuppressWarnings("CodeBlock2Expr")
    @NonNull
    @Override
    protected SettingsManager.Builder onCreateSettings() {
        final SettingsDataStore store = new SharedPreferencesDataStore(
                ServiceLocator.getInstance().getSharedPreferences());
        //noinspection DataFlowIssue
        final SettingsManager.Builder factory = new SettingsManager.Builder(getContext(), store);

        final String pk = EngineId.Bedetheque.getPreferenceKey();

        factory.header(EngineId.Bedetheque.getLabelResId());

        factory.bool(pk + '.' + SearchEngineConfig.PK_SEARCH_ISBN_PREFER_10,
                     R.string.pt_search_prefer_isbn10, null, p -> {
                    p.setIcon(R.drawable.barcode_24px);
                });

        factory.bool(AuthorResolverHelper.getPreferenceKey(EngineId.Bedetheque),
                     R.string.pt_fetch_author_info, null, p -> {
                    p.setIcon(R.drawable.cloud_download_24px);
                    p.setChecked(true);
                });

        factory.bool(BedethequeSearchEngine.PK_BEDETHEQUE_PRESERVE_FORMAT_NAMES,
                     R.string.pt_bedetheque_search_resolve_formats,
                     R.string.pt_search_reformat_format, R.string.yes,
                     null, null);

        factory.header(R.string.pt_maintenance);
        factory.action(PSK_CLEAR_AUTHOR_CACHE,
                       R.string.option_purge_bedetheque_authors_cache,
                       this::onClearAuthorCache, p -> {
                    p.setSummaryProvider(this::getPurgeCacheSummary);
                    p.setIcon(R.drawable.delete_24px);
                }
        );

        CommonSettingsFactory.timeouts(factory, pk);
        CommonSettingsFactory.troubleshoot(factory, pk);

        return factory;
    }

    @Override
    public void onViewCreated(@NonNull final View view,
                              @Nullable final Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        cacheDao = ServiceLocator.getInstance().getBedethequeCacheDao();
    }

    @NonNull
    private String getPurgeCacheSummary(@NonNull final Context context) {
        authorCacheCount = cacheDao.countAuthors();
        final String number;
        if (authorCacheCount > 0) {
            number = String.valueOf(authorCacheCount);
        } else {
            number = getString(R.string.none);
        }
        return getString(R.string.name_colon_value, getString(R.string.lbl_authors), number);
    }

    private boolean onClearAuthorCache(@NonNull final Setting setting) {
        if (authorCacheCount > 0) {
            final Context context = getContext();
            //noinspection DataFlowIssue
            new MaterialAlertDialogBuilder(context)
                    .setIcon(R.drawable.warning_24px)
                    .setMessage(R.string.option_purge_bedetheque_authors_cache)
                    .setNegativeButton(R.string.cancel, (d, w) -> d.dismiss())
                    .setPositiveButton(R.string.ok, (d, w) -> {
                        cacheDao.clearCache();
                        // Refresh the summary
                        getSettingsManager().reload(context, PSK_CLEAR_AUTHOR_CACHE);
                    })
                    .create()
                    .show();
        }
        return true;
    }
}
