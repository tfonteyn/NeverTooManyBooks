/*
 * @copyright 2010 Evan Leybourn
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
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.CallSuper;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.List;

import com.eleybourn.bookcatalogue.database.DBDefinitions;
import com.eleybourn.bookcatalogue.datamanager.Fields;
import com.eleybourn.bookcatalogue.datamanager.Fields.Field;
import com.eleybourn.bookcatalogue.dialogs.editordialog.PartialDatePickerDialogFragment;
import com.eleybourn.bookcatalogue.entities.Book;
import com.eleybourn.bookcatalogue.entities.BookManager;
import com.eleybourn.bookcatalogue.utils.DateUtils;

/**
 * This class is called by {@link EditBookFragment} and displays the publication fields Tab.
 */
public class EditBookPublicationFragment
        extends EditBookBaseFragment
        implements
        PartialDatePickerDialogFragment.OnPartialDatePickerResultsListener {

    /** Fragment manager tag. */
    public static final String TAG = EditBookPublicationFragment.class.getSimpleName();

    /**
     * Field drop down lists.
     * Lists in database so far, we cache them for performance but only load
     * them when really needed.
     */
    private List<String> mFormats;
    /** Field drop down list. */
    private List<String> mLanguages;
    /** Field drop down list. */
    private List<String> mPublishers;
    /** Field drop down list. */
    private List<String> mListPriceCurrencies;

    @Override
    @NonNull
    protected BookManager getBookManager() {
        return ((EditBookFragment) requireParentFragment()).getBookManager();
    }

    //<editor-fold desc="Fragment startup">

    @Override
    @Nullable
    public View onCreateView(@NonNull final LayoutInflater inflater,
                             @Nullable final ViewGroup container,
                             @Nullable final Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_edit_book_publication, container, false);
    }

    /**
     * Has no specific Arguments or savedInstanceState.
     * All storage interaction is done via:
     * {@link BookManager#getBook()} on the hosting Activity
     * {@link #onLoadFieldsFromBook(Book, boolean)} from base class onResume
     * {@link #onSaveFieldsToBook(Book)} from base class onPause
     */
    @CallSuper
    @Override
    public void onActivityCreated(@Nullable final Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);
        ViewUtils.fixFocusSettings(requireView());
    }

    /**
     * Some fields are only present (or need specific handling) on {@link BookFragment}.
     */
    @Override
    @CallSuper
    protected void initFields() {
        super.initFields();
        // multiple use
        Fields.FieldFormatter dateFormatter = new Fields.DateFieldFormatter();

        Field field;

        // book fields

        mFields.add(R.id.pages, DBDefinitions.KEY_PAGES);

        field = mFields.add(R.id.format, DBDefinitions.KEY_FORMAT);
        initValuePicker(field, R.string.lbl_format, R.id.btn_format, getFormats());

        field = mFields.add(R.id.language, DBDefinitions.KEY_LANGUAGE)
                       .setFormatter(new Fields.LanguageFormatter());
        initValuePicker(field, R.string.lbl_language, R.id.btn_language, getLanguages());

        field = mFields.add(R.id.publisher, DBDefinitions.KEY_PUBLISHER);
        initValuePicker(field, R.string.lbl_publisher, R.id.btn_publisher, getPublishers());

        field = mFields.add(R.id.date_published, DBDefinitions.KEY_DATE_PUBLISHED)
                       .setFormatter(dateFormatter);
        //noinspection ConstantConditions
        initPartialDatePicker(getTag(), field, R.string.lbl_date_published, false);

        field = mFields.add(R.id.first_publication, DBDefinitions.KEY_DATE_FIRST_PUBLISHED)
                       .setFormatter(dateFormatter);
        initPartialDatePicker(getTag(), field, R.string.lbl_first_publication, false);

        mFields.add(R.id.price_listed, DBDefinitions.KEY_PRICE_LISTED);
        field = mFields.add(R.id.price_listed_currency, DBDefinitions.KEY_PRICE_LISTED_CURRENCY);
        initValuePicker(field, R.string.lbl_currency, R.id.btn_price_listed_currency,
                        getListPriceCurrencyCodes());
    }

    @Override
    @CallSuper
    protected void onLoadFieldsFromBook(@NonNull final Book book,
                                        final boolean setAllFrom) {
        super.onLoadFieldsFromBook(book, setAllFrom);

        // Restore default visibility
        showHideFields(false);
    }

    //</editor-fold>

    //<editor-fold desc="Field editors callbacks">

    @Override
    public void onPartialDatePickerSave(final int destinationFieldId,
                                        @Nullable final Integer year,
                                        @Nullable final Integer month,
                                        @Nullable final Integer day) {
        mFields.getField(destinationFieldId).setValue(DateUtils.buildPartialDate(year, month, day));
    }

    //</editor-fold>

    //<editor-fold desc="Field drop down lists">

    /**
     * Load a publisher list; reloading this list every time a tab changes is slow.
     * So we cache it.
     *
     * @return List of publishers
     */
    @NonNull
    private List<String> getPublishers() {
        if (mPublishers == null) {
            mPublishers = mDb.getPublisherNames();
        }
        return mPublishers;
    }

    /**
     * Load a language list; reloading this list every time a tab changes is slow.
     * So we cache it.
     *
     * @return List of languages; full displayName
     */
    @NonNull
    private List<String> getLanguages() {
        if (mLanguages == null) {
            //noinspection ConstantConditions
            mLanguages = mDb.getLanguages(getContext());
        }
        return mLanguages;
    }

    /**
     * Load a format list; reloading this list every time a tab changes is slow.
     * So we cache it.
     *
     * @return List of formats
     */
    @NonNull
    private List<String> getFormats() {
        if (mFormats == null) {
            mFormats = mDb.getFormats();
        }
        return mFormats;
    }

    /**
     * Load a currency list; reloading this list every time a tab changes is slow.
     * So we cache it.
     *
     * @return List of ISO currency codes
     */
    @NonNull
    private List<String> getListPriceCurrencyCodes() {
        if (mListPriceCurrencies == null) {
            mListPriceCurrencies = mDb.getCurrencyCodes(DBDefinitions.KEY_PRICE_LISTED_CURRENCY);
        }
        return mListPriceCurrencies;
    }
    //</editor-fold>

}
