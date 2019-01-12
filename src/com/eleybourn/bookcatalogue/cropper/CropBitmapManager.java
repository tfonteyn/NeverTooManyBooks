/*
 * Copyright (C) 2009 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.eleybourn.bookcatalogue.cropper;

import android.graphics.BitmapFactory;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.Map;
import java.util.WeakHashMap;

/**
 * This class provides several utilities to cancel bitmap decoding.
 *
 * The function decodeFileDescriptor() is used to decode a bitmap. During
 * decoding if another thread wants to cancel it, it calls the function
 * cancelThreadDecoding() specifying the Thread which is in decoding.
 *
 * cancelThreadDecoding() is sticky until allowThreadDecoding() is called.
 *
 * You can also cancel decoding for a set of threads using ThreadSet as the
 * parameter for cancelThreadDecoding. To put a thread into a ThreadSet, use the
 * add() method. A ThreadSet holds (weak) references to the threads, so you
 * don't need to remove Thread from it if some thread dies.
 */
class CropBitmapManager {
    @Nullable
    private static CropBitmapManager sManager;
    private final Map<Thread, ThreadStatus> mThreadStatus = new WeakHashMap<>();

    private CropBitmapManager() {
    }

    @NonNull
    public static synchronized CropBitmapManager instance() {
        if (sManager == null) {
            sManager = new CropBitmapManager();
        }
        return sManager;
    }
        
    private enum State {
        Cancel, Allow;

        @NonNull
        @Override
        public String toString() {
            switch (this) {
                case Cancel:
                    return "Cancel";
                case Allow:
                    return "Allow";
            }
            return "";
        }
    }

    private static class ThreadStatus {
        @NonNull
        State mState = State.Allow;
        @Nullable
        BitmapFactory.Options mOptions;
    }
}
