/*
 * @Copyright 2020 HardBackNutter
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

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Bundle;
import android.text.TextUtils;

import androidx.annotation.CallSuper;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import androidx.fragment.app.DialogFragment;
import androidx.fragment.app.FragmentManager;
import androidx.lifecycle.ViewModelProvider;
import androidx.preference.EditTextPreference;
import androidx.preference.ListPreference;
import androidx.preference.MultiSelectListPreference;
import androidx.preference.Preference;
import androidx.preference.PreferenceFragmentCompat;
import androidx.preference.PreferenceScreen;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.snackbar.Snackbar;

import java.io.IOException;
import java.util.Arrays;
import java.util.Objects;
import java.util.regex.Pattern;

import com.hardbacknutter.nevertoomanybooks.BaseActivity;
import com.hardbacknutter.nevertoomanybooks.BuildConfig;
import com.hardbacknutter.nevertoomanybooks.DEBUG_SWITCHES;
import com.hardbacknutter.nevertoomanybooks.R;
import com.hardbacknutter.nevertoomanybooks.RequestCode;
import com.hardbacknutter.nevertoomanybooks.booklist.RowStateDAO;
import com.hardbacknutter.nevertoomanybooks.database.CoversDAO;
import com.hardbacknutter.nevertoomanybooks.database.DBHelper;
import com.hardbacknutter.nevertoomanybooks.database.tasks.Scheduler;
import com.hardbacknutter.nevertoomanybooks.debug.DebugReport;
import com.hardbacknutter.nevertoomanybooks.debug.ErrorMsg;
import com.hardbacknutter.nevertoomanybooks.debug.Logger;
import com.hardbacknutter.nevertoomanybooks.dialogs.TipManager;
import com.hardbacknutter.nevertoomanybooks.scanner.ScannerManager;
import com.hardbacknutter.nevertoomanybooks.utils.AppDir;
import com.hardbacknutter.nevertoomanybooks.utils.DateUtils;
import com.hardbacknutter.nevertoomanybooks.utils.FileUtils;
import com.hardbacknutter.nevertoomanybooks.utils.SoundManager;
import com.hardbacknutter.nevertoomanybooks.viewmodels.ResultDataModel;
import com.hardbacknutter.nevertoomanybooks.widgets.BitmaskPreference;

/**
 * Base settings page.
 * <p>
 * Uses OnSharedPreferenceChangeListener to dynamically update the summary for each preference.
 */
public abstract class BasePreferenceFragment
        extends PreferenceFragmentCompat
        implements SharedPreferences.OnSharedPreferenceChangeListener {

    /**
     * The user modified the scanner in preferences (or not).
     * <p>
     * <br>type: {@code boolean}
     * setResult
     */
    public static final String BKEY_SCANNER_MODIFIED = "scannerModified";
    /** Log tag. */
    private static final String TAG = "BasePreferenceFragment";
    /** Allows auto-scrolling on opening the preference screen to the desired key. */
    public static final String BKEY_AUTO_SCROLL_TO_KEY = TAG + ":scrollTo";
    private static final String DIALOG_FRAGMENT_TAG = TAG + ":dialog";
    private static final int REQ_BITMASK_DIALOG = 0;
    private static final int REQ_PICK_FILE_FOR_EXPORT_DATABASE = 1;
    private static final Pattern SPACE_PATTERN = Pattern.compile(" ", Pattern.LITERAL);
    private static final Pattern COLON_PATTERN = Pattern.compile(":", Pattern.LITERAL);

    protected ResultDataModel mResultDataModel;
    @Nullable
    private String mAutoScrollToKey;

    @Override
    public void onCreate(@Nullable final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        Bundle args = getArguments();
        if (args != null) {
            mAutoScrollToKey = args.getString(BKEY_AUTO_SCROLL_TO_KEY);
        }

        initListeners();
    }

    @Override
    public void onActivityCreated(@Nullable final Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);

        //noinspection ConstantConditions
        mResultDataModel = new ViewModelProvider(getActivity()).get(ResultDataModel.class);
    }

    /**
     * Hook up specific listeners/preferences.
     */
    @CallSuper
    protected void initListeners() {
        Preference preference;

        // Purge image cache database table.
        preference = findPreference(Prefs.PSK_PURGE_IMAGE_CACHE);
        if (preference != null) {
            preference.setOnPreferenceClickListener(p -> {
                //noinspection ConstantConditions
                new MaterialAlertDialogBuilder(getContext())
                        .setIconAttribute(android.R.attr.alertDialogIcon)
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

        preference = findPreference(Prefs.PSK_SEARCH_SITE_ORDER);
        if (preference != null) {
            preference.setOnPreferenceClickListener(p -> {
                Intent intent = new Intent(getContext(), SearchAdminActivity.class);
                startActivityForResult(intent, RequestCode.PREFERRED_SEARCH_SITES);
                return true;
            });
        }

        // Purge BLNS database table.
        preference = findPreference(Prefs.PSK_PURGE_BLNS);
        if (preference != null) {
            preference.setOnPreferenceClickListener(p -> {
                //noinspection ConstantConditions
                new MaterialAlertDialogBuilder(getContext())
                        .setIconAttribute(android.R.attr.alertDialogIcon)
                        .setTitle(R.string.lbl_purge_blns)
                        .setMessage(R.string.info_purge_blns_all)
                        .setNegativeButton(android.R.string.cancel, (d, w) -> d.dismiss())
                        .setPositiveButton(android.R.string.ok, (d, w) -> RowStateDAO.clearAll())
                        .create()
                        .show();
                return true;
            });
        }

        preference = findPreference(Prefs.PSK_TIP_RESET_ALL);
        if (preference != null) {
            preference.setOnPreferenceClickListener(p -> {
                //noinspection ConstantConditions
                TipManager.reset(getContext());
                //noinspection ConstantConditions
                Snackbar.make(getView(), R.string.tip_reset_done, Snackbar.LENGTH_LONG).show();
                return true;
            });
        }

        preference = findPreference(Prefs.PSK_REBUILD_FTS);
        if (preference != null) {
            preference.setOnPreferenceClickListener(p -> {
                //noinspection ConstantConditions
                new MaterialAlertDialogBuilder(getContext())
                        .setIconAttribute(android.R.attr.alertDialogIcon)
                        .setTitle(R.string.menu_rebuild_fts)
                        .setMessage(R.string.confirm_rebuild_fts)
                        .setNegativeButton(android.R.string.cancel, (d, w) -> {
                            Scheduler.scheduleFtsRebuild(getContext(), false);
                            p.setSummary(null);
                        })
                        .setPositiveButton(android.R.string.ok, (d, w) -> {
                            Scheduler.scheduleFtsRebuild(getContext(), true);
                            p.setSummary(R.string.txt_rebuild_scheduled);
                        })
                        .create()
                        .show();
                return true;
            });
        }

        preference = findPreference(Prefs.PSK_REBUILD_INDEX);
        if (preference != null) {
            preference.setOnPreferenceClickListener(p -> {
                //noinspection ConstantConditions
                new MaterialAlertDialogBuilder(getContext())
                        .setIconAttribute(android.R.attr.alertDialogIcon)
                        .setTitle(R.string.menu_rebuild_index)
                        .setMessage(R.string.confirm_rebuild_index)
                        .setNegativeButton(android.R.string.cancel, (d, w) -> {
                            Scheduler.scheduleIndexRebuild(getContext(), false);
                            p.setSummary(null);
                        })
                        .setPositiveButton(android.R.string.ok, (d, w) -> {
                            Scheduler.scheduleIndexRebuild(getContext(), true);
                            p.setSummary(R.string.txt_rebuild_scheduled);
                        })
                        .create()
                        .show();
                return true;
            });
        }

        preference = findPreference(Prefs.PSK_PURGE_FILES);
        if (preference != null) {
            preference.setOnPreferenceClickListener(p -> {
                //noinspection ConstantConditions
                long bytes = AppDir.purge(getContext(), false);
                String msg = getString(R.string.txt_cleanup_files,
                                       FileUtils.formatFileSize(getContext(), bytes),
                                       getString(R.string.lbl_send_debug_info));

                new MaterialAlertDialogBuilder(getContext())
                        .setIcon(R.drawable.ic_warning)
                        .setTitle(R.string.lbl_purge_files)
                        .setMessage(msg)
                        .setNegativeButton(android.R.string.cancel, (d, w) -> d.dismiss())
                        .setPositiveButton(android.R.string.ok, (d, w) ->
                                AppDir.purge(getContext(), true))
                        .create()
                        .show();
                return true;
            });
        }

        preference = findPreference(Prefs.PSK_SEND_DEBUG_INFO);
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

        preference = findPreference(Prefs.PSK_EXPORT_DATABASE);
        if (preference != null) {
            // Export database - Mainly meant for debug or external processing.
            preference.setOnPreferenceClickListener(p -> {
                String name = SPACE_PATTERN.matcher(DateUtils.localSqlDateForToday())
                                           .replaceAll("-");
                name = COLON_PATTERN.matcher(name).replaceAll("");

                final Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT)
                        .addCategory(Intent.CATEGORY_OPENABLE)
                        .setType("*/*")
                        .putExtra(Intent.EXTRA_TITLE,
                                  getString(R.string.app_name) + '-' + name + ".ntmb.db");
                startActivityForResult(intent, REQ_PICK_FILE_FOR_EXPORT_DATABASE);
                return true;
            });
        }
    }

    @Override
    public void onResume() {
        super.onResume();

        PreferenceScreen screen = getPreferenceScreen();
        screen.getSharedPreferences().registerOnSharedPreferenceChangeListener(this);

        // Set the summaries reflecting the current values for all Preferences.
        for (String key : screen.getSharedPreferences().getAll().keySet()) {
            updateSummary(key);
        }

        if (mAutoScrollToKey != null) {
            scrollToPreference(mAutoScrollToKey);
            mAutoScrollToKey = null;
        }
    }

    @Override
    @CallSuper
    public void onSharedPreferenceChanged(@NonNull final SharedPreferences sharedPreferences,
                                          @NonNull final String key) {
        switch (key) {
            case Prefs.pk_ui_locale:
            case Prefs.pk_ui_theme:
            case Prefs.pk_sort_title_reordered:
            case Prefs.pk_show_title_reordered:
                mResultDataModel.putResultData(BaseActivity.BKEY_RECREATE, true);
                break;

            case Prefs.pk_scanner_preferred:
                //noinspection ConstantConditions
                ScannerManager.installScanner(getActivity(), success -> {
                    if (!success) {
                        //noinspection ConstantConditions
                        ScannerManager.setDefaultScanner(getContext());
                    }
                });
                mResultDataModel.putResultData(BKEY_SCANNER_MODIFIED, true);
                break;

            case Prefs.pk_sounds_scan_found_barcode:
                if (sharedPreferences.getBoolean(key, false)) {
                    //noinspection ConstantConditions
                    SoundManager.playFile(getContext(), R.raw.zxing_beep);
                }
                break;

            case Prefs.pk_sounds_scan_isbn_valid:
                if (sharedPreferences.getBoolean(key, false)) {
                    //noinspection ConstantConditions
                    SoundManager.playFile(getContext(), R.raw.beep_high);
                }
                break;

            case Prefs.pk_sounds_scan_isbn_invalid:
                if (sharedPreferences.getBoolean(key, false)) {
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

        switch (requestCode) {
            case RequestCode.PREFERRED_SEARCH_SITES:
                if (resultCode == Activity.RESULT_OK && data != null) {
                    mResultDataModel.putResultData(data);
                }
                break;

            case REQ_PICK_FILE_FOR_EXPORT_DATABASE:
                if (resultCode == Activity.RESULT_OK) {
                    Objects.requireNonNull(data, ErrorMsg.NULL_INTENT_DATA);
                    final Uri uri = data.getData();
                    if (uri != null) {
                        @StringRes
                        int msgId;
                        try {
                            final Context context = getContext();
                            //noinspection ConstantConditions
                            FileUtils.copy(context, DBHelper.getDatabasePath(context), uri);
                            msgId = R.string.progress_end_backup_success;
                        } catch (@NonNull final IOException e) {
                            Logger.error(getContext(), TAG, e);
                            msgId = R.string.error_backup_failed;
                        }
                        //noinspection ConstantConditions
                        Snackbar.make(getView(), msgId, Snackbar.LENGTH_LONG).show();
                    }
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
            final FragmentManager fm = getParentFragmentManager();
            // check if dialog is already showing
            if (fm.findFragmentByTag(DIALOG_FRAGMENT_TAG) != null) {
                return;
            }
            final DialogFragment frag = BitmaskPreference.BitmaskPreferenceDialogFragment
                    .newInstance((BitmaskPreference) preference);
            frag.setTargetFragment(this, REQ_BITMASK_DIALOG);
            frag.show(fm, DIALOG_FRAGMENT_TAG);
            return;
        }

        super.onDisplayPreferenceDialog(preference);
    }

    @Override
    public void onPause() {
        getPreferenceScreen().getSharedPreferences()
                             .unregisterOnSharedPreferenceChangeListener(this);
        super.onPause();
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
        Preference preference = findPreference(key);
        if (preference != null) {
            preference.setSummary(getValueAsString(preference));
        }
    }

    /**
     * Get the current string value for a single Preference.
     *
     * @param preference to get the value of
     *
     * @return the value string
     */
    @NonNull
    private CharSequence getValueAsString(@NonNull final Preference preference) {
        if (preference instanceof ListPreference) {
            CharSequence value = ((ListPreference) preference).getEntry();
            return value != null ? value : getString(R.string.hint_not_set);
        }
        if (preference instanceof EditTextPreference) {
            return ((EditTextPreference) preference).getText();
        }

        if (preference instanceof BitmaskPreference) {
            BitmaskPreference bmp = (BitmaskPreference) preference;
            if (!bmp.isActive()) {
                return bmp.getNotSetSummary();
            }
            // if it is in use, drop through to MultiSelectListPreference
        }

        if (preference instanceof MultiSelectListPreference) {
            MultiSelectListPreference msp = (MultiSelectListPreference) preference;
            StringBuilder text = new StringBuilder();
            for (String s : msp.getValues()) {
                int index = msp.findIndexOfValue(s);
                if (index >= 0) {
                    text.append(msp.getEntries()[index]).append('\n');

                } else {
                    // This re-surfaces sometimes after a careless dev. change.
                    Logger.warnWithStackTrace(
                            preference.getContext(), TAG,
                            "MultiSelectListPreference:"
                            + "\n s=" + s
                            + "\n key=" + msp.getKey()
                            + "\n entries="
                            + TextUtils.join(",", Arrays.asList(msp.getEntries()))
                            + "\n entryValues="
                            + TextUtils.join(",", Arrays.asList(msp.getEntryValues()))
                            + "\n values=" + msp.getValues());
                }

            }
            if (text.length() > 0) {
                return text;
            } else {
                // the preference has no values set, but that is a VALID setting and will be used.
                return preference.getContext().getString(R.string.none);
            }
        } else {
            return "";
        }
    }
}
