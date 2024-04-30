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

import java.util.Objects;

import com.hardbacknutter.nevertoomanybooks.booklist.style.Style;
import com.hardbacknutter.nevertoomanybooks.database.DBKey;
import com.hardbacknutter.nevertoomanybooks.dialogs.DialogLauncher;

class StylePickerLauncher
        extends DialogLauncher {

    private static final String TAG = "StylePickerLauncher";
    static final String BKEY_SHOW_ALL_STYLES = TAG + ":showAllStyles";

    @NonNull
    private final ResultListener resultListener;

    /**
     * Constructor.
     *
     * @param requestKey     FragmentResultListener request key to use for our response.
     * @param resultListener listener
     */
    StylePickerLauncher(@NonNull final String requestKey,
                        @NonNull final StylePickerLauncher.ResultListener resultListener) {
        super(requestKey);
        this.resultListener = resultListener;
    }

    /**
     * Encode and forward the results to {@link #onFragmentResult(String, Bundle)}.
     *
     * @param fragment   the calling DialogFragment
     * @param requestKey to use
     * @param style      the selected style
     *
     * @see #onFragmentResult(String, Bundle)
     */
    @SuppressWarnings("StaticMethodOnlyUsedInOneClass")
    static void setResult(@NonNull final Fragment fragment,
                          @NonNull final String requestKey,
                          @NonNull final Style style) {
        final Bundle result = new Bundle(1);
        result.putString(DBKey.FK_STYLE, style.getUuid());
        fragment.getParentFragmentManager().setFragmentResult(requestKey, result);
    }

    /**
     * Launch the dialog.
     * @param context          preferably the {@code Activity}
     *                         but another UI {@code Context} will also do.
     * @param currentStyle the currently active style
     * @param all          if {@code true} show all styles, otherwise only the preferred ones.
     */
    public void launch(@NonNull final Context context,
                       @NonNull final Style currentStyle,
                       final boolean all) {

        final Bundle args = new Bundle(3);
        args.putString(Style.BKEY_UUID, currentStyle.getUuid());
        args.putBoolean(BKEY_SHOW_ALL_STYLES, all);

        createDialog(context, args);
    }

    @Override
    public void onFragmentResult(@NonNull final String requestKey,
                                 @NonNull final Bundle result) {
        resultListener.onResult(Objects.requireNonNull(result.getString(DBKey.FK_STYLE),
                                                       DBKey.FK_STYLE));
    }

    @FunctionalInterface
    public interface ResultListener {
        /**
         * Callback handler.
         *
         * @param uuid the selected style
         */
        void onResult(@NonNull String uuid);
    }
}
