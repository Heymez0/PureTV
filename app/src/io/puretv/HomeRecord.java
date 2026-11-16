package io.puretv;

import java.util.HashSet;
import java.util.Set;

/** The TV-side record of apps Home screen turned off (it outlives an uninstall). No Android types. */
final class HomeRecord {
    private HomeRecord() {}

    /** Recorded by an earlier install and still off: ours to keep on the record and to turn back on. */
    static Set<String> earlier(Set<String> recorded, Set<String> offNow) {
        Set<String> out = new HashSet<>(recorded);
        out.retainAll(offNow);
        return out;
    }
}
