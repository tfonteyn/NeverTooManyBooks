/*
 * @Copyright 2020 HardBackNutter
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

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.text.TextUtils;

import androidx.annotation.CallSuper;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.FragmentManager;
import androidx.lifecycle.ViewModelProvider;
import androidx.preference.EditTextPreference;
import androidx.preference.ListPreference;
import androidx.preference.MultiSelectListPreference;
import androidx.preference.Preference;
import androidx.preference.PreferenceFragmentCompat;
import androidx.preference.PreferenceGroup;
import androidx.preference.PreferenceScreen;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.snackbar.Snackbar;

import java.util.ArrayList;

import com.hardbacknutter.nevertoomanybooks.BaseActivity;
import com.hardbacknutter.nevertoomanybooks.BuildConfig;
import com.hardbacknutter.nevertoomanybooks.DEBUG_SWITCHES;
import com.hardbacknutter.nevertoomanybooks.R;
import com.hardbacknutter.nevertoomanybooks.RequestCode;
import com.hardbacknutter.nevertoomanybooks.database.CoversDAO;
import com.hardbacknutter.nevertoomanybooks.database.DAO;
import com.hardbacknutter.nevertoomanybooks.debug.DebugReport;
import com.hardbacknutter.nevertoomanybooks.debug.Logger;
import com.hardbacknutter.nevertoomanybooks.dialogs.TipManager;
import com.hardbacknutter.nevertoomanybooks.goodreads.GoodreadsRegistrationActivity;
import com.hardbacknutter.nevertoomanybooks.searches.librarything.LibraryThingRegistrationActivity;
import com.hardbacknutter.nevertoomanybooks.utils.AppDir;
import com.hardbacknutter.nevertoomanybooks.utils.FileUtils;
import com.hardbacknutter.nevertoomanybooks.utils.SoundManager;
import com.hardbacknutter.nevertoomanybooks.viewmodels.ResultDataModel;
import com.hardbacknutter.nevertoomanybooks.viewmodels.StartupViewModel;
import com.hardbacknutter.nevertoomanybooks.widgets.BitmaskPreference;

/**
 * Base settings page.
 * <p>
 * Uses OnSharedPreferenceChangeListener to dynamically update the summary for each preference.
 */
public abstract class BasePreferenceFragment
        extends PreferenceFragmentCompat
        implements SharedPreferences.OnSharedPreferenceChangeListener {

    /** Log tag. */
    private static final String TAG = "BasePreferenceFragment";

    /** Allows auto-scrolling on opening the preference screen to the desired key. */
    @SuppressWarnings("WeakerAccess")
    public static final String BKEY_AUTO_SCROLL_TO_KEY = TAG + ":scrollTo";

    protected ResultDataModel mResultData;
    @Nullable
    private String mAutoScrollToKey;

    @Override
    @CallSuper
    public void onCreatePreferences(final Bundle savedInstanceState,
                                    final String rootKey) {
        final Bundle args = getArguments();
        if (args != null) {
            mAutoScrollToKey = args.getString(BKEY_AUTO_SCROLL_TO_KEY);
        }

        //noinspection ConstantConditions
        mResultData = new ViewModelProvider(getActivity()).get(ResultDataModel.class);
    }

    /**
     * Hook up specific listeners/preferences.
     */
    @Override
    public void onStart() {
        super.onStart();

        Preference preference;

        // there is overhead here in always trying to find all preferences listed,
        // instead of doing this in the sub classes.
        // For now, this allows us to move preferences (or even duplicate) easier.

        preference = findPreference("psk_credentials_goodreads");
        if (preference != null) {
            preference.setOnPreferenceClickListener(p -> {
                startActivity(new Intent(getContext(), GoodreadsRegistrationActivity.class));
                return true;
            });
        }

        preference = findPreference("psk_credentials_library_thing");
        if (preference != null) {
            preference.setOnPreferenceClickListener(p -> {
                startActivity(new Intent(getContext(), LibraryThingRegistrationActivity.class));
                return true;
            });
        }

        // Purge image cache database table.
        preference = findPreference("psk_purge_image_cache");
        if (preference != null) {
            preference.setOnPreferenceClickListener(p -> {
                //noinspection ConstantConditions
                new MaterialAlertDialogBuilder(getContext())
                        .setIcon(R.drawable.ic_warning)
                        .setTitle(R.string.lbl_purge_image_cache)
                        .setMessage(R.string.lbl_purge_image_cache)
                        .setNegativeButton(android.R.string.cancel, (d, w) -> d.dismiss())
                        .setPositiveButton(android.R.string.ok, (d, w) ->
                                CoversDAO.deleteAll(getContext()))
                        .create()
                        .show();
                return true;
            });
        }

        preference = findPreference("psk_search_site_order");
        if (preference != null) {
            preference.setOnPreferenceClickListener(p -> {
                final Intent intent = new Intent(getContext(), SearchAdminActivity.class);
                startActivityForResult(intent, RequestCode.EDIT_SEARCH_SITES);
                return true;
            });
        }

        // Purge BLNS database table.
        preference = findPreference("psk_purge_blns");
        if (preference != null) {
            preference.setOnPreferenceClickListener(p -> {
                //noinspection ConstantConditions
                new MaterialAlertDialogBuilder(getContext())
                        .setIcon(R.drawable.ic_warning)
                        .setTitle(R.string.lbl_purge_blns)
                        .setMessage(R.string.info_purge_blns_all)
                        .setNegativeButton(android.R.string.cancel, (d, w) -> d.dismiss())
                        .setPositiveButton(android.R.string.ok, (d, w) -> DAO.clearNodeStateData())
                        .create()
                        .show();
                return true;
            });
        }

        preference = findPreference("psk_tip_reset_all");
        if (preference != null) {
            preference.setOnPreferenceClickListener(p -> {
                //noinspection ConstantConditions
                TipManager.reset(getContext());
                //noinspection ConstantConditions
                Snackbar.make(getView(), R.string.tip_reset_done, Snackbar.LENGTH_LONG).show();
                return true;
            });
        }

        preference = findPreference("psk_rebuild_fts");
        if (preference != null) {
            preference.setOnPreferenceClickListener(p -> {
                //noinspection ConstantConditions
                new MaterialAlertDialogBuilder(getContext())
                        .setIcon(R.drawable.ic_warning)
                        .setTitle(R.string.menu_rebuild_fts)
                        .setMessage(R.string.confirm_rebuild_fts)
                        .setNegativeButton(android.R.string.cancel, (d, w) -> {
                            StartupViewModel.scheduleFtsRebuild(getContext(), false);
                            p.setSummary(null);
                        })
                        .setPositiveButton(android.R.string.ok, (d, w) -> {
                            StartupViewModel.scheduleFtsRebuild(getContext(), true);
                            p.setSummary(R.string.txt_rebuild_scheduled);
                        })
                        .create()
                        .show();
                return true;
            });
        }

        preference = findPreference("psk_rebuild_index");
        if (preference != null) {
            preference.setOnPreferenceClickListener(p -> {
                //noinspection ConstantConditions
                new MaterialAlertDialogBuilder(getContext())
                        .setIcon(R.drawable.ic_warning)
                        .setTitle(R.string.menu_rebuild_index)
                        .setMessage(R.string.confirm_rebuild_index)
                        .setNegativeButton(android.R.string.cancel, (d, w) -> {
                            StartupViewModel.scheduleIndexRebuild(getContext(), false);
                            p.setSummary(null);
                        })
                        .setPositiveButton(android.R.string.ok, (d, w) -> {
                            StartupViewModel.scheduleIndexRebuild(getContext(), true);
                            p.setSummary(R.string.txt_rebuild_scheduled);
                        })
                        .create()
                        .show();
                return true;
            });
        }

        preference = findPreference("psk_purge_files");
        if (preference != null) {
            preference.setOnPreferenceClickListener(p -> {
                final Context context = getContext();
                final ArrayList<String> bookUuidList;
                try (DAO db = new DAO(TAG)) {
                    bookUuidList = db.getBookUuidList();
                }

                //noinspection ConstantConditions
                final long bytes = AppDir.purge(context, bookUuidList, false);
                final String msg = getString(R.string.txt_cleanup_files,
                                             FileUtils.formatFileSize(context, bytes),
                                             getString(R.string.lbl_send_debug));

                new MaterialAlertDialogBuilder(context)
                        .setIcon(R.drawable.ic_warning)
                        .setTitle(R.string.lbl_purge_files)
                        .setMessage(msg)
                        .setNegativeButton(android.R.string.cancel, (d, w) -> d.dismiss())
                        .setPositiveButton(android.R.string.ok, (d, w) ->
                                AppDir.purge(context, bookUuidList, true))
                        .create()
                        .show();
                return true;
            });
        }

        preference = findPreference("psk_send_debug_info");
        if (preference != null) {
            preference.setOnPreferenceClickListener(p -> {
                //noinspection ConstantConditions
                new MaterialAlertDialogBuilder(getContext())
                        .setIcon(R.drawable.ic_warning)
                        .setTitle(R.string.debug)
                        .setMessage(R.string.debug_send_info_text)
                        .setNegativeButton(android.R.string.cancel, (d, w) -> d.dismiss())
                        .setPositiveButton(android.R.string.ok, (d, w) -> {
                            if (!DebugReport.sendDebugInfo(getContext())) {
                                //noinspection ConstantConditions
                                Snackbar.make(getView(), R.string.error_email_failed,
                                              Snackbar.LENGTH_LONG).show();
                            }
                        })
                        .create()
                        .show();
                return true;
            });
        }
    }

    @Override
    public void onResume() {
        super.onResume();

        final PreferenceScreen screen = getPreferenceScreen();
        screen.getSharedPreferences().registerOnSharedPreferenceChangeListener(this);

        // Set the summaries reflecting the current values.
        updateSummaries(screen);

        if (mAutoScrollToKey != null) {
            scrollToPreference(mAutoScrollToKey);
            mAutoScrollToKey = null;
        }
    }

    @Override
    public void onPause() {
        getPreferenceScreen().getSharedPreferences()
                             .unregisterOnSharedPreferenceChangeListener(this);
        super.onPause();
    }

    /**
     * Recursively update the summary for all preferences in the given group.
     *
     * @param group to update
     */
    private void updateSummaries(@NonNull final PreferenceGroup group) {
        for (int i = 0; i < group.getPreferenceCount(); i++) {
            final Preference p = group.getPreference(i);
            if (p instanceof PreferenceGroup) {
                updateSummaries((PreferenceGroup) p);
            } else {
                final String key = p.getKey();
                if (key != null) {
                    updateSummary(key);
                }
            }
        }
    }

    @Override
    @CallSuper
    public void onSharedPreferenceChanged(@NonNull final SharedPreferences preferences,
                                          @NonNull final String key) {
        switch (key) {
            case Prefs.pk_ui_locale:
            case Prefs.pk_ui_theme:
            case Prefs.pk_sort_title_reordered:
            case Prefs.pk_show_title_reordered:
                mResultData.putResultData(BaseActivity.BKEY_PREF_CHANGE_REQUIRES_RECREATE, true);
                break;

            case Prefs.pk_sounds_scan_found_barcode:
                if (preferences.getBoolean(key, false)) {
                    //noinspection ConstantConditions
                    SoundManager.playFile(getContext(), R.raw.zxing_beep);
                }
                break;

            case Prefs.pk_sounds_scan_isbn_valid:
                if (preferences.getBoolean(key, false)) {
                    //noinspection ConstantConditions
                    SoundManager.playFile(getContext(), R.raw.beep_high);
                }
                break;

            case Prefs.pk_sounds_scan_isbn_invalid:
                if (preferences.getBoolean(key, false)) {
                    //noinspection ConstantConditions
                    SoundManager.playFile(getContext(), R.raw.beep_low);
                }
                break;

            default:
                break;
        }

        // Update the summary after a change.
        updateSummary(key);
    }

    @Override
    public void onActivityResult(final int requestCode,
                                 final int resultCode,
                                 @Nullable final Intent data) {
        if (BuildConfig.DEBUG && DEBUG_SWITCHES.ON_ACTIVITY_RESULT) {
            Logger.enterOnActivityResult(TAG, requestCode, resultCode, data);
        }

        //noinspection SwitchStatementWithTooFewBranches
        switch (requestCode) {
            case RequestCode.EDIT_SEARCH_SITES:
                if (resultCode == Activity.RESULT_OK && data != null) {
                    mResultData.putResultData(data);
                }
                break;

            default:
                super.onActivityResult(requestCode, resultCode, data);
                break;
        }
    }

    /**
     * BitmaskPreference get a custom Dialog were the neutral button displays
     * the "unused" option.
     *
     * <br><br>{@inheritDoc}
     */
    @Override
    public void onDisplayPreferenceDialog(@NonNull final Preference preference) {
        if (preference instanceof BitmaskPreference) {
            final String fragmentTag = "BitmaskPreferenceDialog";
            // getParentFragmentManager required by PreferenceDialogFragmentCompat
            // as the latter insists on using setTargetFragment to communicate back.
            final FragmentManager fm = getParentFragmentManager();
            // check if dialog is already showing
            if (fm.findFragmentByTag(fragmentTag) != null) {
                return;
            }

            BitmaskPreference.BitmaskPreferenceDialogFragment
                    .newInstance(this, (BitmaskPreference) preference)
                    .show(fm, fragmentTag);
            return;
        }

        super.onDisplayPreferenceDialog(preference);
    }


    /**
     * Update the summary for the given key.
     * <p>
     * TODO: maybe implement {@link Preference.SummaryProvider}
     *
     * @param key for preference.
     */
    @CallSuper
    protected void updateSummary(@NonNull final String key) {
        final Preference preference = findPreference(key);
        if (preference != null) {
            final CharSequence summary = getValueAsString(preference);
            if (summary != null) {
                preference.setSummary(summary);
            }
        }
    }

    /**
     * Get the current string value for a single Preference.
     *
     * @param preference to get the value of
     *
     * @return the value string, or {@code null} if the preference had no values
     */
    @Nullable
    private CharSequence getValueAsString(@NonNull final Preference preference) {
        if (preference instanceof ListPreference) {
            final CharSequence value = ((ListPreference) preference).getEntry();
            return value != null ? value : getString(R.string.hint_not_set);
        }

        if (preference instanceof EditTextPreference) {
            return ((EditTextPreference) preference).getText();
        }

        if (preference instanceof BitmaskPreference) {
            final BitmaskPreference bmp = (BitmaskPreference) preference;
            if (!bmp.isActive()) {
                return bmp.getDisregardSummaryText();
            }
            // if it is in use, drop through to MultiSelectListPreference
        }

        if (preference instanceof MultiSelectListPreference) {
            final MultiSelectListPreference msp = (MultiSelectListPreference) preference;
            final StringBuilder text = new StringBuilder();
            for (final String s : msp.getValues()) {
                final int index = msp.findIndexOfValue(s);
                if (index >= 0) {
                    text.append(msp.getEntries()[index]).append('\n');

                } else {
                    // This re-surfaces sometimes after a careless dev. change.
                    //noinspection ConstantConditions
                    Logger.error(getContext(), TAG, new Throwable(),
                                 "MultiSelectListPreference:"
                                 + "\n s=" + s
                                 + "\n key=" + msp.getKey()
                                 + "\n entries=" + TextUtils.join(",", msp.getEntries())
                                 + "\n entryValues=" + TextUtils.join(",", msp.getEntryValues())
                                 + "\n values=" + msp.getValues());
                }

            }
            if (text.length() > 0) {
                return text;
            } else {
                // the preference has no values set, but that is a VALID setting and will be used.
                return getString(R.string.none);
            }
        }

        return null;
    }
}
