import {
  type Editor,
  isActive,
  mergeAttributes,
  Node,
  type Range,
  VueNodeViewRenderer,
  type EditorState,
} from '@halo-dev/richtext-editor'
import ExcalidrawNodeView from './ExcalidrawNodeView.vue'
import ExcalidrawToolboxItem from './ExcalidrawToolboxItem.vue'
import { markRaw } from 'vue'
import MdiDraw from '~icons/mdi/draw'
import MdiDeleteForeverOutline from '~icons/mdi/delete-forever-outline'
import GameIconsSave from '~icons/game-icons/save'
import { deleteNode } from '../utils/delete-node'

declare module '@halo-dev/richtext-editor' {
  interface Commands<ReturnType> {
    excalidraw: {
      setExcalidraw: (attrs?: { drawingName?: string }) => ReturnType
    }
  }
}

export const ExcalidrawNode = Node.create({
  name: 'excalidraw',
  group: 'block',
  atom: true,
  draggable: true,

  addAttributes() {
    return {
      drawingName: {
        default: null,
      },
      width: {
        default: '100%',
      },
      height: {
        default: '400px',
      },
    }
  },

  parseHTML() {
    return [
      {
        tag: 'excalidraw-drawing',
      },
    ]
  },

  renderHTML({ HTMLAttributes }) {
    return ['excalidraw-drawing', mergeAttributes(this.options.HTMLAttributes, HTMLAttributes)]
  },

  addNodeView() {
    return VueNodeViewRenderer(ExcalidrawNodeView)
  },

  addCommands() {
    return {
      setExcalidraw:
        (attrs) =>
        ({ commands }) => {
          return commands.insertContent({
            type: this.name,
            attrs,
          })
        },
    }
  },

  addOptions() {
    return {
      ...this.parent?.(),
      HTMLAttributes: {},
      getToolboxItems({ editor }: { editor: Editor }) {
        return {
          priority: 59,
          component: markRaw(ExcalidrawToolboxItem),
          props: {
            editor,
          },
        }
      },
      getCommandMenuItems() {
        return {
          priority: 50,
          icon: markRaw(MdiDraw),
          title: 'Excalidraw 绘图',
          keywords: ['excalidraw', 'draw', 'huitu', '绘图', '画图', '白板'],
          command: ({ editor, range }: { editor: Editor; range: Range }) => {
            editor.chain().focus().deleteRange(range).setExcalidraw().run()
          },
        }
      },
      getBubbleMenu({ editor }: { editor: Editor }) {
        return {
          pluginKey: 'excalidraw-bubble-menu',
          shouldShow: ({ state }: { state: EditorState }) => {
            return isActive(state, 'excalidraw')
          },
          items: [
            {
              priority: 10,
              props: {
                icon: markRaw(MdiDraw),
                title: '编辑绘图',
                action: () => {
                  const event = new CustomEvent('excalidraw:edit')
                  document.dispatchEvent(event)
                },
              },
            },
            {
              priority: 15,
              props: {
                icon: markRaw(GameIconsSave),
                title: '保存到附件',
                action: () => {
                  const event = new CustomEvent('excalidraw:upload')
                  document.dispatchEvent(event)
                },
              },
            },
            {
              priority: 20,
              props: {
                icon: markRaw(MdiDeleteForeverOutline),
                title: '删除',
                action: () => {
                  deleteNode('excalidraw', editor)
                },
              },
            },
          ],
        }
      },
      getDraggable() {
        return true
      },
    }
  },
})

export default ExcalidrawNode
