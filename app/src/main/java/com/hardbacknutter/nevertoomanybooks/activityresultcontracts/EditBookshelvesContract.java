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
package com.hardbacknutter.nevertoomanybooks.activityresultcontracts;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;

import androidx.activity.result.contract.ActivityResultContract;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.Objects;
import java.util.Optional;

import com.hardbacknutter.nevertoomanybooks.FragmentHostActivityLauncher;
import com.hardbacknutter.nevertoomanybooks.database.DBKey;
import com.hardbacknutter.nevertoomanybooks.settings.bookshelves.EditBookshelvesFragment;

public class EditBookshelvesContract
        extends ActivityResultContract<Long, Optional<EditBookshelvesContract.Output>> {

    /**
     * Create the result which {@link #parseResult(int, Intent)} will receive.
     *
     * @param selectedBookshelfId id, or {@code 0} for none
     * @param modified            was anything at all modified
     *
     * @return Intent
     */
    public static Intent createResult(final long selectedBookshelfId,
                                      final boolean modified) {
        final EditBookshelvesContract.Output output =
                new EditBookshelvesContract.Output(selectedBookshelfId, modified);
        return new Intent().putExtras(output.toBundle());
    }

    @NonNull
    @Override
    public Intent createIntent(@NonNull final Context context,
                               @NonNull final Long id) {
        return FragmentHostActivityLauncher
                .createIntent(context, EditBookshelvesFragment.class)
                .putExtra(DBKey.FK_BOOKSHELF, (long) id);
    }

    @NonNull
    @Override
    public Optional<Output> parseResult(final int resultCode,
                                      @Nullable final Intent intent) {
        if (intent == null || resultCode != Activity.RESULT_OK) {
            return Optional.empty();
        }

        final Bundle result = Objects.requireNonNull(intent.getExtras());
        return Optional.of(Output.fromBundle(result));
    }

    public static class Output {

        private static final String BKEY_MODIFIED = "modified";

        private final long selectedBookshelfId;
        private final boolean modified;

        public Output(final long selectedBookshelfId,
                      final boolean modified) {
            this.selectedBookshelfId = selectedBookshelfId;
            this.modified = modified;
        }

        @NonNull
        static Output fromBundle(@NonNull final Bundle result) {
            final long id = result.getLong(DBKey.FK_BOOKSHELF, 0);
            final boolean modified = result.getBoolean(BKEY_MODIFIED, false);

            return new Output(id, modified);
        }

        @NonNull
        Bundle toBundle() {
            final Bundle args = new Bundle(2);
            args.putLong(DBKey.FK_BOOKSHELF, selectedBookshelfId);
            args.putBoolean(BKEY_MODIFIED, modified);

            return args;
        }

        public long getSelectedBookshelfId() {
            return selectedBookshelfId;
        }

        /**
         * Was anything at all modified.
         *
         * @return flag
         */
        public boolean isModified() {
            return modified;
        }
    }
}
