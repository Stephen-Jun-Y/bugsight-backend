package com.bugsight.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.bugsight.common.exception.BusinessException;
import com.bugsight.common.result.ResultCode;
import com.bugsight.entity.InsectInfo;
import com.bugsight.mapper.InsectInfoMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class InsectService {

    private final InsectInfoMapper insectMapper;
    private final InsectCatalogService insectCatalogService;

    public List<InsectInfo> getPopular(int limit) {
        return insectMapper.selectList(new LambdaQueryWrapper<InsectInfo>()
                .orderByDesc(InsectInfo::getRecognitionCount)
                .last("LIMIT " + limit));
    }

    public Page<InsectInfo> search(String q, Integer harmLevel, int page, int size) {
        Page<InsectInfo> pageReq = new Page<>(page, size);
        LambdaQueryWrapper<InsectInfo> wrapper = new LambdaQueryWrapper<InsectInfo>()
                .like(q != null && !q.isBlank(), InsectInfo::getSpeciesNameCn, q)
                .or(q != null && !q.isBlank(),
                        w -> w.like(InsectInfo::getSpeciesNameEn, q))
                .eq(harmLevel != null, InsectInfo::getHarmLevel, harmLevel)
                .orderByDesc(InsectInfo::getRecognitionCount);
        return insectMapper.selectPage(pageReq, wrapper);
    }

    public InsectInfo getById(Integer id) {
        InsectInfo insect = insectCatalogService.getOrCreate(id);
        if (insect == null) throw new BusinessException(ResultCode.NOT_FOUND);
        return insect;
    }

    /** 相似物种：同科下按识别量取 Top5（排除自身） */
    public List<InsectInfo> getSimilar(Integer id) {
        InsectInfo self = getById(id);
        if (insectMapper.selectById(id) == null) {
            return insectCatalogService.listFallbackSimilar(id, 5);
        }
        return insectMapper.selectList(new LambdaQueryWrapper<InsectInfo>()
                .eq(InsectInfo::getFamilyName, self.getFamilyName())
                .ne(InsectInfo::getId, id)
                .orderByDesc(InsectInfo::getRecognitionCount)
                .last("LIMIT 5"));
    }
}
