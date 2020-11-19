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
package com.hardbacknutter.nevertoomanybooks;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;

import androidx.activity.result.contract.ActivityResultContract;
import androidx.annotation.IntRange;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.hardbacknutter.nevertoomanybooks.booklist.style.BooklistStyle;
import com.hardbacknutter.nevertoomanybooks.database.DBDefinitions;
import com.hardbacknutter.nevertoomanybooks.debug.Logger;
import com.hardbacknutter.nevertoomanybooks.viewmodels.ShowBookViewModel;

/**
 * Hosting activity for showing a book <strong>with</strong>
 * a DrawerLayout/NavigationView side panel.
 */
public class ShowBookActivity
        extends BaseActivity {

    private static final String TAG = "ShowBookActivity";

    @Override
    protected void onSetContentView() {
        setContentView(R.layout.activity_book_details);
    }

    @Override
    public void onCreate(@Nullable final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        addFirstFragment(R.id.main_fragment, ShowBookFragment.class,
                         ShowBookFragment.TAG);
    }

    public static class ResultContract
            extends ActivityResultContract<ResultContract.Input, Bundle> {

        @NonNull
        @Override
        public Intent createIntent(@NonNull final Context context,
                                   @NonNull final ResultContract.Input input) {
            return new Intent(context, ShowBookActivity.class)
                    // the book to display
                    .putExtra(DBDefinitions.KEY_PK_ID, input.bookId)
                    // the current list table, so the user can swipe
                    // to the next/previous book
                    .putExtra(ShowBookViewModel.BKEY_NAV_TABLE_NAME, input.navTableName)
                    // The row id in the list table of the given book.
                    // Keep in mind a book can occur multiple times,
                    // so we need to pass the specific one.
                    .putExtra(ShowBookViewModel.BKEY_LIST_TABLE_ROW_ID, input.listTableRowId)
                    // some style elements are applicable for the details screen
                    .putExtra(BooklistStyle.BKEY_STYLE_UUID, input.styleUuid);
        }

        @Override
        @Nullable
        public Bundle parseResult(final int resultCode,
                                  @Nullable final Intent intent) {
            if (BuildConfig.DEBUG && DEBUG_SWITCHES.ON_ACTIVITY_RESULT) {
                Logger.d(TAG, "parseResult", "|resultCode=" + resultCode + "|intent=" + intent);
            }

            if (intent == null || resultCode != RESULT_OK) {
                return null;
            }
            return intent.getExtras();
        }

        public static class Input {

            @IntRange(from = 1)
            final long bookId;
            @NonNull
            final String styleUuid;
            @NonNull
            final String navTableName;
            final long listTableRowId;

            public Input(@IntRange(from = 1) final long bookId,
                         @NonNull final String navTableName,
                         final long listTableRowId,
                         @NonNull final String styleUuid) {
                this.bookId = bookId;
                this.styleUuid = styleUuid;
                this.navTableName = navTableName;
                this.listTableRowId = listTableRowId;
            }
        }
    }
}
