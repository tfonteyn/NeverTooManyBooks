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

import com.hardbacknutter.nevertoomanybooks.BuildConfig;
import com.hardbacknutter.nevertoomanybooks.DEBUG_SWITCHES;
import com.hardbacknutter.nevertoomanybooks.FragmentHostActivity;
import com.hardbacknutter.nevertoomanybooks.booklist.style.ListStyle;
import com.hardbacknutter.nevertoomanybooks.debug.Logger;
import com.hardbacknutter.nevertoomanybooks.settings.styles.PreferredStylesFragment;

public class PreferredStylesContract
        extends ActivityResultContract<String, Bundle> {

    private static final String TAG = "PreferredStylesContract";

    public static void setResultAndFinish(@NonNull final Activity activity,
                                          @Nullable final ListStyle selectedStyle,
                                          final boolean styleModified) {
        final Intent resultIntent = new Intent();

        // Return the currently selected style UUID, so the caller can apply it.
        // This is independent from any modification to this or another style,
        // or the order of the styles.
        if (selectedStyle != null) {
            resultIntent.putExtra(ListStyle.BKEY_STYLE_UUID, selectedStyle.getUuid());
        }
        // Same here, this is independent from the returned style
        resultIntent.putExtra(EditStyleContract.BKEY_STYLE_MODIFIED, styleModified);

        activity.setResult(Activity.RESULT_OK, resultIntent);
        activity.finish();
    }

    @NonNull
    @Override
    public Intent createIntent(@NonNull final Context context,
                               @NonNull final String styleUuid) {
        return new Intent(context, FragmentHostActivity.class)
                .putExtra(FragmentHostActivity.BKEY_FRAGMENT_TAG, PreferredStylesFragment.TAG)
                .putExtra(ListStyle.BKEY_STYLE_UUID, styleUuid);
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
