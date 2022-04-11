/*
 * @Copyright 2018-2021 HardBackNutter
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
package com.hardbacknutter.nevertoomanybooks.activityresultcontracts;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.os.Parcel;
import android.os.Parcelable;

import androidx.activity.result.contract.ActivityResultContract;
import androidx.annotation.IntDef;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

import com.hardbacknutter.nevertoomanybooks.BuildConfig;
import com.hardbacknutter.nevertoomanybooks.DEBUG_SWITCHES;
import com.hardbacknutter.nevertoomanybooks.FragmentHostActivity;
import com.hardbacknutter.nevertoomanybooks.booklist.style.ListStyle;
import com.hardbacknutter.nevertoomanybooks.debug.Logger;
import com.hardbacknutter.nevertoomanybooks.settings.styles.StyleFragment;

public class EditStyleContract
        extends ActivityResultContract<EditStyleContract.Input, EditStyleContract.Output> {

    public static final int ACTION_CLONE = 0;
    public static final int ACTION_EDIT = 1;

    private static final String TAG = "EditStyleContract";
    public static final String BKEY_ACTION = TAG + ":action";
    public static final String BKEY_SET_AS_PREFERRED = TAG + ":setAsPreferred";


    @NonNull
    public static Input duplicate(@NonNull final ListStyle style) {
        return new Input(ACTION_CLONE, style, style.isPreferred());
    }

    @NonNull
    public static Input edit(@NonNull final ListStyle style) {
        return new Input(ACTION_EDIT, style, style.isPreferred());
    }

    @NonNull
    public static Input edit(@NonNull final ListStyle style,
                             final boolean setAsPreferred) {
        return new Input(ACTION_EDIT, style, setAsPreferred);
    }

    @NonNull
    public static Intent createResultIntent(@NonNull final String templateUuid,
                                            final boolean modified,
                                            @Nullable final String uuid) {
        final Parcelable output = new Output(templateUuid, modified, uuid);
        return new Intent().putExtra(Output.BKEY, output);
    }

    @NonNull
    @Override
    public Intent createIntent(@NonNull final Context context,
                               @NonNull final Input input) {
        return FragmentHostActivity
                .createIntent(context, StyleFragment.class)
                .putExtra(BKEY_ACTION, input.action)
                .putExtra(ListStyle.BKEY_UUID, input.uuid)
                .putExtra(BKEY_SET_AS_PREFERRED, input.setAsPreferred);
    }

    @Override
    @Nullable
    public Output parseResult(final int resultCode,
                              @Nullable final Intent intent) {
        if (BuildConfig.DEBUG && DEBUG_SWITCHES.ON_ACTIVITY_RESULT) {
            Logger.d(TAG, "parseResult", "|resultCode=" + resultCode + "|intent=" + intent);
        }

        if (intent == null || resultCode != Activity.RESULT_OK) {
            return null;
        }

        return intent.getParcelableExtra(Output.BKEY);
    }

    @IntDef({ACTION_CLONE, ACTION_EDIT})
    @Retention(RetentionPolicy.SOURCE)
    public @interface EditAction {

    }

    public static class Input {

        @EditAction
        final int action;

        @NonNull
        final String uuid;

        /**
         * If set to {@code true} the edited/cloned style will be set to preferred.
         * If set to {@code false} the preferred state will not be touched.
         */
        final boolean setAsPreferred;

        Input(@EditAction final int action,
              @NonNull final ListStyle style,
              final boolean setAsPreferred) {
            this.action = action;
            this.uuid = style.getUuid();
            this.setAsPreferred = setAsPreferred;
        }
    }

    public static class Output
            implements Parcelable {

        public static final Creator<Output> CREATOR = new Creator<>() {
            @Override
            public Output createFromParcel(@NonNull final Parcel in) {
                return new Output(in);
            }

            @Override
            public Output[] newArray(final int size) {
                return new Output[size];
            }
        };

        private static final String BKEY = TAG + ":Output";

        /** The uuid which was passed into the {@link Input#uuid} for editing. */
        @NonNull
        public final String templateUuid;

        /** SOMETHING was modified. This normally means that BoB will need to rebuild. */
        public final boolean modified;

        /**
         * Either a new UUID if we cloned a style, or the UUID of the style we edited.
         * Will be {@code null} if we edited the global style
         */
        @Nullable
        public final String uuid;

        private Output(@NonNull final String templateUuid,
                       final boolean modified,
                       @Nullable final String uuid) {
            this.templateUuid = templateUuid;
            this.modified = modified;
            this.uuid = uuid;
        }

        /**
         * {@link Parcelable} Constructor.
         *
         * @param in Parcel to construct the object from
         */
        private Output(@NonNull final Parcel in) {
            uuid = in.readString();
            //noinspection ConstantConditions
            templateUuid = in.readString();
            modified = in.readByte() != 0;
        }

        @Override
        public void writeToParcel(@NonNull final Parcel dest,
                                  final int flags) {
            dest.writeString(uuid);
            dest.writeString(templateUuid);
            dest.writeByte((byte) (modified ? 1 : 0));
        }

        @Override
        public int describeContents() {
            return 0;
        }
    }
}
