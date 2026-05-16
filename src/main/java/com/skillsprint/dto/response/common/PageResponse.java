package com.skillsprint.dto.response.common;

import java.util.List;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.experimental.FieldDefaults;
import org.springframework.data.domain.Page;

@Getter
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
public class PageResponse<T> {

    List<T> items;
    int page;
    int size;
    long totalItems;
    int totalPages;
    boolean first;
    boolean last;

    public static <T> PageResponse<T> from(Page<T> page) {
        return PageResponse.<T>builder()
                .items(page.getContent())
                .page(page.getNumber())
                .size(page.getSize())
                .totalItems(page.getTotalElements())
                .totalPages(page.getTotalPages())
                .first(page.isFirst())
                .last(page.isLast())
                .build();
    }
}
