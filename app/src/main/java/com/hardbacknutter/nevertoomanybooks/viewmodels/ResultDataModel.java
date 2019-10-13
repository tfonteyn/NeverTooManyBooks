/*
 * @Copyright 2019 HardBackNutter
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
package com.hardbacknutter.nevertoomanybooks.viewmodels;

import android.content.Intent;
import android.os.Parcelable;

import androidx.annotation.NonNull;
import androidx.lifecycle.ViewModel;

/**
 * A model that allows multiple fragments and the activity all
 * to share a single data Intent to calling {@link android.app.Activity#setResult(int, Intent)}.
 */
public class ResultDataModel
        extends ViewModel {

    @NonNull
    private Intent mData = new Intent();

    @NonNull
    public Intent getData() {
        return mData;
    }

    @NonNull
    public ResultDataModel putExtra(@NonNull final String name,
                                    final boolean value) {
        mData.putExtra(name, value);
        return this;
    }

    @NonNull
    public ResultDataModel putExtra(@NonNull final String name,
                                    final int value) {
        mData.putExtra(name, value);
        return this;
    }

    @NonNull
    public ResultDataModel putExtra(@NonNull final String name,
                                    final long value) {
        mData.putExtra(name, value);
        return this;
    }

    @NonNull
    public ResultDataModel putExtra(@NonNull final String name,
                                    @NonNull final Parcelable value) {
        mData.putExtra(name, value);
        return this;
    }

    public void putAll(@NonNull final Intent data) {
        mData.putExtras(data);
    }
}
