/*
 * @Copyright 2018-2025 HardBackNutter
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

package com.hardbacknutter.nevertoomanybooks.settings.tags;

import androidx.annotation.NonNull;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.ViewModel;

import java.util.Map;
import java.util.Set;

import com.hardbacknutter.nevertoomanybooks.core.tasks.LiveDataEvent;

@SuppressWarnings("WeakerAccess")
public class TagAdminViewModel
        extends ViewModel {

    private final TagMapperTask mapperTask = new TagMapperTask();
    private boolean modified;

    @Override
    protected void onCleared() {
        mapperTask.cancel();
        super.onCleared();
    }

    boolean isModified() {
        return modified;
    }

    void setModified() {
        this.modified = true;
    }

    @NonNull
    LiveData<LiveDataEvent<Throwable>> onTagMapperFailure() {
        return mapperTask.onFailure();
    }

    @NonNull
    LiveData<LiveDataEvent<Map<TagMapperTask.Options, Integer>>> onTagMapperCancelled() {
        return mapperTask.onCancelled();
    }

    @NonNull
    LiveData<LiveDataEvent<Map<TagMapperTask.Options, Integer>>> onTagMapperFinished() {
        return mapperTask.onFinished();
    }

    void startTagMapper(@NonNull final Set<TagMapperTask.Options> options) {
        mapperTask.start(options);
    }

    void cancelTagMapper() {
        mapperTask.cancel();
    }
}
