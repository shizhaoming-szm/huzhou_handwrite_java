# 手写签名识别服务

这是一个基于Spring Boot的手写签名识别服务，实现了与Python版本相同的功能。

## 功能特点

- 提供两个REST API接口：`/mix` 和 `/classify`
- 支持图片上传和AI模型调用
- 保持与Python版本相同的输入输出格式
- 使用Java 8和Spring MVC架构
- 配置文件采用YAML格式，更易读和维护
- 集成Swagger API文档，便于接口测试和管理
- **完全支持FormData格式上传**

## 接口说明

### 1. 签名比对接口 `/mix`

**请求方式**: POST  
**Content-Type**: multipart/form-data

**请求参数**:
- `images`: 图片文件列表（必填，支持多文件上传）
- `qwen_server`: Qwen模型服务器地址（可选，默认值）
- `qwen_model`: Qwen模型名称（可选，默认值）
- `qwen_api_key`: Qwen API密钥（可选，默认值）
- `cg_server`: ContextGem模型服务器地址（可选，默认值）
- `cg_model`: ContextGem模型名称（可选，默认值）
- `cg_api_key`: ContextGem API密钥（可选，默认值）
- `prompt`: 提示词（可选，默认值）
- `max_tokens`: 最大token数（可选，默认值）
- `max_paras`: 最大段落数（可选，默认值）
- `max_items`: 最大项目数（可选，默认值）

**FormData示例**:
```
POST /mix HTTP/1.1
Content-Type: multipart/form-data; boundary=----WebKitFormBoundary7MA4YWxkTrZu0gW

------WebKitFormBoundary7MA4YWxkTrZu0gW
Content-Disposition: form-data; name="images"; filename="id_card.jpg"
Content-Type: image/jpeg

[图片二进制数据]
------WebKitFormBoundary7MA4YWxkTrZu0gW
Content-Disposition: form-data; name="images"; filename="signature.jpg"
Content-Type: image/jpeg

[图片二进制数据]
------WebKitFormBoundary7MA4YWxkTrZu0gW
Content-Disposition: form-data; name="prompt"

这两张图分别为身份证与手写签名...
------WebKitFormBoundary7MA4YWxkTrZu0gW--
```

**响应示例**:
```json
{
  "qwen_text": "识别的文本内容...",
  "duration": 2.5,
  "concepts": {
    "姓名": ["张三"],
    "签名一致": [true]
  },
  "raw": {
    "姓名": "张三",
    "签名一致": true,
    "理由": "签名与身份证姓名匹配"
  }
}
```

### 2. 图片分类接口 `/classify`

**请求方式**: POST  
**Content-Type**: multipart/form-data

**请求参数**:
- `images`: 图片文件列表（必填，支持多文件上传）
- `qwen_server`: Qwen模型服务器地址（可选，默认值）
- `qwen_model`: Qwen模型名称（可选，默认值）
- `qwen_api_key`: Qwen API密钥（可选，默认值）
- `prompt`: 分类提示词（可选，默认值）
- `max_tokens`: 最大token数（可选，默认值）
- `max_paras`: 最大段落数（可选，默认值）
- `max_items`: 最大项目数（可选，默认值）

**响应示例**:
```json
{
  "responseBody": {
    "llmResult": ["身份证正面"],
    "ocrResult": ""
  },
  "message": "成功",
  "errorCode": 0
}
```

## 环境准备

### 必需环境
1. **Java 8 或更高版本**
   - 下载地址：https://www.oracle.com/java/technologies/javase-downloads.html
   - 验证安装：`java -version`

2. **Maven 3.6 或更高版本**
   - 下载地址：https://maven.apache.org/download.cgi
   - 验证安装：`mvn -version`
   - 配置环境变量：
     - `MAVEN_HOME`: Maven安装目录
     - `PATH`: 添加 `%MAVEN_HOME%\bin`

### 可选环境
- **IDE**: IntelliJ IDEA, Eclipse等
- **Git**: 用于版本控制

## 快速开始

### 方法一：使用命令行
1. **编译项目**
```bash
mvn clean compile
```

2. **运行应用**
```bash
mvn spring-boot:run
```

### 方法二：使用IDE
1. 导入项目到IDE
2. 运行 [Application.java](file://D:\kingsware\szm\zhejiangpoc\handwrite_java\handwrite\src\main\java\com\kinsware\file\Application.java) 的main方法

### 方法三：使用批处理文件
```bash
run.bat
```

## 访问服务

服务默认运行在 `http://localhost:8080`

### API文档访问
- **Swagger UI**: http://localhost:8080/swagger-ui.html
- **API文档**: http://localhost:8080/v2/api-docs

### 测试接口

#### 使用curl测试
```bash
# 测试分类接口
curl -X POST http://localhost:8080/classify \
  -F "images=@test.jpg"

# 测试签名比对接口（多文件上传）
curl -X POST http://localhost:8080/mix \
  -F "images=@id_card.jpg" \
  -F "images=@signature.jpg"
```

#### 使用JavaScript测试
```javascript
// FormData上传示例
const formData = new FormData();
formData.append('images', fileInput1.files[0]); // 身份证图片
formData.append('images', fileInput2.files[0]); // 签名图片
formData.append('prompt', '自定义提示词');

fetch('/mix', {
  method: 'POST',
  body: formData
})
.then(response => response.json())
.then(data => console.log(data));
```

#### 使用Python测试
```python
import requests

# 单文件上传
files = {'images': open('test.jpg', 'rb')}
response = requests.post('http://localhost:8080/classify', files=files)

# 多文件上传
files = [
    ('images', open('id_card.jpg', 'rb')),
    ('images', open('signature.jpg', 'rb'))
]
response = requests.post('http://localhost:8080/mix', files=files)
```

## 配置说明

应用配置在 `src/main/resources/application.yml` 文件中：

```yaml
server:
  port: 8080                           # 服务端口
  tomcat:
    max-http-header-size: 262144       # 最大HTTP头大小
    max-swallow-size: 100MB            # 最大吞吐量
    connection-timeout: 20000          # 连接超时时间

spring:
  servlet:
    multipart:
      enabled: true                    # 启用文件上传
      max-file-size: 100MB             # 单个文件最大大小
      max-request-size: 100MB          # 请求总大小限制
  mvc:
    pathmatch:
      matching-strategy: ant_path_matcher  # 路径匹配策略

logging:
  level:
    com.kinsware.file: INFO           # 日志级别
  pattern:
    console: "%d{yyyy-MM-dd HH:mm:ss} [%thread] %-5level %logger{36} - %msg%n"
```

## 依赖说明

主要依赖：
- Spring Boot 2.4.13
- OkHttp 4.9.3 (HTTP客户端)
- Jackson (JSON处理)
- SnakeYAML (YAML配置支持)
- Springfox Swagger2 (API文档)
- Springfox Swagger UI (文档界面)

## 故障排除

### 1. 端口占用问题
**错误信息**: `Port 8080 is already in use`

**解决方案**:
- 修改 `application.yml` 中的 `server.port` 值
- 或者终止占用端口的进程：
```bash
# Windows
netstat -ano | findstr :8080
taskkill /PID <进程ID> /F

# Linux/Mac
lsof -i :8080
kill -9 <进程ID>
```

### 2. Maven命令未找到
**错误信息**: `'mvn' 不是内部或外部命令`

**解决方案**:
- 确保已安装Maven
- 配置MAVEN_HOME环境变量
- 将Maven bin目录添加到PATH环境变量

### 3. Java版本不兼容
**错误信息**: `UnsupportedClassVersionError`

**解决方案**:
- 确保使用Java 8或更高版本
- 检查JAVA_HOME环境变量设置

### 4. 依赖下载失败
**错误信息**: `Could not resolve dependencies`

**解决方案**:
- 检查网络连接
- 配置Maven镜像源（如阿里云镜像）
- 清理Maven缓存：`mvn clean`

## 注意事项

1. 确保网络可以访问配置的AI模型服务器
2. 图片文件大小限制为100MB
3. 临时文件会在处理完成后自动清理
4. 支持的图片格式：JPEG, PNG, WEBP, BMP
5. 可通过Swagger UI界面直接测试所有API接口
6. **完全支持FormData格式上传，可同时上传多个文件**

## 错误处理

服务会返回标准的HTTP状态码：
- 200: 成功
- 400: 请求参数错误
- 502: AI服务调用失败
- 500: 服务器内部错误