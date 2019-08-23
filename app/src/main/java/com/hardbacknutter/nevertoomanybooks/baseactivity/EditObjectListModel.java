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

    /** Database Access. */
    protected DAO mDb;

    /** Row ID... mainly used (if list is from a book) to know if the object is new. */
    private long mRowId;
    /** Displayed for user reference only. */
    @Nullable
    private String mBookTitle;
    /** If set, used for the series default locale. */
    @Nullable
    private Locale mBookLocale;

    /** flag indicating global changes were made. Used in setResult. */
    private boolean mGlobalReplacementsMade;

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
    public void init(@NonNull final Bundle args) {
        if (mDb == null) {
            mDb = new DAO();

            // Look for id and title
            mRowId = args.getLong(DBDefinitions.KEY_PK_ID);
            mBookTitle = args.getString(DBDefinitions.KEY_TITLE);
            String bookLang = args.getString(DBDefinitions.KEY_LANGUAGE);
            if (bookLang != null && !bookLang.isEmpty()) {
                mBookLocale = new Locale(bookLang);
            }
        }
    }

    public DAO getDb() {
        return mDb;
    }

    public boolean isSingleUsage(final long nrOfReferences) {
        return nrOfReferences <= (mRowId == 0 ? 0 : 1);
    }

    @Nullable
    public String getBookTitle() {
        return mBookTitle;
    }

    @NonNull
    public Locale getBookLocale() {
        if (mBookLocale == null) {
            return LocaleUtils.getPreferredLocale();
        }
        return mBookLocale;
    }

    public boolean globalReplacementsMade() {
        return mGlobalReplacementsMade;
    }

    public void setGlobalReplacementsMade(final boolean changed) {
        mGlobalReplacementsMade = changed;
    }
}
