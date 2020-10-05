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
package com.hardbacknutter.nevertoomanybooks.settings.styles;

import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AppCompatActivity;

import com.hardbacknutter.nevertoomanybooks.R;
import com.hardbacknutter.nevertoomanybooks.booklist.style.BooklistStyle;
import com.hardbacknutter.nevertoomanybooks.settings.BasePreferenceFragment;

/**
 * Settings editor for a Style.
 * <p>
 * Passing in a style with a valid UUID, settings are read/written to the style specific file.
 * If the uuid is {@code ""}, then we're editing the global defaults.
 */
public abstract class StyleBaseFragment
        extends BasePreferenceFragment {

    /** Log tag. */
    private static final String TAG = "StyleBaseFragment";
    public static final String BKEY_TEMPLATE_ID = TAG + ":templateId";

    /** Style we are editing. */
    BooklistStyle mStyle;

    private long mTemplateId;

    @Override
    public void onCreatePreferences(@Nullable final Bundle savedInstanceState,
                                    @Nullable final String rootKey) {
        super.onCreatePreferences(savedInstanceState, rootKey);

        final Bundle args = getArguments();
        if (args != null) {
            mStyle = args.getParcelable(BooklistStyle.BKEY_STYLE);
            mTemplateId = args.getLong(BKEY_TEMPLATE_ID);
        }

        if (mStyle != null) {
            // a user-style, set the correct UUID SharedPreferences to use
            getPreferenceManager().setSharedPreferencesName(mStyle.getUuid());
        } else {
            // we're doing the global preferences, create a placeholder style with an empty uuid
            // and let it use the standard SharedPreferences
            //noinspection ConstantConditions
            mStyle = new BooklistStyle(getContext());
        }

        // always pass the non-global style back; whether existing or new.
        // so even if the user makes no changes, we still send it back!
        // If the user does make changes, we'll overwrite it in onSharedPreferenceChanged
        if (!mStyle.isGlobal()) {
            mResultData.putResultData(BooklistStyle.BKEY_STYLE, mStyle);
        }

        // always pass the template id back if we had one.
        if (mTemplateId != 0) {
            mResultData.putResultData(BKEY_TEMPLATE_ID, mTemplateId);
        }

        // and the actual/current id+uuid
        // mResultDataModel.putResultData(BooklistStyle.BKEY_STYLE_ID, mStyle.getId());
        // mResultDataModel.putResultData(BooklistStyle.BKEY_STYLE_UUID, mStyle.getUuid());
    }

    @Override
    public void onViewCreated(@NonNull final View view,
                              @Nullable final Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        //noinspection ConstantConditions
        final ActionBar actionBar = ((AppCompatActivity) getActivity()).getSupportActionBar();
        if (mStyle.getId() == 0) {
            //noinspection ConstantConditions
            actionBar.setTitle(R.string.lbl_clone_style);
        } else {
            //noinspection ConstantConditions
            actionBar.setTitle(R.string.lbl_edit_style);
        }
        //noinspection ConstantConditions
        actionBar.setSubtitle(mStyle.getLabel(getContext()));
    }

    @Override
    public void onSharedPreferenceChanged(@NonNull final SharedPreferences preferences,
                                          @NonNull final String key) {
        super.onSharedPreferenceChanged(preferences, key);

        // set the result (and again and again...)
        mResultData.putResultData(BooklistStyle.BKEY_STYLE_MODIFIED, true);
        mResultData.putResultData(BooklistStyle.BKEY_STYLE, mStyle);
    }
}
