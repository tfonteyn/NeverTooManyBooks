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

package com.hardbacknutter.nevertoomanybooks.dialogs.inmemory.tagmapping;

import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.Objects;

import com.hardbacknutter.nevertoomanybooks.dialogs.DialogLauncher;
import com.hardbacknutter.nevertoomanybooks.entities.TagMapping;

class EditTagMappingInput {

    private static final String TAG = "EditTagMappingInput";

    private static final String BKEY_EXTRAS = TAG + ":extras";
    private static final String BKEY_EDIT = TAG + ":edit";
    @NonNull
    private final String requestKey;
    @NonNull
    private final TagMapping tagMapping;
    @Nullable
    private final Bundle extras;

    EditTagMappingInput(@NonNull final String requestKey,
                               @NonNull final TagMapping tagMapping,
                               @Nullable final Bundle extras) {
        this.requestKey = requestKey;
        this.tagMapping = tagMapping;
        this.extras = extras;
    }

    static EditTagMappingInput fromBundle(@NonNull final Bundle args) {
        final String requestKey = Objects.requireNonNull(
                args.getString(DialogLauncher.BKEY_REQUEST_KEY),
                DialogLauncher.BKEY_REQUEST_KEY);

        @SuppressWarnings("deprecation")
        final TagMapping tagMapping = Objects.requireNonNull(args.getParcelable(
                EditTagMappingInput.BKEY_EDIT));

        final Bundle extras = args.getBundle(EditTagMappingInput.BKEY_EXTRAS);

        return new EditTagMappingInput(requestKey, tagMapping, extras);
    }

    @NonNull
    Bundle toBundle() {
        final Bundle args = new Bundle();
        args.putString(DialogLauncher.BKEY_REQUEST_KEY, requestKey);
        args.putParcelable(BKEY_EDIT, tagMapping);

        if (extras != null && !extras.isEmpty()) {
            args.putBundle(BKEY_EXTRAS, extras);
        }

        return args;
    }

    @NonNull
    String getRequestKey() {
        return requestKey;
    }

    @NonNull
    TagMapping getTagMapping() {
        return tagMapping;
    }

    @Nullable
    Bundle getExtras() {
        return extras;
    }
}
