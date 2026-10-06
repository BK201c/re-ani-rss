<template>
  <div v-loading="loading" class="config-page app-page-layout">
    <PageHeaderView title="设置" :subtitle="activeTabItem?.description || ''">
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

    <!-- 1. 顶部 Tab 导航（红框：9 个大模块切换） -->
    <div class="config-top-tabs-bar">
      <el-scrollbar class="top-tabs-scrollbar">
        <div class="top-tabs-track">
          <button
              v-for="tab in navTabs"
              :key="tab.name"
              type="button"
              class="top-tab-btn"
              :class="{ 'is-active': activeName === tab.name }"
              @click="switchTab(tab.name)">
            <el-icon class="top-tab-icon">
              <component :is="tab.icon"/>
            </el-icon>
            <span class="top-tab-text">{{ tab.label }}</span>
          </button>
        </div>
      </el-scrollbar>
    </div>

    <!-- 2. 主体工作区：全屏平铺设置区 + 浮动小节导航悬浮窗（高度随内容自适应，不垂直拉满） -->
    <div class="config-workspace">
      <!-- 主平铺设置内容区 -->
      <main class="config-main">
        <el-scrollbar ref="contentScrollbarRef" class="config-content-scroll" @scroll="handleScroll">
          <div class="config-panel-wrapper">
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

      <!-- 悬浮窗：单个 Tab 分组内的小节目录导航 -->
      <div
          v-if="currentSections.length > 1"
          class="config-float-widget"
          :class="[
            `dock-${dockSide}`,
            { 'is-collapsed': isNavCollapsed }
          ]"
      >
        <!-- 收起状态：紧凑微型胶囊 -->
        <button
            v-if="isNavCollapsed"
            type="button"
            class="float-collapsed-capsule"
            title="展开小节目录"
            @click="isNavCollapsed = false"
        >
          <el-icon class="capsule-icon"><List /></el-icon>
          <span class="capsule-label">目录</span>
          <span class="capsule-badge">{{ currentSections.length }}</span>
        </button>

        <!-- 展开状态：悬浮窗卡片 -->
        <div v-else class="float-window-card">
          <div class="float-window-header">
            <div class="header-left">
              <el-icon class="header-icon"><List /></el-icon>
              <span class="header-title">{{ activeTabItem?.label }}</span>
              <span class="header-badge">{{ currentSections.length }}</span>
            </div>
            <div class="header-actions">
              <button
                  type="button"
                  class="win-tool-btn"
                  :title="dockSide === 'right' ? '停靠到左侧' : '停靠到右侧'"
                  @click="toggleDockSide"
              >
                <el-icon>
                  <component :is="dockSide === 'right' ? ArrowLeft : ArrowRight" />
                </el-icon>
              </button>
              <button
                  type="button"
                  class="win-tool-btn"
                  title="收起悬浮窗"
                  @click="isNavCollapsed = true"
              >
                <el-icon><Minus /></el-icon>
              </button>
            </div>
          </div>

          <div class="float-window-body">
            <nav class="float-nav-list">
              <button
                  v-for="sec in currentSections"
                  :key="sec.id"
                  type="button"
                  class="float-nav-item"
                  :class="{ 'is-active': activeSectionId === sec.id }"
                  @click="scrollToSection(sec.id)"
              >
                <span class="nav-dot"></span>
                <div class="nav-content">
                  <span class="nav-title">{{ sec.title }}</span>
                  <span v-if="sec.desc" class="nav-desc">{{ sec.desc }}</span>
                </div>
              </button>
            </nav>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import {computed, nextTick, onMounted, ref} from "vue";
import {ElMessage} from "element-plus";
import {md5} from "js-md5";
import {
  ArrowLeft,
  ArrowRight,
  Bell,
  Check,
  Connection,
  Download,
  Filter,
  InfoFilled,
  List,
  Lock,
  Minus,
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
const activeSectionId = ref('')
const contentScrollbarRef = ref()

// 悬浮窗状态（停靠侧、收起状态）
const dockSide = ref('right')
const isNavCollapsed = ref(false)

const toggleDockSide = () => {
  dockSide.value = dockSide.value === 'right' ? 'left' : 'right'
}

// 1. 顶部 Tab（红框：9 个大模块切换）
const navTabs = [
  {
    name: 'download',
    label: '下载设置',
    description: '配置下载器连接、保存与清理路径、任务控制及高级选项',
    icon: Download
  },
  {
    name: 'basic',
    label: '基本设置',
    description: '页面主题显示偏好、添加订阅默认配置、Bangumi 账号关联与数据备份',
    icon: Setting
  },
  {
    name: 'rss',
    label: 'RSS 与刮削',
    description: '配置媒体库重命名规则、TMDB 与 Bangumi 刮削、RSS 周期及 Tracker 注入',
    icon: Connection
  },
  {
    name: 'exclude',
    label: '全局排除',
    description: '配置全局资源过滤正则与关键字，过滤掉非期望语言、画质或格式',
    icon: Filter
  },
  {
    name: 'proxy',
    label: '网络代理',
    description: '配置 HTTP/SOCKS5 代理服务以加速番剧源、TMDB 及元数据访问',
    icon: Promotion
  },
  {
    name: 'security',
    label: '安全设置',
    description: '修改 WebUI 访问凭据、IP 白名单、ApiKey 及反向代理安全策略',
    icon: Lock
  },
  {
    name: 'notification',
    label: '通知推送',
    description: '配置下载与更新通知模板，支持 Bark、Telegram、邮件及 Webhook',
    icon: Bell
  },
  {
    name: 'afdian',
    label: '支持项目',
    description: '支持开源项目持续维护与功能迭代',
    icon: Present
  },
  {
    name: 'about',
    label: '关于系统',
    description: '检查系统最新版本、重启/关闭服务以及访问项目文档与社区',
    icon: InfoFilled
  }
]

// 2. 单个分组内部的导航目录字典（蓝框：各个大模块内的子小节）
const tabSectionsMap = {
  download: [
    { id: 'download-client', title: '下载器连接', desc: '客户端工具与网络参数' },
    { id: 'download-storage', title: '保存与清理', desc: '保存路径与自动清理规则' },
    { id: 'download-tasks', title: '任务控制', desc: '并发、重试与优先级过滤' },
    {
      id: 'download-qb-advanced',
      title: 'qBittorrent 高级设置',
      desc: 'Tracker 与做种高级控制',
      condition: () => config.value.downloadToolType === 'qBittorrent'
    }
  ],
  basic: [
    { id: 'basic-page', title: '页面显示', desc: '主题配色与全局视图偏好' },
    { id: 'basic-defaults', title: '添加订阅默认值', desc: '新建订阅时的默认规则' },
    { id: 'basic-bangumi', title: 'Bangumi 账号', desc: '授权绑定与数据同步' },
    { id: 'basic-backup', title: '备份与恢复', desc: '配置与数据导入导出' },
    { id: 'basic-other', title: '其他杂项', desc: '系统高级杂项开关' }
  ],
  rss: [
    { id: 'rss-rename', title: '重命名设置', desc: '自动整理与重命名格式规范' },
    { id: 'rss-scrape', title: '刮削设置', desc: 'TMDB 与 Bangumi 元数据获取' },
    { id: 'rss-fetch', title: 'RSS 抓取设置', desc: '更新频率与过滤规则行为' },
    { id: 'rss-trackers', title: 'Trackers 服务器', desc: '公共 Tracker 地址注入加速' }
  ],
  exclude: [
    { id: 'exclude-rules', title: '全局排除规则', desc: '统一排除不需要的字幕组与格式' }
  ],
  proxy: [
    { id: 'proxy-config', title: '代理服务器配置', desc: 'HTTP / SOCKS5 代理设置' },
    { id: 'proxy-test', title: '代理连通性测试', desc: '目标源站连通性与延迟测试' }
  ],
  security: [
    { id: 'security-auth', title: '账号与会话安全', desc: '登录凭据与有效期设置' },
    { id: 'security-network', title: 'IP 白名单与反代', desc: '访问白名单与受信任 IP' }
  ],
  notification: [
    { id: 'notify-template', title: '通知模板', desc: '更新通知文本内容格式' },
    { id: 'notify-channels', title: '通知通道', desc: 'Bark、Telegram 等推送渠道' }
  ],
  afdian: [
    { id: 'afdian-sponsor', title: '爱发电赞助', desc: '支持开发者持续维护项目' }
  ],
  about: [
    { id: 'about-info', title: '关于系统', desc: '版本信息、更新与文档' }
  ]
}

const activeTabItem = computed(() => navTabs.find(tab => tab.name === activeName.value))

const currentSections = computed(() => {
  const list = tabSectionsMap[activeName.value] || []
  return list.filter(item => !item.condition || item.condition())
})

// 切换顶部 Tab
const switchTab = (tabName) => {
  activeName.value = tabName
  nextTick(() => {
    activeSectionId.value = currentSections.value[0]?.id || ''
    contentScrollbarRef.value?.setScrollTop(0)
  })
}

// 点击左侧小节目录，平滑滚动至对应配置区域
const scrollToSection = (id) => {
  activeSectionId.value = id
  const target = document.getElementById(id)
  if (target && contentScrollbarRef.value) {
    const scrollWrap = contentScrollbarRef.value.wrapRef
    if (scrollWrap) {
      const top = target.getBoundingClientRect().top - scrollWrap.getBoundingClientRect().top + scrollWrap.scrollTop - 8
      scrollWrap.scrollTo({ top: Math.max(0, top), behavior: 'smooth' })
    }
  }
}

// 监听右侧滚动，自动高亮当前所在小节
const handleScroll = ({ scrollTop }) => {
  const sections = currentSections.value
  if (!sections.length) return

  const scrollWrap = contentScrollbarRef.value?.wrapRef
  if (!scrollWrap) return

  const wrapRect = scrollWrap.getBoundingClientRect()
  for (let i = sections.length - 1; i >= 0; i--) {
    const el = document.getElementById(sections[i].id)
    if (el) {
      const elRect = el.getBoundingClientRect()
      if (elRect.top - wrapRect.top <= 60) {
        activeSectionId.value = sections[i].id
        break
      }
    }
  }
}

const loadConfig = () => {
  loading.value = true
  http.config()
      .then(res => {
        config.value = res.data
        nextTick(() => {
          activeSectionId.value = currentSections.value[0]?.id || ''
        })
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

/* ================= 1. 顶部 Tab 导航（红框） ================= */
.config-top-tabs-bar {
  flex-shrink: 0;
  background: var(--el-bg-color);
  border-bottom: 1px solid var(--el-border-color-light);
  padding: 0 16px;
}

.top-tabs-scrollbar {
  width: 100%;
}

.top-tabs-track {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 6px 0;
  white-space: nowrap;
}

.top-tab-btn {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  padding: 8px 14px;
  border-radius: 8px;
  border: 1px solid transparent;
  background: none;
  font-size: 13px;
  font-weight: 500;
  color: var(--el-text-color-regular);
  cursor: pointer;
  transition: all 0.2s ease;
  white-space: nowrap;
}

.top-tab-btn:hover {
  background: var(--el-fill-color-light);
  color: var(--el-text-color-primary);
}

.top-tab-btn.is-active {
  background: var(--el-color-primary-light-9);
  border-color: var(--el-color-primary-light-7);
  color: var(--el-color-primary);
  font-weight: 600;
}

.top-tab-icon {
  font-size: 16px;
  flex-shrink: 0;
}

.top-tab-text {
  line-height: 1;
}

/* ================= 2. 主体工作区 ================= */
.config-workspace {
  position: relative;
  flex: 1;
  min-height: 0;
  min-width: 0;
  display: flex;
  overflow: hidden;
  background: var(--el-bg-color-page);
}

.config-main {
  flex: 1;
  min-width: 0;
  height: 100%;
}

.config-content-scroll {
  height: 100%;
}

.config-panel-wrapper {
  max-width: 960px;
  margin: 0 auto;
  padding: 24px 32px 64px;
}

.config-panel-body {
  min-height: 200px;
}

/* ================= 3. 悬浮窗目录导航（不垂直拉满，高度随内容自适应） ================= */
.config-float-widget {
  position: absolute;
  top: 24px;
  z-index: 20;
  transition: all 0.25s cubic-bezier(0.16, 1, 0.3, 1);
  pointer-events: auto;
}

/* 停靠在右侧（默认） */
.config-float-widget.dock-right {
  right: 28px;
}

/* 停靠在左侧 */
.config-float-widget.dock-left {
  left: 28px;
}

/* 展开卡片窗体 */
.float-window-card {
  width: 220px;
  height: fit-content; /* 关键：自适应高度，绝不拉满 */
  max-height: calc(100vh - 220px);
  background: var(--el-bg-color-overlay);
  backdrop-filter: blur(16px);
  -webkit-backdrop-filter: blur(16px);
  border: 1px solid var(--el-border-color-light);
  border-radius: 12px;
  box-shadow: 0 10px 30px rgba(0, 0, 0, 0.18), 0 2px 8px rgba(0, 0, 0, 0.08);
  display: flex;
  flex-direction: column;
  overflow: hidden;
  animation: floatPop 0.2s cubic-bezier(0.16, 1, 0.3, 1);
}

@keyframes floatPop {
  from {
    opacity: 0;
    transform: scale(0.95) translateY(-6px);
  }
  to {
    opacity: 1;
    transform: scale(1) translateY(0);
  }
}

/* 窗头 */
.float-window-header {
  padding: 10px 12px;
  display: flex;
  align-items: center;
  justify-content: space-between;
  border-bottom: 1px solid var(--el-border-color-extra-light);
  background: var(--el-fill-color-lighter);
}

.header-left {
  display: flex;
  align-items: center;
  gap: 6px;
  min-width: 0;
}

.header-icon {
  font-size: 14px;
  color: var(--el-color-primary);
  flex-shrink: 0;
}

.header-title {
  font-size: 12px;
  font-weight: 650;
  color: var(--el-text-color-primary);
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.header-badge {
  font-size: 11px;
  padding: 1px 6px;
  border-radius: 10px;
  background: var(--el-fill-color-dark);
  color: var(--el-text-color-secondary);
  font-weight: 500;
}

.header-actions {
  display: flex;
  align-items: center;
  gap: 2px;
}

.win-tool-btn {
  width: 22px;
  height: 22px;
  border-radius: 6px;
  border: none;
  background: none;
  color: var(--el-text-color-secondary);
  cursor: pointer;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  font-size: 12px;
  transition: all 0.15s ease;
}

.win-tool-btn:hover {
  background: var(--el-fill-color);
  color: var(--el-text-color-primary);
}

/* 窗体内容 */
.float-window-body {
  overflow-y: auto;
  max-height: calc(100vh - 270px);
  padding: 6px;
}

.float-nav-list {
  display: flex;
  flex-direction: column;
  gap: 3px;
}

.float-nav-item {
  display: flex;
  align-items: flex-start;
  gap: 8px;
  padding: 8px 10px;
  border-radius: 8px;
  border: 1px solid transparent;
  background: none;
  cursor: pointer;
  text-align: left;
  transition: all 0.15s ease;
  color: var(--el-text-color-regular);
  width: 100%;
}

.float-nav-item:hover {
  background: var(--el-fill-color-light);
  color: var(--el-text-color-primary);
}

.float-nav-item.is-active {
  background: var(--el-color-primary-light-9);
  border-color: var(--el-color-primary-light-7);
  color: var(--el-color-primary);
}

.nav-dot {
  width: 6px;
  height: 6px;
  border-radius: 50%;
  background: var(--el-border-color);
  margin-top: 5px;
  flex-shrink: 0;
  transition: background 0.15s ease;
}

.float-nav-item.is-active .nav-dot {
  background: var(--el-color-primary);
}

.nav-content {
  min-width: 0;
  flex: 1;
  display: flex;
  flex-direction: column;
}

.nav-title {
  font-size: 12px;
  font-weight: 600;
  line-height: 1.35;
}

.nav-desc {
  font-size: 10px;
  color: var(--el-text-color-secondary);
  margin-top: 2px;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.float-nav-item.is-active .nav-desc {
  color: var(--el-color-primary-light-3);
}

/* 收起时的胶囊按钮 */
.float-collapsed-capsule {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  padding: 6px 14px;
  background: var(--el-bg-color-overlay);
  backdrop-filter: blur(16px);
  -webkit-backdrop-filter: blur(16px);
  border: 1px solid var(--el-border-color-light);
  border-radius: 20px;
  box-shadow: 0 4px 16px rgba(0, 0, 0, 0.15);
  color: var(--el-text-color-primary);
  font-size: 12px;
  font-weight: 550;
  cursor: pointer;
  transition: all 0.2s cubic-bezier(0.16, 1, 0.3, 1);
}

.float-collapsed-capsule:hover {
  background: var(--el-color-primary-light-9);
  border-color: var(--el-color-primary);
  color: var(--el-color-primary);
  transform: translateY(-1px);
  box-shadow: 0 6px 20px rgba(0, 0, 0, 0.2);
}

.capsule-icon {
  font-size: 13px;
  color: var(--el-color-primary);
}

.capsule-badge {
  font-size: 11px;
  padding: 0 5px;
  border-radius: 10px;
  background: var(--el-fill-color-dark);
  color: var(--el-text-color-secondary);
}

/* ================= 响应式适配 ================= */
@media (max-width: 800px) {
  .config-float-widget {
    top: 12px;
    right: 12px !important;
    left: auto !important;
  }

  .float-window-card {
    width: 190px;
  }

  .nav-desc {
    display: none;
  }

  .config-panel-wrapper {
    padding: 16px 14px 32px;
  }
}
</style>
