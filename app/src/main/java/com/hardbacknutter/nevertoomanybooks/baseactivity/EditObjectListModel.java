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
package com.hardbacknutter.nevertoomanybooks.baseactivity;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModel;

import java.util.Locale;

import com.hardbacknutter.nevertoomanybooks.database.DAO;
import com.hardbacknutter.nevertoomanybooks.database.DBDefinitions;
import com.hardbacknutter.nevertoomanybooks.utils.LocaleUtils;

public class EditObjectListModel
        extends ViewModel {

    /** Log tag. */
    private static final String TAG = "EditObjectListModel";

    /**
     * Indicate we made global changes. i.e. the list did not change but entries changed.
     * <p>
     * <br>type: {@code boolean}
     * setResult
     */
    public static final String BKEY_GLOBAL_CHANGES_MADE = TAG + ":globalChanges";
    /**
     * Indicate we made additions/deletions to the list.
     * <p>
     * <br>type: {@code boolean}
     * setResult
     */
    public static final String BKEY_LIST_MODIFIED = TAG + ":listModified";

    /** Accumulate all data that will be send in {@link Activity#setResult}. */
    private final Intent mResultData = new Intent();
    /** Database Access. */
    private DAO mDb;
    /** Book ID. Can be 0 for new books. */
    private long mBookId;
    /** Displayed for user reference only. */
    @Nullable
    private String mBookTitle;
    /** Used for the Series default Locale. */
    private Locale mBookLocale;

    @Override
    protected void onCleared() {
        if (mDb != null) {
            mDb.close();
        }
    }

    /**
     * Pseudo constructor.
     *
     * @param args {@link Intent#getExtras()} or {@link Fragment#getArguments()}
     */
    public void init(@NonNull final Context context,
                     @NonNull final Bundle args) {
        if (mDb == null) {
            mDb = new DAO(TAG);

            // Look for id and title
            mBookId = args.getLong(DBDefinitions.KEY_PK_ID);
            mBookTitle = args.getString(DBDefinitions.KEY_TITLE);
            String bookLang = args.getString(DBDefinitions.KEY_LANGUAGE);
            if (bookLang != null && !bookLang.isEmpty()) {
                mBookLocale = LocaleUtils.getLocale(context, bookLang);
                if (mBookLocale == null) {
                    // fallback
                    mBookLocale = Locale.getDefault();
                }
            } else {
                // fallback
                mBookLocale = Locale.getDefault();
            }
        }
    }

    public DAO getDb() {
        return mDb;
    }

    public boolean isSingleUsage(final long nrOfReferences) {
        return nrOfReferences <= (mBookId == 0 ? 0 : 1);
    }

    @Nullable
    String getBookTitle() {
        return mBookTitle;
    }

    @NonNull
    public Locale getBookLocale() {
        return mBookLocale;
    }


    public void setGlobalReplacementsMade(final boolean changed) {
        mResultData.putExtra(BKEY_GLOBAL_CHANGES_MADE, changed);
    }

    @NonNull
    public Intent getResultData() {
        return mResultData;
    }

    public void setDirty(@SuppressWarnings("SameParameterValue") final boolean isDirty) {
        mResultData.putExtra(BKEY_LIST_MODIFIED, isDirty);
    }
}
