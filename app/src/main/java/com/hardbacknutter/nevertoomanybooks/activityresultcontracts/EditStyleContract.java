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

import java.util.Objects;

import com.hardbacknutter.nevertoomanybooks.BuildConfig;
import com.hardbacknutter.nevertoomanybooks.DEBUG_SWITCHES;
import com.hardbacknutter.nevertoomanybooks.booklist.style.ListStyle;
import com.hardbacknutter.nevertoomanybooks.debug.Logger;
import com.hardbacknutter.nevertoomanybooks.settings.SettingsHostActivity;
import com.hardbacknutter.nevertoomanybooks.settings.styles.StyleFragment;
import com.hardbacknutter.nevertoomanybooks.settings.styles.StyleViewModel;

public class EditStyleContract
        extends ActivityResultContract<EditStyleContract.Input, EditStyleContract.Output> {

    private static final String TAG = "EditStyleContract";
    public static final String BKEY_ACTION = TAG + ":action";
    public static final String BKEY_SET_AS_PREFERRED = TAG + ":setAsPreferred";
    public static final String BKEY_TEMPLATE_UUID = TAG + ":templateUuid";

    /**
     * Styles related data was modified (or not).
     * This includes a ListStyle being modified or deleted,
     * or the order of the preferred styles modified,
     * or the selected ListStyle changed,
     * or ...
     * ENHANCE: make this fine grained and reduce unneeded BoB rebuilds
     * <p>
     * <br>type: {@code boolean}
     */
    public static final String BKEY_STYLE_MODIFIED = TAG + ":modified";

    @NonNull
    @Override
    public Intent createIntent(@NonNull final Context context,
                               @NonNull final Input input) {
        return SettingsHostActivity
                .createIntent(context, StyleFragment.class)
                .putExtra(BKEY_ACTION, input.action)
                .putExtra(ListStyle.BKEY_STYLE_UUID, input.uuid)
                .putExtra(BKEY_SET_AS_PREFERRED, input.setAsPreferred);
    }

    @Override
    @Nullable
    public Output parseResult(final int resultCode,
                              @Nullable final Intent intent) {
        if (BuildConfig.DEBUG && DEBUG_SWITCHES.ON_ACTIVITY_RESULT) {
            Logger.d(TAG, "parseResult", "|resultCode=" + resultCode + "|intent=" + intent);
        }

        if (intent == null || resultCode != Activity.RESULT_OK) {
            return null;
        }

        final Bundle data = intent.getExtras();
        if (data == null) {
            // should not actually ever be the case...
            return null;
        }

        return new Output(
                Objects.requireNonNull(data.getString(BKEY_TEMPLATE_UUID), "BKEY_TEMPLATE_UUID"),
                data.getBoolean(BKEY_STYLE_MODIFIED, false),
                data.getString(ListStyle.BKEY_STYLE_UUID));
    }

    public static class Input {

        @StyleViewModel.EditAction
        final int action;

        @NonNull
        final String uuid;

        /**
         * If set to {@code true} the edited/cloned style will be set to preferred.
         * If set to {@code false} the preferred state will not be touched.
         */
        final boolean setAsPreferred;

        public Input(@StyleViewModel.EditAction final int action,
                     @NonNull final String uuid,
                     final boolean setAsPreferred) {
            this.action = action;
            this.uuid = uuid;
            this.setAsPreferred = setAsPreferred;
        }
    }

    public static class Output {

        /**
         * Either a new UUID if we cloned a style, or the UUID of the style we edited.
         * Will be {@code null} if we edited the global style
         */
        @Nullable
        public final String uuid;

        /** The uuid which was passed into the {@link Input#uuid} for editing. */
        @NonNull
        public final String templateUuid;
        public final boolean modified;

        Output(@NonNull final String templateUuid,
               final boolean modified,
               @Nullable final String uuid) {
            this.templateUuid = templateUuid;
            this.modified = modified;
            this.uuid = uuid;
        }
    }
}
