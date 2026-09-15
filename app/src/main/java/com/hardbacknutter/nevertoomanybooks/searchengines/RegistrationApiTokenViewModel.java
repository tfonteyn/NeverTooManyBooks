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

package com.hardbacknutter.nevertoomanybooks.searchengines;

import android.content.Context;
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.ViewModel;

public class RegistrationApiTokenViewModel
        extends ViewModel {

    private RegistrationApiToken registration;

    void init(@NonNull final Context context,
              @NonNull final Bundle args) {
        if (registration == null) {
            registration = RegistrationApiToken.fromBundle(args);
        }
    }

    @NonNull
    String getMessage() {
        return registration.getMessage();
    }

    int getTokenLen() {
        return registration.getTokenLen();
    }

    @Nullable
    String getApiToken() {
        return registration.getApiToken();
    }

    void setApiToken(@Nullable final String apiToken) {
        registration.setApiToken(apiToken);
    }

    boolean validate() {
        final String apiToken = registration.getApiToken();
        return apiToken == null
               || apiToken.isEmpty()
               || apiToken.length() == registration.getTokenLen();
    }

    @NonNull
    String getRequestKey() {
        return registration.getRequestKey();
    }

    /**
     * Get the result to return.
     *
     * @return registration details
     */
    @NonNull
    RegistrationApiToken getRegistration() {
        return registration;
    }
}
