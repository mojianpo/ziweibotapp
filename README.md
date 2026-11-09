# ziweibotapp
## API接口

### 创建账号接口

curl -X POST "http://localhost:3000/ziweibot/api/account/create" -H "API_KEY: sk-wedfrtseepdd123wer"

### 验证账号接口

curl -X POST 'http://localhost:3000/ziweibot/api/account/verify' \
-H 'API_KEY: ***' \
-H 'Content-Type: application/json' \
-d '{"key":"mojianpo"}'

返回 {"valid":true} 表示账号 KEY 当前可以登录
