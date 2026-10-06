<template>
  <div class="rss-config-form full-width">
    <section class="settings-section">
      <div class="settings-section-heading">
        <h3>重命名设置</h3>
        <p class="settings-section-desc">番剧文件自动整理与重命名格式规范</p>
      </div>
      <RenameView :config="props.config"/>
    </section>

    <section class="settings-section">
      <div class="settings-section-heading">
        <h3>刮削设置</h3>
        <p class="settings-section-desc">TMDB 与 Bangumi 元数据获取与海报刮削</p>
      </div>
      <ScrapeView :config="props.config"/>
    </section>

    <section class="settings-section">
      <div class="settings-section-heading">
        <h3>RSS 抓取设置</h3>
        <p class="settings-section-desc">订阅更新频率、抓取重试与过滤规则行为</p>
      </div>
      <RssView :config="props.config"/>
    </section>

    <section class="settings-section">
      <div class="settings-section-heading">
        <h3>Trackers 服务器</h3>
        <p class="settings-section-desc">自动注入公共 Tracker 地址以加速 BT 下载</p>
      </div>
      <TrackersView :config="props.config"/>
    </section>
  </div>
</template>

<script setup>
import RenameView from "@/view/config/basic/RenameView.vue";
import ScrapeView from "@/view/config/basic/ScrapeView.vue";
import RssView from "@/view/config/basic/RssView.vue";
import TrackersView from "@/view/config/basic/TrackersView.vue";

const props = defineProps(['config'])
</script>

<style scoped>
.settings-section + .settings-section {
  margin-top: 28px;
  padding-top: 24px;
  border-top: 1px solid var(--el-border-color-extra-light);
}

.settings-section-heading {
  margin-bottom: 18px;
}

.settings-section-heading h3 {
  margin: 0;
  color: var(--el-text-color-primary);
  font-size: 15px;
  font-weight: 650;
  line-height: 1.4;
}

.settings-section-desc {
  margin: 4px 0 0;
  color: var(--el-text-color-secondary);
  font-size: 13px;
  line-height: 1.4;
}

@media (max-width: 700px) {
  .settings-section + .settings-section {
    margin-top: 22px;
    padding-top: 20px;
  }

  .settings-section-heading {
    margin-bottom: 15px;
  }
}
</style>
