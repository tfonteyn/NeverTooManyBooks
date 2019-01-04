package com.eleybourn.bookcatalogue.settings;

import android.content.Context;
import android.os.Bundle;

import com.eleybourn.bookcatalogue.BookBaseFragment;
import com.eleybourn.bookcatalogue.BookFragment;
import com.eleybourn.bookcatalogue.R;
import com.eleybourn.bookcatalogue.UniqueId;
import com.eleybourn.bookcatalogue.entities.Book;

import java.util.LinkedHashMap;
import java.util.Map;

import androidx.preference.PreferenceFragmentCompat;
import androidx.preference.PreferenceScreen;
import androidx.preference.SwitchPreferenceCompat;

/**
 * This is the Field Visibility page. It contains a list of all fields and a
 * switch to enable or disable the field on the main edit book screen.
 *
 * Places to add them:
 * {@link BookBaseFragment#showHideFields(boolean)}
 * {@link BookFragment#populateAuthorListField(Book)} and similar show methods in that class
 * or the parent classes
 *
 * Note that the Booklist related preferences do NOT observe visibility of these fields.
 * Modify / Hide / view a list... and unpredictable results might be shown to the user.
 */
public class FieldVisibilitySettingsFragment extends PreferenceFragmentCompat {

    private final static String KEY_PREFIX = "fields.visibility."; /* + fieldName */

    //NEWKIND: new fields visibility
    private static final Map<Integer,String> list = new LinkedHashMap<>();
    static {
        list.put(R.string.lbl_isbn, UniqueId.KEY_BOOK_ISBN);
        list.put(R.string.lbl_cover, UniqueId.BKEY_HAVE_THUMBNAIL);
        list.put(R.string.lbl_series, UniqueId.KEY_SERIES);
        list.put(R.string.lbl_series_num, UniqueId.KEY_SERIES_NUM);
        list.put(R.string.lbl_description, UniqueId.KEY_BOOK_DESCRIPTION);

        list.put(R.string.lbl_publisher,UniqueId.KEY_BOOK_PUBLISHER);
        list.put(R.string.lbl_date_published,UniqueId.KEY_FIRST_PUBLICATION);
        list.put(R.string.lbl_first_publication,UniqueId.KEY_BOOK_DATE_PUBLISHED);

        list.put(R.string.lbl_format,UniqueId.KEY_BOOK_FORMAT);
        list.put(R.string.lbl_genre,UniqueId.KEY_BOOK_GENRE);
        list.put(R.string.lbl_language,UniqueId.KEY_BOOK_LANGUAGE);
        list.put(R.string.lbl_pages,UniqueId.KEY_BOOK_PAGES);
        list.put(R.string.lbl_price_listed,UniqueId.KEY_BOOK_PRICE_LISTED);

        list.put(R.string.table_of_content,UniqueId.KEY_BOOK_ANTHOLOGY_BITMASK);

        // **** PERSONAL FIELDS ****
        list.put(R.string.lbl_bookshelf,UniqueId.KEY_BOOKSHELF_NAME);
        list.put(R.string.lbl_lending,UniqueId.KEY_LOAN_LOANED_TO);
        list.put(R.string.lbl_notes,UniqueId.KEY_BOOK_NOTES);
        list.put(R.string.lbl_location_long,UniqueId.KEY_BOOK_LOCATION);
        list.put(R.string.lbl_price_paid,UniqueId.KEY_BOOK_PRICE_PAID);
        list.put(R.string.lbl_is_read,UniqueId.KEY_BOOK_READ);
        list.put(R.string.lbl_read_start,UniqueId.KEY_BOOK_READ_START);
        list.put(R.string.lbl_read_end,UniqueId.KEY_BOOK_READ_END);
        list.put(R.string.lbl_edition,UniqueId.KEY_BOOK_EDITION_BITMASK);
        list.put(R.string.lbl_is_signed,UniqueId.KEY_BOOK_SIGNED);
        list.put(R.string.lbl_rating,UniqueId.KEY_BOOK_RATING);
    }

    @Override
    public void onCreatePreferences(final Bundle savedInstanceState, final String rootKey) {

        Context context = getPreferenceManager().getContext();
        PreferenceScreen screen = getPreferenceManager().createPreferenceScreen(context);
        screen.setTitle(R.string.menu_manage_fields);
        screen.setSummary(R.string.info_manage_fields);

        SwitchPreferenceCompat pref;
        for (int label : list.keySet()) {
            pref = new SwitchPreferenceCompat(context);
            pref.setKey(KEY_PREFIX + list.get(label));
            pref.setTitle(label);
            pref.setDefaultValue(true);
            screen.addPreference(pref);
        }

        setPreferenceScreen(screen);
    }
}
