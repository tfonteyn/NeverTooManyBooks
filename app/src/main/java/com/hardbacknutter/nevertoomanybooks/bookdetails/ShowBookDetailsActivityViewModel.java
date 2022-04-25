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
        return (Field<T, V>) mFields
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
        final ListFormatter<Entity> normalDetailListFormatter = new ListFormatter<>(Details.Normal);
        final ListFormatter<Entity> fullDetailListFormatter = new ListFormatter<>(Details.Full);

        // book fields
        mFields.add(new TextViewField<>(FragmentId.Main, R.id.title, DBKey.KEY_TITLE));

        mFields.add(new TextViewField<>(FragmentId.Main, R.id.author, Book.BKEY_AUTHOR_LIST,
                                        DBKey.FK_AUTHOR,
                                        fullDetailListFormatter)
                            .addRelatedViews(R.id.lbl_author));

        mFields.add(new TextViewField<>(FragmentId.Main, R.id.series_title, Book.BKEY_SERIES_LIST,
                                        DBKey.KEY_SERIES_TITLE,
                                        fullDetailListFormatter)
                            .addRelatedViews(R.id.lbl_series));

        mFields.add(new TextViewField<>(FragmentId.Main, R.id.isbn, DBKey.KEY_ISBN)
                            .addRelatedViews(R.id.lbl_isbn));

        mFields.add(new TextViewField<>(FragmentId.Main, R.id.description, DBKey.KEY_DESCRIPTION,
                                        notesFormatter));

        mFields.add(new TextViewField<>(FragmentId.Main, R.id.genre, DBKey.KEY_GENRE)
                            .addRelatedViews(R.id.lbl_genre));

        mFields.add(new TextViewField<>(FragmentId.Main, R.id.language, DBKey.KEY_LANGUAGE,
                                        new LanguageFormatter(userLocale))
                            .addRelatedViews(R.id.lbl_language));

        mFields.add(new TextViewField<>(FragmentId.Main, R.id.pages, DBKey.KEY_PAGES,
                                        new PagesFormatter()));

        mFields.add(new TextViewField<>(FragmentId.Main, R.id.format, DBKey.KEY_FORMAT));
        mFields.add(new TextViewField<>(FragmentId.Main, R.id.color, DBKey.KEY_COLOR));

        mFields.add(new TextViewField<>(FragmentId.Main, R.id.publisher, Book.BKEY_PUBLISHER_LIST,
                                        DBKey.KEY_PUBLISHER_NAME,
                                        normalDetailListFormatter));

        mFields.add(new TextViewField<>(FragmentId.Main, R.id.date_published,
                                        DBKey.DATE_BOOK_PUBLICATION,
                                        dateFormatter)
                            .addRelatedViews(R.id.lbl_date_published));

        mFields.add(new TextViewField<>(FragmentId.Main, R.id.first_publication,
                                        DBKey.DATE_FIRST_PUBLICATION,
                                        dateFormatter)
                            .addRelatedViews(R.id.lbl_first_publication));

        mFields.add(new TextViewField<>(FragmentId.Main, R.id.edition, DBKey.BITMASK_EDITION,
                                        new BitmaskFormatter(Details.Normal,
                                                             Book.Edition.getEditions(context)))
                            .addRelatedViews(R.id.lbl_edition));

        mFields.add(new TextViewField<>(FragmentId.Main, R.id.print_run, DBKey.KEY_PRINT_RUN));

        mFields.add(new TextViewField<>(FragmentId.Main, R.id.price_listed, DBKey.PRICE_LISTED,
                                        moneyFormatter)
                            .addRelatedViews(R.id.lbl_price_listed));


        // Personal fields

        mFields.add(new TextViewField<>(FragmentId.Main, R.id.bookshelves, Book.BKEY_BOOKSHELF_LIST,
                                        DBKey.FK_BOOKSHELF,
                                        normalDetailListFormatter)
                            .addRelatedViews(R.id.lbl_bookshelves));

        mFields.add(new TextViewField<>(FragmentId.Main, R.id.date_acquired, DBKey.DATE_ACQUIRED,
                                        dateFormatter)
                            .addRelatedViews(R.id.lbl_date_acquired));

        mFields.add(new TextViewField<>(FragmentId.Main, R.id.location, DBKey.KEY_LOCATION)
                            .addRelatedViews(R.id.lbl_location, R.id.lbl_location_long));

        mFields.add(new RatingBarField(FragmentId.Main, R.id.rating, DBKey.KEY_RATING));

        mFields.add(new TextViewField<>(FragmentId.Main, R.id.condition, DBKey.KEY_BOOK_CONDITION,
                                        new StringArrayResFormatter(
                                                context, R.array.conditions_book))
                            .addRelatedViews(R.id.lbl_condition));

        mFields.add(new TextViewField<>(FragmentId.Main, R.id.condition_cover,
                                        DBKey.KEY_BOOK_CONDITION_COVER,
                                        new StringArrayResFormatter(
                                                context, R.array.conditions_dust_cover))
                            .addRelatedViews(R.id.lbl_condition_cover));

        mFields.add(new TextViewField<>(FragmentId.Main, R.id.notes, DBKey.KEY_PRIVATE_NOTES,
                                        notesFormatter)
                            .addRelatedViews(R.id.lbl_notes));

        mFields.add(new BooleanIndicatorField(FragmentId.Main, R.id.read, DBKey.BOOL_READ));

        mFields.add(new TextViewField<>(FragmentId.Main, R.id.read_start, DBKey.DATE_READ_START,
                                        dateFormatter)
                            .addRelatedViews(R.id.lbl_read_start));

        mFields.add(new TextViewField<>(FragmentId.Main, R.id.read_end, DBKey.DATE_READ_END,
                                        dateFormatter)
                            .addRelatedViews(R.id.lbl_read_end));

        mFields.add(new BooleanIndicatorField(FragmentId.Main, R.id.signed, DBKey.BOOL_SIGNED));

        mFields.add(new TextViewField<>(FragmentId.Main, R.id.price_paid, DBKey.PRICE_PAID,
                                        moneyFormatter)
                            .addRelatedViews(R.id.lbl_price_paid));
    }
}
