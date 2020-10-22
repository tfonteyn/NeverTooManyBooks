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
package com.hardbacknutter.nevertoomanybooks.viewmodels;

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

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.snackbar.Snackbar;

import java.io.File;
import java.io.IOException;

import com.hardbacknutter.nevertoomanybooks.R;
import com.hardbacknutter.nevertoomanybooks.RequestCode;
import com.hardbacknutter.nevertoomanybooks.debug.Logger;
import com.hardbacknutter.nevertoomanybooks.scanner.Scanner;
import com.hardbacknutter.nevertoomanybooks.scanner.ScannerManager;
import com.hardbacknutter.nevertoomanybooks.settings.BasePreferenceFragment;
import com.hardbacknutter.nevertoomanybooks.settings.Prefs;
import com.hardbacknutter.nevertoomanybooks.settings.SettingsActivity;
import com.hardbacknutter.nevertoomanybooks.utils.AppDir;
import com.hardbacknutter.nevertoomanybooks.utils.CameraHelper;
import com.hardbacknutter.nevertoomanybooks.utils.FileUtils;
import com.hardbacknutter.nevertoomanybooks.utils.SoundManager;

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
 *      public void onCreate(@Nullable final Bundle savedInstanceState) {
 *          mScannerModel = new ViewModelProvider(getActivity()).get(ScannerViewModel.class);
 *      }
 *      ...
 *      void doScan() {
 *          mScannerModel.scan(this);
 *      }
 *      ...
 *      public void onActivityResult(final int requestCode,
 *                                   final int resultCode,
 *                                   final Intent data) {
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
    public static final String TAG = "ScannerViewModel";

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
     * DEBUG only.
     */
    public void fakeScanInEmulator(@NonNull final Context context,
                                   @Nullable final Intent data) {
        // detect emulator for testing
        if (Build.PRODUCT.startsWith("sdk")) {
            // when used, the file must be in the root external app dir.
            final File file = AppDir.Root.getFile(context, "barcode.jpg");
            if (file.exists()) {
                final Bitmap bitmap = BitmapFactory.decodeFile(file.getAbsolutePath());
                if (data != null
                    && data.getExtras() != null
                    && data.getExtras().containsKey("data")) {
                    data.putExtra("data", bitmap);
                } else {
                    try {
                        FileUtils.copy(file, CameraHelper.getCameraFile(context));
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
        final boolean isFirst = mFirstStart;
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
     *
     * @return {@code true} if the scan was started
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
                Snackbar.make(fragment.getView(), R.string.txt_waiting_for_scanner,
                              Snackbar.LENGTH_LONG).show();

                try {
                    //noinspection BusyWait
                    Thread.sleep(SCANNER_WAIT);
                } catch (@NonNull final InterruptedException ignore) {
                    // ignore
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
        if (ScannerManager.isBeepOnValid(context)) {
            SoundManager.playFile(context, R.raw.beep_high);
        }
    }

    /**
     * Optionally beep if the scan failed.
     *
     * @param context Current context
     */
    public void onInvalidBeep(@NonNull final Context context) {
        if (ScannerManager.isBeepOnInvalid(context)) {
            SoundManager.playFile(context, R.raw.beep_low);
        }
    }


    /**
     * None of the scanners was available or working correctly.
     * Tell the user, and take them to the preferences to select a scanner.
     *
     * @param fragment hosting fragment
     */
    private void noScannerDialog(@NonNull final Fragment fragment) {

        final String msg = fragment.getString(R.string.error_bad_scanner) + '\n'
                           + fragment.getString(R.string.txt_install_scanner_recommendation);
        //noinspection ConstantConditions
        new MaterialAlertDialogBuilder(fragment.getContext())
                .setIcon(R.drawable.ic_error)
                .setTitle(R.string.pg_barcode_scanner)
                .setMessage(msg)
                .setOnDismissListener(d -> {
                    final Intent intent = new Intent(fragment.getContext(), SettingsActivity.class)
                            .putExtra(BasePreferenceFragment.BKEY_AUTO_SCROLL_TO_KEY,
                                      Prefs.PSK_BARCODE_SCANNER);

                    fragment.startActivityForResult(intent, RequestCode.SETTINGS);
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
        final String msg = fragment.getString(R.string.warning_scanner_failed_to_start)
                           + "\n\n"
                           + fragment.getString(R.string.warning_scanner_retry_install);

        //noinspection ConstantConditions
        new MaterialAlertDialogBuilder(fragment.getContext())
                .setIcon(R.drawable.ic_error)
                .setTitle(R.string.pg_barcode_scanner)
                .setMessage(msg)
                .setNeutralButton(R.string.lbl_settings, (d, w) -> {
                    final Intent intent = new Intent(fragment.getContext(), SettingsActivity.class)
                            .putExtra(BasePreferenceFragment.BKEY_AUTO_SCROLL_TO_KEY,
                                      Prefs.PSK_BARCODE_SCANNER);

                    fragment.startActivityForResult(intent, RequestCode.SETTINGS);
                })
                .setPositiveButton(R.string.action_retry, (d, w) -> {})
                .create()
                .show();
    }
}
