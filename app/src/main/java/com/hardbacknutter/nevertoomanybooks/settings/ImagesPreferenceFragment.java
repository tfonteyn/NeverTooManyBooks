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
package com.hardbacknutter.nevertoomanybooks.settings;

import android.content.Context;
import android.os.Bundle;
import android.view.View;

import androidx.annotation.Keep;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import com.hardbacknutter.nevertoomanybooks.R;
import com.hardbacknutter.nevertoomanybooks.ServiceLocator;
import com.hardbacknutter.nevertoomanybooks.covers.CoverStorage;
import com.hardbacknutter.nevertoomanybooks.covers.ImageHandlerViewModel;
import com.hardbacknutter.nevertoomanybooks.covers.NextAction;
import com.hardbacknutter.prefslib.BooleanSetting;
import com.hardbacknutter.prefslib.Setting;
import com.hardbacknutter.prefslib.SettingsDataStore;
import com.hardbacknutter.prefslib.SettingsManager;
import com.hardbacknutter.prefslib.SharedPreferencesDataStore;

@Keep
public class ImagesPreferenceFragment
        extends BaseSettingsFragment {

    private static final String PSK_PURGE_IMAGE_CACHE = "psk_purge_image_cache";

    @NonNull
    @Override
    protected SettingsManager.Builder onCreateSettings() {
        final SettingsDataStore store = new SharedPreferencesDataStore(
                ServiceLocator.getInstance().getSharedPreferences());
        //noinspection DataFlowIssue
        final SettingsManager.Builder factory = new SettingsManager.Builder(getContext(), store);

        factory.header(R.string.pc_camera);

        // default rotate 0 degrees
        factory.singleChoice(ImageHandlerViewModel.PK_CAMERA_IMAGE_AUTOROTATE,
                             R.string.pt_thumbnails_rotate_auto,
                             R.array.pe_thumbnails_rotate_auto,
                             R.array.pv_thumbnails_rotate_auto,
                             null, p -> {
                    p.setIcon(R.drawable.rotate_right_24px);
                    p.setSelectedIndex(0);
                });

        // default 0 == do nothing
        factory.singleChoice(NextAction.PK_CAMERA_IMAGE_ACTION,
                             R.string.camera_next_action,
                             R.array.pe_camera_next_action,
                             R.array.pv_camera_next_action,
                             null, p -> {
                    p.setIcon(R.drawable.tune_24px);
                    p.setSelectedIndex(0);
                });

        factory.header(R.string.option_image_replace);

        factory.bool(CoverStorage.PK_ENABLE_UNDO,
                     R.string.pt_cover_undo_enabled,
                     R.string.ps_cover_undo_disabled,
                     R.string.ps_cover_undo_enabled,
                     null, p -> {
                    p.setIcon(R.drawable.undo_24px);
                    p.setChecked(true);
                });

        factory.header(R.string.pc_image_cache);

        factory.bool(CoverStorage.PK_CACHE_RESIZED_IMAGES,
                     R.string.pt_thumbnails_cache_resized,
                     R.string.pe_thumbnails_cache_resized_each_time,
                     R.string.pe_thumbnails_cache_resized_stored,
                     this::onChangeCacheResizedImages, p -> {
                    p.setIcon(R.drawable.cached_24px);
                });

        factory.action(PSK_PURGE_IMAGE_CACHE,
                       R.string.option_purge_image_cache,
                       this::onPurgeImageCache, p -> {
                    p.setIcon(R.drawable.delete_24px);
                    p.setSummaryProvider(this::getPurgeCacheSummary);
                }
        );

        return factory;
    }

    @Override
    public void onViewCreated(@NonNull final View view,
                              @Nullable final Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        final SettingsManager settingsManager = getSettingsManager();
        final BooleanSetting pCache = settingsManager
                .requireSetting(CoverStorage.PK_CACHE_RESIZED_IMAGES);
        settingsManager.setEnabled(pCache.isChecked(), PSK_PURGE_IMAGE_CACHE);
    }

    /**
     * Called when the user changed the {@link CoverStorage#PK_CACHE_RESIZED_IMAGES} option.
     *
     * @param setting  to handle
     * @param newValue for the setting
     *
     * @return {@code true} if the change should be accepted
     */
    private boolean onChangeCacheResizedImages(@NonNull final Setting setting,
                                               @Nullable final Object newValue) {
        final boolean enabled = newValue != null && (boolean) newValue;
        getSettingsManager().setEnabled(enabled, PSK_PURGE_IMAGE_CACHE);
        return true;
    }

    /**
     * Called when the user taps the {@link #PSK_PURGE_IMAGE_CACHE} option.
     *
     * @param setting to handle
     *
     * @return {@code true} if handled
     */
    private boolean onPurgeImageCache(@NonNull final Setting setting) {
        if (ServiceLocator.getInstance().getCoverCacheDao().count() > 0) {
            final Context context = getContext();
            //noinspection DataFlowIssue
            new MaterialAlertDialogBuilder(context)
                    .setIcon(R.drawable.warning_24px)
                    .setMessage(R.string.option_purge_image_cache)
                    .setNegativeButton(R.string.cancel, (d, w) -> d.dismiss())
                    .setPositiveButton(R.string.ok, (d, w) -> {
                        ServiceLocator.getInstance().getCoverCacheDao().deleteAll();
                        // Refresh the summary
                        getSettingsManager().reload(context, PSK_PURGE_IMAGE_CACHE);
                    })
                    .create()
                    .show();
        }
        return true;
    }

    @NonNull
    private CharSequence getPurgeCacheSummary(@NonNull final Context context) {
        if (ServiceLocator.getInstance().getCoverStorage().isImageCachingEnabled()) {
            final int count = ServiceLocator.getInstance().getCoverCacheDao().count();
            final String number;
            if (count > 0) {
                number = String.valueOf(count);
            } else {
                number = getString(R.string.none);
            }
            return getString(R.string.name_colon_value, getString(R.string.lbl_covers), number);
        } else {
            return "";
        }
    }
}
