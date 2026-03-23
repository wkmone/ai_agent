# 为前端对话栏添加复制功能

## 实现步骤

1. **导入必要的组件和图标**
   - 导入 `IconButton` 组件用于创建复制按钮
   - 导入 `ContentCopy` 图标用于复制按钮
   - 导入 `Tooltip` 组件用于显示复制成功的提示
   - 导入 `Snackbar` 和 `Alert` 组件用于显示复制成功的消息

2. **添加状态管理**
   - 添加 `copiedMessageId` 状态，用于跟踪当前复制的消息ID
   - 添加 `openSnackbar` 状态，用于控制复制成功提示的显示

3. **实现复制功能**
   - 创建 `handleCopyMessage` 函数，用于处理复制消息的逻辑
   - 使用 `navigator.clipboard.writeText` 方法复制消息内容
   - 设置 `copiedMessageId` 和 `openSnackbar` 状态，显示复制成功的提示

4. **修改消息渲染**
   - 在每条消息的右侧添加复制按钮
   - 使用 `Tooltip` 组件显示复制按钮的提示
   - 根据 `copiedMessageId` 状态显示不同的图标（复制或已复制）

5. **添加复制成功提示**
   - 添加 `Snackbar` 组件，用于显示复制成功的消息
   - 添加 `handleCloseSnackbar` 函数，用于关闭提示

## 具体修改

1. **修改导入语句**
   - 在 App.tsx 文件顶部添加必要的导入

2. **添加状态变量**
   - 在 App 组件中添加 `copiedMessageId` 和 `openSnackbar` 状态

3. **添加处理函数**
   - 添加 `handleCopyMessage` 函数
   - 添加 `handleCloseSnackbar` 函数

4. **修改消息列表渲染**
   - 在每条消息的 Box 组件中添加复制按钮
   - 调整布局，确保复制按钮正确显示

5. **添加复制成功提示**
   - 在 App 组件的返回值末尾添加 `Snackbar` 组件

## 预期效果

- 每条消息右侧会显示一个复制按钮
- 点击复制按钮后，消息内容会被复制到剪贴板
- 复制成功后会显示一个提示消息
- 复制按钮会短暂显示为已复制状态

这样，用户就可以方便地复制对话中的任何消息内容了。