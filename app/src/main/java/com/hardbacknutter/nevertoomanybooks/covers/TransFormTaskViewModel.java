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
package com.hardbacknutter.nevertoomanybooks.covers;

import androidx.annotation.NonNull;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

/**
 * Acts as a listener handover between a {@link TransFormTask} and the {@link CoverHandler}.
 */
public class TransFormTaskViewModel
        extends ViewModel
        implements TransFormTask.OnAfterTransformListener {

    private final MutableLiveData<TransFormTask.TransformedData> mData = new MutableLiveData<>();

    MutableLiveData<TransFormTask.TransformedData> onFinished() {
        return mData;
    }

    @Override
    public void onFinished(@NonNull final TransFormTask.TransformedData data) {
        mData.setValue(data);
    }
}
