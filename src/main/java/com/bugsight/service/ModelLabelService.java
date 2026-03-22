package com.bugsight.service;

import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.List;

@Slf4j
@Service
public class ModelLabelService {

    @Value("${inference.labels-path:}")
    private String configuredLabelsPath;

    private volatile Map<Integer, LabelMeta> cache;

    public Optional<LabelMeta> getLabel(Integer classId) {
        if (classId == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(loadLabels().get(classId));
    }

    public List<LabelMeta> listLabels() {
        return loadLabels().values().stream()
                .sorted((a, b) -> Integer.compare(a.classId(), b.classId()))
                .toList();
    }

    private Map<Integer, LabelMeta> loadLabels() {
        Map<Integer, LabelMeta> current = cache;
        if (current != null) {
            return current;
        }

        synchronized (this) {
            if (cache != null) {
                return cache;
            }

            Map<Integer, LabelMeta> loaded = new ConcurrentHashMap<>();
            Path labelsPath = resolveLabelsPath();
            if (labelsPath == null || !Files.exists(labelsPath)) {
                log.warn("labels.json not found, skip label fallback: {}", labelsPath);
                cache = loaded;
                return cache;
            }

            try {
                JSONObject payload = JSONUtil.parseObj(Files.readString(labelsPath));
                JSONObject classNames = payload.getJSONObject("class_id_to_name");
                if (classNames == null) {
                    log.warn("labels.json missing class_id_to_name: {}", labelsPath);
                    cache = loaded;
                    return cache;
                }

                for (String key : classNames.keySet()) {
                    int classId = Integer.parseInt(key);
                    String speciesNameEn = classNames.getStr(key, "unknown");
                    loaded.put(classId, new LabelMeta(classId, toChineseName(speciesNameEn), speciesNameEn));
                }
                log.info("Loaded {} label mappings from {}", loaded.size(), labelsPath);
            } catch (IOException e) {
                log.warn("Failed to read labels.json: {}", labelsPath, e);
            } catch (Exception e) {
                log.warn("Failed to parse labels.json: {}", labelsPath, e);
            }

            cache = loaded;
            return cache;
        }
    }

    private Path resolveLabelsPath() {
        if (configuredLabelsPath != null && !configuredLabelsPath.isBlank()) {
            return Path.of(configuredLabelsPath);
        }
        return Path.of(
                System.getProperty("user.dir"),
                "scripts",
                "data",
                "models",
                "resnet50_ip102_balanced",
                "labels.json"
        );
    }

    private String toChineseName(String speciesNameEn) {
        return switch (speciesNameEn) {
            case "rice leaf roller" -> "稻纵卷叶螟";
            case "asiatic rice borer" -> "二化螟";
            case "white backed plant hopper" -> "白背飞虱";
            case "rice water weevil" -> "稻水象甲";
            case "grub" -> "蛴螬";
            case "mole cricket" -> "蝼蛄";
            case "wireworm" -> "金针虫";
            case "black cutworm" -> "黑地老虎";
            case "corn borer" -> "玉米螟";
            case "army worm" -> "粘虫";
            case "aphids" -> "蚜虫";
            case "cabbage army worm" -> "甘蓝夜蛾";
            case "beet army worm" -> "甜菜夜蛾";
            case "flax budworm" -> "亚麻芽虫";
            case "Locustoidea" -> "蝗总科";
            case "legume blister beetle" -> "豆芫菁";
            case "blister beetle" -> "芫菁";
            case "Thrips" -> "蓟马";
            case "Limacodidae" -> "刺蛾科";
            case "Lycorma delicatula" -> "斑衣蜡蝉";
            case "Xylotrechus" -> "天牛属";
            case "Cicadella viridis" -> "绿叶蝉";
            case "Miridae" -> "盲蝽科";
            case "Prodenia litura" -> "斜纹夜蛾";
            case "Cicadellidae" -> "叶蝉科";
            default -> speciesNameEn;
        };
    }

    public record LabelMeta(Integer classId, String speciesNameCn, String speciesNameEn) {}
}
