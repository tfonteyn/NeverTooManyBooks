package com.eleybourn.bookcatalogue;

import androidx.lifecycle.ViewModel;

import java.util.ArrayList;

public class CoverBrowserModel extends ViewModel {

    /** List of all editions for the given ISBN. */
    private ArrayList<String> mAlternativeEditions;

    void init() {
        if (mAlternativeEditions == null) {

        }
    }
}
