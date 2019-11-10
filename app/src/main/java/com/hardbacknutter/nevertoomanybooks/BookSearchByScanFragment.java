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
package com.hardbacknutter.nevertoomanybooks;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Build;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.CallSuper;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.lifecycle.ViewModelProvider;

import java.io.File;
import java.io.IOException;

import com.hardbacknutter.nevertoomanybooks.debug.Logger;
import com.hardbacknutter.nevertoomanybooks.scanner.Scanner;
import com.hardbacknutter.nevertoomanybooks.scanner.ScannerManager;
import com.hardbacknutter.nevertoomanybooks.searches.SearchSites;
import com.hardbacknutter.nevertoomanybooks.settings.BasePreferenceFragment;
import com.hardbacknutter.nevertoomanybooks.settings.Prefs;
import com.hardbacknutter.nevertoomanybooks.settings.SettingsActivity;
import com.hardbacknutter.nevertoomanybooks.utils.CameraHelper;
import com.hardbacknutter.nevertoomanybooks.utils.StorageUtils;
import com.hardbacknutter.nevertoomanybooks.utils.UserMessage;
import com.hardbacknutter.nevertoomanybooks.viewmodels.ScannerViewModel;

public class BookSearchByScanFragment
        extends BookSearchByIsbnBaseFragment {

    public static final String TAG = "BookSearchByScanFrag";

    /** wait for the scanner; milliseconds. */
    private static final long SCANNER_WAIT = 1_000;
    /** repeat waiting for the scanner 3 times before reporting failure. */
    private static final int SCANNER_RETRY = 3;
    /**
     * Flag to indicate the Activity should not call 'finish()' after a failure.
     */
    private boolean mKeepAlive;

    /** The scanner. */
    @Nullable
    private ScannerViewModel mScannerModel;


    @Override
    @Nullable
    public View onCreateView(@NonNull final LayoutInflater inflater,
                             @Nullable final ViewGroup container,
                             @Nullable final Bundle savedInstanceState) {

        return inflater.inflate(R.layout.fragment_booksearch_by_scan, container, false);
    }

    @Override
    public void onActivityCreated(@Nullable final Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);

        mScannerModel = new ViewModelProvider(this).get(ScannerViewModel.class);

        //noinspection ConstantConditions
        getActivity().setTitle(R.string.title_search_isbn);

        //noinspection ConstantConditions
        getView().findViewById(R.id.btn_scan).setOnClickListener(v -> startInput());


        if (savedInstanceState == null) {
            //noinspection ConstantConditions
            SearchSites.promptToRegister(getContext(), "search",
                                         mBookSearchBaseModel.getEnabledSearchSites());
        }

        // if we already have an isbn from somewhere, auto-start a search
        String isbn = mBookSearchBaseModel.getIsbnSearchText();
        if (!isbn.isEmpty()) {
            prepareSearch(isbn);
            return;
        }

        // auto-start scanner first time.
        if (mScannerModel.isFirstStart()) {
            startInput();
        }
    }

    void onValid() {
        //noinspection ConstantConditions
        mScannerModel.onValidBeep(getContext());
    }

    void onInvalid() {
        //noinspection ConstantConditions
        mScannerModel.onInvalidBeep(getContext());
    }

    @Override
    @CallSuper
    public void onActivityResult(final int requestCode,
                                 final int resultCode,
                                 @Nullable final Intent data) {
        if (BuildConfig.DEBUG && DEBUG_SWITCHES.ON_ACTIVITY_RESULT) {
            Logger.enterOnActivityResult(TAG, requestCode, resultCode, data);
        }

        switch (requestCode) {
            case UniqueId.REQ_SCAN_BARCODE: {
                //noinspection ConstantConditions
                mScannerModel.setScannerStarted(false);
                if (resultCode == Activity.RESULT_OK) {

                    if (BuildConfig.DEBUG) {
                        // detect emulator for testing
                        if (Build.PRODUCT.startsWith("sdk")) {
                            // when used, the file must be in the root external app dir.
                            //noinspection ConstantConditions
                            File file = new File(StorageUtils.getRootDir(getContext()),
                                                 "barcode.jpg");
                            if (file.exists()) {
                                Bitmap dummy = BitmapFactory.decodeFile(file.getAbsolutePath());
                                if (data != null
                                    && data.getExtras() != null
                                    && data.getExtras().containsKey("data")) {
                                    data.putExtra("data", dummy);
                                } else {
                                    try {
                                        StorageUtils.copyFile(file, CameraHelper.getDefaultFile());
                                    } catch (@NonNull final IOException e) {
                                        e.printStackTrace();
                                    }
                                }
                            }
                        }
                    }
                    Scanner scanner = mScannerModel.getScanner();
                    //noinspection ConstantConditions
                    String barCode = scanner.getBarcode(data);
                    if (barCode != null) {
                        mBookSearchBaseModel.setIsbnSearchText(barCode);
                        prepareSearch(barCode);
                        return;
                    }
                }

                scanFailedOrCancelled();
                return;
            }
            case UniqueId.REQ_BOOK_EDIT: {
                // first do the common action when the user has saved the data for the book.
                super.onActivityResult(requestCode, resultCode, data);
                // go scan next book until the user cancels scanning.
                startInput();
                break;
            }
            case UniqueId.REQ_NAV_PANEL_SETTINGS: {
                // Make sure the scanner gets re-initialised.
                //noinspection ConstantConditions
                mScannerModel.setScanner(null);
                startInput();
                break;
            }
            case UniqueId.REQ_UPDATE_GOOGLE_PLAY_SERVICES: {
                if (resultCode == Activity.RESULT_OK) {
                    // go scan next book until the user cancels scanning.
                    startInput();
                } else {
                    scanFailedOrCancelled();
                }
                break;
            }
            default: {
                super.onActivityResult(requestCode, resultCode, data);
                break;
            }
        }
    }


    /**
     * Start scanner activity.
     */
    void startInput() {
        //noinspection ConstantConditions
        Scanner scanner = mScannerModel.getScanner();
        try {
            if (scanner == null) {
                scanner = ScannerManager.getScanner(mHostActivity);
                if (scanner == null) {
                    noScanner();
                    return;
                }
                mScannerModel.setScanner(scanner);
            }

            // this is really for the Google barcode library which is loaded on first access.
            // We're very conservative/paranoid here, as we already triggered a load during startup.
            int retry = SCANNER_RETRY;
            while (!scanner.isOperational() && retry > 0) {
                //noinspection ConstantConditions
                UserMessage.show(getView(), R.string.info_waiting_for_scanner);

                try {
                    Thread.sleep(SCANNER_WAIT);
                } catch (@NonNull final InterruptedException ignore) {
                }
                if (scanner.isOperational()) {
                    break;
                }
                retry--;
            }

            // ready or not, go scan
            if (!mScannerModel.isScannerStarted()) {
                if (!scanner.startActivityForResult(this, UniqueId.REQ_SCAN_BARCODE)) {
                    // the scanner was loaded, but (still) refuses to start.
                    scannerStartFailed();
                    return;
                }
                mScannerModel.setScannerStarted(true);
            }

        } catch (@NonNull final RuntimeException e) {
            //noinspection ConstantConditions
            Logger.error(getContext(), TAG, e);
            noScanner();
        }
    }

    private void scannerStartFailed() {
        String msg = getString(R.string.warning_scanner_failed_to_start)
                     + "\n\n"
                     + getString(R.string.warning_scanner_retry_install);

        //noinspection ConstantConditions
        new AlertDialog.Builder(getContext())
                .setIconAttribute(android.R.attr.alertDialogIcon)
                .setTitle(R.string.pg_barcode_scanner)
                .setMessage(msg)
                .setNeutralButton(R.string.lbl_settings, (d, w) -> {
                    Intent intent = new Intent(mHostActivity, SettingsActivity.class)
                            .putExtra(BasePreferenceFragment.BKEY_AUTO_SCROLL_TO_KEY,
                                      Prefs.psk_barcode_scanner);

                    mHostActivity.startActivityForResult(intent, UniqueId.REQ_NAV_PANEL_SETTINGS);
                })
                .setPositiveButton(R.string.retry, (d, w) -> {
                    // yes, nothing to do. Closing this dialog should bring up the scanner again
                })
                .create()
                .show();
        mKeepAlive = true;
    }

    /**
     * None of the scanners was available or working correctly.
     * Tell the user, and take them to the preferences to select a scanner.
     */
    private void noScanner() {
        String msg = getString(R.string.info_bad_scanner) + '\n'
                     + getString(R.string.info_install_scanner_recommendation);
        //noinspection ConstantConditions
        new AlertDialog.Builder(getContext())
                .setIconAttribute(android.R.attr.alertDialogIcon)
                .setTitle(R.string.pg_barcode_scanner)
                .setMessage(msg)
                .setOnDismissListener(d -> {
                    Intent intent = new Intent(mHostActivity, SettingsActivity.class)
                            .putExtra(BasePreferenceFragment.BKEY_AUTO_SCROLL_TO_KEY,
                                      Prefs.psk_barcode_scanner);

                    mHostActivity.startActivityForResult(intent, UniqueId.REQ_NAV_PANEL_SETTINGS);
                })
                .create()
                .show();
        mKeepAlive = true;
    }

    /**
     * Scanner Cancelled/failed.
     * Pass the last book we got back to our caller and finish here.
     */
    private void scanFailedOrCancelled() {
        Intent resultData = mBookSearchBaseModel.getActivityResultData();
        if (resultData.getExtras() != null) {
            mHostActivity.setResult(Activity.RESULT_OK, resultData);
        }

        // and exit if no dialog present.
        if (!mKeepAlive) {
            mHostActivity.finish();
        }
    }

}
