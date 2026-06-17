## 已完成
- [x] 卡片模板添加夜间模式支持（2026-06-15）

## 待办

### 功能开发

- [x] [剪贴板锁定 + PopupActivity 状态保留](./docs/feature-plan/2026-0617-Clipboard_Lock_Plan.md)（2026-06-17 已完成）
    - [x] 新增图标资源（ic_lock_closed.xml / ic_lock_open.xml）和中英文字符串
    - [x] Settings.java：新增 clipboard_locked 布尔字段 + 6 个 Popup 状态持久化字段
    - [x] CBWatcherService.java：performClipboardCheck() 加锁检查；通知 addAction() 锁定按钮；提取 buildNotification() 独立方法；处理 ACTION_TOGGLE_LOCK / ACTION_UPDATE_NOTIFICATION 意图
    - [x] PopupActivity.java：底部锁定按钮与 updateLockButtonUI()；onPause() 保存状态；onNewIntent() + resetPopupState() 处理 Activity 复用；onWindowFocusChanged() 加锁检查与聚焦时 UI 同步
    - [x] AndroidManifest.xml：移除 noHistory（Popup 切走后不再立即销毁）
    - [x] activity_popup.xml：底部 footer 新增锁定 ImageButton
    - [x] 修复 CBWatcherService 独立进程导致 Settings 跨进程不同步（移除 android:process=":CBService"）
    - [ ] 功能实现完成后补充单元测试（引入 Robolectric；覆盖锁定判断逻辑、tags 序列化、状态保存/恢复、关键路径 Espresso 端到端测试）

- [ ] [笔记持久化与笔记按钮 UI 状态指示](./docs/feature-plan/2026-0617-Note_Persistence_Plan.md)（2026-06-17 待开发）
    - [ ] PopupActivity.java：删除添加卡片成功后清空笔记的代码（mNoteEditedByUser = ""）
    - [ ] PopupActivity.java：新增 updateNoteButtonUI() 根据笔记内容调整按钮透明度（有空内容时 alpha 1.0，空时 0.6）
    - [ ] PopupActivity.java：在 setupEditNoteDialog() 确认回调和 onResume() 中调用 updateNoteButtonUI()
    - [ ] 功能实现完成后补充单元测试（覆盖笔记保存/恢复逻辑、按钮状态同步）

### 问题分析

- [ ] ISSUE-[2026-0613-1134-OneClick_TwoCards_Cause_Analysis](./docs/bug-analysis/2026-0613-1134-OneClick_TwoCards_Cause_Analysis/2026-0613-1134-OneClick_TwoCards_Cause_Analysis.md)
    - [ ] 分析其实现
    - [ ] 学习用到的技术和代码
    - [ ] 添加单元测试
    - [ ] 按照《重构》的好代码的要求，重构优化代码
    - [ ] 更改业务逻辑，改成：添加卡片时，可以选择其中一个或者两个都选去生成对应anki卡片