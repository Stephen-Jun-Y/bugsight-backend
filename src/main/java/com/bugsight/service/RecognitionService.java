package com.bugsight.service;

import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.bugsight.common.exception.BusinessException;
import com.bugsight.common.result.ResultCode;
import com.bugsight.dto.request.EditHistoryRequest;
import com.bugsight.entity.InsectInfo;
import com.bugsight.entity.RecognitionHistory;
import com.bugsight.mapper.InsectInfoMapper;
import com.bugsight.mapper.RecognitionHistoryMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class RecognitionService {

    private final RecognitionHistoryMapper historyMapper;
    private final InsectInfoMapper insectMapper;
    private final InferenceService inferenceService;
    private final FileService fileService;

    @Transactional
    public RecognitionHistory recognize(Long userId, MultipartFile file, Integer source,
                                        String locationName, BigDecimal lat, BigDecimal lng) {
        // 1. 保存图片
        String imageUrl = fileService.saveImage(file);

        // 2. 调用推理服务
        InferenceService.InferenceResult inferResult = inferenceService.predict(file);

        // 3. 构建 top3 JSON
        List<Map<String, Object>> top3List = inferResult.getTop3().stream().map(item -> {
            InsectInfo insect = insectMapper.selectById(item.getClassIndex());
            return Map.<String, Object>of(
                    "insectId", item.getClassIndex(),
                    "nameCn", insect != null ? insect.getSpeciesNameCn() : "未知",
                    "confidence", item.getConfidence()
            );
        }).collect(Collectors.toList());

        // 4. 更新昆虫识别次数
        insectMapper.update(null, new com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper<InsectInfo>()
                .eq(InsectInfo::getId, inferResult.getTop1().getClassIndex())
                .setSql("recognition_count = recognition_count + 1"));

        // 5. 保存历史记录
        RecognitionHistory history = new RecognitionHistory();
        history.setUserId(userId);
        history.setImageUrl(imageUrl);
        history.setImageOriginalName(file.getOriginalFilename());
        history.setTop1InsectId(inferResult.getTop1().getClassIndex());
        history.setTop1Confidence(BigDecimal.valueOf(inferResult.getTop1().getConfidence()));
        history.setTop3Result(JSONUtil.toJsonStr(top3List));
        history.setLocationName(locationName);
        history.setLatitude(lat);
        history.setLongitude(lng);
        history.setUploadSource(source != null ? source : 1);
        historyMapper.insert(history);

        return history;
    }

    public Page<RecognitionHistory> pageHistory(Long userId, int page, int size, String q) {
        Page<RecognitionHistory> pageReq = new Page<>(page, size);
        LambdaQueryWrapper<RecognitionHistory> wrapper = new LambdaQueryWrapper<RecognitionHistory>()
                .eq(RecognitionHistory::getUserId, userId)
                .orderByDesc(RecognitionHistory::getCreatedAt);
        return historyMapper.selectPage(pageReq, wrapper);
    }

    public RecognitionHistory getDetail(Long userId, Long historyId) {
        RecognitionHistory history = historyMapper.selectById(historyId);
        if (history == null || !history.getUserId().equals(userId)) {
            throw new BusinessException(ResultCode.NOT_FOUND);
        }
        return history;
    }

    @Transactional
    public void editHistory(Long userId, Long historyId, EditHistoryRequest req) {
        RecognitionHistory history = historyMapper.selectById(historyId);
        if (history == null || !history.getUserId().equals(userId)) {
            throw new BusinessException(ResultCode.NOT_FOUND);
        }
        if (req.getNote() != null) history.setNote(req.getNote());
        if (req.getLocationName() != null) history.setLocationName(req.getLocationName());
        if (req.getLatitude() != null) history.setLatitude(req.getLatitude());
        if (req.getLongitude() != null) history.setLongitude(req.getLongitude());
        historyMapper.updateById(history);
    }

    @Transactional
    public void deleteHistory(Long userId, Long historyId) {
        RecognitionHistory history = historyMapper.selectById(historyId);
        if (history == null || !history.getUserId().equals(userId)) {
            throw new BusinessException(ResultCode.NOT_FOUND);
        }
        historyMapper.deleteById(historyId);
    }

    @Transactional
    public int batchDelete(Long userId, List<Long> ids) {
        LambdaQueryWrapper<RecognitionHistory> wrapper = new LambdaQueryWrapper<RecognitionHistory>()
                .in(RecognitionHistory::getId, ids)
                .eq(RecognitionHistory::getUserId, userId);
        return historyMapper.delete(wrapper);
    }
}
