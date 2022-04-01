package com.ubcohci.fingerdetection;

import androidx.annotation.NonNull;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * A utility class to buffer and detect posture/gesture.
 */
public class GestureDetector {

    // An enum define all possible task for each detection result
    public enum MotionTask {
        SWITCH_VOLUME,
        SWITCH_VIDEO,
        SWITCH_BRIGHTNESS,
        ADJUST_VOLUME,
        WAITING,
        NONE
    }

    // A list of possible videos to switch between
    private static final String[] videoHashes = new String[] {
            "HzeK7g8cD0Y",
            "UwxatzcYf9Q",
            "-QuVe-hjMs0"
    };


    // A list of all possible postures
    private static final String[] postures = new String[] {
            "straight_index",
            "straight_two",
            "straight_all",
            "V",
            "hook",
            "one_palm",
            "two_palm",
            "all_palm"
    };

    // A list of possible sequences of postures
    private static final List<String[]> gestures = new ArrayList<>();


    // A mapping from posture to volume level
    private static final Map<String, Integer> postureToVolume = new HashMap<>();

    // A mapping from gestures to volume level
    private static final Map<String, Integer> gestureToBrightness = new HashMap<>();


    // Internal track for the current video
    private int currentIndex = -1;

    // A buffer to check for a gesture
    private final List<String> gestureBuffer = new ArrayList<>();

    // A integer representing max time-out
    // This is updated on based on newly calculated latency
    private int maxTimeout;

    // Representing the time marker for last detected posture.
    private long lastDetectTime;

    // A flag to check if timeOut situation is reached!
    private boolean isTimeout;

    /**
     * Constructor
     */
    public GestureDetector() {}

    // Static initializer
    static {
        //Straight index finger to set the sound at 10x
        postureToVolume.put(postures[0], 10);
        //Straight two fingers to set the sound at 20x
        postureToVolume.put(postures[1], 20);
        //Straight all fingers to set the sound at 30x
        postureToVolume.put(postures[2], 30);

        // V to switch to next video --> Use getNextHash()
        // Hook to switch to previous video --> Use getPreviousHash()

        // Single finger, then two fingers to increase the volume
        gestures.add(new String[] {postures[0], postures[1]});
        // Two fingers, then a single finger to decrease the volume
        gestures.add(new String[] {postures[1], postures[0]});

        // One finger - one finger – one finger – brightness level 10
        gestures.add(new String[] {postures[0], postures[0], postures[0]});
        gestureToBrightness.put(String.format(Locale.CANADA, "%s_%s_%s", postures[0], postures[0], postures[0]), 10);
        // One finger - one finger – two fingers – brightness level 20
        gestures.add(new String[] {postures[0],postures[0], postures[1]});
        gestureToBrightness.put(String.format(Locale.CANADA, "%s_%s_%s", postures[0], postures[0], postures[1]), 20);
    }

    /**
     * Get the task to perform based on the current posture.
     * @param posture The class name of the current posture (exists or not exists)
     * @return A enum representing a task.
     */
    public MotionTask getMotionTask(@NonNull String posture) {
        MotionTask task = null;
        if (isPostureExist(posture)) { // If the posture exists
            if (addToBuffer(posture)) { // Add the new posture to buffer
                switch (gestureBuffer.size()) {
                    case 1: task = MotionTask.WAITING; break;
                    case 2:
                        task = getGestureTask(gestureBuffer.toArray(new String[0])); // Try executing gesture with 2 postures
                        if (task != MotionTask.NONE) {
                            gestureBuffer.clear();
                        } else {
                            task = MotionTask.WAITING;
                        }
                        break;
                    case 3:
                        task = getGestureTask(gestureBuffer.toArray(new String[0]));
                        gestureBuffer.clear();
                        break; // Execute current gesture with 2 postures
                }
            } else { // Duplicate posture to last entry
                task = MotionTask.WAITING;
            }

        } else { // Server cannot recognize a posture
            switch (gestureBuffer.size()) {
                case 0: task = MotionTask.NONE; break; // Nothing in buffer so do nothing
                case 1: task = getPostureTask(gestureBuffer.get(0)); break; // Executing current posture
                case 2: task = getGestureTask(gestureBuffer.toArray(new String[0])); break; // Execute current gesture with 2 postures
            }
            gestureBuffer.clear(); // Clear buffer
        }
        return task;
    }

    /**
     * Find the volume level for the current posture.
     * @param posture The posture's class name
     * @return Volume level
     */
    public Integer findVolumeLevel(@NonNull String posture) {
        return postureToVolume.get(posture);
    }

    /**
     * Get the next video's hash based on the class name.
     * @param posture The class name of the current posture.
     * @return The hash for the corresponding video.
     */
    public String getAnotherHash(@NonNull String posture) {
        return isNextHash(posture)? getNextHash(): getPreviousHash();
    }

    /**
     * Get the posture's class name at the current index.
     * @param index The index of the posture
     * @return The posture's class name
     */
    public String getPostureName(int index) {
        return index > postures.length - 1? null: postures[index];
    }

    /**
     * Check whether the posture initiate forward or backward jump for the cursor
     * of the video list.s
     * @param className The posture's class name.
     * @return Whether the cursor should move forward or backward.
     */
    private boolean isNextHash(@NonNull String className) {
        return className.equals(getPostureName(3));
    }

    /**
     * Get the next hash in line for the next video.
     * @return The hash value of the next video.
     */
    private String getNextHash() {
        currentIndex = (currentIndex + 1) % videoHashes.length;
        return videoHashes[currentIndex];
    }

    /**
     * Get the next hash in line for the next video.
     * @return The hash value of the next video.
     */
    private String getPreviousHash() {
        currentIndex--; // Decrement counter
        if (currentIndex < 0) {
            currentIndex = videoHashes.length - 1;
        }
        return videoHashes[currentIndex];
    }

    /**
     * Check if a posture is supported.
     * @param posture The posture's class name.
     * @return Whether a posture is supported.
     */
    private boolean isPostureExist(String posture) {
        if (posture != null) {
            for (String _posture: postures) {
                if (posture.equals(_posture)) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * Add a posture to an internal buffer.
     * @param posture The posture's class name.
     * @return Whether the add operation is successful.
     */
    private boolean addToBuffer(String posture) {
        // Check if the last element is the same as posture
        // If so, don't add
        if (gestureBuffer.size() > 1 && gestureBuffer.get(gestureBuffer.size() - 1).equals(posture)) {
            return false;
        } else {
            gestureBuffer.add(posture);
            return true;
        }
    }

    /**
     * Get the MotionTask based on the posture.
     * @param posture The posture's class name.
     * @return The task corresponding to the posture.
     */
    private MotionTask getPostureTask(@NonNull String posture) {
        if (posture.equals(postures[3]) || posture.equals(postures[4])) {
            return MotionTask.SWITCH_VIDEO;
        } else if (posture.equals(postures[0]) || posture.equals(postures[1]) || posture.equals(postures[2])) {
            return MotionTask.SWITCH_VOLUME;
        } else {
            return MotionTask.NONE;
        }
    }

    /**
     * Get the MotionTask based on a list of postures (gesture)
     * @param gesture The list of posture's class name.
     * @return The task corresponding to the gesture.
     */
    private MotionTask getGestureTask(@NonNull String[] gesture) {
        for (String[] _gesture: gestures) {
            if (Arrays.equals(_gesture, gesture)) {
                return gesture.length == 2? MotionTask.ADJUST_VOLUME: MotionTask.SWITCH_BRIGHTNESS;
            }
        }
        return MotionTask.NONE;
    }

    /**
     * From the list of postures (gesture), return its string representation to retrieve
     * its value (i.e. brightness level).
     * @param gesture The list of posture's class names.
     * @return String representation of the gesture.
     */
    public String gestureToString(@NonNull String[] gesture) {
        final StringBuilder stringBuilder = new StringBuilder();
        String delimiter = "";
        for (String s: gesture) {
            stringBuilder.append(delimiter);
            stringBuilder.append(s);
            delimiter = "_";
        }
        return stringBuilder.toString();
    }
}
