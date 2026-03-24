package com.bugsight.service;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.bugsight.entity.SearchKeywordStat;
import com.bugsight.mapper.SearchKeywordStatMapper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SearchKeywordStatServiceTest {

    @BeforeAll
    static void initTableInfo() {
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(new MybatisConfiguration(), "");
        TableInfoHelper.initTableInfo(assistant, SearchKeywordStat.class);
    }

    @Mock
    private SearchKeywordStatMapper searchKeywordStatMapper;

    @InjectMocks
    private SearchKeywordStatService searchKeywordStatService;

    @Test
    void insertsNormalizedKeywordWhenMissing() {
        when(searchKeywordStatMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(null);

        searchKeywordStatService.recordSearchKeyword("  Green Leafhopper  ");

        ArgumentCaptor<SearchKeywordStat> captor = ArgumentCaptor.forClass(SearchKeywordStat.class);
        verify(searchKeywordStatMapper).insert(captor.capture());
        assertEquals("Green Leafhopper", captor.getValue().getKeyword());
        assertEquals("green leafhopper", captor.getValue().getKeywordNormalized());
        assertEquals(1L, captor.getValue().getSearchCount());
    }

    @Test
    void incrementsExistingKeywordCount() {
        SearchKeywordStat existing = new SearchKeywordStat();
        existing.setId(3L);
        existing.setKeyword("绿叶蝉");
        existing.setKeywordNormalized("绿叶蝉");
        existing.setSearchCount(4L);

        when(searchKeywordStatMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(existing);

        searchKeywordStatService.recordSearchKeyword("绿叶蝉");

        assertEquals(5L, existing.getSearchCount());
        verify(searchKeywordStatMapper).updateById(existing);
    }

    @Test
    void returnsHotSearchesOrderedFromMapper() {
        SearchKeywordStat first = new SearchKeywordStat();
        first.setKeyword("绿叶蝉");
        first.setSearchCount(12L);
        SearchKeywordStat second = new SearchKeywordStat();
        second.setKeyword("稻纵卷叶螟");
        second.setSearchCount(9L);

        when(searchKeywordStatMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of(first, second));

        List<SearchKeywordStatService.HotSearchItem> result = searchKeywordStatService.listHotSearches(8);

        assertEquals(2, result.size());
        assertEquals("绿叶蝉", result.get(0).keyword());
        assertEquals(12L, result.get(0).count());
    }
}
