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

package com.hardbacknutter.prefslib;

import android.content.Context;

import androidx.annotation.IntRange;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.Arrays;
import java.util.Objects;
import java.util.function.Function;

/**
 * The value is a {@code String} from {@link #getEntryValues()}.
 */
@SuppressWarnings({"unused", "WeakerAccess"})
public class SingleChoiceSetting
        extends Setting
        implements SettingWithDialog {

    private static final int NONE = -1;

    @Nullable
    private String notSetSummary;
    @Nullable
    private String negativeButtonText;

    @Nullable
    private Function<Setting, String> dialogMessageProvider;

    @Nullable
    private CharSequence[] entries;
    @Nullable
    private CharSequence[] entryValues;

    private int checkedIndex = NONE;

    SingleChoiceSetting(@NonNull final String key,
                        @NonNull final SettingsDataStore dataStore) {
        super(Type.SingleChoice, key, dataStore);
    }

    /**
     * Get the human readable labels for the different choices.
     *
     * @return array of labels
     */
    @Nullable
    public CharSequence[] getEntries() {
        return entries;
    }

    /**
     * Set the human readable labels for the different choices.
     *
     * @param entries array of labels
     */
    public void setEntries(@NonNull final CharSequence[] entries) {
        this.entries = entries;
    }

    /**
     * Get the values for the different choices.
     *
     * @return array of values
     */
    @Nullable
    public CharSequence[] getEntryValues() {
        return entryValues;
    }

    /**
     * Set the values for the different choices.
     *
     * @param entryValues array of values
     */
    public void setEntryValues(@NonNull final CharSequence[] entryValues) {
        this.entryValues = entryValues;
    }

    /**
     * The unresolved selected index, or {@link #NONE}.
     *
     * @return selected index
     */
    @IntRange(from = NONE)
    public int getSelectedIndex() {
        return checkedIndex;
    }

    /**
     * Set the raw selected index, or {@link #NONE}.
     *
     * @param index to select
     */
    public void setSelectedIndex(@IntRange(from = NONE) final int index) {
        this.checkedIndex = index;
    }

    /**
     * The selected value from {@link #getEntryValues()}.
     *
     * @return selection, or {@code null} for none
     */
    @Nullable
    public CharSequence getValue() {
        if (checkedIndex == NONE || entryValues == null) {
            return null;
        }
        return entryValues[checkedIndex];
    }

    /**
     * Set the selected value.
     *
     * @param value selection
     */
    public void setValue(@Nullable final CharSequence value) {
        if (value == null || entryValues == null) {
            checkedIndex = NONE;
            return;
        }

        for (int i = 0; i < entryValues.length; i++) {
            if (entryValues[i].equals(value)) {
                checkedIndex = i;
                break;
            }
        }
    }

    @Nullable
    public String getNotSetSummary() {
        return notSetSummary;
    }

    public void setNotSetSummary(@Nullable final String notSetSummary) {
        this.notSetSummary = notSetSummary;
    }

    @Nullable
    public String getNegativeButtonText() {
        return negativeButtonText;
    }

    public void setNegativeButtonText(@Nullable final String negativeButtonText) {
        this.negativeButtonText = negativeButtonText;
    }

    @Override
    public void setDialogMessageProvider(@Nullable final Function<Setting, String> provider) {
        dialogMessageProvider = provider;
    }

    @Override
    @Nullable
    public String getDialogMessage() {
        return dialogMessageProvider != null ? dialogMessageProvider.apply(this) : null;
    }

    /**
     * Get the summary.
     * <p>
     * Source: the 'not-set' from the constructor,
     * or the selected entry entry text.
     *
     * @param context Current context
     *
     * @return summary
     */
    @Nullable
    @Override
    public CharSequence getSummary(@NonNull final Context context) {
        if (summaryProvider != null) {
            return summaryProvider.apply(context);
        }

        if (checkedIndex == NONE || entries == null) {
            return notSetSummary;
        } else {
            return entries[checkedIndex];
        }
    }

    @Override
    public void load(@NonNull final Context context) {
        if (checkedIndex == NONE || entryValues == null) {
            setValue(dataStore.getString(getKey(), null));
        } else {
            setValue(dataStore.getString(getKey(), entryValues[checkedIndex].toString()));
        }
    }

    @Override
    public void save(@NonNull final Context context) {
        if (checkedIndex == NONE || entryValues == null) {
            dataStore.putString(getKey(), null);
        } else {
            dataStore.putString(getKey(), entryValues[checkedIndex].toString());
        }
    }

    @Override
    public boolean equals(@Nullable final Object o) {
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        if (!super.equals(o)) {
            return false;
        }
        final SingleChoiceSetting that = (SingleChoiceSetting) o;
        return checkedIndex == that.checkedIndex
               && Objects.deepEquals(entries, that.entries)
               && Objects.deepEquals(entryValues, that.entryValues)
               && Objects.equals(notSetSummary, that.notSetSummary)
               && Objects.equals(negativeButtonText, that.negativeButtonText);
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), notSetSummary, negativeButtonText,
                            Arrays.hashCode(entries),
                            Arrays.hashCode(entryValues),
                            checkedIndex);
    }
}
