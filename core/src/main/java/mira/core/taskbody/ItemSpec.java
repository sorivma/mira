package mira.core.taskbody;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Registry identity and portable values; no game classes cross into the task model. */
public final class ItemSpec {
    public final String item, tagHash, displayName, tag;
    public final int damage;
    public final List<String> ores;
    public ItemSpec(String item, int damage, String tagHash, List<String> ores, String displayName, String tag) {
        this.item = item; this.damage = damage; this.tagHash = tagHash;
        this.ores = Collections.unmodifiableList(new ArrayList<>(ores));
        this.displayName = displayName; this.tag = tag;
    }
}
