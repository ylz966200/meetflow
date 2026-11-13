# 招聘页面采集原型

需要 Node.js 18 或更高版本。在项目根目录运行：

```powershell
node job-monitor/monitor.mjs https://example.com/job/123
```

支持一次传入多个公开职位详情地址。程序先检查 robots.txt，读取页面中的 JobPosting 结构化数据，并按日期写入 `job-monitor/data/`。同一天的相同链接会覆盖旧快照。未识别的数据和访问错误均标记为待核实；不将访问失败判断为职位失效。程序不读取登录信息，不尝试验证码或绕过访问限制。

这是原型，不支持自动发现职位、BOSS 登录、招聘有效性核验、跨日对比、周报或学习计划调整。结构化数据需要人工复核。BOSS 职位 URL 的实际测试被 robots.txt 禁止，因此此程序无法采集该 URL。
