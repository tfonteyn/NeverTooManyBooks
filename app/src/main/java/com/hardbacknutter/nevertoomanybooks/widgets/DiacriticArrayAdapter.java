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
package com.hardbacknutter.nevertoomanybooks.widgets;

import android.content.Context;
import android.content.res.Resources;
import android.util.Log;
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
import java.util.regex.Pattern;

/**
 * A copy of the {@link android.widget.ArrayAdapter} code from Android-29, rev. 4 (2020-01-07)
 * <p>
 * Modified to support diacritics. Do not blindly overwrite from a newer version of the above.
 * Source code modification can be found by searching by "diacritics"; being part
 * of variable/method names.
 *
 * @param <T> type of objects in the list
 */
public class DiacriticArrayAdapter<T>
        extends BaseAdapter
        implements Filterable, ThemedSpinnerAdapter {

    /**
     * Lock used to modify the content of {@link #mObjects}. Any write operation
     * performed on the array should be synchronized on this lock. This lock is also
     * used by the filter (see {@link #getFilter()} to make a synchronized copy of
     * the original array of data.
     * UnsupportedAppUsage
     */
    private final Object mLock = new Object();

    private final LayoutInflater mInflater;

    private final Context mContext;

    /**
     * The resource indicating what views to inflate to display the content of this
     * array adapter.
     */
    private final int mResource;
    /**
     * If the inflated resource is not a TextView, {@code mFieldId} is used to find
     * a TextView inside the inflated views hierarchy. This field must contain the
     * identifier that matches the one defined in the resource file.
     */
    private final int mFieldId;
    /**
     * The resource indicating what views to inflate to display the content of this
     * array adapter in a drop down widget.
     */
    private int mDropDownResource;
    /**
     * Contains the list of objects that represent the data of this ArrayAdapter.
     * The content of this list is referred to as "the array" in the documentation.
     * UnsupportedAppUsage
     */
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

    // A copy of the original mObjects array, initialized from and then used instead as soon as
    // the mFilter ArrayFilter is used. mObjects will then only contain the filtered values.
    //UnsupportedAppUsage
    private ArrayList<T> mOriginalValues;
    private ArrayFilter mFilter;

    /** Layout inflater used for {@link #getDropDownView(int, View, ViewGroup)}. */
    private LayoutInflater mDropDownInflater;

    /**
     * Constructor.
     *
     * @param context  The current context.
     * @param resource The resource ID for a layout file containing a TextView to use when
     *                 instantiating views.
     */
    @SuppressWarnings("unused")
    public DiacriticArrayAdapter(@NonNull final Context context,
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
    @SuppressWarnings("unused")
    public DiacriticArrayAdapter(@NonNull final Context context,
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
    @SuppressWarnings("unused")
    public DiacriticArrayAdapter(@NonNull final Context context,
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
    @SuppressWarnings("unused")
    public DiacriticArrayAdapter(@NonNull final Context context,
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
    public DiacriticArrayAdapter(@NonNull final Context context,
                                 @LayoutRes final int resource,
                                 @NonNull final List<T> objects) {
        this(context, resource, 0, objects);
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
    protected DiacriticArrayAdapter(@NonNull final Context context,
                                    @LayoutRes final int resource,
                                    @IdRes final int textViewResourceId,
                                    @NonNull final List<T> objects) {
        this(context, resource, textViewResourceId, objects, false);
    }

    private DiacriticArrayAdapter(@NonNull final Context context,
                                  @LayoutRes final int resource,
                                  @IdRes final int textViewResourceId,
                                  @NonNull final List<T> objects,
                                  final boolean objectsFromResources) {
        mContext = context;
        mInflater = LayoutInflater.from(context);
        mResource = mDropDownResource = resource;
        mObjects = objects;
        mObjectsFromResources = objectsFromResources;
        mFieldId = textViewResourceId;
    }

    /**
     * Creates a new DiacriticArrayAdapter from external resources. The content of the array is
     * obtained through {@link android.content.res.Resources#getTextArray(int)}.
     *
     * @param context        The application's environment.
     * @param textArrayResId The identifier of the array to use as the data source.
     * @param textViewResId  The identifier of the layout used to create views.
     *
     * @return a DiacriticArrayAdapter&lt;CharSequence&gt;.
     */
    @SuppressWarnings({"WeakerAccess", "unused"})
    @NonNull
    public static DiacriticArrayAdapter<CharSequence> createFromResource(
            @NonNull final Context context,
            @ArrayRes final int textArrayResId,
            @LayoutRes final int textViewResId) {
        final CharSequence[] strings = context.getResources().getTextArray(textArrayResId);
        return new DiacriticArrayAdapter<>(context, textViewResId, 0, Arrays.asList(strings), true);
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
     * @throws NullPointerException          if the specified collection contains one
     *                                       or more null elements and this list does not permit
     *                                       null
     *                                       elements, or if the specified collection is null
     * @throws IllegalArgumentException      if some property of an element of the
     *                                       specified collection prevents it from being added to
     *                                       this list
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
    @SuppressWarnings("unchecked")
    public void addAll(@NonNull final T... items) {
        synchronized (mLock) {
            if (mOriginalValues != null) {
                Collections.addAll(mOriginalValues, items);
            } else {
                Collections.addAll(mObjects, items);
            }
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
                Collections.sort(mOriginalValues, comparator);
            } else {
                Collections.sort(mObjects, comparator);
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
    @SuppressWarnings("unused")
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
    protected Context getContext() {
        return mContext;
    }

    @Override
    public int getCount() {
        return mObjects.size();
    }

    @Nullable
    @Override
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
    @SuppressWarnings("unused")
    public int getPosition(@Nullable final T item) {
        return mObjects.indexOf(item);
    }

    @Override
    public long getItemId(final int position) {
        return position;
    }

    @NonNull
    @Override
    public View getView(final int position,
                        @Nullable final View convertView,
                        @NonNull final ViewGroup parent) {
        return createViewFromResource(mInflater, position, convertView, parent, mResource);
    }

    @NonNull
    private View createViewFromResource(@NonNull final LayoutInflater inflater,
                                        final int position,
                                        @Nullable final View convertView,
                                        @NonNull final ViewGroup parent,
                                        final int resource) {
        final View view;
        final TextView text;

        if (convertView == null) {
            view = inflater.inflate(resource, parent, false);
        } else {
            view = convertView;
        }

        try {
            if (mFieldId == 0) {
                //  If no custom field is assigned, assume the whole resource is a TextView
                text = (TextView) view;
            } else {
                //  Otherwise, find the TextView field within the layout
                text = view.findViewById(mFieldId);

                if (text == null) {
                    throw new RuntimeException("Failed to find view with ID "
                                               + mContext.getResources().getResourceName(mFieldId)
                                               + " in item layout");
                }
            }
        } catch (@NonNull final ClassCastException e) {
            Log.e("DiacriticArrayAdapter", "You must supply a resource ID for a TextView");
            throw new IllegalStateException(
                    "DiacriticArrayAdapter requires the resource ID to be a TextView", e);
        }

        final T item = getItem(position);
        if (item instanceof CharSequence) {
            text.setText((CharSequence) item);
        } else if (item != null) {
            text.setText(item.toString());
        } else {
            text.setText("");
        }

        return view;
    }

    /**
     * <p>Sets the layout resource to create the drop down views.</p>
     *
     * @param resource the layout resource defining the drop down views
     *
     * @see #getDropDownView(int, android.view.View, android.view.ViewGroup)
     */
    @SuppressWarnings("unused")
    public void setDropDownViewResource(@LayoutRes final int resource) {
        this.mDropDownResource = resource;
    }

    @Nullable
    @Override
    public Resources.Theme getDropDownViewTheme() {
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
    public View getDropDownView(final int position,
                                @Nullable final View convertView,
                                @NonNull final ViewGroup parent) {
        final LayoutInflater inflater = mDropDownInflater == null ? mInflater : mDropDownInflater;
        return createViewFromResource(inflater, position, convertView, parent, mDropDownResource);
    }

    @NonNull
    @Override
    public Filter getFilter() {
        if (mFilter == null) {
            mFilter = new ArrayFilter();
        }
        return mFilter;
    }

    /**
     * @return values from the string array used by {@link #createFromResource(Context, int, int)},
     * or {@code null} if object was created otherwise or if contents were dynamically changed after
     * creation.
     */
    @Override
    public CharSequence[] getAutofillOptions() {
        // First check if app developer explicitly set them.
        final CharSequence[] explicitOptions = super.getAutofillOptions();
        if (explicitOptions != null) {
            return explicitOptions;
        }

        // Otherwise, only return options that came from static resources.
        if (!mObjectsFromResources || mObjects == null || mObjects.isEmpty()) {
            return null;
        }
        final int size = mObjects.size();
        final CharSequence[] options = new CharSequence[size];
        //noinspection SuspiciousToArrayCall
        mObjects.toArray(options);
        return options;
    }

    /**
     * <p>An array filter constrains the content of the array adapter with
     * a prefix. Each item that does not start with the supplied prefix
     * is removed from the list.</p>
     */
    private class ArrayFilter
            extends Filter {

        private final Pattern DIACRITICS_REGEX = Pattern.compile("[^\\p{ASCII}]");

        @Override
        protected FilterResults performFiltering(@Nullable final CharSequence prefix) {
            final FilterResults results = new FilterResults();

            if (mOriginalValues == null) {
                synchronized (mLock) {
                    mOriginalValues = new ArrayList<>(mObjects);
                }
            }

            if (prefix == null || prefix.length() == 0) {
                final ArrayList<T> list;
                synchronized (mLock) {
                    list = new ArrayList<>(mOriginalValues);
                }
                results.values = list;
                results.count = list.size();
            } else {
                //noinspection StringToUpperCaseOrToLowerCaseWithoutLocale
                final String prefixString = prefix.toString().toLowerCase();

                final ArrayList<T> values;
                synchronized (mLock) {
                    values = new ArrayList<>(mOriginalValues);
                }

                final int count = values.size();
                final Collection<T> newValues = new ArrayList<>();

                for (int i = 0; i < count; i++) {
                    final T value = values.get(i);
                    //noinspection StringToUpperCaseOrToLowerCaseWithoutLocale
                    final String valueText = value.toString().toLowerCase();

                    String valueTextNoDiacritics = filterDiacritics(valueText);
                    String prefixStringNoDiacritics = filterDiacritics(prefixString);

                    // First match against the whole, non-split value
                    if (valueText.startsWith(prefixString)
                        || valueTextNoDiacritics.startsWith(prefixStringNoDiacritics)) {
                        newValues.add(value);
                    } else {
                        final String[] words = valueText.split(" ");
                        for (String word : words) {
                            if (word.startsWith(prefixString)
                                || valueTextNoDiacritics.startsWith(prefixStringNoDiacritics)) {
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
         * Normalize a given string to contain only ASCII characters.
         *
         * @param text to normalize
         *
         * @return ascii text
         */
        private String filterDiacritics(@NonNull final CharSequence text) {
            //noinspection StringToUpperCaseOrToLowerCaseWithoutLocale
            return DIACRITICS_REGEX.matcher(Normalizer.normalize(text, Normalizer.Form.NFD))
                                   .replaceAll("")
                                   .toLowerCase();
        }

        @Override
        protected void publishResults(final CharSequence constraint,
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
