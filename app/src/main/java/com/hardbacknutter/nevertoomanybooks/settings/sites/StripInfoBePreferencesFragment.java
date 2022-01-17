/*
 * @Copyright 2018-2021 HardBackNutter
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
package com.hardbacknutter.nevertoomanybooks.settings.sites;

import android.os.Bundle;
import android.text.InputType;
import android.view.View;

import androidx.activity.OnBackPressedCallback;
import androidx.annotation.Keep;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.ViewModelProvider;
import androidx.preference.EditTextPreference;
import androidx.preference.ListPreference;

import java.util.ArrayList;

import com.hardbacknutter.nevertoomanybooks.R;
import com.hardbacknutter.nevertoomanybooks.ServiceLocator;
import com.hardbacknutter.nevertoomanybooks.entities.Bookshelf;
import com.hardbacknutter.nevertoomanybooks.settings.ConnectionValidationBasePreferenceFragment;
import com.hardbacknutter.nevertoomanybooks.sync.stripinfo.BookshelfMapper;
import com.hardbacknutter.nevertoomanybooks.sync.stripinfo.StripInfoAuth;
import com.hardbacknutter.nevertoomanybooks.sync.stripinfo.StripInfoHandler;

@Keep
public class StripInfoBePreferencesFragment
        extends ConnectionValidationBasePreferenceFragment {

    public static final String TAG = "StripInfoBePrefFrag";

    private StripInfoBePreferencesViewModel mVm;

    private final OnBackPressedCallback mOnBackPressedCallback =
            new OnBackPressedCallback(true) {
                @Override
                public void handleOnBackPressed() {
                    proposeConnectionValidation(StripInfoHandler.PK_ENABLED);
                }
            };

    @Override
    public void onCreatePreferences(@Nullable final Bundle savedInstanceState,
                                    @Nullable final String rootKey) {
        super.onCreatePreferences(savedInstanceState, rootKey);

        mVm = new ViewModelProvider(this).get(StripInfoBePreferencesViewModel.class);

        setPreferencesFromResource(R.xml.preferences_site_stripinfo, rootKey);

        EditTextPreference etp;

        etp = findPreference(StripInfoAuth.PK_HOST_USER);
        //noinspection ConstantConditions
        etp.setOnBindEditTextListener(editText -> {
            editText.setInputType(InputType.TYPE_CLASS_TEXT);
            editText.selectAll();
        });
        etp.setSummaryProvider(EditTextPreference.SimpleSummaryProvider.getInstance());

        etp = findPreference(StripInfoAuth.PK_HOST_PASS);
        //noinspection ConstantConditions
        etp.setOnBindEditTextListener(editText -> {
            editText.setInputType(InputType.TYPE_CLASS_TEXT
                                  | InputType.TYPE_TEXT_VARIATION_PASSWORD);
            editText.selectAll();
        });
        etp.setSummaryProvider(preference -> {
            final String value = ((EditTextPreference) preference).getText();
            if (value == null || value.isEmpty()) {
                return getString(R.string.preference_not_set);
            } else {
                return "********";
            }
        });

        final ArrayList<Bookshelf> all = ServiceLocator.getInstance().getBookshelfDao().getAll();
        final CharSequence[] entries = new CharSequence[all.size()];
        final CharSequence[] entryValues = new CharSequence[all.size()];

        int i = 0;
        for (final Bookshelf bookshelf : all) {
            entries[i] = bookshelf.getName();
            entryValues[i] = String.valueOf(bookshelf.getId());
            i++;
        }

        //noinspection ConstantConditions
        final String defValue = String.valueOf(
                Bookshelf.getBookshelf(getContext(), Bookshelf.PREFERRED, Bookshelf.DEFAULT)
                         .getId());

        initBookshelfMapperPref(BookshelfMapper.PK_BOOKSHELF_OWNED, defValue, entries,
                                entryValues);
        initBookshelfMapperPref(BookshelfMapper.PK_BOOKSHELF_WISHLIST, defValue, entries,
                                entryValues);
    }

    private void initBookshelfMapperPref(@NonNull final CharSequence key,
                                         @NonNull final String defValue,
                                         @NonNull final CharSequence[] entries,
                                         @NonNull final CharSequence[] entryValues) {

        final ListPreference p = findPreference(key);
        //noinspection ConstantConditions
        p.setEntries(entries);
        p.setEntryValues(entryValues);
        p.setSummaryProvider(ListPreference.SimpleSummaryProvider.getInstance());

        // The ListPreference has an issue that the initial value is set during the inflation
        // step. At that time, the default value is ONLY available from xml.
        // Internally it will then use this to set the value.
        // Workaround: set the default, and if the pref has no value, set it as well...
        p.setDefaultValue(defValue);
        if (p.getValue() == null) {
            p.setValue(defValue);
        }
    }

    @Override
    public void onViewCreated(@NonNull final View view,
                              @Nullable final Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        //noinspection ConstantConditions
        getActivity().getOnBackPressedDispatcher()
                     .addCallback(getViewLifecycleOwner(), mOnBackPressedCallback);

        mVm.onConnectionSuccessful().observe(getViewLifecycleOwner(), this::onSuccess);
        mVm.onConnectionFailed().observe(getViewLifecycleOwner(), this::onFailure);
        mVm.onProgressUpdate().observe(getViewLifecycleOwner(), this::onProgress);
    }

    protected void validateConnection() {
        mVm.validateConnection();
    }

    @Override
    protected void cancelTask(final int taskId) {
        mVm.cancelTask(taskId);
    }
}
