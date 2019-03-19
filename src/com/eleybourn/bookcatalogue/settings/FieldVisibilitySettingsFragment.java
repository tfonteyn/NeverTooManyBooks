package com.eleybourn.bookcatalogue.settings;

import android.content.Context;
import android.os.Bundle;

import androidx.annotation.Nullable;
import androidx.preference.PreferenceFragmentCompat;
import androidx.preference.PreferenceScreen;
import androidx.preference.SwitchPreferenceCompat;

import java.util.LinkedHashMap;
import java.util.Map;

import com.eleybourn.bookcatalogue.BookBaseFragment;
import com.eleybourn.bookcatalogue.BookFragment;
import com.eleybourn.bookcatalogue.R;
import com.eleybourn.bookcatalogue.UniqueId;
import com.eleybourn.bookcatalogue.datamanager.Fields;
import com.eleybourn.bookcatalogue.entities.Book;

/**
 * This is the Field Visibility page. It contains a list of all fields and a
 * switch to enable or disable the field on the main edit book screen.
 * <p>
 * Places to add them:
 * {@link BookBaseFragment#showHideFields(boolean)}
 * {@link BookFragment#populateAuthorListField(Book)} and similar show methods in that class
 * or the parent classes
 * <p>
 * Note that the Booklist related preferences do NOT observe visibility of these fields.
 * Modify / Hide / view a list... and unpredictable results might be shown to the user.
 */
public class FieldVisibilitySettingsFragment
        extends PreferenceFragmentCompat {

    /** Fragment manager tag. */
    public static final String TAG = FieldVisibilitySettingsFragment.class.getSimpleName();

    //NEWKIND: new fields visibility
    /** list of all the Fields that support hiding. */
    private static final Map<Integer, String> FIELD_LIST = new LinkedHashMap<>();

    static {
        FIELD_LIST.put(R.string.lbl_isbn, UniqueId.KEY_ISBN);
        FIELD_LIST.put(R.string.lbl_cover, UniqueId.BKEY_COVER_IMAGE);
        FIELD_LIST.put(R.string.lbl_series, UniqueId.KEY_SERIES);
        FIELD_LIST.put(R.string.lbl_series_num_long, UniqueId.KEY_SERIES_NUM);
        FIELD_LIST.put(R.string.lbl_description, UniqueId.KEY_DESCRIPTION);

        FIELD_LIST.put(R.string.lbl_publisher, UniqueId.KEY_PUBLISHER);
        FIELD_LIST.put(R.string.lbl_date_published, UniqueId.KEY_DATE_FIRST_PUBLISHED);
        FIELD_LIST.put(R.string.lbl_first_publication, UniqueId.KEY_DATE_PUBLISHED);

        FIELD_LIST.put(R.string.lbl_format, UniqueId.KEY_FORMAT);
        FIELD_LIST.put(R.string.lbl_genre, UniqueId.KEY_GENRE);
        FIELD_LIST.put(R.string.lbl_language, UniqueId.KEY_LANGUAGE);
        FIELD_LIST.put(R.string.lbl_pages, UniqueId.KEY_PAGES);
        FIELD_LIST.put(R.string.lbl_price_listed, UniqueId.KEY_PRICE_LISTED);

        FIELD_LIST.put(R.string.lbl_table_of_content, UniqueId.KEY_TOC_BITMASK);

        // **** PERSONAL FIELDS ****
        FIELD_LIST.put(R.string.lbl_bookshelf, UniqueId.KEY_BOOKSHELF);
        FIELD_LIST.put(R.string.lbl_lending, UniqueId.KEY_LOANEE);
        FIELD_LIST.put(R.string.lbl_notes, UniqueId.KEY_NOTES);
        FIELD_LIST.put(R.string.lbl_location_long, UniqueId.KEY_LOCATION);
        FIELD_LIST.put(R.string.lbl_price_paid, UniqueId.KEY_PRICE_PAID);
        FIELD_LIST.put(R.string.lbl_is_read, UniqueId.KEY_READ);
        FIELD_LIST.put(R.string.lbl_read_start, UniqueId.KEY_READ_START);
        FIELD_LIST.put(R.string.lbl_read_end, UniqueId.KEY_READ_END);
        FIELD_LIST.put(R.string.lbl_edition, UniqueId.KEY_EDITION_BITMASK);
        FIELD_LIST.put(R.string.lbl_is_signed, UniqueId.KEY_SIGNED);
        FIELD_LIST.put(R.string.lbl_rating, UniqueId.KEY_RATING);
    }

    @Override
    public void onCreatePreferences(@Nullable final Bundle savedInstanceState,
                                    @Nullable final String rootKey) {

        Context context = getPreferenceManager().getContext();
        PreferenceScreen screen = getPreferenceManager().createPreferenceScreen(context);
        screen.setTitle(R.string.menu_manage_fields);
        screen.setSummary(R.string.info_manage_fields);

        SwitchPreferenceCompat pref;
        for (int label : FIELD_LIST.keySet()) {
            pref = new SwitchPreferenceCompat(context);
            pref.setKey(Fields.PREFS_FIELD_VISIBILITY + FIELD_LIST.get(label));
            pref.setTitle(label);
            pref.setDefaultValue(true);
            screen.addPreference(pref);
        }

        setPreferenceScreen(screen);
    }
}
