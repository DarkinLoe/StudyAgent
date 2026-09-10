#!/usr/bin/env node
/**
 * 本地预览服务器（无 Docker / 无 Nginx 时使用）。
 *
 * 行为刻意对齐 frontend/nginx.conf，方便本地验证生产形态：
 *   1. 托管 frontend/dist 静态资源
 *   2. SPA history 路由回退到 index.html
 *   3. /api、/actuator 反向代理到后端（默认 http://localhost:8080），透传 X-User-Id
 *
 * 用法：
 *   npm run build
 *   node serve-dist.mjs              # 默认 8081，后端默认 http://localhost:8080
 *   node serve-dist.mjs 8081 http://localhost:8080
 */
import http from 'node:http'
import { createReadStream, existsSync, statSync } from 'node:fs'
import { extname, join, normalize, sep } from 'node:path'
import { fileURLToPath } from 'node:url'

const PORT = Number(process.argv[2] || process.env.PORT || 8081)
const BACKEND = (process.argv[3] || process.env.BACKEND || 'http://localhost:8080').replace(/\/$/, '')
const ROOT = join(fileURLToPath(new URL('.', import.meta.url)), 'dist')

const CONTENT_TYPES = {
    '.html': 'text/html; charset=utf-8',
    '.js': 'application/javascript; charset=utf-8',
    '.mjs': 'application/javascript; charset=utf-8',
    '.css': 'text/css; charset=utf-8',
    '.json': 'application/json; charset=utf-8',
    '.svg': 'image/svg+xml',
    '.png': 'image/png',
    '.jpg': 'image/jpeg',
    '.ico': 'image/x-icon',
    '.woff2': 'font/woff2',
    '.map': 'application/json; charset=utf-8'
}

function sendFile(res, filePath) {
    const type = CONTENT_TYPES[extname(filePath).toLowerCase()] || 'application/octet-stream'
    const isEntry = filePath.endsWith('index.html')
    res.writeHead(200, {
        'Content-Type': type,
        // 与 Nginx 配置一致：入口 HTML 不缓存，hash 资源可缓存
        'Cache-Control': isEntry ? 'no-cache, no-store, must-revalidate' : 'public, max-age=2592000, immutable'
    })
    createReadStream(filePath).pipe(res)
}

/** 解析静态文件路径，禁止越权访问 dist 之外的目录 */
function resolveStatic(pathname) {
    const safe = normalize(decodeURIComponent(pathname)).replace(/^([/\\])+/, '')
    const target = join(ROOT, safe)
    if (!target.startsWith(ROOT + sep) && target !== ROOT) {
        return null
    }
    return target
}

async function proxy(req, res, url) {
    const target = new URL(url.pathname + url.search, BACKEND)
    const headers = { ...req.headers, host: new URL(BACKEND).host }

    let body
    if (req.method !== 'GET' && req.method !== 'HEAD') {
        const chunks = []
        for await (const chunk of req) chunks.push(chunk)
        body = Buffer.concat(chunks)
    }

    try {
        const upstream = await fetch(target, { method: req.method, headers, body })
        const buffer = Buffer.from(await upstream.arrayBuffer())
        const responseHeaders = {}
        upstream.headers.forEach((value, key) => {
            if (!['content-encoding', 'content-length', 'transfer-encoding'].includes(key)) {
                responseHeaders[key] = value
            }
        })
        res.writeHead(upstream.status, responseHeaders)
        res.end(buffer)
    } catch (e) {
        res.writeHead(502, { 'Content-Type': 'application/json; charset=utf-8' })
        res.end(JSON.stringify({ code: 1502, message: `后端不可达（${BACKEND}）：${e.message}`, data: null }))
    }
}

const server = http.createServer(async (req, res) => {
    const url = new URL(req.url, `http://${req.headers.host || 'localhost'}`)

    if (url.pathname.startsWith('/api/') || url.pathname.startsWith('/actuator/')) {
        await proxy(req, res, url)
        return
    }

    if (!existsSync(ROOT)) {
        res.writeHead(500, { 'Content-Type': 'text/plain; charset=utf-8' })
        res.end('未找到 dist 目录，请先执行：npm run build')
        return
    }

    const filePath = resolveStatic(url.pathname)
    if (filePath && existsSync(filePath) && statSync(filePath).isFile()) {
        sendFile(res, filePath)
        return
    }

    // SPA history 回退
    sendFile(res, join(ROOT, 'index.html'))
})

server.listen(PORT, () => {
    console.log(`[preview] 前端:  http://localhost:${PORT}/`)
    console.log(`[preview] 后端:  ${BACKEND} （/api、/actuator 已反向代理）`)
    console.log(`[preview] 静态目录: ${ROOT}`)
})
