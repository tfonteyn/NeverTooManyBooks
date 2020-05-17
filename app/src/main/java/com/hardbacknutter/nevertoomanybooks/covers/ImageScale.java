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
package com.hardbacknutter.nevertoomanybooks.covers;

import android.content.Context;

import androidx.annotation.AnyThread;
import androidx.annotation.IntDef;
import androidx.annotation.NonNull;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

import com.hardbacknutter.nevertoomanybooks.R;

/**
 * Scaling of thumbnails in the application.
 * <p>
 * Must be kept in sync with res/values/strings-preferences.xml#pv_cover_scale_factor
 * <p>
 * res/xml/preferences_styles.xml contains the default set to SCALE_MEDIUM
 */
public final class ImageScale {

    /** Thumbnail Scaling. */
    public static final int SCALE_NOT_DISPLAYED = 0;
    /** Thumbnail Scaling. */
    public static final int SCALE_X_SMALL = 1;
    /** Thumbnail Scaling. */
    public static final int SCALE_SMALL = 2;
    /** Thumbnail Scaling. */
    public static final int SCALE_MEDIUM = 3;
    /** Thumbnail Scaling. */
    public static final int SCALE_LARGE = 4;
    /** Thumbnail Scaling. */
    public static final int SCALE_X_LARGE = 5;
    /** Thumbnail Scaling. */
    public static final int SCALE_2X_LARGE = 6;

    /** scaling factor for each SCALE_* option. See {@link #getSize}. */
    private static final int[] SCALE_FACTOR = {0, 1, 2, 3, 5, 8, 12};

    private ImageScale() {
    }

    /**
     * Get the pixel size an image should be based on the desired scale factor.
     *
     * @param context Current context
     * @param scale   to apply
     *
     * @return amount in pixels
     */
    @AnyThread
    public static int getSize(@NonNull final Context context,
                              @Scale final int scale) {
        return SCALE_FACTOR[scale]
               * (int) context.getResources().getDimension(R.dimen.cover_base_size);
    }

    @IntDef({SCALE_NOT_DISPLAYED, SCALE_X_SMALL, SCALE_SMALL, SCALE_MEDIUM,
             SCALE_LARGE, SCALE_X_LARGE, SCALE_2X_LARGE})
    @Retention(RetentionPolicy.SOURCE)
    public @interface Scale {

    }
}
