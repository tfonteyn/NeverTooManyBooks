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

import android.os.Bundle;
import android.support.annotation.CallSuper;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;

import com.eleybourn.bookcatalogue.BooksOnBookshelf;
import com.eleybourn.bookcatalogue.R;
import com.eleybourn.bookcatalogue.UniqueId;
import com.eleybourn.bookcatalogue.baseactivity.PreferencesBaseActivity;
import com.eleybourn.bookcatalogue.debug.Tracker;
import com.eleybourn.bookcatalogue.dialogs.HintManager;
import com.eleybourn.bookcatalogue.properties.BooleanProperty;
import com.eleybourn.bookcatalogue.properties.ListOfIntegerValuesProperty;
import com.eleybourn.bookcatalogue.properties.ListOfValuesProperty.ItemList;
import com.eleybourn.bookcatalogue.properties.Property;
import com.eleybourn.bookcatalogue.properties.PropertyGroup;
import com.eleybourn.bookcatalogue.properties.PropertyList;
import com.eleybourn.bookcatalogue.properties.PropertyWithGlobalValue;

import java.util.Objects;

/**
 * Activity to manage the preferences associate with Book lists (and {@link BooksOnBookshelf} ).
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
    private static final String PREF_BOOKLIST_GENERATION_MODE = "App.BooklistGenerationMode";
    /** Prefix for all preferences */
    private static final String TAG = "BookList.Global";
    /** Show flat backgrounds in Book lists */
    private static final String PREF_BACKGROUND_THUMBNAILS = TAG + ".BackgroundThumbnails";
    /** Show flat backgrounds in Book lists */
    private static final String PREF_CACHE_THUMBNAILS = TAG + ".CacheThumbnails";
    /** Always expand/collapse/preserve book list state */
    private static final String PREF_BOOK_LIST_STATE = TAG + ".BooklistState";

    /** Booklist state preservation property */
    private static ListOfIntegerValuesProperty mBooklistStateProperty;
    /** Booklist Compatibility mode property values */
    private static ListOfIntegerValuesProperty mBooklistCompatibilityModeProperty;

    /** Enable Thumbnail Cache property definition */
    private static BooleanProperty mCacheThumbnailsProperty;
    /** Enable Background Thumbnail fetch property definition */
    private static BooleanProperty mBackgroundThumbnailsProperty;

    static {
        ItemList<Integer> mBooklistStateListItems = new ItemList<Integer>()
                .add(BOOK_LIST_ALWAYS_EXPANDED, R.string.blp_state_start_expanded)
                .add(BOOK_LIST_ALWAYS_COLLAPSED, R.string.blp_state_start_collapsed)
                .add(BOOK_LIST_STATE_PRESERVED, R.string.blp_state_remember);
        mBooklistStateProperty =
                new ListOfIntegerValuesProperty(mBooklistStateListItems,
                        PREF_BOOK_LIST_STATE,
                        PropertyGroup.GRP_GENERAL,
                        R.string.blp_list_state,
                        BOOK_LIST_ALWAYS_EXPANDED)
                        .setPreferenceKey(PREF_BOOK_LIST_STATE)
                        .setIsGlobal(true);

        ItemList<Integer> mBooklistCompatibilityModeListItems = new ItemList<Integer>()
                .add(BOOKLIST_GENERATE_AUTOMATIC, R.string.blp_generation_use_recommended_option)
                .add(BOOKLIST_GENERATE_OLD_STYLE, R.string.blp_generation_force_compatibility_mode)
                .add(BOOKLIST_GENERATE_FLAT_TRIGGER, R.string.blp_generation_force_enhanced_compatibility_mode)
                .add(BOOKLIST_GENERATE_NESTED_TRIGGER, R.string.blp_generation_force_fully_featured);
        mBooklistCompatibilityModeProperty =
                new ListOfIntegerValuesProperty(mBooklistCompatibilityModeListItems,
                        PREF_BOOKLIST_GENERATION_MODE,
                        PropertyGroup.GRP_ADVANCED_OPTIONS,
                        R.string.blp_generation,
                        BOOKLIST_GENERATE_AUTOMATIC)
                        .setPreferenceKey(PREF_BOOKLIST_GENERATION_MODE)
                        .setIsGlobal(true)
                        .setWeight(1000);

        mCacheThumbnailsProperty =
                new BooleanProperty(PREF_CACHE_THUMBNAILS,
                        PropertyGroup.GRP_THUMBNAILS,
                        R.string.thumbnails_resizing,
                        Boolean.FALSE)
                        .setOptionLabels(R.string.thumbnails_resizing_cache_for_later_use,
                                R.string.thumbnails_resizing_each_time)
                        .setPreferenceKey(PREF_CACHE_THUMBNAILS)
                        .setIsGlobal(true)
                        .setWeight(110);

        mBackgroundThumbnailsProperty =
                new BooleanProperty(PREF_BACKGROUND_THUMBNAILS,
                        PropertyGroup.GRP_THUMBNAILS,
                        R.string.thumbnails_generating_mode,
                        Boolean.FALSE)
                        .setOptionLabels(R.string.thumbnails_generating_mode_use_background_thread,
                                R.string.thumbnails_generating_mode_generate_immediately)
                        .setPreferenceKey(PREF_BACKGROUND_THUMBNAILS)
                        .setIsGlobal(true)
                        .setWeight(100);
    }

    /**
     * Get the current preferred rebuild state for the list
     */
    public static int getRebuildState() {
        Integer value = mBooklistStateProperty.getValue();
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
        Integer value = mBooklistCompatibilityModeProperty.getValue();
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
    public void onCreate(final @Nullable Bundle savedInstanceState) {
        Tracker.enterOnCreate(this, savedInstanceState);
        super.onCreate(savedInstanceState);
        setTitle(R.string.menu_preferences_booklist);
        if (savedInstanceState == null) {
            HintManager.displayHint(this.getLayoutInflater(), R.string.hint_booklist_global_properties, null);
        }
        Tracker.exitOnCreate(this);
    }

    /**
     * Setup each component of the layout using the passed preferences
     */
    @Override
    protected void initFields(final @NonNull PropertyList globalPropertyList) {
        // Create a dummy style and add one group of each kind
        BooklistStyle style = new BooklistStyle("");
        // skip BOOK
        for (int kind = 1; kind < RowKinds.size(); kind++) {
            style.addGroup(kind);
        }

        // Get all the properties from the style that have global defaults.
        for (Property property : style.getProperties()) {
            if (property instanceof PropertyWithGlobalValue) {
                PropertyWithGlobalValue<?> globProp = (PropertyWithGlobalValue<?>) property;
                if (globProp.hasPreferenceKey()) {
                    globProp.setIsGlobal(true);
                    globalPropertyList.add(globProp);
                }
            }
        }
        // Add the locally constructed global properties
        globalPropertyList.add(mBooklistStateProperty);
        globalPropertyList.add(mCacheThumbnailsProperty);
        globalPropertyList.add(mBackgroundThumbnailsProperty);
        globalPropertyList.add(mBooklistCompatibilityModeProperty);
    }
}
