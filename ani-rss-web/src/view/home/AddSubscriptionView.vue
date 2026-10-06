<template>
  <div class="add-sub-page app-page-layout">
    <!-- 弹窗组件挂载 -->
    <CollectionView ref="collectionRef"/>
    <BgmView ref="bgmRef" @callback="bgmCallback"/>

    <!-- 统一页面头部 -->
    <PageHeaderView
        title="RSS"
        :subtitle="step === 1 ? `${currentSourceLabel} · 共 ${totalAnimeCount} 部番剧` : '第 2 步：确认与微调订阅配置'"
    >
      <template #actions>
        <el-button
            v-if="step === 2"
            icon="Back"
            class="auto-button"
            @click="handleBack">
          重新选番
        </el-button>
      </template>
    </PageHeaderView>

    <div class="add-sub-body app-page-content app-page-padding">
      <!-- ================= STEP 1: 宫格式番剧浏览工作台 ================= -->
      <div v-if="step === 1" class="step-one-container">
        <!-- 工具栏：数据源与过滤操作统一布局 -->
        <div class="subscription-toolbar">
          <div class="subscription-filters">
            <el-select
                v-model="activeSource"
                class="subscription-select source-select"
                placeholder="数据源"
                @change="switchSource">
              <el-option
                  v-for="src in sourceList"
                  :key="src.key"
                  :label="src.label"
                  :value="src.key"
              />
            </el-select>

            <el-input
                v-if="activeSource !== 'manual'"
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
                v-if="activeSource !== 'manual' && seasons.length"
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
                v-if="activeSource !== 'manual'"
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
                v-if="activeSource !== 'manual'"
                class="auto-button"
                icon="Refresh"
                :loading="animeListLoading"
                @click="retryLoad">
              刷新
            </el-button>
            <el-button
                class="auto-button"
                icon="FolderAdd"
                @click="collectionRef?.show">
              添加合集
            </el-button>
          </div>
        </div>

        <!-- 非手动模式：卡片宫格流 -->
        <template v-if="activeSource !== 'manual'">

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
                          @click="openAnimeDialog(anime)"
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
                                  <template v-if="anime.exists">
                                    <el-tag size="small" type="success">已订阅</el-tag>
                                    <el-tag
                                        v-for="sub in anime.subscribedSubgroups"
                                        :key="sub"
                                        size="small"
                                        type="success"
                                        effect="plain"
                                        class="card-subgroup-tag"
                                        :title="`已订阅字幕组: ${sub}`">
                                      {{ sub }}
                                    </el-tag>
                                  </template>
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

      <!-- ================= 字幕组选择弹窗 ================= -->
      <el-dialog
          v-model="dialogVisible"
          :title="selectedAnime ? `选择字幕组 · ${selectedAnime.title}` : '选择字幕组'"
          width="760px"
          align-center
          destroy-on-close
          class="anime-group-dialog"
      >
        <div v-if="selectedAnime" class="group-dialog-body" v-loading="groupsLoading">
          <!-- 弹窗内部番剧横幅卡片 -->
          <div class="dialog-anime-banner">
            <img
                :src="proxyImage(selectedAnime.cover)"
                :alt="selectedAnime.title"
                class="dialog-anime-cover"
            />
            <div class="dialog-anime-info">
              <div class="dialog-anime-title-row">
                <h4 class="dialog-anime-title" :title="selectedAnime.title">{{ selectedAnime.title }}</h4>
                <el-tag v-if="selectedAnime.exists" type="success" size="small">已在订阅中</el-tag>
              </div>
              <div class="dialog-anime-meta">
                <span v-if="selectedAnime.score > 0" class="dialog-score">
                  评分: <strong>{{ Number(selectedAnime.score).toFixed(1) }}</strong>
                </span>
                <div class="dialog-external-links">
                  <el-button
                      v-if="(selectedAnime?.source || activeSource) === 'mikan' && selectedAnime?.rawId"
                      icon="Link"
                      size="small"
                      text
                      bg
                      @click="openExternal(selectedAnime.rawId)">
                    在 Mikan 查看
                  </el-button>
                  <el-button
                      v-else-if="(selectedAnime?.source || activeSource) === 'ani-bt' && selectedAnime?.rawId"
                      icon="Link"
                      size="small"
                      text
                      bg
                      @click="openExternal('https://anibt.net/anime/' + selectedAnime.rawId)">
                    在 AniBT 查看
                  </el-button>
                  <el-button
                      v-else-if="(selectedAnime?.source || activeSource) === 'anime-garden' && selectedAnime?.rawId"
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

          <!-- 字幕组与资源区域 -->
          <div class="dialog-groups-main">
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
                      :class="{
                        'is-active': activeGroupIndex === idx,
                        'is-subscribed': isGroupSubscribed(grp)
                      }"
                      @click="activeGroupIndex = idx">
                    <span class="subgroup-name">
                      <el-icon v-if="isGroupSubscribed(grp)" class="subgroup-check-icon">
                        <Check/>
                      </el-icon>
                      {{ grp.label }}
                    </span>
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
                  <div class="group-title-status-row">
                    <h5 class="current-group-title">{{ selectedGroup.label }}</h5>
                    <el-tag
                        v-if="isGroupSubscribed(selectedGroup)"
                        type="success"
                        size="small"
                        effect="plain"
                        class="group-subscribed-tag">
                      <el-icon><Check/></el-icon>
                      已订阅此字幕组
                    </el-tag>
                  </div>
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
                <div v-if="selectedGroup.items.length" class="torrents-list-wrap">
                  <el-scrollbar max-height="240px">
                    <div class="torrents-list">
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
                  </el-scrollbar>
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
      </el-dialog>
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
import PageHeaderView from "@/view/custom/PageHeaderView.vue";
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
const dialogVisible = ref(false)
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
const weekLabels = ['星期日', '星期一', '星期二', '星期三', '星期四', '星期五', '星期六']
const getTodayWeekLabel = () => weekLabels[new Date().getDay()]

const WEEK_ORDER_MAP = {
  '全部': 0,
  '星期一': 1, '周一': 1,
  '星期二': 2, '周二': 2,
  '星期三': 3, '周三': 3,
  '星期四': 4, '周四': 4,
  '星期五': 5, '周五': 5,
  '星期六': 6, '周六': 6,
  '星期日': 7, '周日': 7, '星期天': 7, '周天': 7,
  '剧场版': 8,
  'OVA': 9
}

const normalizeWeekLabel = (raw) => {
  if (!raw) return '其他'
  const str = String(raw).trim()
  if (str.startsWith('周')) {
    const tail = str.slice(1)
    if (tail === '日' || tail === '天') return '星期日'
    return '星期' + tail
  }
  if (/^[1-7]$/.test(str)) {
    const numMap = {'1': '星期一', '2': '星期二', '3': '星期三', '4': '星期四', '5': '星期五', '6': '星期六', '7': '星期日'}
    return numMap[str] || str
  }
  return str
}

const getWeekSortWeight = (label) => {
  return WEEK_ORDER_MAP[label] !== undefined ? WEEK_ORDER_MAP[label] : 99
}

const seasons = ref([])
const selectedSeason = ref('')
const searchKeyword = ref('')
const activeWeek = ref(getTodayWeekLabel())
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

// 按星期分组的番剧列表，支持星期筛选、关键词搜索与订阅状态过滤，且固定按星期顺序排序
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
  const filtered = list.filter(w => w.items && w.items.length > 0)
  return [...filtered].sort((a, b) => getWeekSortWeight(a.weekLabel) - getWeekSortWeight(b.weekLabel))
})

// 统计当前展示的番剧总数
const totalAnimeCount = computed(() => {
  return groupedAnimeList.value.reduce((acc, w) => acc + (w.items?.length || 0), 0)
})

// 可选星期分类导航胶囊：固定按照“全部、星期一、星期二、星期三、星期四、星期五、星期六、星期日”排序
const availableWeeks = computed(() => {
  const result = [{key: '全部', label: '全部', count: animeList.value.length}]
  const weekItems = []
  for (const w of (rawWeeksData.value || [])) {
    const items = w.items || []
    weekItems.push({
      key: w.weekLabel,
      label: w.weekLabel,
      count: items.length
    })
  }
  weekItems.sort((a, b) => getWeekSortWeight(a.label) - getWeekSortWeight(b.label))
  return [...result, ...weekItems]
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

// 已订阅条目列表缓存
const subscribedList = ref([])

// 查找与特定番剧匹配的全部已订阅记录
const findMatchedSubscriptions = (anime) => {
  if (!subscribedList.value.length || !anime) return []

  const animeTitle = (anime.title || '').trim().toLowerCase()
  const cleanAnimeTitle = animeTitle
      .replace(/\s*\(\d{4}\)$/, '')
      .replace(/\s*第[一二三四五六七八九十\d]+[季部分]$/, '')
      .trim()

  let mikanId = ''
  if (anime.source === 'mikan' && anime.rawId) {
    const match = String(anime.rawId).match(/\/(\d+)(?:\/|\?|$)/)
    if (match) mikanId = match[1]
  }
  const animeBgmId = anime.raw?.bgmId || (anime.source !== 'mikan' ? anime.rawId : '')

  return subscribedList.value.filter(ani => {
    // 1. Mikan bangumiId 匹配
    if (mikanId && ani.url) {
      if (ani.url.includes(`bangumiId=${mikanId}`) || ani.url.includes(`Bangumi/${mikanId}`)) {
        return true
      }
    }

    // 2. BgmId 匹配
    if (animeBgmId && ani.bgmUrl) {
      const aniBgmIdMatch = String(ani.bgmUrl).match(/subject\/(\d+)/)
      if (aniBgmIdMatch && aniBgmIdMatch[1] === String(animeBgmId)) {
        return true
      }
    }

    // 3. 标题匹配
    if (ani.title) {
      const aniTitle = ani.title.trim().toLowerCase()
      const cleanAniTitle = aniTitle
          .replace(/\s*\(\d{4}\)$/, '')
          .replace(/\s*第[一二三四五六七八九十\d]+[季部分]$/, '')
          .trim()
      if (cleanAniTitle && cleanAnimeTitle) {
        if (cleanAniTitle === cleanAnimeTitle || aniTitle === animeTitle || cleanAniTitle.includes(cleanAnimeTitle) || cleanAnimeTitle.includes(cleanAniTitle)) {
          return true
        }
      }
    }

    return false
  })
}

// 获取某番剧已订阅的字幕组名称列表
const getAnimeSubscribedSubgroups = (anime) => {
  const matched = findMatchedSubscriptions(anime)
  const subgroups = matched.map(it => it.subgroup).filter(Boolean)
  return Array.from(new Set(subgroups))
}

// 刷新当前所有已渲染番剧卡片的已订阅字幕组和状态
const updateSubscribedInfoForAnimeList = () => {
  for (const item of animeList.value) {
    const subs = getAnimeSubscribedSubgroups(item)
    item.subscribedSubgroups = subs
    if (subs.length > 0) item.exists = true
  }
  for (const week of rawWeeksData.value) {
    for (const item of (week.items || [])) {
      const subs = getAnimeSubscribedSubgroups(item)
      item.subscribedSubgroups = subs
      if (subs.length > 0) item.exists = true
    }
  }
}

// 加载系统中全部已订阅条目
const loadSubscribedList = async () => {
  try {
    const res = await http.listAni()
    const weekList = res?.data?.weekList || []
    const all = []
    for (const w of weekList) {
      if (w.items && Array.isArray(w.items)) {
        all.push(...w.items)
      }
    }
    subscribedList.value = all
    updateSubscribedInfoForAnimeList()
  } catch (e) {
    console.error('加载订阅列表失败:', e)
  }
}

// 判断弹窗中的特定字幕组是否已订阅
const isGroupSubscribed = (grp) => {
  if (!selectedAnime.value || !grp) return false
  const matched = findMatchedSubscriptions(selectedAnime.value)
  if (!matched.length) return false

  const grpName = (grp.subgroup || grp.label || '').trim().toLowerCase()

  // 尝试从 grp.rss 或 grp.raw 提取 subgroupId（针对 Mikan 源）
  let grpSubgroupId = ''
  const rssUrl = grp.rss || grp.raw?.url || ''
  if (rssUrl) {
    const subgroupIdMatch = String(rssUrl).match(/subgroupid=(\d+)/i)
    if (subgroupIdMatch) grpSubgroupId = subgroupIdMatch[1]
  }

  return matched.some(ani => {
    const aniSub = (ani.subgroup || '').trim().toLowerCase()
    if (aniSub && (aniSub === grpName || grpName.includes(aniSub) || aniSub.includes(grpName))) {
      return true
    }

    if (grpSubgroupId && ani.url && String(ani.url).includes(`subgroupid=${grpSubgroupId}`)) {
      return true
    }

    return false
  })
}

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

  const subs = getAnimeSubscribedSubgroups({ rawId, title, source, raw: item })
  if (subs.length > 0) {
    exists = true
  }

  return {
    id,
    rawId,
    title,
    cover,
    score,
    exists,
    subscribedSubgroups: subs,
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
  const targetSource = src || activeSource.value
  activeSource.value = targetSource
  selectedAnime.value = null
  currentGroups.value = []
  searchKeyword.value = ''
  activeWeek.value = getTodayWeekLabel()
  animeListError.value = ''

  if (targetSource === 'manual') {
    rawWeeksData.value = []
    animeList.value = []
    seasons.value = []
    selectedSeason.value = ''
    return
  }

  const state = sourceState.value[targetSource]
  if (state && state.initialized && state.animeList.length > 0) {
    seasons.value = state.seasons
    selectedSeason.value = state.selectedSeason
    rawWeeksData.value = state.weeks
    animeList.value = state.animeList
    updateSubscribedInfoForAnimeList()
  } else {
    rawWeeksData.value = []
    animeList.value = []
    seasons.value = []
    selectedSeason.value = ''
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

  if (!subscribedList.value.length) {
    await loadSubscribedList()
  }

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
        weekLabel: normalizeWeekLabel(w.weekLabel),
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
        weekLabel: normalizeWeekLabel(w.weekdayLabel || (w.weekday ? `星期${w.weekday}` : '')),
        items: (w.animes || []).map(item => normalizeAnimeItem(item, 'ani-bt'))
      }))
      state.weeks = weeks
      state.animeList = weeks.flatMap(w => w.items)

    } else if (currentSrc === 'anime-garden') {
      const res = await http.animeGardenList('')
      const weeksRaw = Array.isArray(res.data) ? res.data : (res.data?.weeks || [])
      const weeks = weeksRaw.map(w => ({
        weekLabel: normalizeWeekLabel(w.weekLabel),
        items: (w.subjects || []).map(item => normalizeAnimeItem(item, 'anime-garden'))
      }))
      state.seasons = []
      state.selectedSeason = ''
      state.weeks = weeks
      state.animeList = weeks.flatMap(w => w.items)
    }

    state.initialized = true

    // 若加载完成时用户已经切换到了其他源，不覆盖当前视图
    if (activeSource.value !== currentSrc) return

    // 同步到页面视图
    seasons.value = state.seasons
    selectedSeason.value = state.selectedSeason
    rawWeeksData.value = state.weeks
    animeList.value = state.animeList
  } catch (e) {
    if (activeSource.value === currentSrc) {
      const errorMsg = e.message || String(e) || '加载失败'
      animeListError.value = `加载番剧列表失败: ${errorMsg}`
      ElMessage.error(animeListError.value)
    }
  } finally {
    if (activeSource.value === currentSrc) {
      animeListLoading.value = false
    }
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
  activeWeek.value = getTodayWeekLabel()
  loadSourceData('')
}

const retryLoad = () => {
  loadSourceData(searchKeyword.value)
}

// 打开字幕组弹窗并加载字幕组数据
const openAnimeDialog = (anime) => {
  selectedAnime.value = anime
  dialogVisible.value = true
  selectAnime(anime)
}

// 选择番剧，立即获取字幕组
const selectAnime = async (anime) => {
  if (!anime) return
  selectedAnime.value = anime
  activeGroupIndex.value = 0
  selectedRegexOption.value = ''

  const source = anime.source || activeSource.value
  const cacheKey = `${source}_${anime.id}`

  if (groupsCache.value[cacheKey]) {
    currentGroups.value = groupsCache.value[cacheKey]
    setupDefaultRegex()
    return
  }

  groupsLoading.value = true
  currentGroups.value = []

  try {
    let res = null
    if (source === 'mikan') {
      res = await http.mikanGroup(anime.rawId)
    } else if (source === 'ani-bt') {
      res = await http.aniBTGroup(anime.rawId)
    } else if (source === 'anime-garden') {
      res = await http.animeGardenGroup(anime.rawId)
    }

    const rawList = res?.data || []
    const normalized = rawList.map(grp => normalizeGroup(grp, source))
    groupsCache.value[cacheKey] = normalized
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

  const currentSource = selectedAnime.value?.source || activeSource.value
  let type = currentSource
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
    dialogVisible.value = false
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
        loadSubscribedList().then(() => {
          loadSourceData()
        })
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

onMounted(() => {
  loadSubscribedList().then(() => {
    loadSourceData()
  })
})

onActivated(() => {
  // 如果之前是在 Step 2，切回来时自动重置为 Step 1 并刷新状态
  if (step.value === 2) {
    step.value = 1
  }
  loadSubscribedList().then(() => {
    loadSourceData()
  })
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

.source-select {
  width: 140px;
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

.card-subgroup-tag {
  max-width: 140px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
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

/* ================= 字幕组弹窗样式 ================= */
.anime-group-dialog :deep(.el-dialog__body) {
  padding: 16px 20px 24px;
  background: var(--el-bg-color-page);
}

.group-dialog-body {
  display: flex;
  flex-direction: column;
  gap: 14px;
  max-height: 75vh;
  overflow-y: auto;
  padding-right: 2px;
}

.dialog-anime-banner {
  display: flex;
  gap: 14px;
  padding: 12px 14px;
  border-radius: 10px;
  background: var(--el-bg-color);
  border: 1px solid var(--el-border-color-lighter);
  align-items: center;
}

.dialog-anime-cover {
  width: 60px;
  height: 84px;
  border-radius: 6px;
  object-fit: cover;
  flex-shrink: 0;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.08);
}

.dialog-anime-info {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.dialog-anime-title-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
}

.dialog-anime-title {
  margin: 0;
  font-size: 15px;
  font-weight: 700;
  color: var(--el-text-color-primary);
  line-height: 1.3;
}

.dialog-anime-meta {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
  flex-wrap: wrap;
}

.dialog-score {
  font-size: 12px;
  color: var(--el-text-color-secondary);
}

.dialog-score strong {
  color: #fb7299;
  font-size: 13px;
}

.dialog-external-links {
  display: flex;
  gap: 8px;
}

.dialog-groups-main {
  display: flex;
  flex-direction: column;
  gap: 12px;
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

.subgroup-pill-btn.is-subscribed {
  border-color: var(--el-color-success-light-5);
  background: var(--el-color-success-light-9);
}

.subgroup-pill-btn.is-subscribed .subgroup-name {
  color: var(--el-color-success-dark-2);
}

.subgroup-pill-btn.is-subscribed.is-active {
  background: var(--el-color-primary-light-9);
  border-color: var(--el-color-primary);
}

.subgroup-pill-btn.is-subscribed.is-active .subgroup-name {
  color: var(--el-color-primary);
}

.subgroup-check-icon {
  margin-right: 4px;
  color: var(--el-color-success);
  font-weight: bold;
}

.subgroup-name {
  font-size: 13px;
  font-weight: 600;
  display: flex;
  align-items: center;
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

.group-title-status-row {
  display: flex;
  align-items: center;
  gap: 8px;
}

.group-subscribed-tag {
  font-size: 11px;
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
