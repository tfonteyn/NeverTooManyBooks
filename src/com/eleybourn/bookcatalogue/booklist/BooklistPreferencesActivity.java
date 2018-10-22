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

package com.eleybourn.bookcatalogue.booklist;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.support.annotation.CallSuper;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;

import com.eleybourn.bookcatalogue.R;
import com.eleybourn.bookcatalogue.UniqueId;
import com.eleybourn.bookcatalogue.baseactivity.PreferencesBaseActivity;
import com.eleybourn.bookcatalogue.booklist.BooklistGroup.RowKinds;
import com.eleybourn.bookcatalogue.debug.Logger;
import com.eleybourn.bookcatalogue.dialogs.HintManager;
import com.eleybourn.bookcatalogue.properties.BooleanListProperty;
import com.eleybourn.bookcatalogue.properties.IntegerListProperty;
import com.eleybourn.bookcatalogue.properties.ListProperty.ItemEntries;
import com.eleybourn.bookcatalogue.properties.Properties;
import com.eleybourn.bookcatalogue.properties.Property;
import com.eleybourn.bookcatalogue.properties.PropertyGroup;
import com.eleybourn.bookcatalogue.properties.ValuePropertyWithGlobalDefault;

import java.util.Objects;

/**
 * Activity to manage the preferences associate with Book lists (and the BooksOnBookshelf activity).
 *
 * @author Philip Warner
 */
public class BooklistPreferencesActivity extends PreferencesBaseActivity {

    public static final int REQUEST_CODE = UniqueId.ACTIVITY_REQUEST_CODE_BOOKLIST_PREFERENCES;

    // ID values for state preservation property
    public static final int BOOK_LIST_ALWAYS_EXPANDED = 1; // default
    public static final int BOOK_LIST_ALWAYS_COLLAPSED = 2;
    public static final int BOOK_LIST_STATE_PRESERVED = 3;

    /** BookList Compatibility mode property values */
    public static final int BOOKLIST_GENERATE_OLD_STYLE = 1;
    public static final int BOOKLIST_GENERATE_FLAT_TRIGGER = 2;
    public static final int BOOKLIST_GENERATE_NESTED_TRIGGER = 3;
    public static final int BOOKLIST_GENERATE_AUTOMATIC = 4; // default

    /** Force list construction to compatible mode (compatible with Android 1.6) (name kept for backwards compatibility) */
    public static final String PREF_BOOKLIST_GENERATION_MODE = "App.BooklistGenerationMode";

    /** Prefix for all preferences */
    private static final String TAG = "BookList.Global";
    /** Show flat backgrounds in Book lists */
    private static final String PREF_BACKGROUND_THUMBNAILS = TAG + ".BackgroundThumbnails";
    /** Show flat backgrounds in Book lists */
    private static final String PREF_CACHE_THUMBNAILS = TAG + ".CacheThumbnails";
    /** Always expand/collapse/preserve book list state */
    private static final String PREF_BOOK_LIST_STATE = TAG + ".BooklistState";

    /** Booklist state preservation property */
    private static final ItemEntries<Integer> mBooklistStateListItems = new ItemEntries<>();
    private static final IntegerListProperty mBooklistStateProperty =
            new IntegerListProperty(mBooklistStateListItems, PREF_BOOK_LIST_STATE,
                    PropertyGroup.GRP_GENERAL, R.string.book_list_state)
                    .setPreferenceKey(PREF_BOOK_LIST_STATE)
                    .setDefaultValue(BOOK_LIST_ALWAYS_EXPANDED)
                    .setGlobal(true);
    /** Booklist Compatibility mode property values */
    private static final ItemEntries<Integer> mBooklistCompatibilityModeListItems = new ItemEntries<>();
    private static final IntegerListProperty mBooklistCompatibilityModeProperty =
            new IntegerListProperty(mBooklistCompatibilityModeListItems, PREF_BOOKLIST_GENERATION_MODE,
                    PropertyGroup.GRP_ADVANCED_OPTIONS, R.string.booklist_generation)
                    .setDefaultValue(BOOKLIST_GENERATE_AUTOMATIC)
                    .setPreferenceKey(PREF_BOOKLIST_GENERATION_MODE)
                    .setGlobal(true);
    /** Enable Thumbnail Cache property definition */
    private static final ItemEntries<Boolean> mCacheThumbnailsListItems = new ItemEntries<>();
    private static final BooleanListProperty mCacheThumbnailsProperty =
            new BooleanListProperty(mCacheThumbnailsListItems, PREF_CACHE_THUMBNAILS,
                    PropertyGroup.GRP_THUMBNAILS, R.string.resizing_cover_thumbnails)
                    .setPreferenceKey(PREF_CACHE_THUMBNAILS)
                    .setDefaultValue(false)
                    .setGlobal(true)
                    .setWeight(100);
    /** Enable Background Thumbnail fetch property definition */
    private static final ItemEntries<Boolean> mBackgroundThumbnailsListItems = new ItemEntries<>();
    private static final BooleanListProperty mBackgroundThumbnailsProperty =
            new BooleanListProperty(mBackgroundThumbnailsListItems, PREF_BACKGROUND_THUMBNAILS,
                    PropertyGroup.GRP_THUMBNAILS, R.string.generating_cover_thumbnails)
                    .setPreferenceKey(PREF_BACKGROUND_THUMBNAILS)
                    .setDefaultValue(false)
                    .setGlobal(true)
                    .setWeight(100);

    static {
        mBooklistStateListItems.add(null, R.string.use_default_setting)
                .add(BOOK_LIST_ALWAYS_EXPANDED, R.string.always_start_booklists_expanded)
                .add(BOOK_LIST_ALWAYS_COLLAPSED, R.string.always_start_booklists_collapsed)
                .add(BOOK_LIST_STATE_PRESERVED, R.string.remember_booklists_state);

        mBooklistCompatibilityModeListItems.add(null, R.string.use_default_setting)
                .add(BOOKLIST_GENERATE_OLD_STYLE, R.string.force_compatibility_mode)
                .add(BOOKLIST_GENERATE_FLAT_TRIGGER, R.string.force_enhanced_compatibility_mode)
                .add(BOOKLIST_GENERATE_NESTED_TRIGGER, R.string.force_fully_featured)
                .add(BOOKLIST_GENERATE_AUTOMATIC, R.string.automatically_use_recommended_option);

        mCacheThumbnailsListItems.add(null, R.string.use_default_setting)
                .add(false, R.string.resize_each_time)
                .add(true, R.string.cache_resized_thumbnails_for_later_use);

        mBackgroundThumbnailsListItems.add(null, R.string.use_default_setting)
                .add(false, R.string.generate_immediately)
                .add(true, R.string.use_background_thread);
    }

    /**
     * Get the current preferred rebuild state for the list
     */
    public static int getRebuildState() {
        Integer value = mBooklistStateProperty.get();
        if (value == null) {
            value = mBooklistStateProperty.getDefaultValue();
            Objects.requireNonNull(value);
        }
        return value;
    }

    /**
     * Get the current preferred compatibility mode for the list
     */
    public static int getCompatibilityMode() {
        Integer value = mBooklistCompatibilityModeProperty.get();
        if (value == null) {
            value = mBooklistCompatibilityModeProperty.getDefaultValue();
            Objects.requireNonNull(value);
        }
        return value;
    }

    public static boolean isThumbnailCacheEnabled() {
        return mCacheThumbnailsProperty.isTrue();
    }

    public static boolean isBackgroundThumbnailsEnabled() {
        return mBackgroundThumbnailsProperty.isTrue();
    }

    /**
     * Return the layout to use for this subclass
     */
    @Override
    public int getLayoutId() {
        return R.layout.activity_booklist_preferences;
    }

    /**
     * Build the activity UI
     */
    @Override
    @CallSuper
    public void onCreate(@Nullable final Bundle savedInstanceState) {
        try {
            super.onCreate(savedInstanceState);
            setTitle(R.string.booklist_preferences);
            if (savedInstanceState == null) {
                HintManager.displayHint(this, R.string.hint_booklist_global_properties, null);
            }
        } catch (Exception e) {
            Logger.error(e);
        }
    }

    /**
     * Setup each component of the layout using the passed preferences
     */
    @Override
    protected void initViews(@NonNull final Properties globalProperties) {
        // Create a dummy style and add one group of each kind
        BooklistStyle style = new BooklistStyle("");
        for (int kind : BooklistGroup.getRowKinds()) {
            if (kind != RowKinds.ROW_KIND_BOOK)
                style.addGroup(kind);
        }

        // Get all the properties from the style that have global defaults.
        for (Property property : style.getProperties()) {
            if (property instanceof ValuePropertyWithGlobalDefault) {
                ValuePropertyWithGlobalDefault<?> globProp = (ValuePropertyWithGlobalDefault<?>) property;
                if (globProp.hasGlobalDefault()) {
                    globProp.setGlobal(true);
                    globalProperties.add(globProp);
                }
            }
        }
        // Add the locally constructed global properties
        globalProperties.add(mBooklistStateProperty);
        globalProperties.add(mCacheThumbnailsProperty);
        globalProperties.add(mBackgroundThumbnailsProperty);
        globalProperties.add(mBooklistCompatibilityModeProperty);
    }

    /**
     * Trap the onPause, and if the Activity is finishing then set the result.
     */
    @Override
    @CallSuper
    public void onPause() {
        super.onPause();

        // as we don't really have an easy way to detect changes, nor use a 'confirm' anywhere,
        // just set Activity.RESULT_OK here basically assuming that the user must have made a change
        if (isFinishing()) {
            Intent intent = new Intent();
            setResult(Activity.RESULT_OK, intent);
        }
    }
}
