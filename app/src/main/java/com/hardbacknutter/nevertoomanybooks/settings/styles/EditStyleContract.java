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
package com.hardbacknutter.nevertoomanybooks.settings.styles;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;

import androidx.activity.result.contract.ActivityResultContract;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.Objects;
import java.util.Optional;

import com.hardbacknutter.nevertoomanybooks.FragmentHostActivityLauncher;
import com.hardbacknutter.nevertoomanybooks.booklist.style.Style;

public class EditStyleContract
        extends ActivityResultContract<EditStyleInput,
        Optional<EditStyleContract.Output>> {

    private static final String TAG = "EditStyleContract";

    private static final String BKEY_MODIFIED = TAG + ":m";
    private static final String BKEY_TEMPLATE_UUID = TAG + ":template";

    /**
     * Create the result which {@link #parseResult(int, Intent)} will receive.
     *
     * @param templateUuid uuid of the original style we cloned (different from current)
     *                     or edited (same as current).
     * @param modified     flag; whether the style was modified (either created ot updated)
     * @param styleUuid    uuid of the modified (or newly created) style
     *
     * @return Intent
     */
    @NonNull
    public static Intent createResult(@NonNull final String templateUuid,
                                      final boolean modified,
                                      @Nullable final String styleUuid) {
        return new Intent().putExtra(BKEY_TEMPLATE_UUID, templateUuid)
                           .putExtra(BKEY_MODIFIED, modified)
                           .putExtra(Style.BKEY_UUID, styleUuid);
    }

    @NonNull
    @Override
    public Intent createIntent(@NonNull final Context context,
                               @NonNull final EditStyleInput args) {
        return FragmentHostActivityLauncher
                .createIntent(context, StyleFragment.class)
                .putExtras(args.toBundle());
    }

    @Override
    @NonNull
    public Optional<Output> parseResult(final int resultCode,
                                        @Nullable final Intent intent) {

        if (intent == null || resultCode != Activity.RESULT_OK) {
            return Optional.empty();
        }

        final String templateUuid = Objects.requireNonNull(
                intent.getStringExtra(BKEY_TEMPLATE_UUID), BKEY_TEMPLATE_UUID);
        final String uuid = intent.getStringExtra(Style.BKEY_UUID);
        final boolean modified = intent.getBooleanExtra(BKEY_MODIFIED, false);

        return Optional.of(new Output(templateUuid, modified, uuid));
    }

    public static final class Output {

        /** The uuid which was passed into the {@link EditStyleInput} for editing. */
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
