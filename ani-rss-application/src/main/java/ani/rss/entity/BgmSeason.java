package ani.rss.entity;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.experimental.Accessors;

import java.io.Serializable;
import java.util.List;

@Data
@Accessors(chain = true)
@Schema(description = "BGM权威季度番剧列表")
public class BgmSeason implements Serializable {
    @Schema(description = "当前季度")
    private String currentSeason;
    @Schema(description = "请求的季度")
    private String requestedSeason;
    @Schema(description = "可用的季度列表")
    private List<String> availableSeasons;
    @Schema(description = "按星期分类的番剧列表")
    private List<ByWeekday> byWeekday;

    @Data
    @Accessors(chain = true)
    @Schema(description = "按星期分类")
    public static class ByWeekday implements Serializable {
        @Schema(description = "番剧列表")
        private List<Anime> animes;
        @Schema(description = "星期几 (1-7)")
        private Integer weekday;
        @Schema(description = "星期标签")
        private String weekdayLabel;
    }

    @Data
    @Accessors(chain = true)
    @Schema(description = "番剧信息")
    public static class Anime implements Serializable {
        @Schema(description = "番剧ID")
        private String animeId;
        @Schema(description = "BGM ID")
        private String bgmId;
        @Schema(description = "封面图片")
        private String cover;
        @Schema(description = "评分")
        private Double rating;
        @Schema(description = "评分")
        private Double score;
        @Schema(description = "标题")
        private Title title;
        @Schema(description = "是否已订阅")
        private Boolean exists;
        @Schema(description = "首播日期")
        private String premiereDate;
        @Schema(description = "播出时间戳（秒）")
        private Long airingAt;
        @Schema(description = "总集数")
        private Integer episodes;
        @Schema(description = "排期状态")
        private String scheduleStatus;
        @Schema(description = "放送星期几")
        private Integer airWeekday;
    }

    @Data
    @Accessors(chain = true)
    @Schema(description = "标题信息")
    public static class Title implements Serializable {
        @Schema(description = "中文标题")
        private String chinese;
        @Schema(description = "主要标题/原名")
        private String primary;
    }
}
