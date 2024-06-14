/*
 * @Copyright 2018-2024 HardBackNutter
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

package com.hardbacknutter.nevertoomanybooks.sync.stripinfo;

import android.content.Context;
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.util.Pair;
import androidx.fragment.app.Fragment;
import androidx.preference.ListPreference;

import java.util.List;

import com.hardbacknutter.nevertoomanybooks.R;
import com.hardbacknutter.nevertoomanybooks.ServiceLocator;
import com.hardbacknutter.nevertoomanybooks.entities.Bookshelf;
import com.hardbacknutter.nevertoomanybooks.settings.BasePreferenceFragment;
import com.hardbacknutter.nevertoomanybooks.sync.SyncServer;

public class StripInfoBookshelfMappingFragment
        extends BasePreferenceFragment {

    /** Fragment/Log tag. */
    public static final String TAG = "StripInfoBookshelfMapF";

    /**
     * Constructor.
     *
     * @return instance
     */
    @NonNull
    public static Fragment create() {
        final Fragment fragment = new StripInfoBookshelfMappingFragment();
        final Bundle args = new Bundle(1);
        args.putParcelable(SyncServer.BKEY_SITE, SyncServer.StripInfo);
        fragment.setArguments(args);
        return fragment;
    }

    public void onCreatePreferences(@Nullable final Bundle savedInstanceState,
                                    @Nullable final String rootKey) {
        super.onCreatePreferences(savedInstanceState, rootKey);
        setPreferencesFromResource(R.xml.preferences_site_stripinfo_mapping, rootKey);

        final Context context = getContext();
        //noinspection DataFlowIssue
        final long id = ServiceLocator.getInstance().getBookshelfDao()
                                      .getBookshelf(context, Bookshelf.USER_DEFAULT)
                                      .map(Bookshelf::getId)
                                      .orElse((long) Bookshelf.HARD_DEFAULT);
        final Pair<CharSequence[], CharSequence[]> values = getBookshelves();
        initBookshelfMapperPref(BookshelfMapper.PK_BOOKSHELF_OWNED, id, values);
        initBookshelfMapperPref(BookshelfMapper.PK_BOOKSHELF_DIGITAL, id, values);
        initBookshelfMapperPref(BookshelfMapper.PK_BOOKSHELF_WISHLIST, id, values);
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
        //noinspection DataFlowIssue
        p.setEntries(values.first);
        p.setEntryValues(values.second);

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
