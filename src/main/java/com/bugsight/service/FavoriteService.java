package com.bugsight.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.bugsight.common.exception.BusinessException;
import com.bugsight.common.result.ResultCode;
import com.bugsight.entity.Favorite;
import com.bugsight.entity.InsectInfo;
import com.bugsight.mapper.FavoriteMapper;
import com.bugsight.mapper.InsectInfoMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class FavoriteService {

    private final FavoriteMapper favoriteMapper;
    private final InsectInfoMapper insectMapper;

    public Page<InsectInfo> listFavorites(Long userId, int page, int size) {
        Page<Favorite> favPage = favoriteMapper.selectPage(new Page<>(page, size),
                new LambdaQueryWrapper<Favorite>()
                        .eq(Favorite::getUserId, userId)
                        .orderByDesc(Favorite::getCreatedAt));

        // 关联查询昆虫信息
        Page<InsectInfo> result = new Page<>(page, size, favPage.getTotal());
        result.setRecords(favPage.getRecords().stream()
                .map(f -> insectMapper.selectById(f.getInsectId()))
                .filter(i -> i != null)
                .toList());
        return result;
    }

    @Transactional
    public void addFavorite(Long userId, Integer insectId) {
        if (insectMapper.selectById(insectId) == null) {
            throw new BusinessException(ResultCode.NOT_FOUND);
        }
        if (favoriteMapper.selectCount(new LambdaQueryWrapper<Favorite>()
                .eq(Favorite::getUserId, userId)
                .eq(Favorite::getInsectId, insectId)) > 0) {
            throw new BusinessException(ResultCode.ALREADY_FAVORITED);
        }
        Favorite fav = new Favorite();
        fav.setUserId(userId);
        fav.setInsectId(insectId);
        favoriteMapper.insert(fav);
    }

    @Transactional
    public void removeFavorite(Long userId, Integer insectId) {
        int deleted = favoriteMapper.delete(new LambdaQueryWrapper<Favorite>()
                .eq(Favorite::getUserId, userId)
                .eq(Favorite::getInsectId, insectId));
        if (deleted == 0) {
            throw new BusinessException(ResultCode.NOT_FAVORITED);
        }
    }

    public boolean isFavorited(Long userId, Integer insectId) {
        return favoriteMapper.selectCount(new LambdaQueryWrapper<Favorite>()
                .eq(Favorite::getUserId, userId)
                .eq(Favorite::getInsectId, insectId)) > 0;
    }
}
