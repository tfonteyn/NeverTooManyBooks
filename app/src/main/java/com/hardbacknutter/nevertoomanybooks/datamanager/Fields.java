/*
 * @Copyright 2020 HardBackNutter
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
package com.hardbacknutter.nevertoomanybooks.datamanager;

import android.content.Context;
import android.util.SparseArray;
import android.view.View;
import android.widget.AutoCompleteTextView;
import android.widget.Checkable;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.IdRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import androidx.annotation.VisibleForTesting;
import androidx.fragment.app.FragmentManager;

import com.google.android.material.chip.ChipGroup;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import com.hardbacknutter.nevertoomanybooks.App;
import com.hardbacknutter.nevertoomanybooks.BuildConfig;
import com.hardbacknutter.nevertoomanybooks.R;
import com.hardbacknutter.nevertoomanybooks.datamanager.fieldaccessors.AccessorFactory;
import com.hardbacknutter.nevertoomanybooks.datamanager.fieldaccessors.BitmaskChipGroupAccessor;
import com.hardbacknutter.nevertoomanybooks.datamanager.fieldaccessors.EditTextAccessor;
import com.hardbacknutter.nevertoomanybooks.datamanager.fieldaccessors.EntityListChipGroupAccessor;
import com.hardbacknutter.nevertoomanybooks.datamanager.fieldaccessors.FieldDataAccessor;
import com.hardbacknutter.nevertoomanybooks.datamanager.fieldformatters.FieldFormatter;
import com.hardbacknutter.nevertoomanybooks.dialogs.PartialDatePickerDialogFragment;
import com.hardbacknutter.nevertoomanybooks.dialogs.checklist.CheckListDialogFragment;
import com.hardbacknutter.nevertoomanybooks.entities.Entity;
import com.hardbacknutter.nevertoomanybooks.utils.LocaleUtils;
import com.hardbacknutter.nevertoomanybooks.utils.Money;
import com.hardbacknutter.nevertoomanybooks.utils.ParseUtils;
import com.hardbacknutter.nevertoomanybooks.widgets.DiacriticArrayAdapter;

/**
 * This is the class that manages data and views for an Activity/Fragment;
 * access to the data that each view represents should be handled via this class
 * (and its related classes) where possible.
 * <ul>Features provides are:
 * <li> handling of visibility via preferences / 'mIsUsedKey' property of a field.</li>
 * <li> understanding of kinds of views (setting a Checkbox (Checkable) value to 'true' will work
 * as expected as will setting the value of a Spinner). As new view types are added, it
 * will be necessary to add new {@link FieldDataAccessor} implementations.
 * In some specific circumstances, an accessor can be defined manually.</li>
 * <li> Custom data accessors and formatter to provide application-specific data rules.</li>
 * <li> simplified extraction of data.</li>
 * </ul>
 * <p>
 * Formatter and Accessors
 * <p>
 * It is up to each accessor to decide what to do with any formatter defined for a field.
 * Formatters only really make sense for TextView and EditText elements.
 * Formatters should implement {@link FieldFormatter#format(Context, Object)} where the Object
 * is transformed to a String - DO NOT CHANGE class variables while doing this.
 * In contrast {@link FieldFormatter#apply(Object, View)} CAN change class variables
 * but should leave the real formatter to the format method.
 * <p>
 * This way, other code can access {@link FieldFormatter#format(Context, Object)}
 * without side-effects.
 *
 * <ul>Data flows to and from a view as follows:
 * <li>IN  (no formatter ):<br>
 * {@link FieldDataAccessor#setValue(DataManager)} ->
 * {@link FieldDataAccessor#setValue(Object)} ->
 * populates the View.
 * </li>
 * <li>IN  (with formatter):<br>
 * {@link FieldDataAccessor#setValue(DataManager)} ->
 * {@link FieldDataAccessor#setValue(Object)} ->
 * {@link FieldFormatter#apply(Object, View)} ->
 * populates the View..
 * </li>
 * <li>OUT (no formatter ):
 * View -> {@link FieldDataAccessor#getValue()} ->  {@link FieldDataAccessor#getValue(DataManager)}
 * </li>
 * <li>OUT (with formatter):
 * View ->  {@link FieldFormatter#extract(View)} ->
 * {@link FieldDataAccessor#getValue()} -> {@link FieldDataAccessor#getValue(DataManager)}
 * </li>
 * </ul>
 */
public class Fields {

    /** Common error message. */
    private static final String ERROR_KEY_SHOULD_NOT_BE_EMPTY = "key should not be empty";
    /** the list with all fields. */
    private final SparseArray<Field> mAllFields = new SparseArray<>();

    /** TextEdit fields will be watched. */
    @Nullable
    private AfterFieldChangeListener mAfterFieldChangeListener;

    /**
     * @param listener the listener for field changes
     */
    public void setAfterFieldChangeListener(@Nullable final AfterFieldChangeListener listener) {
        mAfterFieldChangeListener = listener;
    }

    private void afterFieldChange(@NonNull final Field<?> field) {
        if (mAfterFieldChangeListener != null) {
            //noinspection unchecked
            mAfterFieldChangeListener.afterFieldChange(field);
        }
    }

    public boolean isEmpty() {
        return mAllFields.size() == 0;
    }

    /**
     * Define a String field. It will be added as usual, but all read/writes
     * to the {@link DataManager} or {@code Bundle} will be suppressed.
     *
     * @param view      View to use
     * @param entityKey The preference key to check if this Field is used or not.
     *                  Not being in use merely means it's not displayed;
     *                  all functionality (populate, storage...) is still executed.
     *
     * @return The resulting Field.
     */
    @NonNull
    public Field<String> define(@NonNull final View view,
                                @NonNull final String entityKey) {
        Field<String> field = new Field<>(this, view.getId(), "", entityKey, view);
        mAllFields.put(view.getId(), field);
        return field;
    }

    /**
     * Add a field to this collection.
     *
     * @param view View to use
     * @param key  Key used to access a {@link DataManager} or {@code Bundle}.
     *
     * @return The resulting Field.
     */
    @NonNull
    public Field<String> addString(@NonNull final View view,
                                   @NonNull final String key) {
        if (BuildConfig.DEBUG /* always */) {
            // sanity check
            if (key.isEmpty()) {
                throw new IllegalArgumentException(ERROR_KEY_SHOULD_NOT_BE_EMPTY);
            }
        }

        Field<String> field = new Field<>(this, view.getId(), key, key, view);
        mAllFields.put(view.getId(), field);
        return field;
    }

    /**
     * Add a field to this collection.
     *
     * @param view      View to use
     * @param key       Key used to access a {@link DataManager} or {@code Bundle}.
     * @param entityKey The preference key to check if this Field(Entity) is used or not.
     *
     * @return The resulting Field.
     */
    public Field<List<Entity>> addCsvListEntity(@NonNull final View view,
                                                @NonNull final String key,
                                                @NonNull final String entityKey) {
        if (BuildConfig.DEBUG /* always */) {
            // sanity check
            if (key.isEmpty()) {
                throw new IllegalArgumentException(ERROR_KEY_SHOULD_NOT_BE_EMPTY);
            }
        }

        Field<List<Entity>> field = new Field<>(this, view.getId(), key, entityKey, view);
        mAllFields.put(view.getId(), field);
        return field;
    }

    /**
     * Add a Boolean field to this collection.
     *
     * @param view View to use
     * @param key  Key used to access a {@link DataManager} or {@code Bundle}.
     *
     * @return The resulting Field.
     */
    @NonNull
    public Field<Boolean> addBoolean(@NonNull final View view,
                                     @NonNull final String key) {
        if (BuildConfig.DEBUG /* always */) {
            // sanity check
            if (key.isEmpty()) {
                throw new IllegalArgumentException(ERROR_KEY_SHOULD_NOT_BE_EMPTY);
            }
        }

        Field<Boolean> field = new Field<>(this, view.getId(), key, key, view);
        mAllFields.put(view.getId(), field);
        return field;
    }

    /**
     * Add a Float field to this collection.
     *
     * @param view View to use
     * @param key  Key used to access a {@link DataManager} or {@code Bundle}.
     *
     * @return The resulting Field.
     */
    @NonNull
    public Field<Float> addFloat(@NonNull final View view,
                                 @NonNull final String key) {
        if (BuildConfig.DEBUG /* always */) {
            // sanity check
            if (key.isEmpty()) {
                throw new IllegalArgumentException(ERROR_KEY_SHOULD_NOT_BE_EMPTY);
            }
        }

        Field<Float> field = new Field<>(this, view.getId(), key, key, view);
        mAllFields.put(view.getId(), field);
        return field;
    }

    /**
     * Add a {@link Money} field; used for <strong>display only</strong>.
     * Handles both value and currency parts.
     *
     * @param view View to use
     * @param key  Key used to access a {@link DataManager} or {@code Bundle}.
     *
     * @return The resulting Field.
     */
    public Field<Money> addMoney(@NonNull final View view,
                                 @NonNull final String key) {
        if (BuildConfig.DEBUG /* always */) {
            // sanity check
            if (key.isEmpty()) {
                throw new IllegalArgumentException(ERROR_KEY_SHOULD_NOT_BE_EMPTY);
            }
        }

        Field<Money> field = new Field<>(this, view.getId(), key, key, view);
        mAllFields.put(view.getId(), field);
        return field;
    }

    /**
     * Add a the value part of a {@link Money} field; used for <strong>editing only</strong>.
     * The currency part must be handled separately.
     *
     * @param view View to use
     * @param key  Key used to access a {@link DataManager} or {@code Bundle}.
     *
     * @return The resulting Field.
     */
    @SuppressWarnings("UnusedReturnValue")
    @NonNull
    public Field<Double> addMoneyValue(@NonNull final View view,
                                       @NonNull final String key) {
        if (BuildConfig.DEBUG /* always */) {
            // sanity check
            if (key.isEmpty()) {
                throw new IllegalArgumentException(ERROR_KEY_SHOULD_NOT_BE_EMPTY);
            }
        }

        EditTextAccessor<Double> acc = new EditTextAccessor<>();
        acc.setDecimalInput(true);
        // don't reformat after each text change... for double values this
        // is FAR to annoying due to the trailing ".0"
        acc.setEnableReformat(false);

        // Creating this formatter locally as it's a bit dubious
        // Money or Double (as a Number) comes in, Double goes out
        // The main (only) reason to use it is the extract.
        acc.setFormatter(new FieldFormatter<Number>() {
            @NonNull
            @Override
            public String format(@NonNull final Context context,
                                 @Nullable final Number rawValue) {

                if (rawValue == null) {
                    return "";
                }

                double dv = rawValue.doubleValue();
                if (dv == 0.0d) {
                    return "";
                }

                String formatted = String.valueOf(dv);
                if (formatted.endsWith(".0")) {
                    return formatted.substring(0, formatted.length() - 2);
                } else {
                    return formatted;
                }
            }

            @Override
            public void apply(@Nullable final Number rawValue,
                              @NonNull final View view) {
                ((TextView) view).setText(format(view.getContext(), rawValue));
            }

            @NonNull
            @Override
            public Number extract(@NonNull final View view) {
                String sv = ((TextView) view).getText().toString().trim();
                // URGENT: review the use of the Locale: user or system ?
                return ParseUtils.parseDouble(sv, LocaleUtils.getSystemLocale());
            }
        });

        Field<Double> field = new Field<>(this, view.getId(), key, key, acc);
        mAllFields.put(view.getId(), field);
        return field;
    }

    /**
     * Add an Bitmask field to this collection.
     * It's hardwired to use a {@link ChipGroup} and matching formatter.
     *
     * @param view       View to use
     * @param key        Key used to access a {@link DataManager} or {@code Bundle}.
     * @param allValues  map with all possible values
     * @param isEditable {@code true} if the user can edit the field
     *
     * @return The resulting Field.
     */
    @NonNull
    public Field<Integer> addBitmask(@NonNull final View view,
                                     @NonNull final String key,
                                     @NonNull final Map<Integer, String> allValues,
                                     final boolean isEditable) {
        if (BuildConfig.DEBUG /* always */) {
            // sanity check
            if (key.isEmpty()) {
                throw new IllegalArgumentException(ERROR_KEY_SHOULD_NOT_BE_EMPTY);
            }
        }

        BitmaskChipGroupAccessor acc = new BitmaskChipGroupAccessor();
        acc.setBitmask(allValues);
        acc.setEditable(isEditable);

        Field<Integer> field = new Field<>(this, view.getId(), key, key, acc);
        mAllFields.put(view.getId(), field);
        return field;
    }

    /**
     * Add a field to this collection.
     * <p>
     * View: ChipGroup showing a Chip for each element in the value list.
     * <p>
     * Edit: ChipGroup showing a Chip for each element in the <strong>allValues</strong>
     * with the ones in the value list being selected.
     *
     * @param view       View to use
     * @param key        Key used to access a {@link DataManager} or {@code Bundle}.
     * @param entityKey  The preference key to check if this Field is used or not.
     * @param allValues  list with all possible values
     * @param isEditable {@code true} if the user can edit the field
     *
     * @return The resulting Field.
     */
    @NonNull
    public Field<ArrayList<Entity>> addEntityList(@NonNull final View view,
                                                  @NonNull final String key,
                                                  @NonNull final String entityKey,
                                                  @NonNull final List<Entity> allValues,
                                                  final boolean isEditable) {
        if (BuildConfig.DEBUG /* always */) {
            // sanity check
            if (key.isEmpty()) {
                throw new IllegalArgumentException(ERROR_KEY_SHOULD_NOT_BE_EMPTY);
            }
        }

        EntityListChipGroupAccessor acc = new EntityListChipGroupAccessor();
        acc.setList(allValues);
        acc.setEditable(isEditable);

        Field<ArrayList<Entity>> field = new Field<>(this, view.getId(), key, entityKey, acc);
        mAllFields.put(view.getId(), field);
        return field;
    }

    /**
     * Return the Field associated with the passed layout ID.
     *
     * @param <T>     type of Field value.
     * @param fieldId Layout ID
     *
     * @return Associated Field.
     *
     * @throws IllegalArgumentException if the field does not exist.
     */
    @NonNull
    public <T> Field<T> getField(@IdRes final int fieldId)
            throws IllegalArgumentException {
        //noinspection unchecked
        Field<T> field = (Field<T>) mAllFields.get(fieldId);
        if (field != null) {
            return field;
        }

        throw new IllegalArgumentException("fieldId= 0x" + Integer.toHexString(fieldId));
    }

    @NonNull
    public <T> Field<T> getField(@NonNull final View view) {
        return getField(view.getId());
    }

    /**
     * Load all fields from the passed {@link DataManager}.
     *
     * @param dataManager DataManager to load Field objects from.
     */
    public void setAll(@NonNull final DataManager dataManager) {
        for (int f = 0; f < mAllFields.size(); f++) {
            Field field = mAllFields.valueAt(f);
            if (field.isAutoPopulated()) {
                // do NOT call onChanged, as this is the initial load
                field.getAccessor().setValue(dataManager);
            }
        }
    }

    /**
     * Save all fields to the passed {@link DataManager}.
     *
     * @param dataManager DataManager to put Field objects in.
     */
    public void getAll(@NonNull final DataManager dataManager) {
        for (int f = 0; f < mAllFields.size(); f++) {
            Field field = mAllFields.valueAt(f);
            if (field.isAutoPopulated()) {
                field.getAccessor().getValue(dataManager);
            }
        }
    }

    /**
     * Reset all field visibility based on user preferences.
     *
     * @param parent      parent view for all fields.
     * @param hideIfEmpty hide the field if it's empty
     *                    set to {@code true} when displaying; {@code false} when editing.
     * @param keepHidden  keep a field hidden if it's already hidden
     */
    public void resetVisibility(@NonNull final View parent,
                                final boolean hideIfEmpty,
                                final boolean keepHidden) {
        for (int f = 0; f < mAllFields.size(); f++) {
            Field field = mAllFields.valueAt(f);
            field.setVisibility(parent, hideIfEmpty, keepHidden);
        }
    }

    public void setParentView(@NonNull final View parentView) {
        for (int f = 0; f < mAllFields.size(); f++) {
            Field field = mAllFields.valueAt(f);
            field.setParentView(parentView);
        }
    }

    /**
     * added to the Fields collection with (2018-11-11) a simple call to setDirty(true).
     *
     * @param <T> type of Field value.
     */
    public interface AfterFieldChangeListener<T> {

        void afterFieldChange(@NonNull Field<T> field);
    }

    /**
     * Field definition contains all information and methods necessary to manage display and
     * extraction of data in a view.
     *
     * @param <T> type of Field value.
     */
    public static class Field<T> {

        /** Field ID. */
        @IdRes
        private final int mId;

        /**
         * Key used to access a {@link DataManager} or {@code Bundle}.
         * <ul>
         * <li>key is set<br>
         * Data is fetched from the {@link DataManager} (or Bundle), and populated on the screen.
         * Extraction depends on the formatter in use.
         * </li>
         * <li>key is not set ("")<br>
         * field is defined, but data handling must be done manually.
         * </li>
         * </ul>
         * <p>
         * See {@link #isAutoPopulated()}.
         */
        @NonNull
        private final String mKey;

        /**
         * The preference key (field-name) to check if this Field is used or not.
         * i.e. the key to be used for {@code App.isUsed(mIsUsedKey)}.
         */
        @NonNull
        private final String mIsUsedKey;

        /**
         * Accessor to use (automatically defined).
         * Encapsulates the formatter.
         */
        @NonNull
        private final FieldDataAccessor<T> mFieldDataAccessor;

        /** Parent collection. */
        @SuppressWarnings("FieldNotUsedInToString")
        @NonNull
        private final Fields mParentCollection;

        /** Fields that need to follow visibility. */
        @Nullable
        @IdRes
        private int[] mRelatedFields;

        /**
         * Constructor.
         *
         * @param parentCollection Parent collection
         * @param id               for this field.
         * @param key              Key used to access a {@link DataManager} or {@code Bundle}.
         *                         Set to "" to suppress all access.
         * @param entityKey        The preference key to check if this Field is used or not.
         */
        @VisibleForTesting
        public Field(@NonNull final Fields parentCollection,
                     final int id,
                     @NonNull final String key,
                     @NonNull final String entityKey,
                     @NonNull final FieldDataAccessor<T> fieldDataAccessor) {

            mParentCollection = parentCollection;
            mId = id;

            mKey = key;
            mIsUsedKey = entityKey;

            mFieldDataAccessor = fieldDataAccessor;
            mFieldDataAccessor.setField(this);
        }

        /**
         * Constructor.
         *
         * @param parentCollection Parent collection
         * @param id               for this field.
         * @param key              Key used to access a {@link DataManager} or {@code Bundle}.
         *                         Set to "" to suppress all access.
         * @param entityKey        The preference key to check if this Field(Entity) is used or not.
         * @param view             for this field. Is only used to read the type from.
         *                         <strong>NOT cached!</strong>
         */
        @VisibleForTesting
        public Field(@NonNull final Fields parentCollection,
                     final int id,
                     @NonNull final String key,
                     @NonNull final String entityKey,
                     @NonNull final View view) {

            mParentCollection = parentCollection;
            mId = id;

            mKey = key;
            mIsUsedKey = entityKey;

            mFieldDataAccessor = AccessorFactory.createAccessor(view);
            mFieldDataAccessor.setField(this);
        }

        /**
         * The View is set in the constructor, which passes it on to
         * the {@link FieldDataAccessor} which keeps a WeakReference.
         * <p>
         * After a restart of the hosting fragment, we need to set the view again.
         */
        void setParentView(@NonNull final View parentView) {
            View view = parentView.findViewById(mId);
            mFieldDataAccessor.setView(view);
        }

        /**
         * set the field ids which should follow visibility with this Field.
         * <p>
         * <strong>Dev. note:</strong> this could be done using
         * {@link androidx.constraintlayout.widget.Group}
         * but that means creating a group for EACH field. That would be overkill.
         *
         * @param relatedFields labels etc
         *
         * @return Field (for chaining)
         */
        public Field<T> setRelatedFields(@NonNull @IdRes final int... relatedFields) {
            mRelatedFields = relatedFields;
            return this;
        }

        /**
         * Is the field in use; i.e. is it enabled in the user-preferences.
         *
         * @return {@code true} if the field *can* be visible
         */
        public boolean isUsed() {
            return App.isUsed(mIsUsedKey);
        }

        /**
         * <strong>Conditionally</strong> set the visibility for the field and its related fields.
         *
         * @param parent      parent view for all fields.
         * @param hideIfEmpty hide the field if it's empty
         * @param keepHidden  keep a field hidden if it's already hidden
         */
        private void setVisibility(@NonNull final View parent,
                                   final boolean hideIfEmpty,
                                   final boolean keepHidden) {

            View view = parent.findViewById(mId);
            boolean isUsed = App.isUsed(mIsUsedKey);

            int visibility = view.getVisibility();

            // 1. An ImageView always keeps its current visibility, i.e. skips this step.
            // 2. When 'keepHidden' is set, all hidden fields stay hidden.
            // 3. Empty fields are optionally hidden.
            if (!(view instanceof ImageView)
                && (visibility != View.GONE || !keepHidden)) {
                if (isUsed && hideIfEmpty) {
                    if (view instanceof Checkable) {
                        // hide any unchecked Checkable.
                        visibility = ((Checkable) view).isChecked() ? View.VISIBLE : View.GONE;

                    } else {
                        visibility = !mFieldDataAccessor.isEmpty() ? View.VISIBLE : View.GONE;
                    }
                } else {
                    visibility = isUsed ? View.VISIBLE : View.GONE;
                }
                view.setVisibility(visibility);
            }

            // related fields follow main field visibility
            setRelatedFieldsVisibility(parent, visibility);
        }

        /**
         * <strong>Unconditionally</strong> set the visibility for the field and its related fields.
         *
         * @param parent     parent view for all fields.
         * @param visibility to use
         */
        public void setVisibility(@NonNull final View parent,
                                  final int visibility) {

            View view = parent.findViewById(mId);
            view.setVisibility(visibility);

            // related fields follow main field visibility
            setRelatedFieldsVisibility(parent, visibility);
        }

        /**
         * Set the visibility for the related fields.
         *
         * @param parent     parent view for all fields.
         * @param visibility to use
         */
        private void setRelatedFieldsVisibility(@NonNull final View parent,
                                                final int visibility) {
            View view;
            if (mRelatedFields != null) {
                for (int fieldId : mRelatedFields) {
                    view = parent.findViewById(fieldId);
                    if (view != null) {
                        view.setVisibility(visibility);
                    }
                }
            }
        }

        /**
         * Convenience wrapper for {@link FieldDataAccessor#setFormatter(FieldFormatter)}.
         *
         * @param formatter to use
         *
         * @return Field (for chaining)
         */
        public Field<T> setFormatter(@NonNull final FieldFormatter<T> formatter) {
            mFieldDataAccessor.setFormatter(formatter);
            return this;
        }

        @IdRes
        public int getId() {
            return mId;
        }

        @NonNull
        public String getKey() {
            return mKey;
        }

        /**
         * Check if this field can be automatically populated.
         *
         * @return {@code true} if it can
         */
        boolean isAutoPopulated() {
            return !mKey.isEmpty();
        }

        @NonNull
        public FieldDataAccessor<T> getAccessor() {
            return mFieldDataAccessor;
        }

        public void onChanged() {
            mParentCollection.afterFieldChange(this);
        }

        @Override
        @NonNull
        public String toString() {
            return "Field{"
                   + "mId=" + mId
                   + ", mIsUsedKey='" + mIsUsedKey + '\''
                   + ", mKey='" + mKey + '\''
                   + ", mFieldDataAccessor=" + mFieldDataAccessor
                   + ", mRelatedFields=" + Arrays.toString(mRelatedFields)
                   + '}';
        }

        /**
         * Setup an adapter for the AutoCompleteTextView, using the (optional)
         * formatter.
         *
         * <strong>Set the formatter (if needed) BEFORE calling this method</strong>
         *
         * @param fieldView view to connect
         * @param list      with auto complete values
         */
        public void setAutocomplete(@NonNull final AutoCompleteTextView fieldView,
                                    @NonNull final List<String> list) {
            // only bother when it's in use and we have a list
            if (App.isUsed(mIsUsedKey) && !list.isEmpty()) {
                //noinspection unchecked
                FormattedDiacriticArrayAdapter adapter = new FormattedDiacriticArrayAdapter(
                        fieldView.getContext(), list,
                        (FieldFormatter<String>) mFieldDataAccessor.getFormatter());
                fieldView.setAdapter(adapter);
            }
        }

        /**
         * Setup a date picker.
         *
         * <strong>Set the formatter (if needed) BEFORE calling this method</strong>
         *
         * @param fragmentManager to use
         * @param fieldView       view to connect
         * @param dialogTitleId   title of the dialog box.
         * @param todayIfNone     if true, and if the field was empty,
         *                        pre-populate with today's date
         */
        public void addDatePicker(@NonNull final FragmentManager fragmentManager,
                                  @NonNull final View fieldView,
                                  @StringRes final int dialogTitleId,
                                  final boolean todayIfNone) {
            // only bother when it's in use
            if (App.isUsed(mIsUsedKey)) {
                fieldView.setOnClickListener(v -> PartialDatePickerDialogFragment
                        .newInstance(fieldView.getId(), dialogTitleId,
                                     (String) getAccessor().getValue(), todayIfNone)
                        .show(fragmentManager, PartialDatePickerDialogFragment.TAG));
            }
        }

        /**
         * Setup a checklist picker.
         *
         * @param fieldView     view to connect
         * @param dialogTitleId title of the dialog box.
         * @param listGetter    {@link CheckListDialogFragment.ListGetter}
         */
        public void addCheckListPicker(@NonNull final FragmentManager fragmentManager,
                                       @NonNull final View fieldView,
                                       @StringRes final int dialogTitleId,
                                       @NonNull
                                       final CheckListDialogFragment.ListGetter listGetter) {
            // only bother when it's in use
            if (App.isUsed(mIsUsedKey)) {
                fieldView.setOnClickListener(v -> CheckListDialogFragment
                        .newInstance(fieldView.getId(), dialogTitleId,
                                     listGetter.getList())
                        .show(fragmentManager, CheckListDialogFragment.TAG));
            }
        }
    }

    public static class FormattedDiacriticArrayAdapter
            extends DiacriticArrayAdapter<String> {

        @Nullable
        private final FieldFormatter<String> mFormatter;

        /**
         * Constructor.
         *
         * @param context   Current context.
         * @param objects   The objects to represent in the list view
         * @param formatter to use
         */
        FormattedDiacriticArrayAdapter(@NonNull final Context context,
                                       @NonNull final List<String> objects,
                                       @Nullable final FieldFormatter<String> formatter) {
            super(context, R.layout.dropdown_menu_popup_item, 0, objects);
            mFormatter = formatter;
        }

        @Nullable
        @Override
        public String getItem(final int position) {
            if (mFormatter != null) {
                return mFormatter.format(getContext(), super.getItem(position));
            } else {
                return super.getItem(position);
            }
        }
    }
}
