<script lang="ts" setup>
import type { Editor } from '@halo-dev/richtext-editor'
import { ToolboxItem } from '@halo-dev/richtext-editor'
import { markRaw } from 'vue'
import MdiDraw from '~icons/mdi/draw'

const props = defineProps<{
  editor: Editor
}>()

const handleClick = () => {
  props.editor.chain().focus().setExcalidraw().run()
}
</script>

<template>
  <ToolboxItem
    :editor="editor"
    :icon="markRaw(MdiDraw)"
    title="Excalidraw"
    @click="handleClick"
  />
</template>
