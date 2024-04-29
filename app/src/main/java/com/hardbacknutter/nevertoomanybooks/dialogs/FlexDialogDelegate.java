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

package com.hardbacknutter.nevertoomanybooks.dialogs;

import android.content.DialogInterface;
import android.os.Bundle;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.fragment.app.DialogFragment;
import androidx.fragment.app.Fragment;

public interface FlexDialogDelegate<B>
        extends ToolbarWithActionButtons {

    /**
     * To be called from {@link Fragment#onViewCreated(View, Bundle)}.
     *
     * @param vb the view binding
     */
    void onViewCreated(@NonNull B vb);

    /**
     * To be called from {@link Fragment#onStart()}.
     */
    default void onStart() {
        // no action
    }

    /**
     * To be called from {@link Fragment#onResume()}.
     */
    default void onResume() {
        // no action
    }

    /**
     * To be called from {@link Fragment#onPause()}.
     */
    default void onPause() {
        // no action
    }

    /**
     * To be called from {@link DialogFragment#onCancel(DialogInterface)}.
     *
     * @param dialog .
     */
    default void onCancel(@NonNull final DialogInterface dialog) {
        // no action
    }
}
