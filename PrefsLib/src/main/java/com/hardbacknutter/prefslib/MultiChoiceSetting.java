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

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * The value is a {@code Set<String>} from {@link #getEntryValues()}.
 */
@SuppressWarnings({"unused", "WeakerAccess"})
public class MultiChoiceSetting
        extends Setting
        implements SettingWithDialog {

    @Nullable
    private String notSetSummary;
    @Nullable
    private String clearButtonText;
    @Nullable
    private String negativeButtonText;
    @Nullable
    private String positiveButtonText;

    @Nullable
    private Function<Setting, String> dialogMessageProvider;

    @Nullable
    private CharSequence[] entries;
    @Nullable
    private CharSequence[] entryValues;

    private final Set<String> value = new HashSet<>();

    MultiChoiceSetting(@NonNull final String key) {
        super(Type.MultiChoice, key);
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
     * The unresolved selected indexes, or an empty array for none.
     *
     * @return selected indexes
     */
    @NonNull
    public boolean[] getSelectedIndexes() {
        if (entryValues == null) {
            return new boolean[0];
        }

        final int entryCount = entryValues.length;
        final boolean[] result = new boolean[entryCount];

        for (int i = 0; i < entryCount; i++) {
            result[i] = this.value.contains(entryValues[i].toString());
        }

        return result;
    }

    /**
     * The selected values from {@link #getEntryValues()}.
     *
     * @return selection, can be empty for none
     */
    public Set<String> getValue() {
        return value;
    }

    /**
     * Convenience method.
     * Set the selected values as raw Objects. They will be stringified.
     *
     * @param values selection
     */
    public void setValue(@NonNull final Object... values) {
        if (values.length == 0) {
            setValue((Set<String>) null);
        } else {
            setValue(Arrays.stream(values)
                           .map(String::valueOf)
                           .collect(Collectors.toSet()));
        }
    }

    /**
     * Set the selected values.
     *
     * @param values selection
     */
    public void setValue(@Nullable final Set<String> values) {
        this.value.clear();
        if (values != null && !values.isEmpty()) {
            this.value.addAll(values);
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
    public String getPositiveButtonText() {
        return positiveButtonText;
    }

    public void setPositiveButtonText(@Nullable final String positiveButtonText) {
        this.positiveButtonText = positiveButtonText;
    }

    @Nullable
    public String getNegativeButtonText() {
        return negativeButtonText;
    }

    public void setNegativeButtonText(@Nullable final String negativeButtonText) {
        this.negativeButtonText = negativeButtonText;
    }

    @Nullable
    public String getClearButtonText() {
        return clearButtonText;
    }

    public void setClearButtonText(@Nullable final String clearButtonText) {
        this.clearButtonText = clearButtonText;
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

        if (value.isEmpty() || entries == null || entryValues == null) {
            return notSetSummary;
        } else {
            final List<String> list = new ArrayList<>();
            for (final String v : value) {
                for (int i = 0; i < entryValues.length; i++) {
                    if (entryValues[i].equals(v)) {
                        list.add(entries[i].toString());
                    }
                }
            }

            Collections.sort(list);
            return String.join(", ", list);
        }
    }

    @Override
    public void load(@NonNull final Context context,
                     @NonNull final SettingsDataStore store) {
        setValue(store.getStringSet(getKey(), value));
    }

    @Override
    public void save(@NonNull final Context context,
                     @NonNull final SettingsDataStore store) {
        store.putStringSet(getKey(), value);
    }

    @Override
    public boolean equals(@Nullable final Object o) {
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        if (!super.equals(o)) {
            return false;
        }
        final MultiChoiceSetting that = (MultiChoiceSetting) o;
        return Objects.equals(notSetSummary, that.notSetSummary)
               && Objects.equals(clearButtonText, that.clearButtonText)
               && Objects.equals(negativeButtonText, that.negativeButtonText)
               && Objects.equals(positiveButtonText, that.positiveButtonText)
               && Objects.deepEquals(entries, that.entries)
               && Objects.deepEquals(entryValues, that.entryValues)
               && Objects.equals(value, that.value);
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), notSetSummary,
                            clearButtonText, negativeButtonText, positiveButtonText,
                            Arrays.hashCode(entries),
                            Arrays.hashCode(entryValues),
                            value);
    }
}
