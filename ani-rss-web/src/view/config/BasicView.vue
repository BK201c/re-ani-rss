<template>
  <div class="basic-config-form full-width">
    <section id="basic-page" class="settings-section">
      <div class="settings-section-heading">
        <h3>页面显示</h3>
        <p class="settings-section-desc">界面主题、色彩、布局与视图偏好</p>
      </div>
      <PageView :config="props.config"/>
    </section>

    <section id="basic-defaults" class="settings-section">
      <div class="settings-section-heading">
        <h3>添加订阅默认值</h3>
        <p class="settings-section-desc">新增番剧订阅时的默认下载与行为配置</p>
      </div>
      <AddView :config="props.config"/>
    </section>

    <section id="basic-bangumi" class="settings-section">
      <div class="settings-section-heading">
        <h3>Bangumi 账号</h3>
        <p class="settings-section-desc">绑定 Bangumi 账号以同步收藏与评分信息</p>
      </div>
      <BangumiView :config="props.config"/>
    </section>

    <section id="basic-backup" class="settings-section">
      <div class="settings-section-heading">
        <h3>备份与恢复</h3>
        <p class="settings-section-desc">导出或导入系统配置与订阅数据备份</p>
      </div>
      <BackupView :config="props.config"/>
    </section>

    <section id="basic-other" class="settings-section">
      <div class="settings-section-heading">
        <h3>其他杂项</h3>
        <p class="settings-section-desc">系统其他杂项与高级开关</p>
      </div>
      <OtherView :config="props.config"/>
    </section>
  </div>
</template>

<script setup>
import PageView from "@/view/config/basic/PageView.vue";
import AddView from "@/view/config/basic/AddView.vue";
import BangumiView from "@/view/config/basic/BangumiView.vue";
import BackupView from "./basic/BackupView.vue";
import OtherView from "@/view/config/basic/OtherView.vue";

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
