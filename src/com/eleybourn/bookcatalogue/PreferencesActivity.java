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
import androidx.annotation.CallSuper;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
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
        mProperties.add(new BooleanProperty(R.string.user_interface_open_book_read_only,
                PropertyGroup.GRP_USER_INTERFACE, Boolean.TRUE)
                .setPreferenceKey(BooksOnBookshelf.PREF_BOB_OPEN_BOOK_READ_ONLY)
                .setIsGlobal(true));

        /* List of supported locales */
        final ItemList<String> mLocalesListItems = LocaleUtils.getLocalesPreferencesListItems();
        mProperties.add(new ListOfStringValuesProperty(R.string.user_interface_preferred_language,
                PropertyGroup.GRP_USER_INTERFACE,
                Locale.ENGLISH.getISO3Language(), mLocalesListItems)
                .setPreferenceKey(LocaleUtils.PREF_APP_LOCALE)
                .setIsGlobal(true)
                .setWeight(200));

        /* List of supported themes */
        final ItemList<Integer> mAppThemeItems = ThemeUtils.getThemePreferencesListItems();
        mProperties.add(new ListOfIntegerValuesProperty(R.string.user_interface_theme,
                PropertyGroup.GRP_USER_INTERFACE,
                ThemeUtils.DEFAULT_THEME, mAppThemeItems)
                .setPreferenceKey(ThemeUtils.PREF_APP_THEME)
                .setIsGlobal(true)
                .setWeight(200));

        /* List of supported message implementations */
        final ItemList<Integer> mMessageImplementationItems = new ItemList<Integer>()
                .add(null, R.string.use_default_setting)
                .add(0, R.string.user_interface_messages_use_toast)
                .add(1, R.string.user_interface_messages_use_snackbar);
        mProperties.add(new ListOfIntegerValuesProperty(R.string.user_interface_messages_use,
                PropertyGroup.GRP_USER_INTERFACE,
                0, mMessageImplementationItems)
                .setPreferenceKey(BookCatalogueApp.PREF_APP_USER_MESSAGE)
                .setIsGlobal(true)
                .setWeight(200));

        /* *****************************************************************************
         * GRP_SCANNER:
         ******************************************************************************/

        mProperties.add(new BooleanProperty(R.string.scanning_beep_if_isbn_invalid,
                PropertyGroup.GRP_SCANNER, Boolean.TRUE)
                .setPreferenceKey(SoundManager.PREF_BEEP_IF_SCANNED_ISBN_INVALID)
                .setIsGlobal(true)
                .setWeight(300));

        mProperties.add(new BooleanProperty(R.string.scanning_beep_if_isbn_valid,
                PropertyGroup.GRP_SCANNER)
                .setPreferenceKey(SoundManager.PREF_BEEP_IF_SCANNED_ISBN_VALID)
                .setIsGlobal(true)
                .setWeight(300));

        /* Supported scanners*/
        final ItemList<Integer> mScannerListItems = new ItemList<Integer>()
                .add(null, R.string.use_default_setting)
                .add(ScannerManager.SCANNER_ZXING_COMPATIBLE, R.string.scanning_preferred_scanner_zxing_compatible) // default
                .add(ScannerManager.SCANNER_ZXING, R.string.scanning_preferred_scanner_zxing)
                .add(ScannerManager.SCANNER_PIC2SHOP, R.string.scanning_preferred_scanner_pic2shop);
        mProperties.add(new ListOfIntegerValuesProperty(R.string.scanning_preferred_scanner,
                PropertyGroup.GRP_SCANNER,
                ScannerManager.SCANNER_ZXING_COMPATIBLE, mScannerListItems)
                .setPreferenceKey(ScannerManager.PREF_PREFERRED_SCANNER)
                .setIsGlobal(true));

        /* *****************************************************************************
         * GRP_THUMBNAILS:
         ******************************************************************************/

        mProperties.add(new BooleanProperty(R.string.thumbnails_default_crop_frame_is_whole_image,
                PropertyGroup.GRP_THUMBNAILS)
                .setPreferenceKey(CoverHandler.PREF_CROPPER_FRAME_IS_WHOLE_IMAGE)
                .setIsGlobal(true));

        /* Camera image rotation property values */
        final ItemList<Integer> mRotationListItems = new ItemList<Integer>()
                .add(null, R.string.use_default_setting)
                .add(0, R.string.no)
                .add(90, R.string.menu_cover_rotate_cw)
                .add(-90, R.string.menu_cover_rotate_ccw)
                .add(180, R.string.menu_cover_rotate_180);
        mProperties.add(new ListOfIntegerValuesProperty(R.string.thumbnails_rotate_auto,
                PropertyGroup.GRP_THUMBNAILS, 0, mRotationListItems)
                .setPreferenceKey(CoverHandler.PREF_CAMERA_AUTOROTATE)
                .setIsGlobal(true));

        mProperties.add(new BooleanProperty(R.string.thumbnails_use_external_image_cropper,
                PropertyGroup.GRP_THUMBNAILS)
                .setPreferenceKey(CoverHandler.PREF_CROPPER_USE_EXTERNAL_APP)
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
         * 2018-11-30: making this a configuration option;
         * default CoverHandler.PREF_IMAGE_VIEW_LAYER_TYPE_DEFAULT('-1') == use device default
         */
        final ItemList<Integer> mViewLayerType = new ItemList<Integer>()
                .add(null, R.string.use_default_setting)
                .add(View.LAYER_TYPE_HARDWARE, R.string.pref_layer_type_hardware)
                .add(View.LAYER_TYPE_SOFTWARE, R.string.pref_layer_type_software);
        mProperties.add(new ListOfIntegerValuesProperty(R.string.pref_layer_type,
                PropertyGroup.GRP_THUMBNAILS,
                CoverHandler.PREF_IMAGE_VIEW_LAYER_TYPE_DEFAULT, mViewLayerType)
                .setPreferenceKey(CoverHandler.PREF_IMAGE_VIEW_LAYER_TYPE)
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
