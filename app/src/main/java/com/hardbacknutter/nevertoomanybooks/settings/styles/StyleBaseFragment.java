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

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.ViewModelProvider;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.util.HashMap;
import java.util.Map;

import com.hardbacknutter.nevertoomanybooks.R;
import com.hardbacknutter.nevertoomanybooks.booklist.grouping.BooklistGroup;
import com.hardbacknutter.nevertoomanybooks.booklist.grouping.GroupSettings;
import com.hardbacknutter.nevertoomanybooks.booklist.header.BooklistHeader;
import com.hardbacknutter.nevertoomanybooks.booklist.style.CoverScale;
import com.hardbacknutter.nevertoomanybooks.booklist.style.ScreenLayout;
import com.hardbacknutter.nevertoomanybooks.booklist.style.Style;
import com.hardbacknutter.nevertoomanybooks.booklist.style.StyleDataStore;
import com.hardbacknutter.nevertoomanybooks.booklist.style.TextScale;
import com.hardbacknutter.nevertoomanybooks.database.DBKey;
import com.hardbacknutter.nevertoomanybooks.settings.BaseSettingsFragment;
import com.hardbacknutter.prefslib.BooleanSetting;
import com.hardbacknutter.prefslib.FloatSetting;
import com.hardbacknutter.prefslib.Setting;
import com.hardbacknutter.prefslib.SettingsDataStore;
import com.hardbacknutter.prefslib.SettingsManager;
import com.hardbacknutter.prefslib.SingleChoiceSetting;

/**
 * Base fragment for editing a {@link Style}, or the Style defaults.
 */
public abstract class StyleBaseFragment
        extends BaseSettingsFragment {

    @NonNull
    private final BooleanSetting[] pShowCoversOnDetailsScreen =
            new BooleanSetting[DBKey.NR_OF_BOOK_COVERS];

    StyleViewModel vm;

    private FloatSetting pExpansionLevel;
    private BooleanSetting pShowCovers;
    private SingleChoiceSetting pLayout;

    @Override
    public void onCreate(@Nullable final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        //noinspection DataFlowIssue
        vm = new ViewModelProvider(getActivity()).get(StyleViewModel.class);
    }

    @NonNull
    @Override
    protected SettingsManager.Builder onCreateSettings() {
        final SettingsDataStore store = vm.getStyleDataStore();
        //noinspection DataFlowIssue
        final SettingsManager.Builder factory = new SettingsManager.Builder(getContext(), store);

        factory.text(StyleDataStore.PK_NAME,
                     R.string.lbl_name, null, p -> {
                    p.setIcon(R.drawable.edit_24px);
                });

        factory.header(R.string.pc_bob_list);

        factory.multiChoice(StyleDataStore.PK_LIST_HEADER,
                            R.string.pt_bob_header,
                            R.array.pe_bob_header,
                            R.array.pv_bob_header,
                            null, p -> {
                    p.setIcon(R.drawable.view_headline_24px);
                    p.setValue(BooklistHeader.SHOW_STYLE_NAME, BooklistHeader.SHOW_BOOK_COUNT);
                });

        factory.fragment(StyleDataStore.PK_GROUPS,
                         R.string.pt_bob_groups,
                         com.hardbacknutter.nevertoomanybooks.settings.styles
                                 .StyleGroupsFragment.class.getName(),
                         R.id.content_frame, p -> {
                    p.setIcon(R.drawable.format_list_bulleted_24px);
                    p.setSummaryProvider(c -> vm.getGroupsSummary(c));
                });

        factory.floatRange(StyleDataStore.PK_EXPANSION_LEVEL,
                           R.string.pt_bob_list_state_expansion_level,
                           1, 4, null, p -> {
                    p.setIcon(R.drawable.format_indent_increase_24px);
                    p.setValue(1);
                });

        factory.fragment(StyleDataStore.PSK_LIST_BOOK_LEVEL_SORTING,
                         R.string.lbl_sorting,
                         com.hardbacknutter.nevertoomanybooks.settings.styles
                                 .StyleBooklistBookLevelSortingFragment.class.getName(),
                         R.id.content_frame, p -> {
                    p.setIcon(R.drawable.sort_24px);
                    p.setSummaryProvider(c -> vm.getBookLevelFieldsSortingSummary(c));
                });

        factory.header(R.string.pc_bob_layout);

        factory.singleChoice(StyleDataStore.PK_LAYOUT,
                             R.string.pt_layout,
                             R.array.pe_bob_layout,
                             R.array.pv_bob_layout,
                             this::onChangeLayout, p -> {
                    p.setIcon(R.drawable.aod_24px);
                    p.setSelectedIndex(0);
                });

        factory.fragment(StyleDataStore.PSK_LIST_BOOK_LEVEL_FIELDS,
                         R.string.pt_bob_show_details,
                         com.hardbacknutter.nevertoomanybooks.settings.styles
                                 .StyleBooklistBookLevelFieldsFragment.class.getName(),
                         R.id.content_frame, p -> {
                    p.setIcon(R.drawable.menu_book_24px);
                    p.setSummaryProvider(c -> vm.getBookLevelFieldsVisibilitySummary(c));
                });

        factory.bool(StyleDataStore.PK_SHOW_GROUP_BOOK_COUNT,
                     R.string.pt_bob_show_group_book_count, null, p -> {
                    p.setIcon(R.drawable.functions_24px);
                    p.setChecked(true);
                });

        // Showing thumbnails is really part of 'Extra Book Details',
        // but this is more user-friendly.
        factory.bool(StyleDataStore.PK_LIST_BOOK_SHOW_COVER_0,
                     R.string.pt_bob_cover_show,
                     this::onChangeListBookCover, p -> {
                    p.setIcon(R.drawable.image_24px);
                    p.setChecked(true);
                });

        // Used for both list and grid-mode (key="style.booklist.layout")
        // For simplicity, we always show this option even if the user hides all covers
        // with the key=PSK_LIST_BOOK_SHOW_COVER_0 option.
        factory.singleChoice(StyleDataStore.PK_COVER_CLICK_ACTION,
                             R.string.pt_bob_cover_click,
                             R.array.pe_bob_cover_click,
                             R.array.pv_bob_cover_click, null, p -> {
                    p.setIcon(R.drawable.image_24px);
                    p.setSelectedIndex(0);
                });

        // Shown in grid-mode, hidden in list-mode
        factory.singleChoice(StyleDataStore.PK_COVER_LONG_CLICK_ACTION,
                             R.string.pt_bob_cover_long_click,
                             R.array.pe_bob_cover_long_click,
                             R.array.pv_bob_cover_long_click, null, p -> {
                    p.setIcon(R.drawable.image_24px);
                    p.setSelectedIndex(0);
                });

        // For simplicity, we always show this option even if the user hides all covers
        // with the "pShowCovers" option.
        factory.floatRange(StyleDataStore.PK_COVER_SCALE,
                           R.string.pt_bob_cover_scale,
                           1, 4, null, p -> {
                    p.setIcon(R.drawable.photo_size_select_small_24px);
                    p.setValue(CoverScale.Medium.ordinal());
                    p.setSummaryProvider(c -> CoverScale.byId((int) p.getValue())
                                                        .getLabel(c));
                });

        factory.floatRange(StyleDataStore.PK_TEXT_SCALE,
                           R.string.pt_bob_font_size,
                           0, 4, null, p -> {
                    p.setIcon(R.drawable.format_size_24px);
                    p.setValue(TextScale.Medium.ordinal());
                    p.setSummaryProvider(c -> TextScale.byId((int) p.getValue())
                                                       .getLabel(c));
                });

        // This is the value used for the "android:layout_height" of a GROUP row
        //             true : "?attr/listPreferredItemHeightSmall"
        //             false: "wrap_content"
        factory.bool(StyleDataStore.PK_GROUP_ROW_HEIGHT,
                     R.string.pt_line_spacing,
                     R.string.size_small, R.string.size_large,
                     null, p -> {
                    p.setIcon(R.drawable.format_line_spacing_24px);
                    p.setChecked(true);
                });

        factory.header(R.string.pc_book_detail_screen);

        factory.bool(StyleDataStore.PK_DETAILS_SHOW_COVER[0],
                     R.string.lbl_cover_front,
                     this::onChangeDetailsShowCover0, p -> {
                    p.setIcon(R.drawable.image_24px);
                    p.setChecked(true);
                });
        factory.bool(StyleDataStore.PK_DETAILS_SHOW_COVER[1],
                     R.string.lbl_cover_back,
                     null, p -> {
                    p.setIcon(R.drawable.image_24px);
                    p.setChecked(true);
                });
        factory.bool(StyleDataStore.PK_DETAILS_SHOW_COVER[2],
                     R.string.lbl_image_2,
                     null, p -> {
                    p.setIcon(R.drawable.image_24px);
                    p.setChecked(true);
                });
        factory.bool(StyleDataStore.PK_DETAILS_SHOW_COVER[3],
                     R.string.lbl_image_3,
                     null, p -> {
                    p.setIcon(R.drawable.image_24px);
                    p.setChecked(true);
                });

        factory.header(R.string.pc_reading);

        factory.bool(StyleDataStore.PK_USE_READ_PROGRESS,
                     R.string.lbl_track_progress,
                     R.string.ps_use_read_progress_off,
                     R.string.ps_use_read_progress_on,
                     null, p -> {
                    p.setIcon(R.drawable.menu_book_24px);
                });

        factory.header(R.string.lbl_titles);

        factory.bool(StyleDataStore.PK_SHOW_TITLES_REORDERED,
                     R.string.pc_formatting,
                     R.string.ps_show_titles_reordered_off,
                     R.string.ps_show_titles_reordered_on,
                     null, p -> {
                    p.setIcon(R.drawable.reorder_24px);
                });

        factory.singleChoice(StyleDataStore.PK_CITATION_TYPE,
                             R.string.lbl_citation_type,
                             R.array.lbl_style_citation_type,
                             R.array.pv_style_citation_type,
                             null, p -> {
                    p.setIcon(R.drawable.share_24px);
                    p.setSelectedIndex(0);
                });

        factory.header(StyleDataStore.PSK_STYLE_AUTHOR,
                       R.string.lbl_author);

        factory.bool(StyleDataStore.PK_SHOW_AUTHOR_NAME_GIVEN_FIRST,
                     R.string.pc_formatting,
                     R.string.ps_show_author_name_family_first,
                     R.string.ps_show_author_name_given_first,
                     null, p -> {
                    p.setIcon(R.drawable.reorder_24px);
                });
        factory.bool(StyleDataStore.PK_SORT_AUTHOR_NAME_GIVEN_FIRST,
                     R.string.lbl_sorting,
                     R.string.ps_sort_author_name_family_first,
                     R.string.ps_sort_author_name_given_first,
                     null, p -> {
                    p.setIcon(R.drawable.sort_24px);
                });

        // Enabled if Group 'Author' is present in this style.
        //
        // See {@link BooklistBuilder.TableBuilder#joinWithAuthors}
        // for why this is a MultiSelectListPreference?
        factory.multiChoice(StyleDataStore.PK_GROUPS_AUTHOR_PRIMARY_ROLE,
                            R.string.pt_main_author_type,
                            R.array.pe_author_type,
                            R.array.pv_author_type,
                            null, p -> {
                    p.setIcon(R.drawable.looks_one_24px);
                    // The default is 'not set', which means we take the Author
                    // in position 1 to be the primary.
                });

        // Enabled if Group 'Author' is present in this style.
        factory.bool(Style.UnderEach.Author.getPrefKey(),
                     R.string.pt_bob_books_under_multiple_authors,
                     R.string.ps_bob_books_under_multiple_authors_main_only,
                     R.string.ps_bob_books_under_multiple_authors_each,
                     null, p -> {
                    p.setIcon(R.drawable.functions_24px);
                });

        factory.header(StyleDataStore.PSK_STYLE_SERIES,
                       R.string.lbl_series);

        // Enabled if Group 'Series' is present in this style.
        factory.bool(Style.UnderEach.Series.getPrefKey(),
                     R.string.pt_bob_books_under_multiple_series,
                     R.string.ps_bob_books_under_multiple_series_main_only,
                     R.string.ps_bob_books_under_multiple_series_each,
                     null, p -> {
                    p.setIcon(R.drawable.functions_24px);
                });

        factory.header(StyleDataStore.PSK_STYLE_PUBLISHER,
                       R.string.lbl_publisher);

        // Enabled if Group 'Publisher' is present in this style.
        factory.bool(Style.UnderEach.Publisher.getPrefKey(),
                     R.string.pt_bob_books_under_multiple_publishers,
                     R.string.ps_bob_books_under_multiple_publishers_main_only,
                     R.string.ps_bob_books_under_multiple_publishers_each,
                     null, p -> {
                    p.setIcon(R.drawable.functions_24px);
                });

        // ENHANCE: enable this if/when we introduce the concept of a PRIMARY Bookshelf.
        //  All other plumbing is already implemented.
        // factory.header(StyleDataStore.PSK_STYLE_BOOKSHELF,
        //                R.string.lbl_bookshelf);
        //
        // // Enabled if Group 'Bookshelf' is present in this style.
        // factory.checkable(Style.UnderEach.Bookshelf.getPrefKey(),
        //                   R.string.ps_bob_books_under_multiple_bookshelfs,
        //                   R.string.ps_bob_books_under_multiple_bookshelfs_main_only,
        //                   R.string.ps_bob_books_under_multiple_bookshelfs_each, p -> {
        //             p.setIcon(R.drawable.functions_24px);
        //         });

        return factory;
    }

    @Override
    public void onViewCreated(@NonNull final View view,
                              @Nullable final Bundle savedInstanceState) {
        //noinspection DataFlowIssue
        vm.init(getContext(), EditStyleInput.fromBundle(requireArguments()));
        // init the vm BEFORE calling the super, as onCreateSettings uses it.
        super.onViewCreated(view, savedInstanceState);

        final SettingsManager settingsManager = getSettingsManager();
        pExpansionLevel = settingsManager.requireSetting(StyleDataStore.PK_EXPANSION_LEVEL);
        pShowCovers = settingsManager.requireSetting(StyleDataStore.PK_LIST_BOOK_SHOW_COVER_0);
        pLayout = settingsManager.requireSetting(StyleDataStore.PK_LAYOUT);

        // Book details page
        for (int cIdx = 0; cIdx < DBKey.NR_OF_BOOK_COVERS; cIdx++) {
            pShowCoversOnDetailsScreen[cIdx] = settingsManager
                    .requireSetting(StyleDataStore.PK_DETAILS_SHOW_COVER[cIdx]);
        }

        vm.onNameNotUnique().observe(getViewLifecycleOwner(), this::onNameNotUnique);
    }

    private boolean onChangeDetailsShowCover0(@NonNull final Setting setting,
                                              @Nullable final Object newValue) {
        // Covers on DETAIL screen: Setting cover 0 to false
        final boolean enabled = newValue != null && (boolean) newValue;
        if (!enabled) {
            final SettingsManager settingsManager = getSettingsManager();
            // Set all others to false as well
            for (int cIdx = 1; cIdx < DBKey.NR_OF_BOOK_COVERS; cIdx++) {
                pShowCoversOnDetailsScreen[cIdx].setChecked(false);
                settingsManager.save(pShowCoversOnDetailsScreen[cIdx]);
            }
        }
        return true;
    }

    private boolean onChangeListBookCover(@NonNull final Setting setting,
                                          @Nullable final Object newValue) {
        final SettingsManager settingsManager = getSettingsManager();
        final boolean enabled = newValue != null && (boolean) newValue;
        settingsManager.setEnabled(enabled,
                                   StyleDataStore.PK_COVER_CLICK_ACTION,
                                   StyleDataStore.PK_COVER_SCALE);
        settingsManager.setEnabled(enabled, StyleDataStore.PK_DETAILS_SHOW_COVER);
        return true;
    }

    private boolean onChangeLayout(@NonNull final Setting setting,
                                   @Nullable final Object newValue) {
        final SettingsManager settingsManager = getSettingsManager();
        final int newIndex;
        if (newValue == null) {
            newIndex = 0;
        } else {
            newIndex = Integer.parseInt((String) newValue);
        }
        final ScreenLayout layout = ScreenLayout.byId(newIndex);

        // The whole point of Grid is to show covers
        if (layout == ScreenLayout.Grid) {
            pShowCovers.setChecked(true);
            settingsManager.save(pShowCovers);
        }
        // else ScreenLayout.List: no specific changes, leave it to the user

        updateLayoutVisibility(layout);
        return true;
    }

    @Override
    public void onResume() {
        super.onResume();
        updateLayoutVisibility(ScreenLayout.byId(pLayout.getSelectedIndex()));
        updateGroupSettings();
    }

    /**
     * Use the <strong>current layout setting</strong>.
     *
     * @param layout current value of the setting.
     *
     * @throws IllegalArgumentException (debug) for an unknown layout
     */
    private void updateLayoutVisibility(@NonNull final ScreenLayout layout) {
        final SettingsManager settingsManager = getSettingsManager();
        switch (layout) {
            case List: {
                settingsManager.setVisible(Map.of(
                        StyleDataStore.PK_LIST_BOOK_SHOW_COVER_0, true,
                        StyleDataStore.PK_COVER_LONG_CLICK_ACTION, false));
                break;
            }
            case Grid: {
                settingsManager.setVisible(Map.of(
                        StyleDataStore.PK_LIST_BOOK_SHOW_COVER_0, false,
                        StyleDataStore.PK_COVER_LONG_CLICK_ACTION, true));
                break;
            }
            default:
                throw new IllegalArgumentException(layout.toString());
        }
    }

    /**
     * Show the settings for groups we have and hide for groups we don't or no longer have.
     * When one setting is visible, make the category visible.
     * Adjust the Expansion Leveld field according to the number off groups.
     * Update all affected summaries by notifying the adapter.
     * <p>
     * Read directly from the style as group changes are handled in another Fragment.
     */
    private void updateGroupSettings() {
        final SettingsManager settingsManager = getSettingsManager();
        final Style style = vm.getStyle();

        BooklistGroup.getAllGroups(style).forEach(group -> {
            final GroupSettings groupSettings = group.getGroupSettings();
            if (groupSettings == null) {
                // Not all groups have settings
                return;
            }

            final boolean hasGroup = style.hasGroup(group.getId());
            final String headerKey = groupSettings.getHeaderKey();
            final Map<String, Boolean> updates = new HashMap<>();

            // Individual options visibility
            for (final String key : groupSettings.getKeys()) {
                updates.put(key, hasGroup);
            }

            final boolean headerVisible = hasGroup || groupSettings.getKeys().stream().anyMatch(
                    k -> settingsManager.getVisibleChildren(headerKey).contains(k));

            updates.put(headerKey, headerVisible);
            settingsManager.setVisible(updates);
        });

        // Adjusting/showing the pExpansionLevel is not applicable for the default style.
        // We need to check this explicitly, as the default style
        // will always have ALL groups which have configuration settings.
        if (!vm.isDefaultStyle()) {
            // The 'level expansion' depends on the number of groups in use
            // which could have been changed when returning from the groups fragment.
            final int groupCount = style.getGroupCount();
            if (groupCount > 1) {
                pExpansionLevel.setValueTo(groupCount);
                // Update, but do not save it (no need)
                pExpansionLevel.setValue(style.getExpansionLevel());
                settingsManager.setVisible(Map.of(pExpansionLevel.getKey(), true));
            } else {
                settingsManager.setVisible(Map.of(pExpansionLevel.getKey(), false));
            }
        }

        // Force an update for the summaries of the above touched settings.
        //noinspection DataFlowIssue
        settingsManager.reload(
                getContext(),
                // These represent Fragments; we need to force the summaries to be read
                // from the style after we come back here
                StyleDataStore.PK_GROUPS,
                StyleDataStore.PSK_LIST_BOOK_LEVEL_FIELDS,
                StyleDataStore.PSK_LIST_BOOK_LEVEL_SORTING,
                // We've potentially changed this from code
                StyleDataStore.PK_EXPANSION_LEVEL);
    }

    private void onNameNotUnique(@NonNull final CharSequence message) {
        //noinspection DataFlowIssue
        new MaterialAlertDialogBuilder(getContext())
                .setIcon(R.drawable.warning_24px)
                .setTitle(R.string.dialog_alert_title)
                .setMessage(message)
                .setPositiveButton(R.string.ok, (d, w) -> {
                    d.dismiss();
                    getSettingsManager().performClick(StyleDataStore.PK_NAME);
                })
                .create()
                .show();
    }
}
