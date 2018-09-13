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

import com.eleybourn.bookcatalogue.baseactivity.PreferencesBaseActivity;
import com.eleybourn.bookcatalogue.booklist.BooklistBuilder;
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

/**
 * Activity to display the 'Preferences' dialog and maintain the preferences.
 *
 * @author Philip Warner
 */
public class PreferencesActivity extends PreferencesBaseActivity {

    /** Camera image rotation property values */
    private static final ItemEntries<Integer> mRotationListItems = new ItemEntries<Integer>()
            .add(null, R.string.use_default_setting)
            .add(0, R.string.no)
            .add(90, R.string.menu_rotate_thumb_cw)
            .add(-90, R.string.menu_rotate_thumb_ccw)
            .add(180, R.string.menu_rotate_thumb_180);
    /** List of supported locales */
    private static final ItemEntries<String> mLocalesListItems = getLocalesListItems();
    /** List of supported themes */
    private static final ItemEntries<Integer> mAppThemeItems = getThemeListItems();
    /** Booklist Compatibility mode property values */
    private static final ItemEntries<Integer> mListGenerationOptionsListItems = new ItemEntries<Integer>()
            .add(null, R.string.use_default_setting)
            .add(BooklistBuilder.BOOKLIST_GENERATE_OLD_STYLE, R.string.force_compatibility_mode)
            .add(BooklistBuilder.BOOKLIST_GENERATE_FLAT_TRIGGER, R.string.force_enhanced_compatibility_mode)
            .add(BooklistBuilder.BOOKLIST_GENERATE_NESTED_TRIGGER, R.string.force_fully_featured)
            .add(BooklistBuilder.BOOKLIST_GENERATE_AUTOMATIC, R.string.automatically_use_recommended_option);

    /** Preferred Scanner property values */
    private static final ItemEntries<Integer> mScannerListItems = new ItemEntries<Integer>()
            .add(null, R.string.use_default_setting)
            .add(ScannerManager.SCANNER_ZXING_COMPATIBLE, R.string.zxing_compatible_scanner)
            .add(ScannerManager.SCANNER_ZXING, R.string.zxing_scanner)
            .add(ScannerManager.SCANNER_PIC2SHOP, R.string.pic2shop_scanner);

    /**
     * Build the complete list of all preferences
     */
    private static final Properties mProperties = new Properties()

            /* *****************************************************************************
             * GRP_USER_INTERFACE:
             ******************************************************************************/
            .add(new BooleanProperty(BCPreferences.PREF_CLASSIC_MY_BOOKS, PropertyGroup.GRP_USER_INTERFACE, R.string.include_classic_catalogue_view)
                    .setDefaultValue(false)
                    .setPreferenceKey(BCPreferences.PREF_CLASSIC_MY_BOOKS)
                    .setGlobal(true)
                    .setWeight(100))

            /*
             * Enabling/disabling read-only mode when opening book. If enabled book
             * is opened in read-only mode (editing through menu), else in edit mode.
             */
            .add(new BooleanProperty(BCPreferences.PREF_OPEN_BOOK_READ_ONLY,
                    PropertyGroup.GRP_USER_INTERFACE, R.string.prefs_global_opening_book_mode)
                    .setDefaultValue(true)
                    .setPreferenceKey(BCPreferences.PREF_OPEN_BOOK_READ_ONLY)
                    .setGlobal(true))

            .add(new StringListProperty(mLocalesListItems, BCPreferences.PREF_APP_LOCALE,
                    PropertyGroup.GRP_USER_INTERFACE, R.string.preferred_interface_language)
                    .setPreferenceKey(BCPreferences.PREF_APP_LOCALE)
                    .setGlobal(true)
                    .setWeight(200)
                    .setGroup(PropertyGroup.GRP_USER_INTERFACE))

            .add(new IntegerListProperty(mAppThemeItems, BCPreferences.PREF_APP_THEME,
                    PropertyGroup.GRP_USER_INTERFACE, R.string.preferred_theme)
                    .setDefaultValue(BookCatalogueApp.DEFAULT_THEME)
                    .setPreferenceKey(BCPreferences.PREF_APP_THEME)
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
                    PropertyGroup.GRP_SCANNER, R.string.beep_if_scanned_isbn_valid)
                    .setDefaultValue(false)
                    .setPreferenceKey(SoundManager.PREF_BEEP_IF_SCANNED_ISBN_VALID)
                    .setGlobal(true)
                    .setWeight(300))

            .add(new IntegerListProperty(mScannerListItems, ScannerManager.PREF_PREFERRED_SCANNER,
                    PropertyGroup.GRP_SCANNER,R.string.preferred_scanner)
                    .setDefaultValue(ScannerManager.SCANNER_ZXING_COMPATIBLE)
                    .setPreferenceKey(ScannerManager.PREF_PREFERRED_SCANNER)
                    .setGlobal(true))

            /* *****************************************************************************
             * GRP_THUMBNAILS:
             ******************************************************************************/

            .add(new BooleanProperty(BCPreferences.PREF_CROP_FRAME_WHOLE_IMAGE,
                    PropertyGroup.GRP_THUMBNAILS, R.string.default_crop_frame_is_whole_image)
                    .setDefaultValue(false)
                    .setPreferenceKey(BCPreferences.PREF_CROP_FRAME_WHOLE_IMAGE)
                    .setGlobal(true))

            .add(new IntegerListProperty(mRotationListItems, BCPreferences.PREF_AUTOROTATE_CAMERA_IMAGES,
                    PropertyGroup.GRP_THUMBNAILS, R.string.auto_rotate_camera_images)
                    .setDefaultValue(90)
                    .setPreferenceKey(BCPreferences.PREF_AUTOROTATE_CAMERA_IMAGES)
                    .setGlobal(true))

            .add(new BooleanProperty(BCPreferences.PREF_USE_EXTERNAL_IMAGE_CROPPER,
                    PropertyGroup.GRP_THUMBNAILS, R.string.use_external_image_cropper)
                    .setDefaultValue(false)
                    .setPreferenceKey(BCPreferences.PREF_USE_EXTERNAL_IMAGE_CROPPER)
                    .setGlobal(true))

            /* *****************************************************************************
             * GRP_ADVANCED_OPTIONS:
             ******************************************************************************/

            // Book list compatibility mode setting
            .add(new IntegerListProperty(mListGenerationOptionsListItems, BCPreferences.PREF_BOOKLIST_GENERATION_MODE,
                    PropertyGroup.GRP_ADVANCED_OPTIONS, R.string.booklist_generation)
                    .setDefaultValue(BooklistBuilder.BOOKLIST_GENERATE_AUTOMATIC)
                    .setPreferenceKey(BCPreferences.PREF_BOOKLIST_GENERATION_MODE)
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
    private static ItemEntries<String> getLocalesListItems() {
        ItemEntries<String> items = new ItemEntries<>();

        Locale l = BookCatalogueApp.getSystemLocal();
        items.add("", R.string.preferred_language_x, BookCatalogueApp.getResourceString(R.string.system_locale), l.getDisplayLanguage());

        for (String loc : BookCatalogueApp.getSupportedLocales()) {
            l = BookCatalogueApp.localeFromName(loc);
            items.add(loc, R.string.preferred_language_x, l.getDisplayLanguage(l), l.getDisplayLanguage());
        }
        return items;
    }

    /**
     * Format the list of themes
     *
     * @return List of preference themes
     */
    private static ItemEntries<Integer> getThemeListItems() {
        ItemEntries<Integer> items = new ItemEntries<>();

        String[] themeList = BookCatalogueApp.getResourceStringArray(R.array.supported_themes);
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
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // Make sure the names are correct
        updateLocalesListItems();

        setTitle(R.string.preferences);
    }

    @Override
    public void onPause() {
        // Don't bother listening since we check for locale changes in onResume of super class
        BookCatalogueApp.unregisterOnLocaleChangedListener(mLocaleListener);
        super.onPause();
    }

    @Override
    public void onResume() {
        super.onResume();
        // Listen for locale changes (this activity CAN change it)
        BookCatalogueApp.registerOnLocaleChangedListener(mLocaleListener);
    }

    /**
     * Display current preferences and set handlers to catch changes.
     */
    @Override
    protected void setupViews(Properties globalProps) {
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
            if (loc.isEmpty()) {
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
