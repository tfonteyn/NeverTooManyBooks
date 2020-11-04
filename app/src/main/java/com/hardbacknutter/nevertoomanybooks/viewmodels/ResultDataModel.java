/*
 * @Copyright 2020 HardBackNutter
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
package com.hardbacknutter.nevertoomanybooks.viewmodels;

import android.app.Activity;
import android.content.Intent;

import androidx.annotation.NonNull;
import androidx.lifecycle.ViewModel;

/**
 * A model that allows multiple fragments and the activity all
 * to share a single data Intent to calling {@link android.app.Activity#setResult(int, Intent)}.
 *
 * <strong>Note:</strong> should always be created in the Activity scope.
 */
public class ResultDataModel
        extends ViewModel
        implements ActivityResultDataModel {

    /** Accumulate all data that will be send in {@link Activity#setResult}. */
    @NonNull
    private final Intent mResultData = new Intent();

    @Override
    @NonNull
    public Intent getResultIntent() {
        return mResultData;
    }

    public void putResultData(@NonNull final Intent data) {
        mResultData.putExtras(data);
    }
}
