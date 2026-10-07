package ani.rss.service;

import ani.rss.cache.CacheUtils;
import ani.rss.commons.FileUtils;
import ani.rss.commons.GroupRegexUtils;
import ani.rss.commons.GsonStatic;
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

        boolean isSeasonQuery = StrUtil.isBlank(title) && StrUtil.isBlank(bgmUrl);
        String cacheKey = "anibt:season:raw:" + StrUtil.blankToDefault(season, "current");

        String rawData = null;
        if (isSeasonQuery && !Boolean.TRUE.equals(dto.getRefresh())) {
            rawData = CacheUtils.get(cacheKey);
        }

        if (rawData == null) {
            final String finalSeason = season;
            final String finalBgmId = bgmId;
            rawData = HttpReq.get(HOST + "/api/seasons/anime")
                    .form("season", finalSeason)
                    .form("bgmId", finalBgmId)
                    .form("query", title)
                    .thenFunction(res -> {
                        HttpReq.assertStatus(res);
                        JsonObject jsonObject = GsonStatic.fromJson(res.body(), JsonObject.class);
                        JsonObject data = jsonObject.getAsJsonObject("data");
                        return data.toString();
                    });

            if (isSeasonQuery && StrUtil.isNotBlank(rawData)) {
                CacheUtils.put(cacheKey, rawData, TimeUnit.HOURS.toMillis(24));
            }
        }

        AniBT aniBT = GsonStatic.fromJson(rawData, AniBT.class);

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
