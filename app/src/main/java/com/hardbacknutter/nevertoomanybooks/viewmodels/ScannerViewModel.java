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
package com.hardbacknutter.nevertoomanybooks.viewmodels;

import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Build;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModel;
import androidx.preference.PreferenceManager;

import com.google.android.material.snackbar.Snackbar;

import java.io.File;
import java.io.IOException;

import com.hardbacknutter.nevertoomanybooks.R;
import com.hardbacknutter.nevertoomanybooks.UniqueId;
import com.hardbacknutter.nevertoomanybooks.debug.Logger;
import com.hardbacknutter.nevertoomanybooks.scanner.Scanner;
import com.hardbacknutter.nevertoomanybooks.scanner.ScannerManager;
import com.hardbacknutter.nevertoomanybooks.settings.BasePreferenceFragment;
import com.hardbacknutter.nevertoomanybooks.settings.Prefs;
import com.hardbacknutter.nevertoomanybooks.settings.SettingsActivity;
import com.hardbacknutter.nevertoomanybooks.utils.CameraHelper;
import com.hardbacknutter.nevertoomanybooks.utils.SoundManager;
import com.hardbacknutter.nevertoomanybooks.utils.StorageUtils;

/**
 * Holds the Scanner and related data.
 * <p>
 * Simple use case from inside a Fragment.
 * Note the activity scope used. This allows the user the pull up the side panel to change
 * the scanner... and the activity to update the model.
 * Example: {@link com.hardbacknutter.nevertoomanybooks.EditBookActivity}
 * and {@link com.hardbacknutter.nevertoomanybooks.EditBookFieldsFragment}.
 *
 * <pre>
 * {@code
 *      private ScannerViewModel mScannerModel;
 *      ...
 *      public void onActivityCreated(@Nullable final Bundle savedInstanceState) {
 *          mScannerModel = new ViewModelProvider(getActivity()).get(ScannerViewModel.class);
 *      }
 *      ...
 *      void doScan() {
 *          mScannerModel.scan(this);
 *      }
 *      ...
 *      public void onActivityResult(final int requestCode,
 *                                   final int resultCode,
 *                                   @Nullable final Intent data) {
 *          case UniqueId.REQ_SCAN_BARCODE: {
 *              mScannerModel.setScannerStarted(false);
 *              if (resultCode == Activity.RESULT_OK) {*
 *                  String barCode = mScannerModel.getScanner().getBarcode(data);
 *                  if (barCode != null) {
 *                      // do something with the barCode
 *                  }
 *              }
 *          }
 *      }
 * }
 * </pre>
 */
public class ScannerViewModel
        extends ViewModel {

    /** Log tag. */
    private static final String TAG = "ScannerViewModel";

    /** wait for the scanner; milliseconds. */
    private static final long SCANNER_WAIT = 1_000;
    /** repeat waiting for the scanner 3 times before reporting failure. */
    private static final int SCANNER_RETRY = 3;
    /** Only start the scanner automatically upon the very first start of the fragment. */
    private boolean mFirstStart = true;

    /** flag indicating the scanner is already started. */
    private boolean mScannerStarted;
    /** The preferred (or found) scanner. */
    @Nullable
    private Scanner mScanner;

    /**
     * DEBUG.
     */
    public void fakeBarcodeScan(@NonNull final Context context,
                                @Nullable final Intent data) {
        // detect emulator for testing
        if (Build.PRODUCT.startsWith("sdk")) {
            // when used, the file must be in the root external app dir.
            File file = new File(StorageUtils.getRootDir(context),
                                 "barcode.jpg");
            if (file.exists()) {
                Bitmap dummy = BitmapFactory.decodeFile(file.getAbsolutePath());
                if (data != null
                    && data.getExtras() != null
                    && data.getExtras().containsKey("data")) {
                    data.putExtra("data", dummy);
                } else {
                    try {
                        StorageUtils.copyFile(file, CameraHelper.getCameraFile(context));
                    } catch (@NonNull final IOException e) {
                        Log.d(TAG, "onActivityResult", e);
                    }
                }
            }
        }
    }

    /**
     * Get <strong>and clear</strong> the first-start flag.
     *
     * @return flag
     */
    public boolean isFirstStart() {
        boolean isFirst = mFirstStart;
        mFirstStart = false;
        return isFirst;
    }

    public void setScannerStarted(final boolean scannerStarted) {
        mScannerStarted = scannerStarted;
    }

    @Nullable
    public Scanner getScanner() {
        return mScanner;
    }

    public void resetScanner() {
        mScanner = null;
    }

    /**
     * Start scanner activity.
     *
     * @param fragment    hosting fragment
     * @param requestCode to use
     */
    public boolean scan(@NonNull final Fragment fragment,
                        final int requestCode) {
        try {
            if (mScanner == null) {
                //noinspection ConstantConditions
                mScanner = ScannerManager.getScanner(fragment.getContext());
                if (mScanner == null) {
                    noScannerDialog(fragment);
                    return false;
                }
            }

            // this is really for the Google barcode library which is loaded on first access.
            // We're very conservative/paranoid here, as we already triggered a load during startup.
            int retry = SCANNER_RETRY;
            while (!mScanner.isOperational() && retry > 0) {
                //noinspection ConstantConditions
                Snackbar.make(fragment.getView(), R.string.info_waiting_for_scanner,
                              Snackbar.LENGTH_LONG).show();

                try {
                    Thread.sleep(SCANNER_WAIT);
                } catch (@NonNull final InterruptedException ignore) {
                }
                if (mScanner.isOperational()) {
                    break;
                }
                retry--;
            }

            // ready or not, go scan
            if (!mScannerStarted) {
                if (!mScanner.startActivityForResult(fragment, requestCode)) {
                    scannerFailedDialog(fragment);
                    return false;
                }
                mScannerStarted = true;
            }

        } catch (@NonNull final RuntimeException e) {
            //noinspection ConstantConditions
            Logger.error(fragment.getContext(), TAG, e);
            noScannerDialog(fragment);
            return false;
        }

        return true;
    }

    /**
     * Optionally beep if the scan succeeded.
     *
     * @param context Current context
     */
    public void onValidBeep(@NonNull final Context context) {

        if (PreferenceManager.getDefaultSharedPreferences(context)
                             .getBoolean(Prefs.pk_sounds_scan_isbn_valid, false)) {
            SoundManager.beepHigh(context);
        }
    }

    /**
     * Optionally beep if the scan failed.
     *
     * @param context Current context
     */
    public void onInvalidBeep(@NonNull final Context context) {
        if (PreferenceManager.getDefaultSharedPreferences(context)
                             .getBoolean(Prefs.pk_sounds_scan_isbn_invalid, true)) {
            SoundManager.beepLow(context);
        }
    }

    /**
     * None of the scanners was available or working correctly.
     * Tell the user, and take them to the preferences to select a scanner.
     *
     * @param fragment hosting fragment
     */
    private void noScannerDialog(@NonNull final Fragment fragment) {

        String msg = fragment.getString(R.string.info_bad_scanner) + '\n'
                     + fragment.getString(R.string.info_install_scanner_recommendation);
        new AlertDialog.Builder(fragment.getContext())
                .setIconAttribute(android.R.attr.alertDialogIcon)
                .setTitle(R.string.pg_barcode_scanner)
                .setMessage(msg)
                .setOnDismissListener(d -> {
                    Intent intent = new Intent(fragment.getContext(), SettingsActivity.class)
                            .putExtra(BasePreferenceFragment.BKEY_AUTO_SCROLL_TO_KEY,
                                      Prefs.psk_barcode_scanner);

                    fragment.startActivityForResult(intent, UniqueId.REQ_SETTINGS);
                })
                .create()
                .show();
    }

    /**
     * The scanner (can be) was loaded, but (still) refuses to start.
     *
     * @param fragment hosting fragment
     */
    private void scannerFailedDialog(@NonNull final Fragment fragment) {
        //
        String msg = fragment.getString(R.string.warning_scanner_failed_to_start)
                     + "\n\n"
                     + fragment.getString(R.string.warning_scanner_retry_install);

        new AlertDialog.Builder(fragment.getContext())
                .setIconAttribute(android.R.attr.alertDialogIcon)
                .setTitle(R.string.pg_barcode_scanner)
                .setMessage(msg)
                .setNeutralButton(R.string.lbl_settings, (dialog, which) -> {
                    Intent intent = new Intent(fragment.getContext(), SettingsActivity.class)
                            .putExtra(BasePreferenceFragment.BKEY_AUTO_SCROLL_TO_KEY,
                                      Prefs.psk_barcode_scanner);

                    fragment.startActivityForResult(intent, UniqueId.REQ_SETTINGS);
                })
                .setPositiveButton(R.string.retry, (d, w) -> {})
                .create()
                .show();
    }
}
