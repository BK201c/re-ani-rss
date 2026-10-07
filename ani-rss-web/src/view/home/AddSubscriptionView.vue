<template>
  <div class="add-sub-page app-page-layout">
    <!-- 弹窗组件挂载 -->
    <CollectionView ref="collectionRef"/>
    <BgmView ref="bgmRef" @callback="bgmCallback"/>
    <EditAniView ref="editAniRef" @saved="handleEditSaved"/>

    <!-- 多订阅选择弹窗 -->
    <el-dialog
        v-model="multiSubDialogVisible"
        title="选择要编辑的字幕组订阅"
        width="380px"
        center
        append-to-body>
      <div class="multi-sub-choice-list">
        <p class="multi-sub-tip">该番剧已订阅多个字幕组，请选择需要修改的订阅：</p>
        <div
            v-for="subAni in multiSubList"
            :key="subAni.id"
            class="multi-sub-choice-item"
            @click="openSpecificSubEdit(subAni)">
          <div class="multi-sub-name">{{ subAni.subgroup || '未知字幕组' }}</div>
          <el-button size="small" type="primary" text bg icon="Edit">编辑</el-button>
        </div>
      </div>
    </el-dialog>

    <!-- 统一页面头部 -->
    <PageHeaderView
        title="RSS"
        :subtitle="step === 1 ? `${selectedSeason || '季度番剧'} · 共 ${totalAnimeCount} 部番剧` : '第 2 步：确认与微调订阅配置'"
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
        <!-- 工具栏：权威季度番剧与过滤操作 -->
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

            <el-radio-group
                v-model="viewLayoutMode"
                class="layout-switch-group">
              <el-radio-button value="card">
                <el-tooltip content="卡片布局" placement="top">
                  <div class="layout-toggle-item">
                    <el-icon><Grid /></el-icon>
                  </div>
                </el-tooltip>
              </el-radio-button>
              <el-radio-button value="list">
                <el-tooltip content="列表布局" placement="top">
                  <div class="layout-toggle-item">
                    <el-icon><List /></el-icon>
                  </div>
                </el-tooltip>
              </el-radio-button>
            </el-radio-group>
          </div>

          <div class="subscription-actions">
            <el-button
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
            <el-button
                class="auto-button"
                icon="Edit"
                @click="openManualDialog">
              手动 RSS
            </el-button>
          </div>
        </div>

        <!-- 季度番剧：卡片/列表宫格流 -->

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
                    <!-- 卡片布局 -->
                    <div v-if="viewLayoutMode === 'card'" class="grid-container card-grid-container">
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
                                <div v-if="formatAirTime(anime)" class="card-air-time-row">
                                  <el-tooltip
                                      :content="formatAirTime(anime).tooltip"
                                      placement="top"
                                      raw-content
                                  >
                                    <div class="card-air-time-badge">
                                      <el-icon class="air-time-icon"><Timer /></el-icon>
                                      <span class="air-time-text">{{ formatAirTime(anime).display }}</span>
                                      <span v-if="formatAirTime(anime).hasTime" class="air-time-tz-tag">北京</span>
                                    </div>
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
                                <el-button size="small" type="primary" text bg icon="Plus" @click.stop="openAnimeDialog(anime)">
                                  订阅
                                </el-button>
                                <el-button size="small" text bg icon="Edit" @click.stop="handleEditAnime(anime)">
                                  编辑
                                </el-button>
                              </div>
                            </div>
                          </div>
                        </el-card>
                      </div>
                    </div>

                    <!-- 列表布局 -->
                    <div v-else class="grid-container anime-list-container">
                      <div
                          v-for="anime in weekGroup.items"
                          :key="anime.id"
                          class="anime-list-item-wrap"
                          @click="openAnimeDialog(anime)"
                      >
                        <el-card shadow="never" class="anime-row-card-box">
                          <div class="list-row-content">
                            <div class="list-row-image-container">
                              <img
                                  :src="proxyImage(anime.cover)"
                                  :alt="anime.title"
                                  class="list-row-image"
                                  loading="lazy"
                              />
                              <span v-if="anime.score > 0" class="row-score-badge">
                                {{ Number(anime.score).toFixed(1) }}
                              </span>
                            </div>
                            <div class="list-row-info">
                              <div class="list-row-title-row">
                                <el-tooltip :content="anime.title" placement="top">
                                  <el-text class="list-row-title" truncated>
                                    {{ anime.title }}
                                  </el-text>
                                </el-tooltip>
                              </div>
                              <div class="list-row-meta-row">
                                <div v-if="formatAirTime(anime)" class="card-air-time-row mini-air-time">
                                  <el-tooltip
                                      :content="formatAirTime(anime).tooltip"
                                      placement="top"
                                      raw-content
                                  >
                                    <div class="card-air-time-badge">
                                      <el-icon class="air-time-icon"><Timer /></el-icon>
                                      <span class="air-time-text">{{ formatAirTime(anime).display }}</span>
                                      <span v-if="formatAirTime(anime).hasTime" class="air-time-tz-tag">北京</span>
                                    </div>
                                  </el-tooltip>
                                </div>
                                <div class="list-card-tags inline-tags">
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
                            </div>
                            <div class="list-row-actions">
                              <el-button size="small" type="primary" text bg icon="Plus" @click.stop="openAnimeDialog(anime)">
                                订阅
                              </el-button>
                              <el-button size="small" text bg icon="Edit" @click.stop="handleEditAnime(anime)">
                                编辑
                              </el-button>
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
                      <el-button size="small" @click="openManualDialog">手动添加 RSS</el-button>
                    </template>
                  </el-empty>
                </div>
                <div class="list-bottom-spacer"></div>
              </div>
            </el-scrollbar>
          </div>

      <!-- ================= 手动添加 RSS 弹窗 ================= -->
      <el-dialog
          v-model="manualDialogVisible"
          title="手动添加 RSS"
          width="580px"
          align-center
          append-to-body
          destroy-on-close
          class="manual-rss-dialog"
      >
        <div class="manual-dialog-body">
          <p class="manual-dialog-tip">适用于未收录在预设站点中的动漫，或手动定制的第三方 RSS 源</p>
          <el-form class="manual-form" label-position="top">
            <el-form-item label="番剧名称">
              <div class="manual-title-row">
                <el-input
                    v-model="manualForm.title"
                    placeholder="例如：葬送的芙莉莲"
                    clearable
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
                  clearable
              />
            </el-form-item>

            <el-form-item label="RSS 地址" required>
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
        </div>

        <template #footer>
          <div class="dialog-footer">
            <el-button @click="manualDialogVisible = false">取消</el-button>
            <el-button
                type="primary"
                icon="ArrowRight"
                :loading="subscribingLoading"
                @click="submitManualRss">
              下一步：解析并确认配置
            </el-button>
          </div>
        </template>
      </el-dialog>

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
                  Bangumi 评分: <strong>{{ Number(selectedAnime.score).toFixed(1) }}</strong>
                </span>
                <span v-if="formatAirTime(selectedAnime)" class="dialog-air-time">
                  <el-tooltip
                      :content="formatAirTime(selectedAnime).tooltip"
                      placement="top"
                      raw-content
                  >
                    <span class="dialog-air-time-badge">
                      <el-icon><Timer /></el-icon>
                      播出: {{ formatAirTime(selectedAnime).display }} {{ formatAirTime(selectedAnime).hasTime ? '(北京)' : '' }}
                    </span>
                  </el-tooltip>
                </span>
                <div class="dialog-external-links">
                  <el-button
                      v-if="selectedAnime.bgmId"
                      icon="Link"
                      size="small"
                      text
                      bg
                      @click="openExternal('https://bgm.tv/subject/' + selectedAnime.bgmId)">
                    在 Bangumi 查看
                  </el-button>
                  <el-button
                      v-if="selectedAnime.bgmId"
                      icon="Link"
                      size="small"
                      text
                      bg
                      @click="openExternal('https://anibt.net/anime/' + selectedAnime.bgmId)">
                    在 AniBT 查看
                  </el-button>
                  <el-button
                      v-if="activeDialogSource === 'mikan' && mikanUrlsCache[selectedAnime.bgmId]"
                      icon="Link"
                      size="small"
                      text
                      bg
                      @click="openExternal(mikanUrlsCache[selectedAnime.bgmId])">
                    在 Mikan 查看
                  </el-button>
                </div>
              </div>
            </div>
          </div>

          <!-- 弹窗内部下载数据源切换栏 (蜜柑 Mikan / AniBT / 动漫花园) -->
          <div class="dialog-source-tabs-bar">
            <div class="dialog-source-tabs">
              <button
                  v-for="src in dialogSourceList"
                  :key="src.key"
                  type="button"
                  class="dialog-source-tab-btn"
                  :class="{ 'is-active': activeDialogSource === src.key }"
                  @click="switchDialogSource(src.key)">
                <img v-if="src.icon" :src="src.icon" class="dialog-source-tab-icon" :alt="src.label" />
                <span class="dialog-source-tab-label">{{ src.label }}</span>
                <span v-if="getDialogSourceGroupCount(src.key) !== null" class="dialog-source-tab-count">
                  ({{ getDialogSourceGroupCount(src.key) }})
                </span>
              </button>
            </div>
          </div>

          <!-- 字幕组与资源区域 -->
          <div class="dialog-groups-main">
            <div class="section-title-bar">
              <div class="title-with-count">
                <h4>{{ currentDialogSourceLabel }} 字幕组列表</h4>
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
                  <el-scrollbar class="torrents-scroll">
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
                :description="`${currentDialogSourceLabel} 暂未收录该番剧的字幕组资源，可切换上方其他站点`"
                class="empty-groups"
            >
              <template #extra>
                <div class="empty-switch-hints">
                  <el-button
                      v-for="s in dialogSourceList.filter(item => item.key !== activeDialogSource)"
                      :key="s.key"
                      size="small"
                      @click="switchDialogSource(s.key)">
                    切换到 {{ s.label }}
                  </el-button>
                </div>
              </template>
            </el-empty>
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
import {useLocalStorage} from "@vueuse/core";
import {ElMessage} from "element-plus";
import {
  ArrowLeft,
  ArrowRight,
  Back,
  Check,
  Close,
  CopyDocument,
  Download,
  Edit,
  FolderAdd,
  Grid,
  Link,
  List,
  Plus,
  Refresh,
  Search,
  Timer
} from "@element-plus/icons-vue";

import AniView from "@/view/home/AniView.vue";
import EditAniView from "@/view/home/EditAniView.vue";
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
const editAniRef = ref()

// 流程状态
const step = ref(1) // 1: 浏览选番, 2: 确认配置
const manualDialogVisible = ref(false) // 手动输入 RSS 弹窗显隐
const dialogVisible = ref(false)
const filterSubscribeStatus = ref('all') // all, unsubscribed, subscribed
const viewLayoutMode = useLocalStorage('rss-view-layout-mode', 'card') // 'card' | 'list'
const multiSubDialogVisible = ref(false)
const multiSubList = ref([])

const openManualDialog = () => {
  manualDialogVisible.value = true
}

// 弹窗内部支持的 RSS 下载数据源
const dialogSourceList = [
  {key: 'mikan', label: '蜜柑 Mikan', icon: mikanIcon},
  {key: 'ani-bt', label: 'AniBT', icon: aniBTIcon},
  {key: 'anime-garden', label: '动漫花园', icon: animeGardenIcon}
]
const activeDialogSource = ref('mikan') // 弹窗内当前选中的下载源，默认 Mikan

const currentDialogSourceLabel = computed(() => {
  const src = dialogSourceList.find(s => s.key === activeDialogSource.value)
  return src ? src.label : ''
})

// 数据加载与错误状态
const animeListLoading = ref(false)
const searchLoading = ref(false)
const groupsLoading = ref(false)
const subscribingLoading = ref(false)
const animeListError = ref('')

// 星期导航排布
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

// 权威番剧数据状态 (bgm.tv 季度数据源)
const seasons = ref([])
const selectedSeason = ref('')
const searchKeyword = ref('')
const activeWeek = ref(getTodayWeekLabel())
const rawWeeksData = ref([])
const animeList = ref([])

// 弹窗内部番剧与字幕组状态
const selectedAnime = ref(null)
const mikanUrlsCache = ref({}) // bgmId -> Mikan detail URL
const groupsCache = ref({}) // `${sourceKey}_${bgmId}` -> groups
const currentGroups = ref([])
const activeGroupIndex = ref(0)
const selectedRegexOption = ref('')

// 获取指定源已缓存的字幕组数量（用于 tab 徽章展示）
const getDialogSourceGroupCount = (sourceKey) => {
  if (!selectedAnime.value?.bgmId) return null
  const cacheKey = `${sourceKey}_${selectedAnime.value.bgmId}`
  if (groupsCache.value[cacheKey]) {
    return groupsCache.value[cacheKey].length
  }
  return null
}

// 手动 RSS 表单
const manualForm = ref({
  title: '',
  bgmUrl: '',
  url: ''
})

// Step 2 配置数据
const configuredAni = ref(JSON.parse(JSON.stringify(aniData)))

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
      .replace(/\s*[\(（]\d{4}[\)）]$/, '')
      .trim()

  // 1. 提取当前卡片的 Bangumi subject ID
  let animeBgmId = ''
  if (anime.bgmId) {
    animeBgmId = String(anime.bgmId).trim()
  } else if (anime.raw?.bgmId) {
    animeBgmId = String(anime.raw.bgmId).trim()
  } else if (anime.rawId) {
    animeBgmId = String(anime.rawId).trim()
  }

  return subscribedList.value.filter(ani => {
    // 提取已订阅条目的 Bangumi subject ID
    let aniBgmId = ''
    if (ani.bgmUrl) {
      const match = String(ani.bgmUrl).match(/subject\/(\d+)/i)
      if (match) aniBgmId = match[1]
    }

    // 判定规则：
    // A. 如果双方均有权威 bgmId：
    // 若相等则为同一动漫；若不相等则确凿不是同一动漫，禁止继续模糊匹配
    if (animeBgmId && aniBgmId) {
      return animeBgmId === aniBgmId
    }

    // B. 仅当至少一方缺失 bgmId 时，降级到标题严格全等匹配（绝对禁止子串模糊匹配）
    if (!animeBgmId || !aniBgmId) {
      if (ani.title) {
        const aniTitle = ani.title.trim().toLowerCase()
        const cleanAniTitle = aniTitle
            .replace(/\s*[\(（]\d{4}[\)）]$/, '')
            .trim()
        if (cleanAniTitle && cleanAnimeTitle) {
          return cleanAniTitle === cleanAnimeTitle || aniTitle === animeTitle
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
    item.exists = Boolean(item.raw?.exists) || subs.length > 0
  }
  for (const week of rawWeeksData.value) {
    for (const item of (week.items || [])) {
      const subs = getAnimeSubscribedSubgroups(item)
      item.subscribedSubgroups = subs
      item.exists = Boolean(item.raw?.exists) || subs.length > 0
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

// 处理编辑番剧订阅（复制“订阅”页面的“修改订阅”弹窗功能）
const handleEditAnime = (anime) => {
  const matched = findMatchedSubscriptions(anime)
  if (matched.length === 1) {
    editAniRef.value?.show(matched[0])
  } else if (matched.length > 1) {
    multiSubList.value = matched
    multiSubDialogVisible.value = true
  } else {
    // 尚未订阅：基于当前番剧预填初始数据打开修改订阅弹窗
    const bgmId = anime.bgmId || anime.raw?.bgmId || anime.rawId || ''
    const newAni = {
      ...JSON.parse(JSON.stringify(aniData)),
      title: anime.title || '',
      cover: anime.cover || '',
      bgmUrl: bgmId ? `https://bgm.tv/subject/${bgmId}` : '',
      releaseDate: anime.premiereDate || '',
      score: anime.score || 0
    }
    editAniRef.value?.show(newAni)
  }
}

// 针对多字幕组订阅，打开选定字幕组的编辑弹窗
const openSpecificSubEdit = (subAni) => {
  multiSubDialogVisible.value = false
  editAniRef.value?.show(subAni)
}

// 编辑保存成功后的回调
const handleEditSaved = () => {
  loadSubscribedList().then(() => {
    updateSubscribedInfoForAnimeList()
  })
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

// 权威番剧数据规范化 (来自 bgm.tv 季度库)
const normalizeAnimeItem = (item) => {
  const bgmId = String(item.bgmId || '')
  let title = ''
  let primaryTitle = ''
  if (typeof item.title === 'object' && item.title !== null) {
    title = item.title.chinese || item.title.primary || ''
    primaryTitle = item.title.primary || ''
  } else {
    title = item.title || ''
    primaryTitle = item.title || ''
  }

  const cover = item.cover || ''
  const score = item.rating || 0
  const subs = getAnimeSubscribedSubgroups({ rawId: bgmId, bgmId, title, raw: item })
  const exists = Boolean(item.exists) || subs.length > 0

  return {
    id: bgmId,
    bgmId,
    rawId: bgmId,
    title,
    primaryTitle,
    cover,
    score,
    exists,
    subscribedSubgroups: subs,
    airingAt: item.airingAt,
    premiereDate: item.premiereDate,
    scheduleStatus: item.scheduleStatus,
    raw: item
  }
}

// 格式化番剧播出时间（月日时分，转换为北京时间并提供日本时间对照提示）
const formatAirTime = (anime) => {
  if (!anime) return null

  // 1. 若有具体排期时间戳 airingAt（秒级 Unix 时间戳）
  if (anime.airingAt) {
    const ts = Number(anime.airingAt) * 1000
    const d = new Date(ts)
    if (!isNaN(d.getTime())) {
      const getParts = (tz) => {
        const parts = new Intl.DateTimeFormat('zh-CN', {
          timeZone: tz,
          month: '2-digit',
          day: '2-digit',
          hour: '2-digit',
          minute: '2-digit',
          hour12: false
        }).formatToParts(d)
        const get = (t) => parts.find(p => p.type === t)?.value || ''
        return { m: get('month'), d: get('day'), h: get('hour'), min: get('minute') }
      }

      const bj = getParts('Asia/Shanghai')
      const jp = getParts('Asia/Tokyo')

      return {
        hasTime: true,
        display: `${bj.m}-${bj.d} ${bj.h}:${bj.min}`,
        tooltip: `播出时间：<br/>• 北京时间: ${bj.m}月${bj.d}日 ${bj.h}:${bj.min}<br/>• 日本时间: ${jp.m}月${jp.d}日 ${jp.h}:${jp.min} (JST)`
      }
    }
  }

  // 2. 若仅有首播日期 premiereDate (YYYY-MM-DD)
  if (anime.premiereDate) {
    const parts = String(anime.premiereDate).split('-')
    if (parts.length >= 3) {
      const m = parts[1]
      const d = parts[2]
      return {
        hasTime: false,
        display: `${m}-${d} 首播`,
        tooltip: `首播日期: ${parts[0]}年${m}月${d}日 (暂无具体时分)`
      }
    }
  }

  return null
}

// 字幕组规范化
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

// 季度番剧数据 24 小时本地缓存机制 (避免频繁抓取 bgm.tv 数据导致加载过慢)
const SEASON_CACHE_PREFIX = 'ani_rss_season_cache_'
const CACHE_TTL_MS = 24 * 60 * 60 * 1000 // 24小时

const getSeasonCache = (seasonKey) => {
  try {
    const raw = localStorage.getItem(`${SEASON_CACHE_PREFIX}${seasonKey || 'default'}`)
    if (!raw) return null
    const parsed = JSON.parse(raw)
    if (!parsed || !parsed.timestamp || !parsed.data) return null
    if (Date.now() - parsed.timestamp > CACHE_TTL_MS) {
      localStorage.removeItem(`${SEASON_CACHE_PREFIX}${seasonKey || 'default'}`)
      return null
    }
    return parsed
  } catch {
    return null
  }
}

const setSeasonCache = (seasonKey, data) => {
  try {
    if (!data) return
    const payload = {
      timestamp: Date.now(),
      season: seasonKey || '',
      data
    }
    localStorage.setItem(`${SEASON_CACHE_PREFIX}${seasonKey || 'default'}`, JSON.stringify(payload))
    localStorage.setItem(`${SEASON_CACHE_PREFIX}default`, JSON.stringify(payload))
  } catch (e) {
    console.warn('保存番剧缓存失败:', e)
  }
}

const applySeasonData = (data) => {
  const { requestedSeason, availableSeasons, byWeekday } = data || {}

  if (availableSeasons?.length) {
    seasons.value = availableSeasons.map(s => ({
      label: s,
      value: s,
      raw: s
    }))
  }
  selectedSeason.value = requestedSeason || selectedSeason.value || availableSeasons?.[0] || ''

  const weeks = (byWeekday || []).map(w => ({
    weekLabel: normalizeWeekLabel(w.weekdayLabel || (w.weekday ? `星期${w.weekday}` : '')),
    items: (w.animes || []).map(item => normalizeAnimeItem(item))
  }))
  rawWeeksData.value = weeks
  animeList.value = weeks.flatMap(w => w.items)

  updateSubscribedInfoForAnimeList()
}

// 加载权威季度番剧数据 (使用 bgm.tv 季度列表作为唯一权威数据源，未超24小时直接复用缓存)
const loadAuthorityData = async (keyword = '', seasonParam = null, forceRefresh = false) => {
  animeListError.value = ''
  selectedAnime.value = null
  currentGroups.value = []

  if (!subscribedList.value.length) {
    await loadSubscribedList()
  }

  const isSeasonQuery = !keyword
  const targetSeason = keyword ? '' : (seasonParam || selectedSeason.value || '')

  // 1. 若为季度常规加载且非强制刷新：检查是否存在 24 小时内的本地缓存
  if (isSeasonQuery && !forceRefresh) {
    const cached = getSeasonCache(targetSeason)
    if (cached) {
      applySeasonData(cached.data)
      animeListLoading.value = false
      return
    }
  }

  // 2. 缓存不存在、已超24小时或用户主动强制刷新：发起请求获取最新数据
  animeListLoading.value = true
  try {
    const res = await http.aniBT(targetSeason, '', keyword, forceRefresh)
    const data = res.data || {}
    applySeasonData(data)

    // 针对非搜索查询保存 24 小时缓存
    if (isSeasonQuery && data.byWeekday) {
      setSeasonCache(targetSeason || data.requestedSeason, data)
    }
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
  selectedSeason.value = val
  loadAuthorityData('', val, false)
}

// 搜索
const handleSearch = () => {
  searchLoading.value = true
  loadAuthorityData(searchKeyword.value, null, false).finally(() => {
    searchLoading.value = false
  })
}

const handleClearSearch = () => {
  searchKeyword.value = ''
  loadAuthorityData('', selectedSeason.value, false)
}

const handleClearFilters = () => {
  searchKeyword.value = ''
  filterSubscribeStatus.value = 'all'
  activeWeek.value = getTodayWeekLabel()
  loadAuthorityData('', selectedSeason.value, false)
}

// 强制刷新：忽略24小时缓存
const retryLoad = () => {
  loadAuthorityData(searchKeyword.value, selectedSeason.value, true).then(() => {
    ElMessage.success('番剧列表已刷新')
  })
}

// 打开字幕组弹窗（默认使用 蜜柑 Mikan 数据源）
const openAnimeDialog = async (anime) => {
  if (!anime) return
  selectedAnime.value = anime
  dialogVisible.value = true
  activeDialogSource.value = 'mikan' // 默认选择蜜柑 Mikan
  await switchDialogSource('mikan')
}

// 弹窗内切换下载数据源 (Mikan / AniBT / 动漫花园)
const switchDialogSource = async (sourceKey) => {
  activeDialogSource.value = sourceKey
  activeGroupIndex.value = 0
  selectedRegexOption.value = ''

  if (!selectedAnime.value) return

  const bgmId = selectedAnime.value.bgmId
  const cacheKey = `${sourceKey}_${bgmId}`

  if (groupsCache.value[cacheKey]) {
    currentGroups.value = groupsCache.value[cacheKey]
    setupDefaultRegex()
    return
  }

  groupsLoading.value = true
  currentGroups.value = []

  try {
    const groups = await fetchSubgroupsForSource(sourceKey, selectedAnime.value)
    groupsCache.value[cacheKey] = groups
    currentGroups.value = groups
    setupDefaultRegex()
  } catch (e) {
    console.error(`获取 ${getDialogSourceLabel(sourceKey)} 字幕组失败:`, e)
    ElMessage.error(`获取 ${getDialogSourceLabel(sourceKey)} 字幕组失败: ` + (e.message || e))
  } finally {
    groupsLoading.value = false
  }
}

const getDialogSourceLabel = (key) => {
  const item = dialogSourceList.find(s => s.key === key)
  return item ? item.label : key
}

// 为特定下载站点加载字幕组
const fetchSubgroupsForSource = async (sourceKey, anime) => {
  if (!anime) return []

  if (sourceKey === 'mikan') {
    // 蜜柑 Mikan：通过标题搜索匹配获取 Mikan 详情页，并抓取字幕组
    let mikanUrl = mikanUrlsCache.value[anime.bgmId] || ''

    if (!mikanUrl) {
      // 1. 先用清理后的中文标题搜索（去除末尾 (2026) 等年份）
      const cleanChinese = (anime.title || '')
          .replace(/\s*[\(（]\d{4}[\)）]$/, '')
          .trim()

      if (cleanChinese) {
        const res = await http.mikan(cleanChinese, {})
        const items = res?.data?.weeks?.flatMap(w => w.items || []) || []
        if (items.length > 0) {
          const exact = items.find(it => (it.title || '').trim().toLowerCase() === cleanChinese.toLowerCase())
          mikanUrl = exact?.url || items[0].url
        }
      }

      // 2. 若中文名未匹配到，且有不同日文/原名标题，尝试用原名搜索
      if (!mikanUrl && anime.primaryTitle && anime.primaryTitle !== anime.title) {
        const cleanPrimary = anime.primaryTitle
            .replace(/\s*[\(（]\d{4}[\)）]$/, '')
            .trim()
        if (cleanPrimary) {
          const res = await http.mikan(cleanPrimary, {})
          const items = res?.data?.weeks?.flatMap(w => w.items || []) || []
          if (items.length > 0) {
            mikanUrl = items[0].url
          }
        }
      }

      if (mikanUrl) {
        mikanUrlsCache.value[anime.bgmId] = mikanUrl
      }
    }

    if (mikanUrl) {
      const res = await http.mikanGroup(mikanUrl)
      const rawList = res?.data || []
      return rawList.map(grp => normalizeGroup(grp, 'mikan'))
    }
    return []

  } else if (sourceKey === 'ani-bt') {
    // AniBT：直接根据权威 bgmId 获取
    const res = await http.aniBTGroup(anime.bgmId)
    const rawList = res?.data || []
    return rawList.map(grp => normalizeGroup(grp, 'ani-bt'))

  } else if (sourceKey === 'anime-garden') {
    // 动漫花园：直接根据权威 bgmId 获取
    const res = await http.animeGardenGroup(anime.bgmId)
    const rawList = res?.data || []
    return rawList.map(grp => normalizeGroup(grp, 'anime-garden'))
  }

  return []
}

const setupDefaultRegex = () => {
  if (matchedRegexOptions.value.length) {
    selectedRegexOption.value = matchedRegexOptions.value[0].value
  }
}

// 订阅选中的字幕组 -> 解析并进入 Step 2
const subscribeCurrentGroup = async () => {
  if (!selectedGroup.value || !selectedAnime.value) return

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

  const type = activeDialogSource.value
  const bgmUrl = grp.bgmUrl || (selectedAnime.value.bgmId ? `https://bgm.tv/subject/${selectedAnime.value.bgmId}` : '')

  const aniPayload = {
    ...JSON.parse(JSON.stringify(aniData)),
    type,
    url: grp.rss,
    bgmUrl,
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
    manualDialogVisible.value = false
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
          loadAuthorityData()
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
    loadAuthorityData()
  })
})

onActivated(() => {
  if (step.value === 2) {
    step.value = 1
  }
  loadSubscribedList().then(() => {
    loadAuthorityData()
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
  height: 132px;
}

.list-card-image {
  border: 1px solid var(--el-border-color-light);
  border-radius: var(--el-border-radius-small);
  height: 132px;
  width: 92px;
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
  height: 132px;
}

.list-card-info-inner {
  display: flex;
  flex-direction: column;
  gap: 4px;
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

/* 番剧卡片播出时间样式 */
.card-air-time-row {
  display: flex;
  align-items: center;
  margin-top: 1px;
}

.card-air-time-badge {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  padding: 1px 6px;
  background: var(--el-fill-color-light);
  border: 1px solid var(--el-border-color-lighter);
  border-radius: 4px;
  font-size: 11px;
  color: var(--el-text-color-regular);
  cursor: default;
  transition: all 0.2s;
  line-height: 1.4;
}

.card-air-time-badge:hover {
  border-color: var(--el-color-primary-light-5);
  color: var(--el-color-primary);
  background: var(--el-color-primary-light-9);
}

.air-time-icon {
  font-size: 12px;
  color: var(--el-color-primary);
}

.air-time-text {
  font-weight: 500;
  font-variant-numeric: tabular-nums;
}

.air-time-tz-tag {
  font-size: 10px;
  padding: 0 3px;
  border-radius: 2px;
  background: var(--el-color-primary-light-8);
  color: var(--el-color-primary);
  line-height: 1.2;
}

.list-card-tags {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
  margin-top: 2px;
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
  gap: 8px;
  margin-top: auto;
}

/* ================= 布局切换与列表模式样式 ================= */
.layout-switch-group {
  margin-left: 2px;
}

.layout-toggle-item {
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 0 4px;
  height: 100%;
}

.anime-list-container {
  display: flex;
  flex-direction: column;
  gap: 8px;
  width: 100%;
}

.anime-list-item-wrap {
  cursor: pointer;
  width: 100%;
}

.anime-row-card-box {
  border-radius: 8px;
  background: var(--el-bg-color);
  border: 1px solid var(--el-border-color-lighter);
  transition: all 0.2s cubic-bezier(0.4, 0, 0.2, 1);
}

.anime-row-card-box:hover {
  border-color: var(--el-color-primary-light-5);
  box-shadow: 0 2px 10px rgba(0, 0, 0, 0.08);
}

.anime-row-card-box :deep(.el-card__body) {
  padding: 8px 14px;
  box-sizing: border-box;
}

.list-row-content {
  display: flex;
  align-items: center;
  gap: 14px;
  min-height: 56px;
}

.list-row-image-container {
  width: 44px;
  height: 60px;
  flex-shrink: 0;
  position: relative;
  border-radius: 6px;
  overflow: hidden;
  background: var(--el-fill-color-dark);
}

.list-row-image {
  width: 100%;
  height: 100%;
  object-fit: cover;
  display: block;
}

.row-score-badge {
  position: absolute;
  top: 2px;
  right: 2px;
  background: rgba(0, 0, 0, 0.72);
  color: #ffb800;
  font-size: 10px;
  font-weight: 700;
  padding: 1px 3px;
  border-radius: 3px;
  line-height: 1;
}

.list-row-info {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.list-row-title-row {
  display: flex;
  align-items: center;
}

.list-row-title {
  font-size: 14px;
  font-weight: 600;
  color: var(--el-text-color-primary);
}

.list-row-meta-row {
  display: flex;
  align-items: center;
  gap: 10px;
  flex-wrap: wrap;
}

.mini-air-time {
  margin: 0;
}

.inline-tags {
  display: flex;
  align-items: center;
  gap: 6px;
  flex-wrap: wrap;
  margin-top: 0;
}

.list-row-actions {
  flex-shrink: 0;
  display: flex;
  align-items: center;
  gap: 8px;
}

/* ================= 多字幕组选择弹窗样式 ================= */
.multi-sub-choice-list {
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.multi-sub-tip {
  font-size: 13px;
  color: var(--el-text-color-secondary);
  margin-bottom: 4px;
}

.multi-sub-choice-item {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 10px 14px;
  border-radius: 8px;
  background: var(--el-fill-color-light);
  border: 1px solid var(--el-border-color-lighter);
  cursor: pointer;
  transition: all 0.2s ease;
}

.multi-sub-choice-item:hover {
  background: var(--el-fill-color);
  border-color: var(--el-color-primary-light-5);
}

.multi-sub-name {
  font-weight: 600;
  font-size: 13px;
  color: var(--el-text-color-primary);
}

.empty-anime {
  padding: 40px 0;
}

.list-bottom-spacer {
  height: 16px;
}

/* ================= 字幕组弹窗样式 ================= */
.anime-group-dialog :deep(.el-dialog__body) {
  padding: 16px 20px 20px;
  background: var(--el-bg-color-page);
  height: 590px;
  box-sizing: border-box;
  overflow: hidden;
}

.group-dialog-body {
  display: flex;
  flex-direction: column;
  gap: 12px;
  height: 590px;
  min-height: 590px;
  box-sizing: border-box;
  overflow: hidden;
}

.dialog-anime-banner {
  flex-shrink: 0;
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

.dialog-air-time {
  display: inline-flex;
  align-items: center;
}

.dialog-air-time-badge {
  display: inline-flex;
  align-items: center;
  gap: 5px;
  padding: 2px 8px;
  border-radius: 6px;
  background: var(--el-fill-color);
  border: 1px solid var(--el-border-color-lighter);
  font-size: 12px;
  color: var(--el-text-color-regular);
  cursor: default;
  transition: all 0.2s;
}

.dialog-air-time-badge:hover {
  border-color: var(--el-color-primary-light-5);
  color: var(--el-color-primary);
}

.dialog-external-links {
  display: flex;
  gap: 8px;
}

/* 弹窗内数据源切换 Tabs */
.dialog-source-tabs-bar {
  flex-shrink: 0;
  display: flex;
  background: var(--el-fill-color-light);
  padding: 4px;
  border-radius: 10px;
  border: 1px solid var(--el-border-color-lighter);
}

.dialog-source-tabs {
  display: flex;
  gap: 4px;
  width: 100%;
}

.dialog-source-tab-btn {
  flex: 1;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 8px;
  padding: 8px 14px;
  border: none;
  background: transparent;
  border-radius: 8px;
  font-size: 13px;
  font-weight: 500;
  color: var(--el-text-color-regular);
  cursor: pointer;
  transition: all 0.2s ease;
}

.dialog-source-tab-btn:hover {
  background: var(--el-fill-color);
  color: var(--el-text-color-primary);
}

.dialog-source-tab-btn.is-active {
  background: var(--el-bg-color);
  color: var(--el-color-primary);
  font-weight: 600;
  box-shadow: 0 2px 6px rgba(0, 0, 0, 0.06);
}

.dialog-source-tab-icon {
  width: 18px;
  height: 18px;
  border-radius: 4px;
  object-fit: contain;
}

.dialog-source-tab-label {
  line-height: 1;
}

.dialog-source-tab-count {
  font-size: 11px;
  opacity: 0.8;
}

.empty-switch-hints {
  display: flex;
  gap: 8px;
  margin-top: 10px;
}

.dialog-groups-main {
  flex: 1;
  min-height: 0;
  display: flex;
  flex-direction: column;
  gap: 10px;
  overflow: hidden;
}

.section-title-bar {
  flex-shrink: 0;
}

.subgroups-pills-bar {
  flex-shrink: 0;
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
  flex: 1;
  min-height: 0;
  display: flex;
  flex-direction: column;
  border-radius: 12px;
  background: var(--el-bg-color);
  border: 1px solid var(--el-border-color-lighter);
  overflow: hidden;
}

.selected-group-header {
  flex-shrink: 0;
  padding: 10px 14px;
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
  flex: 1;
  min-height: 0;
  display: flex;
  flex-direction: column;
  padding: 10px 14px 12px;
  overflow: hidden;
}

.torrents-header {
  flex-shrink: 0;
  font-size: 12px;
  font-weight: 600;
  color: var(--el-text-color-secondary);
  margin-bottom: 6px;
}

.torrents-list-wrap {
  flex: 1;
  min-height: 0;
  overflow: hidden;
}

.torrents-scroll {
  height: 100%;
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

.empty-groups {
  flex: 1;
  min-height: 0;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  margin: 0;
  padding: 20px 0;
}

/* ================= 手动 RSS 弹窗 ================= */
.manual-dialog-body {
  padding: 4px 0;
}

.manual-dialog-tip {
  margin: 0 0 16px;
  font-size: 13px;
  color: var(--el-text-color-secondary);
  line-height: 1.5;
}

.manual-title-row {
  width: 100%;
  display: flex;
  gap: 8px;
}

.manual-alert {
  margin: 16px 0 8px;
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

<style>
.anime-group-dialog {
  border-radius: 14px;
  overflow: hidden;
}

.anime-group-dialog .el-dialog__body {
  padding: 16px 20px 20px !important;
  background: var(--el-bg-color-page);
  height: 590px !important;
  min-height: 590px !important;
  max-height: 590px !important;
  box-sizing: border-box;
  overflow: hidden;
}
</style>
