/*
 * @Copyright 2018-2023 HardBackNutter
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
package com.hardbacknutter.nevertoomanybooks.core.widgets.adapters;

import android.content.Context;
import android.content.res.Resources;
import android.view.ContextThemeWrapper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.Filter;
import android.widget.Filterable;
import android.widget.TextView;
import android.widget.ThemedSpinnerAdapter;

import androidx.annotation.ArrayRes;
import androidx.annotation.IdRes;
import androidx.annotation.LayoutRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.regex.Pattern;

/**
 * A copy of the {@link android.widget.ArrayAdapter} code from Android-30, rev. 1 (2021-01-25)
 * Do <strong>NOT</strong> overwrite from a newer version of the above.
 * A diff/merge will be needed.
 * <p>
 * Modified to allow easier extending + easier filter support + some optimizations + annotations.
 * <p>
 * Construct with {@link #ExtArrayAdapter(Context, int, FilterType, List)}
 * or use {@link #setFilterType(FilterType)} to override the default {@link Filter}
 * <p>
 * {@link #getItemText(Object)} can/should be overridden if {@code T} is not a String.
 * This allows a custom conversion to be done instead of the default toString().
 *
 * <ul>
 *      <li>{@link FilterType#Default}:
 *      <br>The Android original.</li>
 *      <li>{@link FilterType#Diacritic}:
 *          <br>Adds diacritic support.
 *          <br>Meant for use with AutoComplete fields.
 *          <br>i.e. the user can type the value in.
 *      </li>
 *      <li>{@link FilterType#Passthrough}:
 *          <br>Meant for use with ExposedDropDownMenu.
 *          <br>i.e. the user can select from a fixed list of values.
 *      </li>
 * </ul>
 *
 * @param <T> type of list item
 */
public class ExtArrayAdapter<T>
        extends BaseAdapter
        implements Filterable, ThemedSpinnerAdapter {

    /**
     * Lock used to modify the content of {@link #mObjects}. Any write operation
     * performed on the array should be synchronized on this lock. This lock is also
     * used by the filter (see {@link #getFilter()} to make a synchronized copy of
     * the original array of data.
     */
    private final Object mLock = new Object();

    @NonNull
    private final LayoutInflater mInflater;
    @NonNull
    private final Context mContext;
    /**
     * The resource indicating what views to inflate to display the content of this
     * array adapter.
     */
    @LayoutRes
    private final int mResource;
    /**
     * If the inflated resource is not a TextView, {@code mFieldId} is used to find
     * a TextView inside the inflated views hierarchy. This field must contain the
     * identifier that matches the one defined in the resource file.
     */
    @IdRes
    private final int mFieldId;
    /** Layout inflater used for {@link #getDropDownView(int, View, ViewGroup)}. */
    @Nullable
    private LayoutInflater mDropDownInflater;
    /**
     * The resource indicating what views to inflate to display the content of this
     * array adapter in a drop down widget.
     */
    @LayoutRes
    private int mDropDownResource;
    /**
     * Contains the list of objects that represent the data of this ArrayAdapter.
     * The content of this list is referred to as "the array" in the documentation.
     */
    @NonNull
    private List<T> mObjects;
    /**
     * Indicates whether the contents of {@link #mObjects} came from static resources.
     */
    private boolean mObjectsFromResources;
    /**
     * Indicates whether or not {@link #notifyDataSetChanged()} must be called whenever
     * {@link #mObjects} is modified.
     */
    private boolean mNotifyOnChange = true;
    /**
     * A copy of the original {@link #mObjects} array, initialized from and then
     * used instead as soon as the {@link #mFilter} {@link Filter} is used.
     * {@link #mObjects} will then only contain the filtered values.
     */
    @Nullable
    private ArrayList<T> mOriginalValues;
    @Nullable
    private Filter mFilter;
    @NonNull
    private FilterType mFilterType = FilterType.Default;

    /**
     * Constructor.
     *
     * @param context  The current context.
     * @param resource The resource ID for a layout file containing a TextView to use when
     *                 instantiating views.
     */
    public ExtArrayAdapter(@NonNull final Context context,
                           @LayoutRes final int resource) {
        this(context, resource, 0, new ArrayList<>());
    }

    /**
     * Constructor.
     *
     * @param context            The current context.
     * @param resource           The resource ID for a layout file containing a layout to use when
     *                           instantiating views.
     * @param textViewResourceId The id of the TextView within the layout resource to be populated
     */
    public ExtArrayAdapter(@NonNull final Context context,
                           @LayoutRes final int resource,
                           @IdRes final int textViewResourceId) {
        this(context, resource, textViewResourceId, new ArrayList<>());
    }

    /**
     * Constructor. This constructor will result in the underlying data collection being
     * immutable, so methods such as {@link #clear()} will throw an exception.
     *
     * @param context  The current context.
     * @param resource The resource ID for a layout file containing a TextView to use when
     *                 instantiating views.
     * @param objects  The objects to represent in the ListView.
     */
    public ExtArrayAdapter(@NonNull final Context context,
                           @LayoutRes final int resource,
                           @NonNull final T[] objects) {
        this(context, resource, 0, Arrays.asList(objects));
    }

    /**
     * Constructor. This constructor will result in the underlying data collection being
     * immutable, so methods such as {@link #clear()} will throw an exception.
     *
     * @param context            The current context.
     * @param resource           The resource ID for a layout file containing a layout to use when
     *                           instantiating views.
     * @param textViewResourceId The id of the TextView within the layout resource to be populated
     * @param objects            The objects to represent in the ListView.
     */
    public ExtArrayAdapter(@NonNull final Context context,
                           @LayoutRes final int resource,
                           @IdRes final int textViewResourceId,
                           @NonNull final T[] objects) {
        this(context, resource, textViewResourceId, Arrays.asList(objects));
    }

    /**
     * Constructor.
     *
     * @param context  The current context.
     * @param resource The resource ID for a layout file containing a TextView to use when
     *                 instantiating views.
     * @param objects  The objects to represent in the ListView.
     */
    public ExtArrayAdapter(@NonNull final Context context,
                           @LayoutRes final int resource,
                           @NonNull final List<T> objects) {
        this(context, resource, 0, objects);
    }

    /**
     * Constructor.
     *
     * @param context    The current context.
     * @param resource   The resource ID for a layout file containing a TextView to use when
     *                   instantiating views.
     * @param filterType to use
     * @param objects    The objects to represent in the ListView.
     */
    public ExtArrayAdapter(@NonNull final Context context,
                           @LayoutRes final int resource,
                           @NonNull final FilterType filterType,
                           @NonNull final List<T> objects) {
        this(context, resource, 0, objects);
        mFilterType = filterType;
    }

    /**
     * Constructor.
     *
     * @param context            The current context.
     * @param resource           The resource ID for a layout file containing a layout to use when
     *                           instantiating views.
     * @param textViewResourceId The id of the TextView within the layout resource to be populated
     * @param objects            The objects to represent in the ListView.
     */
    public ExtArrayAdapter(@NonNull final Context context,
                           @LayoutRes final int resource,
                           @IdRes final int textViewResourceId,
                           @NonNull final List<T> objects) {
        this(context, resource, textViewResourceId, objects, false);
    }

    private ExtArrayAdapter(@NonNull final Context context,
                            @LayoutRes final int resource,
                            @IdRes final int textViewResourceId,
                            @NonNull final List<T> objects,
                            final boolean objectsFromResources) {
        mContext = context;
        mInflater = LayoutInflater.from(context);
        mResource = resource;
        mDropDownResource = resource;
        mObjects = objects;
        mObjectsFromResources = objectsFromResources;
        mFieldId = textViewResourceId;
    }

    /**
     * Creates a new ExtArrayAdapter from external resources. The content of the array is
     * obtained through {@link android.content.res.Resources#getTextArray(int)}.
     * <p>
     * The method name should really be "createFromTextArray".
     *
     * @param context        The current context.
     * @param resource       The resource ID for a layout file containing a layout to use when
     *                       instantiating views.
     * @param filterType     to use
     * @param textArrayResId The identifier of the array to use as the data source.
     *
     * @return An {@code ExtArrayAdapter<CharSequence>}
     */
    @NonNull
    public static ExtArrayAdapter<CharSequence> createFromResource(
            @NonNull final Context context,
            @LayoutRes final int resource,
            @NonNull final FilterType filterType,
            @ArrayRes final int textArrayResId) {

        final CharSequence[] strings = context.getResources().getTextArray(textArrayResId);

        final ExtArrayAdapter<CharSequence> adapter = new ExtArrayAdapter<>(
                context, resource, 0, Arrays.asList(strings), true);
        adapter.setFilterType(filterType);
        return adapter;
    }

    /**
     * Adds the specified object at the end of the array.
     *
     * @param object The object to add at the end of the array.
     *
     * @throws UnsupportedOperationException if the underlying data collection is immutable
     */
    public void add(@Nullable final T object) {
        synchronized (mLock) {
            if (mOriginalValues != null) {
                mOriginalValues.add(object);
            } else {
                mObjects.add(object);
            }
            mObjectsFromResources = false;
        }
        if (mNotifyOnChange) {
            notifyDataSetChanged();
        }
    }

    /**
     * Adds the specified Collection at the end of the array.
     *
     * @param collection The Collection to add at the end of the array.
     *
     * @throws UnsupportedOperationException if the <tt>addAll</tt> operation
     *                                       is not supported by this list
     * @throws ClassCastException            if the class of an element of the specified
     *                                       collection prevents it from being added to this list
     * @throws NullPointerException          if the specified collection contains one or more
     *                                       null elements and this list does not permit null
     *                                       elements, or if the specified collection is null
     * @throws IllegalArgumentException      if some property of an element of the specified
     *                                       collection prevents it from being added to this list
     */
    public void addAll(@NonNull final Collection<? extends T> collection) {
        synchronized (mLock) {
            if (mOriginalValues != null) {
                mOriginalValues.addAll(collection);
            } else {
                mObjects.addAll(collection);
            }
            mObjectsFromResources = false;
        }
        if (mNotifyOnChange) {
            notifyDataSetChanged();
        }
    }

    /**
     * Adds the specified items at the end of the array.
     *
     * @param items The items to add at the end of the array.
     *
     * @throws UnsupportedOperationException if the underlying data collection is immutable
     */
    public void addAll(@NonNull final T... items) {
        synchronized (mLock) {
            Collections.addAll(Objects.requireNonNullElseGet(mOriginalValues, () -> mObjects),
                               items);
            mObjectsFromResources = false;
        }
        if (mNotifyOnChange) {
            notifyDataSetChanged();
        }
    }

    /**
     * Inserts the specified object at the specified index in the array.
     *
     * @param object The object to insert into the array.
     * @param index  The index at which the object must be inserted.
     *
     * @throws UnsupportedOperationException if the underlying data collection is immutable
     */
    public void insert(@Nullable final T object,
                       final int index) {
        synchronized (mLock) {
            if (mOriginalValues != null) {
                mOriginalValues.add(index, object);
            } else {
                mObjects.add(index, object);
            }
            mObjectsFromResources = false;
        }
        if (mNotifyOnChange) {
            notifyDataSetChanged();
        }
    }

    /**
     * Removes the specified object from the array.
     *
     * @param object The object to remove.
     *
     * @throws UnsupportedOperationException if the underlying data collection is immutable
     */
    public void remove(@Nullable final T object) {
        synchronized (mLock) {
            if (mOriginalValues != null) {
                mOriginalValues.remove(object);
            } else {
                mObjects.remove(object);
            }
            mObjectsFromResources = false;
        }
        if (mNotifyOnChange) {
            notifyDataSetChanged();
        }
    }

    /**
     * Remove all elements from the list.
     *
     * @throws UnsupportedOperationException if the underlying data collection is immutable
     */
    public void clear() {
        synchronized (mLock) {
            if (mOriginalValues != null) {
                mOriginalValues.clear();
            } else {
                mObjects.clear();
            }
            mObjectsFromResources = false;
        }
        if (mNotifyOnChange) {
            notifyDataSetChanged();
        }
    }

    /**
     * Sorts the content of this adapter using the specified comparator.
     *
     * @param comparator The comparator used to sort the objects contained
     *                   in this adapter.
     */
    public void sort(@NonNull final Comparator<? super T> comparator) {
        synchronized (mLock) {
            if (mOriginalValues != null) {
                mOriginalValues.sort(comparator);
            } else {
                mObjects.sort(comparator);
            }
        }
        if (mNotifyOnChange) {
            notifyDataSetChanged();
        }
    }

    @Override
    public void notifyDataSetChanged() {
        super.notifyDataSetChanged();
        mNotifyOnChange = true;
    }

    /**
     * Control whether methods that change the list ({@link #add}, {@link #addAll(Collection)},
     * {@link #addAll(Object[])}, {@link #insert}, {@link #remove}, {@link #clear},
     * {@link #sort(Comparator)}) automatically call {@link #notifyDataSetChanged}.  If set to
     * false, caller must manually call notifyDataSetChanged() to have the changes
     * reflected in the attached view.
     * <p>
     * The default is true, and calling notifyDataSetChanged()
     * resets the flag to true.
     *
     * @param notifyOnChange if true, modifications to the list will
     *                       automatically call {@link
     *                       #notifyDataSetChanged}
     */
    public void setNotifyOnChange(final boolean notifyOnChange) {
        mNotifyOnChange = notifyOnChange;
    }

    /**
     * Returns the context associated with this array adapter. The context is used
     * to create views from the resource passed to the constructor.
     *
     * @return The Context associated with this adapter.
     */
    @NonNull
    public Context getContext() {
        return mContext;
    }

    @Override
    public int getCount() {
        return mObjects.size();
    }

    @Override
    @Nullable
    public T getItem(final int position) {
        return mObjects.get(position);
    }

    /**
     * Returns the position of the specified item in the array.
     *
     * @param item The item to retrieve the position of.
     *
     * @return The position of the specified item.
     */
    public int getPosition(@Nullable final T item) {
        return mObjects.indexOf(item);
    }

    @Override
    public long getItemId(final int position) {
        return position;
    }

    @NonNull
    private View createViewFromResource(@NonNull final LayoutInflater inflater,
                                        @Nullable final View convertView,
                                        @NonNull final ViewGroup parent,
                                        final int resource) {
        final View view;
        if (convertView == null) {
            view = inflater.inflate(resource, parent, false);
        } else {
            view = convertView;
        }
        return view;
    }

    @NonNull
    private TextView findTextView(@NonNull final View view) {
        final TextView textView;
        try {
            if (mFieldId == 0) {
                //  If no custom field is assigned, assume the whole resource is a TextView
                textView = (TextView) view;
            } else {
                //  Otherwise, find the TextView field within the layout
                textView = view.findViewById(mFieldId);

                if (textView == null) {
                    throw new RuntimeException("Failed to find view with ID "
                                               + mContext.getResources().getResourceName(mFieldId)
                                               + " in item layout");
                }
            }
        } catch (@NonNull final ClassCastException e) {
            throw new IllegalStateException(
                    "ExtArrayAdapter requires the resource ID to be a TextView", e);
        }
        return textView;
    }

    /**
     * Override to provide a custom string conversion instead of the default toString().
     *
     * @param item to convert
     *
     * @return stringified item
     */
    @NonNull
    protected CharSequence getItemText(@Nullable final T item) {
        if (item instanceof CharSequence) {
            return (CharSequence) item;
        } else if (item != null) {
            return item.toString();
        } else {
            return "";
        }
    }

    @Override
    @NonNull
    public View getView(final int position,
                        @Nullable final View convertView,
                        @NonNull final ViewGroup parent) {
        final View view = createViewFromResource(mInflater, convertView, parent, mResource);
        final TextView textView = findTextView(view);

        final T item = getItem(position);
        textView.setText(getItemText(item));

        return view;
    }

    /**
     * <p>Sets the layout resource to create the drop down views.</p>
     *
     * @param resource the layout resource defining the drop down views
     *
     * @see #getDropDownView(int, android.view.View, android.view.ViewGroup)
     */
    public void setDropDownViewResource(@LayoutRes final int resource) {
        mDropDownResource = resource;
    }

    @Override
    @Nullable
    public Resources.Theme getDropDownViewTheme() {
        //noinspection ReturnOfInnerClass
        return mDropDownInflater == null ? null : mDropDownInflater.getContext().getTheme();
    }

    /**
     * Sets the {@link Resources.Theme} against which drop-down views are
     * inflated.
     * <p>
     * By default, drop-down views are inflated against the theme of the
     * {@link Context} passed to the adapter's constructor.
     *
     * @param theme the theme against which to inflate drop-down views or
     *              {@code null} to use the theme from the adapter's context
     *
     * @see #getDropDownView(int, View, ViewGroup)
     */
    @Override
    public void setDropDownViewTheme(@Nullable final Resources.Theme theme) {
        if (theme == null) {
            mDropDownInflater = null;
        } else if (theme == mInflater.getContext().getTheme()) {
            mDropDownInflater = mInflater;
        } else {
            final Context context = new ContextThemeWrapper(mContext, theme);
            mDropDownInflater = LayoutInflater.from(context);
        }
    }

    @Override
    @NonNull
    public View getDropDownView(final int position,
                                @Nullable final View convertView,
                                @NonNull final ViewGroup parent) {
        final LayoutInflater inflater = mDropDownInflater == null ? mInflater : mDropDownInflater;
        final View view = createViewFromResource(inflater, convertView, parent, mDropDownResource);
        final TextView textView = findTextView(view);

        final T item = getItem(position);
        textView.setText(getItemText(item));

        return view;
    }

    public void setFilterType(@NonNull final FilterType filterType) {
        mFilterType = filterType;
    }

    @Override
    @NonNull
    public Filter getFilter() {
        if (mFilter == null) {
            switch (mFilterType) {
                case Diacritic:
                    mFilter = new DiacriticArrayFilter();
                    break;
                case Passthrough:
                    mFilter = new PassthroughFilter();
                    break;

                case Default:
                default:
                    mFilter = new ArrayFilter();
                    break;
            }
        }
        return mFilter;
    }

    /**
     * Gets a string representation of the adapter data that can help
     * {@link android.service.autofill.AutofillService} autofill the view backed by the adapter.
     *
     * @return values from the string array used by
     *         {@link #createFromResource(Context, int, FilterType, int)},
     *         or {@code null} if object was created otherwise or if contents
     *         were dynamically changed after creation.
     */
    @Nullable
    @Override
    public CharSequence[] getAutofillOptions() {
        // First check if app developer explicitly set them.
        final CharSequence[] explicitOptions = super.getAutofillOptions();
        if (explicitOptions != null) {
            return explicitOptions;
        }

        // Otherwise, only return options that came from static resources.
        if (!mObjectsFromResources || mObjects.isEmpty()) {
            return null;
        }

        return mObjects.stream()
                       .map(this::getItemText)
                       .toArray(CharSequence[]::new);
    }

    /** Builtin Filters. */
    public enum FilterType {
        /** The original {@link android.widget.ArrayAdapter} filter. */
        Default,
        /** Extended default filter with Diacritic support. */
        Diacritic,
        /** Does no actual filtering. */
        Passthrough
    }

    /**
     * The original.
     *
     * <p>An array filter constrains the content of the array adapter with
     * a prefix. Each item that does not start with the supplied prefix
     * is removed from the list.</p>
     */
    private class ArrayFilter
            extends AbstractArrayFilter {

        @Override
        @NonNull
        protected FilterResults performFiltering(@Nullable final CharSequence prefix) {
            final ArrayList<T> values;
            synchronized (mLock) {
                if (mOriginalValues == null) {
                    mOriginalValues = new ArrayList<>(mObjects);
                }
                values = new ArrayList<>(mOriginalValues);
            }

            final FilterResults results = new FilterResults();

            if (prefix == null || prefix.length() == 0) {
                results.values = values;
                results.count = values.size();
            } else {
                final String prefixString = prefix.toString().toLowerCase(Locale.getDefault());

                final int count = values.size();
                final List<T> newValues = new ArrayList<>();

                for (int i = 0; i < count; i++) {
                    final T value = values.get(i);
                    final String valueText = getItemText(value)
                            .toString().toLowerCase(Locale.getDefault());

                    // First match against the whole, non-split value
                    if (valueText.startsWith(prefixString)) {
                        newValues.add(value);
                    } else {
                        final String[] words = valueText.split(" ");
                        for (final String word : words) {
                            if (word.startsWith(prefixString)) {
                                newValues.add(value);
                                break;
                            }
                        }
                    }
                }

                results.values = newValues;
                results.count = newValues.size();
            }

            return results;
        }
    }

    /**
     * This versions also filters on Diacritic characters.
     *
     * <p>An array filter constrains the content of the array adapter with
     * a prefix. Each item that does not start with the supplied prefix
     * is removed from the list.</p>
     */
    private class DiacriticArrayFilter
            extends AbstractArrayFilter {

        private final Pattern diacriticsPattern = Pattern.compile("[^\\p{ASCII}]");

        @Override
        @NonNull
        protected FilterResults performFiltering(@Nullable final CharSequence prefix) {
            final ArrayList<T> values;
            synchronized (mLock) {
                if (mOriginalValues == null) {
                    mOriginalValues = new ArrayList<>(mObjects);
                }
                values = new ArrayList<>(mOriginalValues);
            }

            final FilterResults results = new FilterResults();

            if (prefix == null || prefix.length() == 0) {
                results.values = values;
                results.count = values.size();
            } else {
                final String prefixString = prefix.toString().toLowerCase(Locale.getDefault());
                final String ndPrefixString = toAsciiLowerCase(prefixString);

                final int count = values.size();
                final Collection<T> newValues = new ArrayList<>();

                for (int i = 0; i < count; i++) {
                    final T value = values.get(i);
                    final String valueText = getItemText(value)
                            .toString().toLowerCase(Locale.getDefault());

                    // First match against the whole, non-split value
                    if (valueText.startsWith(prefixString)
                        || toAsciiLowerCase(valueText).startsWith(ndPrefixString)) {
                        newValues.add(value);
                    } else {
                        final String[] words = valueText.split(" ");
                        for (final String word : words) {
                            if (word.startsWith(prefixString)
                                || toAsciiLowerCase(word).startsWith(ndPrefixString)) {
                                newValues.add(value);
                                break;
                            }
                        }
                    }
                }

                results.values = newValues;
                results.count = newValues.size();
            }

            return results;
        }

        /**
         * Normalize a given string to contain only lower case ASCII characters.
         *
         * @param text to normalize
         *
         * @return ascii text
         */
        @NonNull
        private String toAsciiLowerCase(@NonNull final CharSequence text) {
            return diacriticsPattern.matcher(Normalizer.normalize(text, Normalizer.Form.NFD))
                                    .replaceAll("")
                                    .toLowerCase(Locale.getDefault());
        }
    }

    /**
     * Does no actual filtering.
     * Should be used with the Material ExposedDropDownMenu
     */
    private class PassthroughFilter
            extends AbstractArrayFilter {

        @Override
        @NonNull
        protected FilterResults performFiltering(@Nullable final CharSequence constraint) {
            // Mimic other filter behaviour for maximum compatibility
            final List<T> values;
            synchronized (mLock) {
                if (mOriginalValues == null) {
                    mOriginalValues = new ArrayList<>(mObjects);
                }
                values = new ArrayList<>(mOriginalValues);
            }

            final FilterResults results = new FilterResults();
            results.values = values;
            results.count = values.size();
            return results;
        }
    }

    private abstract class AbstractArrayFilter
            extends Filter {

        @Override
        @NonNull
        public CharSequence convertResultToString(@Nullable final Object resultValue) {
            //noinspection unchecked
            return getItemText((T) resultValue);
        }

        @Override
        protected void publishResults(@Nullable final CharSequence constraint,
                                      @NonNull final FilterResults results) {
            //noinspection unchecked
            mObjects = (List<T>) results.values;
            if (results.count > 0) {
                notifyDataSetChanged();
            } else {
                notifyDataSetInvalidated();
            }
        }
    }
}

