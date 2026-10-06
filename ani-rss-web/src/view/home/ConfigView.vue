<template>
  <div v-loading="loading" class="config-page app-page-layout">
    <PageHeaderView title="设置" :subtitle="activeItem?.description || ''">
      <template #actions>
        <el-button
            class="auto-button"
            icon="Check"
            :disabled="loading"
            :loading="configButtonLoading"
            type="primary"
            @click="saveConfig">
          保存配置
        </el-button>
      </template>
    </PageHeaderView>
    <div class="config-layout app-page-content">
      <!-- 左侧二级垂直导航 -->
      <aside class="config-sidebar">
        <el-scrollbar class="config-sidebar-scroll">
          <nav class="config-nav-list">
            <button
                v-for="item in navItems"
                :key="item.name"
                type="button"
                class="config-nav-item"
                :class="{ 'is-active': activeName === item.name }"
                @click="activeName = item.name">
              <el-icon class="config-nav-icon">
                <component :is="item.icon"/>
              </el-icon>
              <div class="config-nav-text">
                <span class="config-nav-title">{{ item.label }}</span>
                <span class="config-nav-desc">{{ item.shortDesc }}</span>
              </div>
            </button>
          </nav>
        </el-scrollbar>
      </aside>

      <!-- 右侧平铺内容区 -->
      <main class="config-main">
        <el-scrollbar class="config-content-scroll">
          <div class="config-panel-wrapper">
            <div class="config-panel-header">
              <h2>{{ activeItem?.label }}</h2>
              <p>{{ activeItem?.description }}</p>
            </div>
            <div class="config-panel-body">
              <DownloadView v-if="activeName === 'download'" v-model:config="config"/>
              <BasicView v-else-if="activeName === 'basic'" v-model:config="config"/>
              <RssConfigView v-else-if="activeName === 'rss'" v-model:config="config"/>
              <ExcludeView
                  v-else-if="activeName === 'exclude'"
                  v-model:exclude="config.exclude"
                  :show-text="true"/>
              <ProxyView v-else-if="activeName === 'proxy'" v-model:config="config"/>
              <Security v-else-if="activeName === 'security'" :config="config"/>
              <NotificationView v-else-if="activeName === 'notification'" v-model:config="config"/>
              <AfdianView v-else-if="activeName === 'afdian'" :config="config"/>
              <AboutView v-else-if="activeName === 'about'" :config="config"/>
            </div>
          </div>
        </el-scrollbar>
      </main>
    </div>
  </div>
</template>

<script setup>
import {computed, onMounted, ref} from "vue";
import {ElMessage} from "element-plus";
import {md5} from "js-md5";
import {
  Bell,
  Check,
  Connection,
  Download,
  Filter,
  InfoFilled,
  Lock,
  Present,
  Promotion,
  Setting
} from "@element-plus/icons-vue";

import DownloadView from "@/view/config/DownloadView.vue";
import BasicView from "@/view/config/BasicView.vue";
import RssConfigView from "@/view/config/RssConfigView.vue";
import ExcludeView from "@/view/config/ExcludeView.vue";
import ProxyView from "@/view/config/ProxyView.vue";
import Security from "@/view/config/SecurityView.vue";
import NotificationView from "@/view/config/NotificationView.vue";
import AfdianView from "@/view/config/AfdianView.vue";
import AboutView from "@/view/config/AboutView.vue";
import PageHeaderView from "@/view/custom/PageHeaderView.vue";
import {configData} from "@/js/config.js";
import * as http from "@/js/http.js";

const configButtonLoading = ref(false)
const loading = ref(true)
const config = ref(configData)
const activeName = ref('download')

const navItems = [
  {
    name: 'download',
    label: '下载设置',
    shortDesc: '下载器、目录与任务行为',
    description: '配置下载器连接、保存与清理路径、任务控制及高级选项',
    icon: Download
  },
  {
    name: 'basic',
    label: '基本设置',
    shortDesc: '界面、默认订阅与账号备份',
    description: '页面主题显示偏好、添加订阅默认配置、Bangumi 账号关联与数据备份',
    icon: Setting
  },
  {
    name: 'rss',
    label: 'RSS 与刮削',
    shortDesc: '重命名、TMDB与Trackers',
    description: '配置媒体库重命名规则、TMDB 与 Bangumi 刮削、RSS 周期及 Tracker 注入',
    icon: Connection
  },
  {
    name: 'exclude',
    label: '全局排除',
    shortDesc: '统一排除不需要的资源',
    description: '配置全局资源过滤正则与关键字，过滤掉非期望语言、画质或格式',
    icon: Filter
  },
  {
    name: 'proxy',
    label: '网络代理',
    shortDesc: '代理服务器与连接测试',
    description: '配置 HTTP/Socks5 代理服务以加速番剧源、TMDB 及元数据访问',
    icon: Promotion
  },
  {
    name: 'security',
    label: '安全设置',
    shortDesc: '账号密码与网络安全',
    description: '修改 WebUI 访问凭据、IP 白名单、ApiKey 及反向代理安全策略',
    icon: Lock
  },
  {
    name: 'notification',
    label: '通知推送',
    shortDesc: '通知模板与消息通道',
    description: '配置下载与更新通知模板，支持 Bark、Telegram、邮件及 Webhook',
    icon: Bell
  },
  {
    name: 'afdian',
    label: '支持项目',
    shortDesc: '爱发电赞助与维护支持',
    description: '支持开源项目持续维护与功能迭代',
    icon: Present
  },
  {
    name: 'about',
    label: '关于系统',
    shortDesc: '版本信息、更新与文档',
    description: '检查系统最新版本、重启/关闭服务以及访问项目文档与社区',
    icon: InfoFilled
  }
]

const activeItem = computed(() => navItems.find(item => item.name === activeName.value))

const loadConfig = () => {
  loading.value = true
  http.config()
      .then(res => {
        config.value = res.data
      })
      .finally(() => {
        loading.value = false
      })
}

const saveConfig = () => {
  configButtonLoading.value = true
  let my_config = JSON.parse(JSON.stringify(config.value))

  let username = my_config.login.username.trim()
  let password = my_config.login.password.trim()

  my_config.login.username = username
  if (password) {
    my_config.login.password = md5(password)
  }

  http.setConfig(my_config)
      .then(res => {
        ElMessage.success(res.message)
        window.$reLoadList?.()
      })
      .finally(() => {
        configButtonLoading.value = false
      })
}

onMounted(() => {
  loadConfig()
})
</script>

<style scoped>
.config-page {
  height: 100%;
  display: flex;
  flex-direction: column;
  overflow: hidden;
}

.config-layout {
  flex: 1;
  min-height: 0;
  display: flex;
  padding: 0;
  background: var(--el-bg-color-page);
}

/* 左侧垂直二级导航 */
.config-sidebar {
  width: 220px;
  flex-shrink: 0;
  border-right: 1px solid var(--el-border-color-light);
  background: var(--el-bg-color);
  display: flex;
  flex-direction: column;
}

.config-sidebar-scroll {
  height: 100%;
}

.config-nav-list {
  display: flex;
  flex-direction: column;
  gap: 4px;
  padding: 12px 10px;
}

.config-nav-item {
  display: flex;
  align-items: center;
  gap: 12px;
  width: 100%;
  padding: 10px 14px;
  border: 1px solid transparent;
  border-radius: 8px;
  background: none;
  cursor: pointer;
  text-align: left;
  transition: all 0.2s ease;
  color: var(--el-text-color-regular);
}

.config-nav-item:hover {
  background: var(--el-fill-color-light);
  color: var(--el-text-color-primary);
}

.config-nav-item.is-active {
  background: var(--el-color-primary-light-9);
  border-color: var(--el-color-primary-light-7);
  color: var(--el-color-primary);
}

.config-nav-icon {
  font-size: 18px;
  flex-shrink: 0;
}

.config-nav-text {
  min-width: 0;
  display: flex;
  flex-direction: column;
}

.config-nav-title {
  font-size: 14px;
  font-weight: 600;
  line-height: 1.3;
}

.config-nav-desc {
  font-size: 11px;
  color: var(--el-text-color-secondary);
  margin-top: 2px;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.config-nav-item.is-active .config-nav-desc {
  color: var(--el-color-primary-light-3);
}

/* 右侧主内容区域 */
.config-main {
  flex: 1;
  min-width: 0;
  height: 100%;
  background: var(--el-bg-color);
}

.config-content-scroll {
  height: 100%;
}

.config-panel-wrapper {
  max-width: 960px;
  margin: 0 auto;
  padding: 24px 32px 48px;
}

.config-panel-header {
  margin-bottom: 24px;
  padding-bottom: 16px;
  border-bottom: 1px solid var(--el-border-color-lighter);
}

.config-panel-header h2 {
  margin: 0 0 6px;
  font-size: 18px;
  font-weight: 650;
  color: var(--el-text-color-primary);
}

.config-panel-header p {
  margin: 0;
  font-size: 13px;
  color: var(--el-text-color-secondary);
}

.config-panel-body {
  min-height: 200px;
}

/* 响应式适配 */
@media (max-width: 800px) {
  .config-layout {
    flex-direction: column;
  }

  .config-sidebar {
    width: 100%;
    height: auto;
    border-right: none;
    border-bottom: 1px solid var(--el-border-color-light);
  }

  .config-nav-list {
    flex-direction: row;
    overflow-x: auto;
    padding: 8px;
    gap: 6px;
  }

  .config-nav-item {
    width: auto;
    flex-shrink: 0;
    padding: 6px 12px;
  }

  .config-nav-desc {
    display: none;
  }

  .config-panel-wrapper {
    padding: 16px 14px 32px;
  }
}
</style>
