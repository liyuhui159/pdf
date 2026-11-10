# 书名自动检索下载助手（Android V7）

安卓 APP：输入书名，检索允许下载的电子书资源；从搜索结果进入详情页，寻找真实 HTTP(S) 文件地址，并在验证后调用系统下载器。

## V7：修复“能搜到却无法下载”

1. 默认提供 **Gutendex**（Project Gutenberg 电子书目录 API）和 **Project Gutenberg** 网页搜索。旧版 example.com 默认占位模板自动迁移；用户保存的其他模板保留。
2. 自动解析 HTML 搜索结果、书籍详情页、下载页和文件链接。点击**解析并下载**后，最多继续分析 3 层、12 个页面，不会无限爬取。
3. 支持常见链接属性 a[href]、data-href、data-url、iframe[src]。网页登录分析器可读取 JavaScript 渲染后的 DOM。
4. **在下载前检查真实响应类型**，不会将登录页面、HTTP 错误页、HTML 下载页简单改名为 PDF。
5. 登录下载保留目标域名的 Cookie、User-Agent 和页面 Referer，并显示系统下载状态、完成信息及错误代码。
6. 对网页倒计时下载按钮，只在页面按钮实际可用时尝试点击，不处理验证码或绕过付费限制。

## 使用步骤

1. 打开 APP，输入书名或读取剪贴板。
2. 保留默认的公版书源，或填写你有权限访问的搜索页，每行一个 URL 模板，必须使用 {keyword} 占位。
3. 点击**同时搜索全部**或**按顺序优先搜索**。
4. 搜索结果中点击**解析并下载**。程序尝试进入相关详情页找到真实 PDF、EPUB、TXT、ZIP 等文件 URL。
5. 如果链接验证成功，Android 系统下载器将文件保存到 Download 文件夹，并由日志反馈进度与实际完成情况。
6. 如果提示未获得真实下载地址，点**打开网页**，到**网页登录分析器**登录/进入详情页/点击网站允许的下载按钮，然后分析当前页。

## 默认模板

```text
https://gutendex.com/books?search={keyword}
https://www.gutenberg.org/ebooks/search/?query={keyword}
```

Gutendex 提供 Project Gutenberg 电子书目录及文件格式链接。并非每个网站都会允许机器人检索或提供可直接使用的下载链接。

## 编译

仓库 `.github/workflows/build-apk.yml` 会运行 Java 单元测试和构建 Debug APK。GitHub Actions 成功后从对应工作流运行的 Artifacts 下载 `book-auto-downloader-debug-apk`；也可用 Android Studio 打开仓库。

## 限制及合规

仅用于自有、获授权、公版、开源或其他明确允许下载的电子书及资料。程序不绕过 DRM、验证码、付费墙或其他访问控制。动态 blob:/data: 链接、跨站点会话、受保护下载接口可能需要在网站内手动操作。
