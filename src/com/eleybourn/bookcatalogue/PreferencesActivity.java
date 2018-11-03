/*
 * @copyright 2012 Philip Warner
 * @license GNU General Public License
 *
 * This file is part of Book Catalogue.
 *
 * Book Catalogue is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Book Catalogue is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with Book Catalogue.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.eleybourn.bookcatalogue;

import android.os.Bundle;
import android.support.annotation.CallSuper;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;

import com.eleybourn.bookcatalogue.baseactivity.PreferencesBaseActivity;
import com.eleybourn.bookcatalogue.properties.BooleanProperty;
import com.eleybourn.bookcatalogue.properties.IntegerListProperty;
import com.eleybourn.bookcatalogue.properties.ListProperty;
import com.eleybourn.bookcatalogue.properties.ListProperty.ItemEntries;
import com.eleybourn.bookcatalogue.properties.Properties;
import com.eleybourn.bookcatalogue.properties.Property;
import com.eleybourn.bookcatalogue.properties.PropertyGroup;
import com.eleybourn.bookcatalogue.properties.StringListProperty;
import com.eleybourn.bookcatalogue.scanner.ScannerManager;
import com.eleybourn.bookcatalogue.utils.SoundManager;

import java.util.Locale;

import static com.eleybourn.bookcatalogue.BookAbstractFragmentWithCoverImage.PREF_AUTOROTATE_CAMERA_IMAGES_DEFAULT;

/**
 * Activity to display the 'Preferences' dialog and maintain the preferences.
 *
 * @author Philip Warner
 */
public class PreferencesActivity extends PreferencesBaseActivity {

    public static final int REQUEST_CODE = UniqueId.ACTIVITY_REQUEST_CODE_PREFERENCES;

    /** Camera image rotation property values */
    private static final ItemEntries<Integer> mRotationListItems = new ItemEntries<Integer>()
            .add(null, R.string.use_default_setting)
            .add(0, R.string.no)
            .add(90, R.string.menu_rotate_thumb_cw)
            .add(-90, R.string.menu_rotate_thumb_ccw)
            .add(180, R.string.menu_rotate_thumb_180);

    /** List of supported message implementations */
    private static final ItemEntries<Integer> mMessageImplementationItems = new ItemEntries<Integer>()
            .add(0, R.string.user_interface_messages_use_toast)
            .add(1, R.string.user_interface_messages_use_snackbar);

    /** List of supported locales */
    private static final ItemEntries<String> mLocalesListItems = getLocalesListItems();
    /** List of supported themes */
    private static final ItemEntries<Integer> mAppThemeItems = getThemeListItems();

    /** Preferred Scanner property values */
    private static final ItemEntries<Integer> mScannerListItems = new ItemEntries<Integer>()
            .add(null, R.string.use_default_setting)
            .add(ScannerManager.SCANNER_ZXING_COMPATIBLE, R.string.scanning_preferred_scanner_zxing_compatible)
            .add(ScannerManager.SCANNER_ZXING, R.string.scanning_preferred_scanner_zxing)
            .add(ScannerManager.SCANNER_PIC2SHOP, R.string.scanning_preferred_scanner_pic2shop);

    /**
     * Build the complete list of all preferences
     */
    private static final Properties mProperties = new Properties()

            /* *****************************************************************************
             * GRP_USER_INTERFACE:
             ******************************************************************************/
            /*
             * Enabling/disabling read-only mode when opening book. If enabled book
             * is opened in read-only mode (editing through menu), else in edit mode.
             */
            .add(new BooleanProperty(BooksOnBookshelf.PREF_OPEN_BOOK_READ_ONLY,
                    PropertyGroup.GRP_USER_INTERFACE, R.string.user_interface_open_book_read_only)
                    .setDefaultValue(true)
                    .setPreferenceKey(BooksOnBookshelf.PREF_OPEN_BOOK_READ_ONLY)
                    .setGlobal(true))

            .add(new StringListProperty(mLocalesListItems, BookCatalogueApp.PREF_APP_LOCALE,
                    PropertyGroup.GRP_USER_INTERFACE, R.string.user_interface_preferred_language)
                    .setPreferenceKey(BookCatalogueApp.PREF_APP_LOCALE)
                    .setGlobal(true)
                    .setWeight(200)
                    .setGroup(PropertyGroup.GRP_USER_INTERFACE))

            .add(new IntegerListProperty(mAppThemeItems, BookCatalogueApp.PREF_APP_THEME,
                    PropertyGroup.GRP_USER_INTERFACE, R.string.user_interface_theme)
                    .setDefaultValue(BookCatalogueApp.DEFAULT_THEME)
                    .setPreferenceKey(BookCatalogueApp.PREF_APP_THEME)
                    .setGlobal(true)
                    .setWeight(200)
                    .setGroup(PropertyGroup.GRP_USER_INTERFACE))

            .add(new IntegerListProperty(mMessageImplementationItems, BookCatalogueApp.PREF_APP_USER_MESSAGE,
                    PropertyGroup.GRP_USER_INTERFACE, R.string.user_interface_messages_use)
                    .setDefaultValue(0)
                    .setPreferenceKey(BookCatalogueApp.PREF_APP_USER_MESSAGE)
                    .setGlobal(true)
                    .setWeight(200)
                    .setGroup(PropertyGroup.GRP_USER_INTERFACE))

            /* *****************************************************************************
             * GRP_SCANNER:
             ******************************************************************************/

            .add(new BooleanProperty(SoundManager.PREF_BEEP_IF_SCANNED_ISBN_INVALID,
                    PropertyGroup.GRP_SCANNER, R.string.beep_if_scanned_isbn_invalid)
                    .setDefaultValue(true)
                    .setPreferenceKey(SoundManager.PREF_BEEP_IF_SCANNED_ISBN_INVALID)
                    .setGlobal(true)
                    .setWeight(300))

            .add(new BooleanProperty(SoundManager.PREF_BEEP_IF_SCANNED_ISBN_VALID,
                    PropertyGroup.GRP_SCANNER, R.string.scanning_beep_if_scanned_isbn_valid)
                    .setDefaultValue(false)
                    .setPreferenceKey(SoundManager.PREF_BEEP_IF_SCANNED_ISBN_VALID)
                    .setGlobal(true)
                    .setWeight(300))

            .add(new IntegerListProperty(mScannerListItems, ScannerManager.PREF_PREFERRED_SCANNER,
                    PropertyGroup.GRP_SCANNER, R.string.scanning_preferred_scanner)
                    .setDefaultValue(ScannerManager.SCANNER_ZXING_COMPATIBLE)
                    .setPreferenceKey(ScannerManager.PREF_PREFERRED_SCANNER)
                    .setGlobal(true))

            /* *****************************************************************************
             * GRP_THUMBNAILS:
             ******************************************************************************/

            .add(new BooleanProperty(BookAbstractFragmentWithCoverImage.PREF_CROP_FRAME_WHOLE_IMAGE,
                    PropertyGroup.GRP_THUMBNAILS, R.string.thumbnails_default_crop_frame_is_whole_image)
                    .setDefaultValue(false)
                    .setPreferenceKey(BookAbstractFragmentWithCoverImage.PREF_CROP_FRAME_WHOLE_IMAGE)
                    .setGlobal(true))

            .add(new IntegerListProperty(mRotationListItems, BookAbstractFragmentWithCoverImage.PREF_AUTOROTATE_CAMERA_IMAGES,
                    PropertyGroup.GRP_THUMBNAILS, R.string.thumbnails_rotate_auto)
                    .setDefaultValue(PREF_AUTOROTATE_CAMERA_IMAGES_DEFAULT)
                    .setPreferenceKey(BookAbstractFragmentWithCoverImage.PREF_AUTOROTATE_CAMERA_IMAGES)
                    .setGlobal(true))

            .add(new BooleanProperty(BookAbstractFragmentWithCoverImage.PREF_USE_EXTERNAL_IMAGE_CROPPER,
                    PropertyGroup.GRP_THUMBNAILS, R.string.thumbnails_use_external_image_cropper)
                    .setDefaultValue(false)
                    .setPreferenceKey(BookAbstractFragmentWithCoverImage.PREF_USE_EXTERNAL_IMAGE_CROPPER)
                    .setGlobal(true));

    /**
     * Listener for Locale changes; update list and maybe reload
     */
    private final BookCatalogueApp.OnLocaleChangedListener mLocaleListener = new BookCatalogueApp.OnLocaleChangedListener() {
        @Override
        public void onLocaleChanged() {
            updateLocalesListItems();
            updateLocaleIfChanged();
            restartActivityIfNeeded();
        }
    };

    /**
     * Format the list of locales (languages)
     *
     * @return List of preference items
     */
    @NonNull
    private static ItemEntries<String> getLocalesListItems() {
        ItemEntries<String> items = new ItemEntries<>();

        Locale locale = BookCatalogueApp.getSystemLocal();
        items.add("", R.string.preferred_language_x, BookCatalogueApp.getResourceString(R.string.system_locale), locale.getDisplayLanguage());

        for (String loc : BookCatalogueApp.getSupportedLocales()) {
            locale = BookCatalogueApp.localeFromName(loc);
            items.add(loc, R.string.preferred_language_x, locale.getDisplayLanguage(locale), locale.getDisplayLanguage());
        }
        return items;
    }

    /**
     * Format the list of themes
     *
     * @return List of preference themes
     */
    @NonNull
    private static ItemEntries<Integer> getThemeListItems() {
        ItemEntries<Integer> items = new ItemEntries<>();

        String[] themeList = BookCatalogueApp.getResourceStringArray(R.array.user_interface_theme_supported);
        for (int i = 0; i < themeList.length; i++) {
            items.add(i, R.string.single_string, themeList[i]);
        }
        return items;
    }

    @Override
    protected int getLayoutId() {
        return R.layout.activity_preferences;
    }

    @Override
    @CallSuper
    public void onCreate(final @Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // Make sure the names are correct
        updateLocalesListItems();

        setTitle(R.string.preferences);
    }

    @Override
    @CallSuper
    public void onPause() {
        // Don't bother listening since we check for locale changes in onResume of super class
        BookCatalogueApp.unregisterOnLocaleChangedListener(mLocaleListener);
        super.onPause();
    }

    @Override
    @CallSuper
    public void onResume() {
        super.onResume();
        // Listen for locale changes (this activity CAN change it)
        BookCatalogueApp.registerOnLocaleChangedListener(mLocaleListener);
    }

    /**
     * Display current preferences and set handlers to catch changes.
     */
    @Override
    protected void initViews(@NonNull Properties globalProps) {
        // Add the locally constructed properties
        for (Property p : mProperties)
            globalProps.add(p);
    }

    /**
     * Utility routine to adjust the strings used in displaying a language list.
     */
    private void updateLocalesListItems() {
        String name;
        String lang;
        for (ListProperty.ItemEntry<String> item : mLocalesListItems) {
            String loc = item.getValue();
            if (loc == null || loc.isEmpty()) {
                name = getString(R.string.system_locale);
                lang = BookCatalogueApp.getSystemLocal().getDisplayLanguage();
            } else {
                Locale locale = BookCatalogueApp.localeFromName(loc);
                name = locale.getDisplayLanguage(locale);
                lang = locale.getDisplayLanguage();
            }
            item.setString(R.string.preferred_language_x, name, lang);
        }
    }
}
