/*
 * @copyright 2012 Philip Warner
 * @license GNU General Public License
 *
 * This file is part of Book Catalogue.
 *
 * Book Catalogue is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Book Catalogue is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with Book Catalogue.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.eleybourn.bookcatalogue.properties;

import android.content.Context;
import android.os.Parcel;
import android.os.Parcelable;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.CallSuper;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;

import com.eleybourn.bookcatalogue.BookCatalogueApp;
import com.eleybourn.bookcatalogue.R;
import com.eleybourn.bookcatalogue.utils.Prefs;
import com.eleybourn.bookcatalogue.utils.ViewTagger;

import java.util.Objects;

/**
 * A Boolean value with an edit View using a checkbox that cycles between 3 fixed values:
 * - true
 * - false
 * - null -> Use Default => use the global value.
 *
 * to Parcel the value, use {@link #writeToParcel(Parcel)} and {@link #readFromParcel(Parcel)}
 *
 * @author Philip Warner
 */
public class BooleanProperty
        extends PropertyWithGlobalValue<Boolean> {

    private int mPositiveTextId = R.string.yes;
    private int mNegativeTextId = R.string.no;
    @Nullable
    private Object[] mPositiveTextArgs;
    @Nullable
    private Object[] mNegativeTextArgs;

    /**
     * Convenience constructor which sets the defaultValue to Boolean.FALSE
     */
    public BooleanProperty(@StringRes final int nameResourceId,
                           @NonNull final PropertyGroup group) {
        super(group, nameResourceId, Boolean.FALSE);
    }

    public BooleanProperty(@StringRes final int nameResourceId,
                           @NonNull final PropertyGroup group,
                           @NonNull final Boolean defaultValue) {
        super(group, nameResourceId, defaultValue);
    }

    /**
     * Override the standard 'true'/'false' labels.
     *
     * If your labels don't need arguments.
     * Otherwise use {@link #setTrueLabel} {@link #setFalseLabel}
     */
    public BooleanProperty setOptionLabels(@StringRes final int positiveId,
                                           @StringRes final int negativeId) {
        mPositiveTextId = positiveId;
        mNegativeTextId = negativeId;
        return this;
    }

    /**
     * Override the standard 'true' label
     */
    public BooleanProperty setTrueLabel(@StringRes final int stringId,
                                        @Nullable final Object... args) {
        mPositiveTextId = stringId;
        mPositiveTextArgs = args;
        return this;
    }

    /**
     * Override the standard 'false label
     */
    public BooleanProperty setFalseLabel(@StringRes final int stringId,
                                         @Nullable final Object... args) {
        mNegativeTextId = stringId;
        mNegativeTextArgs = args;
        return this;
    }

    @NonNull
    @Override
    public View getView(@NonNull final LayoutInflater inflater) {
        final ViewGroup root = (ViewGroup) inflater.inflate(R.layout.row_property_tristate, null);

        // create Holder
        final Holder holder = new Holder();
        holder.property = this;
        holder.name = root.findViewById(R.id.name);
        holder.label = root.findViewById(R.id.value);
        holder.cb = root.findViewById(R.id.btn_edit);
        // Set the ID so weird stuff does not happen on activity reload after config changes.
        holder.cb.setId(nextViewId());

        // Set the initial values
        holder.name.setText(this.getNameResourceId());
        holder.cb.setNullable(!isGlobal());
        setViewValues(holder, getValue());

        // tags used
        ViewTagger.setTag(holder.cb, R.id.TAG_PROPERTY, holder);

        // Setup click handlers for view and checkbox
        root.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(@NonNull final View v) {
                holder.cb.performClick();
            }
        });
        holder.cb.setOnTriStateChangeListener(new TriStateCheckBox.OnTriStateChangeListener() {
            @Override
            public void onTriStateChange(@NonNull final View v,
                                         @NonNull final Boolean state) {
                Holder holder = ViewTagger.getTagOrThrow(v, R.id.TAG_PROPERTY);
                holder.property.setValue(state);
                holder.property.setViewValues(holder, state);
            }
        });

        return root;
    }

    /** Set the checkbox and text fields based on passed value. */
    private void setViewValues(@NonNull final Holder holder,
                               @Nullable final Boolean value) {
        holder.name.setText(this.getNameResourceId());
        holder.cb.setPressed(false);
        holder.cb.setState(value);

        // set labels according to value
        if (value == null) {
            holder.label.setText(R.string.use_default_setting);
        } else {
            if (value) {
                holder.label.setText(
                        BookCatalogueApp.getResString(mPositiveTextId, mPositiveTextArgs));
            } else {
                holder.label.setText(
                        BookCatalogueApp.getResString(mNegativeTextId, mNegativeTextArgs));
            }
        }
    }

    @Override
    @NonNull
    protected Boolean getGlobalValue() {
        return Prefs.getPrefs().getBoolean(getPreferenceKey(), getDefaultValue());
    }

    @Override
    @NonNull
    public BooleanProperty setGlobalValue(@Nullable final Boolean value) {
        Objects.requireNonNull(value);
        Prefs.getPrefs().edit().putBoolean(getPreferenceKey(), value).apply();
        return this;
    }

    /**
     * Convenience method to check for true.
     *
     * Uses the resolved value to check for 'true'
     */
    public boolean isTrue() {
        return getResolvedValue();
    }

    /**
     * Only implemented for chaining with correct return type.
     */
    @Override
    @NonNull
    @CallSuper
    public BooleanProperty setIsGlobal(boolean isGlobal) {
        super.setIsGlobal(isGlobal);
        return this;
    }

    @NonNull
    @Override
    @CallSuper
    public BooleanProperty setDefaultValue(@NonNull final Boolean value) {
        super.setDefaultValue(value);
        return this;
    }

    /**
     * Only implemented for chaining with correct return type.
     */
    @NonNull
    @Override
    @CallSuper
    public BooleanProperty setHint(@StringRes final int hint) {
        super.setHint(hint);
        return this;
    }

    /**
     * Only implemented for chaining with correct return type.
     */
    @NonNull
    @Override
    @CallSuper
    public BooleanProperty setWeight(final int weight) {
        super.setWeight(weight);
        return this;
    }

    /**
     * Only implemented for chaining with correct return type
     */
    @Override
    @NonNull
    @CallSuper
    public BooleanProperty setPreferenceKey(@NonNull final String key) {
        super.setPreferenceKey(key);
        return this;
    }

    /**
     * Only implemented for chaining with correct return type
     */
    @Override
    @NonNull
    @CallSuper
    public BooleanProperty setPreferenceKey(@StringRes final int key) {
        super.setPreferenceKey(key);
        return this;
    }

    public void writeToParcel(@NonNull final Parcel dest) {
        dest.writeValue(this.getValue());
    }

    public void readFromParcel(@NonNull final Parcel in) {
        setValue((Boolean) in.readValue(getClass().getClassLoader()));
    }


    private static class Holder {

        BooleanProperty property;
        TriStateCheckBox cb;
        TextView name;
        TextView label;
    }

    /**
     * Code from: https://gist.github.com/kevin-barrientos/d75a5baa13a686367d45d17aaec7f030
     * based on: https://stackoverflow.com/a/40939367/3950497
     * Icons from Google.
     *
     * Modifications:
     * - use Boolean
     * - listener returns the state
     * - ImageButton
     */
    public static class TriStateCheckBox
            extends androidx.appcompat.widget.AppCompatImageButton {

        /** is state allowed to be null ? */
        private boolean nullable = true;

        @Nullable
        private Boolean state;
        /**
         * Holds a reference to the listener set by a client.
         */
        @Nullable
        private OnTriStateChangeListener clientListener;
        /**
         * This flag is needed to avoid accidentally changing the current {@link #state} when
         * {@link #onRestoreInstanceState(Parcelable)} calls {@link #setState(Boolean)}
         * evoking our {@link #privateListener} and therefore changing the real state.
         */
        private boolean restoring;
        /**
         * This is the listener set to the super class which is going to be evoke each
         * time the check state has changed.
         */
        private final OnClickListener privateListener = new OnClickListener() {

            public void onClick(@NonNull final View v) {
                // cycle through the three states
                if (state == null) {
                    setState(true);
                } else if (state) {
                    setState(false);
                } else {
                    if (nullable) {
                        setState(null);
                    } else {
                        setState(true);
                    }
                }
            }
        };

        public TriStateCheckBox(@NonNull final Context context) {
            super(context);
            init();
        }

        public TriStateCheckBox(@NonNull final Context context,
                                @NonNull final AttributeSet attrs) {
            super(context, attrs);
            init();
        }

        public TriStateCheckBox(@NonNull final Context context,
                                @NonNull final AttributeSet attrs,
                                final int defStyleAttr) {
            super(context, attrs, defStyleAttr);
            init();
        }

        @Nullable
        public Boolean getState() {
            return state;
        }

        public void setState(@Nullable final Boolean state) {
            if (!this.restoring && this.state != state) {
                this.state = state;
                if (this.clientListener != null) {
                    this.clientListener.onTriStateChange(this, state);
                }
                updateBtn();
            }
        }

        public void setNullable(final boolean nullable) {
            this.nullable = nullable;
        }

        private void init() {
            state = null;
            updateBtn();
        }

        private void updateBtn() {
            int btnDrawable;
            if (state == null) {
                btnDrawable = R.drawable.ic_indeterminate_check_box;
            } else if (!state) {
                btnDrawable = R.drawable.ic_check_box_outline_blank;
            } else {
                btnDrawable = R.drawable.ic_check_box;
            }
            setImageResource(btnDrawable);
        }

        public void setOnTriStateChangeListener(@Nullable final OnTriStateChangeListener listener) {
            this.clientListener = listener;
            // always use our implementation
            super.setOnClickListener(privateListener);
        }

        @Override
        public Parcelable onSaveInstanceState() {
            SavedState ss = new SavedState(super.onSaveInstanceState());
            ss.state = state;
            return ss;
        }

        @Override
        public void onRestoreInstanceState(Parcelable state) {
            this.restoring = true; // indicates that the ui is restoring its state
            SavedState ss = (SavedState) state;
            super.onRestoreInstanceState(ss.getSuperState());
            setState(ss.state);
            requestLayout();
            this.restoring = false;
        }

        public interface OnTriStateChangeListener {

            void onTriStateChange(View v,
                                  Boolean state);
        }

        static class SavedState
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
            Boolean state;

            SavedState(Parcelable superState) {
                super(superState);
            }

            private SavedState(Parcel in) {
                super(in);
                int stateInt = in.readInt();
                if (stateInt == -1) {
                    state = null;
                } else {
                    state = (stateInt == 1);
                }
            }

            @Override
            public void writeToParcel(@NonNull final Parcel out,
                                      final int flags) {
                super.writeToParcel(out, flags);
                if (state == null) {
                    out.writeInt(-1);
                } else {
                    out.writeInt(state ? 1 : 0);
                }
            }

            @Override
            public String toString() {
                return "CheckboxTriState.SavedState{"
                        + Integer.toHexString(System.identityHashCode(this))
                        + " state=" + state + '}';
            }
        }
    }
}

