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
import android.support.annotation.CallSuper;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.annotation.StringRes;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.widget.TextView;

import com.eleybourn.bookcatalogue.BookCatalogueApp;
import com.eleybourn.bookcatalogue.R;
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
public class BooleanProperty extends PropertyWithGlobalValue<Boolean> {

    private int mPositiveTextId = R.string.yes;
    private int mNegativeTextId = R.string.no;
    @Nullable
    private Object[] mPositiveTextArgs;
    @Nullable
    private Object[] mNegativeTextArgs;

    public BooleanProperty(final @NonNull String uniqueId,
                           final @NonNull PropertyGroup group,
                           final @StringRes int nameResourceId,
                           final @Nullable Boolean defaultValue) {
        super(uniqueId, group, nameResourceId, defaultValue);
    }

    /**
     * Override the standard 'true'/'false' labels.
     */
    public BooleanProperty setOptionLabels(final @StringRes int positiveId, final @StringRes int negativeId) {
        mPositiveTextId = positiveId;
        mNegativeTextId = negativeId;
        return this;
    }

    /**
     * Override the standard 'true' label
     */
    public BooleanProperty setTrueLabel(final @StringRes int stringId, final @Nullable Object... args) {
        mPositiveTextId = stringId;
        mPositiveTextArgs = args;
        return this;
    }

    /**
     * Override the standard 'false label
     */
    public BooleanProperty setFalseLabel(final @StringRes int stringId, final @Nullable Object... args) {
        mNegativeTextId = stringId;
        mNegativeTextArgs = args;
        return this;
    }

    @NonNull
    @Override
    public View getView(final @NonNull LayoutInflater inflater) {
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
            public void onClick(@NonNull View v) {
                holder.cb.performClick();
            }
        });
        holder.cb.setOnTriStateChangeListener(new TriStateCheckBox.OnTriStateChangeListener() {
            @Override
            public void onTriStateChange(final View v, final Boolean state) {
                Holder holder = ViewTagger.getTagOrThrow(v, R.id.TAG_PROPERTY);
                holder.property.setValue(state);
                holder.property.setViewValues(holder, state);
            }
        });

        return root;
    }

    /** Set the checkbox and text fields based on passed value */
    private void setViewValues(final @NonNull Holder holder, final @Nullable Boolean value) {
        holder.name.setText(this.getNameResourceId());
        holder.cb.setPressed(false);
        holder.cb.setState(value);

        // set labels according to value
        if (value == null) {
            holder.label.setText(R.string.use_default_setting);
        } else {
            if (value) {
                holder.label.setText(BookCatalogueApp.getResourceString(mPositiveTextId, mPositiveTextArgs));
            } else {
                holder.label.setText(BookCatalogueApp.getResourceString(mNegativeTextId, mNegativeTextArgs));
            }
        }
    }

    @Override
    @NonNull
    protected Boolean getGlobalValue() {
        return BookCatalogueApp.getBooleanPreference(getPreferenceKey(), Objects.requireNonNull(getDefaultValue()));
    }

    @Override
    @Nullable
    protected BooleanProperty setGlobalValue(final @Nullable Boolean value) {
        Objects.requireNonNull(value);
        BookCatalogueApp.getSharedPreferences().edit().putBoolean(getPreferenceKey(), value).apply();
        return this;
    }

    /**
     * Convenience method to check for true (with null == false)
     *
     * Uses the resolved value to check for 'true'
     */
    public boolean isTrue() {
        Boolean b = super.getResolvedValue();
        return (b != null ? b : false);
    }

    /**
     * Only implemented for chaining with correct return type
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
    public BooleanProperty setDefaultValue(final @Nullable Boolean value) {
        super.setDefaultValue(value);
        return this;
    }

    /**
     * Only implemented for chaining with correct return type
     */
    @NonNull
    @Override
    @CallSuper
    public BooleanProperty setHint(final @StringRes int hint) {
        super.setHint(hint);
        return this;
    }

    /**
     * Only implemented for chaining with correct return type
     */
    @NonNull
    @Override
    @CallSuper
    public BooleanProperty setWeight(int weight) {
        super.setWeight(weight);
        return this;
    }

    /**
     * Only implemented for chaining with correct return type
     */
    @Override
    @NonNull
    @CallSuper
    public BooleanProperty setPreferenceKey(final @NonNull String key) {
        super.setPreferenceKey(key);
        return this;
    }

    /**
     * Our value is Nullable. To Parcel it, we use three values:
     * true : 1
     * false: 0
     * null : Integer.MIN_VALUE
     *
     * Note that {@link Parcel#writeValue(Object)} actually writes a Boolean as 'int'
     * So 'null' is NOT preserved.
     */
    public void writeToParcel(final @NonNull Parcel dest) {
        Boolean value = this.getValue();
        if (value == null) {
            dest.writeInt(Integer.MIN_VALUE);
        } else {
            dest.writeInt(value ? 1 : 0);
        }
    }

    public void readFromParcel(final @NonNull Parcel in) {
        int parceledInt = in.readInt();
        if (parceledInt == Integer.MIN_VALUE) {
            setValue(null);
        } else {
            setValue(parceledInt == 1 ? Boolean.TRUE : Boolean.FALSE);
        }
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
    public static class TriStateCheckBox extends android.support.v7.widget.AppCompatImageButton {

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

            public void onClick(View v) {
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

        public TriStateCheckBox(final @NonNull Context context) {
            super(context);
            init();
        }

        public TriStateCheckBox(final @NonNull Context context, final @NonNull AttributeSet attrs) {
            super(context, attrs);
            init();
        }

        public TriStateCheckBox(final @NonNull Context context, final @NonNull AttributeSet attrs, final int defStyleAttr) {
            super(context, attrs, defStyleAttr);
            init();
        }

        @Nullable
        public Boolean getState() {
            return state;
        }

        public void setNullable(final boolean nullable) {
            this.nullable = nullable;
        }

        public void setState(final @Nullable Boolean state) {
            if (!this.restoring && this.state != state) {
                this.state = state;
                if (this.clientListener != null) {
                    this.clientListener.onTriStateChange(this, state);
                }
                updateBtn();
            }
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

        public void setOnTriStateChangeListener(final @Nullable OnTriStateChangeListener listener) {
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
            void onTriStateChange(View v, Boolean state);
        }

        static class SavedState extends BaseSavedState {
            @SuppressWarnings("hiding")
            public static final Creator<SavedState> CREATOR =
                    new Creator<SavedState>() {
                        @Override
                        public SavedState createFromParcel(Parcel in) {
                            return new SavedState(in);
                        }

                        @Override
                        public SavedState[] newArray(int size) {
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
            public void writeToParcel(Parcel dest, int flags) {
                super.writeToParcel(dest, flags);
                if (state == null) {
                    dest.writeInt(-1);
                } else {
                    dest.writeInt(state ? 1 : 0);
                }
            }

            @Override
            public String toString() {
                return "CheckboxTriState.SavedState{"
                        + Integer.toHexString(System.identityHashCode(this))
                        + " state=" + state + "}";
            }
        }
    }
}

