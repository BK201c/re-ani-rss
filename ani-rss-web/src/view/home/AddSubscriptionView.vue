<template>
  <div class="add-sub-page app-page-layout">
    <!-- 弹窗组件挂载 -->
    <CollectionView ref="collectionRef"/>
    <BgmView ref="bgmRef" @callback="bgmCallback"/>
    <EditAniView ref="editAniRef" @saved="handleEditSaved"/>
    <CoverView ref="coverRef"/>
    <DelAniView ref="delAniRef" @callback="handleDelSaved"/>

    <!-- 确认番剧配置弹窗 (第二步) -->
    <el-dialog
        v-model="configDialogVisible"
        title="确认番剧配置"
        width="min(680px, calc(100vw - 24px))"
        align-center
        center
        destroy-on-close
        append-to-body
        class="config-ani-dialog"
    >
      <div class="config-dialog-content">
        <AniView v-model:ani="configuredAni" @callback="handleSaveConfiguredAni"/>
      </div>
    </el-dialog>

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
          <div class="multi-sub-name-wrap">
            <span class="multi-sub-name">{{ subAni.subgroup || '未知字幕组' }}</span>
            <el-tag size="small" :type="isSubDisabled(subAni) ? 'info' : 'success'" effect="plain">
              {{ isSubDisabled(subAni) ? '已禁用' : '已启用' }}
            </el-tag>
          </div>
          <el-button size="small" type="primary" text bg icon="Edit">编辑</el-button>
        </div>
      </div>
    </el-dialog>

    <!-- 统一页面头部 -->
    <PageHeaderView
        title="RSS"
        :subtitle="`${selectedSeason || '季度番剧'} · 共 ${totalAnimeCount} 部番剧`"
    />

    <div class="add-sub-body app-page-content app-page-padding">
      <!-- 宫格式番剧浏览工作台 -->
      <div class="step-one-container">
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
                :disabled="animeListLoading"
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
              <el-option label="已订阅 · 已启用" value="enabled"/>
              <el-option label="已订阅 · 已禁用" value="disabled"/>
            </el-select>

            <el-radio-group
                v-model="viewLayoutMode"
                class="layout-switch-group">
              <el-radio-button value="card">
                <el-tooltip :show-after="300" content="卡片布局" placement="top">
                  <div class="layout-toggle-item">
                    <el-icon><Grid /></el-icon>
                  </div>
                </el-tooltip>
              </el-radio-button>
              <el-radio-button value="list">
                <el-tooltip :show-after="300" content="列表布局" placement="top">
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
                :type="isManageMode ? 'primary' : 'default'"
                icon="Operation"
                @click="toggleManageMode">
              {{ isManageMode ? '退出管理' : '管理' }}
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
            <el-button
                class="auto-button"
                icon="Files"
                @click="openBatchCartDialog">
              <span>待订阅清单</span>
              <span v-if="batchCart.length" class="cart-btn-count">({{ batchCart.length }})</span>
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
                      <el-button
                          v-if="isManageMode && hasSubscribedInWeek(weekGroup)"
                          size="small"
                          text
                          type="primary"
                          class="week-select-all-btn"
                          @click="toggleSelectWeekManage(weekGroup)">
                        {{ isWeekManageAllSelected(weekGroup) ? '取消全选本周' : '全选本周' }}
                      </el-button>
                    </h2>
                    <!-- 卡片布局 -->
                    <div v-if="viewLayoutMode === 'card'" class="grid-container card-grid-container">
                      <div
                          v-for="anime in weekGroup.items"
                          :key="anime.id"
                          class="anime-grid-card-wrap"
                          :class="{
                            'is-manage-mode': isManageMode,
                            'is-selected': isAnimeManageSelected(anime),
                            'is-disabled': isManageMode && !anime.exists
                          }"
                          @click="handleCardClick(anime)"
                      >
                        <!-- 卡片管理勾选框 -->
                        <div v-if="isManageMode" class="card-manage-check" @click.stop>
                          <el-checkbox
                              :model-value="isAnimeManageSelected(anime)"
                              :disabled="!anime.exists"
                              @change="toggleManageAnime(anime)"
                          />
                        </div>
                        <el-card shadow="never" class="anime-card-box">
                          <div class="list-card-content">
                            <div class="list-card-image-container">
                              <img
                                  :src="proxyImage(anime.cover)"
                                  :alt="anime.title"
                                  class="list-card-image clickable-cover"
                                  loading="lazy"
                                  title="点击更换封面"
                                  @click.stop="handleCoverClick(anime)"
                              />
                              <span v-if="anime.score > 0" class="card-score-badge">
                                {{ Number(anime.score).toFixed(1) }}
                              </span>
                            </div>
                            <div class="list-card-info">
                              <div class="list-card-info-inner">
                                <div class="card-title-row">
                                  <el-tooltip :show-after="300" :content="`${anime.title}`" placement="top">
                                    <el-text class="list-card-title clickable-title" line-clamp="1" truncated @click.stop="openBgmUrl(anime)">
                                      {{ anime.title }}
                                    </el-text>
                                  </el-tooltip>
                                </div>
                                <div class="card-status-row">
                                  <template v-if="anime.exists">
                                    <el-tag size="small" :type="getAnimeTagType(anime)" class="card-status-tag">
                                      {{ getAnimeStatusText(anime) }}
                                    </el-tag>
                                    <el-tag v-if="getAnimeProgress(anime)" size="small" type="warning" class="card-progress-tag">
                                      {{ getAnimeProgress(anime) }}
                                    </el-tag>
                                  </template>
                                  <el-tag v-else size="small" type="info" effect="plain" class="card-status-tag">
                                    未订阅
                                  </el-tag>
                                </div>
                                <div v-if="anime.exists && anime.subscribedSubgroups?.length" class="card-subgroup-row">
                                  <div class="card-subgroup-list">
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
                                  </div>
                                </div>
                              </div>
                              <div class="list-card-bottom-row">
                                <div class="card-bottom-air-time">
                                  <div v-if="formatAirTime(anime)" class="card-air-time-row">
                                    <div class="card-air-time-badge">
                                      <el-icon class="air-time-icon"><Timer /></el-icon>
                                      <span class="air-time-text">{{ formatAirTime(anime).display }}</span>
                                    </div>
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
                          :class="{
                            'is-manage-mode': isManageMode,
                            'is-selected': isAnimeManageSelected(anime),
                            'is-disabled': isManageMode && !anime.exists
                          }"
                          @click="handleCardClick(anime)"
                      >
                        <!-- 列表管理勾选框 -->
                        <div v-if="isManageMode" class="list-row-manage-check" @click.stop>
                          <el-checkbox
                              :model-value="isAnimeManageSelected(anime)"
                              :disabled="!anime.exists"
                              @change="toggleManageAnime(anime)"
                          />
                        </div>
                        <el-card shadow="never" class="anime-row-card-box">
                          <div class="list-row-content">
                            <div class="list-row-image-container">
                              <img
                                  :src="proxyImage(anime.cover)"
                                  :alt="anime.title"
                                  class="list-row-image clickable-cover"
                                  loading="lazy"
                                  title="点击更换封面"
                                  @click.stop="handleCoverClick(anime)"
                              />
                              <span v-if="anime.score > 0" class="row-score-badge">
                                {{ Number(anime.score).toFixed(1) }}
                              </span>
                            </div>
                            <div class="list-row-info">
                              <div class="list-row-title-row">
                                <el-tooltip :show-after="300" :content="`${anime.title} `" placement="top">
                                  <el-text class="list-row-title clickable-title" truncated @click.stop="openBgmUrl(anime)">
                                    {{ anime.title }}
                                  </el-text>
                                </el-tooltip>
                              </div>
                              <div class="list-row-meta-row">
                                <div v-if="formatAirTime(anime)" class="card-air-time-row mini-air-time">
                                  <div class="card-air-time-badge">
                                    <el-icon class="air-time-icon"><Timer /></el-icon>
                                    <span class="air-time-text">{{ formatAirTime(anime).display }}</span>
                                  </div>
                                </div>
                                <div class="list-card-tags inline-tags">
                                  <template v-if="anime.exists">
                                    <el-tag size="small" :type="getAnimeTagType(anime)">
                                      {{ getAnimeStatusText(anime) }}
                                    </el-tag>
                                    <el-tag v-if="getAnimeProgress(anime)" size="small" type="warning" class="card-progress-tag">
                                      {{ getAnimeProgress(anime) }}
                                    </el-tag>
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

                  <!-- 管理模式底部悬浮工具栏 -->
                  <transition name="el-zoom-in-bottom">
                    <div v-if="isManageMode && selectedManageIds.length > 0" class="manage-floating-bar">
                      <div class="manage-floating-info">
                        已选择 <strong>{{ selectedManageIds.length }}</strong> 部番剧
                        <span class="manage-sub-count">({{ selectedSubCount }} 个订阅)</span>
                      </div>
                      <div class="manage-floating-actions">
                        <el-button
                            size="small"
                            type="primary"
                            plain
                            icon="CircleCheck"
                            :loading="manageActionLoading"
                            @click="handleBatchEnable(true)">
                          批量启用
                        </el-button>
                        <el-button
                            size="small"
                            type="warning"
                            plain
                            icon="CircleClose"
                            :loading="manageActionLoading"
                            @click="handleBatchEnable(false)">
                          禁用
                        </el-button>
                        <el-button
                            size="small"
                            type="danger"
                            plain
                            icon="Delete"
                            :loading="manageActionLoading"
                            @click="handleBatchDelete">
                          删除订阅
                        </el-button>
                        <el-button
                            size="small"
                            icon="RefreshRight"
                            :loading="manageActionLoading"
                            @click="handleUpdateTotalEpisode(false)">
                          更新总集数
                        </el-button>
                        <el-button
                            size="small"
                            icon="Refresh"
                            :loading="manageActionLoading"
                            @click="handleUpdateTotalEpisode(true)">
                          强制更新总集数
                        </el-button>
                        <el-button
                            size="small"
                            icon="MagicStick"
                            :loading="manageActionLoading"
                            @click="handleBatchScrape">
                          刮削
                        </el-button>
                        <el-button
                            size="small"
                            text
                            @click="clearManageSelection">
                          取消选择
                        </el-button>
                      </div>
                    </div>
                  </transition>
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

      <!-- ================= 待订阅清单弹窗 (方案 B: 批量订阅) ================= -->
      <el-dialog
          v-model="batchCartDialogVisible"
          title="待订阅清单"
          width="720px"
          align-center
          append-to-body
          :close-on-click-modal="!batchExecuting"
          :close-on-press-escape="!batchExecuting"
          :show-close="!batchExecuting"
          class="batch-cart-dialog"
      >
        <div v-if="!batchExecuting" class="batch-cart-content">
          <div class="batch-cart-header">
            <span class="batch-cart-tip">
              当前清单中共有 <strong>{{ batchCart.length }}</strong> 部待订阅番剧：
            </span>
            <el-button
                v-if="batchCart.length"
                type="danger"
                text
                size="small"
                icon="Delete"
                @click="clearBatchCart"
            >
              清空清单
            </el-button>
          </div>

          <div v-if="batchCart.length" class="batch-cart-list-wrap">
            <el-scrollbar max-height="420px">
              <div class="batch-cart-list">
                <div
                    v-for="(item, index) in batchCart"
                    :key="`${item.animeId}_${item.source}_${item.groupLabel}`"
                    class="batch-cart-item-row"
                >
                  <img :src="proxyImage(item.cover)" class="batch-cart-item-cover" />
                  <div class="batch-cart-item-info">
                    <div class="batch-cart-title clickable-title" :title="`${item.animeTitle} `" @click.stop="openBgmUrl(item)">{{ item.animeTitle }}</div>
                    <div class="batch-cart-meta">
                      <el-tag size="small" type="primary" effect="plain">{{ item.sourceLabel }}</el-tag>
                      <el-tag size="small" type="success" effect="plain">{{ item.groupLabel }}</el-tag>
                    </div>
                  </div>
                  <el-button
                      type="danger"
                      text
                      bg
                      size="small"
                      icon="Close"
                      title="从清单中移除"
                      @click="removeBatchCartItem(index)"
                  />
                </div>
              </div>
            </el-scrollbar>
          </div>

          <div v-else class="batch-cart-empty">
            <el-empty description="待订阅清单为空">
              <template #extra>
                <span class="empty-tip">在番剧卡片中点击「订阅」，选择站点与字幕组后点击「加入待订阅」即可添加到清单</span>
              </template>
            </el-empty>
          </div>
        </div>

        <!-- 批量执行进度 -->
        <div v-else class="batch-executing-wrap">
          <div class="batch-executing-title">正在批量添加订阅</div>
          <el-progress
              :percentage="Math.round((batchProgress.current / batchProgress.total) * 100)"
              :stroke-width="14"
              striped
              striped-flow
          />
          <div class="batch-executing-info">
            <span>{{ batchProgress.current }} / {{ batchProgress.total }}</span>
            <span class="batch-executing-curr">正在订阅：{{ batchProgress.currentTitle }} ({{ batchProgress.currentGroup }})</span>
          </div>
        </div>

        <template #footer>
          <div v-if="!batchExecuting" class="dialog-footer">
            <el-button @click="batchCartDialogVisible = false">关闭</el-button>
            <el-button
                type="primary"
                icon="Check"
                :disabled="!batchCart.length"
                @click="executeBatchCartSubscribe"
            >
              一键全部订阅 ({{ batchCart.length }})
            </el-button>
          </div>
        </template>
      </el-dialog>

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
        <div v-if="selectedAnime" class="group-dialog-body">
          <!-- 1. 紧凑番剧头部信息 (去除多层卡片框与厚重边距) -->
          <div class="anime-dialog-header">
            <img
                :src="proxyImage(selectedAnime.cover)"
                :alt="selectedAnime.title"
                class="anime-header-cover"
            />
            <div class="anime-header-detail">
              <div class="anime-header-title-row">
                <span class="anime-header-title clickable-title" :title="`${selectedAnime.title} `" @click.stop="openBgmUrl(selectedAnime)">{{ selectedAnime.title }}</span>
                <el-tag v-if="selectedAnime.exists" type="success" size="small">已在订阅中</el-tag>
              </div>
              <div class="anime-header-meta-row">
                <span v-if="selectedAnime.score > 0" class="anime-header-score">
                  Bangumi 评分: <strong>{{ Number(selectedAnime.score).toFixed(1) }}</strong>
                </span>
                <span v-if="formatAirTime(selectedAnime)" class="anime-header-air-time">
                  <el-tooltip :show-after="300"
                      :content="formatAirTime(selectedAnime).tooltip"
                      placement="top"
                      raw-content
                  >
                    <span class="anime-header-time-pill">
                      <el-icon><Timer /></el-icon>
                      播出: {{ formatAirTime(selectedAnime).display }} {{ formatAirTime(selectedAnime).hasTime ? '(北京)' : '' }}
                    </span>
                  </el-tooltip>
                </span>
                <div class="anime-header-links">
                  <el-button
                      v-if="selectedAnime.bgmId"
                      icon="Link"
                      size="small"
                      text
                      bg
                      @click="openExternal('https://bgm.tv/subject/' + selectedAnime.bgmId)">
                    Bangumi
                  </el-button>
                  <el-button
                      v-if="selectedAnime.bgmId"
                      icon="Link"
                      size="small"
                      text
                      bg
                      @click="openExternal('https://anibt.net/anime/' + selectedAnime.bgmId)">
                    AniBT
                  </el-button>
                  <el-button
                      v-if="activeDialogSource === 'mikan' && mikanUrlsCache[selectedAnime.bgmId]"
                      icon="Link"
                      size="small"
                      text
                      bg
                      @click="openExternal(mikanUrlsCache[selectedAnime.bgmId])">
                    Mikan
                  </el-button>
                </div>
              </div>
            </div>
          </div>

          <!-- 2. 数据源切换栏 (扁平 Segmented 控制条，去除厚框) -->
          <div class="anime-dialog-sources-bar">
            <div class="source-segmented-group">
              <button
                  v-for="src in dialogSourceList"
                  :key="src.key"
                  type="button"
                  class="source-segmented-btn"
                  :class="{ 'is-active': activeDialogSource === src.key }"
                  @click="switchDialogSource(src.key)">
                <img v-if="src.icon" :src="src.icon" class="source-btn-icon" :alt="src.label" />
                <span>{{ src.label }}</span>
                <span v-if="getDialogSourceGroupCount(src.key) !== null" class="source-btn-count">
                  ({{ getDialogSourceGroupCount(src.key) }})
                </span>
              </button>
            </div>
          </div>

          <!-- 3. 字幕组与资源区域 (局部 loading，保留头部与数据源清晰可见) -->
          <div class="dialog-groups-area" v-loading="groupsLoading">
            <!-- 字幕组列表水平滚动药丸 (紧凑胶囊流，去除大边框长条盒子) -->
            <div v-if="currentGroups.length" class="subgroups-flow-wrapper">
            <el-scrollbar class="subgroups-flow-scroll">
              <div class="subgroups-pills-row">
                <button
                    v-for="(grp, idx) in currentGroups"
                    :key="idx"
                    type="button"
                    class="subgroup-capsule"
                    :class="{
                      'is-active': activeGroupIndex === idx,
                      'is-subscribed': isGroupSubscribed(grp)
                    }"
                    @click="activeGroupIndex = idx">
                  <el-icon v-if="isGroupSubscribed(grp)" class="subgroup-check-icon">
                    <Check/>
                  </el-icon>
                  <span class="subgroup-name">{{ grp.label }}</span>
                  <span v-if="grp.updateDay" class="subgroup-day">{{ grp.updateDay }}</span>
                </button>
              </div>
            </el-scrollbar>
          </div>

          <!-- 4. 选中字幕组控制栏与种子列表 (一体化主面板，消灭双重卡片嵌套) -->
          <div v-if="selectedGroup" class="selected-subgroup-panel">
            <div class="subgroup-action-toolbar">
              <div class="subgroup-toolbar-left">
                <span class="subgroup-curr-title">{{ selectedGroup.label }}</span>
                <el-tag
                    v-if="isGroupSubscribed(selectedGroup)"
                    type="success"
                    size="small"
                    effect="plain"
                    class="group-subscribed-tag">
                  <el-icon><Check/></el-icon> 已订阅
                </el-tag>
                <div v-if="selectedGroup.tags && selectedGroup.tags.length" class="subgroup-tags-inline">
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

              <div class="subgroup-toolbar-right">
                <el-select
                    v-if="matchedRegexOptions.length > 1"
                    v-model="selectedRegexOption"
                    class="match-regex-select"
                    size="small"
                    placeholder="选择画质规则">
                  <el-option
                      v-for="(opt, oi) in matchedRegexOptions"
                      :key="oi"
                      :label="opt.label"
                      :value="opt.value"
                  />
                </el-select>

                <el-button
                    type="warning"
                    plain
                    size="small"
                    icon="FolderAdd"
                    class="add-cart-btn"
                    @click="addToBatchCart">
                  加入待订阅
                </el-button>

                <el-button
                    type="primary"
                    size="small"
                    icon="Plus"
                    class="quick-subscribe-btn"
                    :loading="subscribingLoading"
                    @click="subscribeCurrentGroup">
                  订阅此字幕组
                </el-button>
              </div>
            </div>

            <!-- 种子资源列表 (扁平极简列表，无小卡片框嵌套) -->
            <div class="subgroup-torrents-area">
              <div class="torrents-area-header">
                <span class="torrents-count-hint">最新发布种子 ({{ selectedGroup.items.length }})</span>
              </div>
              <div v-if="selectedGroup.items.length" class="torrents-list-container">
                <el-scrollbar class="torrents-scroll">
                  <div class="torrents-flat-list">
                    <div
                        v-for="(t, ti) in selectedGroup.items"
                        :key="ti"
                        class="torrent-flat-item">
                      <div class="torrent-flat-info">
                        <span class="torrent-flat-title" :title="t.title">{{ t.title }}</span>
                        <div class="torrent-flat-meta">
                          <span>{{ t.size }}</span>
                          <span class="meta-dot">·</span>
                          <span>{{ t.date }}</span>
                        </div>
                      </div>
                      <div class="torrent-flat-actions">
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

          <!-- 空字幕组状态 -->
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
    </div>
  </div>
</template>

<script setup>
import {computed, onActivated, onMounted, onUnmounted, ref, watch} from "vue";
import {useRouter} from "vue-router";
import {useLocalStorage} from "@vueuse/core";
import {ElMessage} from "element-plus";
import {
  ArrowLeft,
  ArrowRight,
  Back,
  Check,
  CircleCheck,
  CircleClose,
  Close,
  CopyDocument,
  Delete,
  Download,
  Edit,
  Files,
  FolderAdd,
  Grid,
  Link,
  List,
  MagicStick,
  Operation,
  Plus,
  Refresh,
  RefreshRight,
  Search,
  Timer
} from "@element-plus/icons-vue";

import AniView from "@/view/home/AniView.vue";
import EditAniView from "@/view/home/EditAniView.vue";
import CoverView from "@/view/home/CoverView.vue";
import DelAniView from "@/view/home/DelAniView.vue";
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
const coverRef = ref()
const delAniRef = ref()

// 流程状态
const configDialogVisible = ref(false) // 确认番剧配置弹窗显隐
const manualDialogVisible = ref(false) // 手动输入 RSS 弹窗显隐
const dialogVisible = ref(false)
const filterSubscribeStatus = ref('all') // all, unsubscribed, subscribed, enabled, disabled
const viewLayoutMode = useLocalStorage('rss-view-layout-mode', 'card') // 'card' | 'list'
const multiSubDialogVisible = ref(false)
const multiSubList = ref([])

// 管理模式 (批量操作)
const isManageMode = ref(false)
const selectedManageIds = ref([])
const manageActionLoading = ref(false)

const openManualDialog = () => {
  manualDialogVisible.value = true
}

// 待订阅清单 (方案 B: 批量订阅)
const batchCart = useLocalStorage('ani_rss_batch_cart', [])
const batchCartDialogVisible = ref(false)
const batchExecuting = ref(false)
const batchProgress = ref({ current: 0, total: 0, currentTitle: '', currentGroup: '' })

const openBatchCartDialog = () => {
  batchCartDialogVisible.value = true
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
const selectedRegexOption = ref(JSON.stringify([]))

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

// 已订阅条目列表缓存
const subscribedList = ref([])
const isSubscribedListLoaded = ref(false)

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

// 获取番剧当前下载的集数进度 (例如: "3 / 12" 或 "1 / *")
const getAnimeProgress = (anime) => {
  if (!anime?.exists) return ''
  const matched = findMatchedSubscriptions(anime)
  if (!matched.length) return ''
  let best = matched[0]
  for (const item of matched) {
    if ((item.currentEpisodeNumber ?? 0) > (best.currentEpisodeNumber ?? 0)) {
      best = item
    }
  }
  const curr = best.currentEpisodeNumber ?? 0
  const total = best.totalEpisodeNumber || anime.episodes || '*'
  return `${curr} / ${total}`
}

// 判断单个订阅是否处于禁用状态 (兼容后端实体属性 enable 与前端属性 enabled)
const isSubDisabled = (s) => s?.enable === false || s?.enabled === false

// 获取番剧订阅状态文字
const getAnimeStatusText = (anime) => {
  if (!anime?.exists) return '未订阅'
  const matched = findMatchedSubscriptions(anime)
  if (!matched.length) {
    return isSubscribedListLoaded.value ? '未订阅' : '已订阅'
  }
  const allDisabled = matched.every(isSubDisabled)
  return allDisabled ? '已禁用' : '已启用'
}

// 获取番剧订阅状态标签类型
const getAnimeTagType = (anime) => {
  if (!anime?.exists) return 'info'
  const matched = findMatchedSubscriptions(anime)
  if (!matched.length) {
    return isSubscribedListLoaded.value ? 'info' : 'success'
  }
  const allDisabled = matched.every(isSubDisabled)
  return allDisabled ? 'info' : 'success'
}

// 刷新当前所有已渲染番剧卡片的已订阅字幕组和状态
const updateSubscribedInfoForAnimeList = () => {
  for (const item of animeList.value) {
    const matched = findMatchedSubscriptions(item)
    const subs = matched.map(it => it.subgroup).filter(Boolean)
    item.subscribedSubgroups = Array.from(new Set(subs))
    item.exists = isSubscribedListLoaded.value ? (matched.length > 0) : (matched.length > 0 || Boolean(item.raw?.exists))
    if (item.raw) {
      item.raw.exists = item.exists
    }
  }
  for (const week of rawWeeksData.value) {
    for (const item of (week.items || [])) {
      const matched = findMatchedSubscriptions(item)
      const subs = matched.map(it => it.subgroup).filter(Boolean)
      item.subscribedSubgroups = Array.from(new Set(subs))
      item.exists = isSubscribedListLoaded.value ? (matched.length > 0) : (matched.length > 0 || Boolean(item.raw?.exists))
      if (item.raw) {
        item.raw.exists = item.exists
      }
    }
  }
  syncCurrentSeasonCache()
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
    isSubscribedListLoaded.value = true
    updateSubscribedInfoForAnimeList()
  } catch (e) {
    console.error('加载订阅列表失败:', e)
  }
}

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
  } else if (filterSubscribeStatus.value === 'enabled') {
    list = list.map(w => ({
      ...w,
      items: (w.items || []).filter(it => {
        if (!it.exists) return false
        const matched = findMatchedSubscriptions(it)
        return matched.length > 0 && matched.some(s => !isSubDisabled(s))
      })
    }))
  } else if (filterSubscribeStatus.value === 'disabled') {
    list = list.map(w => ({
      ...w,
      items: (w.items || []).filter(it => {
        if (!it.exists) return false
        const matched = findMatchedSubscriptions(it)
        return matched.length > 0 && matched.every(isSubDisabled)
      })
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

// 当前字幕组可用的过滤规则选项（默认“全部资源 (不过滤)”排在第一位）
const matchedRegexOptions = computed(() => {
  if (!selectedGroup.value?.regexList) {
    return [{label: '全部资源 (不过滤)', value: JSON.stringify([])}]
  }
  const validList = selectedGroup.value.regexList.filter(itemGroup => itemGroup && itemGroup.length > 0)
  const allOption = {label: '全部资源 (不过滤)', value: JSON.stringify([])}
  const otherOptions = validList.map(itemGroup => {
    const label = itemGroup.map(it => it.label).join(' / ')
    const value = JSON.stringify(itemGroup.map(it => it.regex))
    return {label, value}
  })
  return [allOption, ...otherOptions]
})

// 点击封面弹出更换封面弹窗 (需求2)
const handleCoverClick = (anime) => {
  if (!anime) return
  if (isManageMode.value) {
    handleCardClick(anime)
    return
  }
  const matched = findMatchedSubscriptions(anime)
  if (matched.length > 0) {
    coverRef.value?.show(matched[0])
  } else {
    ElMessage.info('未订阅的番剧暂无本地记录，无法更换封面')
  }
}

// 点击番剧标题在新标签页打开 bgm.tv 详情页
const openBgmUrl = (anime) => {
  if (!anime) return
  if (isManageMode.value) {
    handleCardClick(anime)
    return
  }
  const bgmId = anime.bgmId || anime.rawId || anime.id || anime.animeId
  if (bgmId && /^\d+$/.test(String(bgmId))) {
    window.open(`https://bgm.tv/subject/${bgmId}`, '_blank', 'noopener')
    return
  }
  if (anime.bgmUrl && anime.bgmUrl.startsWith('http')) {
    window.open(anime.bgmUrl, '_blank', 'noopener')
    return
  }
  const rawTitle = anime.title || anime.animeTitle || ''
  if (rawTitle) {
    let title = rawTitle.replace(/ ?\((19|20)\d{2}\)/g, '').trim()
    title = title.replace(/ ?\[tmdbid=(\d+)]/g, '').trim()
    window.open(`https://bgm.tv/subject_search/${encodeURIComponent(title)}?cat=2`, '_blank', 'noopener')
  }
}

// 点击卡片或行 (需求2: 非管理模式下不触发任何弹窗; 管理模式下切换选择)
const handleCardClick = (anime) => {
  if (isManageMode.value) {
    if (anime?.exists) {
      toggleManageAnime(anime)
    }
  }
}

// 切换管理模式 (需求4) - 保持当前视图模式，不再强制切换到列表
const toggleManageMode = () => {
  isManageMode.value = !isManageMode.value
  selectedManageIds.value = []
}

// 判断某番剧是否在管理多选中
const isAnimeManageSelected = (anime) => {
  if (!anime) return false
  const animeId = anime.bgmId || anime.id
  return selectedManageIds.value.includes(animeId)
}

// 切换单部番剧管理勾选
const toggleManageAnime = (anime) => {
  if (!anime?.exists) return
  const animeId = anime.bgmId || anime.id
  const idx = selectedManageIds.value.indexOf(animeId)
  if (idx > -1) {
    selectedManageIds.value.splice(idx, 1)
  } else {
    selectedManageIds.value.push(animeId)
  }
}

// 判断某周内是否有已订阅的番剧
const hasSubscribedInWeek = (weekGroup) => {
  return (weekGroup.items || []).some(it => it.exists)
}

// 判断某周内所有已订阅番剧是否已全部勾选
const isWeekManageAllSelected = (weekGroup) => {
  const subscribedItems = (weekGroup.items || []).filter(it => it.exists)
  if (!subscribedItems.length) return false
  return subscribedItems.every(it => isAnimeManageSelected(it))
}

// 全选/取消全选某周内所有已订阅番剧
const toggleSelectWeekManage = (weekGroup) => {
  const subscribedItems = (weekGroup.items || []).filter(it => it.exists)
  if (!subscribedItems.length) return
  const allSelected = isWeekManageAllSelected(weekGroup)
  for (const it of subscribedItems) {
    const animeId = it.bgmId || it.id
    const idx = selectedManageIds.value.indexOf(animeId)
    if (allSelected) {
      if (idx > -1) selectedManageIds.value.splice(idx, 1)
    } else {
      if (idx === -1) selectedManageIds.value.push(animeId)
    }
  }
}

// 清空当前管理选择
const clearManageSelection = () => {
  selectedManageIds.value = []
}

// 获取选中的所有已订阅实体条目
const getSelectedSubscriptionItems = () => {
  if (!selectedManageIds.value.length) return []
  const result = []
  for (const week of rawWeeksData.value || []) {
    for (const anime of (week.items || [])) {
      const animeId = anime.bgmId || anime.id
      if (selectedManageIds.value.includes(animeId) && anime.exists) {
        const matched = findMatchedSubscriptions(anime)
        result.push(...matched)
      }
    }
  }
  const map = new Map()
  for (const sub of result) {
    if (sub.id && !map.has(sub.id)) {
      map.set(sub.id, sub)
    }
  }
  return Array.from(map.values())
}

// 统计选中的订阅总数
const selectedSubCount = computed(() => {
  return getSelectedSubscriptionItems().length
})

// 获取选中的订阅 ID 数组
const getSelectedSubIds = () => {
  return getSelectedSubscriptionItems().map(s => s.id)
}

// 批量 启用 / 禁用
const handleBatchEnable = async (enable) => {
  const ids = getSelectedSubIds()
  if (!ids.length) {
    ElMessage.warning('未选择已订阅的番剧')
    return
  }
  manageActionLoading.value = true
  try {
    const res = await http.batchEnable(enable, ids)
    ElMessage.success(res?.message || (enable ? '批量启用成功' : '批量禁用成功'))
    window.$reLoadList?.()
    await loadSubscribedList()
  } catch (e) {
    ElMessage.error((enable ? '批量启用失败: ' : '批量禁用失败: ') + (e.message || e))
  } finally {
    manageActionLoading.value = false
  }
}

// 批量删除订阅（调 DelAniView 弹窗，支持联动删除本地文件）
const handleBatchDelete = () => {
  const items = getSelectedSubscriptionItems()
  if (!items.length) {
    ElMessage.warning('未选择已订阅的番剧')
    return
  }
  delAniRef.value?.show(items)
}

// 删除订阅完成回调
const handleDelSaved = async () => {
  selectedManageIds.value = []
  await loadSubscribedList()
  updateSubscribedInfoForAnimeList()
  window.$reLoadList?.()
}

// 更新总集数 / 强制更新总集数
const handleUpdateTotalEpisode = async (force) => {
  const ids = getSelectedSubIds()
  if (!ids.length) {
    ElMessage.warning('未选择已订阅的番剧')
    return
  }
  manageActionLoading.value = true
  try {
    const res = await http.updateTotalEpisodeNumber(force, ids)
    ElMessage.success(res?.message || (force ? '已触发强制更新总集数' : '已触发更新总集数'))
    window.$reLoadList?.()
    await loadSubscribedList()
    setTimeout(() => {
      loadSubscribedList()
    }, 1500)
  } catch (e) {
    ElMessage.error('更新总集数失败: ' + (e.message || e))
  } finally {
    manageActionLoading.value = false
  }
}

// 批量刮削
const handleBatchScrape = async () => {
  const ids = getSelectedSubIds()
  if (!ids.length) {
    ElMessage.warning('未选择已订阅的番剧')
    return
  }
  manageActionLoading.value = true
  try {
    const res = await http.batchScrape(false, ids)
    ElMessage.success(res?.message || '已触发批量刮削任务')
    window.$reLoadList?.()
    await loadSubscribedList()
  } catch (e) {
    ElMessage.error('批量刮削失败: ' + (e.message || e))
  } finally {
    manageActionLoading.value = false
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
  const matched = findMatchedSubscriptions({ rawId: bgmId, bgmId, title, raw: item })
  const subs = matched.map(it => it.subgroup).filter(Boolean)
  const exists = isSubscribedListLoaded.value ? (matched.length > 0) : (matched.length > 0 || Boolean(item.exists))

  return {
    id: bgmId,
    bgmId,
    rawId: bgmId,
    title,
    primaryTitle,
    cover,
    score,
    exists,
    subscribedSubgroups: Array.from(new Set(subs)),
    airingAt: item.airingAt,
    premiereDate: item.premiereDate,
    scheduleStatus: item.scheduleStatus,
    episodes: item.episodes || 0,
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
        display: `${bj.h}:${bj.min} ${bj.m}-${bj.d}`,
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

// 同步当前季度本地缓存中的订阅存在状态
const syncCurrentSeasonCache = () => {
  try {
    const seasonKey = selectedSeason.value || ''
    const currentCache = getSeasonCache(seasonKey)
    if (currentCache?.data?.byWeekday) {
      let changed = false
      for (const week of currentCache.data.byWeekday) {
        for (const anime of (week.animes || [])) {
          const bgmId = String(anime.bgmId || '')
          let title = ''
          if (typeof anime.title === 'object' && anime.title !== null) {
            title = anime.title.chinese || anime.title.primary || ''
          } else {
            title = anime.title || ''
          }
          const matched = findMatchedSubscriptions({ rawId: bgmId, bgmId, title, raw: anime })
          const newExists = matched.length > 0
          if (anime.exists !== newExists) {
            anime.exists = newExists
            changed = true
          }
        }
      }
      if (changed) {
        setSeasonCache(seasonKey, currentCache.data)
      }
    }
  } catch (e) {
    console.warn('同步本地缓存订阅状态失败:', e)
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

  if (!isSubscribedListLoaded.value) {
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
  searchKeyword.value = ''
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
  selectedRegexOption.value = JSON.stringify([])
  await switchDialogSource('mikan')
}

// 弹窗内切换下载数据源 (Mikan / AniBT / 动漫花园)
const switchDialogSource = async (sourceKey) => {
  activeDialogSource.value = sourceKey
  activeGroupIndex.value = 0
  selectedRegexOption.value = JSON.stringify([])

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
  const allOption = matchedRegexOptions.value.find(opt => opt.value === JSON.stringify([]))
  if (allOption) {
    selectedRegexOption.value = allOption.value
  } else if (matchedRegexOptions.value.length) {
    selectedRegexOption.value = matchedRegexOptions.value[0].value
  } else {
    selectedRegexOption.value = JSON.stringify([])
  }
}

// 切换选中的字幕组时，自动重置过滤规则为“全部资源 (不过滤)”
watch(selectedGroup, () => {
  setupDefaultRegex()
})

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
    title: selectedAnime.value?.title || '',
    totalEpisodeNumber: selectedAnime.value?.episodes || aniData?.episodes || 0
  }

  try {
    const res = await http.rssToAni(aniPayload)
    configuredAni.value = res.data
    configuredAni.value.showDownlaod = false
    configuredAni.value.match = aniPayload.match
    dialogVisible.value = false
    configDialogVisible.value = true
  } catch (e) {
    ElMessage.error('解析 RSS 订阅失败: ' + (e.message || e))
  } finally {
    subscribingLoading.value = false
  }
}

// ================= 方案 B：待订阅清单与批量订阅方法 =================
const addToBatchCart = () => {
  if (!selectedAnime.value || !selectedGroup.value) return

  const anime = selectedAnime.value
  const grp = selectedGroup.value
  const animeId = anime.bgmId || anime.id
  const subgroupName = grp.subgroup || grp.label || ''
  const source = activeDialogSource.value
  const sourceLabel = currentDialogSourceLabel.value

  // 提取画质过滤规则
  let matchArray = []
  if (selectedRegexOption.value) {
    try {
      matchArray = JSON.parse(selectedRegexOption.value)
    } catch {
      matchArray = []
    }
  }

  const bgmUrl = grp.bgmUrl || (anime.bgmId ? `https://bgm.tv/subject/${anime.bgmId}` : '')
  const rssUrl = grp.rss || (grp.items?.[0]?.magnet || '')

  // 检查是否已经在清单中
  const existsIndex = batchCart.value.findIndex(
      item => item.animeId === animeId && item.source === source && item.subgroup === subgroupName
  )

  if (existsIndex > -1) {
    ElMessage.warning(`【${anime.title}】的【${subgroupName}】字幕组已在待订阅清单中`)
    return
  }

  batchCart.value.push({
    animeId,
    animeTitle: anime.title || '',
    cover: anime.cover || '',
    source,
    sourceLabel,
    groupLabel: grp.label || subgroupName,
    subgroup: subgroupName,
    bgmUrl,
    rss: rssUrl,
    match: matchArray.map(s => `{{${subgroupName}}}:${s}`),
    episodes: anime.episodes || 0
  })

  ElMessage.success(`已加入待订阅清单：${anime.title} (${grp.label || subgroupName})`)
  // 自动关闭当前弹窗，方便用户继续浏览挑选
  dialogVisible.value = false
}

const removeBatchCartItem = (index) => {
  if (index >= 0 && index < batchCart.value.length) {
    const removed = batchCart.value.splice(index, 1)[0]
    if (removed) {
      ElMessage.info(`已移除：${removed.animeTitle}`)
    }
  }
}

const clearBatchCart = () => {
  batchCart.value = []
  ElMessage.info('待订阅清单已清空')
}

const executeBatchCartSubscribe = async () => {
  if (!batchCart.value.length) {
    ElMessage.warning('待订阅清单为空')
    return
  }

  const itemsToSubscribe = [...batchCart.value]
  batchExecuting.value = true
  batchProgress.value = {
    current: 0,
    total: itemsToSubscribe.length,
    currentTitle: '',
    currentGroup: ''
  }

  let successCount = 0
  let failedCount = 0

  for (let i = 0; i < itemsToSubscribe.length; i++) {
    const item = itemsToSubscribe[i]
    batchProgress.value.current = i + 1
    batchProgress.value.currentTitle = item.animeTitle || ''
    batchProgress.value.currentGroup = item.groupLabel || item.subgroup || ''

    try {
      const aniPayload = {
        ...JSON.parse(JSON.stringify(aniData)),
        type: item.source,
        url: item.rss,
        bgmUrl: item.bgmUrl,
        subgroup: item.subgroup,
        match: item.match || [],
        title: item.animeTitle || '',
        totalEpisodeNumber: item.episodes || 0
      }

      const res = await http.rssToAni(aniPayload)
      const configured = res.data
      configured.showDownlaod = false
      if (item.match && item.match.length) {
        configured.match = item.match
      }
      await http.addAni(configured)
      successCount++
    } catch (e) {
      console.error(`批量订阅失败 [${item.animeTitle}]:`, e)
      failedCount++
    }
  }

  batchExecuting.value = false
  batchCart.value = []
  batchCartDialogVisible.value = false

  ElMessage.success(`批量订阅完成：成功 ${successCount} 部${failedCount > 0 ? `，失败 ${failedCount} 部` : ''}`)
  window.$reLoadList?.()

  await loadSubscribedList()
  updateSubscribedInfoForAnimeList()
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
    configDialogVisible.value = true
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
        configDialogVisible.value = false
        loadSubscribedList().then(() => {
          loadAuthorityData()
        })
      })
      .catch(e => {
        ElMessage.error(e.message || '保存订阅失败')
      })
      .finally(() => {
        done?.()
      })
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

.week-select-all-btn {
  margin-left: 8px;
  font-size: 12px;
  padding: 0 4px;
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
  position: relative;
  transition: all 0.2s ease;
  border-radius: var(--el-border-radius-base);
}

.anime-grid-card-wrap.is-manage-mode {
  padding-left: 0;
}

.card-manage-check {
  position: absolute;
  left: 10px;
  top: 10px;
  z-index: 10;
  display: flex;
  align-items: center;
  justify-content: center;
  background: rgba(0, 0, 0, 0.65);
  backdrop-filter: blur(6px);
  -webkit-backdrop-filter: blur(6px);
  padding: 4px;
  border-radius: 6px;
  border: 1px solid rgba(255, 255, 255, 0.25);
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.3);
  transition: all 0.2s cubic-bezier(0.16, 1, 0.3, 1);
}

.card-manage-check :deep(.el-checkbox) {
  height: auto;
  margin-right: 0;
}

.card-manage-check :deep(.el-checkbox__inner) {
  border-radius: 4px;
}

.anime-grid-card-wrap.is-selected .card-manage-check {
  background: var(--el-color-primary);
  border-color: var(--el-color-primary);
}

.anime-grid-card-wrap.is-selected .anime-card-box {
  border-color: var(--el-color-primary) !important;
  background: var(--el-color-primary-light-9) !important;
  box-shadow: 0 0 0 2px var(--el-color-primary), 0 4px 14px rgba(0, 0, 0, 0.12) !important;
}

.anime-grid-card-wrap.is-disabled {
  opacity: 0.6;
  cursor: not-allowed;
}

.anime-grid-card-wrap.is-disabled .card-manage-check {
  opacity: 0.5;
  cursor: not-allowed;
}

.anime-grid-card-wrap.is-disabled .anime-card-box:hover {
  transform: none;
  box-shadow: none;
  border-color: var(--el-border-color-light);
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

.clickable-cover {
  cursor: pointer;
  transition: transform 0.2s cubic-bezier(0.4, 0, 0.2, 1), filter 0.2s ease;
}

.clickable-cover:hover {
  transform: scale(1.04);
  filter: brightness(1.06);
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

.clickable-title {
  cursor: pointer;
  transition: color 0.15s ease;
}

.clickable-title:hover {
  color: var(--el-color-primary) !important;
  text-decoration: underline;
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
  font-size: 11px;
  color: var(--el-text-color-secondary);
  cursor: default;
  line-height: 1.4;
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

/* 字幕组专属行 */
.card-subgroup-row {
  display: flex;
  align-items: center;
  gap: 6px;
  min-width: 0;
  margin-top: 2px;
}

.subgroup-row-label {
  font-size: 11px;
  color: var(--el-text-color-secondary);
  flex-shrink: 0;
  line-height: 1;
}

.card-subgroup-list {
  display: flex;
  align-items: center;
  gap: 6px;
  min-width: 0;
  overflow: hidden;
}

.card-subgroup-tag {
  max-width: 180px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  font-size: 11px;
  height: 20px;
  line-height: 18px;
  padding: 0 6px;
}

/* 卡片底部操作与状态条 */
.list-card-bottom-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
  margin-top: auto;
  min-width: 0;
}

.card-status-row {
  display: flex;
  align-items: center;
  gap: 6px;
  min-width: 0;
  margin-top: 1px;
}

.card-status-tag {
  font-weight: 500;
  font-size: 11px;
  height: 22px;
  line-height: 20px;
  padding: 0 6px;
}

.card-progress-tag {
  font-weight: 600;
  font-variant-numeric: tabular-nums;
  font-size: 11px;
  height: 22px;
  line-height: 20px;
  padding: 0 6px;
}

.card-bottom-air-time {
  display: flex;
  align-items: center;
  min-width: 0;
  overflow: hidden;
}

.card-bottom-air-time .card-air-time-row {
  margin-top: 0;
}

.list-card-actions {
  display: flex;
  align-items: center;
  justify-content: flex-end;
  gap: 6px;
  flex-shrink: 0;
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
  position: relative;
  display: flex;
  align-items: center;
  transition: all 0.2s ease;
}

.list-row-manage-check {
  margin-right: 12px;
  flex-shrink: 0;
  display: flex;
  align-items: center;
}

.anime-list-item-wrap.is-selected .anime-row-card-box {
  border-color: var(--el-color-primary) !important;
  background: var(--el-color-primary-light-9) !important;
}

.anime-list-item-wrap.is-disabled {
  opacity: 0.6;
  cursor: not-allowed;
}

.anime-row-card-box {
  flex: 1;
  min-width: 0;
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

.multi-sub-name-wrap {
  display: flex;
  align-items: center;
  gap: 8px;
}

.multi-sub-name {
  font-weight: 600;
  font-size: 13px;
  color: var(--el-text-color-primary);
}

/* ================= 管理模式底部悬浮工具栏 ================= */
.manage-floating-bar {
  position: fixed;
  bottom: 24px;
  left: 50%;
  transform: translateX(-50%);
  z-index: 2000;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 20px;
  padding: 10px 22px;
  background: var(--el-bg-color-overlay);
  border: 1px solid var(--el-border-color-light);
  border-radius: 40px;
  box-shadow: 0 8px 24px rgba(0, 0, 0, 0.16), 0 2px 6px rgba(0, 0, 0, 0.06);
  backdrop-filter: blur(12px);
  animation: manageFloatUp 0.25s cubic-bezier(0.2, 0.8, 0.2, 1);
}

@keyframes manageFloatUp {
  from {
    opacity: 0;
    transform: translate(-50%, 20px);
  }
  to {
    opacity: 1;
    transform: translate(-50%, 0);
  }
}

.manage-floating-info {
  font-size: 13px;
  color: var(--el-text-color-regular);
  display: flex;
  align-items: center;
  gap: 6px;
  white-space: nowrap;
}

.manage-floating-info strong {
  color: var(--el-color-primary);
  font-size: 15px;
  font-weight: 700;
}

.manage-sub-count {
  color: var(--el-text-color-secondary);
  font-size: 12px;
}

.manage-floating-actions {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
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
  gap: 10px;
  height: 560px;
  min-height: 560px;
  box-sizing: border-box;
  overflow: hidden;
}

/* 1. 紧凑番剧头部信息 (去除多层卡片框与厚重边距) */
.anime-dialog-header {
  flex-shrink: 0;
  display: flex;
  gap: 12px;
  padding: 2px 2px 8px 2px;
  border-bottom: 1px solid var(--el-border-color-lighter);
  align-items: center;
}

.anime-header-cover {
  width: 44px;
  height: 60px;
  border-radius: 6px;
  object-fit: cover;
  flex-shrink: 0;
  box-shadow: 0 2px 6px rgba(0, 0, 0, 0.08);
}

.anime-header-detail {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.anime-header-title-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
}

.anime-header-title {
  font-size: 14px;
  font-weight: 700;
  color: var(--el-text-color-primary);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.anime-header-meta-row {
  display: flex;
  align-items: center;
  gap: 10px;
  flex-wrap: wrap;
}

.anime-header-score {
  font-size: 12px;
  color: var(--el-text-color-secondary);
}

.anime-header-score strong {
  color: #fb7299;
  font-size: 13px;
}

.anime-header-air-time {
  display: inline-flex;
  align-items: center;
}

.anime-header-time-pill {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  padding: 1px 6px;
  border-radius: 4px;
  background: var(--el-fill-color-light);
  font-size: 11px;
  color: var(--el-text-color-regular);
}

.anime-header-links {
  display: flex;
  gap: 6px;
  margin-left: auto;
}

/* 2. 数据源切换栏 (扁平 Segmented 控制条，去除厚框) */
.anime-dialog-sources-bar {
  flex-shrink: 0;
}

.source-segmented-group {
  display: flex;
  gap: 4px;
  background: var(--el-fill-color-light);
  padding: 3px;
  border-radius: 8px;
  border: 1px solid var(--el-border-color-extra-light);
}

.source-segmented-btn {
  flex: 1;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 6px;
  padding: 6px 12px;
  border: none;
  background: transparent;
  border-radius: 6px;
  font-size: 13px;
  font-weight: 500;
  color: var(--el-text-color-regular);
  cursor: pointer;
  transition: all 0.2s ease;
}

.source-segmented-btn:hover {
  background: var(--el-fill-color);
  color: var(--el-text-color-primary);
}

.source-segmented-btn.is-active {
  background: var(--el-bg-color);
  color: var(--el-color-primary);
  font-weight: 600;
  box-shadow: 0 1px 4px rgba(0, 0, 0, 0.08);
}

.source-btn-icon {
  width: 16px;
  height: 16px;
  border-radius: 3px;
  object-fit: contain;
}

.source-btn-count {
  font-size: 11px;
  opacity: 0.85;
}

/* 3. 字幕组与资源区域 (局部 loading 与统一柔和遮罩) */
.dialog-groups-area {
  flex: 1;
  min-height: 0;
  display: flex;
  flex-direction: column;
  gap: 10px;
  position: relative;
}

.anime-group-dialog :deep(.el-loading-mask) {
  background-color: color-mix(in srgb, var(--el-bg-color) 70%, transparent);
  backdrop-filter: blur(4px);
  border-radius: 8px;
}

.subgroups-flow-wrapper {
  flex-shrink: 0;
}

.subgroups-flow-scroll {
  width: 100%;
}

.subgroups-pills-row {
  display: flex;
  gap: 6px;
  align-items: center;
  padding: 2px 0;
}

.subgroup-capsule {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  padding: 4px 10px;
  border: 1px solid var(--el-border-color-lighter);
  background: var(--el-fill-color-light);
  border-radius: 6px;
  cursor: pointer;
  white-space: nowrap;
  font-size: 12px;
  font-weight: 500;
  color: var(--el-text-color-primary);
  transition: all 0.15s ease;
}

.subgroup-capsule:hover {
  background: var(--el-fill-color);
  border-color: var(--el-border-color);
}

.subgroup-capsule.is-active {
  background: var(--el-color-primary-light-9);
  border-color: var(--el-color-primary);
  color: var(--el-color-primary);
  font-weight: 600;
}

.subgroup-capsule.is-subscribed {
  border-color: var(--el-color-success-light-5);
  background: var(--el-color-success-light-9);
  color: var(--el-color-success-dark-2);
}

.subgroup-capsule.is-subscribed.is-active {
  background: var(--el-color-primary-light-9);
  border-color: var(--el-color-primary);
  color: var(--el-color-primary);
}

.subgroup-check-icon {
  color: var(--el-color-success);
  font-size: 12px;
}

.subgroup-name {
  line-height: 1.2;
}

.subgroup-day {
  font-size: 10px;
  color: var(--el-text-color-secondary);
  opacity: 0.8;
}

/* 4. 选中字幕组控制栏与种子列表 (一体化主面板，消灭双重卡片嵌套) */
.selected-subgroup-panel {
  flex: 1;
  min-height: 0;
  display: flex;
  flex-direction: column;
  border-radius: 8px;
  background: var(--el-bg-color);
  border: 1px solid var(--el-border-color-lighter);
  overflow: hidden;
}

.subgroup-action-toolbar {
  flex-shrink: 0;
  padding: 8px 12px;
  border-bottom: 1px solid var(--el-border-color-lighter);
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
  background: var(--el-fill-color-lighter);
}

.subgroup-toolbar-left {
  display: flex;
  align-items: center;
  gap: 8px;
  min-width: 0;
}

.subgroup-curr-title {
  font-size: 13px;
  font-weight: 700;
  color: var(--el-text-color-primary);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.subgroup-tags-inline {
  display: flex;
  gap: 4px;
}

.subgroup-toolbar-right {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-shrink: 0;
}

.match-regex-select {
  width: 140px;
}

/* 种子资源列表 (扁平极简列表，无小卡片框嵌套) */
.subgroup-torrents-area {
  flex: 1;
  min-height: 0;
  display: flex;
  flex-direction: column;
  overflow: hidden;
}

.torrents-area-header {
  flex-shrink: 0;
  padding: 6px 12px 4px;
  border-bottom: 1px solid var(--el-border-color-extra-light);
}

.torrents-count-hint {
  font-size: 11px;
  font-weight: 600;
  color: var(--el-text-color-secondary);
}

.torrents-list-container {
  flex: 1;
  min-height: 0;
  overflow: hidden;
}

.torrents-scroll {
  height: 100%;
}

.torrents-flat-list {
  display: flex;
  flex-direction: column;
}

.torrent-flat-item {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
  padding: 7px 12px;
  border-bottom: 1px solid var(--el-border-color-extra-light);
  transition: background 0.15s ease;
}

.torrent-flat-item:last-child {
  border-bottom: none;
}

.torrent-flat-item:hover {
  background: var(--el-fill-color-light);
}

.torrent-flat-info {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.torrent-flat-title {
  font-size: 12px;
  line-height: 1.4;
  word-break: break-all;
  color: var(--el-text-color-primary);
}

.torrent-flat-meta {
  font-size: 11px;
  color: var(--el-text-color-secondary);
  display: flex;
  align-items: center;
  gap: 4px;
}

.torrent-flat-actions {
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

.empty-switch-hints {
  display: flex;
  gap: 8px;
  margin-top: 10px;
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

/* ================= 第二步：确认番剧配置弹窗 ================= */
.config-ani-dialog :deep(.el-dialog__body) {
  padding: 10px 18px 18px;
}

.config-dialog-content {
  min-width: 0;
}

/* ================= 批量模式相关样式 ================= */
.list-week-title-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 12px;
}

.list-week-title-row .list-week-title {
  margin-bottom: 0;
}

.week-batch-btn {
  font-size: 13px;
  font-weight: 500;
}

.anime-grid-card-wrap.is-batch-mode,
.anime-list-item-wrap.is-batch-mode {
  cursor: pointer;
}

.anime-grid-card-wrap.is-batch-selected .anime-card-box,
.anime-list-item-wrap.is-batch-selected .anime-row-card-box {
  border-color: var(--el-color-primary) !important;
  box-shadow: 0 0 0 1px var(--el-color-primary), 0 4px 14px rgba(64, 158, 255, 0.22) !important;
  background-color: var(--el-color-primary-light-9) !important;
}

.anime-grid-card-wrap.is-batch-disabled,
.anime-list-item-wrap.is-batch-disabled {
  opacity: 0.6;
  cursor: not-allowed;
}

.cart-btn-count {
  margin-left: 4px;
  font-weight: 600;
  font-variant-numeric: tabular-nums;
  color: var(--el-color-primary);
}

.add-cart-btn {
  font-weight: 500;
}

/* 待订阅清单弹窗样式 */
.batch-cart-dialog :deep(.el-dialog__body) {
  padding: 16px 20px 20px;
}

.batch-cart-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 12px;
}

.batch-cart-tip {
  font-size: 13px;
  color: var(--el-text-color-regular);
}

.batch-cart-tip strong {
  color: var(--el-color-primary);
  font-size: 15px;
}

.batch-cart-list-wrap {
  border: 1px solid var(--el-border-color-lighter);
  border-radius: 8px;
  background: var(--el-fill-color-lighter);
  padding: 6px;
}

.batch-cart-list {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.batch-cart-item-row {
  display: flex;
  align-items: center;
  gap: 12px;
  background: var(--el-bg-color);
  border: 1px solid var(--el-border-color-lighter);
  border-radius: 8px;
  padding: 8px 12px;
  transition: all 0.2s;
}

.batch-cart-item-row:hover {
  border-color: var(--el-border-color);
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.04);
}

.batch-cart-item-cover {
  width: 36px;
  height: 48px;
  object-fit: cover;
  border-radius: 4px;
  flex-shrink: 0;
  background: var(--el-fill-color-dark);
}

.batch-cart-item-info {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.batch-cart-title {
  font-size: 13px;
  font-weight: 600;
  color: var(--el-text-color-primary);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.batch-cart-meta {
  display: flex;
  align-items: center;
  gap: 8px;
}

.batch-cart-empty {
  padding: 24px 0;
}

.batch-cart-empty .empty-tip {
  font-size: 12px;
  color: var(--el-text-color-secondary);
  display: block;
  margin-top: 4px;
}

.batch-executing-wrap {
  padding: 24px 10px;
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.batch-executing-title {
  font-size: 16px;
  font-weight: 600;
  color: var(--el-text-color-primary);
}

.batch-executing-info {
  display: flex;
  align-items: center;
  justify-content: space-between;
  font-size: 13px;
  color: var(--el-text-color-secondary);
}

.batch-executing-curr {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  max-width: 480px;
  color: var(--el-color-primary);
}
</style>

<style>
.anime-group-dialog {
  border-radius: 12px;
  overflow: hidden;
}

.anime-group-dialog .el-dialog__body {
  padding: 12px 18px 16px !important;
  background: var(--el-bg-color);
  height: 560px !important;
  min-height: 560px !important;
  max-height: 560px !important;
  box-sizing: border-box;
  overflow: hidden;
}

.anime-group-dialog .el-loading-mask {
  background-color: color-mix(in srgb, var(--el-bg-color) 70%, transparent) !important;
  backdrop-filter: blur(4px) !important;
  border-radius: 8px !important;
}
</style>
