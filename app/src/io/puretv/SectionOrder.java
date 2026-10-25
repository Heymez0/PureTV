package io.puretv;

import java.util.List;

/** Bounded insertion shared by the drawer and app grid. No persistence or cross-section transfers. */
final class SectionOrder {
    private SectionOrder() {}
    static <T> boolean move(List<T> items, int from, int to, int firstMovable) {
        if (from < firstMovable || to < firstMovable || from >= items.size() || to >= items.size() || from == to) return false;
        // Focus springs use Arrays.asList: rotate slots with set so fixed-size lists work too.
        T item = items.get(from);
        if (from < to) for (int i = from; i < to; i++) items.set(i, items.get(i + 1));
        else for (int i = from; i > to; i--) items.set(i, items.get(i - 1));
        items.set(to, item);
        return true;
    }
}
