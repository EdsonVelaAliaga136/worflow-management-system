package com.evela.sigotrab.application.port.out;

import java.util.List;

public record PageResult<T>(List<T> items, long totalElements, int page, int size) {
    public PageResult {
        items = List.copyOf(items);
    }
    public int totalPages() {
        return size == 0 ? 0 : (int) Math.ceil((double) totalElements / size);
    }
    public boolean hasNext() { return page + 1 < totalPages(); }
    public boolean hasPrevious() { return page > 0; }
}
