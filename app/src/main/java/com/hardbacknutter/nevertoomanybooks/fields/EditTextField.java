/*
 * @Copyright 2018-2025 HardBackNutter
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
package com.hardbacknutter.nevertoomanybooks.fields;

import android.content.Context;
import android.text.Editable;
import android.text.InputType;
import android.view.View;
import android.widget.AutoCompleteTextView;
import android.widget.EditText;

import androidx.annotation.IdRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;

import java.util.ArrayList;
import java.util.List;

import com.hardbacknutter.nevertoomanybooks.core.utils.IntListPref;
import com.hardbacknutter.nevertoomanybooks.core.widgets.ExtTextWatcher;
import com.hardbacknutter.nevertoomanybooks.datamanager.DataManager;
import com.hardbacknutter.nevertoomanybooks.fields.endicon.ExtEndIconDelegate;
import com.hardbacknutter.nevertoomanybooks.fields.formatters.EditFieldFormatter;
import com.hardbacknutter.nevertoomanybooks.fields.formatters.FieldFormatter;
import com.hardbacknutter.util.logger.LoggerFactory;

/**
 * Stores and retrieves data from an {@link EditText}.
 *
 * @param <T> type of Field value.
 * @param <V> type of Field View, must extend TextView
 */
public class EditTextField<T, V extends EditText>
        extends BaseTextField<T, V>
        implements ExtTextWatcher, MultiOnFocusChangeListener<T, V> {

    private static final String TAG = "EditTextField";

    /** Reformat only every 0.5 seconds: this is good enough and easier on the user. */
    private static final int REFORMAT_DELAY_MS = 500;

    /** Enable or disable the formatting text watcher. */
    private final boolean enableReformat;

    /** Timer for the text watcher. */
    private long lastChange;

    @Nullable
    private List<View.OnFocusChangeListener> focusChangeListeners;

    @NonNull
    private Capitalization capitalization = Capitalization.DoNotChange;

    /**
     * Constructor.
     *
     * @param fragmentId  the hosting {@link FragmentId} for this {@link Field}
     * @param fieldViewId the view id for this {@link Field}
     * @param fieldKey    Key used to access a {@link DataManager}
     */
    public EditTextField(@NonNull final FragmentId fragmentId,
                         @IdRes final int fieldViewId,
                         @NonNull final String fieldKey) {
        super(fragmentId, fieldViewId, fieldKey, fieldKey, null);
        enableReformat = false;
    }

    /**
     * Constructor.
     *
     * @param fragmentId     the hosting {@link FragmentId} for this {@link Field}
     * @param fieldViewId    the view id for this {@link Field}
     * @param fieldKey       Key used to access a {@link DataManager}
     *                       Set to {@code ""} to suppress all access.
     * @param formatter      formatter to use
     * @param enableReformat flag: reformat after every user-change.
     */
    public EditTextField(@NonNull final FragmentId fragmentId,
                         @IdRes final int fieldViewId,
                         @NonNull final String fieldKey,
                         @NonNull final FieldFormatter<T> formatter,
                         final boolean enableReformat) {
        super(fragmentId, fieldViewId, fieldKey, fieldKey, formatter);
        this.enableReformat = enableReformat && formatter instanceof EditFieldFormatter;
    }

    /**
     * Set the id for the surrounding TextInputLayout (if this field has one).
     *
     * @param viewId view id
     *
     * @return {@code this} (for chaining)
     */
    @NonNull
    public EditTextField<T, V> setTextInputLayoutId(@IdRes final int viewId) {
        textInputLayoutId = viewId;
        setErrorViewId(viewId);
        return this;
    }

    @NonNull
    public EditTextField<T, V> addOnFocusChangeListener(
            @NonNull final View.OnFocusChangeListener listener) {
        if (focusChangeListeners == null) {
            focusChangeListeners = new ArrayList<>();
        }
        if (!focusChangeListeners.contains(listener)) {
            focusChangeListeners.add(listener);
        }
        return this;
    }

    public void removeOnFocusChangeListener(
            @NonNull final View.OnFocusChangeListener listener) {
        if (focusChangeListeners != null) {
            focusChangeListeners.remove(listener);
        }
    }

    @NonNull
    public EditTextField<T, V> setCapitalization(@NonNull final Capitalization capitalization) {
        this.capitalization = capitalization;
        return this;
    }

    /**
     * Set the end-icon to use.
     *
     * @param endIconMode to use
     *
     * @return {@code this} (for chaining)
     */
    @NonNull
    public EditTextField<T, V> setEndIconMode(
            @ExtEndIconDelegate.EndIconMode final int endIconMode) {
        this.endIconMode = endIconMode;
        return this;
    }

    @Nullable
    public List<View.OnFocusChangeListener> getOnFocusChangeListeners() {
        return focusChangeListeners;
    }

    @Override
    public void setParentView(@NonNull final View parent) {
        super.setParentView(parent);

        final V view = requireView();

        if (capitalization != Capitalization.DoNotChange) {
            capitalization.apply(view);
        }

        view.addTextChangedListener(this);
        // REMINDER: this overrides the default listener which would show/remove the "end_icon"
        // This is in fact what we want - finally... an android "issue" we like.
        view.setOnFocusChangeListener(this);
    }

    @Override
    public void setValue(@Nullable final T value) {
        super.setValue(value);

        final V view = getView();
        if (view != null) {
            // We need to do this in two steps.
            CharSequence text = null;
            try {
                //  First format the value as normal.
                text = formatter.format(view.getContext(), rawValue);

            } catch (@NonNull final ClassCastException e) {
                // 1. Due to the way a Book loads data from the database,
                // it's possible that it gets the column type wrong.
                // See {@link TypedCursor} class docs.
                //
                // 2. When the data comes from the internet,
                // the parser might get it wrong when a webpage changes structural data
                //
                // 3. see {@link SearchCoordinator#accumulateStringData}
                // where some data is transformed into a String regardless
                LoggerFactory.getLogger().w(TAG, e, "fieldKey=" + getFieldKey(),
                                            "value=" + value,
                                            "text=" + text);
                text = rawValue != null ? String.valueOf(rawValue) : "";
            }

            // Second step set the view but ...
            // ... disable the ChangedTextWatcher.
            view.removeTextChangedListener(this);
            if (view instanceof AutoCompleteTextView) {
                // ... prevent auto-completion to kick in / stop the dropdown from opening.
                ((AutoCompleteTextView) view).setText(text, false);
            } else {
                // ... or set as is
                view.setText(text);
            }
            // ... finally re-enable the watcher
            view.addTextChangedListener(this);
        }
    }

    /**
     * TextWatcher for TextView fields.
     *
     * <ol>
     *     <li>Update the current in-memory value</li>
     *      <li>clears any previous error</li>
     *      <li>Re-formats if allowed and needed</li>
     *      <li>notify listeners of any change</li>
     * </ol>
     * <p>
     * {@inheritDoc}
     */
    @Override
    public void afterTextChanged(@NonNull final Editable editable) {
        final T previous = rawValue;

        final V view = getView();

        //noinspection DataFlowIssue
        final Context context = view.getContext();

        final String text = editable.toString().trim();
        // Update the actual value
        if (formatter instanceof EditFieldFormatter) {
            rawValue = ((EditFieldFormatter<T>) formatter).extract(context, text);
        } else {
            // Without a formatter, we MUST assume <T> to be a String.
            // Make sure NOT to replace a 'null' value with an empty string
            if (rawValue != null || !text.isEmpty()) {
                // If we get an Exception here then the developer made a boo-boo.
                //noinspection unchecked
                rawValue = (T) text;
            }
        }
        // Clear any previous error. The new content will be re-checked at validation time.
        setError(null);

        if (enableReformat) {
            if (System.currentTimeMillis() - lastChange > REFORMAT_DELAY_MS) {
                final String formatted = formatter.format(context, rawValue).toString();
                // If different, replace the encoded value with the formatted value.
                if (!text.equalsIgnoreCase(formatted)) {
                    view.removeTextChangedListener(this);
                    editable.replace(0, editable.length(), formatted);
                    view.addTextChangedListener(this);
                }
            }
            lastChange = System.currentTimeMillis();
        }

        notifyIfChanged(previous);
    }

    /**
     * Used/defined in xml/preferences.xml
     */
    public enum Capitalization {
        /**
         * The default value for all fields.
         * <p>
         * Don't change, use/keep xml or manual code settings.
         */
        DoNotChange("", 0),

        /**
         * Book, Series and TocEntry titles.
         * Overrides textCapCharacters, textCapWords, textCapSentences.
         * Does NOT modify any others.
         */
        Title("edit.capitalize.title", InputType.TYPE_TEXT_FLAG_CAP_WORDS);

        static final int MASK = InputType.TYPE_TEXT_FLAG_CAP_CHARACTERS
                                | InputType.TYPE_TEXT_FLAG_CAP_WORDS
                                | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES;
        @NonNull
        private final String prefKey;
        private final int defaultVal;

        Capitalization(@NonNull final String prefKey,
                       final int defaultVal) {
            this.prefKey = prefKey;
            this.defaultVal = defaultVal;
        }

        @VisibleForTesting
        @NonNull
        public String getPrefKey() {
            return prefKey;
        }

        private int getBits(@NonNull final Context context) {
            switch (IntListPref.getInt(context, prefKey, -1)) {
                case 0:
                    return 0;
                case 1:
                    return InputType.TYPE_TEXT_FLAG_CAP_WORDS;
                case 2:
                    return InputType.TYPE_TEXT_FLAG_CAP_SENTENCES;
                case 3:
                    return InputType.TYPE_TEXT_FLAG_CAP_CHARACTERS;
                default:
                    return defaultVal;
            }
        }

        /**
         * Apply the preferred mode.
         *
         * @param editText to process
         */
        public void apply(@NonNull final EditText editText) {
            if (this == DoNotChange) {
                return;
            }
            // get all current bits, nulling out the 3 values we handle.
            final int current = editText.getInputType() & ~MASK;
            // determine the bits to turn on
            final int value = getBits(editText.getContext());

            editText.setInputType(current | value);
        }
    }
}
