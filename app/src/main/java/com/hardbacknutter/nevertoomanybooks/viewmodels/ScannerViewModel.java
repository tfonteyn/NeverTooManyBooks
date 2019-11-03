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

import androidx.annotation.Nullable;
import androidx.lifecycle.ViewModel;

import com.hardbacknutter.nevertoomanybooks.scanner.Scanner;

/**
 * Holds the Scanner and related data.
 */
public class ScannerViewModel
        extends ViewModel {

    /** Only start the scanner automatically upon the very first start of the fragment. */
    private boolean mFirstStart = true;

    /** flag indicating the scanner is already started. */
    private boolean mScannerStarted;
    /** The preferred (or found) scanner. */
    @Nullable
    private Scanner mScanner;

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
