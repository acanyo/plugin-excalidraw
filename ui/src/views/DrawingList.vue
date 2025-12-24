<script setup lang="ts">
import { ref, watch } from 'vue'
import {
  VPageHeader,
  VSpace,
  VButton,
  VEmpty,
  VLoading,
  IconRefreshLine,
  VPagination,
  VCard,
  Toast,
  Dialog,
  VStatusDot
} from '@halo-dev/components'
import { useQuery } from '@tanstack/vue-query'
import { apiExcalidrawCoreApiClient, excalidrawCoreApiClient } from '../api'
import type { Drawing } from '../api/generated'
import SimpleIconsExcalidraw from '~icons/simple-icons/excalidraw'

const keyword = ref('')
const page = ref(1)
const size = ref(20)
const total = ref(0)
const selectedNames = ref<string[]>([])
const checkAll = ref(false)

const {
  data: drawings,
  isLoading: loading,
  isFetching,
  refetch,
} = useQuery({
  queryKey: ['drawings', page, size, keyword],
  queryFn: async () => {
    const { data } = await apiExcalidrawCoreApiClient.listDrawings({
      page: page.value,
      size: size.value,
      keyword: keyword.value || undefined
    })
    total.value = data.total || 0
    return data.items || []
  },
  refetchInterval: (query) => {
    const hasDeletingItem = query.state.data?.some((d) => d.metadata?.deletionTimestamp)
    return hasDeletingItem ? 1000 : false
  },
})

const handleDelete = (drawing: Drawing) => {
  Dialog.warning({
    title: '确定要删除该绘图吗？',
    description: '删除之后将无法恢复。',
    confirmType: 'danger',
    confirmText: '确定',
    cancelText: '取消',
    onConfirm: async () => {
      try {
        await excalidrawCoreApiClient.deleteDrawing({
          name: drawing.metadata?.name as string,
        })
        Toast.success('删除成功')
        refetch()
      } catch (error) {
        console.error('删除失败:', error)
        Toast.error('删除失败')
      }
    },
  })
}

const handleDeleteInBatch = () => {
  Dialog.warning({
    title: '删除所选绘图',
    description: `将删除 ${selectedNames.value.length} 个绘图，该操作不可恢复。`,
    confirmType: 'danger',
    confirmText: '确定',
    cancelText: '取消',
    onConfirm: async () => {
      try {
        const promises = selectedNames.value.map((name) => {
          return excalidrawCoreApiClient.deleteDrawing({ name })
        })
        await Promise.all(promises)
        selectedNames.value = []
        Toast.success('删除成功')
        refetch()
      } catch (error) {
        console.error('批量删除失败:', error)
        Toast.error('删除失败')
      }
    },
  })
}

const handleCheckAllChange = (e: Event) => {
  const { checked } = e.target as HTMLInputElement
  if (checked) {
    selectedNames.value = (drawings.value || [])
      .filter((d: Drawing) => !d.metadata?.deletionTimestamp)
      .map((d: Drawing) => d.metadata?.name as string)
  } else {
    selectedNames.value = []
  }
}

const isSelected = (drawing: Drawing) => {
  return selectedNames.value.includes(drawing.metadata?.name as string)
}

const toggleSelect = (drawing: Drawing) => {
  if (drawing.metadata?.deletionTimestamp) return
  const name = drawing.metadata?.name as string
  const index = selectedNames.value.indexOf(name)
  if (index > -1) {
    selectedNames.value.splice(index, 1)
  } else {
    selectedNames.value.push(name)
  }
}

const isDeleting = (drawing: Drawing) => {
  return !!drawing.metadata?.deletionTimestamp
}

const formatDatetime = (datetime?: string | null) => {
  if (!datetime) return '-'
  return new Date(datetime).toLocaleString('zh-CN', {
    month: '2-digit',
    day: '2-digit',
    hour: '2-digit',
    minute: '2-digit',
  })
}

watch(selectedNames, (newValue) => {
  const selectableCount = (drawings.value || []).filter((d: Drawing) => !d.metadata?.deletionTimestamp).length
  checkAll.value = newValue.length > 0 && newValue.length === selectableCount
}, { deep: true })
</script>

<template>
  <VPageHeader title="绘图管理">
    <template #icon>
      <SimpleIconsExcalidraw class="text-2xl" />
    </template>
  </VPageHeader>

  <div class="m-0 md:m-4">
    <VCard :body-class="['!p-0']">
      <template #header>
        <div class="block w-full bg-white px-5 py-4 border-b border-gray-100">
          <div class="relative flex flex-col flex-wrap items-start gap-4 sm:flex-row sm:items-center">
            <div class="hidden items-center sm:flex">
              <input
                v-model="checkAll"
                type="checkbox"
                class="h-4 w-4 rounded border-gray-300"
                @change="handleCheckAllChange"
              />
            </div>
            <div class="flex w-full flex-1 items-center sm:w-auto">
              <SearchInput v-if="!selectedNames.length" v-model="keyword" />
              <VSpace v-else>
                <HasPermission :permissions="['plugin:excalidraw:manage']">
                  <VButton type="danger" @click="handleDeleteInBatch">
                    删除所选 ({{ selectedNames.length }})
                  </VButton>
                </HasPermission>
                <VButton @click="selectedNames = []">
                  取消选择
                </VButton>
              </VSpace>
            </div>
            <VSpace spacing="lg" class="flex-wrap">
              <div class="flex flex-row gap-2">
                <div
                  class="group cursor-pointer rounded p-1 hover:bg-gray-200"
                  @click="refetch()"
                >
                  <IconRefreshLine
                    v-tooltip="'刷新'"
                    :class="{ 'animate-spin text-gray-900': isFetching }"
                    class="h-4 w-4 text-gray-600 group-hover:text-gray-900"
                  />
                </div>
              </div>
            </VSpace>
          </div>
        </div>
      </template>

      <VLoading v-if="loading" />

      <Transition v-else-if="!drawings?.length" appear name="fade">
        <VEmpty title="暂无绘图" message="在文章中使用 Excalidraw 创建绘图后会显示在这里">
          <template #actions>
            <VSpace>
              <VButton @click="refetch">刷新</VButton>
            </VSpace>
          </template>
        </VEmpty>
      </Transition>

      <Transition v-else appear name="fade">
        <div class="p-6">
          <div class="grid grid-cols-1 sm:grid-cols-2 md:grid-cols-3 lg:grid-cols-4 xl:grid-cols-5 gap-5">
            <div
              v-for="drawing in drawings"
              :key="drawing.metadata?.name"
              class="drawing-card group relative bg-white rounded-2xl overflow-hidden transition-all duration-300 cursor-pointer hover:scale-[1.02] hover:-translate-y-1"
              :class="{ 'selected': isSelected(drawing), 'opacity-60': isDeleting(drawing) }"
              @click="toggleSelect(drawing)"
            >
              <div class="h-44 bg-gradient-to-br from-slate-50 via-gray-50 to-zinc-100 flex items-center justify-center overflow-hidden p-4">
                <img v-if="drawing.spec?.previewUrl" :src="drawing.spec.previewUrl" loading="lazy" class="max-w-full max-h-full object-contain" alt="预览" />
                <div v-else class="flex flex-col items-center justify-center text-gray-300">
                  <svg xmlns="http://www.w3.org/2000/svg" class="h-12 w-12" fill="none" viewBox="0 0 24 24" stroke="currentColor">
                    <path stroke-linecap="round" stroke-linejoin="round" stroke-width="1" d="M4 16l4.586-4.586a2 2 0 012.828 0L16 16m-2-2l1.586-1.586a2 2 0 012.828 0L20 14m-6-6h.01M6 20h12a2 2 0 002-2V6a2 2 0 00-2-2H6a2 2 0 00-2 2v12a2 2 0 002 2z" />
                  </svg>
                </div>
              </div>
              <div class="p-4 bg-white border-t border-gray-100">
                <div class="truncate text-sm font-medium text-gray-700" :title="drawing.spec?.displayName || drawing.metadata?.name">
                  {{ drawing.spec?.displayName || drawing.metadata?.name }}
                </div>
                <div class="mt-2.5 flex items-center justify-between">
                  <span class="flex items-center text-xs text-gray-400">
                    <svg xmlns="http://www.w3.org/2000/svg" class="h-3.5 w-3.5 mr-1" fill="none" viewBox="0 0 24 24" stroke="currentColor">
                      <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M12 8v4l3 3m6-3a9 9 0 11-18 0 9 9 0 0118 0z" />
                    </svg>
                    {{ formatDatetime(drawing.metadata?.creationTimestamp) }}
                  </span>
                  <VStatusDot v-if="isDeleting(drawing)" state="warning" text="删除中" animate />
                </div>
              </div>
              <div class="absolute inset-0 bg-black/0 group-hover:bg-black/5 transition-colors duration-300"></div>
              <div class="absolute top-3 left-3 opacity-0 group-hover:opacity-100 transition-all duration-300" :class="{ '!opacity-100': isSelected(drawing) }">
                <input
                  type="checkbox"
                  :checked="isSelected(drawing)"
                  :disabled="isDeleting(drawing)"
                  class="h-4 w-4 rounded border-gray-300"
                  @click.stop
                  @change="toggleSelect(drawing)"
                />
              </div>
              <HasPermission :permissions="['plugin:excalidraw:manage']">
                <div v-if="!isDeleting(drawing)" class="absolute top-3 right-3 opacity-0 group-hover:opacity-100 transition-all duration-300 transform translate-y-1 group-hover:translate-y-0">
                  <button
                    class="p-2 rounded-lg bg-white text-gray-400 hover:text-red-500 hover:bg-red-50 shadow-md transition-all duration-200"
                    @click.stop="handleDelete(drawing)"
                    title="删除"
                  >
                    <svg xmlns="http://www.w3.org/2000/svg" class="h-4 w-4" fill="none" viewBox="0 0 24 24" stroke="currentColor">
                      <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M19 7l-.867 12.142A2 2 0 0116.138 21H7.862a2 2 0 01-1.995-1.858L5 7m5 4v6m4-6v6m1-10V4a1 1 0 00-1-1h-4a1 1 0 00-1 1v3M4 7h16" />
                    </svg>
                  </button>
                </div>
              </HasPermission>
            </div>
          </div>
        </div>
      </Transition>

      <template #footer>
        <VPagination
          v-model:page="page"
          v-model:size="size"
          page-label="页"
          size-label="条/页"
          :total-label="`共 ${total} 项数据`"
          :total="total"
          :size-options="[20, 30, 50, 100]"
        />
      </template>
    </VCard>
  </div>
</template>

<style scoped>
.svg-preview {
  width: 100%;
  height: 100%;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 16px;
}

.svg-preview :deep(svg) {
  max-width: 100%;
  max-height: 100%;
  object-fit: contain;
}

.drawing-card {
  border: 1px solid #e5e7eb;
  border-radius: 16px;
  box-shadow: 0 1px 3px rgba(0, 0, 0, 0.05);
}

.drawing-card:hover {
  border-color: #38bdf8;
  box-shadow: 0 10px 25px -5px rgba(56, 189, 248, 0.2), 0 8px 10px -6px rgba(56, 189, 248, 0.15);
}

.drawing-card.selected {
  border-color: #0ea5e9;
  border-width: 2px;
  box-shadow: 0 0 0 3px rgba(14, 165, 233, 0.15);
}
</style>
