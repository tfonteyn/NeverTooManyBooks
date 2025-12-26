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

package com.hardbacknutter.nevertoomanybooks.covers;

import android.widget.ImageView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.lang.ref.WeakReference;
import java.util.Objects;

import com.hardbacknutter.nevertoomanybooks.R;

public class ImageReference {

    @NonNull
    private final WeakReference<ImageView> viewReference;
    @NonNull
    private final String taskUuid;

    ImageReference(@Nullable final String uuid,
                   final int cIdx,
                   @NonNull final ImageView view) {
        taskUuid = uuid != null ? uuid + '_' + cIdx : String.valueOf(cIdx);
        view.setTag(R.id.TAG_THUMBNAIL_TASK, taskUuid);
        this.viewReference = new WeakReference<>(view);
    }

    /**
     * Get the View.
     *
     * @return view, or {@code null} if the view was no longer available or associated.
     */
    @Nullable
    public ImageView getView() {
        final ImageView view = viewReference.get();
        if (view == null || !Objects.equals(taskUuid, view.getTag(R.id.TAG_THUMBNAIL_TASK))) {
            return null;
        }
        return view;
    }
}
