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
import android.view.View;

import com.eleybourn.bookcatalogue.baseactivity.PreferencesBaseActivity;
import com.eleybourn.bookcatalogue.debug.Tracker;
import com.eleybourn.bookcatalogue.properties.BooleanProperty;
import com.eleybourn.bookcatalogue.properties.ListOfIntegerValuesProperty;
import com.eleybourn.bookcatalogue.properties.ListOfStringValuesProperty;
import com.eleybourn.bookcatalogue.properties.ListOfValuesProperty.ItemList;
import com.eleybourn.bookcatalogue.properties.PropertyGroup;
import com.eleybourn.bookcatalogue.properties.PropertyList;
import com.eleybourn.bookcatalogue.scanner.ScannerManager;
import com.eleybourn.bookcatalogue.utils.LocaleUtils;
import com.eleybourn.bookcatalogue.utils.SoundManager;
import com.eleybourn.bookcatalogue.utils.ThemeUtils;

import java.util.Locale;

/**
 * Activity to display the 'Preferences' dialog and maintain the preferences.
 *
 * @author Philip Warner
 */
public class PreferencesActivity extends PreferencesBaseActivity {

    public static final int REQUEST_CODE = UniqueId.ACTIVITY_REQUEST_CODE_PREFERENCES;

    /**
     * Build the complete list of all preferences
     */
    private static final PropertyList mProperties = new PropertyList();
    static {
        /* *****************************************************************************
         * GRP_USER_INTERFACE:
         ******************************************************************************/
        /*
         * Enabling/disabling read-only mode when opening book. If enabled book
         * is opened in read-only mode (editing through menu), else in edit mode.
         */
        mProperties.add(new BooleanProperty(BooksOnBookshelf.PREF_OPEN_BOOK_READ_ONLY,
                PropertyGroup.GRP_USER_INTERFACE, R.string.user_interface_open_book_read_only,
                Boolean.TRUE)
                .setPreferenceKey(BooksOnBookshelf.PREF_OPEN_BOOK_READ_ONLY)
                .setIsGlobal(true));

        /* List of supported locales */
        final ItemList<String> mLocalesListItems = LocaleUtils.getLocalesPreferencesListItems();
        mProperties.add(new ListOfStringValuesProperty(mLocalesListItems, LocaleUtils.PREF_APP_LOCALE,
                PropertyGroup.GRP_USER_INTERFACE, R.string.user_interface_preferred_language,
                Locale.ENGLISH.getISO3Language())
                .setPreferenceKey(LocaleUtils.PREF_APP_LOCALE)
                .setIsGlobal(true)
                .setWeight(200));

        /* List of supported themes */
        final ItemList<Integer> mAppThemeItems = ThemeUtils.getThemePreferencesListItems();
        mProperties.add(new ListOfIntegerValuesProperty(mAppThemeItems, ThemeUtils.PREF_APP_THEME,
                PropertyGroup.GRP_USER_INTERFACE, R.string.user_interface_theme,
                ThemeUtils.DEFAULT_THEME)
                .setPreferenceKey(ThemeUtils.PREF_APP_THEME)
                .setIsGlobal(true)
                .setWeight(200));

        /* List of supported message implementations */
        final ItemList<Integer> mMessageImplementationItems = new ItemList<Integer>()
                .add(0, R.string.user_interface_messages_use_toast)
                .add(1, R.string.user_interface_messages_use_snackbar);
        mProperties.add(new ListOfIntegerValuesProperty(mMessageImplementationItems, BookCatalogueApp.PREF_APP_USER_MESSAGE,
                PropertyGroup.GRP_USER_INTERFACE, R.string.user_interface_messages_use,
                0)
                .setPreferenceKey(BookCatalogueApp.PREF_APP_USER_MESSAGE)
                .setIsGlobal(true)
                .setWeight(200));

        /* *****************************************************************************
         * GRP_SCANNER:
         ******************************************************************************/

        mProperties.add(new BooleanProperty(SoundManager.PREF_BEEP_IF_SCANNED_ISBN_INVALID,
                PropertyGroup.GRP_SCANNER, R.string.scanning_beep_if_isbn_invalid,
                Boolean.TRUE)
                .setPreferenceKey(SoundManager.PREF_BEEP_IF_SCANNED_ISBN_INVALID)
                .setIsGlobal(true)
                .setWeight(300));

        mProperties.add(new BooleanProperty(SoundManager.PREF_BEEP_IF_SCANNED_ISBN_VALID,
                PropertyGroup.GRP_SCANNER, R.string.scanning_beep_if_isbn_valid,
                Boolean.FALSE)
                .setPreferenceKey(SoundManager.PREF_BEEP_IF_SCANNED_ISBN_VALID)
                .setIsGlobal(true)
                .setWeight(300));

        /* Supported scanners*/
        final ItemList<Integer> mScannerListItems = new ItemList<Integer>()
                .add(null, R.string.use_default_setting)
                .add(ScannerManager.SCANNER_ZXING_COMPATIBLE, R.string.scanning_preferred_scanner_zxing_compatible)
                .add(ScannerManager.SCANNER_ZXING, R.string.scanning_preferred_scanner_zxing)
                .add(ScannerManager.SCANNER_PIC2SHOP, R.string.scanning_preferred_scanner_pic2shop);
        mProperties.add(new ListOfIntegerValuesProperty(mScannerListItems, ScannerManager.PREF_PREFERRED_SCANNER,
                PropertyGroup.GRP_SCANNER, R.string.scanning_preferred_scanner,
                ScannerManager.SCANNER_ZXING_COMPATIBLE)
                .setPreferenceKey(ScannerManager.PREF_PREFERRED_SCANNER)
                .setIsGlobal(true));

        /* *****************************************************************************
         * GRP_THUMBNAILS:
         ******************************************************************************/

        mProperties.add(new BooleanProperty(CoverHandler.PREF_CROP_FRAME_WHOLE_IMAGE,
                PropertyGroup.GRP_THUMBNAILS, R.string.thumbnails_default_crop_frame_is_whole_image,
                Boolean.FALSE)
                .setPreferenceKey(CoverHandler.PREF_CROP_FRAME_WHOLE_IMAGE)
                .setIsGlobal(true));

        /* Camera image rotation property values */
        final ItemList<Integer> mRotationListItems = new ItemList<Integer>()
                .add(0, R.string.no)
                .add(90, R.string.menu_cover_rotate_cw)
                .add(-90, R.string.menu_cover_rotate_ccw)
                .add(180, R.string.menu_cover_rotate_180);
        mProperties.add(new ListOfIntegerValuesProperty(mRotationListItems, CoverHandler.PREF_AUTOROTATE_CAMERA_IMAGES,
                PropertyGroup.GRP_THUMBNAILS, R.string.thumbnails_rotate_auto,
                CoverHandler.PREF_AUTOROTATE_CAMERA_IMAGES_DEFAULT)
                .setPreferenceKey(CoverHandler.PREF_AUTOROTATE_CAMERA_IMAGES)
                .setIsGlobal(true));

        mProperties.add(new BooleanProperty(CoverHandler.PREF_USE_EXTERNAL_IMAGE_CROPPER,
                PropertyGroup.GRP_THUMBNAILS, R.string.thumbnails_use_external_image_cropper,
                Boolean.FALSE)
                .setPreferenceKey(CoverHandler.PREF_USE_EXTERNAL_IMAGE_CROPPER)
                .setIsGlobal(true));

        /*
         * Layer Type to use for the Cropper.
         *
         * {@link CropImageViewTouchBase} We get 'unsupported feature' crashes if the option to always use GL is turned on.
         * See:
         * http://developer.android.com/guide/topics/graphics/hardware-accel.html
         * http://stackoverflow.com/questions/13676059/android-unsupportedoperationexception-at-canvas-clippath
         * so for API level > 11, we turn it off manually.
         *
         * 2018-11-30: making this a configuration option; default '-1' == use device default  (0 is another system value)
         */
        final ItemList<Integer> mViewLayerType = new ItemList<Integer>()
                .add(-1, R.string.use_default_setting)
                .add(View.LAYER_TYPE_HARDWARE, R.string.pref_layer_type_hardware)
                .add(View.LAYER_TYPE_SOFTWARE, R.string.pref_layer_type_software);
        mProperties.add(new ListOfIntegerValuesProperty(mViewLayerType, CoverHandler.PREF_VIEW_LAYER_TYPE,
                PropertyGroup.GRP_THUMBNAILS, R.string.pref_layer_type,
                -1)
                .setPreferenceKey(CoverHandler.PREF_VIEW_LAYER_TYPE)
                .setIsGlobal(true)
                .setHint(R.string.hint_pref_layer_type)
                .setWeight(100));
    }

    @Override
    protected int getLayoutId() {
        return R.layout.activity_preferences;
    }

    @Override
    @CallSuper
    public void onCreate(final @Nullable Bundle savedInstanceState) {
        Tracker.enterOnCreate(this, savedInstanceState);
        super.onCreate(savedInstanceState);
        setTitle(R.string.lbl_preferences);
        Tracker.exitOnCreate(this);
    }

    /**
     * Display current preferences and set handlers to catch changes.
     */
    @Override
    protected void initFields(@NonNull PropertyList globalProps) {
        // Add the locally constructed properties
        globalProps.addAll(mProperties);
    }
}
