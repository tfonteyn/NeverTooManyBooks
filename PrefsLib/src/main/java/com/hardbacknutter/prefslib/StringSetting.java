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
import android.text.InputType;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.Objects;
import java.util.function.Function;

/**
 * The value is a {@code String}.
 */
@SuppressWarnings({"unused", "WeakerAccess"})
public class StringSetting
        extends Setting
        implements SettingWithDialog {

    @Nullable
    private String notSetSummary;
    @Nullable
    private String notSetButtonText;
    @Nullable
    private String negativeButtonText;
    @Nullable
    private String positiveButtonText;

    @Nullable
    private Function<Setting, String> dialogMessageProvider;

    private int inputType = InputType.TYPE_CLASS_TEXT;

    @Nullable
    private String value;

    private boolean emptyIsNotSet = true;

    StringSetting(@NonNull final String key,
                  @NonNull final SettingsDataStore dataStore) {
        super(Type.String, key, dataStore);
    }

    public int getInputType() {
        return inputType;
    }

    public void setInputType(final int inputType) {
        this.inputType = inputType;
    }

    public boolean isEmptyIsNotSet() {
        return emptyIsNotSet;
    }

    /**
     * Set the flag for treating the empty string as {@code not set}.
     * The default is {@code true}.
     * <p>
     * {@code true} the empty string implies the value is {@code not set}
     * {@code false} the empty string is a valid value
     *
     * @param emptyIsNotSet flag
     */
    public void setEmptyIsNotSet(final boolean emptyIsNotSet) {
        this.emptyIsNotSet = emptyIsNotSet;
    }

    @Nullable
    public String getNotSetSummary() {
        return notSetSummary;
    }

    public void setNotSetSummary(@Nullable final String notSetSummary) {
        this.notSetSummary = notSetSummary;
    }

    @Nullable
    public String getNotSetButtonText() {
        return notSetButtonText;
    }

    public void setNotSetButtonText(@Nullable final String notSetButtonText) {
        this.notSetButtonText = notSetButtonText;
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
     * Returns {@code null} when the value is {@code not set}.
     *
     * @return value
     *
     * @see #setEmptyIsNotSet(boolean)
     */
    @Nullable
    public String getValue() {
        return value != null && emptyIsNotSet && value.isEmpty() ? null : value;
    }

    public void setValue(@Nullable final String value) {
        this.value = value;
    }

    /**
     * Get the summary.
     * <p>
     * Source:
     * <ol>
     *     <li>The summary provider</li>
     *     <li>The not-set summary; this can be {@code null}</li>
     *     <li>The value; can be empty or can be {@code null}</li>
     * </ol>
     * Note that both summary resource id/text are IGNORED.
     *
     * @param context Current context
     *
     * @return summary
     */
    @Override
    @Nullable
    public CharSequence getSummary(@NonNull final Context context) {
        // The provider ALWAYS wins.
        if (summaryProvider != null) {
            return summaryProvider.apply(context);
        }

        if (value == null || emptyIsNotSet && value.isEmpty()) {
            return notSetSummary;
        }

        return value;
    }

    @Override
    public void load(@NonNull final Context context) {
        setValue(dataStore.getString(getKey(), value));
    }

    @Override
    public void save(@NonNull final Context context) {
        dataStore.putString(getKey(), value);
    }

    @Override
    public boolean equals(@Nullable final Object o) {
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        if (!super.equals(o)) {
            return false;
        }
        final StringSetting that = (StringSetting) o;
        return Objects.equals(value, that.value)
               && inputType == that.inputType
               && Objects.equals(notSetSummary, that.notSetSummary)
               && Objects.equals(negativeButtonText, that.negativeButtonText)
               && Objects.equals(positiveButtonText, that.positiveButtonText);
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), notSetSummary,
                            negativeButtonText, positiveButtonText,
                            inputType, value);
    }
}
