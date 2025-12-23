<script setup lang="ts">
import { ref, onMounted, onBeforeUnmount, watch, nextTick } from 'vue'
import * as React from 'react'
import * as ReactDOM from 'react-dom/client'

const props = defineProps<{
  initialData?: string | null
  libraryItems?: any[]
}>()

const emit = defineEmits<{
  save: [data: { elements: any[]; appState: any; files: any }]
  close: []
}>()

const containerRef = ref<HTMLDivElement | null>(null)
const isLoading = ref(true)
const loadError = ref<string | null>(null)
let root: ReactDOM.Root | null = null
let reactContainer: HTMLDivElement | null = null
let excalidrawAPI: any = null
let isUnmounting = false
let isInitialized = false
let resizeObserver: ResizeObserver | null = null

const triggerResize = () => {
  window.dispatchEvent(new Event('resize'))
}

const cleanupReact = () => {
  isUnmounting = true
  if (root) {
    try {
      root.unmount()
    } catch (e) {
      console.warn('React unmount warning:', e)
    }
    root = null
  }
  if (reactContainer && reactContainer.parentNode) {
    try {
      reactContainer.parentNode.removeChild(reactContainer)
    } catch (e) {
      // ignore
    }
    reactContainer = null
  }
  isUnmounting = false
}

const initExcalidraw = async () => {
  if (isInitialized || !containerRef.value || isUnmounting) {
    return
  }
  isInitialized = true
  isLoading.value = true
  loadError.value = null

  try {
    const ExcalidrawModule = await import('@excalidraw/excalidraw')
    const { Excalidraw } = ExcalidrawModule

    let initialElements: any[] = []
    let initialAppState: any = {}
    let initialFiles: any = null

    if (props.initialData) {
      try {
        const data = JSON.parse(props.initialData)
        initialElements = data.elements || []
        initialAppState = data.appState || {}
        initialFiles = data.files || null
      } catch (e) {
        console.error('解析初始数据失败:', e)
      }
    }

    cleanupReact()
    await nextTick()

    if (!containerRef.value || isUnmounting) return

    reactContainer = document.createElement('div')
    reactContainer.style.width = '100%'
    reactContainer.style.height = '100%'
    reactContainer.style.display = 'flex'
    containerRef.value.appendChild(reactContainer)

    const ExcalidrawComponent = React.createElement(Excalidraw, {
      initialData: {
        elements: initialElements,
        appState: {
          ...initialAppState,
          viewBackgroundColor: initialAppState.viewBackgroundColor || '#ffffff',
        },
        files: initialFiles,
        libraryItems: props.libraryItems || [],
      },
      excalidrawAPI: (api: any) => {
        excalidrawAPI = api
      },
      langCode: 'zh-CN',
      theme: 'light',
      UIOptions: {
        canvasActions: {
          export: { saveFileToDisk: true },
          loadScene: true,
          saveToActiveFile: true,
          saveAsImage: true,
          clearCanvas: true,
        },
      },
    })

    root = ReactDOM.createRoot(reactContainer)
    root.render(ExcalidrawComponent)
    isLoading.value = false

    // 使用 ResizeObserver 监听容器尺寸变化
    if (containerRef.value) {
      resizeObserver = new ResizeObserver(() => {
        triggerResize()
      })
      resizeObserver.observe(containerRef.value)
    }

    // 多次触发 resize 确保 Excalidraw 正确计算尺寸
    setTimeout(triggerResize, 100)
    setTimeout(triggerResize, 300)
    setTimeout(triggerResize, 500)
  } catch (error) {
    console.error('加载 Excalidraw 失败:', error)
    loadError.value = String(error)
    isLoading.value = false
    isInitialized = false
  }
}

const handleSave = () => {
  if (!excalidrawAPI) {
    console.error('Excalidraw API 未初始化')
    return
  }

  const elements = excalidrawAPI.getSceneElements()
  const appState = excalidrawAPI.getAppState()
  const files = excalidrawAPI.getFiles()

  emit('save', {
    elements: JSON.parse(JSON.stringify(elements)),
    appState: {
      viewBackgroundColor: appState.viewBackgroundColor,
      currentItemFontFamily: appState.currentItemFontFamily,
      zoom: appState.zoom,
      scrollX: appState.scrollX,
      scrollY: appState.scrollY,
    },
    files,
  })
}

const handleSaveEvent = () => {
  handleSave()
}

onMounted(() => {
  document.addEventListener('excalidraw:save', handleSaveEvent)
  setTimeout(() => {
    initExcalidraw()
  }, 100)
})

onBeforeUnmount(() => {
  document.removeEventListener('excalidraw:save', handleSaveEvent)
  if (resizeObserver) {
    resizeObserver.disconnect()
    resizeObserver = null
  }
  cleanupReact()
  excalidrawAPI = null
  isInitialized = false
})

watch(
  () => props.initialData,
  (newVal, oldVal) => {
    if (!isUnmounting && isInitialized && newVal !== oldVal) {
      isInitialized = false
      initExcalidraw()
    }
  },
)
</script>

<template>
  <div class="excalidraw-editor">
    <div v-if="isLoading" class="editor-loading">
      <span>正在加载 Excalidraw...</span>
    </div>
    <div v-else-if="loadError" class="editor-error">
      <span>加载失败: {{ loadError }}</span>
    </div>
    <div ref="containerRef" class="editor-container" :style="{ display: isLoading || loadError ? 'none' : 'flex' }"></div>
  </div>
</template>

<style lang="scss" scoped>
.excalidraw-editor {
  display: flex;
  flex-direction: column;
  height: 100%;
  width: 100%;
  position: relative;
}

.editor-loading,
.editor-error {
  flex: 1;
  display: flex;
  align-items: center;
  justify-content: center;
  color: #6b7280;
}

.editor-error {
  color: #ef4444;
}

.editor-container {
  flex: 1;
  width: 100%;
  height: 100%;
  position: relative;
}
</style>

<style lang="scss">
.excalidraw-editor .editor-container {
  .excalidraw {
    width: 100%;
    height: 100%;
    touch-action: none; /* 防止浏览器默认触摸行为干扰 */
  }

  .excalidraw-container {
    width: 100% !important;
    height: 100% !important;
  }

  /* 确保画布事件正常响应 */
  canvas {
    touch-action: none;
  }
}

.excalidraw .context-menu ul,
.excalidraw .context-menu li {
  list-style: none !important;
  margin: 0 !important;
  padding: 0 !important;
}

.excalidraw .context-menu ul {
  padding: 4px 0 !important;
}

/* 优化素材库预览图大小 */
.excalidraw .library-unit {
  width: 120px !important;
  height: 120px !important;
}

.excalidraw .library-unit__dragger {
  width: 100% !important;
  height: 100% !important;
}

.excalidraw .library-unit svg {
  max-width: 100% !important;
  max-height: 100% !important;
}

/* 确保 Excalidraw 弹窗显示在最上层 */
.excalidraw-modal-container,
.excalidraw .Modal,
.excalidraw .Dialog,
.excalidraw [role="dialog"] {
  z-index: 99999 !important;
}

.excalidraw .layer-ui__library,
.excalidraw .layer-ui__library-sidebar,
.excalidraw .library-menu-items-container,
.excalidraw .Stack,
.excalidraw .library-menu-items-container__items {
  overflow: visible !important;
}

.excalidraw .layer-ui__library-sidebar {
  width: 400px !important;
  min-width: 400px !important;
  max-width: 90vw !important;
}

.excalidraw .sidebar {
  width: auto !important;
  max-width: 90vw !important;
}

.excalidraw .sidebar__wrapper {
  overflow: visible !important;
}

</style>
