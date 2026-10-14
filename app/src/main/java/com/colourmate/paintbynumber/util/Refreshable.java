package com.colourmate.paintbynumber.util;

/**
 * Interface for fragments that can refresh their data.
 * Implement this in child gallery fragments.
 */
public interface Refreshable {
    void onRefresh();
}
