/*
 * @Copyright 2019 HardBackNutter
 * @License GNU General Public License
 *
 * This file is part of NeverToManyBooks.
 *
 * In August 2018, this project was forked from:
 * Book Catalogue 5.2.2 @copyright 2010 Philip Warner & Evan Leybourn
 *
 * Without their original creation, this project would not exist in its current form.
 * It was however largely rewritten/refactored and any comments on this fork
 * should be directed at HardBackNutter and not at the original creator.
 *
 * NeverToManyBooks is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * NeverToManyBooks is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 * See the GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with NeverToManyBooks. If not, see <http://www.gnu.org/licenses/>.
 */
package com.hardbacknutter.nevertomanybooks.viewmodels;

import android.os.Bundle;

import androidx.annotation.Nullable;
import androidx.lifecycle.ViewModel;

import com.hardbacknutter.nevertomanybooks.BookSearchByIsbnFragment;

public class BookSearchByScanModel
        extends ViewModel {

    private boolean mFirstStart = true;
    /** flag indicating we're running in SCAN mode. */
    private boolean mScanMode;
    /** flag indicating the scanner is already started. */
    private boolean mScannerStarted;


    public void init(@Nullable final Bundle args) {
        // Have we been started in UI or in scan mode.
        if (args != null) {
            mScanMode = args.getBoolean(BookSearchByIsbnFragment.BKEY_IS_SCAN_MODE);
        }
    }

    public boolean isFirstStart() {
        return mFirstStart;
    }

    public void setFirstStart(final boolean firstStart) {
        mFirstStart = firstStart;
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
}
