package cz.halloween.core;

import java.util.UUID;

public interface HalloweenService {
    long getFragments(UUID playerId);
    long addFragments(UUID playerId, long amount, String source);
    long getServerFragments();
    long getGlobalGoal();
    boolean isEventEnabled();
    double getGlobalProgressPercent();
    int getCurseLevel(UUID playerId);
    String getCurseName(UUID playerId);
}
