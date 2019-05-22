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

import com.eleybourn.bookcatalogue.database.DBDefinitions;
import com.eleybourn.bookcatalogue.datamanager.Fields;
import com.eleybourn.bookcatalogue.datamanager.Fields.Field;

/**
 * This class is called by {@link EditBookFragment} and displays the publication fields Tab.
 */
public class EditBookPublicationFragment
        extends EditBookBaseFragment {

    /** Fragment manager tag. */
    public static final String TAG = EditBookPublicationFragment.class.getSimpleName();

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
     * <ul>
     * <li>{@link #onLoadFieldsFromBook} from base class onResume</li>
     * <li>{@link #onSaveFieldsToBook} from base class onPause</li>
     * </ul>
     * {@inheritDoc}
     */
    @CallSuper
    @Override
    public void onActivityCreated(@Nullable final Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);

        // do other stuff here that might affect the view.

        // Fix up the views
        //noinspection ConstantConditions
        ViewUtils.fixFocusSettings(getView());
    }

    /**
     * Some fields are only present (or need specific handling) on {@link BookFragment}.
     * <p>
     * <br>{@inheritDoc}
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
        initValuePicker(field, R.string.lbl_format, R.id.btn_format,
                        mBookBaseFragmentModel.getFormats());

        field = mFields.add(R.id.language, DBDefinitions.KEY_LANGUAGE)
                       .setFormatter(new Fields.LanguageFormatter());
        initValuePicker(field, R.string.lbl_language, R.id.btn_language,
                        mBookBaseFragmentModel.getLanguagesCodes());

        field = mFields.add(R.id.publisher, DBDefinitions.KEY_PUBLISHER);
        initValuePicker(field, R.string.lbl_publisher, R.id.btn_publisher,
                        mBookBaseFragmentModel.getPublishers());

        field = mFields.add(R.id.date_published, DBDefinitions.KEY_DATE_PUBLISHED)
                       .setFormatter(dateFormatter);
        initPartialDatePicker(field, R.string.lbl_date_published, false);

        field = mFields.add(R.id.first_publication, DBDefinitions.KEY_DATE_FIRST_PUBLISHED)
                       .setFormatter(dateFormatter);
        initPartialDatePicker(field, R.string.lbl_first_publication, false);

        mFields.add(R.id.price_listed, DBDefinitions.KEY_PRICE_LISTED);
        field = mFields.add(R.id.price_listed_currency, DBDefinitions.KEY_PRICE_LISTED_CURRENCY);
        initValuePicker(field, R.string.lbl_currency, R.id.btn_price_listed_currency,
                        mBookBaseFragmentModel.getListPriceCurrencyCodes());
    }

    @Override
    protected void onLoadFieldsFromBook() {
        super.onLoadFieldsFromBook();

        // Restore default visibility
        showHideFields(false);
    }
}
