/*
 * @Copyright 2019 HardBackNutter
 * @License GNU General Public License
 *
 * This file is part of NeverTooManyBooks.
 *
 * In August 2018, this project was forked from:
 * Book Catalogue 5.2.2 @2016 Philip Warner & Evan Leybourn
 *
 * Without their original creation, this project would not exist in its
 * current form. It was however largely rewritten/refactored and any
 * comments on this fork should be directed at HardBackNutter and not
 * at the original creators.
 *
 * NeverTooManyBooks is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * NeverTooManyBooks is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 * See the GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with NeverTooManyBooks. If not, see <http://www.gnu.org/licenses/>.
 */
package com.hardbacknutter.nevertoomanybooks;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.CallSuper;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.Locale;

import com.hardbacknutter.nevertoomanybooks.database.DBDefinitions;
import com.hardbacknutter.nevertoomanybooks.datamanager.Fields;
import com.hardbacknutter.nevertoomanybooks.datamanager.Fields.Field;
import com.hardbacknutter.nevertoomanybooks.utils.LocaleUtils;

/**
 * This class is called by {@link EditBookFragment} and displays the publication fields Tab.
 */
public class EditBookPublicationFragment
        extends EditBookBaseFragment {

    /** Fragment manager tag. */
    public static final String TAG = "EditBookPublicationFragment";

    @Override
    @Nullable
    public View onCreateView(@NonNull final LayoutInflater inflater,
                             @Nullable final ViewGroup container,
                             @Nullable final Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_edit_book_publication, container, false);
    }

    /**
     * Some fields are only present (or need specific handling) on {@link BookFragment}.
     * <p>
     * <br>{@inheritDoc}
     */
    @Override
    protected void initFields() {
        super.initFields();
        Fields fields = getFields();

        //noinspection ConstantConditions
        Locale userLocale = LocaleUtils.getLocale(getContext());

        // multiple use
        Fields.FieldFormatter dateFormatter = new Fields.DateFieldFormatter();

        Field<String> field;

        // book fields

        fields.add(R.id.pages, DBDefinitions.KEY_PAGES);

        field = fields.add(R.id.format, DBDefinitions.KEY_FORMAT)
                      .setFormatter(new Fields.FormatFormatter());
        initValuePicker(field, R.string.lbl_format, R.id.btn_format,
                        mBookModel.getFormats());

        field = fields.add(R.id.language, DBDefinitions.KEY_LANGUAGE)
                      .setFormatter(new Fields.LanguageFormatter(userLocale));
        initValuePicker(field, R.string.lbl_language, R.id.btn_language,
                        mBookModel.getLanguagesCodes());

        field = fields.add(R.id.publisher, DBDefinitions.KEY_PUBLISHER);
        initValuePicker(field, R.string.lbl_publisher, R.id.btn_publisher,
                        mBookModel.getPublishers());

        field = fields.add(R.id.date_published, DBDefinitions.KEY_DATE_PUBLISHED)
                      .setFormatter(dateFormatter);
        initPartialDatePicker(field, R.string.lbl_date_published, false);

        field = fields.add(R.id.first_publication, DBDefinitions.KEY_DATE_FIRST_PUBLICATION)
                      .setFormatter(dateFormatter);
        initPartialDatePicker(field, R.string.lbl_first_publication, false);

        fields.add(R.id.price_listed, DBDefinitions.KEY_PRICE_LISTED);
        field = fields.add(R.id.price_listed_currency, DBDefinitions.KEY_PRICE_LISTED_CURRENCY);
        initValuePicker(field, R.string.lbl_currency, R.id.btn_price_listed_currency,
                        mBookModel.getListPriceCurrencyCodes());
    }

    /**
     * Has no specific Arguments or savedInstanceState.
     * <ul>All storage interaction is done via:
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

        // Fix the focus order for the views
        //noinspection ConstantConditions
        FocusSettings.fix(getView());
    }

    @Override
    protected void onLoadFieldsFromBook() {
        super.onLoadFieldsFromBook();

        // hide unwanted fields
        showOrHideFields(false);
    }
}
