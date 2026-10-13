package cz.halloween.mod;

import java.util.List;
import java.util.Locale;

/**
 * Native equivalent of the ordered nine-chapter story in the Paper implementation.
 * Pure metadata and matching rules are kept testable without booting Minecraft.
 */
public final class HalloweenStoryQuests {
    public record Quest(String id, String title, String description, String objective, int target, long reward) {
        public Quest {
            if (id == null || id.isBlank()) throw new IllegalArgumentException("Quest ID is required.");
            if (title == null || title.isBlank()) throw new IllegalArgumentException("Quest title is required.");
            if (objective == null || objective.isBlank()) throw new IllegalArgumentException("Quest objective is required.");
            if (target < 1 || reward < 0L) throw new IllegalArgumentException("Quest target/reward is invalid.");
        }
    }

    public static final List<Quest> ALL = List.of(
            new Quest("fog-patrol", "Znamení v mlze",
                    "Zabij 10 nepřátelských tvorů a zjisti, co se děje po setmění.",
                    "mob-kill", 10, 150),
            new Quest("gravekeepers-debt", "Dluh hrobníkovi",
                    "Najdi a poraz 2 hrobníky na toulkách světem.",
                    "special:gravekeeper", 2, 250),
            new Quest("blood-silk", "Krvavé hedvábí",
                    "Připrav krvavé pavouky o jejich kořist — poraz 3 kusy.",
                    "special:blood-spider", 3, 300),
            new Quest("cursed-harvest", "Sklizeň za úplňku",
                    "Sklid 20 zralých plodin a posil svou výbavu.",
                    "farming", 20, 200),
            new Quest("witching-hour", "Čarodějnická hodina",
                    "Najdi a poraz 2 hexové čarodějky.",
                    "special:hex-witch", 2, 400),
            new Quest("pumpkin-wraith", "Dýňové prokletí",
                    "Znič 2 dýňové přízraky, než jejich popel zahalí okolí.",
                    "special:pumpkin-wraith", 2, 450),
            new Quest("void-reaper", "Ženec z prázdnoty",
                    "Vypátrej a poraz 2 Žence prázdnoty.",
                    "special:void-reaper", 2, 600),
            new Quest("frost-stalker", "Ledová stopa",
                    "Přemoz 2 Ledové stopaře v chladných krajích.",
                    "special:frost-stalker", 2, 650),
            new Quest("nightmare", "Poslední noční můra",
                    "Přežij noc a poraz Noční můru.",
                    "special:nightmare", 1, 900)
    );

    private HalloweenStoryQuests() {}

    public static int normalizeIndex(long storedIndex) {
        if (storedIndex <= 0L) return 0;
        if (storedIndex >= ALL.size()) return ALL.size();
        return (int) storedIndex;
    }

    public static String normalizeMobId(String id) {
        return id == null ? "" : id.trim().toLowerCase(Locale.ROOT).replace('_', '-');
    }

    public static boolean matchesAction(Quest quest, String action) {
        return quest != null && action != null && quest.objective().equalsIgnoreCase(action);
    }

    public static boolean matchesMobKill(Quest quest, String mobId, boolean hostile) {
        if (quest == null || !hostile) return false;
        if ("mob-kill".equalsIgnoreCase(quest.objective())) return true;
        if (!quest.objective().startsWith("special:")) return false;
        return quest.objective().equalsIgnoreCase("special:" + normalizeMobId(mobId));
    }
}
