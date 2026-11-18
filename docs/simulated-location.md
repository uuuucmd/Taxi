# 司机模拟定位（测试用）

用于在没有真实 GPS 的情况下验证本地派单召回。司机工作台会显示“模拟定位”面板，仅在开发模式或显式开启时可见。

司机工作台是唯一的定位上报入口；“车辆与资质”页面只负责车辆绑定和登记，不再保存定位。附近订单筛选使用工作台最近一次上报的车辆位置。

## 开关

- 开发模式：`npm run dev` 自动显示。
- 生产构建：在仓库根目录 `.env` 中设置 `VITE_ENABLE_LOCATION_SIMULATOR=true` 后重新构建。
- 后端需要 `LOCAL_SIMULATION=true`（默认值）。

## 使用步骤

1. 司机账号完成车辆绑定，并处于可接单状态。
2. 打开司机工作台，在“模拟定位”面板选择预设点，或点击“地图点选”在地图上选点；经纬度输入框只读，不能手动修改。
3. 点击“上报模拟位置”，看到“模拟定位上报成功”即写入成功。
4. 乘客在附近下单后，本地派单召回会从 Redis GEO 中读取该司机。

“跟随当前订单起点”会从当前订单或附近订单中取 `depLongitude/depLatitude`，并额外提供 500 米、3 公里、5 公里三个偏移预设。

## 两种选点方式

- 预设点：前端内置一组坐标常量，选择后自动填入经纬度，不调用任何高德 API。
- 地图点选：展开高德 JS API 地图，点击或拖动标记选点，坐标自动回填。

经纬度输入框只做回显，已设置为只读，司机端不能手动填写。

### 地图点选配置

在仓库根目录 `.env` 中配置“Web 端（JS API）”类型的 Key 和安全密钥，并在高德控制台配置域名白名单：

```text
VITE_AMAP_JS_KEY=你的JSAPIKey
VITE_AMAP_SECURITY_CODE=你的安全密钥
```

地图点选只消耗 JS API 的地图渲染配额，不按次调用路径规划、地理编码等 Web 服务 API。未配置 Key 时，面板会显示提示，预设点仍可用；经纬度仍不能手动输入。

前端 `vite.config.js` 已设置 `envDir: '..'`，因此 Vite 读取的是仓库根目录 `.env`，不是 `frontend/.env`。修改后必须重启 `npm run dev`。

后端使用的 `AMAP_KEY` 是“Web 服务”类型 Key，不能直接给前端 JS API 使用；需要单独创建“Web 端（JS API）”Key 和安全密钥。

## 请求链路

```text
POST /api/driver/point/upload
  -> api-driver PointService
  -> service-driver-user 校验司机-车辆绑定和车辆状态
  -> service-map LocalLocationService
  -> Redis GEOADD + ZADD
```

请求体示例：

```json
{
  "carId": 1001,
  "points": [
    { "location": "116.397428,39.909230", "locatetime": "1760000000000" }
  ]
}
```

## Redis 数据

- 位置索引：`flytaxi:local:{locations}:geo`（GEO）
- 上报时间：`flytaxi:local:{locations}:updated`（ZSET）
- 过期时间：`LOCAL_LOCATION_TTL_SECONDS`，默认 1800 秒

超过 TTL 未再次上报的司机不会出现在召回结果里。

## 注意

- 经纬度最多保留 6 位小数，面板已自动格式化。
- 时间戳必须是 13 位毫秒值，且不能超过服务端当前时间 60 秒。
- 该面板只负责“把司机放到某个坐标”，不会自动派单；`dispatchRealTimeOrder` 仍需由下单事件或测试入口触发。
