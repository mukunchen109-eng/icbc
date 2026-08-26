# Dify 输入格式

后端发送给 Dify Workflow 的请求体，核心结构如下：

```json
{
  "inputs": {
    "news_list": "[{\"id\":\"content_hash\",\"title\":\"央行开展5000亿元MLF操作\",\"content\":\"资讯完整正文……\",\"industry\":\"金融\",\"area\":\"全国\"}]"
  },
  "response_mode": "blocking",
  "user": "report-module"
}
```

说明：

- `inputs.news_list` 的值是一个 JSON 字符串
- 这个字符串表示一组资讯数组
- 数组中的每一项都对应一条资讯

数组元素的标准结构如下：

```json
{
  "id": "content_hash",
  "title": "央行开展5000亿元MLF操作",
  "content": "资讯完整正文……",
  "industry": "金融",
  "area": "全国"
}
```

字段说明：

| 字段 | 说明 |
| --- | --- |
| `id` | 资讯唯一标识，当前使用 `content_hash` |
| `title` | 资讯标题 |
| `content` | 资讯正文 |
| `industry` | 行业分类 |
| `area` | 地域分类 |

示例中如果有多条资讯，`news_list` 里的数组会继续追加，例如：

```json
[
  {
    "id": "content_hash_1",
    "title": "央行开展5000亿元MLF操作",
    "content": "资讯完整正文……",
    "industry": "金融",
    "area": "全国"
  },
  {
    "id": "content_hash_2",
    "title": "北京市发布促进服务消费若干措施",
    "content": "资讯完整正文……",
    "industry": "政策",
    "area": "北京"
  }
]
```

注意：

- Dify 工作流里需要按 `news_list` 这个字段名读取输入
- 如果工作流节点里引用了其它字段，就需要同步调整工作流配置
