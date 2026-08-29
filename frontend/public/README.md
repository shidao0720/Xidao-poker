# 前端静态设计资源

该目录用于存放无需 Vite 编译、需要按原文件名公开访问的前端设计资源。构建时，
`public` 下的文件会被复制到站点根目录，因此代码中不要写 `public`：

```tsx
<img src="/assets/images/cards/as.webp" alt="黑桃 A" />
<audio src="/assets/audio/music/table-ambience.ogg" />
```

## 目录约定

```text
assets/
├── images/
│   ├── cards/        手牌、牌背和牌面图
│   ├── backgrounds/  大厅、牌桌等背景图
│   ├── avatars/      默认头像和头像框（头像 key 由账户资料保存）
│   └── ui/           筹码、按钮纹理等界面素材
├── audio/
│   ├── music/        循环播放的背景音乐
│   └── sfx/          发牌、下注、胜负等短音效
└── fonts/            获得再分发授权的本地字体
```

## 资源规范

- 文件名统一使用小写英文、数字和连字符；扑克牌可固定使用 `as.webp`、`10h.webp` 这类牌面编码。
- 图片优先使用 WebP 或 SVG；需要透明背景时使用 WebP 或 PNG。
- 背景音乐优先提供 OGG，并按兼容需要补充 MP3；短音效优先使用 OGG。
- 音乐应适合无缝循环，播放音量由前端统一控制，不直接把素材归一化到过高响度。
- 只提交原创、公共领域或已取得再分发授权的资源，并在本文件追加来源和许可证。
- 单个大文件提交前先评估体积；若仓库明显膨胀，再启用 Git LFS。

## 素材登记

| 路径 | 用途 | 来源/作者 | 许可证 |
| --- | --- | --- | --- |
| `assets/images/ui/fate-stay-poker-logo.png` | B 主题标题 Logo | shidao0720 提供 | 项目自有素材 |
| `assets/images/ui/fate-stay-poker-logo-source.png` | Logo 原始黑底源文件 | shidao0720 提供 | 项目自有素材 |
| `assets/images/avatars/*` | 账户和牌桌预置头像（SVG/JPG） | Xidao Poker | 项目自有素材 |
