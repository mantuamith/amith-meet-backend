// src/test/java/com/algomeet/authservice/testsupport/PageResponses.java
package com.algomeet.authservice.support;

import com.algomeet.authservice.dto.PageResponse;

import java.util.Collections;
import java.util.List;

public final class PageResponses {
    private PageResponses() {}

    /** Basic factory: computes totalPages/last for you. */
    public static <T> PageResponse<T> of(List<T> content, int page, int size, long totalElements) {
        if (content == null) content = Collections.emptyList();
        int totalPages = size <= 0 ? 1 : (int) Math.max(1, Math.ceil((double) totalElements / (double) size));
        boolean last = page >= (totalPages - 1);
        return PageResponse.<T>builder()
                .content(content)
                .page(page)
                .size(size)
                .totalElements(totalElements)
                .totalPages(totalPages)
                .last(last)
                .build();
    }

    /** Single-page convenience: totalElements = content.size(), page=0, size=content.size(), last=true. */
    public static <T> PageResponse<T> singlePage(List<T> content) {
        int size = content == null ? 0 : content.size();
        return of(content, 0, size == 0 ? 1 : size, size);
    }

    /** Empty page with arbitrary paging numbers. */
    public static <T> PageResponse<T> empty(int page, int size) {
        return of(Collections.emptyList(), page, size, 0);
    }

    /** Build a page when you know total count but you’re returning only a slice. */
    public static <T> PageResponse<T> slice(List<T> content, int page, int size, long totalElements) {
        return of(content, page, size, totalElements);
    }
}
