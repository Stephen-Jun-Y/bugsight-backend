# Insect Catalog Seed Notes

本目录中的 `insect_catalog_seed.json` 是与当前 ResNet50 模型 25 个输出类别一一对应的双语百科底稿。

## 整理原则
- 物种级标签按单一物种建档，例如 `Cnaphalocrocis medinalis`、`Lycorma delicatula`。
- 模型中本身是类群/属/科级标签的，不强行映射为单一物种，而是按对应层级建档，例如：
  - `grub` -> 金龟子类幼虫群
  - `wireworm` -> 叩甲科幼虫群
  - `Miridae` / `Cicadellidae` / `Limacodidae` -> 科级类群
  - `Locustoidea` -> 总科级类群
  - `Xylotrechus` / `Thrips` -> 属级/类群标签
- 中文字段作为主展示文本，英文字段作为配套双语文本。
- `species_name_en` 优先保存规范学名或类群拉丁名。
- `recognition_count` 在重复导入时会被保留，不会被重置。

## 双语字段
当前种子文件额外维护以下双语字段：
- 分类：`order_name_cn`、`family_name_cn`、`genus_name_cn`
- 正文：`body_length_en`、`distribution_en`、`active_season_en`、`protection_level_en`
- 百科：`description_en`、`morphology_en`、`habits_en`

## 主要参考来源类型
本批数据整理时优先参考以下权威来源：
- EPPO Global Database
- UC IPM / UC ANR
- University of Minnesota Extension
- UF/IFAS EDIS / Featured Creatures
- Penn State Extension
- LSU AgCenter
- IRRI / JICA 稻作害虫资料
- Atlas of Living Australia / 其他国家级生物多样性名录

## 导入命令
```bash
DB_HOST=localhost DB_NAME=bugsight DB_USER=your_user DB_PASS=your_pass \
python3 scripts/load_insect_catalog.py --apply
```

默认脚本是 dry-run；只有加 `--apply` 才会真正写入数据库。
