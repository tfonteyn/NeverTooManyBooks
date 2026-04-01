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

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RestrictTo;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import java.util.List;

import com.hardbacknutter.prefslib.internal.UICallback;
import com.hardbacknutter.util.livedataevent.LiveDataEvent;

public class SettingsManagerViewModel
        extends ViewModel
        implements UICallback {

    private final MutableLiveData<LiveDataEvent<ValueUpdate<Setting, Object>>>
            onChanged = new MutableLiveData<>();
    private final MutableLiveData<LiveDataEvent<Setting>>
            onClick = new MutableLiveData<>();
    private final MutableLiveData<LiveDataEvent<Setting>>
            onShowDialog = new MutableLiveData<>();

    private SettingsDataStore dataStore;
    private List<Setting> settings;

    /**
     * Constructor.
     * <p>
     * ONLY to be called from the hosting fragment.
     * <p>
     * Do NOT call from the dialog fragments.
     *
     * @param dataStore to use
     * @param settings          current list
     */
    void init(@NonNull final SettingsDataStore dataStore,
              @NonNull final List<Setting> settings) {
        if (this.dataStore == null) {
            this.dataStore = dataStore;
        }
        // This ViewModel is owned by the Activity, so we can share it
        // between the hosting fragment and any dialog fragments as needed.
        // So we MUST always update the FRAGMENT settings.
        this.settings = settings;
    }

    /**
     * Get the setting for the given key.
     *
     * @param key to get
     * @param <S> type of the Setting
     *
     * @return setting
     *
     * @throws IllegalArgumentException (debug) if the key is unknown
     */
    @NonNull
    public <S extends Setting> S requireSetting(@NonNull final CharSequence key) {
        for (final Setting s : settings) {
            if (s.getKey().contentEquals(key)) {
                //noinspection unchecked
                return (S) s;
            }
        }
        throw new IllegalArgumentException(key.toString());
    }

    @NonNull
    List<Setting> getSettings() {
        return settings;
    }

    @NonNull
    SettingsDataStore getDataStore() {
        return dataStore;
    }

    @NonNull
    LiveData<LiveDataEvent<Setting>> onClick() {
        return onClick;
    }

    @NonNull
    LiveData<LiveDataEvent<Setting>> onShowDialog() {
        return onShowDialog;
    }

    @NonNull
    LiveData<LiveDataEvent<ValueUpdate<Setting, Object>>> onChanged() {
        return onChanged;
    }

    /**
     * Callback to tell us a setting was changed.
     * <p>
     * Typically/only used from a settings dialog editor.
     *
     * @param setting  to change
     * @param newValue for the setting
     */
    @Override
    public void onChange(@NonNull final Setting setting,
                         @Nullable final Object newValue) {
        onChanged.setValue(LiveDataEvent.of(new ValueUpdate<>(setting, newValue)));
    }

    @RestrictTo(RestrictTo.Scope.LIBRARY)
    @Override
    public void onClick(@NonNull final Setting setting) {
        onClick.setValue(LiveDataEvent.of(setting));
    }

    @RestrictTo(RestrictTo.Scope.LIBRARY)
    @Override
    public void showDialog(@NonNull final Setting setting) {
        onShowDialog.setValue(LiveDataEvent.of(setting));
    }
}
