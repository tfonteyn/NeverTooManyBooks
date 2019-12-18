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
package com.hardbacknutter.nevertoomanybooks.widgets;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.util.AttributeSet;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import androidx.appcompat.app.AlertDialog;
import androidx.preference.MultiSelectListPreference;
import androidx.preference.MultiSelectListPreferenceDialogFragmentCompat;

import com.hardbacknutter.nevertoomanybooks.R;

/**
 * Handles a secondary preference key to enable/disabled the actual preference.
 * <p>
 * See {@link com.hardbacknutter.nevertoomanybooks.settings.StyleFiltersFragment}.
 */
public class BitmaskPreference
        extends MultiSelectListPreference {

    /** See {@link com.hardbacknutter.nevertoomanybooks.booklist.filters.BitmaskFilter}. */
    private static final String ACTIVE = ".active";

    public BitmaskPreference(@NonNull final Context context,
                             @Nullable final AttributeSet attrs,
                             final int defStyleAttr,
                             final int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);
    }

    public BitmaskPreference(@NonNull final Context context,
                             @Nullable final AttributeSet attrs,
                             final int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    public BitmaskPreference(@NonNull final Context context,
                             @Nullable final AttributeSet attrs) {
        super(context, attrs);
    }

    public BitmaskPreference(@NonNull final Context context) {
        super(context);
    }

    public boolean isUsed() {
        return getSharedPreferences().getBoolean(getKey() + ACTIVE, false);
    }

    public void setUsed(final boolean used) {
        SharedPreferences.Editor ed = getSharedPreferences().edit();
        ed.putBoolean(getKey() + ACTIVE, used).apply();
        if (!used) {
            ed.remove(getKey());
        }
        ed.apply();
    }

    /**
     * Custom dialog showing a 3rd button with an "unused" to deactivate the entire bitmask.
     */
    public static class BitmaskPreferenceDialogFragment
            extends MultiSelectListPreferenceDialogFragmentCompat {

        private static final String BKEY_UNUSED_STRING_ID = "unusedStrId";

        /** Default text for the neutral button. */
        @StringRes
        private int mUnusedStringId = R.string.unused;

        /**
         * Constructor.
         *
         * @param key            for the preference
         * @param unusedStringId string to use for the 'unused' button.
         *
         * @return instance
         */
        public static BitmaskPreferenceDialogFragment newInstance(@NonNull final String key,
                                                                  final int unusedStringId) {
            final BitmaskPreferenceDialogFragment fragment = new BitmaskPreferenceDialogFragment();
            final Bundle args = new Bundle(2);
            args.putString(ARG_KEY, key);
            args.putInt(BKEY_UNUSED_STRING_ID, unusedStringId);
            fragment.setArguments(args);
            return fragment;
        }

        @Override
        public void onCreate(@Nullable final Bundle savedInstanceState) {
            super.onCreate(savedInstanceState);

            Bundle args = getArguments();
            if (args != null) {
                mUnusedStringId = args.getInt(BKEY_UNUSED_STRING_ID, R.string.unused);
            }
        }

        @Override
        protected void onPrepareDialogBuilder(@NonNull final AlertDialog.Builder builder) {
//            builder.setMessage(getString(R.string.info_bitmask_preference,
//                               getString(mUnusedStringId)));
            builder.setNeutralButton(mUnusedStringId, (dialog, which) -> {
                final BitmaskPreference preference = (BitmaskPreference) getPreference();
                preference.setUsed(false);
                preference.getValues().clear();
            });
            super.onPrepareDialogBuilder(builder);
        }

        @Override
        public void onDialogClosed(final boolean positiveResult) {
            final BitmaskPreference preference = (BitmaskPreference) getPreference();
            if (preference.isUsed()) {
                // handle ok/cancel buttons as normal.
                if (positiveResult) {
                    preference.setUsed(true);
                }
                super.onDialogClosed(positiveResult);
            } else {
                super.onDialogClosed(false);
            }
        }
    }
}
