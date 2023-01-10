/*
 * @Copyright 2018-2022 HardBackNutter
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

import androidx.activity.result.contract.ActivityResultContract;
import androidx.annotation.IntDef;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.util.Objects;
import java.util.Optional;

import com.hardbacknutter.nevertoomanybooks.BuildConfig;
import com.hardbacknutter.nevertoomanybooks.DEBUG_SWITCHES;
import com.hardbacknutter.nevertoomanybooks.FragmentHostActivity;
import com.hardbacknutter.nevertoomanybooks.booklist.style.Style;
import com.hardbacknutter.nevertoomanybooks.debug.Logger;
import com.hardbacknutter.nevertoomanybooks.settings.styles.StyleFragment;

public class EditStyleContract
        extends ActivityResultContract<EditStyleContract.Input,
        Optional<EditStyleContract.Output>> {

    public static final int ACTION_CLONE = 0;
    public static final int ACTION_EDIT = 1;

    private static final String TAG = "EditStyleContract";
    public static final String BKEY_ACTION = TAG + ":action";
    public static final String BKEY_SET_AS_PREFERRED = TAG + ":setAsPreferred";

    private static final String BKEY_MODIFIED = TAG + ":m";
    private static final String BKEY_TEMPLATE_UUID = TAG + ":template";

    @NonNull
    public static Input duplicate(@NonNull final Style style) {
        return new Input(ACTION_CLONE, style, style.isPreferred());
    }

    @NonNull
    public static Input edit(@NonNull final Style style) {
        return new Input(ACTION_EDIT, style, style.isPreferred());
    }

    @NonNull
    public static Input edit(@NonNull final Style style,
                             final boolean setAsPreferred) {
        return new Input(ACTION_EDIT, style, setAsPreferred);
    }

    /**
     * Create the result which {@link #parseResult(int, Intent)} will receive.
     *
     * @return Intent
     */
    @NonNull
    public static Intent createResult(@NonNull final String templateUuid,
                                      final boolean modified,
                                      @Nullable final String uuid) {
        return new Intent().putExtra(BKEY_TEMPLATE_UUID, templateUuid)
                           .putExtra(BKEY_MODIFIED, modified)
                           .putExtra(Style.BKEY_UUID, uuid);
    }

    @NonNull
    @Override
    public Intent createIntent(@NonNull final Context context,
                               @NonNull final Input input) {
        return FragmentHostActivity
                .createIntent(context, StyleFragment.class)
                .putExtra(BKEY_ACTION, input.action)
                .putExtra(Style.BKEY_UUID, input.uuid)
                .putExtra(BKEY_SET_AS_PREFERRED, input.setAsPreferred);
    }

    @Override
    @NonNull
    public Optional<Output> parseResult(final int resultCode,
                                        @Nullable final Intent intent) {
        if (BuildConfig.DEBUG && DEBUG_SWITCHES.ON_ACTIVITY_RESULT) {
            Logger.d(TAG, "parseResult", "|resultCode=" + resultCode + "|intent=" + intent);
        }

        if (intent == null || resultCode != Activity.RESULT_OK) {
            return Optional.empty();
        }

        final String templateUuid = Objects.requireNonNull(
                intent.getStringExtra(BKEY_TEMPLATE_UUID), BKEY_TEMPLATE_UUID);
        final String uuid = intent.getStringExtra(Style.BKEY_UUID);
        final boolean modified = intent.getBooleanExtra(BKEY_MODIFIED, false);

        return Optional.of(new Output(templateUuid, modified, uuid));
    }

    @IntDef({ACTION_CLONE, ACTION_EDIT})
    @Retention(RetentionPolicy.SOURCE)
    public @interface EditAction {

    }

    public static class Input {

        @EditAction
        final int action;

        @NonNull
        final String uuid;

        /**
         * If set to {@code true} the edited/cloned style will be set to preferred.
         * If set to {@code false} the preferred state will not be touched.
         */
        final boolean setAsPreferred;

        Input(@EditAction final int action,
              @NonNull final Style style,
              final boolean setAsPreferred) {
            this.action = action;
            this.uuid = style.getUuid();
            this.setAsPreferred = setAsPreferred;
        }
    }

    public static final class Output {

        /** The uuid which was passed into the {@link Input#uuid} for editing. */
        @NonNull
        private final String templateUuid;

        /** SOMETHING was modified. This normally means that BoB will need to rebuild. */
        private final boolean modified;

        /**
         * Either a new UUID if we cloned a style, or the UUID of the style we edited.
         */
        @Nullable
        private final String uuid;

        private Output(@NonNull final String templateUuid,
                       final boolean modified,
                       @Nullable final String uuid) {
            this.templateUuid = templateUuid;
            this.modified = modified;
            this.uuid = uuid;
        }

        @NonNull
        public String getTemplateUuid() {
            return templateUuid;
        }

        public boolean isModified() {
            return modified;
        }

        /**
         * Get the UUID.
         *
         * @return {@link Optional} with a non-blank UUID
         */
        @NonNull
        public Optional<String> getUuid() {
            if (uuid == null || uuid.isBlank()) {
                return Optional.empty();
            }
            return Optional.of(uuid);
        }
    }
}
