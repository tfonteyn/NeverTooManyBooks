/*
 * @Copyright 2018-2026 HardBackNutter
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

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;

import java.util.List;

import com.hardbacknutter.nevertoomanybooks.R;
import com.hardbacknutter.nevertoomanybooks.ServiceLocator;
import com.hardbacknutter.nevertoomanybooks.database.dao.BookshelfDao;
import com.hardbacknutter.nevertoomanybooks.entities.Bookshelf;
import com.hardbacknutter.nevertoomanybooks.settings.BaseSettingsFragment;
import com.hardbacknutter.nevertoomanybooks.sync.SyncServer;
import com.hardbacknutter.nevertoomanybooks.sync.SyncServerInput;
import com.hardbacknutter.prefslib.SettingsDataStore;
import com.hardbacknutter.prefslib.SettingsManager;
import com.hardbacknutter.prefslib.SharedPreferencesDataStore;
import com.hardbacknutter.prefslib.SingleChoiceSetting;

public class StripInfoBookshelfMappingFragment
        extends BaseSettingsFragment {

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
        // 2026-03-24: We're not using this for now, but want to keep the same
        // logic as used by the Calibre bookshelf mapper.
        final SyncServerInput args = new SyncServerInput(SyncServer.StripInfo);
        fragment.setArguments(args.toBundle());
        return fragment;
    }

    @SuppressWarnings("CodeBlock2Expr")
    @NonNull
    protected SettingsManager.Builder onCreateSettings() {
        final SettingsDataStore store = new SharedPreferencesDataStore(
                ServiceLocator.getInstance().getSharedPreferences());
        //noinspection DataFlowIssue
        final SettingsManager.Builder factory = new SettingsManager.Builder(getContext(), store);

        final BookshelfDao bookshelfDao = ServiceLocator.getInstance().getBookshelfDao();
        final String defBookshelfId = String.valueOf(bookshelfDao.getCurrent()
                                                     .orElseGet(bookshelfDao::getDefault)
                                                     .getId());
        final List<Bookshelf> all = ServiceLocator.getInstance().getBookshelfDao().getAll();
        final int size = all.size();
        final CharSequence[] entries = new CharSequence[size];
        final CharSequence[] entryValues = new CharSequence[size];

        int i = 0;
        for (final Bookshelf bookshelf : all) {
            entries[i] = bookshelf.getName();
            entryValues[i] = String.valueOf(bookshelf.getId());
            i++;
        }

        factory.header(R.string.lbl_assign_bookshelves);

        factory.singleChoice(BookshelfMapper.PK_BOOKSHELF_OWNED,
                             R.string.lbl_strip_info_bookshelf_assigned_to_owned_books,
                             null, p -> {
                    initBookshelfMapperPref(p, entries, entryValues, defBookshelfId);
                });

        factory.singleChoice(BookshelfMapper.PK_BOOKSHELF_DIGITAL,
                             R.string.lbl_strip_info_bookshelf_assigned_to_digital_books,
                             null, p -> {
                    initBookshelfMapperPref(p, entries, entryValues, defBookshelfId);
                });

        factory.singleChoice(BookshelfMapper.PK_BOOKSHELF_WISHLIST,
                             R.string.lbl_strip_info_bookshelf_assigned_to_wishlist,
                             null, p -> {
                    initBookshelfMapperPref(p, entries, entryValues, defBookshelfId);
                });

        return factory;
    }

    private void initBookshelfMapperPref(@NonNull final SingleChoiceSetting p,
                                         @NonNull final CharSequence[] entries,
                                         @NonNull final CharSequence[] entryValues,
                                         @NonNull final String defaultBookshelfId) {

        p.setEntries(entries);
        p.setEntryValues(entryValues);

        for (int i = 0; i < entryValues.length; i++) {
            if (entryValues[i].equals(defaultBookshelfId)) {
                p.setSelectedIndex(i);
                break;
            }
        }
    }
}
