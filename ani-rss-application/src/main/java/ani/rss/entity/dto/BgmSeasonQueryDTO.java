package ani.rss.entity.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.experimental.Accessors;

import java.io.Serializable;

@Data
@Accessors(chain = true)
@Schema(description = "BGM权威季度番剧查询参数")
public class BgmSeasonQueryDTO implements Serializable {
    @Schema(description = "季度编号，按年月标准格式如 202610、202607 或 all，留空则默认当前季度")
    private String season;
    @Schema(description = "搜索标题关键词")
    private String title;
    @Schema(description = "是否强制刷新服务端缓存")
    private Boolean refresh;
}
