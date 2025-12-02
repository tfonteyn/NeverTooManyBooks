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

import androidx.annotation.AnyThread;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.UiThread;

import java.lang.ref.WeakReference;
import java.util.Objects;
import java.util.UUID;

import com.hardbacknutter.nevertoomanybooks.R;

public class ImageReference {

    @NonNull
    private final WeakReference<ImageView> viewReference;
    @NonNull
    private final UUID taskUuid;

    @UiThread
    public ImageReference(@NonNull final ImageView view) {
        taskUuid = UUID.randomUUID();
        view.setTag(R.id.TAG_THUMBNAIL_TASK, taskUuid);
        this.viewReference = new WeakReference<>(view);
    }

    /**
     * Get the actual View.
     * <p>
     * The view association will be cleared.
     * This method should only be called once after background work
     * is finished.
     *
     * @return view, or {@code null} if the view was no longer associated.
     */
    @UiThread
    @Nullable
    public ImageView getView() {
        final ImageView view = viewReference.get();
        final boolean associated = isAssociated(view);
        // always clear the association, but do NOT clear the local taskUuid.
        view.setTag(R.id.TAG_THUMBNAIL_TASK, null);
        return associated ? view : null;
    }

    @AnyThread
    boolean isAssociated() {
        return isAssociated(viewReference.get());
    }

    private boolean isAssociated(@Nullable final ImageView view) {
        return view != null && Objects.equals(taskUuid, view.getTag(R.id.TAG_THUMBNAIL_TASK));
    }

}
