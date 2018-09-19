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

import android.content.Intent;
import android.os.Bundle;
import android.support.annotation.NonNull;

import com.eleybourn.bookcatalogue.R;
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

/**
 * Activity to manage the preferences associate with Book lists (and the BooksOnBookshelf activity).
 *
 * @author Philip Warner
 */
public class BooklistPreferencesActivity extends PreferencesBaseActivity {

    // ID values for state preservation property
    public static final int BOOKLISTS_ALWAYS_EXPANDED = 1;
    public static final int BOOKLISTS_ALWAYS_COLLAPSED = 2;
    public static final int BOOKLISTS_STATE_PRESERVED = 3;

    /** Prefix for all preferences */
    private static final String TAG = "BookList.Global";
    /** Show flat backgrounds in Book lists */
    private static final String PREF_BACKGROUND_THUMBNAILS = TAG + ".BackgroundThumbnails";
    /** Show flat backgrounds in Book lists */
    private static final String PREF_CACHE_THUMBNAILS = TAG + ".CacheThumbnails";
    /** Key added to resulting Intent */
    private static final String PREF_CHANGED = TAG + ".PrefChanged";
    /** Always expand/collapse/preserve book list state */
    private static final String PREF_BOOKLISTS_STATE = TAG + ".BooklistState";


    /** Booklist state preservation property */
    private static final ItemEntries<Integer> mBooklistStateListItems = new ItemEntries<>();
    private static final IntegerListProperty mBooklistStateProperty =
            new IntegerListProperty(mBooklistStateListItems, PREF_BOOKLISTS_STATE,
                    PropertyGroup.GRP_GENERAL, R.string.book_list_state)
                    .setPreferenceKey(PREF_BOOKLISTS_STATE)
                    .setDefaultValue(BOOKLISTS_ALWAYS_EXPANDED)
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
        mBooklistStateListItems.add(null, R.string.use_default_setting);
        mBooklistStateListItems.add(BOOKLISTS_ALWAYS_EXPANDED, R.string.always_start_booklists_expanded);
        mBooklistStateListItems.add(BOOKLISTS_ALWAYS_COLLAPSED, R.string.always_start_booklists_collapsed);
        mBooklistStateListItems.add(BOOKLISTS_STATE_PRESERVED, R.string.remember_booklists_state);

        mCacheThumbnailsListItems.add(null, R.string.use_default_setting);
        mCacheThumbnailsListItems.add(false, R.string.resize_each_time);
        mCacheThumbnailsListItems.add(true, R.string.cache_resized_thumbnails_for_later_use);

        mBackgroundThumbnailsListItems.add(null, R.string.use_default_setting);
        mBackgroundThumbnailsListItems.add(false, R.string.generate_immediately);
        mBackgroundThumbnailsListItems.add(true, R.string.use_background_thread);
    }

    /**
     * Get the current preferred rebuild state for the list
     */
    public static int getRebuildState() {
        Integer value = mBooklistStateProperty.get();
        if (value == null) {
            value = mBooklistStateProperty.getDefaultValue();
            if (value == null) {
                throw new IllegalStateException();
            }
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
    public void onCreate(Bundle savedInstanceState) {
        try {
            super.onCreate(savedInstanceState);
            setTitle(R.string.booklist_preferences);
            if (savedInstanceState == null) {
                HintManager.displayHint(this, R.string.hint_booklist_global_properties, null);
            }
        } catch (Exception e) {
            Logger.logError(e);
        }
    }

    /**
     * Setup each component of the layout using the passed preferences
     */
    @Override
    protected void setupViews(@NonNull final Properties globalProperties) {
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
        // Add the locally constructed properties
        globalProperties.add(mBooklistStateProperty);
        globalProperties.add(mCacheThumbnailsProperty);
        globalProperties.add(mBackgroundThumbnailsProperty);
    }

    /**
     * Trap the onPause, and if the Activity is finishing then set the result.
     */
    @Override
    public void onPause() {
        super.onPause();

        if (isFinishing()) {
            Intent i = new Intent();
            i.putExtra(PREF_CHANGED, true);
            if (getParent() == null) {
                setResult(RESULT_OK, i);
            } else {
                getParent().setResult(RESULT_OK, i);
            }
        }
    }
}
