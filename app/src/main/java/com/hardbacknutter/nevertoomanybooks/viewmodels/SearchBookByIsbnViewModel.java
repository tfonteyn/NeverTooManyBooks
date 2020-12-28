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

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;

import androidx.annotation.IntDef;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.util.Pair;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModel;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.stream.Collectors;

import com.hardbacknutter.nevertoomanybooks.database.DAO;
import com.hardbacknutter.nevertoomanybooks.utils.ISBN;

public class SearchBookByIsbnViewModel
        extends ViewModel
        implements ActivityResultViewModel {

    /** Manual entry by user. */
    public static final int SCANNER_OFF = 0;
    /** Scan and search/edit loop, until scanning is cancelled. */
    public static final int SCANNER_MODE_SINGLE = 1;
    /** Scan and queue the code, until scanning is cancelled. */
    public static final int SCANNER_MODE_BATCH = 2;

    /** Log tag. */
    private static final String TAG = "SearchBookByIsbnViewModel";

    public static final String BKEY_SCAN_MODE = TAG + ":scanMode";
    public static final String BKEY_ISBN_LIST = TAG + ":isbnList";

    /** Accumulate all data that will be send in {@link Activity#setResult}. */
    @NonNull
    private final Intent mResultIntent = new Intent();
    /** The batch mode queue. */
    private final List<ISBN> mScanQueue = new LinkedList<>();
    /** Database Access. */
    private DAO mDb;
    @Mode
    private int mScannerMode;
    /** Only start the scanner automatically upon the very first start of the fragment. */
    private boolean mFirstStart = true;

    /**
     * Inherits the result from {@link com.hardbacknutter.nevertoomanybooks.EditBookActivity}.
     */
    @Override
    @NonNull
    public Intent getResultIntent() {
        return mResultIntent;
    }

    @Override
    protected void onCleared() {
        if (mDb != null) {
            mDb.close();
        }

        super.onCleared();
    }

    /**
     * Pseudo constructor.
     *
     * @param args {@link Intent#getExtras()} or {@link Fragment#getArguments()}
     */
    public void init(@Nullable final Bundle args) {
        if (mDb == null) {
            mDb = new DAO(TAG);

            if (args != null) {
                mScannerMode = args.getInt(BKEY_SCAN_MODE, SCANNER_OFF);

                final List<String> isbnList = args.getStringArrayList(BKEY_ISBN_LIST);
                if (isbnList != null && !isbnList.isEmpty()) {
                    mScanQueue.addAll(isbnList.stream()
                                              .map(ISBN::new)
                                              .filter(isbn -> isbn.isValid(true))
                                              .collect(Collectors.toList()));
                }
            }
        }
    }

    /**
     * Auto-start scanner the first time this fragment starts
     *
     * @return flag
     */
    public boolean isAutoStart() {
        if ((mScannerMode != SCANNER_OFF) && mFirstStart) {
            mFirstStart = false;
            return true;
        }
        return false;
    }

    @NonNull
    public List<ISBN> getScanQueue() {
        return mScanQueue;
    }

    @Mode
    public int getScannerMode() {
        return mScannerMode;
    }

    public void setScannerMode(@Mode final int scannerMode) {
        mScannerMode = scannerMode;
        if (mScannerMode == SCANNER_MODE_SINGLE || mScannerMode == SCANNER_MODE_BATCH) {
            mScanQueue.clear();
        }
    }

    @NonNull
    public ArrayList<Pair<Long, String>> getBookIdAndTitlesByIsbn(@NonNull final ISBN code) {
        return mDb.getBookIdAndTitlesByIsbn(code);
    }


    @Retention(RetentionPolicy.SOURCE)
    @IntDef({SCANNER_OFF,
             SCANNER_MODE_SINGLE,
             SCANNER_MODE_BATCH})
    public @interface Mode {

    }
}
