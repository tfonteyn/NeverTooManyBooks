package com.eleybourn.bookcatalogue.widgets;

import androidx.annotation.Nullable;

/**
 * Replacement for {@link android.widget.SectionIndexer}.
 * <p>
 * This class is a better interface that just gets text for rows as needed rather
 * than having to build a huge index at start.
 */
public interface SectionIndexerV2 {

    @Nullable
    String[] getSectionTextForPosition(int position);
}
