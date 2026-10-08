package ani.rss.service;

import ani.rss.cache.CacheUtils;
import ani.rss.commons.GsonStatic;
import ani.rss.comparator.WeekComparator;
import ani.rss.entity.Ani;
import ani.rss.entity.BgmInfo;
import ani.rss.entity.BgmSeason;
import ani.rss.entity.Config;
import ani.rss.entity.dto.AniBTQueryDTO;
import ani.rss.entity.dto.BgmSeasonQueryDTO;
import ani.rss.entity.AniBT;
import ani.rss.util.basic.HttpReq;
import ani.rss.util.other.AniUtil;
import ani.rss.util.other.BgmUtil;
import ani.rss.util.other.ConfigUtil;
import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.date.DateUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.http.HttpRequest;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.Serializable;
import java.time.LocalDate;
import java.util.*;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
public class BgmSeasonService {

    @Autowired(required = false)
    private AniBTService aniBTService;

    private static final String[] WEEKDAY_CN = {
            "星期日", "星期一", "星期二", "星期三", "星期四", "星期五", "星期六"
    };

    /**
     * 获取当前季度名称，标准格式为年月，如 "202610"
     */
    public static String getCurrentSeason() {
        LocalDate now = LocalDate.now();
        int year = now.getYear();
        int month = now.getMonthValue();
        String seasonMonth;
        if (month >= 1 && month <= 3) {
            seasonMonth = "01";
        } else if (month >= 4 && month <= 6) {
            seasonMonth = "04";
        } else if (month >= 7 && month <= 9) {
            seasonMonth = "07";
        } else {
            seasonMonth = "10";
        }
        return year + seasonMonth;
    }

    /**
     * 获取最近的可用季度列表，标准格式为年月，如 ["202610", "202607", "202604", "202601", "202510", "202507", "202504", "202501"]
     */
    public static List<String> getAvailableSeasons() {
        List<String> list = new ArrayList<>();
        LocalDate now = LocalDate.now();
        int year = now.getYear();
        int month = now.getMonthValue();
        int seasonIndex = (month - 1) / 3;
        String[] seasonMonths = {"01", "04", "07", "10"};

        for (int i = 0; i < 8; i++) {
            list.add(year + seasonMonths[seasonIndex]);
            seasonIndex--;
            if (seasonIndex < 0) {
                seasonIndex = 3;
                year--;
            }
        }
        return list;
    }

    /**
     * 根据季度名称（如 "202610"）换算日期起止区间 [start, end)
     */
    public static String[] getSeasonDateRange(String season) {
        if (StrUtil.isBlank(season) || season.length() < 5) {
            return null;
        }
        try {
            int year = Integer.parseInt(season.substring(0, 4));
            String sub = season.substring(4);
            if (sub.startsWith("01") || sub.startsWith("1") || sub.contains("冬") || sub.contains("01月") || sub.contains("1月")) {
                return new String[]{String.format("%04d-01-01", year), String.format("%04d-04-01", year)};
            } else if (sub.startsWith("04") || sub.startsWith("4") || sub.contains("春") || sub.contains("04月") || sub.contains("4月")) {
                return new String[]{String.format("%04d-04-01", year), String.format("%04d-07-01", year)};
            } else if (sub.startsWith("07") || sub.startsWith("7") || sub.contains("夏") || sub.contains("07月") || sub.contains("7月")) {
                return new String[]{String.format("%04d-07-01", year), String.format("%04d-10-01", year)};
            } else if (sub.startsWith("10") || sub.contains("秋") || sub.contains("10月")) {
                return new String[]{String.format("%04d-10-01", year), String.format("%04d-01-01", year + 1)};
            }
            return null;
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * 获取权威番剧列表
     */
    public BgmSeason list(BgmSeasonQueryDTO dto) {
        if (dto == null) {
            dto = new BgmSeasonQueryDTO();
        }

        List<String> bgmIdList = AniUtil.ANI_LIST
                .stream()
                .map(Ani::getBgmUrl)
                .filter(StrUtil::isNotBlank)
                .map(BgmUtil::getSubjectId)
                .distinct()
                .toList();

        String currentSeason = getCurrentSeason();
        List<String> availableSeasons = getAvailableSeasons();

        String title = StrUtil.trim(dto.getTitle());
        String requestedSeason = StrUtil.blankToDefault(dto.getSeason(), currentSeason);

        boolean isSearch = StrUtil.isNotBlank(title);
        String cacheKey = "bgm:season:" + (isSearch ? "search:" + title : requestedSeason);

        // 1. 服务端 24 小时缓存保护
        if (!Boolean.TRUE.equals(dto.getRefresh())) {
            String cached = CacheUtils.get(cacheKey);
            if (StrUtil.isNotBlank(cached)) {
                try {
                    BgmSeason bgmSeason = GsonStatic.fromJson(cached, BgmSeason.class);
                    refreshExists(bgmSeason, bgmIdList);
                    return bgmSeason;
                } catch (Exception e) {
                    log.warn("解析BGM季度缓存失败: {}", e.getMessage());
                }
            }
        }

        BgmSeason bgmSeason;
        try {
            if (isSearch) {
                bgmSeason = searchByTitle(title, currentSeason, availableSeasons, bgmIdList);
            } else if ("all".equalsIgnoreCase(requestedSeason)) {
                bgmSeason = loadAllSeasons(currentSeason, availableSeasons, bgmIdList);
            } else if (requestedSeason.equals(currentSeason)) {
                bgmSeason = loadCalendarSeason(currentSeason, availableSeasons, bgmIdList);
            } else {
                bgmSeason = loadHistoricalSeason(requestedSeason, currentSeason, availableSeasons, bgmIdList);
            }

            if (bgmSeason != null && CollUtil.isNotEmpty(bgmSeason.getByWeekday())) {
                CacheUtils.put(cacheKey, GsonStatic.toJson(bgmSeason), TimeUnit.HOURS.toMillis(24));
            }
            return bgmSeason;
        } catch (Exception e) {
            log.error("从bgm.tv获取权威番剧数据失败: {}", e.getMessage(), e);
            // 降级保护：如果bgm.tv上游网络异常且有AniBT可用，进行兜底
            if (aniBTService != null) {
                log.info("尝试从 AniBTService 进行服务降级兜底...");
                try {
                    AniBT aniBT = aniBTService.list(new AniBTQueryDTO().setSeason(dto.getSeason()).setTitle(dto.getTitle()));
                    return fallbackFromAniBT(aniBT, currentSeason, availableSeasons, bgmIdList);
                } catch (Exception ex) {
                    log.error("AniBTService 降级失败: {}", ex.getMessage());
                }
            }
            throw new RuntimeException("获取bgm.tv番剧列表失败: " + e.getMessage());
        }
    }

    /**
     * 关键词搜索
     */
    private BgmSeason searchByTitle(String title, String currentSeason, List<String> availableSeasons, List<String> bgmIdList) {
        List<BgmInfo> bgmInfos = BgmUtil.search(title);
        Map<String, List<BgmSeason.Anime>> weekdayMap = new LinkedHashMap<>();

        for (BgmInfo info : bgmInfos) {
            BgmSeason.Anime anime = new BgmSeason.Anime()
                    .setAnimeId(info.getId())
                    .setBgmId(info.getId())
                    .setCover(info.getImages() != null ? StrUtil.blankToDefault(info.getImages().getLarge(), info.getImages().getCommon()) : "")
                    .setRating(info.getRating() != null && info.getRating().getScore() != null ? info.getRating().getScore() : 0.0)
                    .setScore(info.getRating() != null && info.getRating().getScore() != null ? info.getRating().getScore() : 0.0)
                    .setTitle(new BgmSeason.Title()
                            .setChinese(StrUtil.blankToDefault(info.getNameCn(), info.getName()))
                            .setPrimary(info.getName()))
                    .setPremiereDate(info.getDate() != null ? DateUtil.formatDate(info.getDate()) : "")
                    .setEpisodes(info.getEps() != null ? info.getEps() : 0)
                    .setExists(bgmIdList.contains(info.getId()));

            String label = "搜索结果";
            if (info.getDate() != null) {
                int dayOfWeek = DateUtil.dayOfWeek(info.getDate()) - 1;
                if (dayOfWeek >= 0 && dayOfWeek < WEEKDAY_CN.length) {
                    label = WEEKDAY_CN[dayOfWeek];
                }
            }
            weekdayMap.computeIfAbsent(label, k -> new ArrayList<>()).add(anime);
        }

        List<BgmSeason.ByWeekday> byWeekday = toByWeekdayList(weekdayMap);
        return new BgmSeason()
                .setCurrentSeason(currentSeason)
                .setRequestedSeason("")
                .setAvailableSeasons(availableSeasons)
                .setByWeekday(byWeekday);
    }

    /**
     * 通过 bgm.tv /calendar 获取当前每日放送季度
     */
    private BgmSeason loadCalendarSeason(String currentSeason, List<String> availableSeasons, List<String> bgmIdList) {
        String bgmApi = ConfigUtil.CONFIG.getBgmApi();
        HttpRequest req = HttpReq.get(bgmApi + "/calendar");
        BgmUtil.setToken(req);

        String body = req.thenFunction(res -> {
            HttpReq.assertStatus(res);
            return res.body();
        });

        JsonArray calendarArray = GsonStatic.fromJson(body, JsonArray.class);
        Map<String, List<BgmSeason.Anime>> weekdayMap = new LinkedHashMap<>();

        for (JsonElement el : calendarArray) {
            if (!el.isJsonObject()) continue;
            JsonObject dayObj = el.getAsJsonObject();
            JsonObject weekdayObj = dayObj.getAsJsonObject("weekday");
            String weekdayCn = weekdayObj.has("cn") ? weekdayObj.get("cn").getAsString() : "其他";

            JsonArray items = dayObj.getAsJsonArray("items");
            if (items == null) continue;

            List<BgmSeason.Anime> animeList = new ArrayList<>();
            for (JsonElement itemEl : items) {
                if (!itemEl.isJsonObject()) continue;
                JsonObject it = itemEl.getAsJsonObject();

                String id = it.has("id") ? String.valueOf(it.get("id").getAsInt()) : "";
                String name = it.has("name") && !it.get("name").isJsonNull() ? it.get("name").getAsString() : "";
                String nameCn = it.has("name_cn") && !it.get("name_cn").isJsonNull() ? it.get("name_cn").getAsString() : "";
                String airDate = it.has("air_date") && !it.get("air_date").isJsonNull() ? it.get("air_date").getAsString() : "";
                int airWeekday = it.has("air_weekday") && !it.get("air_weekday").isJsonNull() ? it.get("air_weekday").getAsInt() : 0;

                double score = 0.0;
                if (it.has("rating") && it.get("rating").isJsonObject()) {
                    JsonObject ratingObj = it.getAsJsonObject("rating");
                    if (ratingObj.has("score") && !ratingObj.get("score").isJsonNull()) {
                        score = ratingObj.get("score").getAsDouble();
                    }
                }

                String cover = "";
                if (it.has("images") && it.get("images").isJsonObject()) {
                    JsonObject imgObj = it.getAsJsonObject("images");
                    if (imgObj.has("large") && !imgObj.get("large").isJsonNull()) {
                        cover = imgObj.get("large").getAsString();
                    } else if (imgObj.has("common") && !imgObj.get("common").isJsonNull()) {
                        cover = imgObj.get("common").getAsString();
                    }
                }

                int eps = it.has("eps") && !it.get("eps").isJsonNull() ? it.get("eps").getAsInt() : 0;

                BgmSeason.Anime anime = new BgmSeason.Anime()
                        .setAnimeId(id)
                        .setBgmId(id)
                        .setTitle(new BgmSeason.Title()
                                .setChinese(StrUtil.blankToDefault(nameCn, name))
                                .setPrimary(name))
                        .setCover(cover)
                        .setRating(score)
                        .setScore(score)
                        .setPremiereDate(airDate)
                        .setAirWeekday(airWeekday)
                        .setEpisodes(eps)
                        .setExists(bgmIdList.contains(id));

                animeList.add(anime);
            }

            // 按评分倒序
            animeList.sort(Comparator.comparingDouble(BgmSeason.Anime::getRating).reversed());
            weekdayMap.put(weekdayCn, animeList);
        }

        List<BgmSeason.ByWeekday> byWeekday = toByWeekdayList(weekdayMap);
        return new BgmSeason()
                .setCurrentSeason(currentSeason)
                .setRequestedSeason(currentSeason)
                .setAvailableSeasons(availableSeasons)
                .setByWeekday(byWeekday);
    }

    /**
     * 通过 bgm.tv /v0/search/subjects 获取历史或指定季度
     */
    private BgmSeason loadHistoricalSeason(String season, String currentSeason, List<String> availableSeasons, List<String> bgmIdList) {
        String[] range = getSeasonDateRange(season);
        if (range == null) {
            return loadCalendarSeason(currentSeason, availableSeasons, bgmIdList);
        }

        String bgmApi = ConfigUtil.CONFIG.getBgmApi();
        Map<String, List<BgmSeason.Anime>> weekdayMap = new LinkedHashMap<>();
        Set<String> seenIds = new HashSet<>();

        // 分页获取前 60 部作品（3页，每页20）
        for (int offset = 0; offset < 60; offset += 20) {
            try {
                JsonObject filter = new JsonObject();
                JsonArray typeArr = new JsonArray();
                typeArr.add(2); // 动画
                filter.add("type", typeArr);

                JsonArray dateArr = new JsonArray();
                dateArr.add(">=" + range[0]);
                dateArr.add("<" + range[1]);
                filter.add("air_date", dateArr);

                JsonObject reqBody = new JsonObject();
                reqBody.addProperty("keyword", "");
                reqBody.addProperty("sort", "rank");
                reqBody.add("filter", filter);

                HttpRequest req = HttpReq.post(bgmApi + "/v0/search/subjects?limit=20&offset=" + offset, reqBody.toString());
                BgmUtil.setToken(req);

                String body = req.thenFunction(res -> {
                    if (!res.isOk()) return "{}";
                    return res.body();
                });

                JsonObject resObj = GsonStatic.fromJson(body, JsonObject.class);
                JsonArray data = resObj.getAsJsonArray("data");
                if (data == null || data.isEmpty()) {
                    break;
                }

                for (JsonElement el : data) {
                    if (!el.isJsonObject()) continue;
                    JsonObject it = el.getAsJsonObject();

                    String id = it.has("id") ? String.valueOf(it.get("id").getAsInt()) : "";
                    if (id.isEmpty() || seenIds.contains(id)) continue;
                    seenIds.add(id);

                    String name = it.has("name") && !it.get("name").isJsonNull() ? it.get("name").getAsString() : "";
                    String nameCn = it.has("name_cn") && !it.get("name_cn").isJsonNull() ? it.get("name_cn").getAsString() : "";
                    String date = it.has("date") && !it.get("date").isJsonNull() ? it.get("date").getAsString() : "";

                    double score = 0.0;
                    if (it.has("rating") && it.get("rating").isJsonObject()) {
                        JsonObject ratingObj = it.getAsJsonObject("rating");
                        if (ratingObj.has("score") && !ratingObj.get("score").isJsonNull()) {
                            score = ratingObj.get("score").getAsDouble();
                        }
                    }

                    String cover = "";
                    if (it.has("images") && it.get("images").isJsonObject()) {
                        JsonObject imgObj = it.getAsJsonObject("images");
                        if (imgObj.has("large") && !imgObj.get("large").isJsonNull()) {
                            cover = imgObj.get("large").getAsString();
                        } else if (imgObj.has("common") && !imgObj.get("common").isJsonNull()) {
                            cover = imgObj.get("common").getAsString();
                        }
                    }

                    int eps = it.has("eps") && !it.get("eps").isJsonNull() ? it.get("eps").getAsInt() : 0;

                    String weekdayLabel = "其他";
                    int airWeekday = 0;
                    if (StrUtil.isNotBlank(date)) {
                        try {
                            Date d = DateUtil.parseDate(date);
                            int dayIdx = DateUtil.dayOfWeek(d) - 1;
                            if (dayIdx >= 0 && dayIdx < WEEKDAY_CN.length) {
                                weekdayLabel = WEEKDAY_CN[dayIdx];
                                airWeekday = dayIdx == 0 ? 7 : dayIdx;
                            }
                        } catch (Exception ignored) {}
                    }

                    BgmSeason.Anime anime = new BgmSeason.Anime()
                            .setAnimeId(id)
                            .setBgmId(id)
                            .setTitle(new BgmSeason.Title()
                                    .setChinese(StrUtil.blankToDefault(nameCn, name))
                                    .setPrimary(name))
                            .setCover(cover)
                            .setRating(score)
                            .setScore(score)
                            .setPremiereDate(date)
                            .setAirWeekday(airWeekday)
                            .setEpisodes(eps)
                            .setExists(bgmIdList.contains(id));

                    weekdayMap.computeIfAbsent(weekdayLabel, k -> new ArrayList<>()).add(anime);
                }
            } catch (Exception e) {
                log.warn("获取历史季度 {} 分页 offset={} 失败: {}", season, offset, e.getMessage());
                break;
            }
        }

        for (List<BgmSeason.Anime> list : weekdayMap.values()) {
            list.sort(Comparator.comparingDouble(BgmSeason.Anime::getRating).reversed());
        }

        List<BgmSeason.ByWeekday> byWeekday = toByWeekdayList(weekdayMap);
        return new BgmSeason()
                .setCurrentSeason(currentSeason)
                .setRequestedSeason(season)
                .setAvailableSeasons(availableSeasons)
                .setByWeekday(byWeekday);
    }

    /**
     * 全部季度数据合并
     */
    private BgmSeason loadAllSeasons(String currentSeason, List<String> availableSeasons, List<String> bgmIdList) {
        Map<String, List<BgmSeason.Anime>> weekdayMap = new LinkedHashMap<>();
        Set<String> seenIds = new HashSet<>();

        for (String s : availableSeasons) {
            try {
                BgmSeason seasonData;
                if (s.equals(currentSeason)) {
                    seasonData = loadCalendarSeason(currentSeason, availableSeasons, bgmIdList);
                } else {
                    seasonData = loadHistoricalSeason(s, currentSeason, availableSeasons, bgmIdList);
                }
                if (seasonData != null && seasonData.getByWeekday() != null) {
                    for (BgmSeason.ByWeekday day : seasonData.getByWeekday()) {
                        String label = day.getWeekdayLabel();
                        for (BgmSeason.Anime a : (day.getAnimes() != null ? day.getAnimes() : Collections.<BgmSeason.Anime>emptyList())) {
                            if (seenIds.add(a.getBgmId())) {
                                weekdayMap.computeIfAbsent(label, k -> new ArrayList<>()).add(a);
                            }
                        }
                    }
                }
            } catch (Exception e) {
                log.warn("合并季度 {} 数据失败: {}", s, e.getMessage());
            }
        }

        for (List<BgmSeason.Anime> list : weekdayMap.values()) {
            list.sort(Comparator.comparingDouble(BgmSeason.Anime::getRating).reversed());
        }

        List<BgmSeason.ByWeekday> byWeekday = toByWeekdayList(weekdayMap);
        return new BgmSeason()
                .setCurrentSeason(currentSeason)
                .setRequestedSeason("all")
                .setAvailableSeasons(availableSeasons)
                .setByWeekday(byWeekday);
    }

    /**
     * 降级转换：从 AniBT 实体转为 BgmSeason 实体
     */
    private BgmSeason fallbackFromAniBT(AniBT aniBT, String currentSeason, List<String> availableSeasons, List<String> bgmIdList) {
        if (aniBT == null) return null;
        List<BgmSeason.ByWeekday> byWeekdayList = new ArrayList<>();
        if (aniBT.getByWeekday() != null) {
            for (AniBT.ByWeekday bw : aniBT.getByWeekday()) {
                List<BgmSeason.Anime> animes = new ArrayList<>();
                if (bw.getAnimes() != null) {
                    for (AniBT.Anime a : bw.getAnimes()) {
                        animes.add(new BgmSeason.Anime()
                                .setAnimeId(a.getAnimeId())
                                .setBgmId(a.getBgmId())
                                .setCover(a.getCover())
                                .setRating(a.getRating())
                                .setScore(a.getRating())
                                .setTitle(new BgmSeason.Title()
                                        .setChinese(a.getTitle() != null ? a.getTitle().getChinese() : "")
                                        .setPrimary(a.getTitle() != null ? a.getTitle().getPrimary() : ""))
                                .setPremiereDate(a.getPremiereDate())
                                .setAiringAt(a.getAiringAt())
                                .setEpisodes(a.getEpisodes())
                                .setScheduleStatus(a.getScheduleStatus())
                                .setExists(bgmIdList.contains(a.getBgmId())));
                    }
                }
                byWeekdayList.add(new BgmSeason.ByWeekday()
                        .setWeekday(bw.getWeekday())
                        .setWeekdayLabel(bw.getWeekdayLabel())
                        .setAnimes(animes));
            }
        }

        return new BgmSeason()
                .setCurrentSeason(currentSeason)
                .setRequestedSeason(normalizeSeason(aniBT.getRequestedSeason()))
                .setAvailableSeasons(availableSeasons)
                .setByWeekday(byWeekdayList);
    }

    private String normalizeSeason(String s) {
        if (StrUtil.isBlank(s)) return getCurrentSeason();
        if (s.matches("^\\d{4}(01|04|07|10)$")) return s;
        if (s.length() >= 5) {
            try {
                int year = Integer.parseInt(s.substring(0, 4));
                String sub = s.substring(4);
                if (sub.contains("冬") || sub.contains("01") || sub.contains("1月")) return year + "01";
                if (sub.contains("春") || sub.contains("04") || sub.contains("4月")) return year + "04";
                if (sub.contains("夏") || sub.contains("07") || sub.contains("7月")) return year + "07";
                if (sub.contains("秋") || sub.contains("10") || sub.contains("10月")) return year + "10";
            } catch (Exception ignored) {}
        }
        return s;
    }

    private List<BgmSeason.ByWeekday> toByWeekdayList(Map<String, List<BgmSeason.Anime>> weekdayMap) {
        List<BgmSeason.ByWeekday> result = new ArrayList<>();
        WeekComparator weekComparator = new WeekComparator();

        List<Map.Entry<String, List<BgmSeason.Anime>>> entries = new ArrayList<>(weekdayMap.entrySet());
        entries.sort((a, b) -> weekComparator.compare(a.getKey(), b.getKey()));

        for (Map.Entry<String, List<BgmSeason.Anime>> entry : entries) {
            String label = entry.getKey();
            int weekday = 8;
            for (int i = 0; i < WEEKDAY_CN.length; i++) {
                if (WEEKDAY_CN[i].equals(label)) {
                    weekday = (i == 0 ? 7 : i);
                    break;
                }
            }
            result.add(new BgmSeason.ByWeekday()
                    .setWeekday(weekday)
                    .setWeekdayLabel(label)
                    .setAnimes(entry.getValue()));
        }
        return result;
    }

    private void refreshExists(BgmSeason bgmSeason, List<String> bgmIdList) {
        if (bgmSeason == null || bgmSeason.getByWeekday() == null) return;
        for (BgmSeason.ByWeekday day : bgmSeason.getByWeekday()) {
            if (day.getAnimes() == null) continue;
            for (BgmSeason.Anime a : day.getAnimes()) {
                a.setExists(bgmIdList.contains(a.getBgmId()));
            }
        }
    }
}
