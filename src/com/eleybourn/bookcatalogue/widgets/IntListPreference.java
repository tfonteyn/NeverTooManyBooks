package com.eleybourn.bookcatalogue.widgets;

import android.content.Context;
import android.content.res.Resources;
import android.content.res.TypedArray;
import android.os.Parcel;
import android.os.Parcelable;
import android.util.AttributeSet;
import android.util.TypedValue;

import androidx.annotation.ArrayRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.preference.DialogPreference;
import androidx.preference.Preference;

import com.eleybourn.bookcatalogue.debug.Logger;

/**
 * A {@link Preference} that displays a list of entries as
 * a dialog.
 * <p>
 * This preference will store an int into the SharedPreferences. This int will be the value
 * from the {@link #setEntryValues(int[])} array.
 */
public class IntListPreference
        extends DialogPreference {

    private static final int DEFAULT_WHEN_NO_DEFAULT_SET = Integer.MIN_VALUE;

    private static final String ANDROID_NS = "http://schemas.android.com/apk/res/android";

    private CharSequence[] mEntries;
    private int[] mEntryValues;
    private int mValue;
    private String mSummary;
    private boolean mValueSet;

    public IntListPreference(final Context context,
                             final AttributeSet attrs,
                             final int defStyleAttr,
                             final int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);

        final Resources res = context.getResources();

        final int entriesResId = attrs.getAttributeResourceValue(ANDROID_NS, "entries", 0);
        mEntries = res.getTextArray(entriesResId);

        int valuesResId = attrs.getAttributeResourceValue(ANDROID_NS, "entryValues", 0);
        mEntryValues = res.getIntArray(valuesResId);

        int summaryResId = attrs.getAttributeResourceValue(ANDROID_NS, "summary", 0);
        if (summaryResId != 0) {
            mSummary = res.getString(summaryResId);
        }
    }

    public IntListPreference(final Context context,
                             final AttributeSet attrs,
                             final int defStyleAttr) {
        this(context, attrs, defStyleAttr, 0);
    }

    public IntListPreference(final Context context,
                             final AttributeSet attrs) {
        this(context, attrs,
             TypedArrayUtils_getAttr(context, androidx.preference.R.attr.dialogPreferenceStyle,
                                     android.R.attr.dialogPreferenceStyle));
    }

    public IntListPreference(final Context context) {
        this(context, null);
    }

    public static int TypedArrayUtils_getAttr(@NonNull final Context context,
                                              final int attr,
                                              final int fallbackAttr) {
        TypedValue value = new TypedValue();
        context.getTheme().resolveAttribute(attr, value, true);
        if (value.resourceId != 0) {
            return attr;
        }
        return fallbackAttr;
    }

    /**
     * Sets the human-readable entries to be shown in the list.
     * This will be shown in subsequent dialogs.
     * <p>
     * Each entry must have a corresponding index in
     * {@link #setEntryValues(int[])}.
     *
     * @param entries The entries.
     *
     * @see #setEntryValues(int[])
     */
    public void setEntries(@NonNull final CharSequence[] entries) {
        mEntries = entries;
    }

    /**
     * The list of entries to be shown in the list in subsequent dialogs.
     *
     * @return The list as an array.
     */
    public CharSequence[] getEntries() {
        return mEntries;
    }

    /**
     * @param entriesResId The entries array as a resource.
     *
     * @see #setEntries(CharSequence[])
     */
    public void setEntries(@ArrayRes final int entriesResId) {
        setEntries(getContext().getResources().getTextArray(entriesResId));
    }

    /**
     * The array to find the value to save for a preference when an entry from
     * entries is selected. If a user clicks on the second item in entries, the
     * second item in this array will be saved to the preference.
     *
     * @param entryValues The array to be used as values to save for the preference.
     */
    public void setEntryValues(final int[] entryValues) {
        mEntryValues = entryValues;
    }

    /**
     * Returns the array of values to be saved for the preference.
     *
     * @return The array of values.
     */
    public int[] getEntryValues() {
        return mEntryValues;
    }

    /**
     * @param entryValuesResId The entry values array as a resource.
     *
     * @see #setEntryValues(int[])
     */
    public void setEntryValues(@ArrayRes final int entryValuesResId) {
        setEntryValues(getContext().getResources().getIntArray(entryValuesResId));
    }

    /**
     * Returns the summary of this ListPreference. If the summary
     * has a {@linkplain java.lang.String#format String formatting}
     * marker in it (i.e. "%s" or "%1$s"), then the current entry
     * value will be substituted in its place.
     *
     * @return the summary with appropriate string substitution
     */
    @Override
    public CharSequence getSummary() {
        final CharSequence entry = getEntry();
        if (mSummary == null) {
            return super.getSummary();
        } else {
            return String.format(mSummary, entry == null ? "" : entry);
        }
    }

    /**
     * Sets the summary for this Preference with a CharSequence.
     * If the summary has a
     * {@linkplain java.lang.String#format String formatting}
     * marker in it (i.e. "%s" or "%1$s"), then the current entry
     * value will be substituted in its place when it's retrieved.
     *
     * @param summary The summary for the preference.
     */
    @Override
    public void setSummary(@Nullable final CharSequence summary) {
        super.setSummary(summary);
        if (summary == null && mSummary != null) {
            mSummary = null;
        } else if (summary != null && !summary.equals(mSummary)) {
            mSummary = summary.toString();
        }
    }

    /**
     * Returns the value of the key. This should be one of the entries in
     * {@link #getEntryValues()}.
     *
     * @return The value of the key.
     */
    public int getValue() {
        return mValue;
    }

    /**
     * Sets the value of the key. This should be one of the entries in
     * {@link #getEntryValues()}.
     *
     * @param value The value to set for the key.
     */
    public void setValue(final int value) {
        // Always persist/notify the first time.
        final boolean changed = (mValue != value);
        if (changed || !mValueSet) {
            mValue = value;
            mValueSet = true;
            persistInt(value);
            if (changed) {
                notifyChanged();
            }
        }
    }

    /**
     * Returns the entry corresponding to the current value.
     *
     * @return The entry corresponding to the current value, or null.
     */
    @Nullable
    public CharSequence getEntry() {
        int index = getValueIndex();
        return index >= 0 && mEntries != null ? mEntries[index] : null;
    }

    /**
     * Returns the index of the given value (in the entry values array).
     *
     * @param value The value whose index should be returned.
     *
     * @return The index of the value, or -1 if not found.
     */
    public int findIndexOfValue(final int value) {
        if (mEntryValues != null) {
            for (int i = mEntryValues.length - 1; i >= 0; i--) {
                if (mEntryValues[i] == value) {
                    return i;
                }
            }
        }
        return -1;
    }

    private int getValueIndex() {
        return findIndexOfValue(mValue);
    }

    /**
     * Sets the value to the given index from the entry values.
     *
     * @param index The index of the value to set.
     */
    public void setValueIndex(final int index) {
        if (mEntryValues != null) {
            setValue(mEntryValues[index]);
        }
    }

    @Override
    protected Object onGetDefaultValue(@NonNull final TypedArray a,
                                       final int index) {
        return a.getInteger(index, DEFAULT_WHEN_NO_DEFAULT_SET);
    }

    @Override
    protected void onSetInitialValue(@Nullable final Object defaultValue) {
        if (defaultValue == null) {
            Logger.debug("defaultValue was NULL");
        }
        int i = getPersistedInt(
                (Integer) (defaultValue == null ? DEFAULT_WHEN_NO_DEFAULT_SET : defaultValue));
        setValue(i);
    }

    @Override
    protected Parcelable onSaveInstanceState() {
        final Parcelable superState = super.onSaveInstanceState();
        if (isPersistent()) {
            // No need to save instance state since it's persistent
            return superState;
        }

        final SavedState myState = new SavedState(superState);
        myState.value = getValue();
        return myState;
    }

    @Override
    protected void onRestoreInstanceState(@NonNull final Parcelable state) {
        if (!state.getClass().equals(SavedState.class)) {
            // Didn't save state for us in onSaveInstanceState
            super.onRestoreInstanceState(state);
            return;
        }

        SavedState myState = (SavedState) state;
        super.onRestoreInstanceState(myState.getSuperState());
        setValue(myState.value);
    }

    private static class SavedState
            extends BaseSavedState {

        /** {@link Parcelable}. */
        public static final Creator<SavedState> CREATOR =
                new Creator<SavedState>() {
                    @Override
                    public SavedState createFromParcel(@NonNull final Parcel source) {
                        return new SavedState(source);
                    }

                    @Override
                    public SavedState[] newArray(final int size) {
                        return new SavedState[size];
                    }
                };
        int value;

        public SavedState(@NonNull final Parcel source) {
            super(source);
            value = source.readInt();
        }

        public SavedState(@NonNull final Parcelable superState) {
            super(superState);
        }

        @Override
        public void writeToParcel(@NonNull final Parcel dest,
                                  final int flags) {
            super.writeToParcel(dest, flags);
            dest.writeInt(value);
        }
    }

}
