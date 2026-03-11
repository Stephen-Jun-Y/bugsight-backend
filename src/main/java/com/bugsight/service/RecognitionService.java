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
import java.util.ArrayList;
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
        if (q != null && !q.isBlank()) {
            wrapper.and(w -> w.like(RecognitionHistory::getImageOriginalName, q)
                    .or().like(RecognitionHistory::getNote, q)
                    .or().like(RecognitionHistory::getLocationName, q));
        }
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


    public com.bugsight.dto.response.RecognitionResponse getRecognitionResult(Long userId, Long recognitionId) {
        return toRecognitionResponse(getDetail(userId, recognitionId));
    }

    public Page<com.bugsight.dto.response.RecognitionResponse> pageRecognitionResults(Long userId, int page, int size, String q) {
        Page<RecognitionHistory> historyPage = pageHistory(userId, page, size, q);
        Page<com.bugsight.dto.response.RecognitionResponse> result = new Page<>(page, size, historyPage.getTotal());
        result.setRecords(historyPage.getRecords().stream().map(this::toRecognitionResponse).toList());
        return result;
    }

    public com.bugsight.dto.response.RecognitionResponse toRecognitionResponse(RecognitionHistory history) {
        InsectInfo top1 = history.getTop1InsectId() != null ? insectMapper.selectById(history.getTop1InsectId()) : null;
        List<com.bugsight.dto.response.RecognitionResponse.SimilarSpecies> similar = new ArrayList<>();
        if (history.getTop3Result() != null && JSONUtil.isTypeJSON(history.getTop3Result())) {
            List<Map<String, Object>> top3 = JSONUtil.toList(history.getTop3Result(), Map.class);
            similar = top3.stream().map(item -> com.bugsight.dto.response.RecognitionResponse.SimilarSpecies.builder()
                    .speciesId(Integer.valueOf(String.valueOf(item.getOrDefault("insectId", 0))))
                    .name(String.valueOf(item.getOrDefault("nameCn", "未知")))
                    .score(new BigDecimal(String.valueOf(item.getOrDefault("confidence", "0"))))
                    .build()).toList();
        }

        return com.bugsight.dto.response.RecognitionResponse.builder()
                .recognitionId(history.getId())
                .species(com.bugsight.dto.response.RecognitionResponse.Species.builder()
                        .id(history.getTop1InsectId())
                        .name(top1 != null ? top1.getSpeciesNameCn() : "未知")
                        .latinName(top1 != null ? top1.getSpeciesNameEn() : "Unknown")
                        .build())
                .confidence(history.getTop1Confidence())
                .similar(similar)
                .imageUrl(history.getImageUrl())
                .note(history.getNote())
                .location(history.getLocationName())
                .capturedAt(history.getCreatedAt())
                .build();
    }

}