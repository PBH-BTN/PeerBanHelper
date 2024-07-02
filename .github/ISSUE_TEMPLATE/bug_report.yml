name: '错误报告'
description: '报告与 PeerBanHelper 有关的程序错误'

title: '[BUG] '
labels:
  - 'Bug'

body:
  - type: 'markdown'
    attributes:
      value: |-
        ## 请注意
        此表单**仅用于反馈错误**，如果是其它类型的反馈，请选择底部的 Open blank issue。

        请尽可能完整且详细地填写所有表单项，以便我们以最高效率并准确的排查故障和诊断问题
  - type: 'textarea'
    attributes:
      label: '版本号'
      description: |-
        输入您正在使用 PeerBanHelper 的版本号，通常可在窗口标题或者 WebUI 页面的底部找到
      placeholder: 'vX.X.X'
    validations:
      required: true
  - type: 'textarea'
    attributes:
      label: '操作系统平台和系统架构'
      description: |-
        输入 PBH 所在的操作系统平台（不是下载器），例如：Windows、Debian、iStoreOS 等  
        此外，您还需要输入系统架构。如果是 x86 设备，则通常为 x64；如果是 arm 设备，则通常为 arm64。请根据实际情况填写。如果不知道，也可以不写系统架构类型。
      placeholder: '操作系统平台名称……'
    validations:
      required: true
  - type: 'textarea'
    attributes:
      label: '部署方式'
      description: |-
        输入您部署 PeerBanHelper 方式，官方支持的有如下几种方式：
        * Windows 安装程序（通过 .exe 安装）
        * Windows 绿色懒人包（解压即用的 .zip 文件）
        * Docker 镜像
      placeholder: '部署方式……'
    validations:
      required: true
  - type: 'textarea'
    attributes:
      label: '关联的下载器类型'
      description: |-
        输入您的 PBH 关联的下载器类型，例如：
        * qBittorrent
        * Transmission
        * Deluge
        * ... 等
      placeholder: '我添加的下载器有……'
    validations:
      required: true
  - type: 'textarea'
    attributes:
      label: '问题描述'
      description: |-
        在此详细的描述你所遇到的问题
    validations:
      required: true
  - type: 'textarea'
    attributes:
      label: '复现步骤'
      description: '如果你清楚如何复现此故障，也欢迎告诉我们，帮助我们更快的复现它。如果它是一个偶尔才会出现的错误，请告诉我们它通常可能会在什么情况下出现。'
      placeholder: |-
        1. 第一步
        2. ...
        3. 出现 BUG!
    validations:
      required: true
  - type: 'textarea'
    attributes:
      label: '截图/日志文件'
      description: |-
        如果你有一些截图或者日志能够更好的解释你所提出的问题，你可以在这里上传。
      placeholder: '<截图文件>'
    validations:
      required: false
  - type: 'textarea'
    attributes:
      label: '额外信息'
      description: '如果你还有其他觉得可能对排查和解决此问题有帮助的更多信息，可以在这里告诉我们'
      placeholder: ''
  - type: checkboxes
    id: check-list
    attributes:
      label: 检查清单
      description: 请检查并勾选下面的所有的复选框，如果您没有这样做，我们可能会直接关闭这个 Issue
      options:
        - label: "我确定正在运行 Github Releases 中的最新的正式版本 PeerBanHelper"
          required: false
        - label: "我确定我所添加的下载器已满足 README 中的前置要求（如版本号和插件）"
          required: false
        - label: "我确定我所提到的问题，均未在 README 和 WIKI 中有所解答"
          required: false
        - label: "我确定我没有检查这个检查清单，只是闭眼选中了所有的复选框"
          required: false
        - label: "我确定这不是一个与安全有关的安全漏洞，它可以被安全的公开报告"
          required: false
        - label: "我确定我已知悉，如果我没有正确地填写问题报告表单，则 Issue 可能会被关闭"
          required: false
