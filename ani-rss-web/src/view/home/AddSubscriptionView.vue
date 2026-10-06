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
            <template v-if="step === 1">第 1 步：浏览并选择番剧与字幕组</template>
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

    <!-- 主体区域 -->
    <div class="add-sub-body app-page-content">
      <!-- ================= STEP 1: 左右分栏工作台 ================= -->
      <div v-if="step === 1" class="step-one-workspace">
        <!-- 非手动模式：左右分栏工作台 -->
        <template v-if="activeSource !== 'manual'">
          <!-- 左侧：番剧浏览栏 -->
          <section class="master-panel">
            <!-- 筛选与搜索控制条 -->
            <div class="master-controls">
              <div class="controls-row">
                <el-select
                    v-if="seasons.length"
                    v-model="selectedSeason"
                    class="season-select"
                    placeholder="选择季度"
                    :disabled="animeListLoading"
                    @change="handleSeasonChange">
                  <el-option
                      v-for="s in seasons"
                      :key="s.seasonLabel"
                      :label="s.seasonLabel"
                      :value="s.seasonLabel"
                  />
                </el-select>
                <el-input
                    v-model="searchKeyword"
                    class="search-input"
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
              </div>

              <!-- 星期快速导航 -->
              <div class="week-pills-bar">
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
            </div>

            <!-- 番剧海报流列表 -->
            <div v-loading="animeListLoading" class="anime-list-wrapper">
              <el-scrollbar class="anime-scrollbar">
                <div v-if="filteredAnimeList.length" class="anime-cards-list">
                  <div
                      v-for="anime in filteredAnimeList"
                      :key="anime.id"
                      class="anime-item-card"
                      :class="{ 'is-selected': selectedAnime?.id === anime.id }"
                      @click="selectAnime(anime)">
                    <div class="anime-cover-box">
                      <img
                          :src="proxyImage(anime.cover)"
                          :alt="anime.title"
                          class="anime-cover-img"
                          loading="lazy"
                      />
                      <span v-if="anime.score > 0" class="anime-score-badge">
                        {{ Number(anime.score).toFixed(1) }}
                      </span>
                    </div>
                    <div class="anime-info-box">
                      <div class="anime-item-title" :title="anime.title">
                        {{ anime.title }}
                      </div>
                      <div class="anime-item-tags">
                        <el-tag v-if="anime.exists" size="small" type="success" effect="light">
                          已订阅
                        </el-tag>
                        <el-tag v-else size="small" type="info" effect="plain">
                          未订阅
                        </el-tag>
                      </div>
                    </div>
                  </div>
                </div>
                <el-empty
                    v-else-if="!animeListLoading"
                    description="暂无匹配番剧"
                    class="empty-anime"
                />
              </el-scrollbar>
            </div>
          </section>

          <!-- 右侧：番剧详情与字幕组种子面板 -->
          <section class="detail-panel">
            <template v-if="selectedAnime">
              <el-scrollbar class="detail-scrollbar">
                <div class="detail-content-wrap">
                  <!-- 顶部番剧 Hero Banner -->
                  <div class="anime-hero-card">
                    <img
                        :src="proxyImage(selectedAnime.cover)"
                        :alt="selectedAnime.title"
                        class="hero-cover-img"
                    />
                    <div class="hero-main-info">
                      <div class="hero-title-row">
                        <h2 class="hero-title">{{ selectedAnime.title }}</h2>
                      </div>
                      <div class="hero-meta-row">
                        <span v-if="selectedAnime.score > 0" class="hero-score">
                          评分: <strong>{{ Number(selectedAnime.score).toFixed(1) }}</strong>
                        </span>
                        <el-tag v-if="selectedAnime.exists" type="success" size="small">已在订阅中</el-tag>
                      </div>
                      <div class="hero-actions">
                        <el-button
                            v-if="activeSource === 'mikan' && selectedAnime.rawId"
                            icon="Link"
                            size="small"
                            text
                            bg
                            @click="openExternal(selectedAnime.rawId)">
                          在 Mikan 查看
                        </el-button>
                      </div>
                    </div>
                  </div>

                  <!-- 字幕组与种子资源面板 -->
                  <div v-loading="groupsLoading" class="subgroups-container">
                    <div class="section-title-bar">
                      <div class="title-with-count">
                        <h3>字幕组列表</h3>
                        <span class="sub-count-tag" v-if="currentGroups.length">
                          共 {{ currentGroups.length }} 个字幕组
                        </span>
                      </div>
                    </div>

                    <!-- 字幕组水平标签栏 -->
                    <div v-if="currentGroups.length" class="subgroups-tabs-wrapper">
                      <el-scrollbar class="subgroup-scroll">
                        <div class="subgroups-pills">
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
                          <h4 class="current-group-title">{{ selectedGroup.label }}</h4>
                          <!-- 特征标签 Tags -->
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

                        <!-- 一键订阅核心主按钮 -->
                        <div class="group-header-right">
                          <!-- 如果有多组匹配规则可选 -->
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
              </el-scrollbar>
            </template>

            <!-- 未选择番剧时的占位 -->
            <div v-else class="empty-detail-placeholder">
              <el-empty
                  description="请在左侧选择一部番剧查看字幕组与种子资源"
                  :image-size="120"
              />
            </div>
          </section>
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
import {computed, onMounted, ref} from "vue";
import {useRouter} from "vue-router";
import {ElMessage} from "element-plus";
import {
  ArrowLeft,
  ArrowRight,
  Check,
  CopyDocument,
  Download,
  FolderAdd,
  Link,
  Plus,
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
const activeSource = ref('mikan') // mikan, anibt, anime-garden, manual

const sourceList = [
  {key: 'mikan', label: '蜜柑 Mikan', icon: mikanIcon},
  {key: 'anibt', label: 'AniBT', icon: aniBTIcon},
  {key: 'anime-garden', label: '动漫花园', icon: animeGardenIcon},
  {key: 'manual', label: '手动 RSS', icon: null}
]

// 数据状态
const animeListLoading = ref(false)
const searchLoading = ref(false)
const groupsLoading = ref(false)
const subscribingLoading = ref(false)

const seasons = ref([])
const selectedSeason = ref('')
const searchKeyword = ref('')
const activeWeek = ref('全部')
const rawWeeksData = ref([])

// 番剧与字幕组
const animeList = ref([])
const selectedAnime = ref(null)
const groupsCache = ref({}) // id -> groups
const currentGroups = ref([])
const activeGroupIndex = ref(0)
const selectedRegexOption = ref('')

// 手动 RSS
const manualForm = ref({
  title: '',
  bgmUrl: '',
  url: ''
})

// Step 2 配置数据
const configuredAni = ref(JSON.parse(JSON.stringify(aniData)))

// 可选星期分类
const availableWeeks = computed(() => {
  const result = [{key: '全部', label: '全部', count: animeList.value.length}]
  for (const w of rawWeeksData.value) {
    const items = w.items || w.animes || w.subjects || []
    result.push({
      key: w.weekLabel,
      label: w.weekLabel,
      count: items.length
    })
  }
  return result
})

// 根据当前星期筛选番剧
const filteredAnimeList = computed(() => {
  if (activeWeek.value === '全部') {
    return animeList.value
  }
  const weekGroup = rawWeeksData.value.find(w => w.weekLabel === activeWeek.value)
  if (!weekGroup) return []
  const items = weekGroup.items || weekGroup.animes || weekGroup.subjects || []
  return items.map(item => normalizeAnimeItem(item, activeSource.value))
})

// 当前选中的字幕组对象
const selectedGroup = computed(() => {
  if (!currentGroups.value.length) return null
  return currentGroups.value[activeGroupIndex.value] || null
})

// 当前字幕组可用的过滤规则选项
const matchedRegexOptions = computed(() => {
  if (!selectedGroup.value?.regexList?.length) {
    return []
  }
  const options = selectedGroup.value.regexList.map(itemGroup => {
    if (!itemGroup.length) {
      return {label: '全部资源 (不过滤)', value: JSON.stringify([])}
    }
    const label = itemGroup.map(it => it.label).join(' / ')
    const value = JSON.stringify(itemGroup.map(it => it.regex))
    return {label, value}
  })
  return options
})

// 数据规范化辅助
const normalizeAnimeItem = (item, source) => {
  return {
    id: String(item.url || item.bgmId || item.id),
    rawId: item.url || item.bgmId || item.id,
    title: item.title,
    cover: item.cover,
    score: item.score || item.rating || 0,
    exists: Boolean(item.exists),
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
    magnet: ti.magnet,
    torrent: ti.torrent
  }))
  return {
    label,
    updateDay: group.updateDay || '',
    tags,
    regexList,
    items,
    rss: group.rss || group.url,
    bgmUrl: group.bgmUrl,
    subgroup: group.subgroup || label,
    raw: group,
    source
  }
}

// 切换数据源
const switchSource = (src) => {
  activeSource.value = src
  selectedAnime.value = null
  currentGroups.value = []
  searchKeyword.value = ''
  activeWeek.value = '全部'
  groupsCache.value = {}
  if (src !== 'manual') {
    loadSourceData()
  }
}

// 加载数据源数据
const loadSourceData = async (keyword = '', seasonParam = null) => {
  animeListLoading.value = true
  selectedAnime.value = null
  currentGroups.value = []
  rawWeeksData.value = []
  animeList.value = []

  try {
    if (activeSource.value === 'mikan') {
      const res = await http.mikan(keyword, seasonParam || {})
      const {seasons: sList, weeks} = res.data || {}
      if (sList?.length) {
        seasons.value = sList
        const found = sList.find(s => s.select)
        selectedSeason.value = found ? found.seasonLabel : sList[0].seasonLabel
      }
      rawWeeksData.value = weeks || []
      const flattened = (weeks || []).flatMap(w => w.items || [])
      animeList.value = flattened.map(item => normalizeAnimeItem(item, 'mikan'))
    } else if (activeSource.value === 'anibt') {
      const res = await http.aniBT(seasonParam, '', keyword)
      const {seasons: sList, weeks} = res.data || {}
      if (sList?.length) {
        seasons.value = sList
        const found = sList.find(s => s.select)
        selectedSeason.value = found ? found.seasonLabel : sList[0].seasonLabel
      }
      rawWeeksData.value = weeks || []
      const flattened = (weeks || []).flatMap(w => w.animes || [])
      animeList.value = flattened.map(item => normalizeAnimeItem(item, 'anibt'))
    } else if (activeSource.value === 'anime-garden') {
      const res = await http.animeGardenList('')
      const {weeks} = res.data || {}
      rawWeeksData.value = weeks || []
      const flattened = (weeks || []).flatMap(w => w.subjects || [])
      animeList.value = flattened.map(item => normalizeAnimeItem(item, 'anime-garden'))
    }

    // 默认激活第一个番剧
    if (animeList.value.length > 0) {
      selectAnime(animeList.value[0])
    }
  } catch (e) {
    ElMessage.error('加载番剧列表失败: ' + (e.message || e))
  } finally {
    animeListLoading.value = false
  }
}

// 季度变更
const handleSeasonChange = (val) => {
  const target = seasons.value.find(s => s.seasonLabel === val)
  if (target) {
    loadSourceData('', target)
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

// 选择番剧，立即在右侧获取字幕组
const selectAnime = async (anime) => {
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
    } else if (activeSource.value === 'anibt') {
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

  const aniPayload = {
    ...JSON.parse(JSON.stringify(aniData)),
    type: activeSource.value,
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
  loadSourceData()
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
  overflow: hidden;
  padding: 0;
}

/* ================= Step 1 左右分栏工作台 ================= */
.step-one-workspace {
  height: 100%;
  display: flex;
  overflow: hidden;
}

/* 左侧栏：番剧选择 */
.master-panel {
  width: 400px;
  flex-shrink: 0;
  height: 100%;
  border-right: 1px solid var(--el-border-color-light);
  background: var(--el-bg-color);
  display: flex;
  flex-direction: column;
}

.master-controls {
  padding: 12px 14px 10px;
  border-bottom: 1px solid var(--el-border-color-lighter);
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.controls-row {
  display: flex;
  gap: 8px;
}

.season-select {
  width: 130px;
  flex-shrink: 0;
}

.search-input {
  flex: 1;
}

.week-pills-bar {
  display: flex;
  gap: 4px;
  overflow-x: auto;
  padding-bottom: 2px;
}

.week-pill-btn {
  padding: 4px 8px;
  border: none;
  background: var(--el-fill-color-light);
  border-radius: 4px;
  font-size: 12px;
  color: var(--el-text-color-regular);
  cursor: pointer;
  white-space: nowrap;
  transition: all 0.15s;
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
  font-size: 10px;
  opacity: 0.85;
}

/* 番剧海报卡片流 */
.anime-list-wrapper {
  flex: 1;
  min-height: 0;
}

.anime-scrollbar {
  height: 100%;
}

.anime-cards-list {
  padding: 8px;
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.anime-item-card {
  display: flex;
  gap: 10px;
  padding: 8px;
  border-radius: 8px;
  border: 1px solid transparent;
  background: var(--el-bg-color);
  cursor: pointer;
  transition: all 0.15s ease;
}

.anime-item-card:hover {
  background: var(--el-fill-color-light);
}

.anime-item-card.is-selected {
  border-color: var(--el-color-primary);
  background: var(--el-color-primary-light-9);
}

.anime-cover-box {
  position: relative;
  width: 52px;
  height: 72px;
  flex-shrink: 0;
  border-radius: 6px;
  overflow: hidden;
  background: var(--el-fill-color);
}

.anime-cover-img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}

.anime-score-badge {
  position: absolute;
  top: 2px;
  right: 2px;
  background: rgba(0, 0, 0, 0.72);
  color: #f7ba2a;
  font-size: 10px;
  font-weight: 700;
  padding: 1px 4px;
  border-radius: 4px;
}

.anime-info-box {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
  justify-content: space-between;
}

.anime-item-title {
  font-size: 13px;
  font-weight: 600;
  color: var(--el-text-color-primary);
  line-height: 1.35;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
}

.anime-item-tags {
  display: flex;
  gap: 6px;
  align-items: center;
}

/* 右侧栏：详情与种子 */
.detail-panel {
  flex: 1;
  min-width: 0;
  height: 100%;
  background: var(--el-bg-color-page);
  display: flex;
  flex-direction: column;
}

.detail-scrollbar {
  height: 100%;
}

.detail-content-wrap {
  padding: 16px 20px 40px;
  display: flex;
  flex-direction: column;
  gap: 16px;
}

/* 番剧 Hero 卡片 */
.anime-hero-card {
  display: flex;
  gap: 16px;
  padding: 16px;
  border-radius: 12px;
  background: var(--el-bg-color);
  border: 1px solid var(--el-border-color-lighter);
}

.hero-cover-img {
  width: 76px;
  height: 104px;
  flex-shrink: 0;
  border-radius: 8px;
  object-fit: cover;
  box-shadow: 0 4px 10px rgba(0, 0, 0, 0.1);
}

.hero-main-info {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
  justify-content: space-between;
}

.hero-title {
  margin: 0;
  font-size: 18px;
  font-weight: 700;
  color: var(--el-text-color-primary);
  line-height: 1.3;
}

.hero-meta-row {
  display: flex;
  align-items: center;
  gap: 12px;
  margin: 6px 0;
}

.hero-score {
  font-size: 13px;
  color: var(--el-text-color-secondary);
}

.hero-score strong {
  color: #f7ba2a;
  font-size: 15px;
}

.hero-actions {
  display: flex;
  gap: 8px;
}

/* 字幕组与资源区域 */
.subgroups-container {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.section-title-bar {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.title-with-count {
  display: flex;
  align-items: center;
  gap: 8px;
}

.title-with-count h3 {
  margin: 0;
  font-size: 15px;
  font-weight: 650;
  color: var(--el-text-color-primary);
}

.sub-count-tag {
  font-size: 12px;
  color: var(--el-text-color-secondary);
}

/* 字幕组标签胶囊 */
.subgroups-tabs-wrapper {
  background: var(--el-bg-color);
  padding: 6px 8px;
  border-radius: 10px;
  border: 1px solid var(--el-border-color-lighter);
}

.subgroups-pills {
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

.subgroup-pill-btn.is-active .subgroup-day {
  color: var(--el-color-primary-light-3);
}

/* 选中字幕组卡片 */
.selected-group-card {
  border-radius: 12px;
  background: var(--el-bg-color);
  border: 1px solid var(--el-border-color-lighter);
  overflow: hidden;
}

.selected-group-header {
  padding: 14px 18px;
  border-bottom: 1px solid var(--el-border-color-lighter);
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  background: var(--el-fill-color-lighter);
}

.group-header-left {
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.current-group-title {
  margin: 0;
  font-size: 16px;
  font-weight: 700;
  color: var(--el-text-color-primary);
}

.group-tags {
  display: flex;
  flex-wrap: wrap;
  gap: 4px;
}

.group-header-right {
  display: flex;
  align-items: center;
  gap: 10px;
}

.match-regex-select {
  width: 170px;
}

.quick-subscribe-btn {
  font-weight: 600;
}

/* 种子列表平铺 */
.torrents-stream {
  padding: 14px 18px;
}

.torrents-header {
  margin-bottom: 10px;
}

.torrents-title {
  font-size: 13px;
  font-weight: 600;
  color: var(--el-text-color-secondary);
}

.torrents-list {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.torrent-item-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  padding: 10px 12px;
  border-radius: 8px;
  background: var(--el-fill-color-blank);
  border: 1px solid var(--el-border-color-extra-light);
  transition: all 0.15s ease;
}

.torrent-item-row:hover {
  background: var(--el-fill-color-light);
  border-color: var(--el-border-color-light);
}

.torrent-main {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.torrent-name {
  font-size: 13px;
  color: var(--el-text-color-primary);
  line-height: 1.4;
  word-break: break-all;
}

.torrent-meta {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 11px;
  color: var(--el-text-color-secondary);
}

.torrent-actions {
  display: flex;
  align-items: center;
  gap: 6px;
  flex-shrink: 0;
}

.empty-detail-placeholder {
  flex: 1;
  display: flex;
  align-items: center;
  justify-content: center;
}

/* 手动 RSS 模式 */
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

/* 响应式适配 */
@media (max-width: 900px) {
  .step-one-workspace {
    flex-direction: column;
  }

  .master-panel {
    width: 100%;
    height: 380px;
    border-right: none;
    border-bottom: 1px solid var(--el-border-color-light);
  }

  .detail-panel {
    height: auto;
    flex: 1;
  }
}
</style>
