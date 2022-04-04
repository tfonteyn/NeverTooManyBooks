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
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.View;

import androidx.annotation.IdRes;
import androidx.annotation.IntRange;
import androidx.annotation.NonNull;
import androidx.lifecycle.ViewModel;
import androidx.preference.PreferenceManager;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

import com.hardbacknutter.nevertoomanybooks.R;
import com.hardbacknutter.nevertoomanybooks.ServiceLocator;
import com.hardbacknutter.nevertoomanybooks.booklist.style.ListStyle;
import com.hardbacknutter.nevertoomanybooks.database.DBKey;
import com.hardbacknutter.nevertoomanybooks.entities.Book;
import com.hardbacknutter.nevertoomanybooks.entities.Details;
import com.hardbacknutter.nevertoomanybooks.entities.Entity;
import com.hardbacknutter.nevertoomanybooks.fields.Field;
import com.hardbacknutter.nevertoomanybooks.fields.FieldBuilder;
import com.hardbacknutter.nevertoomanybooks.fields.accessors.BooleanIndicatorAccessor;
import com.hardbacknutter.nevertoomanybooks.fields.accessors.RatingBarAccessor;
import com.hardbacknutter.nevertoomanybooks.fields.accessors.TextViewAccessor;
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
 */
public class ShowBookDetailsActivityViewModel
        extends ViewModel {

    private final List<MenuHandler> mMenuHandlers = new ArrayList<>();
    /** the list with all fields. */
    private final List<Field<?, ? extends View>> mFields = new ArrayList<>();

    private ListStyle mStyle;

    private boolean mModified;

    /**
     * Part of the fragment result data.
     * This informs the BoB whether it should rebuild its list.
     *
     * @return {@code true} if the book was changed and successfully saved.
     */
    public boolean isModified() {
        return mModified;
    }

    void updateFragmentResult() {
        mModified = true;
    }

    /**
     * Pseudo constructor.
     *
     * @param context current context
     * @param args    Bundle with arguments
     */
    public void init(@NonNull final Context context,
                     @NonNull final Bundle args) {
        if (mStyle == null) {
            // the style can be 'null' here. If so, the default one will be used.
            final String styleUuid = args.getString(ListStyle.BKEY_UUID);
            mStyle = ServiceLocator.getInstance().getStyles().getStyleOrDefault(context, styleUuid);

            initFields(context);

            mMenuHandlers.add(new ViewBookOnWebsiteHandler());
            mMenuHandlers.add(new AmazonHandler());
        }
    }

    @NonNull
    public ListStyle getStyle() {
        return mStyle;
    }

    @NonNull
    List<MenuHandler> getMenuHandlers() {
        return mMenuHandlers;
    }

    boolean useLoanee(@NonNull final Context context) {
        final SharedPreferences global = PreferenceManager.getDefaultSharedPreferences(context);
        return DBKey.isUsed(global, DBKey.KEY_LOANEE);
    }

    boolean useToc(@NonNull final Context context) {
        final SharedPreferences global = PreferenceManager.getDefaultSharedPreferences(context);
        return DBKey.isUsed(global, DBKey.BITMASK_TOC);
    }

    /**
     * Check if this cover should should be shown / is used.
     * <p>
     * The order we use to decide:
     * <ol>
     *     <li>Global visibility is set to HIDE -> return {@code false}</li>
     *     <li>return the visibility as set in the style.</li>
     * </ol>
     *
     * @param global Global preferences
     * @param cIdx   0..n image index
     *
     * @return {@code true} if in use
     */
    boolean isCoverUsed(@NonNull final SharedPreferences global,
                        @IntRange(from = 0, to = 1) final int cIdx) {

        // Globally disabled overrules style setting
        if (!DBKey.isUsed(global, DBKey.COVER_IS_USED[cIdx])) {
            return false;
        }

        // let the style decide
        return mStyle.getDetailScreenBookFields().isShowCover(global, cIdx);
    }

    @NonNull
    List<Field<?, ? extends View>> getFields() {
        return mFields;
    }

    @NonNull
    Optional<Field<?, ? extends View>> getField(@IdRes final int id) {
        return mFields.stream()
                      .filter(field -> field.getId() == id)
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
        return (Field<T, V>) mFields
                .stream()
                .filter(field -> field.getId() == id)
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Field not found: " + id));
    }

    private void initFields(@NonNull final Context context) {

        final Locale userLocale = context.getResources().getConfiguration().getLocales().get(0);
        // These FieldFormatters are shared between multiple fields.
        final FieldFormatter<String> dateFormatter = new DateFieldFormatter(userLocale);
        final FieldFormatter<Money> moneyFormatter = new MoneyFormatter(userLocale);
        final FieldFormatter<String> notesFormatter = new HtmlFormatter<>(true, true);
        final ListFormatter<Entity> normalDetailListFormatter = new ListFormatter<>();
        final ListFormatter<Entity> fullDetailListFormatter = new ListFormatter<>(Details.Full);

        // book fields
        mFields.add(new FieldBuilder<>(R.id.title, DBKey.KEY_TITLE,
                                       new TextViewAccessor<>())
                            .build());

        mFields.add(new FieldBuilder<>(R.id.author, Book.BKEY_AUTHOR_LIST,
                                       new TextViewAccessor<>(fullDetailListFormatter))
                            .setEntityKey(DBKey.FK_AUTHOR)
                            .setRelatedFields(R.id.lbl_author)
                            .build());

        mFields.add(new FieldBuilder<>(R.id.series_title, Book.BKEY_SERIES_LIST,
                                       new TextViewAccessor<>(fullDetailListFormatter))
                            .setEntityKey(DBKey.KEY_SERIES_TITLE)
                            .setRelatedFields(R.id.lbl_series)
                            .build());

        mFields.add(new FieldBuilder<>(R.id.isbn, DBKey.KEY_ISBN,
                                       new TextViewAccessor<>())
                            .setRelatedFields(R.id.lbl_isbn)
                            .build());

        mFields.add(new FieldBuilder<>(R.id.description, DBKey.KEY_DESCRIPTION,
                                       new TextViewAccessor<>(notesFormatter))
                            .build());

        mFields.add(new FieldBuilder<>(R.id.genre, DBKey.KEY_GENRE,
                                       new TextViewAccessor<>())
                            .setRelatedFields(R.id.lbl_genre)
                            .build());

        mFields.add(new FieldBuilder<>(R.id.language, DBKey.KEY_LANGUAGE,
                                       new TextViewAccessor<>(new LanguageFormatter(userLocale)))
                            .setRelatedFields(R.id.lbl_language)
                            .build());

        mFields.add(new FieldBuilder<>(R.id.pages, DBKey.KEY_PAGES,
                                       new TextViewAccessor<>(new PagesFormatter()))
                            .build());

        mFields.add(new FieldBuilder<>(R.id.format, DBKey.KEY_FORMAT,
                                       new TextViewAccessor<>())
                            .build());

        mFields.add(new FieldBuilder<>(R.id.color, DBKey.KEY_COLOR,
                                       new TextViewAccessor<>())
                            .build());

        mFields.add(new FieldBuilder<>(R.id.publisher, Book.BKEY_PUBLISHER_LIST,
                                       new TextViewAccessor<>(normalDetailListFormatter))
                            .setEntityKey(DBKey.KEY_PUBLISHER_NAME)
                            .build());

        mFields.add(new FieldBuilder<>(R.id.date_published, DBKey.DATE_BOOK_PUBLICATION,
                                       new TextViewAccessor<>(dateFormatter))
                            .setRelatedFields(R.id.lbl_date_published)
                            .build());

        mFields.add(new FieldBuilder<>(R.id.first_publication, DBKey.DATE_FIRST_PUBLICATION,
                                       new TextViewAccessor<>(dateFormatter))
                            .setRelatedFields(R.id.lbl_first_publication)
                            .build());

        mFields.add(new FieldBuilder<>(R.id.edition, DBKey.BITMASK_EDITION,
                                       new TextViewAccessor<>(new BitmaskFormatter(
                                               Details.Normal,
                                               Book.Edition.getEditions(context))))
                            .setRelatedFields(R.id.lbl_edition)
                            .build());

        mFields.add(new FieldBuilder<>(R.id.print_run, DBKey.KEY_PRINT_RUN,
                                       new TextViewAccessor<>())
                            .build());

        mFields.add(new FieldBuilder<>(R.id.price_listed, DBKey.PRICE_LISTED,
                                       new TextViewAccessor<>(moneyFormatter))
                            .setRelatedFields(R.id.lbl_price_listed)
                            .build());


        // Personal fields

        mFields.add(new FieldBuilder<>(R.id.bookshelves, Book.BKEY_BOOKSHELF_LIST,
                                       new TextViewAccessor<>(normalDetailListFormatter))
                            .setEntityKey(DBKey.FK_BOOKSHELF)
                            .setRelatedFields(R.id.lbl_bookshelves)
                            .build());

        mFields.add(new FieldBuilder<>(R.id.date_acquired, DBKey.DATE_ACQUIRED,
                                       new TextViewAccessor<>(dateFormatter))
                            .setRelatedFields(R.id.lbl_date_acquired)
                            .build());

        mFields.add(new FieldBuilder<>(R.id.location, DBKey.KEY_LOCATION,
                                       new TextViewAccessor<>())
                            .setRelatedFields(R.id.lbl_location, R.id.lbl_location_long)
                            .build());

        mFields.add(new FieldBuilder<>(R.id.rating, DBKey.KEY_RATING,
                                       new RatingBarAccessor(false))
                            .build());

        mFields.add(new FieldBuilder<>(R.id.condition, DBKey.KEY_BOOK_CONDITION,
                                       new TextViewAccessor<>(new StringArrayResFormatter(
                                               context, R.array.conditions_book)))
                            .setRelatedFields(R.id.lbl_condition)
                            .build());

        mFields.add(new FieldBuilder<>(R.id.condition_cover, DBKey.KEY_BOOK_CONDITION_COVER,
                                       new TextViewAccessor<>(new StringArrayResFormatter(
                                               context, R.array.conditions_dust_cover)))
                            .setRelatedFields(R.id.lbl_condition_cover)
                            .build());

        mFields.add(new FieldBuilder<>(R.id.notes, DBKey.KEY_PRIVATE_NOTES,
                                       new TextViewAccessor<>(notesFormatter))
                            .setRelatedFields(R.id.lbl_notes)
                            .build());

        mFields.add(new FieldBuilder<>(R.id.read, DBKey.BOOL_READ,
                                       new BooleanIndicatorAccessor())
                            .build());

        mFields.add(new FieldBuilder<>(R.id.read_start, DBKey.DATE_READ_START,
                                       new TextViewAccessor<>(dateFormatter))
                            .setRelatedFields(R.id.lbl_read_start)
                            .build());

        mFields.add(new FieldBuilder<>(R.id.read_end, DBKey.DATE_READ_END,
                                       new TextViewAccessor<>(dateFormatter))
                            .setRelatedFields(R.id.lbl_read_end)
                            .build());

        mFields.add(new FieldBuilder<>(R.id.signed, DBKey.BOOL_SIGNED,
                                       new BooleanIndicatorAccessor())
                            .build());

        mFields.add(new FieldBuilder<>(R.id.price_paid, DBKey.PRICE_PAID,
                                       new TextViewAccessor<>(moneyFormatter))
                            .setRelatedFields(R.id.lbl_price_paid)
                            .build());
    }
}
