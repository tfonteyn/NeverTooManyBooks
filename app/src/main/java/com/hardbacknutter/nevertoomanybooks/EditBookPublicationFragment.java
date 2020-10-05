/*
 * @Copyright 2020 HardBackNutter
 * @License GNU General Public License
 *
 * This file is part of NeverTooManyBooks.
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

import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.preference.PreferenceManager;

import java.util.List;

import com.hardbacknutter.nevertoomanybooks.database.DBDefinitions;
import com.hardbacknutter.nevertoomanybooks.databinding.FragmentEditBookPublicationBinding;
import com.hardbacknutter.nevertoomanybooks.entities.Book;
import com.hardbacknutter.nevertoomanybooks.entities.Publisher;
import com.hardbacknutter.nevertoomanybooks.fields.Field;
import com.hardbacknutter.nevertoomanybooks.fields.Fields;
import com.hardbacknutter.nevertoomanybooks.fields.accessors.DecimalEditTextAccessor;
import com.hardbacknutter.nevertoomanybooks.fields.accessors.EditTextAccessor;
import com.hardbacknutter.nevertoomanybooks.fields.accessors.TextViewAccessor;
import com.hardbacknutter.nevertoomanybooks.fields.formatters.CsvFormatter;
import com.hardbacknutter.nevertoomanybooks.fields.formatters.DateFieldFormatter;
import com.hardbacknutter.nevertoomanybooks.fields.formatters.DoubleNumberFormatter;

public class EditBookPublicationFragment
        extends EditBookBaseFragment {

    /** Log tag. */
    private static final String TAG = "EditBookPublicationFrag";

    /** View Binding. */
    private FragmentEditBookPublicationBinding mVb;

    @NonNull
    @Override
    public String getFragmentId() {
        return TAG;
    }

    @Override
    @Nullable
    public View onCreateView(@NonNull final LayoutInflater inflater,
                             @Nullable final ViewGroup container,
                             @Nullable final Bundle savedInstanceState) {
        mVb = FragmentEditBookPublicationBinding.inflate(inflater, container, false);
        return mVb.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull final View view,
                              @Nullable final Bundle savedInstanceState) {
        // setup common stuff and calls onInitFields()
        super.onViewCreated(view, savedInstanceState);

        mBookViewModel.onPublisherList().observe(getViewLifecycleOwner(), publishers -> {
            final Field<List<Publisher>, TextView> field = getField(R.id.publisher);
            field.getAccessor().setValue(publishers);
            field.validate();
        });

        mVb.publisher.setOnClickListener(v -> EditBookPublisherListDialogFragment
                .newInstance()
                // no listener/callback. We share the book view model in the Activity scope
                .show(getChildFragmentManager(), EditBookPublisherListDialogFragment.TAG));
    }

    @Override
    public void onResume() {
        //noinspection ConstantConditions
        mBookViewModel.prunePublishers(getContext());

        // hook up the Views, and calls {@link #onPopulateViews}
        super.onResume();
        // With all Views populated, (re-)add the helpers which rely on fields having valid views

        final SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(getContext());
        addAutocomplete(prefs, getField(R.id.format), () -> mFragmentVM.getAllFormats());
        addAutocomplete(prefs, getField(R.id.color), () -> mFragmentVM.getAllColors());
        addAutocomplete(prefs, getField(R.id.price_listed_currency),
                        () -> mFragmentVM.getAllListPriceCurrencyCodes());

        addPartialDatePicker(prefs, getField(R.id.date_published),
                             R.string.lbl_date_published, false);

        addPartialDatePicker(prefs, getField(R.id.first_publication),
                             R.string.lbl_first_publication, false);
    }

    @Override
    protected void onInitFields(@NonNull final Fields fields) {

        fields.add(R.id.pages, new EditTextAccessor<>(), DBDefinitions.KEY_PAGES)
              .setRelatedFields(R.id.lbl_pages);

        fields.add(R.id.format, new EditTextAccessor<>(), DBDefinitions.KEY_FORMAT)
              .setRelatedFields(R.id.lbl_format);

        fields.add(R.id.color, new EditTextAccessor<>(), DBDefinitions.KEY_COLOR)
              .setRelatedFields(R.id.lbl_color);

        fields.add(R.id.publisher, new TextViewAccessor<>(new CsvFormatter()),
                   Book.BKEY_PUBLISHER_LIST, DBDefinitions.KEY_PUBLISHER_NAME)
              .setRelatedFields(R.id.lbl_publisher);

        fields.add(R.id.print_run, new EditTextAccessor<>(), DBDefinitions.KEY_PRINT_RUN)
              .setRelatedFields(R.id.lbl_print_run);

        fields.add(R.id.date_published, new TextViewAccessor<>(new DateFieldFormatter()),
                   DBDefinitions.KEY_DATE_PUBLISHED)
              .setTextInputLayout(R.id.lbl_date_published);

        fields.add(R.id.first_publication, new TextViewAccessor<>(new DateFieldFormatter()),
                   DBDefinitions.KEY_DATE_FIRST_PUBLICATION)
              .setTextInputLayout(R.id.lbl_first_publication);

        // MUST be defined before the currency field is defined.
        fields.add(R.id.price_listed, new DecimalEditTextAccessor(new DoubleNumberFormatter()),
                   DBDefinitions.KEY_PRICE_LISTED);
        fields.add(R.id.price_listed_currency, new EditTextAccessor<>(),
                   DBDefinitions.KEY_PRICE_LISTED_CURRENCY)
              .setRelatedFields(R.id.lbl_price_listed,
                                R.id.lbl_price_listed_currency, R.id.price_listed_currency);
    }

    @Override
    void onPopulateViews(@NonNull final Fields fields,
                         @NonNull final Book book) {
        super.onPopulateViews(fields, book);

        // hide unwanted fields
        //noinspection ConstantConditions
        fields.setVisibility(getView(), false, false);
    }
}
