import { Icon } from '@arco-design/web-vue'
import { defineComponent, h } from 'vue'

const IconFont = Icon.addFromIconFontCn({
  src: 'https://at.alicdn.com/t/c/font_4646549_xrh0h0mmvwj.js'
})

export function genIconComponent(type: string) {
  return defineComponent({
    setup() {
      return () => h(IconFont, { type })
    }
  })
}

export default IconFont
