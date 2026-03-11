package com.bugsight.dto.response;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class PageResponse<T> {

    private List<T> list;
    private long total;
    private long page;
    private long size;

    public static <T> PageResponse<T> from(Page<T> pageData) {
        return PageResponse.<T>builder()
                .list(pageData.getRecords())
                .total(pageData.getTotal())
                .page(pageData.getCurrent())
                .size(pageData.getSize())
                .build();
    }
}
