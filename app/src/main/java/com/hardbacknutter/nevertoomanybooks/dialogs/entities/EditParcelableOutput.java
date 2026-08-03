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

package com.hardbacknutter.nevertoomanybooks.dialogs.entities;

import android.os.Bundle;
import android.os.Parcelable;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;

import java.util.Objects;

import com.hardbacknutter.nevertoomanybooks.BuildConfig;
import com.hardbacknutter.nevertoomanybooks.dialogs.LauncherOutput;

public class EditParcelableOutput<T extends Parcelable>
        implements LauncherOutput {

    private static final String TAG = "EditParcelableOutput";
    private static final String BKEY_ORIGINAL = TAG + ":original";
    private static final String BKEY_EDIT = TAG + ":edit";

    @NonNull
    private final EditAction action;
    @NonNull
    private final T original;
    @NonNull
    private final T edited;

    /**
     * Constructor.
     *
     * @param action   EditAction
     * @param original the previous value
     * @param edited   the new value
     */
    public EditParcelableOutput(@NonNull final EditAction action,
                                @NonNull final T original,
                                @NonNull final T edited) {
        this.action = action;
        this.original = original;
        this.edited = edited;
    }

    @SuppressWarnings("deprecation")
    @NonNull
    static <T extends Parcelable> EditParcelableOutput<T> fromBundle(@NonNull final Bundle args) {
        final EditAction action = Objects.requireNonNull(
                args.getParcelable(EditAction.BKEY), EditAction.BKEY);
        final T previousValue = Objects.requireNonNull(
                args.getParcelable(BKEY_ORIGINAL), BKEY_ORIGINAL);
        final T currentValue = Objects.requireNonNull(
                args.getParcelable(BKEY_EDIT), BKEY_EDIT);

        return new EditParcelableOutput<>(action, previousValue, currentValue);
    }

    @NonNull
    public Bundle toBundle() {
        final Bundle result = new Bundle(3);
        result.putParcelable(EditAction.BKEY, action);
        result.putParcelable(BKEY_ORIGINAL, original);
        result.putParcelable(BKEY_EDIT, edited);

        return result;
    }

    @NonNull
    EditAction getAction() {
        return action;
    }

    @NonNull
    T getOriginal() {
        return original;
    }

    @NonNull
    T getEdited() {
        return edited;
    }

    @Override
    public void send(@NonNull final Fragment fragment,
                     @NonNull final String requestKey) {

        if (BuildConfig.DEBUG /* always */) {
            if (action != EditAction.Add && action != EditAction.Edit) {
                throw new IllegalArgumentException("action must be Add or Edit");
            }
        }
        LauncherOutput.super.send(fragment, requestKey);
    }

    @Override
    @NonNull
    public String toString() {
        return "EditParcelableOutput{"
               + "action=" + action
               + ", original=" + original
               + ", edited=" + edited
               + '}';
    }
}
