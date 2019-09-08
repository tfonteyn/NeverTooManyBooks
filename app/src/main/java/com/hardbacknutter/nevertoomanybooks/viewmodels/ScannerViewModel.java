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

import android.content.Intent;
import android.os.Bundle;

import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModel;

import com.hardbacknutter.nevertoomanybooks.BookSearchByIsbnFragment;
import com.hardbacknutter.nevertoomanybooks.scanner.Scanner;

/**
 * Holds the Scanner and related data.
 */
public class ScannerViewModel
        extends ViewModel {

    /** Only start the scanner automatically upon the very first start of the fragment. */
    private boolean mFirstStart = true;
    /** flag indicating we're running in SCAN mode. */
    private boolean mScanMode;
    /** flag indicating the scanner is already started. */
    private boolean mScannerStarted;
    /** The preferred (or found) scanner. */
    @Nullable
    private Scanner mScanner;

    /**
     * Pseudo constructor.
     *
     * @param args {@link Intent#getExtras()} or {@link Fragment#getArguments()}
     */
    public void init(@Nullable final Bundle args) {
        // Have we been started in UI or in scan mode.
        if (args != null) {
            mScanMode = args.getBoolean(BookSearchByIsbnFragment.BKEY_IS_SCAN_MODE);
        }
    }

    public boolean isFirstStart() {
        return mFirstStart;
    }

    public void clearFirstStart() {
        mFirstStart = false;
    }

    public boolean isScanMode() {
        return mScanMode;
    }

    public boolean isScannerStarted() {
        return mScannerStarted;
    }

    public void setScannerStarted(final boolean scannerStarted) {
        mScannerStarted = scannerStarted;
    }

    @Nullable
    public Scanner getScanner() {
        return mScanner;
    }

    public void setScanner(@Nullable final Scanner scanner) {
        mScanner = scanner;
    }
}
