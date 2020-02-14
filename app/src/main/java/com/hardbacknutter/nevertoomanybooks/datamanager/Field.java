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

import android.util.Log;
import android.view.View;
import android.widget.Checkable;
import android.widget.ImageView;

import androidx.annotation.IdRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.lang.ref.WeakReference;
import java.util.Arrays;

import com.hardbacknutter.nevertoomanybooks.App;
import com.hardbacknutter.nevertoomanybooks.BookBaseFragment;
import com.hardbacknutter.nevertoomanybooks.BuildConfig;
import com.hardbacknutter.nevertoomanybooks.DEBUG_SWITCHES;
import com.hardbacknutter.nevertoomanybooks.datamanager.fieldaccessors.FieldViewAccessor;
import com.hardbacknutter.nevertoomanybooks.debug.ErrorMsg;

/**
 * Field definition contains all information and methods necessary to manage display and
 * extraction of data in a view.
 *
 * @param <T> type of Field value.
 */
public class Field<T> {

    /** Log tag. */
    private static final String TAG = "Field";

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

    /** Accessor to use. Encapsulates the formatter. */
    @NonNull
    private final FieldViewAccessor<T> mFieldDataAccessor;

    @SuppressWarnings("FieldNotUsedInToString")
    @Nullable
    private WeakReference<Fields.AfterChangeListener> mAfterFieldChangeListener;

    /** Fields that need to follow visibility. */
    @Nullable
    @IdRes
    private int[] mRelatedFields;

    /**
     * Constructor.
     *
     * @param id        for this field.
     * @param key       Key used to access a {@link DataManager}
     *                  Set to "" to suppress all access.
     * @param entityKey The preference key to check if this Field is used or not
     */
    Field(final int id,
          @NonNull final FieldViewAccessor<T> fieldDataAccessor,
          @NonNull final String key,
          @NonNull final String entityKey) {

        mId = id;
        mKey = key;
        mIsUsedKey = entityKey;

        mFieldDataAccessor = fieldDataAccessor;
        mFieldDataAccessor.setField(this);
    }

    @NonNull
    public FieldViewAccessor<T> getAccessor() {
        return mFieldDataAccessor;
    }

    /**
     * Called from {@link BookBaseFragment} #loadFields() (from onResume())
     * to set the View for the field.
     *
     * @param parentView of the field View
     */
    void setParentView(@NonNull final View parentView) {
        mFieldDataAccessor.setView(parentView.findViewById(mId));
    }

    void setAfterFieldChangeListener(@Nullable final Fields.AfterChangeListener listener) {
        mAfterFieldChangeListener = new WeakReference<>(listener);
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
     * <strong>Conditionally</strong> set the visibility for the field and its related fields.
     *
     * @param parent      parent view for all fields.
     * @param hideIfEmpty hide the field if it's empty
     * @param keepHidden  keep a field hidden if it's already hidden
     */
    void setVisibility(@NonNull final View parent,
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

        parent.findViewById(mId).setVisibility(visibility);
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
        if (mRelatedFields != null) {
            for (int fieldId : mRelatedFields) {
                View view = parent.findViewById(fieldId);
                if (view != null) {
                    view.setVisibility(visibility);
                }
            }
        }
    }

    @NonNull
    public String getKey() {
        return mKey;
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
     * Check if this field can be automatically populated.
     *
     * @return {@code true} if it can
     */
    boolean isAutoPopulated() {
        return !mKey.isEmpty();
    }

    public void onChanged() {
        if (mAfterFieldChangeListener != null) {
            if (mAfterFieldChangeListener.get() != null) {
                mAfterFieldChangeListener.get().afterFieldChange(mId);
            } else {
                if (BuildConfig.DEBUG && DEBUG_SWITCHES.TRACE_WEAK_REFERENCES) {
                    Log.d(TAG, "onChanged|" + ErrorMsg.WEAK_REFERENCE);
                }
            }
        }
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
}
