/*
 * @Copyright 2020 HardBackNutter
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
package com.hardbacknutter.nevertoomanybooks.entities;

import androidx.annotation.NonNull;

/**
 * State engine for the status of an entity.
 */
public class EntityStage {

    private static final String ERROR_CURRENT_STAGE = "Current mStage=";
    private Stage mStage = Stage.ReadOnly;
    private boolean mLocked;

    void lock() {
        if (!mLocked) {
            mLocked = true;
        } else {
            throw new IllegalStateException("Already locked");
        }
    }

    void unlock() {
        if (mLocked) {
            mLocked = false;
        } else {
            throw new IllegalStateException("Already unlocked");
        }
    }

    @NonNull
    public Stage getStage() {
        return mStage;
    }

    public void setStage(@NonNull final Stage stage) {
        if (mLocked) {
            return;
        }

        switch (stage) {
            case ReadOnly:
                if (mStage == Stage.ReadOnly || mStage == Stage.Saved) {
                    mStage = Stage.ReadOnly;
                } else {
                    throw new IllegalStateException(ERROR_CURRENT_STAGE + mStage);
                }
                break;

            case WriteAble:
                if (mStage == Stage.ReadOnly || mStage == Stage.Dirty) {
                    mStage = Stage.WriteAble;
                } else {
                    throw new IllegalStateException(ERROR_CURRENT_STAGE + mStage);
                }
                break;

            case Dirty:
                if (mStage == Stage.WriteAble || mStage == Stage.Dirty) {
                    mStage = Stage.Dirty;
                } else {
                    throw new IllegalStateException(ERROR_CURRENT_STAGE + mStage);
                }

                break;

            case Saved:
                if (mStage == Stage.Dirty) {
                    mStage = Stage.Saved;
                } else {
                    throw new IllegalStateException(ERROR_CURRENT_STAGE + mStage);
                }
                break;
        }
    }

    /**
     * The flow is normally: ReadOnly -> Writeable -> Dirty -> Saved.
     * An 'undo' from WriteAble/Dirty back to 'ReadOnly' is fully supported.
     * <p>
     * The {@link #Saved} stage is not really needed, but serves are a clear distinction between a
     * 'before' and 'after' being edited.
     */
    public enum Stage {
        /** Initial state. */
        ReadOnly,
        /** The entity <strong>can</strong> be modified, but that has not been done yet. */
        WriteAble,
        /** The entity <strong>has</strong> been modified. */
        Dirty,
        /** The modifications have been saved. */
        Saved
    }
}
