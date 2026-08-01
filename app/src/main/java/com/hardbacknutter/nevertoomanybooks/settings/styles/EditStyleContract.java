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
import android.os.Bundle;

import androidx.activity.result.contract.ActivityResultContract;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.Objects;
import java.util.Optional;

import com.hardbacknutter.nevertoomanybooks.FragmentHostActivityLauncher;

public class EditStyleContract
        extends ActivityResultContract<EditStyleInput,
        Optional<EditStyleOutput>> {

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
    public Optional<EditStyleOutput> parseResult(final int resultCode,
                                                 @Nullable final Intent intent) {

        if (intent == null || resultCode != Activity.RESULT_OK) {
            return Optional.empty();
        }

        final Bundle args = Objects.requireNonNull(intent.getExtras());
        return Optional.of(EditStyleOutput.fromBundle(args));
    }
}
