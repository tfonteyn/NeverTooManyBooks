/*
 * @Copyright 2018-2024 HardBackNutter
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

package com.hardbacknutter.nevertoomanybooks;

import android.content.Context;
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;

import com.hardbacknutter.nevertoomanybooks.database.DBKey;
import com.hardbacknutter.nevertoomanybooks.dialogs.DialogLauncher;
import com.hardbacknutter.nevertoomanybooks.entities.Bookshelf;

class BookshelfFiltersLauncher
        extends DialogLauncher {

    private static final String TAG = "BookshelfFilters";
    private static final String RK_FILTERS = TAG + ":rk:filters";

    private static final String BKEY_MODIFIED = TAG + ":m";

    @NonNull
    private final ResultListener resultListener;

    /**
     * Constructor.
     *
     * @param resultListener listener
     */
    BookshelfFiltersLauncher(@NonNull final ResultListener resultListener) {
        super(RK_FILTERS,
              BookshelfFiltersDialogFragment::new,
              BookshelfFiltersBottomSheet::new);
        this.resultListener = resultListener;
    }

    /**
     * Encode and forward the results to {@link #onFragmentResult(String, Bundle)}.
     *
     * @param fragment   the calling DialogFragment
     * @param requestKey to use
     * @param modified   flag to indicate whether the filters have changed
     *
     * @see #onFragmentResult(String, Bundle)
     */
    @SuppressWarnings("StaticMethodOnlyUsedInOneClass")
    static void setResult(@NonNull final Fragment fragment,
                          @NonNull final String requestKey,
                          final boolean modified) {
        final Bundle result = new Bundle(1);
        result.putBoolean(BKEY_MODIFIED, modified);
        fragment.getParentFragmentManager().setFragmentResult(requestKey, result);
    }

    /**
     * Launch the dialog.
     * @param context          preferably the {@code Activity}
     *                         but another UI {@code Context} will also do.
     * @param bookshelf to edit
     */
    public void launch(@NonNull final Context context,
                       @NonNull final Bookshelf bookshelf) {
        final Bundle args = new Bundle(2);
        args.putParcelable(DBKey.FK_BOOKSHELF, bookshelf);

        showDialog(context, args);
    }

    @Override
    public void onFragmentResult(@NonNull final String requestKey,
                                 @NonNull final Bundle result) {
        resultListener.onResult(result.getBoolean(BKEY_MODIFIED));
    }

    @FunctionalInterface
    public interface ResultListener {
        /**
         * Callback handler.
         *
         * @param modified flag to indicate whether the filters have changed
         */
        void onResult(boolean modified);
    }
}
