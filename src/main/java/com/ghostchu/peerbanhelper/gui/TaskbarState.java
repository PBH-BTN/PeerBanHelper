package com.ghostchu.peerbanhelper.gui;

public enum TaskbarState {
    /**
     * Stops displaying the progress.
     */
    OFF,
    /**
     * The progress indicator displays with normal color and determinate
     * mode.
     */
    NORMAL,
    /**
     * Shows progress as paused, progress can be resumed by the user.
     * Switches to the determinate display.
     */
    PAUSED,
    /**
     * The progress indicator displays activity without specifying what
     * proportion of the progress is complete.
     */
    INDETERMINATE,
    /**
     * Shows that an error has occurred. Switches to the determinate
     * display.
     */
    ERROR
}
