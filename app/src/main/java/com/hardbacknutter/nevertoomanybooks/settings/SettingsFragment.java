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

import android.content.Context;
import android.os.Build;
import android.os.Bundle;
import android.os.storage.StorageVolume;
import android.provider.Settings;
import android.text.Spannable;
import android.text.SpannableString;
import android.text.style.ForegroundColorSpan;
import android.view.View;

import androidx.activity.OnBackPressedCallback;
import androidx.activity.result.ActivityResultLauncher;
import androidx.annotation.ColorInt;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.widget.Toolbar;
import androidx.lifecycle.LifecycleOwner;
import androidx.lifecycle.ViewModelProvider;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.snackbar.Snackbar;

import java.io.IOException;
import java.util.List;
import java.util.Locale;

import com.hardbacknutter.nevertoomanybooks.R;
import com.hardbacknutter.nevertoomanybooks.ServiceLocator;
import com.hardbacknutter.nevertoomanybooks.StartupViewModel;
import com.hardbacknutter.nevertoomanybooks.booklist.RebuildBooklist;
import com.hardbacknutter.nevertoomanybooks.booklist.style.FieldVisibility;
import com.hardbacknutter.nevertoomanybooks.core.storage.StorageException;
import com.hardbacknutter.nevertoomanybooks.core.tasks.TaskProgress;
import com.hardbacknutter.nevertoomanybooks.core.utils.AttrUtils;
import com.hardbacknutter.nevertoomanybooks.covers.CoverVolume;
import com.hardbacknutter.nevertoomanybooks.database.DBKey;
import com.hardbacknutter.nevertoomanybooks.dialogs.ErrorDialog;
import com.hardbacknutter.nevertoomanybooks.entities.codes.ProductCodeValidity;
import com.hardbacknutter.nevertoomanybooks.fields.EditTextField;
import com.hardbacknutter.nevertoomanybooks.network.NetworkCheckerImpl;
import com.hardbacknutter.nevertoomanybooks.settings.identifiers.IdentifiersEditorContract;
import com.hardbacknutter.nevertoomanybooks.settings.searchsites.SearchSitesAllListsContract;
import com.hardbacknutter.nevertoomanybooks.settings.styles.EditStyleInput;
import com.hardbacknutter.nevertoomanybooks.settings.tags.TagAdminContract;
import com.hardbacknutter.nevertoomanybooks.sync.calibre.CalibreHandler;
import com.hardbacknutter.nevertoomanybooks.tasks.ProgressDelegate;
import com.hardbacknutter.nevertoomanybooks.tasks.StorageMoverTask;
import com.hardbacknutter.nevertoomanybooks.utils.ReorderHelper;
import com.hardbacknutter.nevertoomanybooks.utils.mappers.ColorMapper;
import com.hardbacknutter.nevertoomanybooks.utils.mappers.FormatMapper;
import com.hardbacknutter.nevertoomanybooks.widgets.popupmenu.ExtMenuButton;
import com.hardbacknutter.prefslib.BooleanSetting;
import com.hardbacknutter.prefslib.Setting;
import com.hardbacknutter.prefslib.SettingsDataStore;
import com.hardbacknutter.prefslib.SettingsManager;
import com.hardbacknutter.prefslib.SharedPreferencesDataStore;
import com.hardbacknutter.prefslib.SingleChoiceSetting;
import com.hardbacknutter.util.livedataevent.LiveDataEvent;

/**
 * Global settings page.
 * <p>
 * TODO: add per-app locale:
 *  https://developer.android.com/guide/topics/resources/app-languages
 *  {@link Settings#ACTION_APP_LOCALE_SETTINGS}
 * TODO: add link: {@link Settings#ACTION_APPLICATION_SETTINGS}
 *  to allow access to storage etc...
 */
public class SettingsFragment
        extends BaseSettingsFragment {

    /** Fragment/Log tag. */
    private static final String TAG = "SettingsFragment";

    private static final String PSK_ADVANCED_OPTIONS = "psk_advanced_options";
    private static final String PSK_BARCODE = "psk_barcode";
    private static final String PSK_CALIBRE = "psk_calibre";
    private static final String PSK_FIELDS_VISIBILITY = "psk_fields_visibility";
    private static final String PSK_IDENTIFIERS = "psk_identifiers";
    private static final String PSK_SEARCH_SITE_ORDER = "psk_search_site_order";
    private static final String PSK_STYLE_DEFAULTS = "psk_style_defaults";
    private static final String PSK_TAGS = "psk_tags";
    private static final String PSK_THUMBNAILS = "psk_thumbnails";
    private static final String PSK_USER_INTERFACE = "psk_user_interface";

    private final ActivityResultLauncher<Void> editSitesLauncher =
            registerForActivityResult(new SearchSitesAllListsContract(),
                                      success -> { /* ignore */ });

    private SettingsViewModel vm;

    private final ActivityResultLauncher<Void> manageTagsLauncher =
            registerForActivityResult(new TagAdminContract(), o -> o.ifPresent(
                    settingsOutput -> {
                        if (settingsOutput.isForceRebuildBooklist()) {
                            vm.setForceRebuildBooklist();
                        }
                    }));

    private final ActivityResultLauncher<Void> manageIdentitiesLauncher =
            registerForActivityResult(new IdentifiersEditorContract(), o -> o.ifPresent(
                    settingsOutput -> {
                        if (settingsOutput.isForceRebuildBooklist()) {
                            vm.setForceRebuildBooklist();
                        }
                    }));

    /** Set the hosting Activity result, and close it. */
    private final OnBackPressedCallback backPressedCallback =
            new OnBackPressedCallback(true) {
                @Override
                public void handleOnBackPressed() {
                    //noinspection DataFlowIssue
                    new SettingsOutput(vm.isForceActivityRecreation(), vm.isForceRebuildBooklist())
                            .finishActivityAndSend(getActivity());
                }
            };
    private StorageVolumeHelper storageVolumeHelper;

    @NonNull
    @Override
    protected SettingsManager.Builder onCreateSettings() {
        final SettingsDataStore store = new SharedPreferencesDataStore(
                ServiceLocator.getInstance().getSharedPreferences());
        //noinspection DataFlowIssue
        final SettingsManager.Builder factory = new SettingsManager.Builder(getContext(), store);

        factory.header(R.string.pc_ui);
        factory.fragment(PSK_USER_INTERFACE,
                         R.string.pc_ui,
                         com.hardbacknutter.nevertoomanybooks.settings
                                 .UserInterfacePreferenceFragment.class.getName(),
                         R.id.content_frame, p -> {
                    p.setIcon(R.drawable.display_settings_24px);
                    final Context context = getContext();
                    //noinspection DataFlowIssue
                    p.setSummary(String.join(", ", List.of(
                            context.getString(R.string.pt_ui_language),
                            context.getString(R.string.pt_ui_theme),
                            context.getString(R.string.pt_ui_theme_colors),
                            // don't list more, keep it clean
                            "…")));
                });

        factory.header(R.string.pc_search);

        factory.action(PSK_SEARCH_SITE_ORDER,
                       R.string.lbl_websites,
                       this::onSetSearchSiteOrder, p -> {
                    p.setIcon(R.drawable.search_24px);
                    p.setSummary(R.string.pt_website_search_order_and_enable);
                }
        );

        factory.bool(NetworkCheckerImpl.PK_NETWORK_ALLOW_METERED,
                     R.string.pt_allow_metered_network_data,  null, p -> {
                    p.setIcon(R.drawable.data_usage_24px);
                    p.setChecked(true);
                });

        factory.bool(FormatMapper.PK_SEARCH_REFORMAT_FORMAT,
                     R.string.pt_search_reformat_format,  null, p -> {
                    p.setIcon(R.drawable.merge_24px);
                    p.setChecked(true);
                });

        factory.bool(ColorMapper.PK_SEARCH_REFORMAT_COLOR,
                     R.string.pt_search_reformat_color, null, p -> {
                    p.setIcon(R.drawable.merge_24px);
                    p.setChecked(true);
                });

        factory.header(R.string.lbl_sorting);

        // This is the global setting used for
        // - book, toc and series titles.
        // - publisher name.
        //
        // It decides how the OB columns are populated.
        // If the user changes this setting, a rebuild of all OB columns is needed!
        // ENHANCE: create TWO OB columns for each title column to avoid rebuilds
        // ENHANCE: add a style override setting
        // ENHANCE: split this into 2: book/series,toc titles + publisher name
        factory.bool(ReorderHelper.PK_SORT_TITLE_REORDERED,
                     R.string.ps_show_titles_reordered_on,
                     this::onChangeSortTitleReordered, p -> {
                    p.setIcon(R.drawable.sort_24px);
                    p.setSummaryProvider(c -> getTitleOrderSummary(p));
                    p.setChecked(true);
                });

        factory.bool(ReorderHelper.PK_DEDUP_TRY_REORDERED,
                     R.string.pt_deduplication_title_matching,
                     R.string.pt_deduplication_title_matching_summary_off,
                     R.string.pt_deduplication_title_matching_summary_on,
                     null, p -> {
                    p.setIcon(R.drawable.equal_24px);
                });

        factory.header(R.string.lbl_style);

        factory.fragment(PSK_STYLE_DEFAULTS,
                         R.string.action_edit_defaults,
                         com.hardbacknutter.nevertoomanybooks.settings.styles
                                 .StyleDefaultsFragment.class.getName(),
                         R.id.content_frame, p -> {
                    p.setIcon(R.drawable.style_24px);
                    p.setArgumentSupplier(() -> EditStyleInput.editDefaults().toBundle());
                });

        factory.header(R.string.pc_edit);

        factory.fragment(PSK_THUMBNAILS,
                         R.string.lbl_images,
                         com.hardbacknutter.nevertoomanybooks.settings
                                 .ImagesPreferenceFragment.class.getName(),
                         R.id.content_frame, p -> {
                    p.setIcon(R.drawable.image_24px);
                });

        factory.singleChoice(EditTextField.Capitalization.Title.getPrefKey(),
                             R.string.pt_capitalize_title,
                             R.array.pe_edit_capitalize,
                             R.array.pv_edit_capitalize, null, p -> {
                    p.setIcon(R.drawable.titlecase_24px);
                    // default 1: 'title': capitalize all words.
                    p.setSelectedIndex(1);
                });
        factory.singleChoice(ProductCodeValidity.PK_EDIT_BOOK_PRODUCT_CODE_CHECKS,
                             R.string.pt_edit_book_isbn_checks,
                             R.array.pe_edit_book_isbn_checks,
                             R.array.pv_edit_book_isbn_checks, null, p -> {
                    p.setIcon(R.drawable.check_24px);
                    // default 1: ProductCodeValidity.ValidCodes
                    p.setSelectedIndex(1);
                });

        factory.fragment(PSK_BARCODE,
                         R.string.pt_barcode_scanner,
                         com.hardbacknutter.nevertoomanybooks.settings
                                 .BarcodePreferenceFragment.class.getName(),
                         R.id.content_frame, p -> {
                    p.setIcon(R.drawable.barcode_scanner_24px);
                });

        factory.header(R.string.pc_lists);

        factory.singleChoice(RebuildBooklist.PK_BOOKLIST_REBUILD_STATE,
                             R.string.pt_bob_list_rebuild_state,
                             R.array.pe_bob_list_state,
                             R.array.pv_bob_list_state, null, p -> {
                    p.setIcon(R.drawable.view_list_24px);
                    // Default: Booklist.PREF_REBUILD_SAVED_STATE == 0
                    p.setSelectedIndex(0);
                });
        factory.singleChoice(ExtMenuButton.PK_MODE,
                             R.string.pt_bob_row_menu,
                             R.array.pe_bob_row_menu,
                             R.array.pv_bob_row_menu, null, p -> {
                    p.setIcon(R.drawable.view_list_24px);
                    // Default: ExtMenuButton.Always == 0
                    p.setSelectedIndex(0);
                });
        factory.singleChoice(FastScrollerMode.PK_OVERLAY,
                             R.string.pt_fast_scroller_overlay,
                             R.array.pe_fastscroller_overlay,
                             R.array.pv_fastscroller_overlay,
                             this::onChangeScrollerOverlay, p -> {
                    p.setIcon(R.drawable.chat_24px);
                    // Default: OverlayProviderFactory.TYPE_MD2 == 3
                    p.setSelectedIndex(0);
                });

        factory.header(R.string.pc_advanced_options);

        factory.action(PSK_TAGS,
                       R.string.lbl_tags,
                       this::onEditTags, p -> {
                    p.setIcon(R.drawable.tag_24px);
                }
        );

        factory.action(PSK_IDENTIFIERS,
                       R.string.lbl_identifiers,
                       this::onEditIdentifiers, p -> {
                    p.setIcon(R.drawable.label_24px);
                }
        );

        factory.fragment(PSK_FIELDS_VISIBILITY,
                         R.string.pt_field_visibility,
                         com.hardbacknutter.nevertoomanybooks.settings
                                 .FieldVisibilityPreferenceFragment.class.getName(),
                         R.id.content_frame, p -> {
                    p.setIcon(R.drawable.visibility_24px);
                });

        factory.bool(FieldVisibility.PK_LOANS,
                     R.string.pt_lending_enabled,
                     R.string.disabled, R.string.enabled,
                     this::onChangeEnableLending, p -> {
                    p.setIcon(R.drawable.group_24px);
                    p.setChecked(true);
                });
        factory.bool(IdentifiersEditorContract.PK_EDIT_BOOK_TABS_EXTERNAL_ID,
                     R.string.pt_allow_editing_external_id,
                     R.string.disabled, R.string.enabled,
                     null, p -> {
                    p.setIcon(R.drawable.tab_24px);
                });

        factory.fragment(PSK_CALIBRE,
                         R.string.pt_calibre_content_server,
                         com.hardbacknutter.nevertoomanybooks.sync.calibre
                                 .CalibrePreferencesFragment.class.getName(),
                         R.id.content_frame, p -> {
                    p.setIcon(R.drawable.cloud_24px);
                    p.setSummaryProvider(c -> CalibreHandler.isSyncEnabled()
                                              ? c.getString(R.string.enabled)
                                              : c.getString(R.string.disabled));
                });

        factory.singleChoice(CoverVolume.PK_VOLUME_INDEX,
                             R.string.pt_storage_volume,
                             this::onChangeVolumeIndex, p -> {
                    p.setIcon(R.drawable.folder_24px);

                    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) {
                        p.setSummary(getString(R.string.warning_requires_android_x, 9));
                    } else {
                        final Context context = getContext();
                        //noinspection DataFlowIssue
                        final List<StorageVolume> storageVolumes =
                                CoverVolume.getAvailable(context);

                        final int size = storageVolumes.size();
                        final CharSequence[] labels = new CharSequence[size];
                        final CharSequence[] values = new CharSequence[size];

                        for (int i = 0; i < size; i++) {
                            final StorageVolume sv = storageVolumes.get(i);
                            labels[i] = sv.getDescription(context);
                            values[i] = String.valueOf(i);
                        }

                        p.setEntries(labels);
                        p.setEntryValues(values);
                        // Default: 0 == internal 'shared' storage.
                        p.setSelectedIndex(0);
                    }
                });

        factory.fragment(PSK_ADVANCED_OPTIONS,
                         R.string.pt_maintenance,
                         com.hardbacknutter.nevertoomanybooks.settings
                                 .MaintenanceFragment.class.getName(),
                         R.id.content_frame, p -> {
                    p.setIcon(R.drawable.settings_24px);
                });

        return factory;
    }

    @Override
    public void onViewCreated(@NonNull final View view,
                              @Nullable final Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        //noinspection DataFlowIssue
        getActivity().getOnBackPressedDispatcher()
                     .addCallback(getViewLifecycleOwner(), backPressedCallback);

        final Toolbar toolbar = getToolbar();
        toolbar.setTitle(R.string.lbl_settings);
        toolbar.setSubtitle("");

        vm = new ViewModelProvider(getActivity()).get(SettingsViewModel.class);
        //noinspection DataFlowIssue
        vm.init(getContext(), getArguments());

        final SettingsManager settingsManager = getSettingsManager();
        final SingleChoiceSetting pStorageVolume = settingsManager
                .requireSetting(CoverVolume.PK_VOLUME_INDEX);
        // On Android 9+, the Context#getExternalFilesDirs method will return
        // both internal and sdcard directories.
        // Android 8.x it "depends" ... as this is quite old now,
        // we simply do not support 8.x for moving the cover storage.
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) {
            settingsManager.setEnabled(false, CoverVolume.PK_VOLUME_INDEX);
        } else {
            storageVolumeHelper = new StorageVolumeHelper(getContext(), pStorageVolume);
            storageVolumeHelper.initObservers(vm, getViewLifecycleOwner());
        }
    }

    private boolean onEditIdentifiers(@NonNull final Setting setting) {
        manageIdentitiesLauncher.launch(null);
        return true;
    }

    private boolean onEditTags(@NonNull final Setting setting) {
        manageTagsLauncher.launch(null);
        return true;
    }

    private boolean onSetSearchSiteOrder(@NonNull final Setting setting) {
        editSitesLauncher.launch(null);
        return true;
    }

    private boolean onChangeScrollerOverlay(@NonNull final Setting setting,
                                            @Nullable final Object newValue) {
        vm.setForceActivityRecreation();
        return true;
    }

    private boolean onChangeVolumeIndex(@NonNull final Setting setting,
                                        @Nullable final Object newValue) {
        final int volumeIndex = newValue != null ? (int) newValue : 0;
        return storageVolumeHelper.onStorageVolumeChange(volumeIndex);
    }

    private boolean onChangeSortTitleReordered(@NonNull final Setting setting,
                                               @Nullable final Object newValue) {
        final Context context = getContext();
        //noinspection DataFlowIssue
        new MaterialAlertDialogBuilder(context)
                .setIcon(R.drawable.warning_24px)
                .setMessage(R.string.confirm_rebuild_orderby_columns)
                // this dialog is important. Make sure the user pays some attention
                .setCancelable(false)
                // Cancel always restores the original situation.
                .setNegativeButton(R.string.cancel, (d, w)
                        -> restoreSortTitleReordered(context, setting))
                .setPositiveButton(R.string.ok, (d, w) -> {
                    final boolean checked = newValue != null && (boolean) newValue;
                    if (checked == vm.getStoredTitleOrderBy()) {
                        // we're (back) on the original value, i.e. no changes
                        // We'd get here if the user changes their mind
                        // and clicked OK again.
                        restoreSortTitleReordered(context, setting);
                    } else {
                        // Schedule the rebuild
                        StartupViewModel.schedule(context,
                                                  StartupViewModel.PK_REBUILD_TITLE_OB,
                                                  true);
                        // Update/store the new value

                        ((BooleanSetting) setting).setChecked(checked);
                        getSettingsManager().save(setting);
                    }
                })
                .create()
                .show();

        // Never let the system update the preference value,
        // it's handled in above Dialog.
        return false;
    }

    private void restoreSortTitleReordered(@NonNull final Context context,
                                           @NonNull final Setting setting) {
        // Remove any scheduling
        StartupViewModel.schedule(context, StartupViewModel.PK_REBUILD_TITLE_OB, false);
        // revert/store the original value
        ((BooleanSetting) setting).setChecked(vm.getStoredTitleOrderBy());
        getSettingsManager().save(setting);
    }

    private boolean onChangeEnableLending(@NonNull final Setting setting,
                                          @Nullable final Object newValue) {
        final boolean visible = newValue != null && (boolean) newValue;
        // Copy the legacy format to the bitfield.
        final FieldVisibility fieldVisibility =
                ServiceLocator.getInstance().getGlobalFieldVisibility();
        fieldVisibility.setVisible(DBKey.LOANEE_NAME, visible);
        fieldVisibility.save();
        // The legacy flag is stored as normal
        return true;
    }

    @NonNull
    private CharSequence getTitleOrderSummary(@NonNull final BooleanSetting p) {
        final Context context = getContext();
        @SuppressWarnings("DataFlowIssue")
        String summary = p.isChecked()
                         ? context.getString(R.string.ps_show_titles_reordered_on)
                         : context.getString(R.string.ps_show_titles_reordered_off);


        final Spannable spannable;
        // Use the 'schedulerKey' to get the condition!
        if (ServiceLocator.getInstance().getSharedPreferences()
                          .getBoolean(StartupViewModel.PK_REBUILD_TITLE_OB, false)) {
            // don't use android.R.attr.colorError which is API 29+ only
            @ColorInt
            final int color = AttrUtils.getColorInt(context, androidx.appcompat.R.attr.colorError);
            final int warningStart = summary.length() + 1;
            // Add the warning
            final Locale locale = context.getResources().getConfiguration()
                                         .getLocales().get(0);
            summary += '\n' + context.getString(R.string.warning_restart_required)
                                     .toUpperCase(locale);
            spannable = new SpannableString(summary);
            spannable.setSpan(new ForegroundColorSpan(color),
                              warningStart, summary.length(), 0);
        } else {
            @ColorInt
            final int color = AttrUtils.getColorInt(context, android.R.attr.textColorPrimary);
            spannable = new SpannableString(summary);
            spannable.setSpan(new ForegroundColorSpan(color), 0, summary.length(), 0);
        }

        return spannable;
    }

    /**
     * Encapsulates all the code to handle the
     * {@link CoverVolume#PK_VOLUME_INDEX} preference.
     */
    private class StorageVolumeHelper {
        private static final int OPTION_USE = 0;
        private static final int OPTION_MOVE = 1;

        @NonNull
        private final Context context;
        @NonNull
        private final SingleChoiceSetting storageVolumePref;
        @Nullable
        private ProgressDelegate progressDelegate;
        private int volumeChangedOptionChosen;

        StorageVolumeHelper(@NonNull final Context context,
                            @NonNull final SingleChoiceSetting preference) {
            this.context = context;
            this.storageVolumePref = preference;
        }

        void initObservers(@NonNull final SettingsViewModel vm,
                           @NonNull final LifecycleOwner lifecycleOwner) {
            vm.onProgress().observe(lifecycleOwner, this::onProgress);
            vm.onMoveCancelled().observe(lifecycleOwner, this::onMoveCancelled);
            vm.onMoveFailure().observe(lifecycleOwner, this::onMoveFailure);
            vm.onMoveFinished().observe(lifecycleOwner, this::onMoveFinished);
        }

        boolean onStorageVolumeChange(final int newVolumeIndex) {
            //noinspection DataFlowIssue
            final CharSequence newVolumeDesc = storageVolumePref.getEntries()[newVolumeIndex];

            if (vm.isMissingStorageVolume()) {
                // The originally used volume is not available; there is nothing to move.
                // Handle this as a simple 'select'
                new MaterialAlertDialogBuilder(context)
                        .setIcon(R.drawable.warning_24px)
                        .setTitle(R.string.lbl_storage_settings)
                        // this dialog is important. Make sure the user pays some attention
                        .setCancelable(false)
                        .setMessage(context.getString(R.string.option_storage_select,
                                                      newVolumeDesc))
                        .setNegativeButton(R.string.cancel, (d, w) -> d.dismiss())
                        .setPositiveButton(R.string.ok, (d, w) ->
                                setStorageVolume(newVolumeIndex))
                        .create()
                        .show();
            } else {
                final int oldVolumeIndex = vm.getStoredVolumeIndex();
                final CharSequence oldVolumeDesc = storageVolumePref.getEntries()[oldVolumeIndex];

                final CharSequence[] items = {
                        // OPTION_USE
                        context.getString(R.string.option_storage_select, newVolumeDesc),
                        // OPTION_MOVE
                        context.getString(R.string.option_moving_covers_from_x_to_y,
                                          oldVolumeDesc, newVolumeDesc)};
                // default to option_moving_covers_from_x_to_y
                volumeChangedOptionChosen = OPTION_MOVE;

                new MaterialAlertDialogBuilder(context)
                        .setIcon(R.drawable.warning_24px)
                        .setTitle(R.string.lbl_storage_settings)
                        // this dialog is important. Make sure the user pays some attention
                        .setCancelable(false)
                        .setSingleChoiceItems(items, volumeChangedOptionChosen,
                                              (d, w) -> volumeChangedOptionChosen = w)
                        .setNegativeButton(R.string.cancel, (d, w) -> d.dismiss())
                        .setPositiveButton(R.string.ok, (d, w) ->
                                onVolumeChangedOptionChosen(oldVolumeIndex, newVolumeIndex))
                        .create()
                        .show();
            }

            // Do not let the system update the preference value.
            return false;
        }

        private void onVolumeChangedOptionChosen(final int oldVolumeIndex,
                                                 final int newVolumeIndex) {
            switch (volumeChangedOptionChosen) {
                case OPTION_USE: {
                    setStorageVolume(newVolumeIndex);
                    break;
                }
                case OPTION_MOVE: {
                    try {
                        vm.moveData(context, oldVolumeIndex, newVolumeIndex);
                    } catch (@NonNull final IOException e) {
                        ErrorDialog.show(context, TAG, e);
                    }
                    break;
                }
                default:
                    throw new IllegalStateException(String.valueOf(volumeChangedOptionChosen));
            }
        }

        /**
         * Update the screen and sets the actual preference value,
         * i.o.w. the value for {@link CoverVolume#PK_VOLUME_INDEX},
         * and init the new volume/directory.
         *
         * @param volume index to store/use
         *
         * @return {@code true} on success
         */
        private boolean setStorageVolume(final int volume) {
            storageVolumePref.setValue(String.valueOf(volume));
            getSettingsManager().save(storageVolumePref);
            try {
                // Init the newly configured volume
                ServiceLocator.getInstance().getCoverStorage().initDir();
                vm.setForceActivityRecreation();
                return true;

            } catch (@NonNull final StorageException e) {
                // This should never happen... flw
                // To get here the user would have to have displayed the dialog,
                // manually removed the SDCARD
                // and then choose the removed SDCARD from the dialog.
                ErrorDialog.show(context, TAG, e);
                return false;
            }
        }

        private void onProgress(@NonNull final LiveDataEvent<TaskProgress> message) {
            message.process(progress -> {
                if (progressDelegate == null) {
                    progressDelegate = new ProgressDelegate(getProgressFrame())
                            .setTitle(R.string.lbl_moving_data)
                            .setPreventSleep(true)
                            .setIndeterminate(true)
                            .setOnCancelListener(v -> vm.cancelTask(progress.taskId))
                            .show();
                }
                progressDelegate.onProgress(progress);
            });
        }

        private void closeProgressDialog() {
            if (progressDelegate != null) {
                progressDelegate.dismiss();
                progressDelegate = null;
            }
        }

        private void onMoveFinished(@NonNull final LiveDataEvent<Integer> message) {
            closeProgressDialog();

            message.process(volume -> {
                if (setStorageVolume(volume)) {
                    //noinspection DataFlowIssue
                    Snackbar.make(getView(), R.string.action_done, Snackbar.LENGTH_LONG).show();
                }
            });
        }

        private void onMoveFailure(@NonNull final LiveDataEvent<Throwable> message) {
            closeProgressDialog();

            message.process(e -> {
                //noinspection DataFlowIssue
                ErrorDialog.show(getContext(), TAG, e,
                                 getString(R.string.lbl_moving_data),
                                 getString(R.string.error_storage_not_accessible));
            });
        }

        private void onMoveCancelled(@NonNull final LiveDataEvent<Integer> message) {
            closeProgressDialog();

            message.process(volumeOrCancelCode -> {
                if (volumeOrCancelCode == StorageMoverTask.CANCELLED_NO_SPACE_ON_DISK) {
                    new MaterialAlertDialogBuilder(context)
                            .setIcon(R.drawable.warning_24px)
                            .setTitle(R.string.lbl_storage_settings)
                            .setMessage(R.string.error_storage_not_writable)
                            .setPositiveButton(R.string.ok, (d, w) -> d.dismiss())
                            .create()
                            .show();
                    return;
                }

                // FIXME: need better msg + tell user to clean up the destination
                showMessageAndFinishActivity(getString(R.string.cancelled));
            });
        }
    }
}
