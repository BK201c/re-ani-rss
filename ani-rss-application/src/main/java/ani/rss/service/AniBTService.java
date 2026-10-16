package ani.rss.service;

import ani.rss.commons.FileUtils;
import ani.rss.commons.GroupRegexUtils;
import ani.rss.commons.GsonStatic;
import ani.rss.cache.CacheUtils;
import ani.rss.comparator.WeekComparator;
import ani.rss.entity.Ani;
import ani.rss.entity.AniBT;
import ani.rss.entity.GroupRegex;
import ani.rss.entity.dto.AniBTQueryDTO;
import ani.rss.util.basic.HttpReq;
import ani.rss.util.other.AniUtil;
import ani.rss.util.other.BgmUtil;
import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.StrUtil;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import org.springframework.stereotype.Service;

import java.util.Comparator;
import java.util.List;
import java.util.concurrent.TimeUnit;

@Service
public class AniBTService {
    private static final String HOST = "https://anibt.net";

    /** 独立获取排期，不按 RSS 资源数量过滤尚未开播的作品。 */
    public AniBT schedules(String season, String title, boolean refresh) {
        String key = "anibt:schedules:" + season + ":" + title;
        String cached = refresh ? null : CacheUtils.get(key);
        if (cached != null) {
            return GsonStatic.fromJson(cached, AniBT.class);
        }
        AniBT schedules = HttpReq.get(HOST + "/api/seasons/anime")
                .timeout(5000)
                .form("season", season)
                .form("query", title)
                .thenFunction(res -> {
                    HttpReq.assertStatus(res);
                    JsonObject response = GsonStatic.fromJson(res.body(), JsonObject.class);
                    return GsonStatic.fromJson(response.getAsJsonObject("data"), AniBT.class);
                });
        CacheUtils.put(key, GsonStatic.toJson(schedules), TimeUnit.MINUTES.toMillis(10));
        return schedules;
    }

    public AniBT list(AniBTQueryDTO dto) {
        String title = dto.getTitle();

        List<String> bgmIdList = AniUtil.ANI_LIST
                .stream()
                .map(Ani::getBgmUrl)
                .filter(StrUtil::isNotBlank)
                .map(BgmUtil::getSubjectId)
                .distinct()
                .toList();

        String bgmUrl = dto.getBgmUrl();
        String season = dto.getSeason();

        String bgmId = "";
        if (StrUtil.isNotBlank(bgmUrl)) {
            bgmId = BgmUtil.getSubjectId(bgmUrl);
        }

        if (StrUtil.isNotBlank(title)) {
            season = "";
            bgmId = "";
        }

        AniBT aniBT = HttpReq.get(HOST + "/api/seasons/anime")
                .form("season", season)
                .form("bgmId", bgmId)
                .form("query", title)
                .thenFunction(res -> {
                    HttpReq.assertStatus(res);
                    JsonObject jsonObject = GsonStatic.fromJson(res.body(), JsonObject.class);
                    JsonObject data = jsonObject.getAsJsonObject("data");
                    return GsonStatic.fromJson(data, AniBT.class);
                });

        List<AniBT.ByWeekday> byWeekday = aniBT.getByWeekday();

        for (AniBT.ByWeekday weekday : byWeekday) {
            List<AniBT.Anime> animeList = weekday.getAnimes();
            animeList = animeList.stream()
                    .filter(anime -> {
                        if (StrUtil.isBlank(title)) {
                            return anime.getRssReleaseCount() > 0;
                        }
                        return true;
                    })
                    .sorted(Comparator.comparingDouble(AniBT.Anime::getRating).reversed())
                    .peek(anime -> {
                        boolean exists = bgmIdList.contains(anime.getBgmId());
                        anime.setExists(exists);
                    })
                    .toList();
            weekday.setAnimes(animeList);
        }

        WeekComparator weekComparator = new WeekComparator();
        byWeekday = byWeekday.stream()
                .filter(weekday -> CollUtil.isNotEmpty(weekday.getAnimes()))
                .sorted((a, b) ->
                        weekComparator.compare(a.getWeekdayLabel(), b.getWeekdayLabel())
                )
                .toList();
        aniBT.setByWeekday(byWeekday);

        return aniBT;
    }

    public List<AniBT.Group> getGroups(String bgmId) {
        return HttpReq.get(HOST + "/api/anime/groups")
                .form("bgmId", bgmId)
                .thenFunction(res -> {
                    HttpReq.assertStatus(res);
                    JsonObject jsonObject = GsonStatic.fromJson(res.body(), JsonObject.class);
                    JsonArray groups = jsonObject.getAsJsonObject("data")
                            .getAsJsonArray("groups");
                    List<AniBT.Group> groupList = GsonStatic.fromJsonList(groups, AniBT.Group.class);
                    for (AniBT.Group group : groupList) {
                        String slug = group.getSlug();
                        String rss = "https://anibt.net/rss/anime.xml?bgmId={}&groupSlug={}";
                        rss = StrUtil.format(rss, bgmId, slug);
                        group.setRss(rss);

                        List<AniBT.Item> items = group.getItems();
                        GroupRegex groupRegx = GroupRegexUtils.toGroupRegx(items, AniBT.Item::getTitle);

                        for (AniBT.Item item : items) {
                            Long size = item.getSize();
                            String formatSize = FileUtils.formatSize(size, true);
                            item.setFormatSize(formatSize);
                        }

                        group.setBgmId(bgmId)
                                .setGroupRegex(groupRegx);
                    }
                    return groupList;
                });
    }
}
