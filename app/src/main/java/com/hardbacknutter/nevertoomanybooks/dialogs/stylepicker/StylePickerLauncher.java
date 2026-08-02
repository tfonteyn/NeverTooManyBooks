/*
 * @Copyright 2018-2026 HardBackNutter
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

package com.hardbacknutter.nevertoomanybooks.dialogs.stylepicker;

import android.content.Context;
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.UiContext;

import java.util.Objects;

import com.hardbacknutter.nevertoomanybooks.booklist.style.Style;
import com.hardbacknutter.nevertoomanybooks.database.DBKey;
import com.hardbacknutter.nevertoomanybooks.dialogs.DialogLauncher;
import com.hardbacknutter.nevertoomanybooks.dialogs.LauncherOutput;

public class StylePickerLauncher
        extends DialogLauncher {

    @NonNull
    private final ResultListener resultListener;

    /**
     * Constructor.
     *
     * @param resultListener listener
     */
    public StylePickerLauncher(@NonNull final ResultListener resultListener) {
        super(DBKey.FK_STYLE,
              StylePickerDialogFragment::new,
              StylePickerBottomSheet::new);
        this.resultListener = resultListener;
    }

    /**
     * Launch the dialog.
     *
     * @param context      preferably the {@code Activity}
     *                     but another UI {@code Context} will also do.
     * @param currentStyle the currently active style
     * @param all          if {@code true} show all styles, otherwise only the preferred ones.
     */
    public void launch(@NonNull @UiContext final Context context,
                       @NonNull final Style currentStyle,
                       final boolean all) {

        final StylePickerInput input = new StylePickerInput(
                getRequestKey(), currentStyle.getUuid(), all);
        showDialog(context, input.toBundle());
    }

    @Override
    public void onFragmentResult(@NonNull final String requestKey,
                                 @NonNull final Bundle result) {
        final String styleUuid = Output.fromBundle(result);
        resultListener.onResult(Objects.requireNonNull(styleUuid, DBKey.FK_STYLE));
    }

    public static class Output
            implements LauncherOutput {

        @NonNull
        private final String styleUuid;

        /**
         * Constructor.
         *
         * @param styleUuid the selected style
         */
        Output(@NonNull final String styleUuid) {
            this.styleUuid = styleUuid;
        }

        @Nullable
        static String fromBundle(@NonNull final Bundle args) {
            return args.getString(DBKey.FK_STYLE);
        }

        @NonNull
        @Override
        public Bundle toBundle() {
            final Bundle args = new Bundle(1);
            args.putString(DBKey.FK_STYLE, styleUuid);
            return args;
        }
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
