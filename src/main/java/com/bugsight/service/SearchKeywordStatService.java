package com.bugsight.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.bugsight.entity.SearchKeywordStat;
import com.bugsight.mapper.SearchKeywordStatMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;

@Service
@RequiredArgsConstructor
public class SearchKeywordStatService {

    private final SearchKeywordStatMapper searchKeywordStatMapper;

    public void recordSearchKeyword(String keyword) {
        String normalized = normalizeKeyword(keyword);
        if (normalized.isBlank()) {
            return;
        }

        SearchKeywordStat existing = searchKeywordStatMapper.selectOne(new LambdaQueryWrapper<SearchKeywordStat>()
                .eq(SearchKeywordStat::getKeywordNormalized, normalized)
                .last("LIMIT 1"));

        if (existing == null) {
            SearchKeywordStat created = new SearchKeywordStat();
            created.setKeyword(keyword.trim().replaceAll("\\s+", " "));
            created.setKeywordNormalized(normalized);
            created.setSearchCount(1L);
            created.setLastSearchedAt(LocalDateTime.now());
            searchKeywordStatMapper.insert(created);
            return;
        }

        existing.setKeyword(keyword.trim().replaceAll("\\s+", " "));
        existing.setSearchCount((existing.getSearchCount() == null ? 0L : existing.getSearchCount()) + 1L);
        existing.setLastSearchedAt(LocalDateTime.now());
        searchKeywordStatMapper.updateById(existing);
    }

    public List<HotSearchItem> listHotSearches(int limit) {
        int realLimit = Math.max(1, Math.min(limit, 20));
        return searchKeywordStatMapper.selectList(new LambdaQueryWrapper<SearchKeywordStat>()
                        .orderByDesc(SearchKeywordStat::getSearchCount)
                        .orderByDesc(SearchKeywordStat::getLastSearchedAt)
                        .last("LIMIT " + realLimit))
                .stream()
                .map(item -> new HotSearchItem(item.getKeyword(), item.getSearchCount() == null ? 0L : item.getSearchCount()))
                .toList();
    }

    private String normalizeKeyword(String keyword) {
        if (keyword == null) {
            return "";
        }
        return keyword.trim()
                .replaceAll("\\s+", " ")
                .toLowerCase(Locale.ROOT);
    }

    public record HotSearchItem(String keyword, long count) {}
}
