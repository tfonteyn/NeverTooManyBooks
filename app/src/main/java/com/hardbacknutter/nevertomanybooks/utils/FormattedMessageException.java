/*
 * @Copyright 2019 HardBackNutter
 * @License GNU General Public License
 *
 * This file is part of NeverToManyBooks.
 *
 * In August 2018, this project was forked from:
 * Book Catalogue 5.2.2 @copyright 2010 Philip Warner & Evan Leybourn
 *
 * Without their original creation, this project would not exist in its current form.
 * It was however largely rewritten/refactored and any comments on this fork
 * should be directed at HardBackNutter and not at the original creator.
 *
 * NeverToManyBooks is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * NeverToManyBooks is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 * See the GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with NeverToManyBooks. If not, see <http://www.gnu.org/licenses/>.
 */
package com.hardbacknutter.nevertomanybooks.utils;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;

import com.hardbacknutter.nevertomanybooks.App;

/**
 * * String ID and args are stored for later retrieval.
 * * The messages will be shown to the user.
 */
public abstract class FormattedMessageException
        extends Exception {

    @StringRes
    final int mStringId;
    /** Args to pass to format function. */
    @Nullable
    private final Object[] mArgs;

    @SuppressWarnings("WeakerAccess")
    public FormattedMessageException(@StringRes final int stringId) {
        mStringId = stringId;
        mArgs = null;
    }

    public FormattedMessageException(@StringRes final int stringId,
                                     @NonNull final Object... args) {
        mStringId = stringId;
        mArgs = args;
    }

    /**
     * Use {@link #getLocalizedMessage(Context)} directly if possible.
     */
    public String getLocalizedMessage() {
        Context userContext = App.getFakeUserContext();
        return getLocalizedMessage(userContext);
    }

    @NonNull
    public String getLocalizedMessage(@NonNull final Context context) {
        if (mArgs != null) {
            return context.getString(mStringId, mArgs);
        } else {
            return context.getString(mStringId);
        }
    }
}
