import { rsbuildConfig } from '@halo-dev/ui-plugin-bundler-kit';
import Icons from "unplugin-icons/rspack";
import { pluginSass } from "@rsbuild/plugin-sass";
import { pluginReact } from "@rsbuild/plugin-react";
import type { RsbuildConfig } from "@rsbuild/core";

export default rsbuildConfig({
  rsbuild: {
    resolve: {
      alias: {
        "@": "./src",
      },
    },
    source: {
      define: {
        'process.env': JSON.stringify({}),
      },
    },
    plugins: [pluginSass(), pluginReact()],
    tools: {
      rspack: {
        plugins: [Icons({ compiler: "vue3" })],
      },
    },
  },
}) as RsbuildConfig
