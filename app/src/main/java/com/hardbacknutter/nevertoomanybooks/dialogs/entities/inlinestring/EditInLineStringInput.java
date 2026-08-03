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

package com.hardbacknutter.nevertoomanybooks.dialogs.entities.inlinestring;

import android.os.Bundle;

import androidx.annotation.NonNull;

import java.util.Objects;

import com.hardbacknutter.nevertoomanybooks.dialogs.DialogLauncher;

class EditInLineStringInput {

    private static final String TAG = "EditInLineStringInput";
    private static final String BKEY_ITEM = TAG + ":item";

    @NonNull
    private final String requestKey;
    @NonNull
    private final String text;

    EditInLineStringInput(@NonNull final String requestKey,
                          @NonNull final String text) {
        this.requestKey = requestKey;
        this.text = text;
    }

    @NonNull
    static EditInLineStringInput fromBundle(@NonNull final Bundle args) {
        final String requestKey = Objects.requireNonNull(
                args.getString(DialogLauncher.BKEY_REQUEST_KEY),
                DialogLauncher.BKEY_REQUEST_KEY);
        final String text = args.getString(BKEY_ITEM, "");

        return new EditInLineStringInput(requestKey, text);
    }

    @NonNull
    Bundle toBundle() {
        final Bundle args = new Bundle(2);
        args.putString(DialogLauncher.BKEY_REQUEST_KEY, requestKey);
        args.putString(BKEY_ITEM, text);

        return args;
    }

    @NonNull
    String getRequestKey() {
        return requestKey;
    }

    @NonNull
    String getText() {
        return text;
    }

    @Override
    @NonNull
    public String toString() {
        return "EditInLineStringInput{"
               + "requestKey='" + requestKey + '\''
               + ", text='" + text + '\''
               + '}';
    }
}
