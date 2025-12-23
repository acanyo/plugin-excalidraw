<script setup lang="ts">
import { NodeViewWrapper, nodeViewProps } from '@halo-dev/richtext-editor'
import { ref, onMounted, onBeforeUnmount, watch, computed, Teleport } from 'vue'
import { VButton, Toast } from '@halo-dev/components'
import ExcalidrawEditor from './ExcalidrawEditor.vue'
import { excalidrawCoreApiClient, apiExcalidrawCoreApiClient } from '../api'

const props = defineProps(nodeViewProps)

const showEditor = ref(false)
const drawingData = ref<string | null>(null)
const svgPreview = ref<string | null>(null)
const isLoading = ref(false)
const isSaving = ref(false)
const editorRef = ref<InstanceType<typeof ExcalidrawEditor> | null>(null)
const isExistingDrawing = ref(false)
const libraryItems = ref<any[]>([])
const inputFileName = ref('')
const pendingSaveData = ref<{ jsonData: string; svgData: string } | null>(null)
const pendingUpload = ref(false)
const nameDialogRef = ref<HTMLDialogElement | null>(null)
const nameInputRef = ref<HTMLInputElement | null>(null)

const showNameDialog = () => {
  nameDialogRef.value?.showModal()
  setTimeout(() => nameInputRef.value?.focus(), 100)
}

const closeNameDialog = () => {
  nameDialogRef.value?.close()
}

const fontCache = new Map<string, string>()

const drawingName = computed(() => props.node.attrs.drawingName)
const selected = computed(() => props.selected)

const FONT_CDN_BASE = 'https://cdn.jsdmirror.com/npm/@excalidraw/excalidraw@0.17.6/dist/excalidraw-assets/'
const FONT_FILES: Record<string, string> = {
  Virgil: 'Virgil.woff2',
  Cascadia: 'Cascadia.woff2',
  Assistant: 'Assistant-Regular.woff2',
}

const embedFontsInSvg = async (svgString: string): Promise<string> => {
  const usedFonts = Object.keys(FONT_FILES).filter(
    (font) => svgString.includes(`"${font}"`) || svgString.includes(`'${font}'`)
  )
  if (usedFonts.length === 0) return svgString

  const fontData = await Promise.all(
    usedFonts.map(async (fontName) => {
      try {
        if (fontCache.has(fontName)) {
          return { fontName, base64: fontCache.get(fontName)! }
        }
        const res = await fetch(FONT_CDN_BASE + FONT_FILES[fontName])
        if (!res.ok) return null
        const blob = await res.blob()
        const base64 = await new Promise<string>((resolve) => {
          const reader = new FileReader()
          reader.onloadend = () => resolve(reader.result as string)
          reader.readAsDataURL(blob)
        })
        fontCache.set(fontName, base64)
        return { fontName, base64 }
      } catch {
        return null
      }
    })
  )

  let result = svgString
  fontData.filter(Boolean).forEach(({ fontName, base64 }: any) => {
    const regex = new RegExp(
      `(@font-face\\s*\\{[^}]*font-family:\\s*["']?${fontName}["']?[^}]*src:\\s*)url\\([^)]+\\)`,
      'g'
    )
    result = result.replace(regex, `$1url("${base64}")`)
  })
  return result
}

const openEditor = async () => {
  await loadLibraryItems()
  showEditor.value = true
}

const closeEditor = () => {
  showEditor.value = false
}

const handleFooterSave = () => {
  const event = new CustomEvent('excalidraw:save')
  document.dispatchEvent(event)
}

const handleClick = (e: MouseEvent) => {
  e.stopPropagation()
  props.editor.commands.setNodeSelection(props.getPos())
}

const handleDblClick = (e: MouseEvent) => {
  e.stopPropagation()
  e.preventDefault()
  openEditor()
}

const handleEditEvent = () => {
  if (selected.value) {
    openEditor()
  }
}

const handleUploadEvent = () => {
  if (selected.value) {
    uploadToAttachment()
  }
}

onMounted(() => {
  document.addEventListener('excalidraw:edit', handleEditEvent)
  document.addEventListener('excalidraw:upload', handleUploadEvent)
})

onBeforeUnmount(() => {
  document.removeEventListener('excalidraw:edit', handleEditEvent)
  document.removeEventListener('excalidraw:upload', handleUploadEvent)
})

const handleSave = async (data: { elements: any[]; appState: any; files: any }) => {
  if (isSaving.value) return
  
  isSaving.value = true
  try {
    const jsonData = JSON.stringify(data)
    drawingData.value = jsonData

    const { exportToSvg } = await import('@excalidraw/excalidraw')
    const svg = await exportToSvg({
      elements: data.elements,
      appState: {
        ...data.appState,
        exportWithDarkMode: false,
        exportEmbedScene: true,
      },
      files: data.files,
      exportPadding: 10,
    })
    
    const embeddedSvg = await embedFontsInSvg(svg.outerHTML)
    svgPreview.value = embeddedSvg

    if (!drawingName.value) {
      pendingSaveData.value = { jsonData, svgData: embeddedSvg }
      pendingUpload.value = false
      inputFileName.value = ''
      showNameDialog()
      isSaving.value = false
      return
    }

    await saveDrawing(jsonData, embeddedSvg)
    closeEditor()
  } catch (error) {
    console.error('保存绘图失败:', error)
    Toast.error('保存失败')
  } finally {
    isSaving.value = false
  }
}

const confirmFileName = async () => {
  const name = inputFileName.value.trim() || `drawing-${Date.now()}`
  closeNameDialog()
  
  isSaving.value = true
  try {
    if (pendingSaveData.value) {
      await saveDrawingWithName(name, pendingSaveData.value.jsonData, pendingSaveData.value.svgData)
      
      if (pendingUpload.value) {
        await doUploadToAttachment(name, pendingSaveData.value.jsonData)
      }
      
      pendingSaveData.value = null
      closeEditor()
    }
  } catch (error) {
    console.error('保存失败:', error)
    Toast.error('保存失败')
  } finally {
    isSaving.value = false
  }
}

const cancelNameModal = () => {
  closeNameDialog()
  pendingSaveData.value = null
  pendingUpload.value = false
}

const saveDrawingWithName = async (name: string, jsonData: string, svgData: string) => {
  const drawingPayload: any = {
    apiVersion: 'excalidraw.xhhao.com/v1alpha1',
    kind: 'Drawing',
    metadata: { name },
    spec: { displayName: name, data: jsonData, svg: svgData },
  }
  
  await excalidrawCoreApiClient.createDrawing({ drawing: drawingPayload })
  isExistingDrawing.value = true
  props.updateAttributes({ drawingName: name })
  Toast.success('保存成功')
}

const saveDrawing = async (jsonData: string, svgData: string) => {
  const name = drawingName.value!
  const isNewDrawing = !isExistingDrawing.value

  const drawingPayload: any = {
    apiVersion: 'excalidraw.xhhao.com/v1alpha1',
    kind: 'Drawing',
    metadata: { name },
    spec: { displayName: name, data: jsonData, svg: svgData },
  }
  if (isExistingDrawing.value) {
    try {
      const { data: existing } = await excalidrawCoreApiClient.getDrawing({ name })
      drawingPayload.metadata = existing.metadata
      drawingPayload.spec = { ...existing.spec, displayName: name, data: jsonData, svg: svgData }
      await excalidrawCoreApiClient.updateDrawing({ name, drawing: drawingPayload })
    } catch (e) {
      await excalidrawCoreApiClient.createDrawing({ drawing: drawingPayload })
      isExistingDrawing.value = true
    }
  } else {
    await excalidrawCoreApiClient.createDrawing({ drawing: drawingPayload })
    isExistingDrawing.value = true
  }

  if (isNewDrawing) {
    props.updateAttributes({ drawingName: name })
  }
}

const isUploading = ref(false)

const doUploadToAttachment = async (fileName: string, jsonContent: string) => {
  const { data } = await apiExcalidrawCoreApiClient.uploadExcalidrawFile({
    uploadRequest: { fileName, jsonContent, userName: '' }
  })
  if (data.url === 'ok') {
    Toast.success('已保存到附件库')
  } else if (data.url === '未配置存储策略') {
    Toast.warning('请先在插件设置中配置附件存储策略')
  } else {
    Toast.error('上传失败')
  }
}

const uploadToAttachment = async () => {
  if (isUploading.value) return
  
  if (!drawingName.value || !drawingData.value) {
    if (!drawingData.value) {
      Toast.warning('请先保存绘图')
      return
    }
    pendingSaveData.value = { jsonData: drawingData.value, svgData: svgPreview.value || '' }
    pendingUpload.value = true
    inputFileName.value = ''
    showNameDialog()
    return
  }
  
  isUploading.value = true
  try {
    await doUploadToAttachment(drawingName.value, drawingData.value)
  } catch (e) {
    console.error('上传失败:', e)
    Toast.error('上传失败')
  } finally {
    isUploading.value = false
  }
}

const loadDrawing = async () => {
  if (!drawingName.value) return

  isLoading.value = true
  try {
    const { data: drawing } = await excalidrawCoreApiClient.getDrawing({ name: drawingName.value })
    if (drawing.spec?.data) {
      drawingData.value = drawing.spec.data
      isExistingDrawing.value = true
      if (drawing.spec.svg) {
        svgPreview.value = drawing.spec.svg
      } else {
        await generatePreview(drawing.spec.data)
      }
    }
  } catch (error: any) {
    if (error?.response?.status !== 404) {
      console.error('加载绘图失败:', error)
    }
    isExistingDrawing.value = false
  } finally {
    isLoading.value = false
  }
}

const generatePreview = async (jsonData: string) => {
  try {
    const data = JSON.parse(jsonData)
    const { exportToSvg } = await import('@excalidraw/excalidraw')
    const svg = await exportToSvg({
      elements: data.elements || [],
      appState: {
        ...(data.appState || {}),
        exportWithDarkMode: false,
        exportEmbedScene: true,
      },
      files: data.files || null,
      exportPadding: 10,
    })
    const embeddedSvg = await embedFontsInSvg(svg.outerHTML)
    svgPreview.value = embeddedSvg
  } catch (error) {
    console.error('生成预览失败:', error)
  }
}

const loadLibraryItems = async () => {
  try {
    const { data } = await apiExcalidrawCoreApiClient.listDrawings({
      page: 1,
      size: 100,
    })
    const items = (data.items || [])
      .filter((drawing: any) => drawing.spec?.data)
      .map((drawing: any) => {
        try {
          const drawingData = JSON.parse(drawing.spec.data)
          return {
            id: drawing.metadata?.name,
            status: 'published',
            elements: drawingData.elements || [],
            created: new Date(drawing.metadata?.creationTimestamp || Date.now()).getTime(),
          }
        } catch {
          return null
        }
      })
      .filter(Boolean)
    libraryItems.value = items
  } catch (error) {
    console.error('加载素材库失败:', error)
  }
}

onMounted(() => {
  if (drawingName.value) {
    loadDrawing()
  }
})

watch(
  () => drawingName.value,
  (newName) => {
    if (newName) {
      loadDrawing()
    }
  },
)
</script>

<template>
  <NodeViewWrapper class="excalidraw-node-wrapper" :class="{ 'is-selected': selected }">
    <div
      class="excalidraw-container"
      :style="{ height: node.attrs.height }"
      @click="handleClick"
      @dblclick="handleDblClick"
    >
      <div v-if="isLoading" class="excalidraw-loading">
        <span>加载中...</span>
      </div>

      <div v-else-if="svgPreview" class="excalidraw-preview">
        <div class="svg-container" v-html="svgPreview"></div>
        <div class="preview-hint">
          <span>双击编辑 · 单击选中后可删除</span>
        </div>
      </div>

      <div v-else class="excalidraw-empty">
        <div class="empty-content">
          <svg
            xmlns="http://www.w3.org/2000/svg"
            width="48"
            height="48"
            viewBox="0 0 24 24"
            fill="none"
            stroke="currentColor"
            stroke-width="1.5"
          >
            <path d="M12 19l7-7 3 3-7 7-3-3z" />
            <path d="M18 13l-1.5-7.5L2 2l3.5 14.5L13 18l5-5z" />
            <path d="M2 2l7.586 7.586" />
            <circle cx="11" cy="11" r="2" />
          </svg>
          <span>双击创建 Excalidraw 绘图</span>
        </div>
      </div>
    </div>

    <Teleport to="body">
      <div v-if="showEditor" class="excalidraw-fullscreen-overlay">
        <div class="excalidraw-fullscreen-container">
          <ExcalidrawEditor
            :initial-data="drawingData"
            :library-items="libraryItems"
            @save="handleSave"
            @close="closeEditor"
          />
          <div class="excalidraw-floating-buttons">
            <VButton :disabled="isSaving" @click="closeEditor">取消</VButton>
            <VButton 
              type="default" 
              :loading="isUploading" 
              :disabled="isSaving || isUploading" 
              @click="uploadToAttachment"
            >
              保存到附件
            </VButton>
            <VButton type="secondary" class="save-btn" :loading="isSaving" :disabled="isSaving" @click="handleFooterSave">
              {{ isSaving ? '保存中...' : '保存' }}
            </VButton>
          </div>
        </div>
      </div>
    </Teleport>

    <Teleport to="body">
      <dialog ref="nameDialogRef" class="name-dialog" @close="cancelNameModal">
        <div class="name-dialog-header">
          <h3>输入绘图名称</h3>
          <button class="close-btn" @click="cancelNameModal">×</button>
        </div>
        <div class="name-dialog-content">
          <p class="name-dialog-hint">请输入绘图名称，留空将自动生成</p>
          <input
            ref="nameInputRef"
            v-model="inputFileName"
            type="text"
            class="name-input"
            placeholder="例如：my-drawing"
            @keyup.enter="confirmFileName"
          />
        </div>
        <div class="name-dialog-footer">
          <button class="dialog-btn cancel-btn" @click="cancelNameModal">取消</button>
          <button class="dialog-btn confirm-btn" :disabled="isSaving" @click="confirmFileName">
            {{ isSaving ? '保存中...' : '确定' }}
          </button>
        </div>
      </dialog>
    </Teleport>
  </NodeViewWrapper>
</template>

<style lang="scss" scoped>
.excalidraw-node-wrapper {
  width: 100%;
  margin: 1rem 0;
}

.excalidraw-container {
  border: 2px dashed #e5e7eb;
  border-radius: 8px;
  overflow: hidden;
  background: #fafafa;
  position: relative;
  min-height: 200px;
  cursor: pointer;
  transition: all 0.2s;

  &:hover {
    border-color: #d1d5db;
  }
}

.excalidraw-loading {
  display: flex;
  align-items: center;
  justify-content: center;
  height: 100%;
  min-height: 200px;
  color: #9ca3af;
}

.excalidraw-preview {
  width: 100%;
  height: 100%;
  position: relative;
  overflow: hidden;

  .svg-container {
    width: 100%;
    min-height: 200px;
    max-height: 500px;
    display: flex;
    align-items: center;
    justify-content: center;
    padding: 1rem;
    box-sizing: border-box;

    :deep(svg) {
      max-width: 100%;
      max-height: 100%;
      width: auto;
      height: auto;
      display: block;
      object-fit: contain;
    }
  }

  .preview-hint {
    position: absolute;
    bottom: 0;
    left: 0;
    right: 0;
    padding: 8px;
    background: linear-gradient(transparent, rgba(0, 0, 0, 0.6));
    color: white;
    font-size: 12px;
    text-align: center;
    opacity: 0;
    transition: opacity 0.2s;
  }

  &:hover .preview-hint {
    opacity: 1;
  }
}

.excalidraw-empty {
  display: flex;
  align-items: center;
  justify-content: center;
  height: 100%;
  min-height: 200px;
  transition: all 0.2s;

  &:hover {
    background: #f3f4f6;
  }

  .empty-content {
    display: flex;
    flex-direction: column;
    align-items: center;
    gap: 0.75rem;
    color: #9ca3af;

    svg {
      opacity: 0.5;
    }

    span {
      font-size: 0.875rem;
    }
  }
}
</style>

<style lang="scss">
.excalidraw-fullscreen-overlay {
  position: fixed;
  top: 0;
  left: 0;
  width: 100vw;
  height: 100vh;
  z-index: 9999;
  background: #fff;
}

.excalidraw-fullscreen-container {
  width: 100%;
  height: 100%;
  position: relative;
}

/* 浮动按钮样式*/
.excalidraw-floating-buttons {
  position: absolute;
  top: 16px;
  right: 106px;
  display: flex;
  gap: 8px;
  z-index: 4;
  
  .save-btn {
    background: #1f2937 !important;
    color: #fff !important;
    border-color: #1f2937 !important;

    &:hover {
      background: #374151 !important;
      border-color: #374151 !important;
    }
  }
}

</style>
<style lang="scss">
.name-dialog {
  border: none;
  border-radius: 16px;
  padding: 0;
  box-shadow: 
    0 0 0 1px rgba(0, 0, 0, 0.05),
    0 10px 15px -3px rgba(0, 0, 0, 0.1),
    0 25px 50px -12px rgba(0, 0, 0, 0.25);
  max-width: 420px;
  width: 90%;
  overflow: hidden;

  &::backdrop {
    background: rgba(17, 24, 39, 0.6);
    backdrop-filter: blur(4px);
  }
}

.name-dialog-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 20px 24px 16px;

  h3 {
    margin: 0;
    font-size: 18px;
    font-weight: 600;
    color: #111827;
    letter-spacing: -0.025em;
  }

  .close-btn {
    background: none;
    border: none;
    font-size: 24px;
    line-height: 1;
    color: #9ca3af;
    cursor: pointer;
    padding: 4px;
    border-radius: 6px;
    display: flex;
    align-items: center;
    justify-content: center;
    transition: all 0.15s;

    &:hover {
      background: #f3f4f6;
      color: #4b5563;
    }
  }
}

.name-dialog-content {
  padding: 0 24px 24px;
}

.name-dialog-hint {
  margin: 0 0 16px;
  color: #6b7280;
  font-size: 14px;
  line-height: 1.5;
}

.name-input {
  display: block;
  width: 100%;
  padding: 14px 16px;
  border: 2px solid #e5e7eb;
  border-radius: 12px;
  font-size: 16px;
  line-height: 1.5;
  outline: none;
  transition: all 0.2s ease;
  box-sizing: border-box;
  background: #fff;
  color: #111827;
  font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, sans-serif;

  &:hover {
    border-color: #6366f1;
  }

  &:focus {
    border-color: #6366f1;
    box-shadow: 
      0 0 0 4px rgba(99, 102, 241, 0.15),
      inset 0 1px 2px rgba(0, 0, 0, 0.05);
  }

  &::placeholder {
    color: #9ca3af;
    font-weight: 400;
  }
}

.name-dialog-footer {
  display: flex;
  justify-content: flex-end;
  gap: 12px;
  padding: 16px 24px;
  background: #f9fafb;
  border-top: 1px solid #e5e7eb;
}

.dialog-btn {
  padding: 10px 20px;
  border-radius: 8px;
  font-size: 14px;
  font-weight: 500;
  cursor: pointer;
  transition: all 0.15s ease;
  line-height: 1.25;

  &:disabled {
    opacity: 0.5;
    cursor: not-allowed;
  }
}

.cancel-btn {
  background: #fff;
  border: 1px solid #d1d5db;
  color: #374151;

  &:hover:not(:disabled) {
    background: #f3f4f6;
    border-color: #9ca3af;
  }
}

.confirm-btn {
  background: linear-gradient(135deg, #6366f1 0%, #4f46e5 100%);
  border: none;
  color: #fff;
  box-shadow: 0 1px 2px rgba(79, 70, 229, 0.3);

  &:hover:not(:disabled) {
    background: linear-gradient(135deg, #4f46e5 0%, #4338ca 100%);
    box-shadow: 0 4px 12px rgba(79, 70, 229, 0.4);
    transform: translateY(-1px);
  }
  
  &:active:not(:disabled) {
    transform: translateY(0);
  }
}
</style>
