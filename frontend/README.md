# AssistAgent Web

单页面智能合规问答前端，使用 React、TypeScript 和 Vite，通过 POST SSE 调用后端流式接口。

## 本地开发

```bash
npm install
npm run dev
```

开发服务器默认运行在 `http://localhost:5173`，并将 `/api` 代理到 `http://localhost:8080`。

## 生产构建

```bash
npm run build
```

如果前后端分别部署，复制 `.env.example` 为 `.env.production`，并把 `VITE_API_BASE_URL` 设置为后端地址。
