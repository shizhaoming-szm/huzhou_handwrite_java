import os
import sys
import json
import base64
import time
import uuid
from typing import List
import asyncio
from contextlib import asynccontextmanager

from openai import OpenAI, AsyncOpenAI
from fastapi import FastAPI, UploadFile, File, Form, HTTPException
import uvicorn
import httpx

import re


def _mime_from_ext(path: str) -> str:
    ext = os.path.splitext(path)[1].lower()
    if ext in {".jpg", ".jpeg"}:
        return "image/jpeg"
    if ext == ".png":
        return "image/png"
    if ext == ".webp":
        return "image/webp"
    if ext == ".bmp":
        return "image/bmp"
    return "application/octet-stream"


def bytes_to_data_url(data: bytes, mime: str) -> str:
    b64 = base64.b64encode(data).decode()
    return f"data:{mime};base64,{b64}"


class ClientPool:
    def __init__(self):
        self._clients = {}
        self._lock = asyncio.Lock()

    async def get_client(self, server: str, api_key: str, use_async: bool = True):
        key = f"{server}_{api_key}"
        async with self._lock:
            if key not in self._clients:
                http_client = httpx.AsyncClient(
                    timeout=httpx.Timeout(300.0, connect=60.0),
                    limits=httpx.Limits(max_connections=50, max_keepalive_connections=10)
                )
                if use_async:
                    self._clients[key] = AsyncOpenAI(api_key=api_key, base_url=server, http_client=http_client)
                else:
                    self._clients[key] = OpenAI(api_key=api_key, base_url=server)
            return self._clients[key]

    async def close_all(self):
        async with self._lock:
            for client in self._clients.values():
                if hasattr(client, 'close'):
                    if asyncio.iscoroutinefunction(client.close):
                        await client.close()
                    else:
                        client.close()
            self._clients.clear()


client_pool = ClientPool()


async def read_image_async(path: str) -> bytes:
    def _read():
        with open(path, "rb") as f:
            return f.read()
    return await asyncio.to_thread(_read)


async def run_pipeline(images_paths: list[str], qwen_server: str, qwen_model: str, qwen_api_key: str, cg_server: str, cg_model: str, cg_api_key: str, prompt: str, max_tokens: int, max_paras: int, max_items: int) -> dict:
    data_urls: List[str] = []
    for p in images_paths:
        data = await read_image_async(p)
        mime = _mime_from_ext(p)
        data_urls.append(bytes_to_data_url(data, mime))
    print(f"读取图片完成: {len(images_paths)} 张")

    q_client = await client_pool.get_client(qwen_server, qwen_api_key, use_async=True)
    try:
        print(f"检查Qwen模型: server={qwen_server}, model={qwen_model}")
        q_models = await q_client.models.list()
        q_ids = {m.id for m in q_models.data}
        if qwen_model not in q_ids:
            return {"error": f"Qwen模型不可用: {qwen_model}", "available_models": sorted(list(q_ids))}
    except Exception as e:
        return {"error": f"无法连接到Qwen服务: {e}"}

    c_client = await client_pool.get_client(cg_server, cg_api_key, use_async=True)
    try:
        print(f"检查文本模型: server={cg_server}, model={cg_model}")
        c_models = await c_client.models.list()
        c_ids = {m.id for m in c_models.data}
        if cg_model not in c_ids:
            return {"error": f"ContextGem模型不可用: {cg_model}", "available_models": sorted(list(c_ids))}
    except Exception as e:
        return {"error": f"无法连接到ContextGem服务: {e}"}

    content = [{"type": "text", "text": prompt}] + [{"type": "image_url", "image_url": {"url": url}} for url in data_urls]
    print(f"视觉识别开始: images={len(data_urls)}, prompt_len={len(prompt)}")
    start = time.time()
    qwen_text = ""
    try:
        stream = await q_client.chat.completions.create(
            model=qwen_model,
            messages=[{"role": "user", "content": content}],
            temperature=0,
            stream=True
        )
        async for chunk in stream:
            if chunk.choices and chunk.choices[0].delta.content:
                qwen_text += chunk.choices[0].delta.content
    except Exception as e:
        return {"error": f"视觉模型错误: {e}"}
    end = time.time()
    print(f"视觉识别结束: duration={round(end - start, 3)}s, text_len={len(qwen_text)}")
    preview = (qwen_text[:400] + ("..." if len(qwen_text) > 400 else ""))
    print(f"识别文本预览: {preview}")
    instruct = (
        "仅返回JSON，包含键：姓名(string)、签名一致(boolean)、理由(string)。"
        "如果无法判断签名一致，则将签名一致设为false。"
        "识别文本如下：\n" + qwen_text
    )
    print(f"文本抽取开始: model={cg_model}, max_tokens={max_tokens}, instruct_len={len(instruct)}")
    content = ""
    try:
        stream = await c_client.chat.completions.create(
            model=cg_model,
            messages=[{"role": "user", "content": instruct}],
            temperature=0,
            max_tokens=max_tokens,
            stream=True
        )
        async for chunk in stream:
            if chunk.choices and chunk.choices[0].delta.content:
                content += chunk.choices[0].delta.content
    except Exception as e:
        return {"error": f"文本模型错误: {e}"}
    print(f"文本抽取结束: output_len={len(content)}")
    print(f"文本抽取原始输出: {content}")
    data = None
    try:
        data = json.loads(content)
    except Exception:
        s = re.sub(r"```[\s\S]*?```", "", content, flags=re.I | re.S)
        blocks = re.findall(r"```json\s*([\s\S]*?)```", s, flags=re.I)
        if not blocks:
            blocks = re.findall(r"```\s*([\s\S]*?)```", s, flags=re.I)
        for b in blocks:
            t = b.strip()
            try:
                data = json.loads(t)
                break
            except Exception:
                continue
        if data is None:
            objs = re.findall(r"\{[\s\S]*?\}", s)
            for o in objs:
                try:
                    data = json.loads(o)
                    break
                except Exception:
                    continue
    parsed = isinstance(data, dict)
    if not parsed:
        print("JSON解析失败，返回无法识别")
        out = {"姓名": [], "签名一致": ["无法识别"]}
        return {"qwen_text": qwen_text, "duration": round(end - start, 3), "concepts": out, "raw": content}
    name = data.get("姓名") or data.get("name") or ""
    match = data.get("签名一致")
    if match is None:
        match = bool(data.get("signature_match"))
    print(f"抽取结果: 姓名={name if name else '空'}, 签名一致={bool(match)}")
    out = {"姓名": ([name] if name else []), "签名一致": [bool(match)]}
    return {"qwen_text": qwen_text, "duration": round(end - start, 3), "concepts": out, "raw": data}


async def run_pipeline_classify(images_paths: list[str], qwen_server: str, qwen_model: str, qwen_api_key: str,  prompt: str, max_tokens: int, max_paras: int, max_items: int) -> dict:
    data_urls: List[str] = []
    for p in images_paths:
        data = await read_image_async(p)
        mime = _mime_from_ext(p)
        data_urls.append(bytes_to_data_url(data, mime))
    print(f"读取图片完成: {len(images_paths)} 张")

    q_client = await client_pool.get_client(qwen_server, qwen_api_key, use_async=True)
    try:
        print(f"检查Qwen模型: server={qwen_server}, model={qwen_model}")
        q_models = await q_client.models.list()
        q_ids = {m.id for m in q_models.data}
        if qwen_model not in q_ids:
            return {"error": f"Qwen模型不可用: {qwen_model}", "available_models": sorted(list(q_ids))}
    except Exception as e:
        return {"error": f"无法连接到Qwen服务: {e}"}

    content = [{"type": "text", "text": prompt}] + [{"type": "image_url", "image_url": {"url": url}} for url in data_urls]
    print(f"视觉识别开始: images={len(data_urls)}, prompt_len={len(prompt)}")
    start = time.time()
    qwen_text = ""
    try:
        stream = await q_client.chat.completions.create(
            model=qwen_model,
            messages=[{"role": "user", "content": content}],
            temperature=0,
            stream=True
        )
        async for chunk in stream:
            if chunk.choices and chunk.choices[0].delta.content:
                qwen_text += chunk.choices[0].delta.content
    except Exception as e:
        return {"error": f"视觉模型错误: {e}"}
    end = time.time()
    print(f"视觉识别结束: duration={round(end - start, 3)}s, text_len={len(qwen_text)}")
    preview = (qwen_text[:400] + ("..." if len(qwen_text) > 400 else ""))
    print(f"识别文本预览: {qwen_text}")
    return qwen_text


@asynccontextmanager
async def lifespan(app: FastAPI):
    yield
    await client_pool.close_all()


app = FastAPI(lifespan=lifespan)


@app.post("/mix")
async def mix(
    images: List[UploadFile] = File(...),
    qwen_server: str = Form("http://192.168.111.86:11434/v1"),
    qwen_model: str = Form("qwen3-vl:8b-instruct"),
    qwen_api_key: str = Form("not-needed"),
    cg_server: str = Form("http://192.168.111.86:11434/v1"),
    cg_model: str = Form("qwen3-vl:8b-instruct"),
    cg_api_key: str = Form("sk-155b32c6d3334255a3539a3839cc4d99"),
    prompt: str = Form("这两张图分别为身份证与手写签名。请先准确识别并输出两图的文本内容，尤其是身份证上的姓名原文和手写签名的逐字转写；不要编造。如签名过于模糊或潦草无法辨认请明确说明。依据规则：若签名可读内容与身份证姓名的相似度低于80%则判为不一致；若无法判断也视为不一致。"),
    max_tokens: int = Form(16384),
    max_paras: int = Form(2000),
    max_items: int = Form(5),
):
    if not images:
        raise HTTPException(status_code=400, detail="未提供图片")

    temp_paths: list[str] = []
    try:
        print(f"接收文件: {len(images)} 个")
        for up in images:
            data = await up.read()
            ext = os.path.splitext(up.filename or "")[1] or ".png"
            uid = uuid.uuid4().hex
            tmp = os.path.join(os.getcwd(), f"_tmp_{uid}_{os.getpid()}{ext}")
            with open(tmp, "wb") as f:
                f.write(data)
            temp_paths.append(tmp)
        print(f"生成临时文件: {len(temp_paths)} 个")

        res = await run_pipeline(temp_paths, qwen_server, qwen_model, qwen_api_key, cg_server, cg_model, cg_api_key, prompt, max_tokens, max_paras, max_items)
        if "error" in res:
            raise HTTPException(status_code=502, detail=res)
        print("请求完成")
        return res
    finally:
        for p in temp_paths:
            try:
                if os.path.exists(p):
                    os.remove(p)
            except Exception:
                pass


@app.post("/classify")
async def classify(
    images: List[UploadFile] = File(...),
    qwen_server: str = Form("https://llm.huzhou.gov.cn/servingpod/b50538bd4c0a44ddb94666e7b3e756c9/v1/chat/completions"),
    qwen_model: str = Form("hzrs-Qwen3-VL-8B-Instruct"),
    qwen_api_key: str = Form("not-needed"),
    prompt: str = Form("对图片进行分类，类型有\"身份证正面\"，\"身份证反面\"，\"出生医学证明\"，\"常驻人口登记卡\"，\"居民户口簿信息\"，如果不是以上类型，请返回\"未分类\"，仅返回上述分类结果"),
    max_tokens: int = Form(16384),
    max_paras: int = Form(2000),
    max_items: int = Form(5),
):
    if not images:
        raise HTTPException(status_code=400, detail="未提供图片")

    temp_paths: list[str] = []
    try:
        print(f"接收文件: {len(images)} 个")
        for up in images:
            data = await up.read()
            ext = os.path.splitext(up.filename or "")[1] or ".png"
            tmp = os.path.join(os.getcwd(), f"_tmp_{int(time.time()*1000)}_{os.getpid()}{ext}")
            with open(tmp, "wb") as f:
                f.write(data)
            temp_paths.append(tmp)
        print(f"生成临时文件: {len(temp_paths)} 个")

        res = await run_pipeline_classify(temp_paths, qwen_server, qwen_model, qwen_api_key, prompt, max_tokens, max_paras, max_items)
        if "error" in res:
            raise HTTPException(status_code=502, detail=res)
        print("请求完成")
        result = {"llmResult": [res],"ocrResult":""}
        return {"responseBody":result,"message":"成功","errorCode":0}
    finally:
        for p in temp_paths:
            try:
                if os.path.exists(p):
                    os.remove(p)
            except Exception:
                pass


def main():
    port = int(os.getenv("MIX_OUT_PORT", "8001"))
    uvicorn.run(app, host="0.0.0.0", port=port)


if __name__ == "__main__":
    main()
