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
package com.hardbacknutter.nevertoomanybooks.settings;

import android.os.Bundle;
import android.view.View;

import androidx.annotation.Keep;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.ViewModelProvider;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import com.hardbacknutter.nevertoomanybooks.R;
import com.hardbacknutter.nevertoomanybooks.ServiceLocator;
import com.hardbacknutter.nevertoomanybooks.booklist.style.FieldVisibility;
import com.hardbacknutter.nevertoomanybooks.database.DBKey;
import com.hardbacknutter.prefslib.BooleanSetting;
import com.hardbacknutter.prefslib.Setting;
import com.hardbacknutter.prefslib.SettingsDataStore;
import com.hardbacknutter.prefslib.SettingsManager;

/**
 * NEWTHINGS: new fields visibility.
 * <p>
 *      Key name **MUST** be equal to the {@link DBKey} name.
 *      The front/back cover are handled specially by {@link DBKey#COVER}
 * <p>
 * The list here must be kept in sync with the bit-fields in
 * {@link com.hardbacknutter.nevertoomanybooks.booklist.style.FieldVisibility}
 *      FIXME: dynamically build this screen using those bitfields ?
 *       but what to do with the exceptions (always visible) fields as documented below?
 * <p>
 * Preferences are sorted and shown in LOCALE-alphabetical order.
 */
@Keep
public class FieldVisibilityPreferenceFragment
        extends BaseSettingsFragment {

    @NonNull
    private final List<String> pCoverKeys = new ArrayList<>();
    private SettingsViewModel vm;

    @NonNull
    @Override
    protected SettingsManager.Builder onCreateSettings() {
        final SettingsDataStore store = new VSDataStore();
        //noinspection DataFlowIssue
        final SettingsManager.Builder factory = new SettingsManager.Builder(getContext(), store)
                // Use a single listener as all options are handled the same
                .setChangedListener(this::onChange);

        factory.header(R.string.pt_field_visibility, p -> {
            p.setSummary(R.string.ps_manage_fields);
            p.setSorted(true);
        });

        // bit: 0
        factory.bool(DBKey.COVER[0], R.string.lbl_cover_front, true);
        // bit: 1
        factory.bool(DBKey.COVER[1], R.string.lbl_cover_back, true);

        // bit 2: Author is always visible
        // bit 3: Bookshelf is always visible

        // bit: 4; Series name includes Series 'isComplete' and Series number.
        factory.bool(DBKey.FK_SERIES, R.string.lbl_series, true);
        // bit: 5
        factory.bool(DBKey.FK_PUBLISHER, R.string.lbl_publisher, true);
        // bit: 6
        factory.bool(DBKey.CONTENT_TYPE, R.string.lbl_table_of_content, true);

        // bit 7: Lending is handled elsewhere

        // bit: 8
        factory.bool(DBKey.AUTHOR.BOOK_AUTHOR_ROLE, R.string.lbl_author_role, true);
        // bit: 9
        factory.bool(DBKey.CONDITION_BOOK, R.string.lbl_condition, true);
        // bit: 10
        factory.bool(DBKey.CONDITION_COVER, R.string.lbl_dust_cover, true);
        // bit: 11
        factory.bool(DBKey.ISBN, R.string.lbl_isbn, true);
        // bit: 12
        factory.bool(DBKey.PUBLICATION_DATE, R.string.lbl_date_published, true);
        // bit: 13
        factory.bool(DBKey.COLOR, R.string.lbl_color, true);
        // bit: 14
        factory.bool(DBKey.DESCRIPTION, R.string.lbl_description, true);
        // bit: 15
        // This is the icon/flag only.
        // The free-form text field is bit 40
        factory.bool(DBKey.EDITION_FLAGS, R.string.lbl_edition, true);
        // bit: 16
        factory.bool(DBKey.FIRST_PUBLICATION_DATE, R.string.lbl_date_first_publication, true);
        // bit: 17
        factory.bool(DBKey.FORMAT, R.string.lbl_format, true);
        // bit: 18
        factory.bool(DBKey.FK_TAG, R.string.lbl_tags, true);
        // bit: 19
        factory.bool(DBKey.LANGUAGE, R.string.lbl_language, true);
        // bit: 20
        factory.bool(DBKey.LOCATION, R.string.lbl_location, true);
        // bit: 21
        factory.bool(DBKey.PAGES, R.string.lbl_pages, true);
        // bit: 22
        factory.bool(DBKey.PRICE_LISTED, R.string.lbl_price_listed, true);
        // bit: 23
        factory.bool(DBKey.PRICE_PAID, R.string.lbl_price_paid, true);
        // bit: 24
        factory.bool(DBKey.PERSONAL_NOTES, R.string.lbl_personal_notes, true);
        // bit: 25
        factory.bool(DBKey.RATING, R.string.lbl_rating, true);
        // bit: 26
        factory.bool(DBKey.SIGNED__BOOL, R.string.lbl_signed, true);
        // bit: 27
        // This is the icon/flag only.
        // The detailed status is determined by DBKey.READ_PROGRESS (bit 34)
        factory.bool(DBKey.READ__BOOL, R.string.lbl_read, true);
        // bit: 28
        factory.bool(DBKey.READ_START__DATE, R.string.lbl_read_start, true);
        // bit: 29
        factory.bool(DBKey.READ_END__DATE, R.string.lbl_read_end, true);
        // bit: 30
        factory.bool(DBKey.DATE_ADDED__UTC, R.string.lbl_date_added, true);
        // bit: 31
        factory.bool(DBKey.DATE_LAST_UPDATED__UTC, R.string.lbl_date_last_updated, true);
        // bit: 32
        factory.bool(DBKey.TRANSLATION_ORIGINAL_TITLE, R.string.lbl_original_title, true);
        // bit: 33
        factory.bool(DBKey.FK_AUTHOR_REAL_AUTHOR, R.string.lbl_author_pseudonym, true);
        // bit: 34
        // This is the detailed progress only.
        // The read/unread status is determined by DBKey.READ__BOOL (bit 27)
        factory.bool(DBKey.READ_PROGRESS, R.string.lbl_track_progress, true);
        // bit: 35
        factory.bool(DBKey.PRINT_RUN, R.string.lbl_print_run, true);
        // bit: 36
        factory.bool(DBKey.DATE_ACQUIRED, R.string.lbl_date_acquired, true);
        // bit: 37
        factory.bool(DBKey.TRANSLATION_ORIGINAL_LANGUAGE, R.string.lbl_original_language, true);
        // bit: 38
        factory.bool(DBKey.COVER[2], R.string.lbl_image_2, true);
        // bit: 39
        factory.bool(DBKey.COVER[3], R.string.lbl_image_3, true);
        // bit: 40
        // This is the free-form text
        // For the icon see bit 15
        factory.bool(DBKey.EDITION_INFO, R.string.lbl_edition_info, true);

        return factory;
    }

    @Override
    public void onViewCreated(@NonNull final View view,
                              @Nullable final Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        //noinspection DataFlowIssue
        vm = new ViewModelProvider(getActivity()).get(SettingsViewModel.class);

        pCoverKeys.addAll(Arrays.asList(DBKey.COVER).subList(0, DBKey.NR_OF_BOOK_COVERS));
    }

    private boolean onChange(@NonNull final Setting setting,
                             @Nullable final Object newValue) {

        final String key = setting.getKey();
        final boolean enabled = newValue != null && (boolean) newValue;
        // Did the user disable a cover?
        if (!enabled && pCoverKeys.contains(key)) {
            // disable all further covers as well.
            boolean found = false;

            for (final String coverKeys : pCoverKeys) {
                // Loop until we find the first key
                if (!found && coverKeys.equals(key)) {
                    found = true;
                    continue;
                }

                if (found) {
                    final SettingsManager settingsManager = getSettingsManager();
                    final BooleanSetting coverSetting = settingsManager
                            .requireSetting(coverKeys);
                    // Handle the update/storage/notification !
                    coverSetting.setChecked(false);
                    settingsManager.save(coverSetting);
                }
            }
        }

        // Changing ANY field visibility will usually require recreating the activity
        vm.setForceActivityRecreation();

        return true;
    }

    /**
     * Redirects storage to a single long value.
     */
    private static class VSDataStore
            implements SettingsDataStore {

        @NonNull
        private final FieldVisibility fieldVisibility;

        VSDataStore() {
            fieldVisibility = ServiceLocator.getInstance().getGlobalFieldVisibility();
            fieldVisibility.load();
        }

        @Override
        public void putBoolean(@NonNull final String key,
                               @Nullable final Boolean value) {
            fieldVisibility.setVisible(key, value != null && value);
            fieldVisibility.save();
        }

        @Override
        public boolean getBoolean(@NonNull final String key,
                                  @Nullable final Boolean defValue) {
            return fieldVisibility.isVisible(key).orElse(true);
        }
    }
}
