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
package com.hardbacknutter.nevertoomanybooks.bookedit;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.widget.EditText;
import android.widget.TextView;

import androidx.annotation.IdRes;
import androidx.annotation.LayoutRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.constraintlayout.helper.widget.Flow;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collection;
import java.util.EnumSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import com.hardbacknutter.nevertoomanybooks.R;
import com.hardbacknutter.nevertoomanybooks.ServiceLocator;
import com.hardbacknutter.nevertoomanybooks.activityresultcontracts.EditBookOutput;
import com.hardbacknutter.nevertoomanybooks.booklist.style.Style;
import com.hardbacknutter.nevertoomanybooks.bookreadstatus.BookReadStatusViewModel;
import com.hardbacknutter.nevertoomanybooks.bookreadstatus.ReadingProgress;
import com.hardbacknutter.nevertoomanybooks.core.database.DaoWriteException;
import com.hardbacknutter.nevertoomanybooks.core.parsers.DateParser;
import com.hardbacknutter.nevertoomanybooks.core.parsers.FullDateParser;
import com.hardbacknutter.nevertoomanybooks.core.parsers.ISODateParser;
import com.hardbacknutter.nevertoomanybooks.core.parsers.MoneyParser;
import com.hardbacknutter.nevertoomanybooks.core.parsers.RealNumberParser;
import com.hardbacknutter.nevertoomanybooks.core.storage.StorageException;
import com.hardbacknutter.nevertoomanybooks.core.utils.ISBN;
import com.hardbacknutter.nevertoomanybooks.core.utils.LocaleListUtils;
import com.hardbacknutter.nevertoomanybooks.database.DBKey;
import com.hardbacknutter.nevertoomanybooks.database.dao.AuthorDao;
import com.hardbacknutter.nevertoomanybooks.database.dao.BookDao;
import com.hardbacknutter.nevertoomanybooks.database.dao.BookshelfDao;
import com.hardbacknutter.nevertoomanybooks.database.dao.ColorDao;
import com.hardbacknutter.nevertoomanybooks.database.dao.FormatDao;
import com.hardbacknutter.nevertoomanybooks.database.dao.IdentifierDao;
import com.hardbacknutter.nevertoomanybooks.database.dao.LanguageDao;
import com.hardbacknutter.nevertoomanybooks.database.dao.LocationDao;
import com.hardbacknutter.nevertoomanybooks.database.dao.PublisherDao;
import com.hardbacknutter.nevertoomanybooks.database.dao.SeriesDao;
import com.hardbacknutter.nevertoomanybooks.database.dao.StylesHelper;
import com.hardbacknutter.nevertoomanybooks.database.dao.TagDao;
import com.hardbacknutter.nevertoomanybooks.database.dao.TocEntryDao;
import com.hardbacknutter.nevertoomanybooks.entities.Author;
import com.hardbacknutter.nevertoomanybooks.entities.Book;
import com.hardbacknutter.nevertoomanybooks.entities.Bookshelf;
import com.hardbacknutter.nevertoomanybooks.entities.DataHolder;
import com.hardbacknutter.nevertoomanybooks.entities.Details;
import com.hardbacknutter.nevertoomanybooks.entities.Entity;
import com.hardbacknutter.nevertoomanybooks.entities.EntityStage;
import com.hardbacknutter.nevertoomanybooks.entities.Identifier;
import com.hardbacknutter.nevertoomanybooks.entities.Publisher;
import com.hardbacknutter.nevertoomanybooks.entities.Series;
import com.hardbacknutter.nevertoomanybooks.entities.Tag;
import com.hardbacknutter.nevertoomanybooks.entities.TocEntry;
import com.hardbacknutter.nevertoomanybooks.fields.AutoCompleteTextField;
import com.hardbacknutter.nevertoomanybooks.fields.BitmaskChipGroupField;
import com.hardbacknutter.nevertoomanybooks.fields.CompoundButtonField;
import com.hardbacknutter.nevertoomanybooks.fields.DecimalEditTextField;
import com.hardbacknutter.nevertoomanybooks.fields.EditTextField;
import com.hardbacknutter.nevertoomanybooks.fields.EntityListDropDownMenuField;
import com.hardbacknutter.nevertoomanybooks.fields.Field;
import com.hardbacknutter.nevertoomanybooks.fields.FragmentId;
import com.hardbacknutter.nevertoomanybooks.fields.IdentifierField;
import com.hardbacknutter.nevertoomanybooks.fields.RatingBarEditField;
import com.hardbacknutter.nevertoomanybooks.fields.StringArrayDropDownMenuField;
import com.hardbacknutter.nevertoomanybooks.fields.TextViewField;
import com.hardbacknutter.nevertoomanybooks.fields.formatters.DateFieldFormatter;
import com.hardbacknutter.nevertoomanybooks.fields.formatters.DoubleNumberFormatter;
import com.hardbacknutter.nevertoomanybooks.fields.formatters.FieldFormatter;
import com.hardbacknutter.nevertoomanybooks.fields.formatters.LanguageFormatter;
import com.hardbacknutter.nevertoomanybooks.fields.formatters.ListFormatter;
import com.hardbacknutter.nevertoomanybooks.menus.MenuHandler;
import com.hardbacknutter.nevertoomanybooks.menus.SiteSearchMenuHandler;
import com.hardbacknutter.nevertoomanybooks.menus.ViewBookOnSiteMenuHandler;
import com.hardbacknutter.nevertoomanybooks.utils.Languages;

@SuppressWarnings("WeakerAccess")
public class EditBookViewModel
        extends ViewModel
        implements BookReadStatusViewModel {

    /**
     * ISBN/code Validity level.
     * Type: int
     *
     * @see ISBN.Validity
     */
    public static final String PK_EDIT_BOOK_ISBN_CHECKS = "edit.book.isbn.checks";

    /** the list with all fields. */
    private final List<Field<?, ? extends View>> allFields = new ArrayList<>();

    /** The key is the fragment tag. */
    private final Collection<FragmentId> fragmentsWithUnfinishedEdits =
            EnumSet.noneOf(FragmentId.class);
    private final MutableLiveData<Boolean> onReadStatusUpdateUI = new MutableLiveData<>();
    private List<MenuHandler<DataHolder>> menuHandlers;
    /**
     * The Book we're editing (creating/updating).
     * It will never be {@code null} after being loaded in {@link #init(Context, Bundle)}.
     */
    private Book book;
    /**
     * Field drop down lists.
     * Lists in database so far, we cache them for performance but only load
     * them when really needed.
     * <p>
     * FIXME: sometimes the user will have added a new item; and then accesses
     *  the list again... and it will not show. => we don't refresh these lists!
     */
    @Nullable
    private List<Tag> tags;
    /** Field drop down list. */
    @Nullable
    private List<String> locations;
    /** Field drop down list. */
    @Nullable
    private List<String> formats;
    /** Field drop down list. */
    @Nullable
    private List<String> colors;
    /** Field drop down list. */
    @Nullable
    private List<String> languagesCodes;
    /** Field drop down list. */
    @Nullable
    private List<String> pricePaidCurrencies;
    /** Field drop down list. */
    @Nullable
    private List<String> listPriceCurrencies;
    /** Field drop down list. */
    @Nullable
    private List<String> authorNamesFormatted;
    /** Field drop down list. */
    @Nullable
    private List<String> authorFamilyNames;
    /** Field drop down list. */
    @Nullable
    private List<String> authorGivenNames;
    /** Field drop down list. */
    @Nullable
    private List<String> publisherNames;
    /** Field drop down list. */
    @Nullable
    private List<String> seriesTitles;
    /** The currently displayed tab. */
    private int currentTab;
    /** These FieldFormatters can be shared between multiple fields. */
    private FieldFormatter<String> dateFormatter;
    private FieldFormatter<String> languageFormatter;
    private ListFormatter<Entity> listFormatterAutoDetails;
    private ListFormatter<Entity> listFormatterNormalDetails;
    private DoubleNumberFormatter doubleNumberFormatter;
    /** {@code true} if the book was changed and successfully saved. */
    private boolean modified;
    private String errStrNonBlankRequired;
    private String errStrReadStartAfterEnd;
    private DateParser<LocalDateTime> dateParser;
    private RealNumberParser realNumberParser;
    private Style style;
    private List<Locale> userLocales;

    private AuthorDao authorDao;
    private BookDao bookDao;
    private BookshelfDao bookshelfDao;
    private ColorDao colorDao;
    private FormatDao formatDao;
    private LanguageDao languageDao;
    private LocationDao locationDao;
    private PublisherDao publisherDao;
    private SeriesDao seriesDao;
    private TagDao tagDao;
    private TocEntryDao tocEntryDao;

    /**
     * Get the user preferred ISBN validity level check for (by the user) editing ISBN codes.
     *
     * @return Validity level
     */
    @NonNull
    ISBN.Validity getLevel() {
        // -1 default (i.e. invalid) will force the Validity default enum to be returned.
        final int id = ServiceLocator.getInstance().getSharedPreferences()
                                     .getIntFromString(PK_EDIT_BOOK_ISBN_CHECKS, -1);
        return ISBN.Validity.byId(id);
    }

    int getCurrentTab() {
        return currentTab;
    }

    void setCurrentTab(final int currentTab) {
        this.currentTab = currentTab;
    }

    /**
     * Pseudo constructor.
     *
     * @param context current context
     * @param args    {@link Intent#getExtras()} or {@link Fragment#getArguments()}
     */
    void init(@NonNull final Context context,
              @Nullable final Bundle args) {

        if (authorDao == null) {
            final ServiceLocator serviceLocator = ServiceLocator.getInstance();
            authorDao = serviceLocator.getAuthorDao();
            bookDao = serviceLocator.getBookDao();
            bookshelfDao = serviceLocator.getBookshelfDao();
            colorDao = serviceLocator.getColorDao();
            formatDao = serviceLocator.getFormatDao();
            languageDao = serviceLocator.getLanguageDao();
            locationDao = serviceLocator.getLocationDao();
            publisherDao = serviceLocator.getPublisherDao();
            seriesDao = serviceLocator.getSeriesDao();
            tagDao = serviceLocator.getTagDao();
            tocEntryDao = serviceLocator.getTocEntryDao();

            errStrNonBlankRequired = context.getString(R.string.vldt_non_blank_required);
            errStrReadStartAfterEnd = context.getString(R.string.vldt_read_start_after_end);

            menuHandlers = List.of(new ViewBookOnSiteMenuHandler(),
                                   new SiteSearchMenuHandler());

            // Lookup the provided style or use the default if not found.
            final String styleUuid = args != null ? args.getString(Style.BKEY_UUID) : null;
            final StylesHelper stylesHelper = serviceLocator.getStyles();
            style = stylesHelper.getStyle(styleUuid).orElseGet(stylesHelper::getDefault);

            final Locale systemLocale = serviceLocator.getSystemLocaleList().get(0);
            userLocales = LocaleListUtils.asList(
                    context.getResources().getConfiguration().getLocales());
            final Locale userLocale = userLocales.get(0);

            realNumberParser = new RealNumberParser(userLocales);
            // We need a FullDateParser to cope with international Locale formats
            // as the fields will contain user-locale specific representations.
            dateParser = new FullDateParser(new ISODateParser(systemLocale), userLocales);

            final Languages languages = serviceLocator.getLanguages();

            dateFormatter = new DateFieldFormatter(userLocale, false);
            languageFormatter = new LanguageFormatter(userLocale, languages);
            doubleNumberFormatter = new DoubleNumberFormatter(realNumberParser);
            listFormatterAutoDetails = new ListFormatter<>(Details.AutoSelect, style);
            listFormatterNormalDetails = new ListFormatter<>(Details.Normal, style);

            if (args != null) {
                // 1. Do we have a Book? e.g. after an internet search
                final Book bookFromArguments = args.getParcelable(Book.BKEY_BOOK_DATA);
                if (bookFromArguments != null) {
                    book = bookFromArguments;
                    // It should always be a new book here, but paranoia...
                    if (book.isNew()) {
                        // DATE_ACQUIRED is always used
                        book.ensureDateAcquired();
                        // if BOOK_CONDITION is wanted, assume the user got a new book.
                        book.ensureCondition();
                        // it's all new data, not saved yet, hence 'Dirty'
                        book.setStage(EntityStage.Stage.Dirty);
                    }

                } else {
                    // 2. Do we have an id?, e.g. user clicked on a book in a list.
                    final long bookId = args.getLong(DBKey.FK_BOOK, 0);
                    if (bookId > 0) {
                        book = Book.from(bookId);
                    } else {
                        book = new Book();
                    }
                    // has unchanged data, hence 'WriteAble'
                    book.setStage(EntityStage.Stage.WriteAble);
                }
            } else {
                // 3. No args, we want an empty new book (e.g. user wants to add one manually).
                book = new Book();
                // has no data, hence 'WriteAble'
                book.setStage(EntityStage.Stage.WriteAble);
            }

            book.addValidators(context);
            book.ensureBookshelf();
            book.ensureLanguage(userLocale);
        }
    }

    @NonNull
    RealNumberParser getRealNumberParser() {
        return realNumberParser;
    }

    @NonNull
    DateParser<LocalDateTime> getDateParser() {
        return dateParser;
    }

    @NonNull
    List<MenuHandler<DataHolder>> getMenuHandlers() {
        return menuHandlers;
    }

    @NonNull
    List<Field<?, ? extends View>> getFields(@NonNull final FragmentId fragmentId) {
        return allFields.stream()
                        .filter(field -> field.getFragmentId() == fragmentId)
                        .collect(Collectors.toList());
    }

    /**
     * Find the Field (across all fragments) associated with the passed ID.
     * If two fragments contain the same field (id) (which really should never happen... flw),
     * the first one found is returned.
     *
     * @param id Field/View ID
     *
     * @return Optional with the Field
     */
    @NonNull
    private <T, V extends View> Optional<Field<T, V>> getField(@IdRes final int id) {
        //noinspection unchecked
        return allFields.stream()
                        .filter(field -> field.getFieldViewId() == id)
                        .map(field -> (Field<T, V>) field)
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
        return allFields.stream()
                        .filter(field -> field.getFieldViewId() == id)
                        .map(field -> (Field<T, V>) field)
                        .findFirst()
                        .orElseThrow(() -> new IllegalArgumentException("Field not found: " + id));
    }

    /**
     * Save the values of the specified field group to the given Book.
     *
     * @param fragmentId the hosting fragment for this set of fields
     * @param book       to put the field values in
     */
    void saveFields(@NonNull final FragmentId fragmentId,
                    @NonNull final Book book) {
        getFields(fragmentId).stream()
                             .filter(Field::isAutoPopulated)
                             .forEach(field -> field.save(book));
    }

    /**
     * Get the list of fragments (their tags) which have unfinished edits.
     *
     * @return list
     */
    @NonNull
    Collection<FragmentId> getUnfinishedEdits() {
        return fragmentsWithUnfinishedEdits;
    }

    @NonNull
    Style getStyle() {
        return style;
    }

    @NonNull
    Book getBook() {
        return book;
    }

    @Override
    public boolean isRead() {
        return book.isRead();
    }

    @Override
    public void setReadNow(final boolean read) {
        book.setReadNow(read);
        book.setStage(EntityStage.Stage.Dirty);
        updateReadStatus(true);
    }

    @Override
    @NonNull
    public ReadingProgress getReadingProgress() {
        return book.getReadingProgress();
    }

    @Override
    public void setReadingProgress(@NonNull final ReadingProgress readingProgress) {
        book.setReadingProgress(readingProgress);
        book.setStage(EntityStage.Stage.Dirty);
        updateReadStatus(true);
    }

    @Override
    public void updateReadStatus(final boolean statusModified) {
        onReadStatusUpdateUI.setValue(statusModified);
    }

    /**
     * Called when a picker returns a newly selected date.
     *
     * @param fieldIds   to update
     * @param selections date(s) to set
     */
    void onDateSet(@NonNull final int[] fieldIds,
                   @NonNull final Long[] selections) {
        for (int i = 0; i < fieldIds.length; i++) {
            if (selections[i] == null) {
                onDateSet(fieldIds[i], "");
            } else {
                onDateSet(fieldIds[i], Instant.ofEpochMilli(selections[i])
                                              .atZone(ZoneId.systemDefault())
                                              .format(DateTimeFormatter.ISO_LOCAL_DATE));
            }
        }
    }

    /**
     * Called when a picker returns a newly selected date.
     *
     * @param fieldId to update
     * @param dateStr to set
     */
    void onDateSet(@IdRes final int fieldId,
                   @NonNull final String dateStr) {

        final Field<String, TextView> field = requireField(fieldId);
        final String previous = field.getValue();

        // Update BOTH the book and the field
        book.putString(field.getFieldKey(), dateStr);
        field.setValue(dateStr);
        field.notifyIfChanged(previous);

        // If we are setting the read-end date,
        // then we must set the read-flag/progress accordingly
        if (fieldId == R.id.read_end && !dateStr.isEmpty()) {
            book.putBoolean(DBKey.READ__BOOL, true);
            book.putString(DBKey.READ_PROGRESS, "");
            // Update *this* fragment + the ReadStatusFragment
            updateReadStatus(false);
        }
        // Note we're NOT calling updateReadStatus() when the R.id.read_start field
        // is updated; there is no need
    }

    @NonNull
    @Override
    public LiveData<Boolean> onUpdateReadStatus() {
        return onReadStatusUpdateUI;
    }

    boolean isAnthology() {
        final Field<Long, View> typeField = requireField(R.id.book_type);
        return Objects.equals(typeField.getValue(), Book.ContentType.Anthology.getId());
    }

    /**
     * Insert/update the book into the database, store cover files, and prepare activity results.
     *
     * @param context Current context
     *
     * @throws StorageException  The covers directory is not available
     * @throws DaoWriteException on failure
     */
    void saveBook(@NonNull final Context context)
            throws StorageException, DaoWriteException {

        if (book.isNew()) {
            bookDao.insert(context, book);
        } else {
            bookDao.update(context, book);
        }
        modified = true;
        book.setStage(EntityStage.Stage.Clean);
    }

    @NonNull
    Intent createResultIntent() {
        return new EditBookOutput(modified, book.getId(), 0)
                .createResultIntent();
    }

    /**
     * Delete an individual {@link TocEntry}.
     *
     * @param context  Current context
     * @param tocEntry to delete.
     *
     * @return {@code true} if a row was deleted
     */
    boolean deleteTocEntry(@NonNull final Context context,
                           @NonNull final TocEntry tocEntry) {
        return tocEntryDao.delete(context, tocEntry);
    }

    /**
     * Check if the book already exists in the database.
     *
     * @return {@code true} if it does
     */
    boolean bookExists() {
        if (book.isNew()) {
            final String isbnStr = book.getIsbn();
            if (!isbnStr.isEmpty()) {
                return bookDao.bookExistsByIsbn(isbnStr);
            }
        }

        return false;
    }

    /**
     * Add any fields the book does not have yet (does not overwrite existing ones).
     *
     * @param args to check
     */
    void addFieldsFromArguments(@Nullable final Bundle args) {
        if (args != null) {
            final Book bookFromArguments = args.getParcelable(Book.BKEY_BOOK_DATA);
            if (bookFromArguments != null) {
                bookFromArguments.keySet()
                                 .stream()
                                 .filter(key -> !book.contains(key))
                                 .forEach(key -> book.put(key, bookFromArguments
                                         .get(key, realNumberParser)));
            }
        }
    }

    @NonNull
    List<Bookshelf> getAllBookshelves() {
        // not cached.
        // This allows the user to edit the global list of shelves while editing a book.
        return bookshelfDao.getAll();
    }

    /**
     * Load an {@link Author} names list.
     *
     * @return list of names
     */
    @NonNull
    List<String> getAllAuthorNames() {
        if (authorNamesFormatted == null) {
            authorNamesFormatted = authorDao.getNames(DBKey.AUTHOR.FORMATTED_FULL_NAME);
        }
        return authorNamesFormatted;
    }

    /**
     * Load an {@link Author} Family names list.
     *
     * @return list of names
     */
    @NonNull
    List<String> getAllAuthorFamilyNames() {
        if (authorFamilyNames == null) {
            authorFamilyNames = authorDao.getNames(DBKey.AUTHOR.FAMILY_NAME);
        }
        return authorFamilyNames;
    }

    /**
     * Load an {@link Author} Given names list.
     *
     * @return list of names
     */
    @NonNull
    List<String> getAllAuthorGivenNames() {
        if (authorGivenNames == null) {
            authorGivenNames = authorDao.getNames(DBKey.AUTHOR.GIVEN_NAMES);
        }
        return authorGivenNames;
    }

    /**
     * Load a {@link Publisher} names list.
     *
     * @return list of names
     */
    @NonNull
    List<String> getAllPublisherNames() {
        if (publisherNames == null) {
            publisherNames = publisherDao.getNames();
        }
        return publisherNames;
    }

    /**
     * Load a {@link Series} titles list.
     *
     * @return list of titles
     */
    @NonNull
    List<String> getAllSeriesTitles() {
        if (seriesTitles == null) {
            seriesTitles = seriesDao.getNames();
        }
        return seriesTitles;
    }

    /**
     * Add or remove the given fragment tag from the list of unfinished edits.
     *
     * @param fragmentId         of fragment
     * @param hasUnfinishedEdits flag
     */
    void setUnfinishedEdits(@NonNull final FragmentId fragmentId,
                            final boolean hasUnfinishedEdits) {
        if (hasUnfinishedEdits) {
            // Flag up this fragment as having unfinished edits.
            fragmentsWithUnfinishedEdits.add(fragmentId);
        } else {
            fragmentsWithUnfinishedEdits.remove(fragmentId);
        }
    }

    /**
     * Get a unique list of all languages (ISO codes) in the database.
     * The list is ordered by {@link DBKey#DATE_LAST_UPDATED__UTC}.
     * It's extended with a set of defaults.
     *
     * @return The list of ISO 639-2 codes
     */
    @NonNull
    private List<String> getAllLanguagesCodes() {
        if (languagesCodes == null) {
            final Set<String> set = new LinkedHashSet<>(languageDao.getList());
            // Provide defaults: the device language + the set we explicitly support
            set.addAll(ServiceLocator.getInstance().getLanguages().getDefaultCodes(userLocales));
            languagesCodes = new ArrayList<>(set);
        }
        return languagesCodes;
    }

    /**
     * Get a unique list of all book-formats in the database, ordered alphabetically.
     * It's extended with a set of defaults.
     *
     * @param context Current context
     *
     * @return List of formats
     */
    @NonNull
    private List<String> getAllFormats(@NonNull final Context context) {
        if (formats == null) {
            final Set<String> set = new LinkedHashSet<>(formatDao.getList());
            // Provide some defaults
            set.add(context.getString(R.string.book_format_paperback));
            set.add(context.getString(R.string.book_format_paperback_large));
            set.add(context.getString(R.string.book_format_softcover));
            set.add(context.getString(R.string.book_format_hardcover));
            formats = new ArrayList<>(set);
        }
        return formats;
    }

    /**
     * Get a unique list of all book-colours in the database, ordered alphabetically.
     * It's extended with a set of defaults.
     *
     * @param context Current context
     *
     * @return List of colours
     */
    @NonNull
    private List<String> getAllColors(@NonNull final Context context) {
        if (colors == null) {
            final Set<String> set = new LinkedHashSet<>(colorDao.getList());
            // Provide some defaults
            set.add(context.getString(R.string.book_color_black_and_white));
            set.add(context.getString(R.string.book_color_full_color));
            colors = new ArrayList<>(set);
        }
        return colors;
    }

    /**
     * Get a unique list of all tags in the database, ordered alphabetically.
     *
     * @return list
     */
    @NonNull
    List<Tag> getAllTags() {
        if (tags == null) {
            tags = tagDao.getAll();
        }
        return tags;
    }

    /**
     * Get a unique list of all locations in the database, ordered alphabetically.
     *
     * @return List of locations
     */
    @NonNull
    private List<String> getAllLocations() {
        if (locations == null) {
            locations = locationDao.getList();
        }
        return locations;
    }

    /**
     * Get a unique list of all currencies (ISO codes) used for the list-price in the database,
     * ordered alphabetically.
     * It's extended with a set of defaults.
     *
     * @return List of ISO currency codes
     */
    @NonNull
    private List<String> getAllListPriceCurrencyCodes() {
        if (listPriceCurrencies == null) {
            final Set<String> set = new LinkedHashSet<>(
                    bookDao.getCurrencyCodes(DBKey.PRICE_LISTED));
            set.addAll(getDefaultCurrencies());
            listPriceCurrencies = new ArrayList<>(set);
        }
        return listPriceCurrencies;
    }

    /**
     * Get a unique list of all currencies (ISO codes) used for the paid-price in the database,
     * ordered alphabetically.
     * It's extended with a set of defaults.
     *
     * @return List of ISO currency codes
     */
    @NonNull
    private List<String> getAllPricePaidCurrencyCodes() {
        if (pricePaidCurrencies == null) {
            final Set<String> set = new LinkedHashSet<>(
                    bookDao.getCurrencyCodes(DBKey.PRICE_PAID));
            set.addAll(getDefaultCurrencies());
            pricePaidCurrencies = new ArrayList<>(set);
        }
        return pricePaidCurrencies;
    }

    @NonNull
    private List<String> getDefaultCurrencies() {
        // sure, this is very crude and discriminating - oh well
        return List.of(MoneyParser.EUR, MoneyParser.GBP, MoneyParser.USD, MoneyParser.CNY);
    }

    /**
     * Check if the passed Author is only used by this book.
     *
     * @param context Current context
     * @param author  to check
     *
     * @return {@code true} if the Author is only used by this book
     */
    boolean isSingleUsage(@NonNull final Context context,
                          @NonNull final Author author) {
        if (author.getId() == 0) {
            final Locale userLocale = userLocales.get(0);
            final Locale bookLocale = book.getLocale(userLocale).orElse(userLocale);
            authorDao.fixId(context, author, bookLocale);
            if (author.getId() == 0) {
                return true;
            }
        }

        final int books = authorDao.countBooks(author);
        final int tocEntries = tocEntryDao.count(author);

        // If the book is new, then there should be no other references.
        // If the book exists in the database, then obv. there should be 1 reference.
        final int zeroOrOneRef = book.isNew() ? 0 : 1;

        // edge case: if an Author has ONE book and there is a single TOCEntry for
        // that book (with obviously the same Author) then it's considered single use.
        if (books == zeroOrOneRef && tocEntries == zeroOrOneRef) {
            return true;
        }

        return (books + tocEntries) <= zeroOrOneRef;
    }

    /**
     * Check if the passed Series is only used by this book.
     *
     * @param context Current context
     * @param series  to check
     *
     * @return {@code true} if the Series is only used by this book
     */
    boolean isSingleUsage(@NonNull final Context context,
                          @NonNull final Series series) {
        if (series.getId() == 0) {
            final Locale userLocale = userLocales.get(0);
            seriesDao.fixId(context, series, series.getLocale(userLocale).orElseGet(
                    () -> book.getLocale(userLocale).orElse(userLocale)));
            if (series.getId() == 0) {
                return true;
            }
        }

        final int nrOfReferences = seriesDao.countBooks(series);
        return nrOfReferences <= (book.isNew() ? 0 : 1);
    }

    /**
     * Check if the passed Publisher is only used by this book.
     *
     * @param context   Current context
     * @param publisher to check
     *
     * @return {@code true} if the Publisher is only used by this book
     */
    boolean isSingleUsage(@NonNull final Context context,
                          @NonNull final Publisher publisher) {
        if (publisher.getId() == 0) {
            final Locale userLocale = userLocales.get(0);
            final Locale bookLocale = book.getLocale(userLocale).orElse(userLocale);
            publisherDao.fixId(context, publisher, bookLocale);
            if (publisher.getId() == 0) {
                return true;
            }
        }

        final int nrOfReferences = publisherDao.countBooks(publisher);
        return nrOfReferences <= (book.isNew() ? 0 : 1);
    }

    void updateAuthors(@NonNull final List<Author> list) {
        // Update BOTH the book and the field
        book.setAuthors(list);
        requireField(R.id.author).setValue(list);
    }

    void updateSeries(@NonNull final List<Series> list) {
        // Update BOTH the book and the field
        book.setSeries(list);
        requireField(R.id.series_title).setValue(list);
    }

    void updatePublishers(@NonNull final List<Publisher> list) {
        // Update BOTH the book and the field
        book.setPublishers(list);
        requireField(R.id.publisher).setValue(list);
    }

    void updateTags(@NonNull final List<Tag> list) {
        // Update BOTH the book and the field
        book.setTags(list);
        requireField(R.id.tags).setValue(list);
    }

    void updateBookshelves(@NonNull final Set<Long> previousSelection,
                           @NonNull final Set<Long> bookshelfIds,
                           @Nullable final Bundle extras) {
        if (previousSelection.equals(bookshelfIds)) {
            // No changes made
            return;
        }

        final Field<List<Bookshelf>, TextView> field = requireField(R.id.bookshelves);
        final List<Bookshelf> previous = field.getValue();

        final List<Bookshelf> selected =
                getAllBookshelves()
                        .stream()
                        .filter(bookshelf -> bookshelfIds.contains(bookshelf.getId()))
                        .collect(Collectors.toList());

        // Update BOTH the book and the field
        book.setBookshelves(selected);
        field.setValue(selected);
        field.notifyIfChanged(previous);
    }

    void changeForThisBook(@NonNull final Context context,
                           @NonNull final Author original,
                           @NonNull final Author modified)
            throws DaoWriteException {

        final Locale userLocale = userLocales.get(0);
        final Locale bookLocale = book.getLocale(userLocale).orElse(userLocale);
        authorDao.insert(context, modified, bookLocale);
        final List<Author> list = book.getAuthors();
        // unlink the original, and link with the new one
        // Note that the original *might* be orphaned at this time.
        // That's OK, it will get garbage collected from the database sooner or later.
        list.remove(original);
        list.add(modified);
        book.setAuthors(list);
        book.pruneAuthors(context);
    }

    void changeForAllBooks(@NonNull final Context context,
                           @NonNull final Author original,
                           @NonNull final Author modified)
            throws DaoWriteException {
        // copy all new data
        original.copyFrom(modified, true);

        final Locale userLocale = userLocales.get(0);
        final Locale bookLocale = book.getLocale(userLocale).orElse(userLocale);
        authorDao.update(context, original, bookLocale);
        book.pruneAuthors(context);
        book.refreshAuthors(context);
    }

    void changeForThisBook(@NonNull final Context context,
                           @NonNull final Series original,
                           @NonNull final Series modified)
            throws DaoWriteException {

        final Locale userLocale = userLocales.get(0);
        final Locale bookLocale = book.getLocale(userLocale).orElse(userLocale);
        seriesDao.insert(context, modified, bookLocale);
        final List<Series> list = book.getSeries();
        // unlink the original, and link with the new one
        // Note that the original *might* be orphaned at this time.
        // That's OK, it will get garbage collected from the database sooner or later.
        list.remove(original);
        list.add(modified);
        book.setSeries(list);
        book.pruneSeries(context);
    }

    void changeForAllBooks(@NonNull final Context context,
                           @NonNull final Series original,
                           @NonNull final Series modified)
            throws DaoWriteException {
        // copy all new data
        original.copyFrom(modified, true);

        final Locale userLocale = userLocales.get(0);
        final Locale bookLocale = book.getLocale(userLocale).orElse(userLocale);
        seriesDao.update(context, original, bookLocale);
        book.pruneSeries(context);
        book.refreshSeries(context);
    }

    void changeForThisBook(@NonNull final Context context,
                           @NonNull final Publisher original,
                           @NonNull final Publisher modified)
            throws DaoWriteException {

        final Locale userLocale = userLocales.get(0);
        final Locale bookLocale = book.getLocale(userLocale).orElse(userLocale);
        publisherDao.insert(context, modified, bookLocale);
        final List<Publisher> list = book.getPublishers();
        // unlink the original, and link with the new one
        // Note that the original *might* be orphaned at this time.
        // That's OK, it will get garbage collected from the database sooner or later.
        list.remove(original);
        list.add(modified);
        book.setPublishers(list);
        book.prunePublishers(context);
    }

    void changeForAllBooks(@NonNull final Context context,
                           @NonNull final Publisher original,
                           @NonNull final Publisher modified)
            throws DaoWriteException {
        // copy all new data
        original.copyFrom(modified);

        final Locale userLocale = userLocales.get(0);
        final Locale bookLocale = book.getLocale(userLocale).orElse(userLocale);
        publisherDao.update(context, original, bookLocale);
        book.prunePublishers(context);
        book.refreshPublishers(context);
    }

    void fixId(@NonNull final Context context,
               @NonNull final Author author) {
        final Locale userLocale = userLocales.get(0);
        final Locale bookLocale = book.getLocale(userLocale).orElse(userLocale);
        authorDao.fixId(context, author, bookLocale);
    }

    void fixId(@NonNull final Context context,
               @NonNull final Series series) {
        final Locale userLocale = userLocales.get(0);
        seriesDao.fixId(context, series, series.getLocale(userLocale).orElseGet(
                () -> book.getLocale(userLocale).orElse(userLocale)));
    }

    void fixId(@NonNull final Context context,
               @NonNull final Publisher publisher) {
        final Locale userLocale = userLocales.get(0);
        final Locale bookLocale = book.getLocale(userLocale).orElse(userLocale);
        publisherDao.fixId(context, publisher, bookLocale);
    }

    void fixId(@NonNull final Context context,
               @NonNull final TocEntry tocEntry) {
        final Locale userLocale = userLocales.get(0);
        final Locale bookLocale = book.getLocale(userLocale).orElse(userLocale);
        tocEntryDao.fixId(context, tocEntry, bookLocale);
    }

    @SuppressWarnings("SameParameterValue")
    void initFieldsMain(@NonNull final FragmentId fragmentId) {
        allFields.add(new TextViewField<>(fragmentId, R.id.author, Book.BKEY_AUTHOR_LIST,
                                          DBKey.FK_AUTHOR,
                                          listFormatterAutoDetails)
                           .setTextInputLayoutId(R.id.lbl_author)
                           .setValidator(field -> field.setErrorIfEmpty(
                                   errStrNonBlankRequired)));

        allFields.add(new TextViewField<>(fragmentId, R.id.series_title, Book.BKEY_SERIES_LIST,
                                          DBKey.FK_SERIES,
                                          listFormatterAutoDetails)
                           .setTextInputLayoutId(R.id.lbl_series));

        allFields.add(new EditTextField<>(fragmentId, R.id.title, DBKey.TITLE)
                           .setTextInputLayoutId(R.id.lbl_title)
                           .setCapitalization(EditTextField.Capitalization.Title)
                           .setEndIconMode(TextInputLayout.END_ICON_CLEAR_TEXT)
                           .setValidator(field -> field.setErrorIfEmpty(
                                   errStrNonBlankRequired)));

        allFields.add(new EditTextField<>(fragmentId, R.id.original_title,
                                          DBKey.TRANSLATION_ORIGINAL_TITLE)
                           .setTextInputLayoutId(R.id.lbl_original_title)
                           .setCapitalization(EditTextField.Capitalization.Title)
                           .setEndIconMode(TextInputLayout.END_ICON_CLEAR_TEXT));

        allFields.add(new AutoCompleteTextField(fragmentId, R.id.original_language,
                                                DBKey.TRANSLATION_ORIGINAL_LANGUAGE,
                                                this::getAllLanguagesCodes)
                           .setFormatter(languageFormatter, true)
                           .setTextInputLayoutId(R.id.lbl_original_language));

        allFields.add(new EditTextField<>(fragmentId, R.id.description, DBKey.DESCRIPTION)
                           .setTextInputLayoutId(R.id.lbl_description)
                           .setEndIconMode(TextInputLayout.END_ICON_CLEAR_TEXT));

        // Not using a EditIsbn custom View, as we want to be able to enter invalid codes here.
        allFields.add(new EditTextField<>(fragmentId, R.id.isbn, DBKey.ISBN)
                           .setTextInputLayoutId(R.id.lbl_isbn));
        // don't do this for now. There is a scan icon as end-icon.
        //                  .setEndIconMode(TextInputLayout.END_ICON_CLEAR_TEXT)

        allFields.add(new AutoCompleteTextField(fragmentId, R.id.language, DBKey.LANGUAGE,
                                                this::getAllLanguagesCodes)
                           .setFormatter(languageFormatter, true)
                           .setTextInputLayoutId(R.id.lbl_language)
                           .setValidator(field -> field.setErrorIfEmpty(
                                   errStrNonBlankRequired)));

        // Personal fields
        allFields.add(new TextViewField<>(FragmentId.Main, R.id.tags, Book.BKEY_TAG_LIST,
                                          DBKey.FK_TAG,
                                          listFormatterNormalDetails)
                           .addRelatedViews(R.id.lbl_tags));

        allFields.add(new TextViewField<>(fragmentId, R.id.bookshelves, Book.BKEY_BOOKSHELF_LIST,
                                          DBKey.FK_BOOKSHELF,
                                          listFormatterNormalDetails)
                           .setTextInputLayoutId(R.id.lbl_bookshelves)
                           .setValidator(field -> field.setErrorIfEmpty(
                                   errStrNonBlankRequired)));
    }

    @SuppressWarnings("SameParameterValue")
    void initFieldsPublication(@NonNull final Context context,
                               @NonNull final FragmentId fragmentId) {
        allFields.add(new AutoCompleteTextField(fragmentId, R.id.format, DBKey.FORMAT,
                                                () -> getAllFormats(context))
                           .setTextInputLayoutId(R.id.lbl_format));

        allFields.add(new AutoCompleteTextField(fragmentId, R.id.color, DBKey.COLOR,
                                                () -> getAllColors(context))
                           .setTextInputLayoutId(R.id.lbl_color));

        allFields.add(new TextViewField<>(fragmentId, R.id.publisher, Book.BKEY_PUBLISHER_LIST,
                                          DBKey.FK_PUBLISHER,
                                          listFormatterNormalDetails)
                           .setTextInputLayoutId(R.id.lbl_publisher));

        allFields.add(new TextViewField<>(fragmentId, R.id.first_publication,
                                          DBKey.FIRST_PUBLICATION_DATE,
                                          dateFormatter)
                           .setTextInputLayoutId(R.id.lbl_first_publication)
                           .setEndIconMode(TextInputLayout.END_ICON_CLEAR_TEXT));

        allFields.add(new TextViewField<>(fragmentId, R.id.date_published,
                                          DBKey.PUBLICATION_DATE,
                                          dateFormatter)
                           .setTextInputLayoutId(R.id.lbl_date_published)
                           .setEndIconMode(TextInputLayout.END_ICON_CLEAR_TEXT));

        allFields.add(new EditTextField<>(fragmentId, R.id.pages, DBKey.PAGES)
                           .setTextInputLayoutId(R.id.lbl_pages)
                           .setEndIconMode(TextInputLayout.END_ICON_CLEAR_TEXT));


        // MUST be defined before the currency field is defined.
        allFields.add(new DecimalEditTextField(fragmentId, R.id.price_listed, DBKey.PRICE_LISTED)
                           .setFormatter(doubleNumberFormatter, false)
                           .setTextInputLayoutId(R.id.lbl_price_listed)
                           .setEndIconMode(TextInputLayout.END_ICON_CLEAR_TEXT)
                           // Copy to price_paid field if applicable
                           .addOnFocusChangeListener((view, hasFocus) -> {
                               if (!hasFocus) {
                                   getField(R.id.price_paid).ifPresent(destField -> {
                                       if (destField.isEmpty()) {
                                           // Paranoia... parse it to a double.
                                           final double value = realNumberParser.toDouble(
                                                   requireField(R.id.price_listed).getValue());
                                           // Update BOTH the book and the field
                                           getBook().putDouble(DBKey.PRICE_PAID, value);
                                           destField.setValue(value);
                                       }
                                   });
                               }
                           })
                           .addRelatedViews(R.id.lbl_price_listed,
                                            R.id.lbl_price_listed_currency,
                                            R.id.price_listed_currency));

        allFields.add(new AutoCompleteTextField(fragmentId, R.id.price_listed_currency,
                                                DBKey.PRICE_LISTED_CURRENCY,
                                                this::getAllListPriceCurrencyCodes)
                           .setTextInputLayoutId(R.id.lbl_price_listed_currency)
                           // Copy to price_paid_currency field if applicable
                           .addOnFocusChangeListener((v, hasFocus) -> {
                               if (!hasFocus) {
                                   getField(R.id.price_paid_currency).ifPresent(destField -> {
                                       if (destField.isEmpty()) {
                                           final String value = (String)
                                                   requireField(R.id.price_listed_currency)
                                                           .getValue();
                                           if (value != null) {
                                               // Update BOTH the book and the field
                                               getBook().putString(DBKey.PRICE_PAID_CURRENCY,
                                                                   value);
                                               destField.setValue(value);
                                           }
                                       }
                                   });
                               }
                           })
                           .setUsedKey(DBKey.PRICE_LISTED));

        allFields.add(new EditTextField<>(fragmentId, R.id.print_run, DBKey.PRINT_RUN)
                           .setTextInputLayoutId(R.id.lbl_print_run)
                           .setEndIconMode(TextInputLayout.END_ICON_CLEAR_TEXT));

        allFields.add(new BitmaskChipGroupField(fragmentId, R.id.edition, DBKey.EDITION,
                                                Book.Edition::getAll)
                           .addRelatedViews(R.id.lbl_edition));
    }

    @SuppressWarnings("SameParameterValue")
    void initFieldsNotes(@NonNull final Context context,
                         @NonNull final FragmentId fragmentId) {

        allFields.add(new CompoundButtonField(fragmentId, R.id.cbx_signed, DBKey.SIGNED__BOOL));

        allFields.add(new RatingBarEditField(fragmentId, R.id.rating, DBKey.RATING));

        allFields.add(new EditTextField<>(fragmentId, R.id.notes, DBKey.PERSONAL_NOTES)
                           .setTextInputLayoutId(R.id.lbl_notes)
                           .setEndIconMode(TextInputLayout.END_ICON_CLEAR_TEXT));

        // MUST be defined before the currency.
        allFields.add(new DecimalEditTextField(fragmentId, R.id.price_paid, DBKey.PRICE_PAID)
                           .setFormatter(doubleNumberFormatter, false)
                           .setTextInputLayoutId(R.id.lbl_price_paid)
                           .setEndIconMode(TextInputLayout.END_ICON_CLEAR_TEXT)
                           .addRelatedViews(R.id.lbl_price_paid,
                                            R.id.lbl_price_paid_currency,
                                            R.id.price_paid_currency));

        allFields.add(new AutoCompleteTextField(fragmentId, R.id.price_paid_currency,
                                                DBKey.PRICE_PAID_CURRENCY,
                                                this::getAllPricePaidCurrencyCodes)
                           .setTextInputLayoutId(R.id.lbl_price_paid_currency)
                           .setUsedKey(DBKey.PRICE_PAID));

        allFields.add(new StringArrayDropDownMenuField(fragmentId, R.id.condition,
                                                       DBKey.CONDITION_BOOK,
                                                       context, R.array.lbl_book_condition)
                           .setTextInputLayoutId(R.id.lbl_condition));

        allFields.add(new StringArrayDropDownMenuField(fragmentId, R.id.condition_cover,
                                                       DBKey.CONDITION_COVER,
                                                       context, R.array.lbl_dust_cover_condition)
                           .setTextInputLayoutId(R.id.lbl_condition_cover));

        allFields.add(new AutoCompleteTextField(fragmentId, R.id.location, DBKey.LOCATION,
                                                this::getAllLocations)
                           .setTextInputLayoutId(R.id.lbl_location));

        allFields.add(new TextViewField<>(fragmentId, R.id.date_acquired, DBKey.DATE_ACQUIRED,
                                          dateFormatter)
                           .setTextInputLayoutId(R.id.lbl_date_acquired)
                           .setEndIconMode(TextInputLayout.END_ICON_CLEAR_TEXT));

        allFields.add(new TextViewField<>(fragmentId, R.id.read_start, DBKey.READ_START__DATE,
                                          dateFormatter)
                           .setTextInputLayoutId(R.id.lbl_read_start)
                           .setEndIconMode(TextInputLayout.END_ICON_CLEAR_TEXT)
                           .setValidator(this::validateReadStartAndEndFields));

        allFields.add(new TextViewField<>(fragmentId, R.id.read_end, DBKey.READ_END__DATE,
                                          dateFormatter)
                           .setTextInputLayoutId(R.id.lbl_read_end)
                           .setEndIconMode(TextInputLayout.END_ICON_CLEAR_TEXT)
                           .setValidator(this::validateReadStartAndEndFields));
    }

    private void validateReadStartAndEndFields(@NonNull final Field<String, TextView> field) {

        // we ignore the passed field, so we can use this validator for both fields.
        final Field<String, TextView> startField = requireField(R.id.read_start);
        final Field<String, TextView> endField = requireField(R.id.read_end);

        final String start = startField.getValue();
        if (start == null || start.isEmpty()) {
            startField.setError(null);
            endField.setError(null);
            return;
        }

        final String end = endField.getValue();
        if (end == null || end.isEmpty()) {
            startField.setError(null);
            endField.setError(null);
            return;
        }

        if (start.compareToIgnoreCase(end) > 0) {
            endField.setError(errStrReadStartAfterEnd);

        } else {
            startField.setError(null);
            endField.setError(null);
        }
    }

    @SuppressWarnings("SameParameterValue")
    void initFieldsToc(@NonNull final Context context,
                       @NonNull final FragmentId fragmentId) {

        allFields.add(new EntityListDropDownMenuField<>(fragmentId, R.id.book_type,
                                                        DBKey.CONTENT_TYPE,
                                                        context,
                                                        Book.ContentType.getAll())
                           .setTextInputLayoutId(R.id.lbl_book_type));
    }

    @SuppressWarnings("SameParameterValue")
    void initFieldsExternalId(@NonNull final LayoutInflater inflater,
                              @NonNull final ViewGroup root,
                              @NonNull final FragmentId fragmentId) {

        allFields.add(new CompoundButtonField(fragmentId, R.id.btn_auto_update_allowed,
                                              DBKey.AUTO_UPDATE));

        // NEWTHINGS: adding a new search engine:
        //   optional: external id KEY add a field; don't forget to add to the layout as well
        //  Keep them alphabetic.

        // We're no longer using the LongNumberFormatter as we don't
        // need the extraction to a 'long'. Identifiers are now all 'String' values.
        // Just use a custom formatter to keep the field empty
        // instead of displaying any "0" values.
        final FieldFormatter<String> sidLongFormatter =
                (context, value) -> value != null && !"0".equals(value) ? value : "";

        final IdentifierDao dao = ServiceLocator.getInstance().getIdentifierDao();

        final List<String> identifierKeys = List.of(Identifier.SID_ASIN,
                                                    Identifier.SID_BEDETHEQUE,
                                                    Identifier.SID_BIBLIOTECE_PL,
                                                    Identifier.SID_BNF,
                                                    Identifier.SID_DATABAZE_KNIH,
                                                    Identifier.SID_DNB,
                                                    Identifier.SID_GOODREADS,
                                                    Identifier.SID_ISFDB,
                                                    Identifier.SID_KBNL,
                                                    Identifier.SID_LAST_DODO_NL,
                                                    Identifier.SID_LIBRARY_THING,
                                                    Identifier.SID_OPEN_LIBRARY,
                                                    Identifier.SID_STRIP_INFO);

        final int[] ids = new int[identifierKeys.size()];

        for (int i = 0; i < identifierKeys.size(); i++) {

            final String identifierKey = identifierKeys.get(i);
            final Optional<Identifier> oIdentifier = dao.find(identifierKey,
                                                              Identifier.EntityType.Book);
            // Paranoia
            if (oIdentifier.isPresent()) {
                final Identifier identifier = oIdentifier.get();
                @LayoutRes
                final int layoutId;
                if (identifier.getType() == Identifier.Type.Number) {
                    layoutId = R.layout.row_edit_sid_number;
                } else {
                    layoutId = R.layout.row_edit_sid_text;
                }
                final View v = inflater.inflate(layoutId, root, false);

                final TextInputLayout til = v.findViewById(R.id.til);
                final int tilId = View.generateViewId();
                til.setId(tilId);
                til.setHint(identifier.getName());

                final TextInputEditText tie = v.findViewById(R.id.tie);
                final int tieId = View.generateViewId();
                tie.setId(tieId);
                // last one?
                if (i == allFields.size() - 1) {
                    tie.setImeOptions(EditorInfo.IME_ACTION_DONE);
                }

                root.addView(v);
                ids[i] = tilId;

                final EditTextField<String, EditText> field =
                        new IdentifierField<>(fragmentId, tieId, identifierKey)
                                .setTextInputLayoutId(tilId)
                                .setEndIconMode(TextInputLayout.END_ICON_CLEAR_TEXT);

                if (identifier.getType() == Identifier.Type.Number) {
                    field.setFormatter(sidLongFormatter, true);
                }
                allFields.add(field);
            }

            final Flow flow = root.findViewById(R.id.flow_site_ids);
            flow.setReferencedIds(ids);
        }
    }

    /**
     * Check if the given fragment handles (displays) the given field.
     *
     * @param fragmentId the hosting fragment for this set of fields
     * @param fieldId    to check
     *
     * @return {@code true} if the given fragment handles the given field
     */
    public boolean handlesField(@NonNull final FragmentId fragmentId,
                                final int fieldId) {
        return allFields.stream()
                        // This will return a single field (or none)
                        .filter(field -> field.getFieldViewId() == fieldId)
                        // let's see if it's owned by the given fragment
                        .anyMatch(field -> field.getFragmentId() == fragmentId);
    }
}
