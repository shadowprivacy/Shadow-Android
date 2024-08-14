package su.sres.imageeditor.core;

public interface UndoRedoStackListener {

    void onAvailabilityChanged(boolean undoAvailable, boolean redoAvailable);
}