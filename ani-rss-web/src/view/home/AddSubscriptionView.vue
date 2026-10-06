<template>
  <div class="add-sub-page app-page-layout">
    <!-- 弹窗组件挂载 -->
    <CollectionView ref="collectionRef"/>
    <BgmView ref="bgmRef" @callback="bgmCallback"/>

    <!-- 顶栏导航 -->
    <header class="add-sub-header">
      <div class="header-left">
        <el-button
            class="back-btn"
            icon="ArrowLeft"
            circle
            @click="handleBack"
            :title="step === 2 ? '返回选择番剧' : '返回订阅列表'"
        />
        <div class="header-title-group">
          <h1 class="header-title">添加订阅</h1>
          <span class="header-subtitle">
            <template v-if="step === 1">{{ currentSourceLabel }} · 共 {{ totalAnimeCount }} 部番剧</template>
            <template v-else>第 2 步：确认与微调订阅配置</template>
          </span>
        </div>
      </div>

      <!-- Step 1 数据源切换 -->
      <div v-if="step === 1" class="header-center">
        <div class="source-segmented">
          <button
              v-for="src in sourceList"
              :key="src.key"
              type="button"
              class="source-tab-btn"
              :class="{ 'is-active': activeSource === src.key }"
              @click="switchSource(src.key)">
            <img v-if="src.icon" :src="src.icon" :alt="src.label" class="source-tab-icon"/>
            <el-icon v-else class="source-tab-icon-el">
              <Link/>
            </el-icon>
            <span>{{ src.label }}</span>
          </button>
        </div>
      </div>

      <div class="header-right">
        <el-button
            v-if="step === 1"
            icon="FolderAdd"
            bg
            text
            @click="collectionRef?.show">
          添加合集
        </el-button>
        <template v-else>
          <el-button bg text @click="step = 1">
            重新选番
          </el-button>
        </template>
      </div>
    </header>

    <div class="add-sub-body app-page-content app-page-padding">
      <!-- ================= STEP 1: 宫格式番剧浏览工作台 ================= -->
      <div v-if="step === 1" class="step-one-container">
        <!-- 非手动模式：卡片宫格流 -->
        <template v-if="activeSource !== 'manual'">
          <!-- 工具栏：筛选控制 -->
          <div class="subscription-toolbar">
            <div class="subscription-filters">
              <el-input
                  v-model="searchKeyword"
                  class="subscription-search"
                  placeholder="搜索番剧名称..."
                  clearable
                  prefix-icon="Search"
                  @keyup.enter="handleSearch"
                  @clear="handleClearSearch"
              >
                <template #append>
                  <el-button icon="Search" :loading="searchLoading" @click="handleSearch"/>
                </template>
              </el-input>

              <el-select
                  v-if="seasons.length"
                  v-model="selectedSeason"
                  class="subscription-select season-select"
                  placeholder="选择季度"
                  :disabled="animeListLoading || (searchKeyword && searchKeyword.length > 0)"
                  @change="handleSeasonChange">
                <el-option
                    v-for="s in seasons"
                    :key="s.value"
                    :label="s.label"
                    :value="s.value"
                />
              </el-select>

              <el-select
                  v-model="filterSubscribeStatus"
                  class="subscription-select status-select"
                  placeholder="订阅状态">
                <el-option label="全部状态" value="all"/>
                <el-option label="仅未订阅" value="unsubscribed"/>
                <el-option label="仅已订阅" value="subscribed"/>
              </el-select>
            </div>

            <div class="subscription-actions">
              <el-button
                  class="auto-button"
                  icon="Refresh"
                  :loading="animeListLoading"
                  @click="retryLoad">
                刷新
              </el-button>
            </div>
          </div>

          <!-- 星期快速导航胶囊栏 -->
          <div v-if="availableWeeks.length > 1" class="week-pills-bar">
            <button
                v-for="w in availableWeeks"
                :key="w.key"
                type="button"
                class="week-pill-btn"
                :class="{ 'is-active': activeWeek === w.key }"
                @click="activeWeek = w.key">
              {{ w.label }}
              <span v-if="w.count > 0" class="week-count">({{ w.count }})</span>
            </button>
          </div>

          <!-- 垂直周流卡片宫格列表 -->
          <div v-loading="animeListLoading" class="grid-list-scroll-wrap">
            <el-scrollbar class="hide-scrollbar">
              <div class="list-content">
                <template v-if="groupedAnimeList.length">
                  <div
                      v-for="weekGroup in groupedAnimeList"
                      :key="weekGroup.weekLabel"
                      class="week-section-block">
                    <h2 class="list-week-title">
                      {{ weekGroup.weekLabel }}
                      <span class="week-count-tag">({{ weekGroup.items.length }})</span>
                    </h2>
                    <div class="grid-container card-grid-container">
                      <div
                          v-for="anime in weekGroup.items"
                          :key="anime.id"
                          class="anime-grid-card-wrap"
                          @click="openAnimeDrawer(anime)"
                      >
                        <el-card shadow="never" class="anime-card-box">
                          <div class="list-card-content">
                            <div class="list-card-image-container">
                              <img
                                  :src="proxyImage(anime.cover)"
                                  :alt="anime.title"
                                  class="list-card-image"
                                  loading="lazy"
                              />
                              <span v-if="anime.score > 0" class="card-score-badge">
                                {{ Number(anime.score).toFixed(1) }}
                              </span>
                            </div>
                            <div class="list-card-info">
                              <div class="list-card-info-inner">
                                <div class="card-title-row">
                                  <el-tooltip :content="anime.title" placement="top">
                                    <el-text class="list-card-title" line-clamp="2" truncated>
                                      {{ anime.title }}
                                    </el-text>
                                  </el-tooltip>
                                </div>
                                <div class="list-card-tags">
                                  <el-tag v-if="anime.exists" size="small" type="success">
                                    已订阅
                                  </el-tag>
                                  <el-tag v-else size="small" type="info" effect="plain">
                                    未订阅
                                  </el-tag>
                                </div>
                              </div>
                              <div class="list-card-actions">
                                <el-button size="small" type="primary" text bg icon="Plus">
                                  选择字幕组
                                </el-button>
                              </div>
                            </div>
                          </div>
                        </el-card>
                      </div>
                    </div>
                  </div>
                </template>

                <!-- 空状态展示 -->
                <div v-else-if="!animeListLoading" class="empty-anime">
                  <el-empty :description="animeListError || '暂无匹配番剧'">
                    <template #extra>
                      <el-button v-if="animeListError" type="primary" size="small" icon="Refresh" @click="retryLoad">重试加载</el-button>
                      <el-button v-if="!animeListError && (searchKeyword || filterSubscribeStatus !== 'all')" size="small" icon="Back" @click="handleClearFilters">重置筛选</el-button>
                      <el-button size="small" @click="switchSource('manual')">切换到手动输入 RSS</el-button>
                    </template>
                  </el-empty>
                </div>
                <div class="list-bottom-spacer"></div>
              </div>
            </el-scrollbar>
          </div>
        </template>

        <!-- 手动输入 RSS 模式 -->
        <div v-else class="manual-rss-workspace">
          <div class="manual-rss-card">
            <div class="manual-rss-header">
              <h3>自定义 RSS 订阅</h3>
              <p>适用于未收录在预设站点中的动漫，或手动定制的第三方 RSS 源</p>
            </div>
            <el-form class="manual-form" label-position="top">
              <el-form-item label="番剧名称">
                <div class="manual-title-row">
                  <el-input
                      v-model="manualForm.title"
                      placeholder="例如：葬送的芙莉莲"
                  />
                  <el-button
                      icon="Search"
                      type="primary"
                      text
                      bg
                      @click="bgmRef?.show(manualForm.title)">
                    搜索 Bangumi
                  </el-button>
                </div>
              </el-form-item>

              <el-form-item label="Bangumi 条目地址">
                <el-input
                    v-model="manualForm.bgmUrl"
                    placeholder="https://bgm.tv/subject/123456"
                />
              </el-form-item>

              <el-form-item label="RSS 地址">
                <el-input
                    v-model="manualForm.url"
                    type="textarea"
                    :rows="4"
                    placeholder="https://example.com/feed.xml"
                />
              </el-form-item>
            </el-form>

            <el-alert
                title="包含磁力链接的 RSS 不支持 Aria2 下载器。建议填写对应的 Bangumi 地址以获得正确的元数据匹配。"
                type="info"
                show-icon
                :closable="false"
                class="manual-alert"
            />

            <div class="manual-action-bar">
              <el-button
                  type="primary"
                  size="large"
                  icon="ArrowRight"
                  :loading="subscribingLoading"
                  @click="submitManualRss">
                下一步：解析并确认配置
              </el-button>
            </div>
          </div>
        </div>

      <!-- ================= 字幕组选择抽屉 ================= -->
      <el-drawer
          v-model="drawerVisible"
          :size="drawerWidth"
          :with-header="false"
          class="anime-drawer-modal"
          destroy-on-close
      >
        <div v-if="selectedAnime" class="drawer-content-wrap">
          <!-- 抽屉头部 Hero Banner -->
          <div class="drawer-header">
            <div class="drawer-hero-card">
              <img
                  :src="proxyImage(selectedAnime.cover)"
                  :alt="selectedAnime.title"
                  class="drawer-hero-cover"
              />
              <div class="drawer-hero-info">
                <div class="drawer-title-row">
                  <h3 class="drawer-anime-title" :title="selectedAnime.title">{{ selectedAnime.title }}</h3>
                  <el-button circle text icon="Close" @click="drawerVisible = false" class="drawer-close-btn"/>
                </div>
                <div class="drawer-hero-meta">
                  <span v-if="selectedAnime.score > 0" class="drawer-score">
                    评分: <strong>{{ Number(selectedAnime.score).toFixed(1) }}</strong>
                  </span>
                  <el-tag v-if="selectedAnime.exists" type="success" size="small">已在订阅中</el-tag>
                </div>
                <div class="drawer-external-links">
                  <el-button
                      v-if="activeSource === 'mikan' && selectedAnime.rawId"
                      icon="Link"
                      size="small"
                      text
                      bg
                      @click="openExternal(selectedAnime.rawId)">
                    在 Mikan 查看
                  </el-button>
                  <el-button
                      v-else-if="activeSource === 'ani-bt' && selectedAnime.rawId"
                      icon="Link"
                      size="small"
                      text
                      bg
                      @click="openExternal('https://anibt.net/anime/' + selectedAnime.rawId)">
                    在 AniBT 查看
                  </el-button>
                  <el-button
                      v-else-if="activeSource === 'anime-garden' && selectedAnime.rawId"
                      icon="Link"
                      size="small"
                      text
                      bg
                      @click="openExternal('https://bgm.tv/subject/' + selectedAnime.rawId)">
                    在 Bangumi 查看
                  </el-button>
                </div>
              </div>
            </div>
          </div>

          <!-- 字幕组与种子资源面板 -->
          <div v-loading="groupsLoading" class="drawer-groups-body">
            <div class="section-title-bar">
              <div class="title-with-count">
                <h4>字幕组列表</h4>
                <span class="sub-count-tag" v-if="currentGroups.length">
                  共 {{ currentGroups.length }} 个字幕组
                </span>
              </div>
            </div>

            <!-- 字幕组水平标签栏 -->
            <div v-if="currentGroups.length" class="subgroups-pills-bar">
              <el-scrollbar class="subgroup-scroll">
                <div class="subgroups-pills-flow">
                  <button
                      v-for="(grp, idx) in currentGroups"
                      :key="idx"
                      type="button"
                      class="subgroup-pill-btn"
                      :class="{ 'is-active': activeGroupIndex === idx }"
                      @click="activeGroupIndex = idx">
                    <span class="subgroup-name">{{ grp.label }}</span>
                    <span v-if="grp.updateDay" class="subgroup-day">
                      {{ grp.updateDay }}
                    </span>
                  </button>
                </div>
              </el-scrollbar>
            </div>

            <!-- 当前选中字幕组详情与一键订阅 -->
            <div v-if="selectedGroup" class="selected-group-card">
              <div class="selected-group-header">
                <div class="group-header-left">
                  <h5 class="current-group-title">{{ selectedGroup.label }}</h5>
                  <div v-if="selectedGroup.tags && selectedGroup.tags.length" class="group-tags">
                    <el-tag
                        v-for="tag in selectedGroup.tags"
                        :key="tag"
                        size="small"
                        effect="plain"
                        class="feature-tag">
                      {{ tag }}
                    </el-tag>
                  </div>
                </div>

                <!-- 一键订阅核心操作 -->
                <div class="group-header-right">
                  <el-select
                      v-if="matchedRegexOptions.length > 1"
                      v-model="selectedRegexOption"
                      class="match-regex-select"
                      size="default"
                      placeholder="选择画质规则">
                    <el-option
                        v-for="(opt, oi) in matchedRegexOptions"
                        :key="oi"
                        :label="opt.label"
                        :value="opt.value"
                    />
                  </el-select>

                  <el-button
                      type="primary"
                      size="default"
                      icon="Plus"
                      class="quick-subscribe-btn"
                      :loading="subscribingLoading"
                      @click="subscribeCurrentGroup">
                    订阅此字幕组
                  </el-button>
                </div>
              </div>

              <!-- 种子资源列表 -->
              <div class="torrents-stream">
                <div class="torrents-header">
                  <span class="torrents-title">最新发布种子 ({{ selectedGroup.items.length }})</span>
                </div>
                <div v-if="selectedGroup.items.length" class="torrents-list">
                  <div
                      v-for="(t, ti) in selectedGroup.items"
                      :key="ti"
                      class="torrent-item-row">
                    <div class="torrent-main">
                      <span class="torrent-name" :title="t.title">{{ t.title }}</span>
                      <div class="torrent-meta">
                        <span class="meta-item">{{ t.size }}</span>
                        <span class="meta-dot">·</span>
                        <span class="meta-item">{{ t.date }}</span>
                      </div>
                    </div>
                    <div class="torrent-actions">
                      <el-button
                          v-if="t.magnet"
                          icon="CopyDocument"
                          size="small"
                          text
                          bg
                          title="复制磁力链接"
                          @click="copyText(t.magnet)"
                      />
                      <el-button
                          v-if="t.torrent"
                          icon="Download"
                          size="small"
                          text
                          bg
                          title="下载种子文件"
                          @click="openExternal(t.torrent)"
                      />
                      <el-button
                          size="small"
                          text
                          type="primary"
                          bg
                          icon="Check"
                          @click="subscribeCurrentGroup">
                        订阅
                      </el-button>
                    </div>
                  </div>
                </div>
                <el-empty v-else description="该字幕组暂无发布条目" :image-size="60"/>
              </div>
            </div>

            <el-empty
                v-else-if="!groupsLoading"
                description="未获取到该番剧的字幕组资源"
                class="empty-groups"
            />
          </div>
        </div>
      </el-drawer>
      </div>

      <!-- ================= STEP 2: 配置确认与保存 ================= -->
      <div v-else-if="step === 2" class="step-two-container">
        <el-scrollbar class="step-two-scroll">
          <div class="step-two-content-wrap">
            <div class="step-two-header">
              <h2>确认番剧配置</h2>
              <p>请核对并调整番剧的标题、TMDB 刮削信息、排除匹配规则与下载路径</p>
            </div>
            <div class="step-two-form-card">
              <AniView v-model:ani="configuredAni" @callback="handleSaveConfiguredAni"/>
            </div>
          </div>
        </el-scrollbar>
      </div>
    </div>
  </div>
</template>

<script setup>
import {computed, onActivated, onMounted, onUnmounted, ref} from "vue";
import {useRouter} from "vue-router";
import {ElMessage} from "element-plus";
import {
  ArrowLeft,
  ArrowRight,
  Back,
  Check,
  Close,
  CopyDocument,
  Download,
  FolderAdd,
  Link,
  Plus,
  Refresh,
  Search
} from "@element-plus/icons-vue";

import AniView from "@/view/home/AniView.vue";
import CollectionView from "@/view/home/CollectionView.vue";
import BgmView from "@/view/home/BgmView.vue";
import {aniData} from "@/js/ani.js";
import {proxyImage} from "@/js/global.js";
import * as http from "@/js/http.js";

import mikanIcon from "@/icon/icon-Mikan.png";
import aniBTIcon from "@/icon/icon-AniBT.png";
import animeGardenIcon from "@/icon/icon-AnimeGarden.png";

const router = useRouter()

// 引用
const collectionRef = ref()
const bgmRef = ref()

// 流程状态
const step = ref(1) // 1: 浏览选番, 2: 确认配置
const activeSource = ref('mikan') // mikan, ani-bt, anime-garden, manual
const drawerVisible = ref(false)
const drawerWidth = ref('560px')
const filterSubscribeStatus = ref('all') // all, unsubscribed, subscribed

const sourceList = [
  {key: 'mikan', label: '蜜柑 Mikan', icon: mikanIcon},
  {key: 'ani-bt', label: 'AniBT', icon: aniBTIcon},
  {key: 'anime-garden', label: '动漫花园', icon: animeGardenIcon},
  {key: 'manual', label: '手动 RSS', icon: null}
]

// 数据状态
const animeListLoading = ref(false)
const searchLoading = ref(false)
const groupsLoading = ref(false)
const subscribingLoading = ref(false)
const animeListError = ref('')

// 当前展示的视图数据
const seasons = ref([])
const selectedSeason = ref('')
const searchKeyword = ref('')
const activeWeek = ref('全部')
const rawWeeksData = ref([])
const animeList = ref([])

// 番剧与字幕组
const selectedAnime = ref(null)
const groupsCache = ref({}) // id -> groups
const currentGroups = ref([])
const activeGroupIndex = ref(0)
const selectedRegexOption = ref('')

// 每个源独立的状态缓存，避免切换源时状态覆盖或重新拉取全站
const sourceState = ref({
  mikan: {
    seasons: [],
    selectedSeason: '',
    selectedSeasonRaw: null,
    weeks: [],
    animeList: [],
    initialized: false
  },
  'ani-bt': {
    seasons: [],
    selectedSeason: '',
    weeks: [],
    animeList: [],
    initialized: false
  },
  'anime-garden': {
    seasons: [],
    selectedSeason: '',
    weeks: [],
    animeList: [],
    initialized: false
  }
})

// 手动 RSS
const manualForm = ref({
  title: '',
  bgmUrl: '',
  url: ''
})

// Step 2 配置数据
const configuredAni = ref(JSON.parse(JSON.stringify(aniData)))

const currentSourceLabel = computed(() => {
  const src = sourceList.find(s => s.key === activeSource.value)
  return src ? src.label : ''
})

// 按星期分组的番剧列表，支持星期筛选、关键词搜索与订阅状态过滤
const groupedAnimeList = computed(() => {
  let list = rawWeeksData.value || []
  if (activeWeek.value !== '全部') {
    list = list.filter(w => w.weekLabel === activeWeek.value)
  }
  if (searchKeyword.value.trim()) {
    const kw = searchKeyword.value.trim().toLowerCase()
    list = list.map(w => ({
      ...w,
      items: (w.items || []).filter(it => (it.title || '').toLowerCase().includes(kw))
    }))
  }
  if (filterSubscribeStatus.value === 'subscribed') {
    list = list.map(w => ({
      ...w,
      items: (w.items || []).filter(it => it.exists)
    }))
  } else if (filterSubscribeStatus.value === 'unsubscribed') {
    list = list.map(w => ({
      ...w,
      items: (w.items || []).filter(it => !it.exists)
    }))
  }
  return list.filter(w => w.items && w.items.length > 0)
})

// 统计当前展示的番剧总数
const totalAnimeCount = computed(() => {
  return groupedAnimeList.value.reduce((acc, w) => acc + (w.items?.length || 0), 0)
})

// 可选星期分类导航胶囊
const availableWeeks = computed(() => {
  const result = [{key: '全部', label: '全部', count: animeList.value.length}]
  for (const w of rawWeeksData.value) {
    const items = w.items || []
    result.push({
      key: w.weekLabel,
      label: w.weekLabel,
      count: items.length
    })
  }
  return result
})

// 当前选中的字幕组对象
const selectedGroup = computed(() => {
  if (!currentGroups.value.length) return null
  return currentGroups.value[activeGroupIndex.value] || null
})

// 当前字幕组可用的过滤规则选项
const matchedRegexOptions = computed(() => {
  if (!selectedGroup.value?.regexList) {
    return []
  }
  const list = [...selectedGroup.value.regexList]
  const hasEmpty = list.some(itemGroup => !itemGroup || !itemGroup.length)
  if (!hasEmpty) {
    list.push([])
  }
  return list.map(itemGroup => {
    if (!itemGroup || !itemGroup.length) {
      return {label: '全部资源 (不过滤)', value: JSON.stringify([])}
    }
    const label = itemGroup.map(it => it.label).join(' / ')
    const value = JSON.stringify(itemGroup.map(it => it.regex))
    return {label, value}
  })
})

// 数据规范化辅助
const normalizeAnimeItem = (item, source) => {
  let id = ''
  let rawId = ''
  let title = ''
  let cover = item.cover || ''
  let score = 0
  let exists = Boolean(item.exists)

  if (source === 'mikan') {
    id = String(item.url || '')
    rawId = item.url || ''
    title = item.title || ''
    score = item.score || 0
  } else if (source === 'ani-bt') {
    id = String(item.bgmId || item.animeId || '')
    rawId = String(item.bgmId || item.animeId || '')
    if (typeof item.title === 'object' && item.title !== null) {
      title = item.title.primary || item.title.chinese || ''
    } else {
      title = item.title || ''
    }
    score = item.rating || 0
  } else if (source === 'anime-garden') {
    id = String(item.id || '')
    rawId = String(item.id || '')
    title = item.name || item.title || ''
    score = item.score || 0
  }

  return {
    id,
    rawId,
    title,
    cover,
    score,
    exists,
    raw: item,
    source
  }
}

const normalizeGroup = (group, source) => {
  const label = group.label || group.name || '未知字幕组'
  const tags = group.groupRegex?.tags || []
  const regexList = group.groupRegex?.regexList || []
  const items = (group.items || []).map(ti => ({
    title: ti.title,
    size: ti.formatSize || ti.size || '--',
    date: ti.createdAt || ti.publishedAt || '--',
    magnet: ti.magnet || '',
    torrent: ti.torrent || ''
  }))
  let bgmUrl = group.bgmUrl || ''
  if (!bgmUrl && group.bgmId) {
    bgmUrl = `https://bgm.tv/subject/${group.bgmId}`
  }
  return {
    label,
    updateDay: group.updateDay || '',
    tags,
    regexList,
    items,
    rss: group.rss || group.url || '',
    bgmUrl,
    subgroup: group.subgroup || label,
    raw: group,
    source
  }
}

// 切换数据源
const switchSource = (src) => {
  if (activeSource.value === src) return
  activeSource.value = src
  selectedAnime.value = null
  currentGroups.value = []
  searchKeyword.value = ''
  activeWeek.value = '全部'
  animeListError.value = ''

  if (src === 'manual') return

  const state = sourceState.value[src]
  if (state && state.initialized && state.animeList.length > 0) {
    seasons.value = state.seasons
    selectedSeason.value = state.selectedSeason
    rawWeeksData.value = state.weeks
    animeList.value = state.animeList
    selectAnime(state.animeList[0])
  } else {
    loadSourceData()
  }
}

// 加载数据源数据
const loadSourceData = async (keyword = '', seasonParam = null) => {
  const currentSrc = activeSource.value
  if (currentSrc === 'manual') return

  animeListLoading.value = true
  animeListError.value = ''
  selectedAnime.value = null
  currentGroups.value = []

  const state = sourceState.value[currentSrc]

  try {
    if (currentSrc === 'mikan') {
      let reqBody = {}
      if (keyword) {
        reqBody = {}
      } else if (seasonParam) {
        reqBody = seasonParam
      } else if (state.selectedSeasonRaw) {
        reqBody = state.selectedSeasonRaw
      }

      const res = await http.mikan(keyword, reqBody)
      const {seasons: sList, weeks: rawWeeks} = res.data || {}

      // 如果返回了季度列表，更新季度
      if (sList?.length) {
        state.seasons = sList.map(s => ({
          label: s.seasonLabel,
          value: s.seasonLabel,
          raw: s
        }))
        if (!state.selectedSeason) {
          const found = sList.find(s => s.select) || sList[0]
          state.selectedSeason = found.seasonLabel
          state.selectedSeasonRaw = found
        }
      }

      // 如果是按季度查询，记录当前季度
      if (!keyword && seasonParam) {
        state.selectedSeason = seasonParam.seasonLabel || state.selectedSeason
        state.selectedSeasonRaw = seasonParam
      }

      const weeks = (rawWeeks || []).map(w => ({
        weekLabel: w.weekLabel,
        items: (w.items || []).map(item => normalizeAnimeItem(item, 'mikan'))
      }))
      state.weeks = weeks
      state.animeList = weeks.flatMap(w => w.items)

    } else if (currentSrc === 'ani-bt') {
      const targetSeason = keyword ? '' : (seasonParam || state.selectedSeason || '')
      const res = await http.aniBT(targetSeason, '', keyword)
      const {requestedSeason, availableSeasons, byWeekday} = res.data || {}

      if (availableSeasons?.length) {
        state.seasons = availableSeasons.map(s => ({
          label: s,
          value: s,
          raw: s
        }))
      }
      state.selectedSeason = requestedSeason || state.selectedSeason || availableSeasons?.[0] || ''

      const weeks = (byWeekday || []).map(w => ({
        weekLabel: w.weekdayLabel || `星期${w.weekday}`,
        items: (w.animes || []).map(item => normalizeAnimeItem(item, 'ani-bt'))
      }))
      state.weeks = weeks
      state.animeList = weeks.flatMap(w => w.items)

    } else if (currentSrc === 'anime-garden') {
      const res = await http.animeGardenList('')
      const weeksRaw = Array.isArray(res.data) ? res.data : (res.data?.weeks || [])
      const weeks = weeksRaw.map(w => ({
        weekLabel: w.weekLabel,
        items: (w.subjects || []).map(item => normalizeAnimeItem(item, 'anime-garden'))
      }))
      state.seasons = []
      state.selectedSeason = ''
      state.weeks = weeks
      state.animeList = weeks.flatMap(w => w.items)
    }

    state.initialized = true

    // 同步到页面视图
    seasons.value = state.seasons
    selectedSeason.value = state.selectedSeason
    rawWeeksData.value = state.weeks
    animeList.value = state.animeList
  } catch (e) {
    const errorMsg = e.message || String(e) || '加载失败'
    animeListError.value = `加载番剧列表失败: ${errorMsg}`
    ElMessage.error(animeListError.value)
  } finally {
    animeListLoading.value = false
  }
}

// 季度变更
const handleSeasonChange = (val) => {
  const currentSrc = activeSource.value
  const state = sourceState.value[currentSrc]
  if (!state) return

  const target = state.seasons.find(s => s.value === val)
  if (!target) return

  state.selectedSeason = val
  selectedSeason.value = val

  if (currentSrc === 'mikan') {
    state.selectedSeasonRaw = target.raw
    loadSourceData('', target.raw)
  } else if (currentSrc === 'ani-bt') {
    loadSourceData('', target.value)
  }
}

// 搜索
const handleSearch = () => {
  searchLoading.value = true
  loadSourceData(searchKeyword.value).finally(() => {
    searchLoading.value = false
  })
}

const handleClearSearch = () => {
  searchKeyword.value = ''
  loadSourceData('')
}

const handleClearFilters = () => {
  searchKeyword.value = ''
  filterSubscribeStatus.value = 'all'
  activeWeek.value = '全部'
  loadSourceData('')
}

const retryLoad = () => {
  loadSourceData(searchKeyword.value)
}

// 打开字幕组抽屉并加载字幕组数据
const openAnimeDrawer = (anime) => {
  selectedAnime.value = anime
  drawerVisible.value = true
  selectAnime(anime)
}

// 选择番剧，立即获取字幕组
const selectAnime = async (anime) => {
  if (!anime) return
  selectedAnime.value = anime
  activeGroupIndex.value = 0
  selectedRegexOption.value = ''

  if (groupsCache.value[anime.id]) {
    currentGroups.value = groupsCache.value[anime.id]
    setupDefaultRegex()
    return
  }

  groupsLoading.value = true
  currentGroups.value = []

  try {
    let res = null
    if (activeSource.value === 'mikan') {
      res = await http.mikanGroup(anime.rawId)
    } else if (activeSource.value === 'ani-bt') {
      res = await http.aniBTGroup(anime.rawId)
    } else if (activeSource.value === 'anime-garden') {
      res = await http.animeGardenGroup(anime.rawId)
    }

    const rawList = res?.data || []
    const normalized = rawList.map(grp => normalizeGroup(grp, activeSource.value))
    groupsCache.value[anime.id] = normalized
    currentGroups.value = normalized
    setupDefaultRegex()
  } catch (e) {
    ElMessage.error('获取字幕组失败: ' + (e.message || e))
  } finally {
    groupsLoading.value = false
  }
}

const setupDefaultRegex = () => {
  if (matchedRegexOptions.value.length) {
    selectedRegexOption.value = matchedRegexOptions.value[0].value
  }
}

// 订阅选中的字幕组 -> 解析并进入 Step 2
const subscribeCurrentGroup = async () => {
  if (!selectedGroup.value) return

  subscribingLoading.value = true
  const grp = selectedGroup.value
  const subgroupName = grp.subgroup || grp.label

  let matchArray = []
  if (selectedRegexOption.value) {
    try {
      matchArray = JSON.parse(selectedRegexOption.value)
    } catch {
      matchArray = []
    }
  }

  let type = activeSource.value
  if (type === 'manual') type = 'other'

  const aniPayload = {
    ...JSON.parse(JSON.stringify(aniData)),
    type,
    url: grp.rss,
    bgmUrl: grp.bgmUrl || '',
    subgroup: subgroupName,
    match: matchArray.map(s => `{{${subgroupName}}}:${s}`),
    title: selectedAnime.value?.title || ''
  }

  try {
    const res = await http.rssToAni(aniPayload)
    configuredAni.value = res.data
    configuredAni.value.showDownlaod = false
    configuredAni.value.match = aniPayload.match
    drawerVisible.value = false
    step.value = 2
  } catch (e) {
    ElMessage.error('解析 RSS 订阅失败: ' + (e.message || e))
  } finally {
    subscribingLoading.value = false
  }
}

// 手动模式提交
const submitManualRss = async () => {
  if (!manualForm.value.url.trim()) {
    ElMessage.warning('请填写 RSS 地址')
    return
  }

  subscribingLoading.value = true
  const aniPayload = {
    ...JSON.parse(JSON.stringify(aniData)),
    type: 'other',
    url: manualForm.value.url.trim(),
    bgmUrl: manualForm.value.bgmUrl.trim(),
    title: manualForm.value.title.trim()
  }

  try {
    const res = await http.rssToAni(aniPayload)
    configuredAni.value = res.data
    configuredAni.value.showDownlaod = false
    step.value = 2
  } catch (e) {
    ElMessage.error('解析 RSS 订阅失败: ' + (e.message || e))
  } finally {
    subscribingLoading.value = false
  }
}

// 保存最终配置并返回
const handleSaveConfiguredAni = (done) => {
  http.addAni(configuredAni.value)
      .then(res => {
        ElMessage.success(res.message || '添加订阅成功')
        window.$reLoadList?.()
        step.value = 1
        loadSourceData()
        router.push('/subscriptions')
      })
      .catch(e => {
        ElMessage.error(e.message || '保存订阅失败')
      })
      .finally(() => {
        done?.()
      })
}

// 返回导航
const handleBack = () => {
  if (step.value === 2) {
    step.value = 1
  } else {
    router.push('/subscriptions')
  }
}

// 辅助方法
const bgmCallback = (item) => {
  manualForm.value.title = item.name_cn || item.name
  manualForm.value.bgmUrl = item.url
}

const copyText = (text) => {
  const el = document.createElement('input')
  el.value = text
  document.body.appendChild(el)
  el.select()
  document.execCommand('copy')
  document.body.removeChild(el)
  ElMessage.success('已复制磁力链接')
}

const openExternal = (url) => {
  if (url) window.open(url, '_blank')
}

const updateDrawerWidth = () => {
  if (window.innerWidth < 768) {
    drawerWidth.value = '100%'
  } else {
    drawerWidth.value = '560px'
  }
}

onMounted(() => {
  updateDrawerWidth()
  window.addEventListener('resize', updateDrawerWidth)
  loadSourceData()
})

onActivated(() => {
  // 如果之前是在 Step 2，切回来时自动重置为 Step 1 并刷新状态
  if (step.value === 2) {
    step.value = 1
  }
  loadSourceData()
})

onUnmounted(() => {
  window.removeEventListener('resize', updateDrawerWidth)
})
</script>

<style scoped>
.add-sub-page {
  height: 100%;
  display: flex;
  flex-direction: column;
  overflow: hidden;
  background: var(--el-bg-color-page);
}

/* 顶栏 */
.add-sub-header {
  height: 56px;
  flex-shrink: 0;
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 0 20px;
  background: var(--el-bg-color);
  border-bottom: 1px solid var(--el-border-color-light);
  gap: 16px;
}

.header-left {
  display: flex;
  align-items: center;
  gap: 12px;
}

.header-title-group {
  display: flex;
  flex-direction: column;
}

.header-title {
  margin: 0;
  font-size: 16px;
  font-weight: 650;
  color: var(--el-text-color-primary);
  line-height: 1.2;
}

.header-subtitle {
  font-size: 11px;
  color: var(--el-text-color-secondary);
  margin-top: 2px;
}

.header-center {
  flex: 1;
  display: flex;
  justify-content: center;
}

.source-segmented {
  display: flex;
  align-items: center;
  background: var(--el-fill-color-light);
  padding: 3px;
  border-radius: 8px;
  gap: 4px;
}

.source-tab-btn {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 6px 14px;
  border: none;
  background: none;
  border-radius: 6px;
  font-size: 13px;
  color: var(--el-text-color-regular);
  cursor: pointer;
  transition: all 0.2s ease;
}

.source-tab-btn:hover {
  color: var(--el-text-color-primary);
}

.source-tab-btn.is-active {
  background: var(--el-bg-color);
  color: var(--el-color-primary);
  font-weight: 600;
  box-shadow: 0 1px 3px rgba(0, 0, 0, 0.08);
}

.source-tab-icon {
  width: 16px;
  height: 16px;
  border-radius: 3px;
}

.source-tab-icon-el {
  font-size: 14px;
}

/* 主体容器 */
.add-sub-body {
  flex: 1;
  min-height: 0;
  min-width: 0;
  display: flex;
  flex-direction: column;
  overflow: hidden;
}

/* ================= Step 1 宫格卡片工作台 ================= */
.step-one-container {
  flex: 1;
  height: 100%;
  min-height: 0;
  display: flex;
  flex-direction: column;
  overflow: hidden;
}

/* 工具栏（与订阅页面一致） */
.subscription-toolbar {
  flex-shrink: 0;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
  padding-bottom: 10px;
}

.subscription-filters,
.subscription-actions {
  min-width: 0;
  display: flex;
  align-items: center;
  gap: 8px;
}

.subscription-filters {
  flex: 1;
  flex-wrap: wrap;
}

.subscription-actions {
  flex-shrink: 0;
}

.subscription-search {
  width: 240px;
}

.subscription-select {
  width: 130px;
}

/* 星期胶囊导航 */
.week-pills-bar {
  display: flex;
  gap: 6px;
  overflow-x: auto;
  padding-bottom: 8px;
  flex-shrink: 0;
}

.week-pill-btn {
  padding: 4px 10px;
  border: none;
  background: var(--el-fill-color-light);
  border-radius: 14px;
  font-size: 12px;
  color: var(--el-text-color-regular);
  cursor: pointer;
  white-space: nowrap;
  transition: all 0.15s ease;
}

.week-pill-btn:hover {
  background: var(--el-fill-color);
}

.week-pill-btn.is-active {
  background: var(--el-color-primary);
  color: #fff;
  font-weight: 600;
}

.week-count {
  font-size: 11px;
  opacity: 0.85;
}

/* 垂直周流卡片宫格列表 */
.grid-list-scroll-wrap {
  flex: 1;
  min-height: 0;
  display: flex;
  flex-direction: column;
  overflow: hidden;
}

.hide-scrollbar {
  flex: 1;
  min-height: 0;
}

.list-content {
  margin: 0;
}

.week-section-block {
  margin-bottom: 20px;
}

.list-week-title {
  margin-top: 14px;
  margin-bottom: 8px;
  font-size: 16px;
  font-weight: 600;
  color: var(--el-text-color-primary);
  display: flex;
  align-items: center;
  gap: 8px;
}

.week-count-tag {
  font-size: 13px;
  font-weight: normal;
  color: var(--el-text-color-secondary);
}

/* 宫格布局（与 SubscriptionListView 一致） */
.grid-container {
  display: grid;
  grid-gap: 8px;
  width: 100%;
}

.card-grid-container {
  grid-template-columns: repeat(auto-fill, minmax(380px, 1fr));
}

@media (max-width: 800px) {
  .card-grid-container {
    grid-template-columns: 1fr;
  }
}

.anime-grid-card-wrap {
  cursor: pointer;
}

.anime-card-box {
  border-radius: var(--el-border-radius-base);
  border: 1px solid var(--el-border-color-light);
  transition: all 0.25s ease;
  cursor: pointer;
}

.anime-card-box:hover {
  transform: translateY(-2px);
  box-shadow: 0 4px 14px rgba(0, 0, 0, 0.08);
  border-color: var(--el-color-primary-light-5);
}

/* 卡片内部结构（与 AniCardView 一致） */
.list-card-content {
  display: flex;
  width: 100%;
  align-items: center;
  gap: 12px;
}

.list-card-image-container {
  position: relative;
  flex-shrink: 0;
  height: 125px;
}

.list-card-image {
  border: 1px solid var(--el-border-color-light);
  border-radius: var(--el-border-radius-small);
  height: 125px;
  width: 88px;
  object-fit: cover;
  display: block;
}

.card-score-badge {
  position: absolute;
  top: 4px;
  right: 4px;
  background: rgba(0, 0, 0, 0.72);
  color: #fb7299;
  font-size: 11px;
  font-weight: 700;
  padding: 1px 5px;
  border-radius: 4px;
  backdrop-filter: blur(2px);
}

.list-card-info {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
  justify-content: space-between;
  height: 125px;
}

.list-card-info-inner {
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.card-title-row {
  display: flex;
  align-items: flex-start;
}

.list-card-title {
  font-size: 14px;
  font-weight: 600;
  color: var(--el-text-color-primary);
  line-height: 1.4;
}

.list-card-tags {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
  margin-top: 4px;
}

.list-card-actions {
  display: flex;
  justify-content: flex-end;
  margin-top: auto;
}

.empty-anime {
  padding: 40px 0;
}

.list-bottom-spacer {
  height: 16px;
}

/* ================= 抽屉面板样式 ================= */
.anime-drawer-modal :deep(.el-drawer__body) {
  padding: 0;
  overflow-y: auto;
  background: var(--el-bg-color-page);
}

.drawer-content-wrap {
  display: flex;
  flex-direction: column;
  min-height: 100%;
}

.drawer-header {
  padding: 20px;
  background: var(--el-bg-color);
  border-bottom: 1px solid var(--el-border-color-lighter);
}

.drawer-hero-card {
  display: flex;
  gap: 16px;
  align-items: flex-start;
}

.drawer-hero-cover {
  width: 80px;
  height: 112px;
  border-radius: 8px;
  object-fit: cover;
  flex-shrink: 0;
  box-shadow: 0 4px 10px rgba(0, 0, 0, 0.1);
}

.drawer-hero-info {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.drawer-title-row {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 8px;
}

.drawer-anime-title {
  margin: 0;
  font-size: 16px;
  font-weight: 700;
  color: var(--el-text-color-primary);
  line-height: 1.35;
}

.drawer-close-btn {
  flex-shrink: 0;
}

.drawer-hero-meta {
  display: flex;
  align-items: center;
  gap: 10px;
}

.drawer-score {
  font-size: 13px;
  color: var(--el-text-color-secondary);
}

.drawer-score strong {
  color: #fb7299;
  font-size: 14px;
}

.drawer-external-links {
  display: flex;
  gap: 8px;
  margin-top: 4px;
}

.drawer-groups-body {
  padding: 16px 20px 40px;
  display: flex;
  flex-direction: column;
  gap: 14px;
}

.subgroups-pills-bar {
  background: var(--el-bg-color);
  padding: 6px 8px;
  border-radius: 10px;
  border: 1px solid var(--el-border-color-lighter);
}

.subgroups-pills-flow {
  display: flex;
  gap: 6px;
  align-items: center;
}

.subgroup-pill-btn {
  display: flex;
  flex-direction: column;
  align-items: flex-start;
  padding: 6px 12px;
  border: 1px solid var(--el-border-color-lighter);
  background: var(--el-fill-color-light);
  border-radius: 6px;
  cursor: pointer;
  white-space: nowrap;
  transition: all 0.15s ease;
}

.subgroup-pill-btn:hover {
  background: var(--el-fill-color);
}

.subgroup-pill-btn.is-active {
  background: var(--el-color-primary-light-9);
  border-color: var(--el-color-primary);
  color: var(--el-color-primary);
}

.subgroup-name {
  font-size: 13px;
  font-weight: 600;
}

.subgroup-day {
  font-size: 10px;
  color: var(--el-text-color-secondary);
  margin-top: 2px;
}

.selected-group-card {
  border-radius: 12px;
  background: var(--el-bg-color);
  border: 1px solid var(--el-border-color-lighter);
  overflow: hidden;
}

.selected-group-header {
  padding: 12px 16px;
  border-bottom: 1px solid var(--el-border-color-lighter);
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  background: var(--el-fill-color-lighter);
  flex-wrap: wrap;
}

.current-group-title {
  margin: 0;
  font-size: 15px;
  font-weight: 700;
}

.group-tags {
  display: flex;
  flex-wrap: wrap;
  gap: 4px;
  margin-top: 4px;
}

.group-header-right {
  display: flex;
  align-items: center;
  gap: 8px;
}

.match-regex-select {
  width: 160px;
}

.torrents-stream {
  padding: 12px 16px;
}

.torrents-title {
  font-size: 12px;
  font-weight: 600;
  color: var(--el-text-color-secondary);
  margin-bottom: 8px;
}

.torrents-list {
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.torrent-item-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  padding: 8px 10px;
  border-radius: 8px;
  background: var(--el-fill-color-blank);
  border: 1px solid var(--el-border-color-extra-light);
  transition: all 0.15s ease;
}

.torrent-item-row:hover {
  background: var(--el-fill-color-light);
}

.torrent-main {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.torrent-name {
  font-size: 12px;
  line-height: 1.4;
  word-break: break-all;
}

.torrent-meta {
  font-size: 11px;
  color: var(--el-text-color-secondary);
}

.torrent-actions {
  display: flex;
  align-items: center;
  gap: 4px;
  flex-shrink: 0;
}

/* ================= 手动 RSS 模式 ================= */
.manual-rss-workspace {
  width: 100%;
  height: 100%;
  overflow-y: auto;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 24px;
}

.manual-rss-card {
  width: 100%;
  max-width: 680px;
  background: var(--el-bg-color);
  padding: 28px 32px;
  border-radius: 12px;
  border: 1px solid var(--el-border-color-lighter);
  box-shadow: 0 2px 12px rgba(0, 0, 0, 0.04);
}

.manual-rss-header {
  margin-bottom: 20px;
}

.manual-rss-header h3 {
  margin: 0 0 4px;
  font-size: 18px;
  font-weight: 650;
  color: var(--el-text-color-primary);
}

.manual-rss-header p {
  margin: 0;
  font-size: 13px;
  color: var(--el-text-color-secondary);
}

.manual-title-row {
  width: 100%;
  display: flex;
  gap: 8px;
}

.manual-alert {
  margin: 16px 0 24px;
}

.manual-action-bar {
  display: flex;
  justify-content: flex-end;
}

/* ================= Step 2 配置确认 ================= */
.step-two-container {
  height: 100%;
}

.step-two-scroll {
  height: 100%;
}

.step-two-content-wrap {
  max-width: 860px;
  margin: 0 auto;
  padding: 24px 20px 48px;
}

.step-two-header {
  margin-bottom: 20px;
}

.step-two-header h2 {
  margin: 0 0 6px;
  font-size: 18px;
  font-weight: 650;
  color: var(--el-text-color-primary);
}

.step-two-header p {
  margin: 0;
  font-size: 13px;
  color: var(--el-text-color-secondary);
}

.step-two-form-card {
  background: var(--el-bg-color);
  padding: 24px;
  border-radius: 12px;
  border: 1px solid var(--el-border-color-lighter);
}
</style>
