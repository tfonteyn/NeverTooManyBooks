/*
 * @Copyright 2018-2026 HardBackNutter
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
import android.os.LocaleList;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import com.hardbacknutter.nevertoomanybooks.R;
import com.hardbacknutter.nevertoomanybooks.ServiceLocator;
import com.hardbacknutter.nevertoomanybooks.booklist.style.Style;
import com.hardbacknutter.nevertoomanybooks.bookreadstatus.BookReadStatusViewModel;
import com.hardbacknutter.nevertoomanybooks.bookreadstatus.ReadingProgress;
import com.hardbacknutter.nevertoomanybooks.core.parsers.RealNumberParser;
import com.hardbacknutter.nevertoomanybooks.core.utils.LocaleListUtils;
import com.hardbacknutter.nevertoomanybooks.core.utils.Money;
import com.hardbacknutter.nevertoomanybooks.database.DBKey;
import com.hardbacknutter.nevertoomanybooks.database.dao.BookDao;
import com.hardbacknutter.nevertoomanybooks.database.dao.LoaneeDao;
import com.hardbacknutter.nevertoomanybooks.entities.Author;
import com.hardbacknutter.nevertoomanybooks.entities.Book;
import com.hardbacknutter.nevertoomanybooks.entities.DataHolder;
import com.hardbacknutter.nevertoomanybooks.entities.Details;
import com.hardbacknutter.nevertoomanybooks.entities.Entity;
import com.hardbacknutter.nevertoomanybooks.fields.BooleanIndicatorField;
import com.hardbacknutter.nevertoomanybooks.fields.Field;
import com.hardbacknutter.nevertoomanybooks.fields.RatingBarField;
import com.hardbacknutter.nevertoomanybooks.fields.TextViewField;
import com.hardbacknutter.nevertoomanybooks.fields.formatters.BitmaskFormatter;
import com.hardbacknutter.nevertoomanybooks.fields.formatters.ClickableListFormatter;
import com.hardbacknutter.nevertoomanybooks.fields.formatters.DateFieldFormatter;
import com.hardbacknutter.nevertoomanybooks.fields.formatters.FieldFormatter;
import com.hardbacknutter.nevertoomanybooks.fields.formatters.HtmlFormatter;
import com.hardbacknutter.nevertoomanybooks.fields.formatters.LanguageFormatter;
import com.hardbacknutter.nevertoomanybooks.fields.formatters.ListFormatter;
import com.hardbacknutter.nevertoomanybooks.fields.formatters.MoneyFormatter;
import com.hardbacknutter.nevertoomanybooks.fields.formatters.PagesFormatter;
import com.hardbacknutter.nevertoomanybooks.fields.formatters.StringArrayResFormatter;
import com.hardbacknutter.nevertoomanybooks.menus.MenuHandler;
import com.hardbacknutter.nevertoomanybooks.menus.SiteSearchMenuHandler;
import com.hardbacknutter.nevertoomanybooks.menus.ViewBookOnSiteMenuHandler;
import com.hardbacknutter.nevertoomanybooks.utils.Languages;

/**
 * Shared data between details and Read fragments.
 * <p>
 * FIXME: cleanup unneeded addRelatedViews for labels
 */
public class ShowBookDetailsViewModel
        extends ViewModel
        implements BookReadStatusViewModel {

    private static final String BOOK_NOT_LOADED_YET = "Book not loaded yet";

    private final MutableLiveData<Book> onBookLoaded = new MutableLiveData<>();
    private final MutableLiveData<Boolean> onReadStatusUpdateUI = new MutableLiveData<>();

    /** key: the field-key. */
    private final Map<String, Field<?, ? extends View>> fields = new HashMap<>();

    private List<MenuHandler<DataHolder>> menuHandlers;

    private boolean embedded;

    /**
     * Initially set from the args, but can be overwritten by {@link #displayBook(long)}
     * when we're in embedded mode.
     */
    private long bookId;
    @Nullable
    private Book book;

    private BookDao bookDao;
    private LoaneeDao loaneeDao;
    private RealNumberParser realNumberParser;

    /**
     * Pseudo constructor.
     *
     * @param context current context
     * @param args    Bundle with arguments
     * @param style   to apply
     *
     * @throws IllegalArgumentException (debug) if the args did not contain a book id
     */
    @VisibleForTesting(otherwise = VisibleForTesting.PACKAGE_PRIVATE)
    public void init(@NonNull final Context context,
                     @NonNull final Bundle args,
                     @NonNull final Style style) {
        if (bookDao == null) {
            bookDao = ServiceLocator.getInstance().getBookDao();
            loaneeDao = ServiceLocator.getInstance().getLoaneeDao();

            menuHandlers = List.of(new ViewBookOnSiteMenuHandler(),
                                   new SiteSearchMenuHandler());

            final LocaleList userLocales = context.getResources().getConfiguration().getLocales();
            final List<Locale> allLocales = LocaleListUtils.asList(userLocales);
            realNumberParser = new RealNumberParser(allLocales);

            initFields(context, style, ServiceLocator.getInstance().getLanguages());
        }

        // Always refresh these
        embedded = args.getBoolean(ShowBookDetailsFragment.BKEY_EMBEDDED, false);
        bookId = args.getLong(DBKey.FK_BOOK, 0);
        if (bookId <= 0) {
            throw new IllegalArgumentException(DBKey.FK_BOOK);
        }
    }

    @NonNull
    public RealNumberParser getRealNumberParser() {
        return realNumberParser;
    }

    /**
     * Observable - triggers a UI update for the given {@link Book}.
     *
     * @return book
     */
    @NonNull
    LiveData<Book> onBookLoaded() {
        return onBookLoaded;
    }

    /**
     * Are we running in embedded mode.
     *
     * @return flag
     */
    public boolean isEmbedded() {
        return embedded;
    }

    /**
     * Tests ONLY. Load the book without triggering onBookLoaded.
     */
    @VisibleForTesting
    public void loadBook() {
        book = Book.from(bookId);
    }

    /**
     * (Re)load the data for the current book and trigger a UI update.
     */
    void displayBook() {
        book = Book.from(bookId);
        onBookLoaded.setValue(book);
        updateReadStatus(false);
    }

    /**
     * Load the data for the given book id and trigger a UI update.
     *
     * @param bookId to display
     */
    public void displayBook(final long bookId) {
        this.bookId = bookId;
        displayBook();
    }

    /**
     * Get the currently displayed book.
     *
     * @return the book
     */
    @NonNull
    public Book getBook() {
        Objects.requireNonNull(book, BOOK_NOT_LOADED_YET);
        return book;
    }

    /**
     * The book was returned, remove the loanee.
     * <p>
     * <strong>Important:</strong> we're not using {@link #onBookLoaded}.
     * The caller MUST manually update the display and result-data.
     *
     * @return {@code false} on any failure
     */
    @SuppressWarnings("UnusedReturnValue")
    boolean deleteLoan() {
        Objects.requireNonNull(book, BOOK_NOT_LOADED_YET);
        return loaneeDao.delete(book);
    }

    /**
     * Delete the current book.
     * <p>
     * <strong>Important:</strong> we're not using {@link #onBookLoaded}.
     * The caller MUST manually update the display and result-data.
     *
     * @return {@code false} on any failure
     */
    @SuppressWarnings("UnusedReturnValue")
    boolean deleteBook() {
        Objects.requireNonNull(book, BOOK_NOT_LOADED_YET);

        if (bookDao.delete(book)) {
            book = null;
            return true;
        } else {
            return false;
        }
    }

    @Override
    public boolean isRead() {
        Objects.requireNonNull(book, BOOK_NOT_LOADED_YET);
        return book.isRead();
    }

    @Override
    public void setReadNow(final boolean read) {
        Objects.requireNonNull(book, BOOK_NOT_LOADED_YET);
        bookDao.setRead(book, read);
        updateReadStatus(true);
    }

    @Override
    public void updateReadStatus(final boolean statusModified) {
        onReadStatusUpdateUI.setValue(statusModified);
    }

    @Override
    @NonNull
    public ReadingProgress getReadingProgress() {
        Objects.requireNonNull(book, BOOK_NOT_LOADED_YET);
        return book.getReadingProgress();
    }

    @Override
    public void setReadingProgress(@NonNull final ReadingProgress readingProgress) {
        Objects.requireNonNull(book, BOOK_NOT_LOADED_YET);
        bookDao.setReadingProgress(book, readingProgress);
        updateReadStatus(true);
    }

    @Override
    @NonNull
    public LiveData<Boolean> onUpdateReadStatus() {
        return onReadStatusUpdateUI;
    }


    @NonNull
    List<MenuHandler<DataHolder>> getMenuHandlers() {
        return menuHandlers;
    }

    @NonNull
    Collection<Field<?, ? extends View>> getFields() {
        return fields.values();
    }

    @NonNull
    <T, V extends View> Optional<Field<T, V>> getField(@NonNull final String key) {
        //noinspection unchecked
        final Field<T, V> field = (Field<T, V>) fields.get(key);
        return field == null ? Optional.empty() : Optional.of(field);
    }

    private void addField(@NonNull final Field<?, ? extends View> field) {
        fields.put(field.getFieldKey(), field);
    }

    private void initFields(@NonNull final Context context,
                            @NonNull final Style style,
                            @NonNull final Languages languages) {
        final Locale userLocale = context.getResources().getConfiguration().getLocales().get(0);

        // These FieldFormatters are shared between multiple fields.
        final FieldFormatter<String> dateFormatter = new DateFieldFormatter(userLocale, false);
        final FieldFormatter<String> dateUtcFormatter = new DateFieldFormatter(userLocale, true);
        final FieldFormatter<Money> moneyFormatter = new MoneyFormatter(userLocale);
        final FieldFormatter<String> notesFormatter = new HtmlFormatter<String>()
                .setEnableLinks(true)
                .setConvertLineFeeds(true);
        final FieldFormatter<String> languageFormatter =
                new LanguageFormatter(userLocale, languages);
        final ListFormatter<Entity> normalDetailListFormatter =
                new ListFormatter<>(Details.Normal, style);
        final ListFormatter<Entity> fullDetailListFormatter =
                new ListFormatter<>(Details.Full, style);

        // book fields
        addField(new TextViewField<>(R.id.title, DBKey.TITLE));
        addField(new TextViewField<>(R.id.original_title,
                                       DBKey.TRANSLATION_ORIGINAL_TITLE));
        addField(new TextViewField<>(R.id.original_language,
                                       DBKey.TRANSLATION_ORIGINAL_LANGUAGE,
                                       languageFormatter));

        addField(new TextViewField<>(R.id.author, Book.BKEY_AUTHOR_LIST,
                                       DBKey.FK_AUTHOR,
                                       new ClickableListFormatter<Author>(context, (c, author) ->
                                               author.getLabel(c, Details.Full, style))));

        addField(new TextViewField<>(R.id.series_title, Book.BKEY_SERIES_LIST,
                                       DBKey.FK_SERIES,
                                       fullDetailListFormatter)
                           .addRelatedViews(R.id.lbl_series));

        addField(new TextViewField<>(R.id.isbn, DBKey.ISBN)
                           .addRelatedViews(R.id.lbl_isbn));

        addField(new TextViewField<>(R.id.description, DBKey.DESCRIPTION,
                                       notesFormatter)
                           // The description_scroller is not present on all devices.
                           // Do NOT replace it with "description_layout" !!!
                           .addRelatedViews(R.id.description_scroller));

        addField(new TextViewField<>(R.id.language, DBKey.LANGUAGE,
                                       languageFormatter)
                           .addRelatedViews(R.id.lbl_language));

        addField(new TextViewField<>(R.id.pages, DBKey.PAGES,
                                       new PagesFormatter()));

        addField(new TextViewField<>(R.id.format, DBKey.FORMAT));
        addField(new TextViewField<>(R.id.color, DBKey.COLOR));

        addField(new TextViewField<>(R.id.publisher, Book.BKEY_PUBLISHER_LIST,
                                       DBKey.FK_PUBLISHER,
                                       normalDetailListFormatter));

        addField(new TextViewField<>(R.id.date_published,
                                       DBKey.PUBLICATION_DATE,
                                       dateFormatter)
                           .addRelatedViews(R.id.lbl_date_published));

        addField(new TextViewField<>(R.id.first_publication,
                                       DBKey.FIRST_PUBLICATION_DATE,
                                       dateFormatter)
                           .addRelatedViews(R.id.lbl_first_publication));

        addField(new TextViewField<>(R.id.edition_flags, DBKey.EDITION_FLAGS,
                                     new BitmaskFormatter(Details.Normal, Book.Edition::getAll))
                           .addRelatedViews(R.id.lbl_edition));

        addField(new TextViewField<>(R.id.edition_info, DBKey.EDITION_INFO));

        addField(new TextViewField<>(R.id.print_run, DBKey.PRINT_RUN));

        addField(new TextViewField<>(R.id.price_listed, DBKey.PRICE_LISTED,
                                       moneyFormatter)
                           .addRelatedViews(R.id.lbl_price_listed));


        // Personal fields

        addField(new TextViewField<>(R.id.bookshelves, Book.BKEY_BOOKSHELF_LIST,
                                       DBKey.FK_BOOKSHELF,
                                       normalDetailListFormatter)
                           .addRelatedViews(R.id.lbl_bookshelves));

        addField(new TextViewField<>(R.id.tags, Book.BKEY_TAG_LIST,
                                       DBKey.FK_TAG,
                                       normalDetailListFormatter)
                           .addRelatedViews(R.id.lbl_tags));

        addField(new TextViewField<>(R.id.date_acquired,
                                       DBKey.DATE_ACQUIRED,
                                       dateFormatter)
                           .addRelatedViews(R.id.lbl_date_acquired));

        addField(new TextViewField<>(R.id.location,
                                       DBKey.LOCATION)
                           .addRelatedViews(R.id.lbl_location, R.id.lbl_location_long));

        addField(new RatingBarField(R.id.rating,
                                      DBKey.RATING));

        addField(new TextViewField<>(R.id.condition,
                                       DBKey.CONDITION_BOOK,
                                       new StringArrayResFormatter(
                                               context, R.array.lbl_book_condition))
                           .addRelatedViews(R.id.lbl_condition));

        addField(new TextViewField<>(R.id.condition_cover,
                                       DBKey.CONDITION_COVER,
                                       new StringArrayResFormatter(
                                               context, R.array.lbl_dust_cover_condition))
                           .addRelatedViews(R.id.lbl_condition_cover));

        addField(new TextViewField<>(R.id.notes,
                                       DBKey.PERSONAL_NOTES,
                                       notesFormatter)
                           .addRelatedViews(R.id.lbl_notes));

        addField(new TextViewField<>(R.id.read_start,
                                       DBKey.READ_START__DATE,
                                       dateFormatter)
                           .addRelatedViews(R.id.lbl_read_start));

        addField(new TextViewField<>(R.id.read_end,
                                       DBKey.READ_END__DATE,
                                       dateFormatter)
                           .addRelatedViews(R.id.lbl_read_end));

        addField(new BooleanIndicatorField(R.id.signed,
                                             DBKey.SIGNED__BOOL));

        addField(new TextViewField<>(R.id.price_paid,
                                       DBKey.PRICE_PAID,
                                       moneyFormatter)
                           .addRelatedViews(R.id.lbl_price_paid));


        addField(new TextViewField<>(R.id.date_added,
                                       DBKey.DATE_ADDED__UTC,
                                       dateUtcFormatter)
                           .addRelatedViews(R.id.lbl_date_added));

        addField(new TextViewField<>(R.id.date_last_updated,
                                       DBKey.DATE_LAST_UPDATED__UTC,
                                       dateUtcFormatter)
                           .addRelatedViews(R.id.lbl_date_last_updated));
    }
}
