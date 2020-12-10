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
package com.hardbacknutter.nevertoomanybooks.booklist.style;

import android.content.Context;
import android.content.res.TypedArray;

import androidx.annotation.IntDef;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.util.Map;
import java.util.Objects;

import com.hardbacknutter.nevertoomanybooks.R;
import com.hardbacknutter.nevertoomanybooks.booklist.style.prefs.PInteger;
import com.hardbacknutter.nevertoomanybooks.booklist.style.prefs.PPref;

/**
 * Encapsulate Font Scale and all related data/logic.
 * <p>
 * Dev. Note: the resource attributes depend on the {@link #mScale}, so we can't cache
 * them in the constructor.
 */
public class TextScale {

    /** <strong>ALL</strong> text. */
    public static final String PK_TEXT_SCALE = "style.booklist.scale.font";

    /**
     * Text Scaling.
     * NEVER change these values, they get stored in preferences.
     * The book title in the list is by default 'medium' (see styles.xml)
     * Other elements are always 1 size 'less' than the title.
     */
    static final int TEXT_SCALE_0_VERY_SMALL = 0;
    /** Text Scaling. */
    static final int TEXT_SCALE_1_SMALL = 1;
    /** Text Scaling. This is the default. */
    static final int TEXT_SCALE_2_MEDIUM = 2;
    /** Text Scaling. */
    static final int TEXT_SCALE_3_LARGE = 3;
    /** Text Scaling. */
    static final int TEXT_SCALE_4_VERY_LARGE = 4;

    /** Relative size of list text. */
    private final PInteger mScale;

    /**
     * Constructor.
     *
     * @param isPersistent     flag
     * @param persistenceLayer Style reference.
     */
    TextScale(final boolean isPersistent,
              @NonNull final StylePersistenceLayer persistenceLayer) {
        mScale = new PInteger(isPersistent, persistenceLayer, PK_TEXT_SCALE, TEXT_SCALE_2_MEDIUM);
    }

    /**
     * Copy constructor.
     *
     * @param isPersistent     flag
     * @param persistenceLayer Style reference.
     * @param textScale        to copy from
     */
    TextScale(final boolean isPersistent,
              @NonNull final StylePersistenceLayer persistenceLayer,
              @NonNull final TextScale textScale) {
        mScale = new PInteger(isPersistent, persistenceLayer, textScale.mScale);
    }

    /**
     * Used by built-in styles only. Set by user via preferences screen.
     *
     * @param scale id
     */
    @SuppressWarnings("SameParameterValue")
    void set(@Scale final int scale) {
        mScale.set(scale);
    }

    /**
     * Get the scaling factor to apply to the View padding if text is scaled.
     *
     * @param context Current context
     *
     * @return scale factor
     */
    public float getPaddingFactor(@NonNull final Context context) {
        final TypedArray ta = context.getResources()
                                     .obtainTypedArray(R.array.bob_text_padding_in_percent);
        try {
            return ta.getFloat(mScale.getValue(), TEXT_SCALE_2_MEDIUM);
        } finally {
            ta.recycle();
        }
    }

    /**
     * Get the text <strong>size in SP units</strong> to apply.
     *
     * @param context Current context
     *
     * @return sp units
     */
    public float getFontSizeInSpUnits(@NonNull final Context context) {
        final TypedArray ta = context.getResources()
                                     .obtainTypedArray(R.array.bob_text_size_in_sp);
        try {
            return ta.getFloat(mScale.getValue(), TEXT_SCALE_2_MEDIUM);
        } finally {
            ta.recycle();
        }
    }

    /**
     * Convenience method for use in the Preferences screen.
     * Get the summary text for the scale factor to be used.
     *
     * @param context Current context
     *
     * @return summary text
     */
    public String getFontScaleSummaryText(@NonNull final Context context) {
        return context.getResources().getStringArray(R.array.pe_bob_text_scale)[mScale.getValue()];
    }

    /**
     * Check if the current setting is the default.
     *
     * @return {@code true} if this is the default
     */
    public boolean isDefaultScale() {
        return mScale.getValue() == TEXT_SCALE_2_MEDIUM;
    }

    /**
     * Add all entries to the given map.
     *
     * @param map to add to
     */
    void addToMap(@NonNull final Map<String, PPref> map) {
        map.put(mScale.getKey(), mScale);
    }

    @Override
    public boolean equals(@Nullable final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final TextScale textScale = (TextScale) o;
        return Objects.equals(mScale, textScale.mScale);
    }

    @Override
    public int hashCode() {
        return Objects.hash(mScale);
    }

    @NonNull
    @Override
    public String toString() {
        return "TextScale{"
               + "mScale=" + mScale
               + '}';
    }

    @IntDef({TEXT_SCALE_0_VERY_SMALL,
             TEXT_SCALE_1_SMALL,
             TEXT_SCALE_2_MEDIUM,
             TEXT_SCALE_3_LARGE,
             TEXT_SCALE_4_VERY_LARGE})
    @Retention(RetentionPolicy.SOURCE)
    @interface Scale {

    }
}
