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
package com.hardbacknutter.nevertoomanybooks.settings.styles;

import android.os.Bundle;
import android.view.View;

import androidx.annotation.Keep;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.widget.Toolbar;
import androidx.lifecycle.ViewModelProvider;

import com.hardbacknutter.nevertoomanybooks.R;
import com.hardbacknutter.nevertoomanybooks.booklist.style.Style;
import com.hardbacknutter.nevertoomanybooks.booklist.style.StyleDataStore;
import com.hardbacknutter.nevertoomanybooks.settings.BaseSettingsFragment;
import com.hardbacknutter.prefslib.SettingsDataStore;
import com.hardbacknutter.prefslib.SettingsManager;

/**
 * Sub settings editor for a single style.
 */
@Keep
public class StyleBooklistBookLevelFieldsFragment
        extends BaseSettingsFragment {

    private StyleViewModel vm;

    @Override
    public void onCreate(@Nullable final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        //noinspection DataFlowIssue
        vm = new ViewModelProvider(getActivity()).get(StyleViewModel.class);
    }

    /**
     * NEWTHINGS: BookLevelField: Keys must be kept in sync.
     *   {@link StyleDataStore} keys
     *   {@link com.hardbacknutter.nevertoomanybooks.booklist.style.BaseStyle}
     *          BOOK_LEVEL_FIELDS_DEFAULTS
     *
     * @return the builder
     */
    @NonNull
    @Override
    protected SettingsManager.Builder onCreateSettings() {
        final SettingsDataStore store = vm.getStyleDataStore();
        //noinspection DataFlowIssue
        final SettingsManager.Builder factory = new SettingsManager.Builder(getContext(), store);

        factory.header(R.string.pt_bob_show_details, p -> {
            p.setSorted(true);
        });

        factory.bool(StyleDataStore.VIS_PREFIX + "author",
                     R.string.lbl_author, false);
        factory.bool(StyleDataStore.VIS_PREFIX + "series",
                     R.string.lbl_series, true);
        factory.bool(StyleDataStore.VIS_PREFIX + "publisher",
                     R.string.lbl_publisher, false);
        factory.bool(StyleDataStore.VIS_PREFIX + "bookshelves",
                     R.string.lbl_bookshelves, false);
        factory.bool(StyleDataStore.VIS_PREFIX + "original.title",
                     R.string.lbl_original_title, false);
        factory.bool(StyleDataStore.VIS_PREFIX + "original.language",
                     R.string.lbl_original_language, false);
        factory.bool(StyleDataStore.VIS_PREFIX + "condition",
                     R.string.lbl_condition, false);
        factory.bool(StyleDataStore.VIS_PREFIX + "isbn",
                     R.string.lbl_isbn, false);
        factory.bool(StyleDataStore.VIS_PREFIX + "publication.date",
                     R.string.lbl_date_published, false);
        factory.bool(StyleDataStore.VIS_PREFIX + "first.publication.date",
                     R.string.lbl_date_first_publication, false);
        factory.bool(StyleDataStore.VIS_PREFIX + "date.added",
                     R.string.lbl_date_added, false);
        factory.bool(StyleDataStore.VIS_PREFIX + "date.updated",
                     R.string.lbl_date_last_updated, false);
        factory.bool(StyleDataStore.VIS_PREFIX + "date.acquired",
                     R.string.lbl_date_acquired, false);
        factory.bool(StyleDataStore.VIS_PREFIX + "format",
                     R.string.lbl_format, false);
        factory.bool(StyleDataStore.VIS_PREFIX + "language",
                     R.string.lbl_language, false);
        factory.bool(StyleDataStore.VIS_PREFIX + "location",
                     R.string.lbl_location, false);
        factory.bool(StyleDataStore.VIS_PREFIX + "rating",
                     R.string.lbl_rating, false);
        factory.bool(StyleDataStore.VIS_PREFIX + "pages",
                     R.string.lbl_pages, false);
        factory.bool(StyleDataStore.VIS_PREFIX + "signed",
                     R.string.lbl_signed, true);
        factory.bool(StyleDataStore.VIS_PREFIX + "edition",
                     R.string.lbl_edition, true);
        factory.bool(StyleDataStore.VIS_PREFIX + "loanee",
                     R.string.lbl_lend_out, true);
        factory.bool(StyleDataStore.VIS_PREFIX + "reading.progress",
                     R.string.lbl_track_progress, false);
        factory.bool(StyleDataStore.VIS_PREFIX + "read",
                     R.string.lbl_read, true);

        return factory;
    }

    @Override
    public void onViewCreated(@NonNull final View view,
                              @Nullable final Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        final Style style = vm.getStyle();

        final Toolbar toolbar = getToolbar();
        if (style.getId() == 0) {
            toolbar.setTitle(R.string.lbl_clone_style);
        } else {
            toolbar.setTitle(R.string.lbl_edit_style);
        }
        //noinspection DataFlowIssue
        toolbar.setSubtitle(style.getLabel(getContext()));
    }
}
