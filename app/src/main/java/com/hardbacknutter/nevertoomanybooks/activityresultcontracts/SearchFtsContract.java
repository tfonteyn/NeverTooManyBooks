/*
 * @Copyright 2018-2021 HardBackNutter
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
package com.hardbacknutter.nevertoomanybooks.activityresultcontracts;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;

import androidx.activity.result.contract.ActivityResultContract;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.ArrayList;

import com.hardbacknutter.nevertoomanybooks.BuildConfig;
import com.hardbacknutter.nevertoomanybooks.DEBUG_SWITCHES;
import com.hardbacknutter.nevertoomanybooks.FragmentHostActivity;
import com.hardbacknutter.nevertoomanybooks.SearchCriteria;
import com.hardbacknutter.nevertoomanybooks.SearchFtsFragment;
import com.hardbacknutter.nevertoomanybooks.database.DBKey;
import com.hardbacknutter.nevertoomanybooks.debug.Logger;
import com.hardbacknutter.nevertoomanybooks.entities.Book;

public class SearchFtsContract
        extends ActivityResultContract<SearchCriteria, Bundle> {

    private static final String TAG = "SearchFtsContract";

    public static void setResultAndFinish(@NonNull final Activity activity,
                                          @SuppressWarnings("TypeMayBeWeakened")
                                          @NonNull final ArrayList<Long> bookIdList,
                                          @Nullable final String titleSearchText,
                                          @Nullable final String seriesTitleSearchText,
                                          @Nullable final String authorSearchText,
                                          @Nullable final String publisherNameSearchText,
                                          @Nullable final String keywordsSearchText) {

        final Intent resultIntent = new Intent()
                // pass the book ID's for the list
                .putExtra(Book.BKEY_BOOK_ID_LIST, bookIdList)
                // pass these for displaying to the user
                .putExtra(DBKey.KEY_TITLE, titleSearchText)
                .putExtra(DBKey.KEY_SERIES_TITLE, seriesTitleSearchText)
                .putExtra(SearchCriteria.BKEY_SEARCH_TEXT_AUTHOR, authorSearchText)
                .putExtra(SearchCriteria.BKEY_SEARCH_TEXT_PUBLISHER, publisherNameSearchText)
                .putExtra(SearchCriteria.BKEY_SEARCH_TEXT_KEYWORDS, keywordsSearchText);
        activity.setResult(Activity.RESULT_OK, resultIntent);
        activity.finish();
    }

    @NonNull
    @Override
    public Intent createIntent(@NonNull final Context context,
                               @NonNull final SearchCriteria criteria) {
        final Intent intent = new Intent(context, FragmentHostActivity.class)
                .putExtra(FragmentHostActivity.BKEY_FRAGMENT_TAG, SearchFtsFragment.TAG);
        criteria.to(intent);
        return intent;
    }

    @Override
    @Nullable
    public Bundle parseResult(final int resultCode,
                              @Nullable final Intent intent) {
        if (BuildConfig.DEBUG && DEBUG_SWITCHES.ON_ACTIVITY_RESULT) {
            Logger.d(TAG, "parseResult", "|resultCode=" + resultCode + "|intent=" + intent);
        }

        if (intent == null || resultCode != Activity.RESULT_OK) {
            return null;
        }
        return intent.getExtras();
    }
}
