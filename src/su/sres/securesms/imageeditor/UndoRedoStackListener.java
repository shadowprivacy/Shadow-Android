package su.sres.securesms.imageeditor;

public interface UndoRedoStackListener {

    void onAvailabilityChanged(boolean undoAvailable, boolean redoAvailable);
}