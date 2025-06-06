<template>
  <div class="vod">

    <el-row justify="space-between">
      <el-col :span="18">
        <el-breadcrumb separator="/">
          <el-breadcrumb-item v-for="item in paths">
            <a @click="loadFolder(item.path)">{{ item.text }}</a>
          </el-breadcrumb-item>
        </el-breadcrumb>
      </el-col>

      <el-col :span="2">
        <el-input v-model="keyword" @keyup.enter="search" :disabled="searching" placeholder="搜索电报资源">
          <template #append>
            <el-button :icon="Search" :disabled="searching" @click="search"/>
          </template>
        </el-input>
      </el-col>

      <el-col :span="2">
        <el-button :icon="Film" circle @click="loadHistory"/>
        <el-button :icon="Delete" circle @click="clearHistory"
                   v-if="paths.length>1&&paths[1].path=='/~history'"/>
        <el-button :icon="Setting" circle @click="settingVisible=true"/>
        <el-button :icon="Plus" circle @click="handleAdd"/>
      </el-col>
    </el-row>

    <div class="divider"></div>

    <el-row justify="center">
      <el-col :xs="3" :sm="3" :md="5" :span="9" v-if="results.length">
        {{ filteredResults.length }}/{{ results.length }}条搜索结果&nbsp;&nbsp;
        <el-select style="width: 90px" v-model="shareType" @change="filterSearchResults">
          <el-option
            v-for="item in options"
            :key="item.value"
            :label="item.label"
            :value="item.value"
          />
        </el-select>
        &nbsp;&nbsp;
        <el-button :icon="Delete" circle @click="clearSearch"></el-button>
        <el-table :data="filteredResults" v-loading="searching" class="results" @row-click="loadResult">
          <el-table-column prop="vod_name" label="内容">
            <template #default="scope">
              <el-tooltip :content="scope.row.vod_play_url">
                {{ getShareType(scope.row.type_name) }}
                {{ scope.row.vod_name }}
              </el-tooltip>
            </template>
          </el-table-column>
        </el-table>
      </el-col>

      <el-col :xs="22" :sm="20" :md="18" :span="14">
        <el-table v-loading="loading" :data="files" style="width: 100%" @row-click="load">
          <el-table-column prop="vod_name" label="名称">
            <template #default="scope">
              <el-popover :width="300" placement="left-start" v-if="scope.row.vod_tag=='folder'&&scope.row.vod_pic">
                <template #reference>
                  📺
                </template>
                <template #default>
                  <el-image :src="imageUrl(scope.row.vod_pic)" loading="lazy" show-progress fit="cover"/>
                </template>
              </el-popover>
              <span v-else-if="scope.row.vod_tag=='folder'">📂</span>
              <span v-else-if="scope.row.vod_id.endsWith('playlist$1')">▶️</span>
              <span v-else>🎬</span>
              {{ scope.row.vod_name }}
            </template>
          </el-table-column>
          <el-table-column label="大小" width="120" v-if="!isHistory">
            <template #default="scope">
              {{ scope.row.vod_tag === 'file' ? scope.row.vod_remarks : '-' }}
            </template>
          </el-table-column>
          <el-table-column prop="dbid" label="豆瓣ID" width="120" v-if="!isHistory">
            <template #default="scope">
              <a @click.stop :href="'https://movie.douban.com/subject/'+scope.row.dbid" target="_blank"
                 v-if="scope.row.dbid">
                {{ scope.row.dbid }}
              </a>
            </template>
          </el-table-column>
          <el-table-column label="评分" width="90" v-if="!isHistory">
            <template #default="scope">
              {{ scope.row.vod_tag === 'folder' ? scope.row.vod_remarks : '' }}
            </template>
          </el-table-column>
          <el-table-column prop="index" label="集数" width="90" v-if="isHistory"/>
          <el-table-column prop="progress" label="进度" width="120" v-if="isHistory"/>
          <el-table-column prop="vod_time" :label="isHistory?'播放时间':'修改时间'" width="165"/>
        </el-table>
        <el-pagination layout="total, prev, pager, next, jumper, sizes"
                       :current-page="page" :page-size="size" :total="total"
                       @current-change="reload" @size-change="handleSizeChange"/>
      </el-col>
    </el-row>

    <el-dialog v-model="dialogVisible" :title="title" :fullscreen="true" @opened="start" @close="stop">
      <div class="video-container">
        <el-row>
          <el-col :span="18">
            <video
              ref="videoPlayer"
              :src="playUrl"
              :autoplay="true"
              @ended="playNextVideo"
              @play="updatePlayState"
              @pause="updatePlayState"
              @volumechange="updateMuteState"
              controls>
            </video>
          </el-col>
          <el-col :span="5">
            <div v-if="playlist.length>1">
              <div style="margin-left: 30px; margin-bottom: 10px;">
                <el-link :href="buildVlcUrl(currentVideoIndex)" target="_blank">第{{ currentVideoIndex + 1 }}集</el-link> /
                <el-link :href="buildVlcUrl(0)" target="_blank">总共{{ playlist.length }}集</el-link>
              </div>
              <el-scrollbar ref="scrollbarRef" height="1050px">
                <ul>
                  <li v-for="(video, index) in playlist" :key="index" @click="playVideo(index)">
                    <el-link type="primary" v-if="currentVideoIndex==index">{{ video.text }}</el-link>
                    <el-link v-else>{{ video.text }}</el-link>
                  </li>
                </ul>
              </el-scrollbar>
            </div>
          </el-col>
        </el-row>

        <el-row>
          <el-col :span="18">
            <div>
              <el-button-group>
                <el-button @click="play" v-if="!playing">播放</el-button>
                <el-button @click="pause" v-if="playing">暂停</el-button>
                <el-button @click="close">退出</el-button>
                <el-button @click="toggleMute">{{ isMuted ? '取消静音' : '静音' }}</el-button>
                <el-button @click="toggleFullscreen">全屏</el-button>
                <el-button @click="backward">后退</el-button>
                <el-button @click="forward">前进</el-button>
                <el-button @click="playPrevVideo" v-if="playlist.length>1">上集</el-button>
                <el-button @click="playNextVideo" v-if="playlist.length>1">下集</el-button>
                <el-popover placement="bottom" width="400px" v-if="playlist.length>1">
                  <template #reference>
                    <el-button>片头</el-button>
                  </template>
                  <template #default>
                    跳过片头
                    <el-input-number
                      v-model="minute1"
                      class="mx-4"
                      :min="0"
                      :max="4"
                      value-on-clear="min"
                      controls-position="right"
                      @change="handleMinute1Change"
                    />
                    :
                    <el-input-number
                      v-model="second1"
                      class="mx-4"
                      :min="0"
                      :max="59"
                      :step="5"
                      value-on-clear="min"
                      controls-position="right"
                      @change="handleSecond1Change"
                    />
                  </template>
                </el-popover>
                <el-popover placement="bottom" width="400px" v-if="playlist.length>1">
                  <template #reference>
                    <el-button>片尾</el-button>
                  </template>
                  <template #default>
                    跳过片尾
                    <el-input-number
                      v-model="minute2"
                      class="mx-4"
                      :min="0"
                      :max="4"
                      value-on-clear="min"
                      controls-position="right"
                      @change="handleMinute2Change"
                    />
                    :
                    <el-input-number
                      v-model="second2"
                      class="mx-4"
                      :min="0"
                      :max="59"
                      :step="5"
                      value-on-clear="min"
                      controls-position="right"
                      @change="handleSecond2Change"
                    />
                  </template>
                </el-popover>
                <el-popover placement="bottom" width="300px">
                  <template #reference>
                    <el-button>{{ currentSpeed == 1 ? '1.0' : currentSpeed == 2 ? '2.0' : currentSpeed }}</el-button>
                  </template>
                  <template #default>
                    播放速度
                    <el-slider v-model="currentSpeed" @change="setSpeed" :min="0.5" :max="2" :step="0.1" show-stops/>
                  </template>
                </el-popover>
                <el-popover placement="bottom" width="300px">
                  <template #reference>
                    <el-button>{{ currentVolume }}</el-button>
                  </template>
                  <template #default>
                    音量
                    <el-slider v-model="currentVolume" @change="setVolume" :min="0" :max="100" :step="5"/>
                  </template>
                </el-popover>
                <el-popover placement="right-start">
                  <template #reference>
                    <el-button :icon="QuestionFilled"/>
                  </template>
                  <template #default>
                    <div>
                      <div>播放： 空格键</div>
                      <div>全屏： 回车键</div>
                      <div>退出： Esc</div>
                      <div>静音： m</div>
                      <div>减速： -</div>
                      <div>加速： +</div>
                      <div>普速： =</div>
                      <div>后退15秒： ←</div>
                      <div>前进15秒： →</div>
                      <div v-if="playlist.length>1">上集： ↑</div>
                      <div v-if="playlist.length>1">下集： ↓</div>
                    </div>
                  </template>
                </el-popover>
              </el-button-group>
            </div>
          </el-col>
        </el-row>

        <div class="divider"></div>

        <el-row>
          <el-col :span="18">
            <el-descriptions>
              <el-descriptions-item label="名称">{{ movies[0].vod_name }}</el-descriptions-item>
              <el-descriptions-item label="类型">{{ movies[0].type_name || '未知' }}</el-descriptions-item>
              <el-descriptions-item label="年代">{{ movies[0].vod_year || '未知' }}</el-descriptions-item>
              <el-descriptions-item label="地区">{{ movies[0].vod_area || '未知' }}</el-descriptions-item>
              <el-descriptions-item label="语言">{{ movies[0].vod_lang || '未知' }}</el-descriptions-item>
              <el-descriptions-item label="评分">{{ movies[0].vod_remarks || '未知' }}</el-descriptions-item>
              <el-descriptions-item label="导演">{{ movies[0].vod_director || '未知' }}</el-descriptions-item>
              <el-descriptions-item label="演员">{{ movies[0].vod_actor || '未知' }}</el-descriptions-item>
              <el-descriptions-item label="豆瓣" v-if="movies[0].dbid">
                <a :href="'https://movie.douban.com/subject/'+movies[0].dbid" target="_blank">
                  {{ movies[0].dbid }}
                </a>
              </el-descriptions-item>
            </el-descriptions>
            <p>
              {{ movies[0].vod_content }}
            </p>
          </el-col>
        </el-row>
      </div>
    </el-dialog>

    <el-dialog v-model="formVisible" title="添加分享" @opened="focus">
      <el-form label-width="140" :model="form">
        <el-form-item label="分享链接" required>
          <el-input id="link" v-model="form.link" @keyup.enter="addShare" autocomplete="off"/>
        </el-form-item>
        <el-form-item label="挂载路径">
          <el-input v-model="form.path" autocomplete="off" placeholder="留空为临时挂载"/>
        </el-form-item>
        <el-form-item label="提取码">
          <el-input v-model="form.code" autocomplete="off"/>
        </el-form-item>
      </el-form>
      <template #footer>
      <span class="dialog-footer">
        <el-button @click="formVisible=false">取消</el-button>
        <el-button type="primary" @click="addShare">添加</el-button>
      </span>
      </template>
    </el-dialog>

    <el-dialog v-model="settingVisible" title="搜索配置">
      <el-form label-width="140">
        <el-form-item label="电报频道群组">
          <el-input v-model="tgChannels" :rows="3" type="textarea" placeholder="逗号分割，留空使用默认值"/>
          <span>登陆后使用此频道列表搜索。</span>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="updateTgChannels">更新</el-button>
        </el-form-item>
        <el-form-item label="电报频道列表">
          <el-input v-model="tgWebChannels" :rows="3" type="textarea" placeholder="逗号分割，留空使用默认值"/>
          <span>未登陆使用此频道列表搜索。</span>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="updateTgWebChannels">更新</el-button>
        </el-form-item>
        <el-form-item label="远程搜索地址">
          <el-input v-model="tgSearch"/>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="updateTgSearch">更新</el-button>
        </el-form-item>
        <el-form-item label="搜索超时时间">
          <el-input-number v-model="tgTimeout" :min="500" :max="30000"/>&nbsp;毫秒
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="updateTgTimeout">更新</el-button>
        </el-form-item>
      </el-form>
      <template #footer>
      <span class="dialog-footer">
        <el-button @click="settingVisible=false">取消</el-button>
      </span>
      </template>
    </el-dialog>

  </div>
</template>

<script setup lang="ts">
// @ts-nocheck
import {onMounted, ref} from 'vue'
import axios from "axios"
import {ElMessage, type ScrollbarInstance} from "element-plus";
import type {VodItem} from "@/model/VodItem";
import {useRoute, useRouter} from "vue-router";
import clipBorad from "vue-clipboard3";
import {onUnmounted} from "@vue/runtime-core";
import {Delete, Film, Plus, QuestionFilled, Search, Setting} from "@element-plus/icons-vue";

let {toClipboard} = clipBorad();

interface Item {
  path: string
  text: string
}

let timer = 0
const route = useRoute()
const router = useRouter()
const videoPlayer = ref(null)
const scrollbarRef = ref<ScrollbarInstance>()
const token = ref('')
const keyword = ref('')
const tgChannels = ref('')
const tgWebChannels = ref('')
const tgSearch = ref('')
const tgTimeout = ref(3000)
const shareType = ref('')
const title = ref('')
const playUrl = ref('')
const movies = ref<VodItem[]>([])
const playFrom = ref<string[]>([])
const playlist = ref<Item[]>([])
const currentVideoIndex = ref(0)
const duration = ref(0)
const currentTime = ref(0)
const currentSpeed = ref(1)
const currentVolume = ref(100)
const skipStart = ref(0)
const skipEnd = ref(0)
const minute1 = ref(0)
const second1 = ref(0)
const minute2 = ref(0)
const second2 = ref(0)
const loading = ref(false)
const playing = ref(false)
const isMuted = ref(false)
const isFullscreen = ref(false)
const dialogVisible = ref(false)
const formVisible = ref(false)
const settingVisible = ref(false)
const isHistory = ref(false)
const searching = ref(false)
const page = ref(1)
const size = ref(40)
const total = ref(0)
const files = ref<VodItem[]>([])
const results = ref<VodItem[]>([])
const filteredResults = ref<VodItem[]>([])
const paths = ref<Item[]>([])
const form = ref({
  link: '',
  path: '',
  code: '',
})
const options = [
  {label: '全部', value: ''},
  {label: '夸克', value: '5'},
  {label: '阿里', value: '0'},
  {label: '123', value: '3'},
  {label: '天翼', value: '9'},
  {label: '迅雷', value: '2'},
  {label: 'UC', value: '7'},
  {label: '115', value: '8'},
  {label: 'PikPak', value: '1'},
]

const handleAdd = () => {
  form.value = {
    link: '',
    path: '',
    code: '',
  }
  formVisible.value = true
}

const search = () => {
  searching.value = true
  axios.get('/api/telegram/search?wd=' + keyword.value).then(({data}) => {
    searching.value = false
    results.value = data.map(e => {
      return {
        vod_id: e.id + '',
        vod_name: e.name,
        vod_tag: 'folder',
        vod_time: formatDate(e.time),
        type_name: e.type,
        vod_play_from: e.channel,
        vod_play_url: e.link,
      }
    })
    if (results.value.length == 0) {
      ElMessage.info('无搜索结果')
    }
    filterSearchResults()
  }, () => {
    searching.value = false
  })
}

const filterSearchResults = () => {
  filteredResults.value = shareType.value ? results.value.filter(e => e.type_name == shareType.value) : results.value
}

const getShareType = (type: string) => {
  if (type == '0') {
    return '📀'
  }
  if (type == '5') {
    return '🚀'
  }
  if (type == '7') {
    return '🌞'
  }
  if (type == '3') {
    return '💾'
  }
  if (type == '8') {
    return '📡'
  }
  if (type == '9') {
    return '☁'
  }
  if (type == '1') {
    return '🅿'
  }
  if (type == '2') {
    return '⚡'
  }
  return ''
}

const clearSearch = () => {
  keyword.value = ''
  results.value = []
  filteredResults.value = []
}

const updateTgChannels = () => {
  axios.post('/api/settings', {name: 'tg_channels', value: tgChannels.value}).then(({data}) => {
    tgChannels.value = data.value
    ElMessage.success('更新成功')
  })
}

const updateTgWebChannels = () => {
  axios.post('/api/settings', {name: 'tg_web_channels', value: tgWebChannels.value}).then(({data}) => {
    tgWebChannels.value = data.value
    ElMessage.success('更新成功')
  })
}

const updateTgSearch = () => {
  axios.post('/api/settings', {name: 'tg_search', value: tgSearch.value}).then(({data}) => {
    tgSearch.value = data.value
    ElMessage.success('更新成功')
  })
}

const updateTgTimeout = () => {
  axios.post('/api/settings', {name: 'tg_timeout', value: tgTimeout.value + ''}).then(() => {
    ElMessage.success('更新成功')
  })
}

const focus = () => {
  document.getElementById('link').focus()
}

const addShare = () => {
  axios.post('/api/share-link', form.value).then(({data}) => {
    loadFolder(data)
    formVisible.value = false
  })
}

const loadResult = (row: any) => {
  form.value = {
    link: row.vod_play_url,
    path: '',
    code: '',
  }
  axios.post('/api/share-link', form.value).then(({data}) => {
    loadFolder(data)
  })
}

const load = (row: any) => {
  if (isHistory.value) {
    goParent(row.vod_id)
  }

  if (row.vod_tag === 'folder') {
    loadFolder(row.vod_id)
  } else {
    if (row.vod_id.endsWith('playlist$1')) {
      toClipboard(row.vod_play_url).then(() => {
        ElMessage.success('播放地址复制成功')
      })
    }
    loadDetail(row.vod_id)
  }
}

const goParent = (path: string) => {
  path = getPath(path)
  const index = path.lastIndexOf('/')
  const parent = path.substring(0, index)
  loadFolder(parent)
}

const imageUrl = (url: string) => {
  if (url.endsWith("/folder.png")) {
    return url;
  }
  return '/images?url=' + encodeURIComponent(url)
}

const newImageUrl = (url: string) => {
  if (url.endsWith("/folder.png")) {
    return url;
  }
  return '/images?url=' + encodeURIComponent(url.replace('/s_ratio_poster/', '/m/'))
}

const handleSizeChange = (value: number) => {
  size.value = value
  reload(1)
}

const reload = (value: number) => {
  page.value = value
  loadFiles(paths.value[paths.value.length - 1].path)
}

const loadFolder = (path: string) => {
  if (path == '/~history') {
    return
  }
  router.push('/vod' + getPath(path).replace('\t', '%09'))
  page.value = 1
  loadFiles(path)
}

const loadFiles = (path: string) => {
  const id = extractPaths(path)
  isHistory.value = false
  loading.value = true
  files.value = []
  axios.get('/vod' + token.value + '?ac=web&pg=' + page.value + '&size=' + size.value + '&t=' + id).then(({data}) => {
    files.value = data.list
    total.value = data.total
    loading.value = false
  }, () => {
    loading.value = false
  })
}

const getPath = (id: string) => {
  return decodeURIComponent(id).replace('1$', '').replace('$1', '')
}

const extractPaths = (id: string) => {
  const path = getPath(id)
  if (path == '/') {
    paths.value = [{text: '🏠首页', path: '/'}]
  } else {
    const array = path.split('/')
    const items: Item[] = []
    for (let index = 0; index < array.length; ++index) {
      const path = array.slice(0, index + 1).join('/')
      const text = array[index]
      items.push({text: text ? text : '🏠首页', path: path ? path : '/'})
    }
    paths.value = items
  }
  if (id.startsWith('1$')) {
    return id
  }
  return '1$' + encodeURIComponent(path) + '$1'
}

const loadDetail = (id: string) => {
  loading.value = true
  axios.get('/vod' + token.value + '?ac=web&ids=' + id).then(({data}) => {
    movies.value = data.list
    movies.value[0].vod_id = id
    playFrom.value = movies.value[0].vod_play_from.split("$$$");
    playlist.value = movies.value[0].vod_play_url.split("$$$")[0].split("#").map(e => {
      let u = e.split('$')
      if (u.length == 1) {
        return {
          path: e,
          text: movies.value[0].vod_name
        }
      } else {
        return {
          path: u[1],
          text: u[0]
        }
      }
    })
    getHistory(id)
    getPlayUrl()
    loading.value = false
    dialogVisible.value = true
  }, () => {
    loading.value = false
  })
}

const handleKeyDown = (event: KeyboardEvent) => {
  if (formVisible.value) {
    return;
  }
  if (!dialogVisible.value) {
    // if (event.code === 'Space' && files.value.length > 0 && files.value[0].vod_tag === 'file') {
    //   event.preventDefault()
    //   loadDetail(files.value[0].vod_id)
    // } else if (event.code === 'Escape' && paths.value.length > 1) {
    //   event.preventDefault()
    //   loadFolder(paths.value[paths.value.length - 2].path)
    // } else if (event.code === 'KeyA') {
    //   if (event.ctrlKey || event.metaKey) {
    //     return;
    //   }
    //   event.preventDefault()
    //   handleAdd()
    // } else if (event.code === 'KeyH') {
    //   if (event.ctrlKey || event.metaKey) {
    //     return;
    //   }
    //   event.preventDefault()
    //   loadHistory()
    // } else if (event.code === 'KeyS') {
    //   if (event.ctrlKey || event.metaKey) {
    //     return;
    //   }
    //   event.preventDefault()
    //   search()
    // }
    return
  }
  if (event.code === 'Space') {
    event.preventDefault()
    togglePlay()
  } else if (event.code === 'ArrowRight') {
    event.preventDefault()
    forward()
  } else if (event.code === 'ArrowLeft') {
    event.preventDefault()
    backward()
  } else if (event.code === 'ArrowUp') {
    event.preventDefault()
    playPrevVideo()
  } else if (event.code === 'ArrowDown') {
    event.preventDefault()
    playNextVideo()
  } else if (event.code === 'Enter') {
    event.preventDefault()
    toggleFullscreen()
  } else if (event.key === '+') {
    event.preventDefault()
    incSpeed()
  } else if (event.key === '-') {
    event.preventDefault()
    decSpeed()
  } else if (event.key === '=') {
    event.preventDefault()
    setSpeed(1.0)
  } else if (event.code === 'KeyM') {
    if (event.ctrlKey || event.metaKey) {
      return;
    }
    event.preventDefault()
    toggleMute()
  } else if (event.code === 'KeyC') {
    if (event.ctrlKey || event.metaKey) {
      return;
    }
    event.preventDefault()
    copyPlayUrl()
  } else if (event.code === 'KeyV') {
    if (event.ctrlKey || event.metaKey) {
      return;
    }
    event.preventDefault()
    openInVLC()
  }
}

const togglePlay = () => {
  if (videoPlayer.value) {
    if (playing.value) {
      pause()
    } else {
      play()
    }
  }
}

const start = () => {
  if (videoPlayer.value) {
    if (currentTime.value < skipStart.value) {
      currentTime.value = skipStart.value
    }
    videoPlayer.value.currentTime = currentTime.value
    videoPlayer.value.playbackRate = currentSpeed.value
    videoPlayer.value.volume = currentVolume.value / 100.
    videoPlayer.value.addEventListener('playbackratechange', handleSpeedChange)
    videoPlayer.value.addEventListener('volumechange', handleVolumeChange)
    videoPlayer.value.addEventListener('timeupdate', handleTimeUpdate)
    //videoPlayer.value.addEventListener('durationchange', handleDurationChange)
    videoPlayer.value.addEventListener('loadedmetadata', handleDurationChange)
    scroll()
    play()
  }
}

const close = () => {
  if (videoPlayer.value) {
    videoPlayer.value.removeEventListener('playbackratechange', handleSpeedChange)
    videoPlayer.value.removeEventListener('volumechange', handleVolumeChange)
    videoPlayer.value.removeEventListener('timeupdate', handleTimeUpdate)
    //videoPlayer.value.removeEventListener('durationchange', handleDurationChange)
  }
  dialogVisible.value = false
}

const stop = () => {
  document.title = 'AList - TvBox'
  pause()
}

const handleMinute1Change = (value: number) => {
  minute1.value = value || 0
  skipStart.value = minute1.value * 60 + second1.value
  saveHistory()
}

const handleSecond1Change = (value: number) => {
  second1.value = value || 0
  skipStart.value = minute1.value * 60 + second1.value
  saveHistory()
}

const handleMinute2Change = (value: number) => {
  minute2.value = value || 0
  skipEnd.value = minute2.value * 60 + second2.value
  saveHistory()
}

const handleSecond2Change = (value: number) => {
  second2.value = value || 0
  skipEnd.value = minute2.value * 60 + second2.value
  saveHistory()
}

const scroll = () => {
  if (scrollbarRef.value) {
    scrollbarRef.value.setScrollTop(currentVideoIndex.value * 21.5)
  }
}

const startPlay = () => {
  setTimeout(skip, 500)
}

const skip = () => {
  if (videoPlayer.value) {
    if (videoPlayer.value.currentTime < skipStart.value) {
      videoPlayer.value.currentTime = skipStart.value
    }
  }
}

const play = () => {
  if (videoPlayer.value) {
    const res = videoPlayer.value.play();
    if (res) {
      res.then(_ => {
        playing.value = true
        saveHistory()
      }).catch(e => {
        console.error(e)
      })
    }
  }
}

const pause = () => {
  if (videoPlayer.value) {
    const res = videoPlayer.value.pause();
    if (res) {
      res.then(_ => {
        playing.value = false
      }).catch(e => {
        console.error(e)
      })
    }
    saveHistory()
  }
}

const forward = () => {
  if (videoPlayer.value) {
    videoPlayer.value.currentTime += 15;
    play()
  }
}

const backward = () => {
  if (videoPlayer.value) {
    videoPlayer.value.currentTime -= 15;
    play()
  }
}

const toggleMute = () => {
  if (videoPlayer.value) {
    videoPlayer.value.muted = !videoPlayer.value.muted;
  }
}

const toggleFullscreen = () => {
  if (videoPlayer.value) {
    if (!isFullscreen.value) {
      enterFullscreen(videoPlayer.value);
    } else {
      exitFullscreen();
    }
  }
}

const enterFullscreen = (element) => {
  if (element.requestFullscreen) {
    element.requestFullscreen();
  } else if (element.mozRequestFullScreen) { // Firefox
    element.mozRequestFullScreen();
  } else if (element.webkitRequestFullscreen) { // Chrome, Safari
    element.webkitRequestFullscreen();
  } else if (element.msRequestFullscreen) { // IE/Edge
    element.msRequestFullscreen();
  }
}

const exitFullscreen = () => {
  if (document.exitFullscreen) {
    document.exitFullscreen();
  } else if (document.mozCancelFullScreen) {
    document.mozCancelFullScreen();
  } else if (document.webkitExitFullscreen) {
    document.webkitExitFullscreen();
  } else if (document.msExitFullscreen) {
    document.msExitFullscreen();
  }
};

const handleFullscreenChange = () => {
  isFullscreen.value = document.fullscreenElement === videoPlayer.value;
}

const setVolume = (volume: number) => {
  currentVolume.value = volume
  localStorage.setItem('volume', volume)
  if (videoPlayer.value) {
    videoPlayer.value.volume = volume / 100.0
  }
}

const handleVolumeChange = () => {
  if (videoPlayer.value) {
    currentVolume.value = Math.round(videoPlayer.value.volume * 100)
    localStorage.setItem('volume', currentVolume.value)
  }
}

const handleTimeUpdate = () => {
  if (videoPlayer.value) {
    const time = videoPlayer.value.currentTime
    if (duration.value > skipStart.value + skipEnd.value && time + skipEnd.value > duration.value) {
      videoPlayer.value.currentTime = duration.value
    }
  }
}

const handleDurationChange = () => {
  if (videoPlayer.value) {
    duration.value = videoPlayer.value.duration
  }
}

const setSpeed = (speed: number) => {
  currentSpeed.value = speed
  if (videoPlayer.value) {
    videoPlayer.value.playbackRate = speed
  }
}

const incSpeed = () => {
  let speed = parseFloat((currentSpeed.value + 0.1).toFixed(1))
  if (speed > 2.0) {
    speed = 2.0
  }
  setSpeed(speed)
}

const decSpeed = () => {
  let speed = parseFloat((currentSpeed.value - 0.1).toFixed(1))
  if (speed < 0.5) {
    speed = 0.5
  }
  setSpeed(speed)
}

const handleSpeedChange = () => {
  if (videoPlayer.value) {
    currentSpeed.value = videoPlayer.value.playbackRate
  }
}

const updatePlayState = () => {
  if (videoPlayer.value) {
    playing.value = !videoPlayer.value.paused;
  }
}

const updateMuteState = () => {
  if (videoPlayer.value) {
    isMuted.value = videoPlayer.value.muted;
  }
}

const getPlayUrl = () => {
  const index = currentVideoIndex.value
  playUrl.value = playlist.value[index].path
  title.value = playlist.value[index].text
  document.title = title.value
  saveHistory()
}

const copyPlayUrl = () => {
  toClipboard(playUrl.value).then(() => {
    ElMessage.success('播放地址复制成功')
  })
}

const buildVlcUrl = (start: number) => {
  const id = movies.value[0].vod_id
  let url = playUrl.value
  if (id.endsWith('playlist$1')) {
    const path = getPath(id)
    const index = path.lastIndexOf('/')
    const parent = path.substring(0, index)
    url = window.location.origin + '/m3u8' + token.value + '?path=' + encodeURIComponent(parent + '$' + start)
  }
  return `vlc://${url}`
}

const openInVLC = () => {
  const url = playUrl.value
  const vlcAttempt = window.open(`vlc://${url}`, '_blank')

  setTimeout(() => {
    if (!vlcAttempt || vlcAttempt.closed) {
      try {
        window.open(`xdg-open vlc://${url}`, '_blank')
      } catch (e) {
        copyPlayUrl()
      }
    }
    pause()
  }, 500)
}

const save = () => {
  if (playing.value) {
    saveHistory()
  }
}

const saveHistory = () => {
  if (!videoPlayer.value || !dialogVisible.value) {
    return
  }
  const movie = movies.value[0]
  const id = movie.vod_id
  const name = movie.vod_name
  const index = currentVideoIndex.value
  const items = JSON.parse(localStorage.getItem('history') || '[]')
  for (let item of items) {
    if (item.id === id) {
      if (item.i == index) {
        item.c = videoPlayer.value.currentTime
      } else {
        item.c = 0
      }
      item.i = index
      item.b = skipStart.value
      item.e = skipEnd.value
      item.s = currentSpeed.value
      item.t = new Date().getTime()
      localStorage.setItem('history', JSON.stringify(items))
      return
    }
  }
  items.push({
    id: id,
    n: name,
    i: index,
    c: videoPlayer.value.currentTime,
    b: skipStart.value,
    e: skipEnd.value,
    s: currentSpeed.value,
    t: new Date().getTime()
  })
  const sorted = items.sort((a, b) => b.t - a.t).slice(0, 40);
  localStorage.setItem('history', JSON.stringify(sorted))
}

const getHistory = (id: string) => {
  const items = JSON.parse(localStorage.getItem('history') || '[]')
  for (let item of items) {
    if (item.id === id) {
      currentVideoIndex.value = item.i
      currentTime.value = item.c
      currentSpeed.value = item.s || 1
      skipStart.value = item.b || 0
      skipEnd.value = item.e || 0
      minute1.value = Math.floor(skipStart.value / 60)
      second1.value = skipStart.value % 60
      minute2.value = Math.floor(skipEnd.value / 60)
      second2.value = skipEnd.value % 60
      return
    }
  }
  currentTime.value = 0
  currentVideoIndex.value = 0
}

const formatDate = (timestamp: number): string => {
  const date: Date = new Date(timestamp);
  const year: number = date.getFullYear();
  const month: string = String(date.getMonth() + 1).padStart(2, '0');
  const day: string = String(date.getDate()).padStart(2, '0');
  const hours: string = String(date.getHours()).padStart(2, '0');
  const minutes: string = String(date.getMinutes()).padStart(2, '0');
  const seconds: string = String(date.getSeconds()).padStart(2, '0');
  return `${year}-${month}-${day} ${hours}:${minutes}:${seconds}`;
}

const formatTime = (seconds: number): string => {
  const h = Math.floor(seconds / 3600);
  const m = Math.floor(seconds % 3600 / 60);
  const s = Math.floor(seconds % 3600 % 60);

  if (h > 0) {
    return [
      h.toString().padStart(2, '0'),
      m.toString().padStart(2, '0'),
      s.toString().padStart(2, '0')
    ].join(':');
  }
  return [
    m.toString().padStart(2, '0'),
    s.toString().padStart(2, '0')
  ].join(':');
}

const loadHistory = () => {
  const items = JSON.parse(localStorage.getItem('history') || '[]')
  files.value = items.sort((a, b) => b.t - a.t).map(e => {
    return {
      vod_id: e.id,
      vod_name: e.n,
      index: e.i + 1,
      progress: formatTime(e.c),
      vod_tag: 'file',
      vod_time: formatDate(e.t)
    }
  })
  isHistory.value = true
  total.value = files.value.length
  paths.value = [{text: '🏠首页', path: '/'}, {text: '播放记录', path: '/~history'}]
}

const clearHistory = () => {
  localStorage.removeItem('history')
  files.value = []
  total.value = 0
}

const playNextVideo = () => {
  if (currentVideoIndex.value + 1 == playlist.value.length) {
    return
  }
  currentVideoIndex.value++;
  scroll()
  getPlayUrl()
  startPlay()
}

const playPrevVideo = () => {
  if (currentVideoIndex.value < 1) {
    return
  }
  currentVideoIndex.value--;
  scroll()
  getPlayUrl()
  startPlay()
}

const playVideo = (index: number) => {
  currentVideoIndex.value = index;
  getPlayUrl()
  startPlay()
}

onMounted(async () => {
  axios.get('/api/token').then(({data}) => {
    token.value = data ? '/' + (data + '').split(',')[0] : ''
    if (Array.isArray(route.params.path)) {
      const path = route.params.path.join('/')
      loadFiles('/' + path)
    } else {
      loadFiles('/')
    }
  })
  axios.get('/api/settings').then(({data}) => {
    tgChannels.value = data.tg_channels
    tgWebChannels.value = data.tg_web_channels
    tgSearch.value = data.tg_search
    tgTimeout.value = +data.tg_timeout
  })
  currentVolume.value = parseInt(localStorage.getItem('volume') || '100')
  timer = setInterval(save, 5000)
  window.addEventListener('keydown', handleKeyDown);
  document.addEventListener('fullscreenchange', handleFullscreenChange);
})

onUnmounted(() => {
  clearInterval(timer)
  window.removeEventListener('keydown', handleKeyDown);
  document.removeEventListener('fullscreenchange', handleFullscreenChange);
})
</script>

<style scoped>
video {
  width: 100%;
  height: 1080px;
}

.divider {
  margin: 15px 0;
}

.results {
  width: 100%;
  max-height: 1080px;
  overflow: auto;
}
</style>
