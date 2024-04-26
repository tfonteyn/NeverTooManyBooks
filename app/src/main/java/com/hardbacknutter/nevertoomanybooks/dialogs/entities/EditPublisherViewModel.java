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

package com.hardbacknutter.nevertoomanybooks.dialogs.entities;

import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModel;

import java.util.Objects;

import com.hardbacknutter.nevertoomanybooks.dialogs.ParcelableDialogLauncher;
import com.hardbacknutter.nevertoomanybooks.entities.Publisher;

public class EditPublisherViewModel
        extends ViewModel {

    /** FragmentResultListener request key to use for our response. */
    private String requestKey;

    /** The Publisher we're editing. */
    private Publisher publisher;

    /** Current edit. */
    private Publisher currentEdit;

    /**
     * Pseudo constructor.
     *
     * @param args {@link Fragment#requireArguments()}
     */
    public void init(@NonNull final Bundle args) {
        if (requestKey == null) {
            requestKey = Objects.requireNonNull(
                    args.getString(ParcelableDialogLauncher.BKEY_REQUEST_KEY),
                    ParcelableDialogLauncher.BKEY_REQUEST_KEY);
            publisher = Objects.requireNonNull(
                    args.getParcelable(ParcelableDialogLauncher.BKEY_ITEM),
                    ParcelableDialogLauncher.BKEY_ITEM);

            currentEdit = new Publisher(publisher.getName());
        }
    }

    @NonNull
    public String getRequestKey() {
        return requestKey;
    }

    @NonNull
    public Publisher getPublisher() {
        return publisher;
    }

    @NonNull
    public Publisher getCurrentEdit() {
        return currentEdit;
    }

    public boolean isChanged() {
        // Case-sensitive! We must allow the user to correct case.
        return !publisher.isSameName(currentEdit);
    }
}
