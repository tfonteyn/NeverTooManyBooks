/*
 * @Copyright 2018-2025 HardBackNutter
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

package com.hardbacknutter.nevertoomanybooks.dialogs.entities.bookshelffilters;

import android.content.Context;
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.annotation.UiContext;

import com.hardbacknutter.nevertoomanybooks.dialogs.DialogLauncher;
import com.hardbacknutter.nevertoomanybooks.dialogs.LauncherOutput;
import com.hardbacknutter.nevertoomanybooks.entities.Bookshelf;

public class BookshelfFiltersLauncher
        extends DialogLauncher {

    private static final String TAG = "BookshelfFilters";
    private static final String RK_FILTERS = TAG + ":rk:filters";

    @NonNull
    private final ResultListener resultListener;

    /**
     * Constructor.
     *
     * @param resultListener listener
     */
    public BookshelfFiltersLauncher(@NonNull final ResultListener resultListener) {
        super(RK_FILTERS,
              BookshelfFiltersDialogFragment::new,
              BookshelfFiltersBottomSheet::new);
        this.resultListener = resultListener;
    }

    /**
     * Launch the dialog.
     * @param context          preferably the {@code Activity}
     *                         but another UI {@code Context} will also do.
     * @param bookshelf to edit
     */
    public void launch(@NonNull @UiContext final Context context,
                       @NonNull final Bookshelf bookshelf) {

        final BookshelfFiltersInput input = new BookshelfFiltersInput(getRequestKey(), bookshelf);
        showDialog(context, input.toBundle());
    }

    @Override
    public void onFragmentResult(@NonNull final String requestKey,
                                 @NonNull final Bundle result) {
        resultListener.onResult(Output.fromBundle(result));
    }

    public static class Output
            implements LauncherOutput {

        private static final String BKEY_MODIFIED = "modified";

        private final boolean modified;

        public Output(final boolean modified) {
            this.modified = modified;
        }

        static boolean fromBundle(@NonNull final Bundle args) {
            return args.getBoolean(BKEY_MODIFIED);
        }

        @NonNull
        @Override
        public Bundle toBundle() {
            final Bundle args = new Bundle(1);
            args.putBoolean(BKEY_MODIFIED, modified);
            return args;
        }
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
