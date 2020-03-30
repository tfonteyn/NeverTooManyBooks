/*
 * @Copyright 2020 HardBackNutter
 * @License GNU General Public License
 *
 * This file is part of NeverTooManyBooks.
 *
 * In August 2018, this project was forked from:
 * Book Catalogue 5.2.2 @2016 Philip Warner & Evan Leybourn
 *
 * Without their original creation, this project would not exist in its
 * current form. It was however largely rewritten/refactored and any
 * comments on this fork should be directed at HardBackNutter and not
 * at the original creators.
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
package com.hardbacknutter.nevertoomanybooks.datamanager.validators;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.annotation.StringRes;

import com.hardbacknutter.nevertoomanybooks.utils.exceptions.FormattedMessageException;

/**
 * Exception class for all validation errors.
 */
public class ValidatorException
        extends FormattedMessageException {

    private static final long serialVersionUID = 6008774357227588993L;
    @StringRes
    private int mErrorLabelId;

    public ValidatorException(@StringRes final int msgId) {
        super(msgId);
    }

    public ValidatorException(@StringRes final int msgId,
                              @StringRes final int errorLabelId) {
        super(msgId);
        mErrorLabelId = errorLabelId;
    }

    @NonNull
    @Override
    public String getLocalizedMessage(@NonNull final Context context) {
        if (mErrorLabelId != 0) {
            return context.getString(mMsgId, context.getString(mErrorLabelId));
        } else {
            return context.getString(mMsgId);
        }
    }
}
