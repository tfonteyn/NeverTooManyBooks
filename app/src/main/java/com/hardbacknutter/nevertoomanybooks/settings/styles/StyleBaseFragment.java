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
package com.hardbacknutter.nevertoomanybooks.settings.styles;

import android.content.SharedPreferences;
import android.os.Bundle;
import android.util.Log;

import androidx.annotation.CallSuper;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.XmlRes;
import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AppCompatActivity;

import com.hardbacknutter.nevertoomanybooks.BuildConfig;
import com.hardbacknutter.nevertoomanybooks.DEBUG_SWITCHES;
import com.hardbacknutter.nevertoomanybooks.R;
import com.hardbacknutter.nevertoomanybooks.UniqueId;
import com.hardbacknutter.nevertoomanybooks.booklist.BooklistStyle;
import com.hardbacknutter.nevertoomanybooks.settings.BasePreferenceFragment;

/**
 * Settings editor for a Style.
 * <p>
 * Passing in a style with a valid UUID, settings are read/written to the style specific file.
 * If the uuid is {@code null}, then we're editing the global defaults.
 */
public abstract class StyleBaseFragment
        extends BasePreferenceFragment {

    /** Fragment manager tag. */
    public static final String TAG = "StylePreferenceFragment";

    static final String BKEY_TEMPLATE_ID = TAG + ":templateId";

    /** Style we are editing. */
    BooklistStyle mStyle;

    @XmlRes
    protected abstract int getLayoutId();

    @Override
    @CallSuper
    public void onCreatePreferences(@Nullable final Bundle savedInstanceState,
                                    @Nullable final String rootKey) {

        Bundle args = getArguments();
        if (args != null) {
            mStyle = args.getParcelable(UniqueId.BKEY_STYLE);
        }

        if (mStyle == null) {
            // we're doing the global preferences
            mStyle = new BooklistStyle();
            if (BuildConfig.DEBUG /* always */) {
                Log.d(TAG, "onCreatePreferences|we're doing the global preferences");
            }
        }

        if (BuildConfig.DEBUG && DEBUG_SWITCHES.DUMP_STYLE) {
            Log.d(TAG, "onCreatePreferences|" + mStyle);
        }

        // We use the style UUID as the filename for the prefs.
        String uuid = mStyle.getUuid();
        if (!uuid.isEmpty()) {
            getPreferenceManager().setSharedPreferencesName(uuid);
        }
        // else if uuid.isEmpty(), use global SharedPreferences for editing global defaults

        setPreferencesFromResource(getLayoutId(), rootKey);
    }

    @Override
    @CallSuper
    public void onActivityCreated(@Nullable final Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);

        // always pass the non-global style back; whether existing or new.
        // so even if the user makes no changes, we still send it back!
        if (!mStyle.getUuid().isEmpty()) {
            mResultDataModel.putResultData(UniqueId.BKEY_STYLE, mStyle);
        }

        // always pass the template id back; not currently used here.
        Bundle args = getArguments();
        if (args != null) {
            mResultDataModel.putResultData(BKEY_TEMPLATE_ID, args.getLong(BKEY_TEMPLATE_ID));
        }

        @SuppressWarnings("ConstantConditions")
        ActionBar actionBar = ((AppCompatActivity) getActivity()).getSupportActionBar();
        if (actionBar != null) {
            if (mStyle.getId() == 0) {
                actionBar.setTitle(R.string.title_clone_style);
            } else {
                actionBar.setTitle(R.string.title_edit_style);
            }
            //noinspection ConstantConditions
            actionBar.setSubtitle(mStyle.getLabel(getContext()));
        }
    }

    @Override
    @CallSuper
    public void onSharedPreferenceChanged(@NonNull final SharedPreferences sharedPreferences,
                                          @NonNull final String key) {
        super.onSharedPreferenceChanged(sharedPreferences, key);

        // set the result (and again and again...)
        mResultDataModel.putResultData(UniqueId.BKEY_STYLE_MODIFIED, true);
        mResultDataModel.putResultData(UniqueId.BKEY_STYLE, mStyle);
    }

}
