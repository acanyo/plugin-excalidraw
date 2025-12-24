// Excalidraw 资源路径
;(window as any).EXCALIDRAW_ASSET_PATH = 'https://cdn.jsdmirror.com/npm/@excalidraw/excalidraw@0.17.6/dist/'

import { definePlugin } from '@halo-dev/console-shared'
import { markRaw } from 'vue'
import { ExcalidrawNode } from './editor/ExcalidrawNode'
import DrawingList from './views/DrawingList.vue'
import SimpleIconsExcalidraw from '~icons/simple-icons/excalidraw'

export default definePlugin({
  components: {},
  routes: [
    {
      parentName: 'Root',
      route: {
        path: '/excalidraw',
        name: 'Excalidraw',
        component: DrawingList,
        meta: {
          title: '绘图管理',
          searchable: true,
          permissions: ['plugin:excalidraw:view'],
          menu: {
            name: '绘图管理',
            group: 'content',
            icon: markRaw(SimpleIconsExcalidraw),
            priority: 10,
          },
        },
      },
    },
  ],
  extensionPoints: {
    'default:editor:extension:create': () => {
      return [ExcalidrawNode]
    },
  },
})
