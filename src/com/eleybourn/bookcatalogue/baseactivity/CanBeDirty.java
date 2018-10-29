package com.eleybourn.bookcatalogue.baseactivity;

/**
 * Would ideally 'live' inside {@link BaseActivity} but that creates a circular conflict of course.
 */
public interface CanBeDirty {
    boolean isDirty();

    void setDirty(final boolean isDirty);
}
