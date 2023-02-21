/*
 * @Copyright 2018-2023 HardBackNutter
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
package com.hardbacknutter.nevertoomanybooks.searchengines.stripinfo;

import android.content.Context;
import android.os.Bundle;
import android.text.InputType;

import androidx.annotation.Keep;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.util.Pair;
import androidx.preference.EditTextPreference;
import androidx.preference.ListPreference;

import java.util.List;

import com.hardbacknutter.nevertoomanybooks.BuildConfig;
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
    // category
    private static final String PSK_SYNC_OPTIONS = "psk_sync_options";

    @Override
    public void onCreatePreferences(@Nullable final Bundle savedInstanceState,
                                    @Nullable final String rootKey) {
        super.onCreatePreferences(savedInstanceState, rootKey);
        init(R.string.site_stripinfo_be, StripInfoHandler.PK_ENABLED);
        setPreferencesFromResource(R.xml.preferences_site_stripinfo, rootKey);

        //noinspection ConstantConditions
        findPreference(PSK_SYNC_OPTIONS)
                .setVisible(BuildConfig.ENABLE_STRIP_INFO_LOGIN);

        if (BuildConfig.ENABLE_STRIP_INFO_LOGIN) {

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

            final Context context = getContext();
            //noinspection ConstantConditions
            final long id = Bookshelf.getBookshelf(context, Bookshelf.PREFERRED)
                                     .map(Bookshelf::getId)
                                     .orElse((long) Bookshelf.DEFAULT);
            final Pair<CharSequence[], CharSequence[]> values = getBookshelves();
            initBookshelfMapperPref(BookshelfMapper.PK_BOOKSHELF_OWNED, id, values);
            initBookshelfMapperPref(BookshelfMapper.PK_BOOKSHELF_WISHLIST, id, values);
        }
    }

    /**
     * Get two arrays with matching name and id's for all Bookshelves.
     *
     * @return Pair of (entries,entryValues)
     */
    @NonNull
    private Pair<CharSequence[], CharSequence[]> getBookshelves() {
        final List<Bookshelf> all = ServiceLocator.getInstance().getBookshelfDao().getAll();
        final CharSequence[] entries = new CharSequence[all.size()];
        final CharSequence[] entryValues = new CharSequence[all.size()];

        int i = 0;
        for (final Bookshelf bookshelf : all) {
            entries[i] = bookshelf.getName();
            entryValues[i] = String.valueOf(bookshelf.getId());
            i++;
        }

        return new Pair<>(entries, entryValues);
    }

    private void initBookshelfMapperPref(
            @NonNull final CharSequence key,
            final long defaultId,
            @NonNull final Pair<CharSequence[], CharSequence[]> values) {

        final ListPreference p = findPreference(key);
        //noinspection ConstantConditions
        p.setEntries(values.first);
        p.setEntryValues(values.second);
        p.setSummaryProvider(ListPreference.SimpleSummaryProvider.getInstance());

        // The ListPreference has an issue that the initial value is set during the inflation
        // step. At that time, the default value is ONLY available from xml.
        // Internally it will then use this to set the value.
        // Workaround: set the default, and if the pref has no value, set it as well...
        final String defValue = String.valueOf(defaultId);
        p.setDefaultValue(defValue);
        if (p.getValue() == null) {
            p.setValue(defValue);
        }
    }
}
