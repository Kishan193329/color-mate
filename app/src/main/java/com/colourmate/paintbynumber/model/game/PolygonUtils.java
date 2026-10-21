package com.colourmate.paintbynumber.model.game;

public class PolygonUtils {

    public static float getScale(Size originalSize, Size containerSize) {
        return Math.min(
                containerSize.width / originalSize.width,
                containerSize.height / originalSize.height
        );
    }

    public static void getOffset(Size contentSize, Size outerSize, Size centerOffset) {
        centerOffset.width =
                (outerSize.width / 2.0f) - (contentSize.width / 2.0f);
        centerOffset.height =
                (outerSize.height / 2.0f) - (contentSize.height / 2.0f);
    }
}

