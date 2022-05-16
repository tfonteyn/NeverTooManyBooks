/*
 * @Copyright 2018-2021 HardBackNutter
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
package com.hardbacknutter.nevertoomanybooks.bookdetails;

import android.content.Context;
import android.os.Bundle;
import android.view.View;

import androidx.annotation.IdRes;
import androidx.annotation.NonNull;
import androidx.lifecycle.ViewModel;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

import com.hardbacknutter.nevertoomanybooks.R;
import com.hardbacknutter.nevertoomanybooks.ServiceLocator;
import com.hardbacknutter.nevertoomanybooks.booklist.style.Style;
import com.hardbacknutter.nevertoomanybooks.database.DBKey;
import com.hardbacknutter.nevertoomanybooks.entities.Book;
import com.hardbacknutter.nevertoomanybooks.entities.Details;
import com.hardbacknutter.nevertoomanybooks.entities.Entity;
import com.hardbacknutter.nevertoomanybooks.fields.BooleanIndicatorField;
import com.hardbacknutter.nevertoomanybooks.fields.Field;
import com.hardbacknutter.nevertoomanybooks.fields.FragmentId;
import com.hardbacknutter.nevertoomanybooks.fields.RatingBarField;
import com.hardbacknutter.nevertoomanybooks.fields.TextViewField;
import com.hardbacknutter.nevertoomanybooks.fields.formatters.BitmaskFormatter;
import com.hardbacknutter.nevertoomanybooks.fields.formatters.DateFieldFormatter;
import com.hardbacknutter.nevertoomanybooks.fields.formatters.FieldFormatter;
import com.hardbacknutter.nevertoomanybooks.fields.formatters.HtmlFormatter;
import com.hardbacknutter.nevertoomanybooks.fields.formatters.LanguageFormatter;
import com.hardbacknutter.nevertoomanybooks.fields.formatters.ListFormatter;
import com.hardbacknutter.nevertoomanybooks.fields.formatters.MoneyFormatter;
import com.hardbacknutter.nevertoomanybooks.fields.formatters.PagesFormatter;
import com.hardbacknutter.nevertoomanybooks.fields.formatters.StringArrayResFormatter;
import com.hardbacknutter.nevertoomanybooks.searchengines.amazon.AmazonHandler;
import com.hardbacknutter.nevertoomanybooks.utils.MenuHandler;
import com.hardbacknutter.nevertoomanybooks.utils.Money;

/**
 * Contains data in the <strong>Activity</strong> scope;
 * i.e. shared between all fragments in the {@link ShowBookPagerFragment} pager adapter.
 * <p>
 * This means that we save cpu-cycles as we don't have to create the field definitions
 * for each page change, but also means we cannot display more then one page at a time.
 */
public class ShowBookDetailsActivityViewModel
        extends ViewModel {

    private final List<MenuHandler> menuHandlers = new ArrayList<>();
    /** the list with all fields. */
    private final List<Field<?, ? extends View>> fields = new ArrayList<>();

    private Style style;

    private boolean modified;

    /**
     * Part of the fragment result data.
     * This informs the BoB whether it should rebuild its list.
     *
     * @return {@code true} if the book was changed and successfully saved.
     */
    public boolean isModified() {
        return modified;
    }

    void updateFragmentResult() {
        modified = true;
    }

    /**
     * Pseudo constructor.
     *
     * @param context current context
     * @param args    Bundle with arguments
     */
    public void init(@NonNull final Context context,
                     @NonNull final Bundle args) {
        if (style == null) {
            // the style can be 'null' here. If so, the default one will be used.
            final String styleUuid = args.getString(Style.BKEY_UUID);
            style = ServiceLocator.getInstance().getStyles().getStyleOrDefault(context, styleUuid);

            initFields(context);

            menuHandlers.add(new ViewBookOnWebsiteHandler());
            menuHandlers.add(new AmazonHandler());
        }
    }

    @NonNull
    public Style getStyle() {
        return style;
    }

    @NonNull
    List<MenuHandler> getMenuHandlers() {
        return menuHandlers;
    }

    @NonNull
    List<Field<?, ? extends View>> getFields() {
        return fields;
    }

    @NonNull
    Optional<Field<?, ? extends View>> getField(@IdRes final int id) {
        return fields.stream()
                     .filter(field -> field.getFieldViewId() == id)
                     .findFirst();
    }

    /**
     * Return the Field associated with the passed ID.
     *
     * @param <T> type of Field value.
     * @param <V> type of View for this field.
     * @param id  Field/View ID
     *
     * @return Associated Field.
     *
     * @throws IllegalArgumentException if the field id was not found
     */
    @NonNull
    <T, V extends View> Field<T, V> requireField(@IdRes final int id) {
        //noinspection unchecked
        return (Field<T, V>) fields
                .stream()
                .filter(field -> field.getFieldViewId() == id)
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Field not found: " + id));
    }

    private void initFields(@NonNull final Context context) {

        final Locale userLocale = context.getResources().getConfiguration().getLocales().get(0);
        // These FieldFormatters are shared between multiple fields.
        final FieldFormatter<String> dateFormatter = new DateFieldFormatter(userLocale);
        final FieldFormatter<Money> moneyFormatter = new MoneyFormatter(userLocale);
        final FieldFormatter<String> notesFormatter = new HtmlFormatter<>(true, true);
        final ListFormatter<Entity> normalDetailListFormatter =
                new ListFormatter<>(Details.Normal, style);
        final ListFormatter<Entity> fullDetailListFormatter =
                new ListFormatter<>(Details.Full, style);

        // book fields
        fields.add(new TextViewField<>(FragmentId.Main, R.id.title, DBKey.TITLE));

        fields.add(new TextViewField<>(FragmentId.Main, R.id.author, Book.BKEY_AUTHOR_LIST,
                                       DBKey.FK_AUTHOR,
                                       fullDetailListFormatter)
                           .addRelatedViews(R.id.lbl_author));

        fields.add(new TextViewField<>(FragmentId.Main, R.id.series_title, Book.BKEY_SERIES_LIST,
                                       DBKey.SERIES_TITLE,
                                       fullDetailListFormatter)
                           .addRelatedViews(R.id.lbl_series));

        fields.add(new TextViewField<>(FragmentId.Main, R.id.isbn, DBKey.KEY_ISBN)
                           .addRelatedViews(R.id.lbl_isbn));

        fields.add(new TextViewField<>(FragmentId.Main, R.id.description, DBKey.DESCRIPTION,
                                       notesFormatter));

        fields.add(new TextViewField<>(FragmentId.Main, R.id.genre, DBKey.GENRE)
                           .addRelatedViews(R.id.lbl_genre));

        fields.add(new TextViewField<>(FragmentId.Main, R.id.language, DBKey.LANGUAGE,
                                       new LanguageFormatter(userLocale))
                           .addRelatedViews(R.id.lbl_language));

        fields.add(new TextViewField<>(FragmentId.Main, R.id.pages, DBKey.PAGES,
                                       new PagesFormatter()));

        fields.add(new TextViewField<>(FragmentId.Main, R.id.format, DBKey.BOOK_FORMAT));
        fields.add(new TextViewField<>(FragmentId.Main, R.id.color, DBKey.COLOR));

        fields.add(new TextViewField<>(FragmentId.Main, R.id.publisher, Book.BKEY_PUBLISHER_LIST,
                                       DBKey.PUBLISHER_NAME,
                                       normalDetailListFormatter));

        fields.add(new TextViewField<>(FragmentId.Main, R.id.date_published,
                                       DBKey.DATE_BOOK_PUBLICATION,
                                       dateFormatter)
                           .addRelatedViews(R.id.lbl_date_published));

        fields.add(new TextViewField<>(FragmentId.Main, R.id.first_publication,
                                       DBKey.DATE_FIRST_PUBLICATION,
                                       dateFormatter)
                           .addRelatedViews(R.id.lbl_first_publication));

        fields.add(new TextViewField<>(FragmentId.Main, R.id.edition, DBKey.BITMASK_EDITION,
                                       new BitmaskFormatter(Details.Normal,
                                                            Book.Edition.getEditions(context)))
                           .addRelatedViews(R.id.lbl_edition));

        fields.add(new TextViewField<>(FragmentId.Main, R.id.print_run, DBKey.PRINT_RUN));

        fields.add(new TextViewField<>(FragmentId.Main, R.id.price_listed, DBKey.PRICE_LISTED,
                                       moneyFormatter)
                           .addRelatedViews(R.id.lbl_price_listed));


        // Personal fields

        fields.add(new TextViewField<>(FragmentId.Main, R.id.bookshelves, Book.BKEY_BOOKSHELF_LIST,
                                       DBKey.FK_BOOKSHELF,
                                       normalDetailListFormatter)
                           .addRelatedViews(R.id.lbl_bookshelves));

        fields.add(new TextViewField<>(FragmentId.Main, R.id.date_acquired, DBKey.DATE_ACQUIRED,
                                       dateFormatter)
                           .addRelatedViews(R.id.lbl_date_acquired));

        fields.add(new TextViewField<>(FragmentId.Main, R.id.location, DBKey.LOCATION)
                           .addRelatedViews(R.id.lbl_location, R.id.lbl_location_long));

        fields.add(new RatingBarField(FragmentId.Main, R.id.rating, DBKey.RATING));

        fields.add(new TextViewField<>(FragmentId.Main, R.id.condition, DBKey.BOOK_CONDITION,
                                       new StringArrayResFormatter(
                                               context, R.array.conditions_book))
                           .addRelatedViews(R.id.lbl_condition));

        fields.add(new TextViewField<>(FragmentId.Main, R.id.condition_cover,
                                       DBKey.BOOK_CONDITION_COVER,
                                       new StringArrayResFormatter(
                                               context, R.array.conditions_dust_cover))
                           .addRelatedViews(R.id.lbl_condition_cover));

        fields.add(new TextViewField<>(FragmentId.Main, R.id.notes, DBKey.PERSONAL_NOTES,
                                       notesFormatter)
                           .addRelatedViews(R.id.lbl_notes));

        fields.add(new BooleanIndicatorField(FragmentId.Main, R.id.read, DBKey.READ__BOOL));

        fields.add(new TextViewField<>(FragmentId.Main, R.id.read_start, DBKey.READ_START__DATE,
                                       dateFormatter)
                           .addRelatedViews(R.id.lbl_read_start));

        fields.add(new TextViewField<>(FragmentId.Main, R.id.read_end, DBKey.READ_END__DATE,
                                       dateFormatter)
                           .addRelatedViews(R.id.lbl_read_end));

        fields.add(new BooleanIndicatorField(FragmentId.Main, R.id.signed, DBKey.SIGNED__BOOL));

        fields.add(new TextViewField<>(FragmentId.Main, R.id.price_paid, DBKey.PRICE_PAID,
                                       moneyFormatter)
                           .addRelatedViews(R.id.lbl_price_paid));
    }
}
