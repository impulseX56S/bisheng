#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
Python 客户端签名生成示例
演示如何生成与服务器端兼容的签名
"""

import hashlib
import hmac
import base64
import time
import uuid
from typing import Dict


# 配置信息 (从安全渠道获取)
APP_ID = "test-app-001"
MASTER_SECRET = "my-super-secret-master-key-2024"
DERIVE_SALT = "your-master-secret-salt-2024"
KEY_VERSION = 1
ITERATIONS = 1000


def pbkdf2_hmac_sha256(password: str, salt: str, iterations: int, dk_len: int) -> bytes:
    """PBKDF2-HMAC-SHA256 密钥派生"""
    import hashlib
    return hashlib.pbkdf2_hmac('sha256', password.encode('utf-8'), 
                               salt.encode('utf-8'), iterations, dklen=dk_len)


def derive_secret(master_secret: str, app_id: str, version: int) -> str:
    """从 Master Secret 派生签名密钥"""
    salt = f"{DERIVE_SALT}:{app_id}:v{version}"
    derived_key = pbkdf2_hmac_sha256(master_secret, salt, ITERATIONS, 32)
    return base64.b64encode(derived_key).decode('utf-8')


def hmac_sha256(key_base64: str, content: str) -> str:
    """HMAC-SHA256 签名"""
    key_bytes = base64.b64decode(key_base64)
    signature = hmac.new(key_bytes, content.encode('utf-8'), hashlib.sha256).digest()
    return signature.hex()


def build_sign_content(app_id: str, timestamp: int, nonce: str, body: str, path: str) -> str:
    """构建待签名字符串"""
    parts = [
        f"appId={app_id}",
        f"nonce={nonce}",
        f"path={path}",
        f"timestamp={timestamp}"
    ]
    
    if body:
        parts.append(f"body={body}")
    
    return "&".join(parts)


def generate_signature_headers(app_id: str, master_secret: str, 
                                path: str, body: str = "") -> Dict[str, str]:
    """生成完整的签名请求头"""
    timestamp = int(time.time() * 1000)  # 毫秒时间戳
    nonce = uuid.uuid4().hex
    
    # 构建待签名字符串
    content = build_sign_content(app_id, timestamp, nonce, body, path)
    
    # 派生密钥
    derived_secret = derive_secret(master_secret, app_id, KEY_VERSION)
    
    # 生成签名
    sign = hmac_sha256(derived_secret, content)
    
    return {
        "X-App-ID": app_id,
        "X-Timestamp": str(timestamp),
        "X-Nonce": nonce,
        "X-Sign": sign,
        "X-Sign-Version": str(KEY_VERSION)
    }


def main():
    """主函数 - 演示使用"""
    path = "/api/data"
    body = ""
    
    # 生成签名头
    headers = generate_signature_headers(APP_ID, MASTER_SECRET, path, body)
    
    print("=== 签名请求头 ===")
    for k, v in headers.items():
        print(f"{k}: {v}")
    
    print("\n=== requests 库使用示例 ===")
    print("""
import requests

url = "http://localhost:8080/api/data"
headers = """ + str(headers) + """

response = requests.get(url, headers=headers)
print(response.status_code)
print(response.json())
""")
    
    print("\n=== cURL 命令 ===")
    curl_cmd = f'curl -X GET http://localhost:8080{path}'
    for k, v in headers.items():
        curl_cmd += f' \\\n  -H "{k}: {v}"'
    print(curl_cmd)


if __name__ == "__main__":
    main()
```

## 安装依赖

```bash
pip install requests
```

## 使用方法

```bash
python signature_client.py
```

## 封装为类

```python
class SecureApiClient:
    """安全的 API 客户端"""
    
    def __init__(self, base_url: str, app_id: str, master_secret: str):
        self.base_url = base_url.rstrip('/')
        self.app_id = app_id
        self.master_secret = master_secret
        self.session = requests.Session()
    
    def _generate_headers(self, method: str, path: str, body: str = "") -> Dict[str, str]:
        """生成签名头"""
        timestamp = int(time.time() * 1000)
        nonce = uuid.uuid4().hex
        
        # 构建待签名字符串
        content = build_sign_content(self.app_id, timestamp, nonce, body, path)
        
        # 派生密钥并生成签名
        derived_secret = derive_secret(self.master_secret, self.app_id, KEY_VERSION)
        sign = hmac_sha256(derived_secret, content)
        
        return {
            "X-App-ID": self.app_id,
            "X-Timestamp": str(timestamp),
            "X-Nonce": nonce,
            "X-Sign": sign,
            "X-Sign-Version": str(KEY_VERSION)
        }
    
    def request(self, method: str, path: str, **kwargs):
        """发送带签名的请求"""
        url = f"{self.base_url}{path}"
        body = kwargs.get('json', kwargs.get('data', ''))
        
        # 生成签名头
        headers = self._generate_headers(method, path, str(body))
        
        # 合并用户自定义头
        if 'headers' in kwargs:
            headers.update(kwargs.pop('headers'))
        
        # 发送请求
        response = self.session.request(method, url, headers=headers, **kwargs)
        response.raise_for_status()
        return response.json()
    
    def get(self, path: str, **kwargs):
        return self.request('GET', path, **kwargs)
    
    def post(self, path: str, **kwargs):
        return self.request('POST', path, **kwargs)


# 使用示例
if __name__ == "__main__":
    client = SecureApiClient(
        base_url="http://localhost:8080",
        app_id="test-app-001",
        master_secret="my-super-secret-master-key-2024"
    )
    
    # GET 请求
    result = client.get("/api/data", params={"param": "value"})
    print(result)
    
    # POST 请求
    result = client.post("/api/submit", json={"key": "value"})
    print(result)
